# [보르타 레조] A30 · S7 · S6 스마트폰 자동 모델 감지 및 1:1 맞춤 개조 엔진
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host " [보르타 레조] A30 · S7 · S6 스마트폰 자동 모델 감지 및 1:1 전용기기 개조" -ForegroundColor Yellow
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host " [1] 연결된 모든 스마트폰의 모델명을 adb로 자동 식별합니다." -ForegroundColor Gray
Write-Host " [2] A30 / S7 / S6 각 모델에 선별된 1:1 맞춤 설정 및 전용 APK만 설치합니다." -ForegroundColor Gray
Write-Host " [3] 배터리 스웰링(팽창) 방지 85% 보호 충전 및 완속 충전, 화면 슬립을 주입합니다." -ForegroundColor Gray
Write-Host " [4] 통신사/전화/연락처/DMB 등 불필요 앱을 동결하여 리소스를 100% 확보합니다." -ForegroundColor Gray
Write-Host ""

# 1. ADB 경로 탐색
$adb = "adb"
$sdkAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (Test-Path $sdkAdb) {
    $adb = $sdkAdb
} elseif (Test-Path "C:\platform-tools\adb.exe") {
    $adb = "C:\platform-tools\adb.exe"
}

Write-Host "ADB 실행 경로: $adb" -ForegroundColor Green
Write-Host "연결된 안드로이드 기기를 검색 중입니다..." -ForegroundColor Green
Write-Host ""

# 2. 연결된 기기 리스트 파싱
$devicesOutput = & $adb devices
$deviceList = @()

foreach ($line in $devicesOutput) {
    if ($line -match "^([A-Za-z0-9_-]+)\s+device$") {
        $deviceList += $matches[1]
    }
}

if ($deviceList.Count -eq 0) {
    Write-Host "[경고] 연결된 안드로이드 기기가 없습니다!" -ForegroundColor Red
    Write-Host "스마트폰을 USB로 연결하고 'USB 디버깅'을 켜주세요." -ForegroundColor Yellow
    exit 1
}

Write-Host "총 $($deviceList.Count)대의 기기가 감지되었습니다." -ForegroundColor Cyan
Write-Host ""

# 3. 공통 설정 주입 함수 (배터리 스웰링 방지 및 무음화 - A30 안전볼륨 무간섭 원칙 엄수)
function Apply-DeviceTuning($devId) {
    Write-Host "  -> 시스템 무음화, 배터리 85% 보호, 완속 충전, 화면 슬립, 스테레오(master_mono=0) 주입 중..." -ForegroundColor Gray
    & $adb -s $devId shell "settings put system master_mono 0" 2>$null
    & $adb -s $devId shell "settings put system sound_effects_enabled 0" 2>$null
    & $adb -s $devId shell "settings put system dtmf_tone 0" 2>$null
    & $adb -s $devId shell "settings put global charging_sounds_enabled 0" 2>$null
    & $adb -s $devId shell "settings put global stay_on_while_plugged_in 0" 2>$null
    & $adb -s $devId shell "settings put global protect_battery 1" 2>$null
    & $adb -s $devId shell "settings put system adaptive_fast_charging 0" 2>$null
    & $adb -s $devId shell "media volume --stream 1 --set 0" 2>$null
    & $adb -s $devId shell "media volume --stream 2 --set 0" 2>$null
    & $adb -s $devId shell "media volume --stream 5 --set 0" 2>$null
}

# 4. 불필요 앱 동결/삭제 함수 (통신사, 에이닷, T전화, 연락처 등)
function Debloat-Packages($devId) {
    Write-Host "  -> 통신사(SKT/KT/LGU+), 에이닷, T전화, 연락처, 메시지, DMB, 라디오 동결 중..." -ForegroundColor Gray
    $packages = @(
        "com.skt.prod.dialer",
        "com.skt.t_smart_charge",
        "com.samsung.android.app.contacts",
        "com.sec.phone",
        "com.android.phone",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.messaging",
        "com.samsung.android.app.dtv.dmb",
        "com.sec.android.app.fm",
        "com.sec.android.app.popupcalculator",
        "com.sec.android.daemonapp",
        "com.sec.android.app.soundalive",
        "com.sec.hearingadjust",
        "Com.sktelecom.minit",
        "com.skt.skaf.OA00018282",
        "com.skt.skaf.OA00199800",
        "com.skt.skaf.OA00412131",
        "com.skt.skaf.A000Z00040",
        "com.sktelecom.smartcard.SmartcardService",
        "com.kt.olleh.customercare",
        "com.kt.show.cs",
        "com.kt.service.cs",
        "uplus.membership",
        "com.lguplus.mobile.cs",
        "com.lguplus.smartpay"
    )
    foreach ($pkg in $packages) {
        & $adb -s $devId shell "pm uninstall -k --user 0 $pkg" 2>$null
        & $adb -s $devId shell "pm disable-user --user 0 $pkg" 2>$null
    }
}

