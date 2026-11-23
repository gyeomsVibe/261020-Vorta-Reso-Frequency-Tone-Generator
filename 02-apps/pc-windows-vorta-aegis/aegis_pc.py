import argparse
import ctypes
import json
import os
import posixpath
import socket
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

APP_DIR = os.path.abspath(os.path.dirname(__file__))
WEB_DIR = os.path.join(APP_DIR, "web")
ASSETS_DIR = os.path.join(APP_DIR, "assets")

LOCAL_DATA_DIR = os.path.join(
    os.environ.get("LOCALAPPDATA") or os.path.expanduser("~"),
    "VortaAegis",
)
STATE_FILE = os.path.join(LOCAL_DATA_DIR, "state.json")
RUNTIME_FILE = os.path.join(LOCAL_DATA_DIR, "runtime.json")
EDGE_PROFILE_DIR = os.path.join(LOCAL_DATA_DIR, "edge_profile")

EDGE_CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
]

DEFAULT_STATE = {
    "volumePercent": 45,
    "muted": False,
    "lastTab": 0,
    "lastPresetId": 1,
    "ultrasonicGuard": True,
}

MIME_MAP = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".mjs": "application/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".svg": "image/svg+xml",
    ".ico": "image/x-icon",
    ".png": "image/png",
}


class HostController:
    def __init__(self, state_file: str):
        self.state_file = state_file
        self.lock = threading.Lock()
        self.state = self._load_state()
        self.active_clients = 0
        self.ever_had_client = False
        self.last_disconnect_at = None
        self.stopping = False
        self.started_at = time.monotonic()

    def _load_state(self) -> dict:
        data = dict(DEFAULT_STATE)
        try:
            if os.path.exists(self.state_file):
                with open(self.state_file, "r", encoding="utf-8") as f:
                    loaded = json.load(f)
                if isinstance(loaded, dict):
                    data.update(loaded)
        except Exception:
            pass
        return self._sanitize(data)

    def _sanitize(self, data: dict) -> dict:
        pct = data.get("volumePercent", 45)
        try:
            pct = max(0, min(100, int(round(float(pct)))))
        except Exception:
            pct = 45
        tab = data.get("lastTab", 0)
        try:
            tab = int(tab)
            if tab < 0 or tab >= 8:
                tab = 0
        except Exception:
            tab = 0
        preset_id = data.get("lastPresetId", 1)
        try:
            preset_id = max(1, min(48, int(preset_id)))
        except Exception:
            preset_id = 1
        return {
            "volumePercent": pct,
            "muted": bool(data.get("muted", False)),
            "lastTab": tab,
            "lastPresetId": preset_id,
            "ultrasonicGuard": bool(data.get("ultrasonicGuard", True)),
        }

    def get_state(self) -> dict:
        with self.lock:
            return dict(self.state)

    def patch_state(self, patch: dict) -> dict:
        with self.lock:
            merged = dict(self.state)
            if isinstance(patch, dict):
                merged.update(patch)
            self.state = self._sanitize(merged)
            os.makedirs(os.path.dirname(self.state_file), exist_ok=True)
            tmp_path = self.state_file + ".tmp"
            with open(tmp_path, "w", encoding="utf-8") as f:
                json.dump(self.state, f, ensure_ascii=False, indent=2)
            os.replace(tmp_path, self.state_file)
            return dict(self.state)

    def client_connected(self):
        with self.lock:
            self.active_clients += 1
            self.ever_had_client = True
            self.last_disconnect_at = None

    def client_disconnected(self):
        with self.lock:
            self.active_clients = max(0, self.active_clients - 1)
            if self.active_clients == 0:
                self.last_disconnect_at = time.monotonic()

    def ping_payload(self) -> dict:
        with self.lock:
            return {
                "ok": True,
                "app": "vorta-aegis-pc",
                "version": "2.3.0",
                "pid": os.getpid(),
                "active_clients": self.active_clients,
                "ever_had_client": self.ever_had_client,
            }


