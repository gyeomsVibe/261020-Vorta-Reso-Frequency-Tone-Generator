@echo off
setlocal EnableDelayedExpansion
chcp 949 > nul
cd /d "%~dp0"
title [갤럭시 S7 v4.5] Android 8.0 화면오프/잠금화면 관통 안전볼륨 해제·자유볼륨·보르타 5종

echo ===============================================================================
echo  [갤럭시 S7 (SM-G930S, Android 8.0 API 26)] 전수 정상화 및 보르타 5종 최적화
echo  - 1중: 보르타 5종(S7/S6/A30/FreqGen/Aegis) Android 8.0 맞춤 설치 및 AOT speed 컴파일
echo  - 2중: 자유 볼륨 제어(0~15단) + S7VolGuardThread 화면오프/잠금화면 관통 안전볼륨 해제 (85%% 제거)
echo  - 3중: mono_audio=0 스테레오 원복 + 4중 안전볼륨/음장 차단 + 무선고속충전 0/배터리 보호
echo  - 4중: 디바이스 관리 보존 + 비필수 블로트 18종 동결 및 RUN_IN_BACKGROUND/Doze 영구 예외
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

set "FOUND_S7=0"
for /f "skip=1 tokens=1,2" %%S in ('"%ADB%" devices') do (
    if "%%T"=="device" (
        call :OPTIMIZE_S7 %%S
    )
)
if "%FOUND_S7%"=="0" echo [오류] 연결된 갤럭시 S7 SM-G930/G935 기기를 찾지 못했습니다. USB 디버깅을 확인하세요.
goto :END

:OPTIMIZE_S7
set "SERIAL=%~1"
set "MODEL="
set "SDK="
for /f "delims=" %%M in ('"%ADB%" -s %SERIAL% shell getprop ro.product.model 2^>nul') do set "MODEL=%%M"
for /f "delims=" %%K in ('"%ADB%" -s %SERIAL% shell getprop ro.build.version.sdk 2^>nul') do set "SDK=%%K"
echo "!MODEL!" | findstr /I "G930 G935" >nul
if errorlevel 1 exit /b 0

set "FOUND_S7=1"
echo [*] 대상 기기 감지: %SERIAL% (!MODEL!, Android SDK !SDK!)

echo [1/5] 접근성 초기화 및 보르타 5종 APK 설치 (S7 / S6 / A30 / FreqGen / Aegis)...

"%ADB%" -s %SERIAL% shell "settings put secure accessibility_enabled 0 ; settings put secure enabled_accessibility_services '' ; am force-stop com.vorta.s7frequency ; am force-stop com.vorta.s6frequency" >nul 2>&1
if exist "vorta_s7_app\vorta_s7_pro.apk" "%ADB%" -s %SERIAL% push "vorta_s7_app\vorta_s7_pro.apk" /data/local/tmp/vorta_s7_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_s7_pro.apk && rm -f /data/local/tmp/vorta_s7_pro.apk"
if exist "vorta_s6_app\vorta_s6_pro.apk" "%ADB%" -s %SERIAL% push "vorta_s6_app\vorta_s6_pro.apk" /data/local/tmp/vorta_s6_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_s6_pro.apk && rm -f /data/local/tmp/vorta_s6_pro.apk"
if exist "smato_a30_app\smato_a30_pro.apk" "%ADB%" -s %SERIAL% push "smato_a30_app\smato_a30_pro.apk" /data/local/tmp/smato_a30_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/smato_a30_pro.apk && rm -f /data/local/tmp/smato_a30_pro.apk"
if exist "vorta_freqgen_app\vorta_freqgen_pro.apk" "%ADB%" -s %SERIAL% push "vorta_freqgen_app\vorta_freqgen_pro.apk" /data/local/tmp/vorta_freqgen_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_freqgen_pro.apk && rm -f /data/local/tmp/vorta_freqgen_pro.apk"
if exist "vorta_aegis_app\vorta_aegis_pro.apk" "%ADB%" -s %SERIAL% push "vorta_aegis_app\vorta_aegis_pro.apk" /data/local/tmp/vorta_aegis_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_aegis_pro.apk && rm -f /data/local/tmp/vorta_aegis_pro.apk"

echo [2/5] Android 8.0 권한, 백그라운드 실행 허용, Doze 화이트리스트, AOT speed 컴파일...
for %%P in (com.vorta.s7frequency com.vorta.s6frequency com.smato.a30frequency com.vorta.freqgen com.vorta.aegis) do (
    "%ADB%" -s %SERIAL% shell "pm grant %%P android.permission.WRITE_SECURE_SETTINGS ; cmd appops set %%P WRITE_SETTINGS allow ; cmd appops set %%P RUN_IN_BACKGROUND allow ; cmd appops set %%P WAKE_LOCK allow ; dumpsys deviceidle whitelist +%%P ; am set-inactive %%P false ; cmd package compile -m speed -f %%P" >nul 2>&1
)

