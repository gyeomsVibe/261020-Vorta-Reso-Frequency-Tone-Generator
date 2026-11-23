@echo off
setlocal EnableDelayedExpansion
chcp 949 > nul
cd /d "%~dp0"
title [보르타 통합 v5.2 / Pro v3.3] OS 버전별 맞춤 APK 자동 배포

echo ===============================================================================
echo  [보르타 통합 v5.2] S6(API 24) / S7(API 26) / A30(API 30) OS 버전별 맞춤 설치
echo  - 보르타 이지스(vorta_aegis_app) 제외 4종 병합 앱 + Vorta Pro v3.3 동시 배포
echo  - 48kHz 32-bit Float / -1.0 dBTP 순수 정현파 + 좌측 상단 아코디언 메뉴
echo  - 자유볼륨 0~15단 보존 (85%% 강제고정 없음, A30 볼륨 무개입)
echo ===============================================================================

set "ADB="
if exist "C:\platform-tools\adb.exe" set "ADB=C:\platform-tools\adb.exe"
if "%ADB%"=="" (
    for /f "delims=" %%I in ('where adb 2^>nul') do if "!ADB!"=="" set "ADB=%%I"
)
if "%ADB%"=="" (
    echo [오류] adb.exe를 찾을 수 없습니다. C:\platform-tools\adb.exe 경로를 확인하세요.
    pause
    exit /b 1
)

set "COUNT=0"
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        set /a COUNT+=1
        set "SER=%%A"
        for /f "delims=" %%M in ('"%ADB%" -s !SER! shell getprop ro.product.model') do set "MODEL=%%M"
        for /f "delims=" %%S in ('"%ADB%" -s !SER! shell getprop ro.build.version.sdk') do set "SDK=%%S"
        echo.
        echo [*] 대상 기기: !SER! (!MODEL!, Android SDK !SDK!)

        set "UNIFIED_APK="
        set "TARGET_TYPE=PERSONAL"
        echo !MODEL! | findstr /I "G920 G925 G928" >nul && (
            set "UNIFIED_APK=vorta_unified_app\vorta_unified_s6_api24.apk"
            set "TARGET_TYPE=S6"
        )
        echo !MODEL! | findstr /I "G930 G935" >nul && (
            set "UNIFIED_APK=vorta_unified_app\vorta_unified_s7_api26.apk"
            set "TARGET_TYPE=S7"
        )
        echo !MODEL! | findstr /I "A30" >nul && (
            set "UNIFIED_APK=vorta_unified_app\vorta_unified_a30_api30.apk"
            set "TARGET_TYPE=A30"
        )

        if "!UNIFIED_APK!"=="" (
            echo [안내] 개인용/미등록 기기(!MODEL!) -> 안전 보호 모드 (권한·볼륨 미변경)
            if exist "vorta_freqgen_app\vorta_freqgen_pro.apk" (
                "%ADB%" -s !SER! install -r -d "vorta_freqgen_app\vorta_freqgen_pro.apk"
            )
        ) else (
            echo [1/4] 통합 앱(!UNIFIED_APK!) 및 Vorta Pro v3.3 설치...
            "%ADB%" -s !SER! install -r -d "!UNIFIED_APK!"
            if exist "vorta_freqgen_app\vorta_freqgen_pro.apk" (
                "%ADB%" -s !SER! install -r -d "vorta_freqgen_app\vorta_freqgen_pro.apk"
            )

            echo [2/4] 권한, Doze 예외, AOT speed 컴파일 적용...
            for %%P in (com.vorta.unified com.vorta.freqgen) do (
                "%ADB%" -s !SER! shell "pm grant %%P android.permission.WRITE_SECURE_SETTINGS ; cmd appops set %%P WRITE_SETTINGS allow ; cmd appops set %%P RUN_IN_BACKGROUND allow ; cmd appops set %%P WAKE_LOCK allow ; dumpsys deviceidle whitelist +%%P ; cmd package compile -m speed -f %%P" >nul 2>&1
            )

            echo [3/4] 기기별 OS 맞춤 안전장치 구성 (!TARGET_TYPE!)...
            if "!TARGET_TYPE!"=="S6" (
                "%ADB%" -s !SER! shell "settings put secure enabled_accessibility_services com.vorta.unified/com.vorta.unified.VortaAccessibilityService ; settings put secure accessibility_enabled 1 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system volumelimit_on 0 ; settings put global audio_safe_volume_state 2 ; am broadcast -a com.vorta.unified.ACTION_SAFE_VOLUME_CHECK -p com.vorta.unified --include-stopped-packages" >nul 2>&1
            )
            if "!TARGET_TYPE!"=="S7" (
                "%ADB%" -s !SER! shell "settings put secure enabled_accessibility_services com.vorta.unified/com.vorta.unified.VortaAccessibilityService ; settings put secure accessibility_enabled 1 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system volumelimit_on 0 ; settings put global audio_safe_volume_state 2 ; am broadcast -a com.vorta.unified.ACTION_SAFE_VOLUME_CHECK -p com.vorta.unified --include-stopped-packages" >nul 2>&1
            )
            if "!TARGET_TYPE!"=="A30" (
                "%ADB%" -s !SER! shell "settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system sound_alive_effect 0 ; settings put system uhqa_effect 0 ; settings put system tube_amp_effect 0 ; settings put system k2hd_effect 0 ; settings put system concert_hall_effect 0 ; settings put system dolby_atmos_mode 0 ; settings put global dolby_atmos_mode 0 ; settings put global multisound_state 0" >nul 2>&1
            )

            echo [4/4] 보르타 통합 v5.2 기동 및 버전 검증...
            "%ADB%" -s !SER! shell "dumpsys package com.vorta.unified | grep -m 1 versionCode ; dumpsys package com.vorta.freqgen | grep -m 1 versionCode ; am start -n com.vorta.unified/.MainActivity"
        )
    )
)

if "%COUNT%"=="0" (
    echo [오류] USB 디버깅으로 연결된 안드로이드 기기를 찾지 못했습니다.
    pause
    exit /b 1
)

echo.
echo ===============================================================================
echo  [완료] 보르타 통합 v5.2 및 Pro v3.3 배포가 완료되었습니다.
echo ===============================================================================
pause
exit /b 0