# 5. 각 기기별 1:1 맞춤 모델 판별 및 작업 수행
foreach ($devId in $deviceList) {
    $model = (& $adb -s $devId shell getprop ro.product.model).Trim()
    Write-Host "-------------------------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "[기기 감지] 시리얼: $devId | 모델명: $model" -ForegroundColor Green
    Write-Host "-------------------------------------------------------------------------------" -ForegroundColor DarkGray

    # 공통 최적화 및 배터리 스웰링 방지
    Apply-DeviceTuning -devId $devId
    Debloat-Packages -devId $devId

    # 모델별 1:1 맞춤 분기
    if ($model -match "A305|A30") {
        Write-Host ">>> [Galaxy A30 매칭] 스마토 앰프 #1 (120W 모노) 전용 프로필 적용" -ForegroundColor Yellow
        $apkPath = "$PSScriptRoot\smato_a30_app\smato_a30_pro.apk"
        if (Test-Path $apkPath) {
            Write-Host "  -> A30 전용 APK (v4.0) 설치 중..." -ForegroundColor Cyan
            & $adb -s $devId install -r -d $apkPath
            & $adb -s $devId shell "am force-stop com.smato.a30frequency" 2>$null
            & $adb -s $devId shell "am start -n com.smato.a30frequency/.MainActivity" 2>$null
            Write-Host "  -> [성공] 보르타 레조 A30 v4.0 앱 실행 완료!" -ForegroundColor Green
        } else {
            Write-Host "  -> [오류] A30 APK를 찾을 수 없습니다: $apkPath" -ForegroundColor Red
        }
    }
    elseif ($model -match "G930|S7") {
        Write-Host ">>> [Galaxy S7 매칭] 스마토 앰프 #2 (240W 2채널 진동스피커 L/R) 전용 프로필 적용" -ForegroundColor Yellow
        & $adb -s $devId shell "pm uninstall local.s7.wiredgain" 2>$null
        & $adb -s $devId shell "pm uninstall com.boedec.hoel.frequencygenerator" 2>$null
        & $adb -s $devId shell "pm uninstall com.vorta.s7safevolumeoverlay" 2>$null
        & $adb -s $devId shell "settings put global audio_safe_volume_state 2" 2>$null
        & $adb -s $devId shell "settings put global safe_media_volume_active 0" 2>$null
        & $adb -s $devId shell "settings put global safe_media_volume_enabled 0" 2>$null
        & $adb -s $devId shell "settings put secure unsafe_volume_music_active_ms 0" 2>$null
        & $adb -s $devId shell "settings put system volume_limiter_enable 0" 2>$null
        & $adb -s $devId shell "settings put system volume_limiter_value 15" 2>$null
        $apkPath = "$PSScriptRoot\vorta_s7_app\vorta_s7_pro.apk"
        if (Test-Path $apkPath) {
            Write-Host "  -> S7 전용 APK (v4.0) 설치 중..." -ForegroundColor Cyan
            & $adb -s $devId install -r -d $apkPath
            & $adb -s $devId shell "pm grant com.vorta.s7frequency android.permission.WRITE_SECURE_SETTINGS" 2>$null
            & $adb -s $devId shell "appops set com.vorta.s7frequency WRITE_SETTINGS allow" 2>$null
            & $adb -s $devId shell "settings put secure enabled_accessibility_services com.vorta.s7frequency/.S7AccessibilityService" 2>$null
            & $adb -s $devId shell "settings put secure accessibility_enabled 1" 2>$null
            & $adb -s $devId shell "am force-stop com.vorta.s7frequency" 2>$null
            & $adb -s $devId shell "am start -n com.vorta.s7frequency/.MainActivity" 2>$null
            Write-Host "  -> [성공] 보르타 레조 S7 v4.0 앱 실행 완료!" -ForegroundColor Green
        } else {
            Write-Host "  -> [오류] S7 APK를 찾을 수 없습니다: $apkPath" -ForegroundColor Red
        }
    }
    elseif ($model -match "G920|S6") {
        Write-Host ">>> [Galaxy S6 매칭] 트랜스2 (Lepy LP-168S BTL 68W 독립 우퍼) 전용 프로필 적용" -ForegroundColor Yellow
        & $adb -s $devId shell "settings put global audio_safe_volume_state 2" 2>$null
        & $adb -s $devId shell "settings put global safe_media_volume_active 0" 2>$null
        & $adb -s $devId shell "settings put global safe_media_volume_enabled 0" 2>$null
        & $adb -s $devId shell "settings put secure unsafe_volume_music_active_ms 0" 2>$null
        & $adb -s $devId shell "settings put system volume_limiter_enable 0" 2>$null
        & $adb -s $devId shell "settings put system volume_limiter_value 15" 2>$null
        $apkPath = "$PSScriptRoot\vorta_s6_app\vorta_s6_pro.apk"
        if (Test-Path $apkPath) {
            Write-Host "  -> S6 전용 APK (v4.0) 설치 중..." -ForegroundColor Cyan
            & $adb -s $devId install -r -d $apkPath
            & $adb -s $devId shell "pm grant com.vorta.s6frequency android.permission.WRITE_SECURE_SETTINGS" 2>$null
            & $adb -s $devId shell "appops set com.vorta.s6frequency WRITE_SETTINGS allow" 2>$null
            & $adb -s $devId shell "settings put secure enabled_accessibility_services com.vorta.s6frequency/.S6AccessibilityService" 2>$null
            & $adb -s $devId shell "settings put secure accessibility_enabled 1" 2>$null
            & $adb -s $devId shell "am force-stop com.vorta.s6frequency" 2>$null
            & $adb -s $devId shell "am start -n com.vorta.s6frequency/.MainActivity" 2>$null
            Write-Host "  -> [성공] 보르타 레조 S6 v4.0 앱 실행 완료!" -ForegroundColor Green
        } else {
            Write-Host "  -> [오류] S6 APK를 찾을 수 없습니다: $apkPath" -ForegroundColor Red
        }
    }
    else {
        Write-Host "[주의] 미등록 기기 ($model) 입니다. 다른 기기 전용 APK는 설치하지 않고 환경설정만 완료했습니다." -ForegroundColor DarkYellow
    }
    Write-Host ""
}

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host " [완료] 선별된 기기별 1:1 맞춤형 보르타 레조 설정 및 전용 APK 배포 완료!" -ForegroundColor Green
Write-Host "===============================================================================" -ForegroundColor Cyan