echo [3/5] 스테레오 순수 음원(mono_audio=0), 자유볼륨 안전해제, 저발열·배터리 보호 적용...
"%ADB%" -s %SERIAL% shell "settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system sound_alive_effect 0 ; settings put system SOUNDALIVE_GENRE_INDEX 0 ; settings put system SOUNDALIVE_AUDIO_PATH 0 ; settings put system k2hd_effect 0 ; settings put system uhqa_effect 0 ; settings put system tube_amp_effect 0 ; settings put system concert_hall_effect 0 ; settings put system hearing_aid 0 ; settings put system amplify_ambient_sound 0 ; settings put system run_amplify_ambient_sound 0 ; settings put global multisound_state 0 ; settings put system sound_effects_enabled 0 ; settings put system dtmf_tone 0 ; settings put system lockscreen_sounds_enabled 0 ; settings put system sip_key_feedback_sound 0 ; settings put system charging_sounds_enabled 0 ; settings put global power_sounds_enabled 0 ; settings put system power_sounds_enabled 0 ; settings put global dock_sounds_enabled 0 ; settings put system dock_sounds_enabled 0 ; settings put global audio_safe_volume_state 2 ; settings put secure audio_safe_volume_state 2 ; settings put secure unsafe_volume_music_active_ms 0 ; settings put system volume_limiter 0 ; settings put system volume_limiter_enable 0 ; settings put system volumelimit_on 0 ; settings put system volume_limiter_value 15 ; settings put system volume_ring 0 ; settings put system volume_notification 0 ; settings put system volume_system 0 ; settings put system volume_alarm 0 ; settings put system wireless_fast_charging 0 ; settings put system adaptive_fast_charging 0 ; settings put system protect_battery 1 ; settings put global protect_battery 1 ; settings put global stay_on_while_plugged_in 0 ; settings put global low_power 0 ; settings put global sem_low_power_mode 0 ; settings put global wifi_scan_always_enabled 0 ; settings put secure screensaver_activate_on_sleep 0 ; settings put secure accessibility_display_magnification_enabled 0" >nul 2>&1

echo [4/5] 무폴링 패시브 S7 안전볼륨 접근성 활성화 및 백그라운드 킬러/블로트 21종 동결...
"%ADB%" -s %SERIAL% shell "settings put secure enabled_accessibility_services com.vorta.s7frequency/com.vorta.s7frequency.S7AccessibilityService ; settings put secure accessibility_enabled 1" >nul 2>&1
"%ADB%" -s %SERIAL% shell "am broadcast -a com.vorta.s7frequency.ACTION_SAFE_VOLUME_CHECK -p com.vorta.s7frequency --include-stopped-packages" >nul 2>&1
for %%B in (com.samsung.faceservice com.samsung.cmh com.samsung.storyservice com.sec.spp.push com.samsung.android.app.simplesharing com.sec.android.diagmonagent com.samsung.android.hmt.vrsvc com.wssyncmldm com.sec.android.soagent com.skt.hps20client com.skp.tstore.startup com.samsung.android.rubin.app com.samsung.android.intelligenceservice2 com.samsung.android.app.aodservice com.samsung.android.game.gametools com.sec.android.app.vepreload com.samsung.android.app.watchmanagerstub com.samsung.android.drivelink.stub) do (
    "%ADB%" -s %SERIAL% shell "timeout 8 pm disable-user --user 0 %%B" >nul 2>&1
)
"%ADB%" -s %SERIAL% shell "am kill-all" >nul 2>&1

echo [5/5] 최종 검증 리포트...
"%ADB%" -s %SERIAL% shell "echo mono=$(settings get system mono_audio) safe=$(settings get global audio_safe_volume_state) a11y=$(settings get secure enabled_accessibility_services); dumpsys package com.vorta.s7frequency | grep -m 1 versionCode; pm list packages | grep -E 'vorta|smato'; grep -E 'MemAvailable|MemFree' /proc/meminfo"
"%ADB%" -s %SERIAL% shell "input keyevent 224 ; am start -n com.vorta.s7frequency/.MainActivity" >nul 2>&1
echo [완료] 갤럭시 S7 Android 8.0 맞춤 최적화 및 보르타 5종 설정이 완료되었습니다.
exit /b 0

:END
if not "%~1"=="--no-pause" pause
exit /b 0