def _make_handler(ctrl: HostController):
    class AegisHandler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, format, *args):
            return

        def _send_json(self, status: int, payload: dict):
            raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(raw)

        def _serve_file(self, base_dir: str, rel_path: str):
            norm = posixpath.normpath(rel_path).lstrip("/")
            fs_path = os.path.abspath(os.path.join(base_dir, *norm.split("/")))
            if os.path.commonpath([os.path.abspath(base_dir), fs_path]) != os.path.abspath(base_dir):
                self.send_error(403)
                return
            if not os.path.isfile(fs_path):
                self.send_error(404)
                return
            ext = os.path.splitext(fs_path)[1].lower()
            ctype = MIME_MAP.get(ext, "application/octet-stream")
            with open(fs_path, "rb") as f:
                data = f.read()
            self.send_response(200)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path

            if path == "/api/ping":
                self._send_json(200, ctrl.ping_payload())
                return

            if path == "/api/state":
                self._send_json(200, ctrl.get_state())
                return

            if path == "/api/events":
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream; charset=utf-8")
                self.send_header("Cache-Control", "no-cache")
                self.send_header("Connection", "keep-alive")
                self.end_headers()
                ctrl.client_connected()
                try:
                    while not ctrl.stopping:
                        msg = f"data: {json.dumps({'ok': True})}\n\n".encode("utf-8")
                        self.wfile.write(msg)
                        self.wfile.flush()
                        time.sleep(1.0)
                except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
                    pass
                finally:
                    ctrl.client_disconnected()
                return

            if path == "/favicon.ico":
                self._serve_file(ASSETS_DIR, "vorta_aegis.ico")
                return

            if path.startswith("/assets/"):
                self._serve_file(ASSETS_DIR, path[len("/assets/"):])
                return

            if path == "/" or path == "":
                self._serve_file(WEB_DIR, "index.html")
                return

            self._serve_file(WEB_DIR, path.lstrip("/"))

        def do_POST(self):
            parsed = urllib.parse.urlparse(self.path)
            if parsed.path != "/api/state":
                self.send_error(404)
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                length = 0
            raw = self.rfile.read(min(length, 65536)) if length > 0 else b"{}"
            try:
                patch = json.loads(raw.decode("utf-8"))
            except Exception:
                self._send_json(400, {"ok": False, "error": "invalid json"})
                return
            updated = ctrl.patch_state(patch)
            self._send_json(200, updated)

    return AegisHandler


def _focus_existing_window(title_substr: str = "보르타 이지스") -> bool:
    if os.name != "nt":
        return False
    try:
        user32 = ctypes.windll.user32
        found = []

        @ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
        def enum_cb(hwnd, _lparam):
            if user32.IsWindowVisible(hwnd):
                length = user32.GetWindowTextLengthW(hwnd)
                if length > 0:
                    buf = ctypes.create_unicode_buffer(length + 1)
                    user32.GetWindowTextW(hwnd, buf, length + 1)
                    if title_substr in buf.value:
                        found.append(hwnd)
            return True

        user32.EnumWindows(enum_cb, 0)
        if found:
            hwnd = found[0]
            user32.ShowWindow(hwnd, 9)  # SW_RESTORE
            user32.SetForegroundWindow(hwnd)
            return True
    except Exception:
        pass
    return False


def _try_reuse_running_instance() -> bool:
    if not os.path.exists(RUNTIME_FILE):
        return False
    try:
        with open(RUNTIME_FILE, "r", encoding="utf-8") as f:
            rt = json.load(f)
        port = int(rt.get("port", 0))
        if port <= 0:
            return False
        url = f"http://127.0.0.1:{port}/api/ping"
        with urllib.request.urlopen(url, timeout=0.6) as resp:
            if resp.status == 200:
                data = json.loads(resp.read().decode("utf-8"))
                if data.get("app") == "vorta-aegis-pc":
                    _focus_existing_window("보르타 이지스")
                    return True
    except Exception:
        return False
    return False


