@echo off
chcp 949 >nul
cd /d "%~dp0"
title [보르타 v4.5/v3.2] A30 / S7 / S6 / FreqGen / Aegis 전종 OS 맞춤 설치 및 화면오프 안전볼륨 해제

echo ===============================================================================
echo   [보르타] 갤럭시 A30 / S7 / S6 보르타 5종 전 제품군 맞춤 배포 및 최적화
echo ===============================================================================
echo.

set "PATH=%PATH%;C:\platform-tools;C:\Users\Kimyoongyeom\AppData\Local\Android\Sdk\platform-tools"
where adb >nul 2>&1
if errorlevel 1 (
    echo [ERROR] adb.exe를 찾을 수 없습니다.
    pause
    exit /b 1
)

echo [1/4] 연결된 안드로이드 기기 탐색 중...
adb devices -l
echo.

set DEV_TMP=%TEMP%\adb_devices_list.txt
adb devices > "%DEV_TMP%"

set FOUND=0
for /f "tokens=1,2" %%a in ('type "%DEV_TMP%" ^| findstr /v "List of"') do (
    if "%%b"=="device" (
        set FOUND=1
        call :PROCESS_DEVICE %%a
    )
)
if exist "%DEV_TMP%" del "%DEV_TMP%"

if "%FOUND%"=="0" (
    echo [!] 연결된 기기를 찾지 못했습니다. USB 디버깅 연결 상태를 확인하세요.
)

echo.
echo ===============================================================================
echo   [완료] 모든 연결 기기 보르타 5종 맞춤 설치 및 최적화 완료.
echo ===============================================================================
if not "%~1"=="--no-pause" pause
exit /b 0

:PROCESS_DEVICE
set TARGET=%1
echo -------------------------------------------------------------------------------
echo [*] 타겟 디바이스 처리 중: %TARGET%
for /f "tokens=*" %%m in ('adb -s %TARGET% shell getprop ro.product.model') do set MODEL=%%m
for /f "tokens=*" %%k in ('adb -s %TARGET% shell getprop ro.build.version.sdk') do set SDK=%%k
echo [*] 모델명: %MODEL% (API %SDK%)

echo [2/4] 구형 발생기 앱(com.boedec.hoel.frequencygenerator) 비활성화 및 기존 접근성 정리...
adb -s %TARGET% shell "timeout 8 pm disable-user --user 0 com.boedec.hoel.frequencygenerator" >nul 2>&1
adb -s %TARGET% shell "settings put secure accessibility_enabled 0 ; settings put secure accessibility_display_magnification_enabled 0 ; settings put secure enabled_accessibility_services ''"

echo [3/4] 보르타 시리즈 5종 APK 설치 및 AOT speed 컴파일...
if exist "%~dp0vorta_s6_app\vorta_s6_pro.apk" adb -s %TARGET% install -r -d "%~dp0vorta_s6_app\vorta_s6_pro.apk"
if exist "%~dp0vorta_s7_app\vorta_s7_pro.apk" adb -s %TARGET% install -r -d "%~dp0vorta_s7_app\vorta_s7_pro.apk"
if exist "%~dp0smato_a30_app\smato_a30_pro.apk" adb -s %TARGET% install -r -d "%~dp0smato_a30_app\smato_a30_pro.apk"
if exist "%~dp0vorta_freqgen_app\vorta_freqgen_pro.apk" adb -s %TARGET% install -r -d "%~dp0vorta_freqgen_app\vorta_freqgen_pro.apk"
if exist "%~dp0vorta_aegis_app\vorta_aegis_pro.apk" adb -s %TARGET% install -r -d "%~dp0vorta_aegis_app\vorta_aegis_pro.apk"

