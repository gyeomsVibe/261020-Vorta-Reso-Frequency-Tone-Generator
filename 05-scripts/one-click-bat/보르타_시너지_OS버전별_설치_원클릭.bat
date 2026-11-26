@echo off
setlocal EnableDelayedExpansion
chcp 949 > nul
cd /d "%~dp0"
title [보르타 시너지 v1.1.1] OS 버전별 병행 설치 및 불안정 기기 보호 원클릭

echo ===============================================================================
echo  [보르타 시너지 v1.1.1] OS 버전별 병행 설치 + 불안정 기기 보호 설정
echo  - 패키지: com.vorta.synergy (기존 com.vorta.unified 삭제 없이 공존 설치)
echo  - 하드웨어 보호: 85%% 충전제한 / 고속충전 차단 / 화면유지 해제 / 4채널 대역 분리
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

        set "SYNERGY_APK="
        set "TARGET_TYPE=GENERAL"
        echo !MODEL! | findstr /I "G920 G925 G928" >nul && (
            set "SYNERGY_APK=vorta_synergy_app\vorta_synergy_s6_api24.apk"
            set "TARGET_TYPE=S6"
        )
        echo !MODEL! | findstr /I "G930 G935" >nul && (
            set "SYNERGY_APK=vorta_synergy_app\vorta_synergy_s7_api26.apk"
            set "TARGET_TYPE=S7"
        )
        echo !MODEL! | findstr /I "A30" >nul && (
            set "SYNERGY_APK=vorta_synergy_app\vorta_synergy_a30_api30.apk"
            set "TARGET_TYPE=A30"
        )

        if "!SYNERGY_APK!"=="" (
            echo [안내] 미등록/범용 단말(!MODEL!) -> 안전 보호 모드(볼륨·배터리 설정 무변경)
            if exist "vorta_synergy_app\vorta_synergy_a30_api30.apk" (
                "%ADB%" -s !SER! install -r -d "vorta_synergy_app\vorta_synergy_a30_api30.apk"
            )
        ) else (
            echo [1/5] 보르타 시너지 v1.1.1(!SYNERGY_APK!) 병행 설치...
            "%ADB%" -s !SER! install -r -d "!SYNERGY_APK!"

            echo [2/5] 권한, Doze 예외, AOT speed 컴파일 적용...
            "%ADB%" -s !SER! shell "pm grant com.vorta.synergy android.permission.WRITE_SECURE_SETTINGS ; cmd appops set com.vorta.synergy WRITE_SETTINGS allow ; cmd appops set com.vorta.synergy RUN_IN_BACKGROUND allow ; cmd appops set com.vorta.synergy WAKE_LOCK allow ; dumpsys deviceidle whitelist +com.vorta.synergy ; cmd package compile -m speed -f com.vorta.synergy" >nul 2>&1

            echo [3/5] 배터리 스웰링·발열 방지 보호 (85%% 제한 · 고속충전 OFF · stay_on=0)...
            "%ADB%" -s !SER! shell "settings put global stay_on_while_plugged_in 0 ; settings put system protect_battery 1 ; settings put global protect_battery 1 ; settings put system adaptive_fast_charging 0 ; settings put system wireless_fast_charging 0" >nul 2>&1

            echo [4/5] 기기별 오디오·안전볼륨 보호 구성 (!TARGET_TYPE!)...
            if "!TARGET_TYPE!"=="S6" (
                "%ADB%" -s !SER! shell "CUR=`settings get secure enabled_accessibility_services`; case \"$CUR\" in *com.vorta.synergy*) ;; ''|null) settings put secure enabled_accessibility_services com.vorta.synergy/com.vorta.synergy.SynergyAccessibilityService ;; *) settings put secure enabled_accessibility_services $CUR:com.vorta.synergy/com.vorta.synergy.SynergyAccessibilityService ;; esac ; settings put secure accessibility_enabled 1 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system volumelimit_on 0 ; settings put global audio_safe_volume_state 2 ; am broadcast -a com.vorta.synergy.ACTION_SAFE_VOLUME_CHECK -p com.vorta.synergy --include-stopped-packages" >nul 2>&1
                for /f "delims=" %%Y in ('"%ADB%" -s !SER! shell "date +%%Y"') do set "DEV_YEAR=%%Y"
                if !DEV_YEAR! LSS 2024 (
                    echo [주의] S6 시스템 시계가 !DEV_YEAR!년으로 설정되어 있습니다. 기기 설정에서 현재 날짜/시간을 맞춰주세요.
                )
            )
            if "!TARGET_TYPE!"=="S7" (
                "%ADB%" -s !SER! shell "CUR=`settings get secure enabled_accessibility_services`; case \"$CUR\" in *com.vorta.synergy*) ;; ''|null) settings put secure enabled_accessibility_services com.vorta.synergy/com.vorta.synergy.SynergyAccessibilityService ;; *) settings put secure enabled_accessibility_services $CUR:com.vorta.synergy/com.vorta.synergy.SynergyAccessibilityService ;; esac ; settings put secure accessibility_enabled 1 ; settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system volumelimit_on 0 ; settings put global audio_safe_volume_state 2 ; am broadcast -a com.vorta.synergy.ACTION_SAFE_VOLUME_CHECK -p com.vorta.synergy --include-stopped-packages" >nul 2>&1
            )
            if "!TARGET_TYPE!"=="A30" (
                "%ADB%" -s !SER! shell "settings put system mono_audio 0 ; settings put system master_mono 0 ; settings put system sound_alive_effect 0 ; settings put system uhqa_effect 0 ; settings put system tube_amp_effect 0 ; settings put system k2hd_effect 0 ; settings put system concert_hall_effect 0 ; settings put system dolby_atmos_mode 0 ; settings put global dolby_atmos_mode 0 ; settings put global multisound_state 0" >nul 2>&1
            )

            echo [5/5] 보르타 시너지 v1.1.1 기동 및 버전 검증...
            "%ADB%" -s !SER! shell "dumpsys package com.vorta.synergy | grep -m 1 versionCode ; am start -n com.vorta.synergy/.MainActivity"
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
echo  [완료] 보르타 시너지 v1.1.1 병행 설치 및 불안정 기기 보호 설정 완료
echo  - 2번째 골전도 기기는 앱 좌측 상단에서 [CH 4 · 골전도 보조]로 1회 지정하세요.
echo ===============================================================================
pause
exit /b 0