@echo off
setlocal EnableDelayedExpansion
chcp 949 > nul
cd /d "%~dp0"
title [보르타 레조 v4.5] A30 / S7 / S6 / FreqGen / Aegis 전제품군 OS 맞춤 설치 및 안전장치

echo ===============================================================================
echo  [보르타 레조 v4.5] 보르타 5종 전 제품군 기기 OS 맞춤 최적화 설치
echo  - 대상 앱: vorta_s6, vorta_s7, smato_a30, vorta_freqgen, vorta_aegis (5종)
echo  - S6(Direct-Binder) / S7(화면오프 잠금화면 관통): 자유볼륨(0~15단, 85%% 제거) + VolGuard
echo  - A30(Android 11): 순수 정현파 음향효과 영구고정 (볼륨 미개입)
echo ===============================================================================
echo.
set "ADB=adb"
if exist "C:\platform-tools\adb.exe" (
    set "ADB=C:\platform-tools\adb.exe"
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
)
"%ADB%" reconnect offline >nul 2>&1
"%ADB%" devices
echo.
set "FOUND_ANY=0"
for /f "skip=1 tokens=1,2" %%S in ('"%ADB%" devices') do (
    if "%%T"=="device" (
        set "FOUND_ANY=1"
        call :PROCESS_DEVICE %%S
    )
)
if "%FOUND_ANY%"=="0" (
    echo [오류] 연결된 스마트폰을 찾을 수 없습니다! USB 케이블과 USB 디버깅을 확인해 주세요.
    goto :END
)
echo.
echo ===============================================================================
echo  [완료] 보르타 5종 앱 맞춤 설치, 권한 부여, AOT 최적화 및 안전장치가 완료되었습니다.
echo ===============================================================================
goto :END

:PROCESS_DEVICE
set "SERIAL=%~1"
set "MODEL="
set "SDK="
for /f "delims=" %%M in ('"%ADB%" -s %SERIAL% shell getprop ro.product.model 2^>nul') do set "MODEL=%%M"
for /f "delims=" %%K in ('"%ADB%" -s %SERIAL% shell getprop ro.build.version.sdk 2^>nul') do set "SDK=%%K"
echo [*] 연결 기기: %SERIAL% (%MODEL%, API %SDK%)

echo "!MODEL!" | findstr /I "A30" >nul
if !errorlevel! equ 0 goto :PREP_A30
echo [*] [1/5] S7/S6 안전볼륨 락 해제, 스테레오 원복(mono_audio=0) 및 배터리/저발열 보호...
"%ADB%" -s %SERIAL% shell "settings put global audio_safe_volume_state 2 ; settings put secure unsafe_volume_music_active_ms 0 ; settings put secure audio_safe_volume_state 2 ; settings put system volume_limiter 0 ; settings put system volume_limiter_enable 0 ; settings put system volumelimit_on 0 ; settings put system volume_limiter_value 15 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system sound_alive_effect 0 ; settings put system sound_effects_enabled 0 ; settings put system power_sounds_enabled 0 ; settings put global power_sounds_enabled 0 ; settings put global protect_battery 1 ; settings put system protect_battery 1 ; settings put system adaptive_fast_charging 0 ; settings put system wireless_fast_charging 0 ; settings put global stay_on_while_plugged_in 0 ; settings put global wifi_scan_always_enabled 0" 2>nul
goto :PREP_DONE

:PREP_A30
echo [*] [1/5] A30 전용: 음질·음향효과 순수 정현파 영구 고정 및 배터리 보호 ^(볼륨 미조작^)...
"%ADB%" -s %SERIAL% shell "settings put system sound_alive_effect 0 ; settings put system SOUNDALIVE_GENRE_INDEX 0 ; settings put system SOUNDALIVE_AUDIO_PATH 0 ; settings put system k2hd_effect 0 ; settings put system uhqa_effect 0 ; settings put system tube_amp_effect 0 ; settings put system concert_hall_effect 0 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system master_balance 0.0 ; settings put system sound_balance 0.0 ; settings put system hearing_aid 0 ; settings put system amplify_ambient_sound 0 ; settings put system run_amplify_ambient_sound 0 ; settings put global multisound_state 0 ; settings put system sound_effects_enabled 0 ; settings put system dtmf_tone 0 ; settings put system lockscreen_sounds_enabled 0 ; settings put system sip_key_feedback_sound 0 ; settings put system haptic_feedback_enabled 0 ; settings put system charging_sounds_enabled 0 ; settings put secure charging_sounds_enabled 0 ; settings put global charging_sounds_enabled 0 ; settings put global protect_battery 1 ; settings put system adaptive_fast_charging 0 ; settings put global stay_on_while_plugged_in 0 ; settings put global sem_low_power_mode 0 ; settings put global low_power 0 ; settings put global wifi_sleep_policy 2" 2>nul
"%ADB%" -s %SERIAL% shell "timeout 8 pm disable-user --user 0 com.sec.android.app.soundalive ; timeout 8 pm disable-user --user 0 com.sec.hearingadjust" 2>nul

:PREP_DONE
echo [*] [2/5] 기존 폴링 접근성 및 구형 발생기 앱 초기화...
"%ADB%" -s %SERIAL% shell "timeout 8 pm disable-user --user 0 com.boedec.hoel.frequencygenerator ; settings put secure accessibility_enabled 0 ; settings put secure accessibility_display_magnification_enabled 0 ; settings put secure enabled_accessibility_services ''" 2>nul