for %%P in (com.vorta.s6frequency com.vorta.s7frequency com.smato.a30frequency com.vorta.freqgen com.vorta.aegis) do (
    adb -s %TARGET% shell "pm grant %%P android.permission.WRITE_SECURE_SETTINGS ; cmd appops set %%P WRITE_SETTINGS allow ; cmd appops set %%P RUN_IN_BACKGROUND allow ; cmd appops set %%P WAKE_LOCK allow ; dumpsys deviceidle whitelist +%%P ; am set-inactive %%P false ; cmd package compile -m speed -f %%P" >nul 2>&1
)

echo [4/4] 기기별 자유볼륨·스테레오·안전볼륨 해제 및 맞춤 기동...
echo %MODEL% | findstr /i "A30" >nul
if not errorlevel 1 goto :TARGET_A30
echo %MODEL% | findstr /i "G930 G935" >nul
if not errorlevel 1 goto :TARGET_S7
echo %MODEL% | findstr /i "G920 G925 G928" >nul
if not errorlevel 1 goto :TARGET_S6

adb -s %TARGET% shell "am start -n com.vorta.freqgen/.MainActivity" >nul 2>&1
exit /b 0

:TARGET_A30
adb -s %TARGET% shell "settings put global stay_on_while_plugged_in 0 ; settings put global protect_battery 1 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; am start -n com.smato.a30frequency/.MainActivity" >nul 2>&1
exit /b 0

:TARGET_S7
adb -s %TARGET% shell "settings put secure enabled_accessibility_services com.vorta.s7frequency/com.vorta.s7frequency.S7AccessibilityService ; settings put secure accessibility_enabled 1 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system volume_limiter 0 ; settings put system volume_limiter_enable 0 ; settings put system volumelimit_on 0 ; settings put secure unsafe_volume_music_active_ms 0 ; settings put global audio_safe_volume_state 2 ; settings put secure audio_safe_volume_state 2 ; settings put system volume_limiter_value 15 ; settings put global protect_battery 1 ; settings put system protect_battery 1 ; settings put system adaptive_fast_charging 0 ; settings put system wireless_fast_charging 0 ; settings put global stay_on_while_plugged_in 0 ; settings put global wifi_scan_always_enabled 0 ; am broadcast -a com.vorta.s7frequency.ACTION_SAFE_VOLUME_CHECK -p com.vorta.s7frequency --include-stopped-packages" >nul 2>&1
for %%B in (com.samsung.faceservice com.samsung.cmh com.samsung.storyservice com.sec.spp.push com.samsung.android.app.simplesharing com.sec.android.diagmonagent com.samsung.android.hmt.vrsvc com.wssyncmldm com.sec.android.soagent com.skt.hps20client com.skp.tstore.startup com.samsung.android.rubin.app com.samsung.android.intelligenceservice2 com.samsung.android.app.aodservice com.samsung.android.game.gametools com.sec.android.app.vepreload com.samsung.android.app.watchmanagerstub com.samsung.android.drivelink.stub) do (
    adb -s %TARGET% shell "timeout 8 pm disable-user --user 0 %%B" >nul 2>&1
)
adb -s %TARGET% shell "am kill-all ; am start -n com.vorta.s7frequency/.MainActivity" >nul 2>&1
exit /b 0

:TARGET_S6
adb -s %TARGET% shell "settings put secure enabled_accessibility_services com.vorta.s6frequency/com.vorta.s6frequency.S6AccessibilityService ; settings put secure accessibility_enabled 1 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system volume_limiter 0 ; settings put system volume_limiter_enable 0 ; settings put system volumelimit_on 0 ; settings put secure unsafe_volume_music_active_ms 0 ; settings put global audio_safe_volume_state 2 ; settings put global protect_battery 1 ; settings put system protect_battery 1 ; settings put system adaptive_fast_charging 0 ; settings put global stay_on_while_plugged_in 0 ; settings put system volume_limiter_value 15 ; am broadcast -a com.vorta.s6frequency.ACTION_SAFE_VOLUME_CHECK -p com.vorta.s6frequency --include-stopped-packages ; am start -n com.vorta.s6frequency/.MainActivity" >nul 2>&1
exit /b 0