def _seed_edge_profile(profile_dir: str):
    os.makedirs(os.path.join(profile_dir, "Default"), exist_ok=True)
    first_run = os.path.join(profile_dir, "First Run")
    if not os.path.exists(first_run):
        with open(first_run, "w", encoding="utf-8") as f:
            f.write("")
    prefs_path = os.path.join(profile_dir, "Default", "Preferences")
    if not os.path.exists(prefs_path):
        prefs = {
            "browser": {
                "has_seen_welcome_page": True,
                "check_default_browser": False,
            },
            "first_run_tabs": [],
            "distribution": {
                "skip_first_run_ui": True,
                "suppress_first_run_bubble": True,
                "do_not_create_desktop_shortcut": True,
                "do_not_create_taskbar_shortcut": True,
            },
        }
        with open(prefs_path, "w", encoding="utf-8") as f:
            json.dump(prefs, f)


def _find_browser() -> str:
    for candidate in EDGE_CANDIDATES:
        if os.path.exists(candidate):
            return candidate
    raise FileNotFoundError("Microsoft Edge or Chrome executable not found")


def _write_runtime(port: int):
    os.makedirs(LOCAL_DATA_DIR, exist_ok=True)
    tmp_path = RUNTIME_FILE + ".tmp"
    with open(tmp_path, "w", encoding="utf-8") as f:
        json.dump({"port": port, "pid": os.getpid()}, f)
    os.replace(tmp_path, RUNTIME_FILE)


def _remove_runtime():
    try:
        if os.path.exists(RUNTIME_FILE):
            with open(RUNTIME_FILE, "r", encoding="utf-8") as f:
                rt = json.load(f)
            if rt.get("pid") == os.getpid():
                os.remove(RUNTIME_FILE)
    except Exception:
        pass


def main():
    parser = argparse.ArgumentParser(description="Vorta Aegis Pro v2.3 Windows PC Host")
    parser.add_argument("--port", type=int, default=0, help="Port to bind on 127.0.0.1 (0=ephemeral)")
    parser.add_argument("--no-browser", action="store_true", help="Serve HTTP without launching browser window")
    args = parser.parse_args()

    if not args.no_browser and _try_reuse_running_instance():
        return

    os.makedirs(LOCAL_DATA_DIR, exist_ok=True)
    ctrl = HostController(STATE_FILE)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), _make_handler(ctrl))
    server.daemon_threads = True
    port = server.server_address[1]
    _write_runtime(port)

    browser_proc = None
    if not args.no_browser:
        browser_exe = _find_browser()
        _seed_edge_profile(EDGE_PROFILE_DIR)
        cmd = [
            browser_exe,
            f"--app=http://127.0.0.1:{port}/",
            f"--user-data-dir={EDGE_PROFILE_DIR}",
            "--window-size=480,920",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-features=msEdgeSignInOnFirstRun,msEdgeFirstRunExperience",
        ]
        creationflags = 0x08000000 if os.name == "nt" else 0
        browser_proc = subprocess.Popen(cmd, creationflags=creationflags)

        def watchdog():
            while not ctrl.stopping:
                time.sleep(0.5)
                with ctrl.lock:
                    had = ctrl.ever_had_client
                    active = ctrl.active_clients
                    disc = ctrl.last_disconnect_at
                now = time.monotonic()
                if had and active == 0 and disc is not None and (now - disc) >= 3.0:
                    ctrl.stopping = True
                    server.shutdown()
                    return
                if browser_proc is not None and browser_proc.poll() is not None:
                    if (had and active == 0) or (not had and (now - ctrl.started_at) >= 10.0):
                        ctrl.stopping = True
                        server.shutdown()
                        return

        threading.Thread(target=watchdog, daemon=True).start()
    else:
        print(f"AEGIS_HOST_READY port={port} pid={os.getpid()}", flush=True)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        ctrl.stopping = True
        server.server_close()
        _remove_runtime()


if __name__ == "__main__":
    main()