echo [*] [3/5] 보르타 시리즈 5종 전 제품군 맞춤 APK 설치...
if exist "vorta_s6_app\vorta_s6_pro.apk" "%ADB%" -s %SERIAL% push "vorta_s6_app\vorta_s6_pro.apk" /data/local/tmp/vorta_s6_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_s6_pro.apk && rm -f /data/local/tmp/vorta_s6_pro.apk"
if exist "vorta_s7_app\vorta_s7_pro.apk" "%ADB%" -s %SERIAL% push "vorta_s7_app\vorta_s7_pro.apk" /data/local/tmp/vorta_s7_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_s7_pro.apk && rm -f /data/local/tmp/vorta_s7_pro.apk"
if exist "smato_a30_app\smato_a30_pro.apk" "%ADB%" -s %SERIAL% push "smato_a30_app\smato_a30_pro.apk" /data/local/tmp/smato_a30_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/smato_a30_pro.apk && rm -f /data/local/tmp/smato_a30_pro.apk"
if exist "vorta_freqgen_app\vorta_freqgen_pro.apk" "%ADB%" -s %SERIAL% push "vorta_freqgen_app\vorta_freqgen_pro.apk" /data/local/tmp/vorta_freqgen_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_freqgen_pro.apk && rm -f /data/local/tmp/vorta_freqgen_pro.apk"
if exist "vorta_aegis_app\vorta_aegis_pro.apk" "%ADB%" -s %SERIAL% push "vorta_aegis_app\vorta_aegis_pro.apk" /data/local/tmp/vorta_aegis_pro.apk >nul && "%ADB%" -s %SERIAL% shell "pm install -r -d /data/local/tmp/vorta_aegis_pro.apk && rm -f /data/local/tmp/vorta_aegis_pro.apk"

echo [*] [4/5] 5종 앱 권한 부여, 배터리 절전 예외 및 AOT speed 컴파일...
for %%P in (com.vorta.s6frequency com.vorta.s7frequency com.smato.a30frequency com.vorta.freqgen com.vorta.aegis) do (
    "%ADB%" -s %SERIAL% shell "pm grant %%P android.permission.WRITE_SECURE_SETTINGS ; pm grant %%P android.permission.WRITE_SETTINGS ; cmd appops set %%P WRITE_SETTINGS allow ; cmd appops set %%P RUN_IN_BACKGROUND allow ; cmd appops set %%P WAKE_LOCK allow ; dumpsys deviceidle whitelist +%%P ; am set-inactive %%P false ; cmd package compile -m speed -f %%P" >nul 2>&1
)

echo "!MODEL!" | findstr /I "G920 G925 G928" >nul
if not errorlevel 1 goto :FINISH_S6
echo "!MODEL!" | findstr /I "G930 G935" >nul
if not errorlevel 1 goto :FINISH_S7
echo "!MODEL!" | findstr /I "A30" >nul
if not errorlevel 1 goto :FINISH_A30

"%ADB%" -s %SERIAL% shell "input keyevent 224 ; am start -n com.vorta.freqgen/.MainActivity" 2>nul
exit /b 0

:FINISH_S6
echo [*] [5/5] 갤럭시 S6 감지: 무폴링 패시브 안전볼륨 접근성 활성화, 배경 데몬 8종 동결 및 자유볼륨 유지...
for %%B in (com.samsung.faceservice com.samsung.cmh com.samsung.storyservice com.sec.spp.push com.ktpns.pa com.sec.android.app.sns3 com.samsung.android.app.simplesharing com.sec.android.diagmonagent) do (
    "%ADB%" -s %SERIAL% shell "timeout 8 pm disable-user --user 0 %%B" >nul 2>&1
)
"%ADB%" -s %SERIAL% shell "am kill-all ; settings put secure enabled_accessibility_services com.vorta.s6frequency/com.vorta.s6frequency.S6AccessibilityService ; settings put secure accessibility_enabled 1 ; am broadcast -a com.vorta.s6frequency.ACTION_SAFE_VOLUME_CHECK -p com.vorta.s6frequency --include-stopped-packages ; input keyevent 224 ; am start -n com.vorta.s6frequency/.MainActivity" 2>nul
exit /b 0

:FINISH_S7
echo [*] [5/5] 갤럭시 S7 감지: 무폴링 패시브 S7 접근성 활성화, 배경 데몬/블로트 18종 동결 및 자유볼륨 유지...
for %%B in (com.samsung.faceservice com.samsung.cmh com.samsung.storyservice com.sec.spp.push com.samsung.android.app.simplesharing com.sec.android.diagmonagent com.samsung.android.hmt.vrsvc com.wssyncmldm com.sec.android.soagent com.skt.hps20client com.skp.tstore.startup com.samsung.android.rubin.app com.samsung.android.intelligenceservice2 com.samsung.android.app.aodservice com.samsung.android.game.gametools com.sec.android.app.vepreload com.samsung.android.app.watchmanagerstub com.samsung.android.drivelink.stub) do (
    "%ADB%" -s %SERIAL% shell "timeout 8 pm disable-user --user 0 %%B" >nul 2>&1
)
"%ADB%" -s %SERIAL% shell "am kill-all ; settings put secure enabled_accessibility_services com.vorta.s7frequency/com.vorta.s7frequency.S7AccessibilityService ; settings put secure accessibility_enabled 1 ; am broadcast -a com.vorta.s7frequency.ACTION_SAFE_VOLUME_CHECK -p com.vorta.s7frequency --include-stopped-packages ; input keyevent 224 ; am start -n com.vorta.s7frequency/.MainActivity" 2>nul
exit /b 0

:FINISH_A30
echo [*] [5/5] 갤럭시 A30 감지: smato_a30 실행...
"%ADB%" -s %SERIAL% shell "input keyevent 224 ; am start -n com.smato.a30frequency/.MainActivity" 2>nul
exit /b 0

:END
if not "%~1"=="--no-pause" pause
exit /b 0
