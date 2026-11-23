@echo off
setlocal EnableDelayedExpansion
chcp 949 > nul
cd /d "%~dp0"
title [갤럭시 S6 v4.5] KT로고 멈춤 복구 + 화면오프 Direct-Binder 안전볼륨 해제·자유볼륨

echo ===============================================================================
echo  [갤럭시 S6 (SM-G920K, Android 7.0 API 24)] 자동복구 및 5종 앱 맞춤 최적화
echo  - 1중: 소프트 부트루프(SIGILL/libLLVM) 또는 72시간 이상 가동 시 자동 콜드재부팅
echo  - 2중: 보르타 5종 전 앱 Android 7.0 맞춤 설치 및 부팅 리시버 S6 단독 격리
echo  - 3중: 접근성 4초 감시 루프 차단 + 오디오 스레드 Binder 폴링 제거
echo  - 4중: AOT speed 컴파일 + 배경 데몬 8종 동결 + Direct-Binder 무팝업 안전볼륨 해제·자유볼륨(0~15단)
echo ===============================================================================
echo.

set "ADB=adb"
if exist "C:\platform-tools\adb.exe" (
    set "ADB=C:\platform-tools\adb.exe"
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
)
if not "%ADB%"=="adb" goto :ADB_OK
where adb >nul 2>&1
if errorlevel 1 (
    echo [오류] adb.exe를 찾을 수 없습니다.
    goto :END
)
:ADB_OK

set "FOUND_S6=0"
for /f "skip=1 tokens=1,2" %%S in ('"%ADB%" devices') do (
    if "%%T"=="device" (
        call :CHECK_AND_RECOVER %%S
    )
)
if "%FOUND_S6%"=="0" echo [오류] 연결된 갤럭시 S6 SM-G920 기기를 찾지 못했습니다. USB 연결 및 디버깅을 확인하세요.
goto :END

:CHECK_AND_RECOVER
set "SERIAL=%~1"
set "MODEL="
set "SDK="
for /f "delims=" %%M in ('"%ADB%" -s %SERIAL% shell getprop ro.product.model 2^>nul') do set "MODEL=%%M"
for /f "delims=" %%K in ('"%ADB%" -s %SERIAL% shell getprop ro.build.version.sdk 2^>nul') do set "SDK=%%K"
echo "!MODEL!" | findstr /I "G920 G925 G928" >nul
if errorlevel 1 exit /b 0

set "FOUND_S6=1"
echo [*] 대상 기기 감지: %SERIAL% (!MODEL!, Android SDK !SDK!)

set "NEED_REBOOT=0"
set "BOOT_OK="
set "BOOTANIM="
set "UPTIME_SEC=0"
for /f "delims=" %%B in ('"%ADB%" -s %SERIAL% shell getprop sys.boot_completed 2^>nul') do set "BOOT_OK=%%B"
for /f "delims=" %%A in ('"%ADB%" -s %SERIAL% shell getprop init.svc.bootanim 2^>nul') do set "BOOTANIM=%%A"
for /f "tokens=1 delims=." %%U in ('"%ADB%" -s %SERIAL% shell cat /proc/uptime 2^>nul') do set "UPTIME_SEC=%%U"

echo [1/5] 상태 진단: boot_completed=!BOOT_OK! / bootanim=!BOOTANIM! / uptime=!UPTIME_SEC!s
if not "!BOOT_OK!"=="1" set "NEED_REBOOT=1"
if /I "!BOOTANIM!"=="running" set "NEED_REBOOT=1"

"%ADB%" -s %SERIAL% shell "service check activity" 2>nul | findstr /I "found" >nul
if errorlevel 1 set "NEED_REBOOT=1"

"%ADB%" -s %SERIAL% logcat -d -b crash 2>nul | findstr /I "SIGILL libLLVM" >nul
if not errorlevel 1 set "NEED_REBOOT=1"

if defined UPTIME_SEC (
    if !UPTIME_SEC! GEQ 259200 (
        echo [!] 연속 가동 72시간^(259200초^) 초과 감지 -> 페이지 캐시 손상 예방 콜드 재부팅 실행
        set "NEED_REBOOT=1"
    )
)

if not "!NEED_REBOOT!"=="1" goto :BOOT_READY
echo [!] 이상 징후 또는 장기 무재부팅 감지 -> 커널 콜드 재부팅 실행 중...
"%ADB%" -s %SERIAL% reboot
"%ADB%" -s %SERIAL% wait-for-device
set /a WAIT_CNT=0
:WAIT_BOOT
set "BOOT_OK="
set "BOOTANIM="
for /f "delims=" %%B in ('"%ADB%" -s %SERIAL% shell getprop sys.boot_completed 2^>nul') do set "BOOT_OK=%%B"
for /f "delims=" %%A in ('"%ADB%" -s %SERIAL% shell getprop init.svc.bootanim 2^>nul') do set "BOOTANIM=%%A"
if "!BOOT_OK!"=="1" if /I "!BOOTANIM!"=="stopped" goto :BOOT_READY
set /a WAIT_CNT+=1
if !WAIT_CNT! GEQ 24 (
    echo [경고] 부팅 대기 시간 초과. 현재 상태에서 안전장치 적용을 시도합니다.
    goto :BOOT_READY
)
timeout /t 5 /nobreak >nul
goto :WAIT_BOOT

