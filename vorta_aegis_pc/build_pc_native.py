import os
import subprocess
import sys

from make_icon import build_icons, ICO_PATH
from sync_presets import sync

PC_DIR = os.path.abspath(os.path.dirname(__file__))
NATIVE_DIR = os.path.join(PC_DIR, "native")
EXE_PATH = os.path.join(PC_DIR, "VortaAegisPC.exe")

FX_DIR = r"C:\Windows\Microsoft.NET\Framework64\v4.0.30319"
CSC_EXE = os.path.join(FX_DIR, "csc.exe")

REFS = [
    os.path.join(FX_DIR, "WPF", "PresentationFramework.dll"),
    os.path.join(FX_DIR, "WPF", "PresentationCore.dll"),
    os.path.join(FX_DIR, "WPF", "WindowsBase.dll"),
    os.path.join(FX_DIR, "System.Xaml.dll"),
    os.path.join(FX_DIR, "System.Windows.Forms.dll"),
    os.path.join(FX_DIR, "System.Drawing.dll"),
    os.path.join(FX_DIR, "System.Web.Extensions.dll"),
]


def build_native_exe() -> str:
    if not os.path.exists(CSC_EXE):
        raise FileNotFoundError(f"C# compiler not found: {CSC_EXE}")
    for ref in REFS:
        if not os.path.exists(ref):
            raise FileNotFoundError(f"Framework reference missing: {ref}")

    sync()
    build_icons()

    cs_files = sorted(
        os.path.join(NATIVE_DIR, fn)
        for fn in os.listdir(NATIVE_DIR)
        if fn.endswith(".cs")
    )
    if not cs_files:
        raise RuntimeError("No C# source files found under native/")

    cmd = [
        CSC_EXE,
        "/nologo",
        "/utf8output",
        "/target:winexe",
        "/platform:x64",
        "/optimize+",
        "/unsafe",
        f"/win32icon:{ICO_PATH}",
        f"/out:{EXE_PATH}",
    ]
    cmd.extend(f"/r:{r}" for r in REFS)
    cmd.extend(cs_files)

    res = subprocess.run(cmd, cwd=PC_DIR, capture_output=True, text=True, errors="replace")
    if res.returncode != 0:
        raise RuntimeError(f"csc.exe failed ({res.returncode}):\nSTDOUT:\n{res.stdout}\nSTDERR:\n{res.stderr}")

    size = os.path.getsize(EXE_PATH)
    print(f"[SUCCESS] Built native x64 binary: {EXE_PATH} ({size:,} bytes)")
    return EXE_PATH


if __name__ == "__main__":
    build_native_exe()
