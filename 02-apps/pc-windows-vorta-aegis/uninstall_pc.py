import base64
import json
import subprocess


def main():
    ps = '''
$desktop = [Environment]::GetFolderPath('Desktop')
$programs = [Environment]::GetFolderPath('Programs')
$removed = @()
foreach ($p in @((Join-Path $desktop '보르타 이지스.lnk'), (Join-Path $programs '보르타 이지스.lnk'))) {
    if (Test-Path $p) {
        Remove-Item -Force $p
        $removed += $p
    }
}
@{"ok"=$true; "removed"=$removed} | ConvertTo-Json -Compress
'''
    encoded = base64.b64encode(ps.encode("utf-16le")).decode("ascii")
    out = subprocess.check_output(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded],
        text=True,
        encoding="utf-8",
    )
    print(out.strip())


if __name__ == "__main__":
    main()