:BOOT_READY
echo [2/5] 보르타 시리즈 5종 전 제품군 Android 7.0 맞춤 설치 및 권한·AOT speed 컴파일...
if exist "vorta_s6_app\vorta_s6_pro.apk" "%ADB%" -s %SERIAL% push "vorta_s6_app\vorta_s6_pro.apk" /data/local/tmp/vorta_s6_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_s6_pro.apk && rm -f /data/local/tmp/vorta_s6_pro.apk"
if exist "vorta_s7_app\vorta_s7_pro.apk" "%ADB%" -s %SERIAL% push "vorta_s7_app\vorta_s7_pro.apk" /data/local/tmp/vorta_s7_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_s7_pro.apk && rm -f /data/local/tmp/vorta_s7_pro.apk"
if exist "smato_a30_app\smato_a30_pro.apk" "%ADB%" -s %SERIAL% push "smato_a30_app\smato_a30_pro.apk" /data/local/tmp/smato_a30_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/smato_a30_pro.apk && rm -f /data/local/tmp/smato_a30_pro.apk"
if exist "vorta_freqgen_app\vorta_freqgen_pro.apk" "%ADB%" -s %SERIAL% push "vorta_freqgen_app\vorta_freqgen_pro.apk" /data/local/tmp/vorta_freqgen_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_freqgen_pro.apk && rm -f /data/local/tmp/vorta_freqgen_pro.apk"
if exist "vorta_aegis_app\vorta_aegis_pro.apk" "%ADB%" -s %SERIAL% push "vorta_aegis_app\vorta_aegis_pro.apk" /data/local/tmp/vorta_aegis_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_aegis_pro.apk && rm -f /data/local/tmp/vorta_aegis_pro.apk"

for %%P in (com.vorta.s6frequency com.vorta.s7frequency com.smato.a30frequency com.vorta.freqgen com.vorta.aegis) do (
    "%ADB%" -s %SERIAL% shell "pm grant %%P android.permission.WRITE_SECURE_SETTINGS ; cmd appops set %%P WRITE_SETTINGS allow ; cmd appops set %%P RUN_IN_BACKGROUND allow ; cmd appops set %%P WAKE_LOCK allow ; dumpsys deviceidle whitelist +%%P ; cmd package compile -m speed -f %%P" >nul 2>&1
)

echo [3/5] 무폴링 패시브 안전볼륨 자동확인 접근성 활성화 및 안전볼륨·배터리 보호 설정...
"%ADB%" -s %SERIAL% shell "settings put secure enabled_accessibility_services com.vorta.s6frequency/com.vorta.s6frequency.S6AccessibilityService ; settings put secure accessibility_enabled 1 ; settings put global audio_safe_volume_state 2 ; settings put secure unsafe_volume_music_active_ms 0 ; settings put system volume_limiter 0 ; settings put system volume_limiter_enable 0 ; settings put system volume_limiter_value 15 ; settings put system volumelimit_on 0 ; settings put secure audio_safe_volume_state 2 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put global protect_battery 1 ; settings put system protect_battery 1 ; settings put system adaptive_fast_charging 0 ; settings put global stay_on_while_plugged_in 0 ; settings put system volume_ring 0 ; settings put system volume_notification 0 ; settings put system volume_system 0 ; settings put system sound_effects_enabled 0" >nul 2>&1
"%ADB%" -s %SERIAL% shell "am broadcast -a com.vorta.s6frequency.ACTION_SAFE_VOLUME_CHECK -p com.vorta.s6frequency --include-stopped-packages" >nul 2>&1

echo [4/5] 비필수 배경 미디어/푸시 데몬 8종 동결 및 메모리 정리...
for %%B in (com.samsung.faceservice com.samsung.cmh com.samsung.storyservice com.sec.spp.push com.ktpns.pa com.sec.android.app.sns3 com.samsung.android.app.simplesharing com.sec.android.diagmonagent) do (
    "%ADB%" -s %SERIAL% shell "timeout 8 pm disable-user --user 0 %%B" >nul 2>&1
)
"%ADB%" -s %SERIAL% shell "am kill-all" >nul 2>&1

echo [5/5] 최종 검증 리포트...
"%ADB%" -s %SERIAL% shell "echo boot_completed=$(getprop sys.boot_completed) bootanim=$(getprop init.svc.bootanim) safe=$(settings get global audio_safe_volume_state) a11y=$(settings get secure enabled_accessibility_services); dumpsys package com.vorta.s6frequency | grep -m 1 versionCode; pm list packages | grep -E 'vorta|smato'; grep -E 'MemFree|Cached' /proc/meminfo"
"%ADB%" -s %SERIAL% shell "input keyevent 224 ; am start -n com.vorta.s6frequency/.MainActivity" >nul 2>&1
echo [완료] 갤럭시 S6 정상화 및 보르타 5종 전 앱 맞춤 최적화가 완료되었습니다.
exit /b 0

:END
if not "%~1"=="--no-pause" pause
exit /b 0
