import base64
import json
import os
import subprocess

from build_pc_native import build_native_exe, EXE_PATH
from make_icon import ICO_PATH

PC_DIR = os.path.abspath(os.path.dirname(__file__))
BAT_PATH = os.path.join(PC_DIR, "보르타_이지스_실행.bat")


def _run_ps_utf16(script: str) -> str:
    encoded = base64.b64encode(script.encode("utf-16le")).decode("ascii")
    out = subprocess.check_output(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded],
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return out.strip()


def write_batch_launcher():
    lines = [
        "@echo off",
        f'start "" "{EXE_PATH}"',
    ]
    with open(BAT_PATH, "w", encoding="cp949", errors="replace") as f:
        f.write("\r\n".join(lines) + "\r\n")
    print(f"Wrote launcher batch (CP949) -> {BAT_PATH}")


def create_shortcuts() -> dict:
    ps_script = f'''
$ErrorActionPreference = 'Stop'
$desktop = [Environment]::GetFolderPath('Desktop')
$programs = [Environment]::GetFolderPath('Programs')
$targets = @(
    (Join-Path $desktop '보르타 이지스.lnk'),
    (Join-Path $programs '보르타 이지스.lnk')
)
$ws = New-Object -ComObject WScript.Shell
$created = @()
foreach ($lnkPath in $targets) {{
    $sc = $ws.CreateShortcut($lnkPath)
    $sc.TargetPath = '{EXE_PATH}'
    $sc.Arguments = ''
    $sc.WorkingDirectory = '{PC_DIR}'
    $sc.IconLocation = '{ICO_PATH},0'
    $sc.Description = '보르타 이지스 Pro v3.1 PC (자유 리사이즈 · 백그라운드 상주 · WASAPI 동시재생)'
    $sc.Save()
    $created += $lnkPath
}}
$created | ConvertTo-Json -Compress
'''
    raw = _run_ps_utf16(ps_script)
    paths = json.loads(raw)
    if isinstance(paths, str):
        paths = [paths]
    return {"shortcuts": paths, "bat": BAT_PATH, "ico": ICO_PATH, "exe": EXE_PATH}


def main():
    build_native_exe()
    write_batch_launcher()
    info = create_shortcuts()
    print(json.dumps({"ok": True, **info}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
