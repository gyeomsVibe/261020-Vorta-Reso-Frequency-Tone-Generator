# deploy_note20u.ps1 - Galaxy Note 20 Ultra 5G Deployment & Verification Script
$ErrorActionPreference = "Stop"

$ADB = "C:\Users\Kimyoongyeom\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$DEV_ID = "R3CN800MV8R"
$APK_PATH = "D:\D_Workspace_NB\주파수\vorta_aegis_app\vorta_aegis_pro.apk"
$PKG = "com.vorta.aegis"
$ACTIVITY = "$PKG/.MainActivity"

Write-Host "=================================================="
Write-Host "📱 Deploying Vorta Aegis Pro to Galaxy Note 20 Ultra"
Write-Host "Target Device: $DEV_ID (SM-N986N, Android 13)"
Write-Host "=================================================="

# 1. Check Device
$devices = & $ADB devices -l | Out-String
if ($devices -notmatch $DEV_ID) {
    Write-Error "Device $DEV_ID not found in adb devices!"
}
Write-Host "[1/5] Target Device Confirmed Active."

# 2. Install APK
Write-Host "[2/5] Installing APK: $APK_PATH..."
$installRes = & $ADB -s $DEV_ID install -r $APK_PATH | Out-String
Write-Host $installRes
if ($installRes -notmatch "Success") {
    Write-Error "APK installation failed: $installRes"
}

# 3. Launch App
Write-Host "[3/5] Launching $ACTIVITY..."
$launchRes = & $ADB -s $DEV_ID shell am start -n $ACTIVITY
Write-Host $launchRes

Start-Sleep -Seconds 2

# 4. Verify Process
Write-Host "[4/5] Checking running process..."
$pidCheck = & $ADB -s $DEV_ID shell pidof $PKG
if (-not $pidCheck) {
    Write-Error "Process $PKG is not running!"
}
Write-Host "PID: $pidCheck - Process is ACTIVE!"

# 5. Capture Screenshot for Verification
Write-Host "[5/5] Capturing screen verification..."
& $ADB -s $DEV_ID shell screencap -p /sdcard/aegis_verify.png
& $ADB -s $DEV_ID pull /sdcard/aegis_verify.png "D:\D_Workspace_NB\주파수\note20u_aegis_screen.png"
& $ADB -s $DEV_ID shell rm /sdcard/aegis_verify.png

Write-Host "=================================================="
Write-Host "🎉 [VERIFIED] Vorta Aegis Pro Successfully Running on Note 20 Ultra!"
Write-Host "Screenshot saved to: D:\D_Workspace_NB\주파수\note20u_aegis_screen.png"
Write-Host "=================================================="
