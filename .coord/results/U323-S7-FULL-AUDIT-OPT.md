# Card Result: U323-S7-FULL-AUDIT-OPT

- **Card**: `U323-S7-FULL-AUDIT-OPT`
- **Status**: `DONE`
- **Conductor / Executor**: Antigravity (MIA Strategic 11-Stage Framework + `/CRITIC /SELFREFINE /REDTEAM`)
- **Target Device**: Samsung Galaxy S7 (`SM-G930S`, serial `ce021602916dbb3804`, `herolteskt`, Android `8.0.0` Oreo / SDK `API 26`, Build `R16NW.G930SKSU3EVG2`, Exynos 8890 `arm64-v8a`)

## 1. 실측 근본 원인 (Live S7 Telemetry + `services.vdex` DEX)
1. **구형 `com.vorta.s7frequency` ANR 강제 종료 23+건**: S7에 구버전(`targetSdk=22`) APK 4종이 남아 있었고(`vorta_aegis` 미설치), 구형 `S7AccessibilityService$2.onChange`(`ContentObserver`) + 정적 `VOLUME_CHANGED_ACTION` 85% 락 루프 + `TYPE_WINDOW_CONTENT_CHANGED`/`TYPE_WINDOWS_CHANGED`(`notificationTimeout=50`)가 `2026-11-10` 다수 및 `2026-11-18 03:49:11` ANR(`system_server 70%`, `freqgen 22%`, `s7 17%`, `audioserver 16%`)을 유발함.
2. **삼성 `MARs` / `SmartManager_v5`(`com.samsung.android.lool`) 백그라운드 대량 킬**: `SMWidgetOneButton`이 `0.2초` 내 28개 프로세스를 `am_kill` 했으며 `dumpsys activity mars`에서 `com.vorta.s6frequency`가 `AUTO_EXPIRED` → `mRestrictedPackages (--POL 1)` 상태였음.
3. **Android 8.0 API 26 포그라운드 제한 및 구형 크래시**: `smato_a30` 구형 APK의 `2026-11-09` `UnknownFormatConversionException` 크래시 3건 및 4개 앱 `startService()` / `intent == null` 즉시 `startForeground()` 미보장.
4. **기기 재부팅·배터리 노후화**: `SYSTEM_LAST_KMSG` 9건은 모두 `sec_reboot (0, userrequested)`(커널 패닉/WDT `0`건). 단, 배터리 `20160307` 제조, `mSavedBatteryAsoc=52%`, `3,062` 사이클(`mSavedBatteryUsage=306214`), `MaxTemp 56.5°C` 노후 + `wireless_fast_charging=1` 발열 위험 확인.
5. **스테레오 및 볼륨 결함**: `settings get system mono_audio` = **`1`** (240W 2채널 스테레오 및 `L=-R` 역위상 상쇄), `accessibility_display_magnification_enabled=1`, `power_sounds_enabled=1`, 그리고 `vorta_s7_app` 등 85%(13/15) 볼륨 하드락 잔존.

## 2. 수정 및 배포 요약
- **`vorta_s7_app`**: `accessibility_service_config.xml`(`typeWindowStateChanged` only + `flagRetrieveInteractiveWindows` + `100ms`), `S7AccessibilityService.java`(`getWindows()` `TYPE_VOLUME_OVERLAY` 0-폴링 확인), `S7VolumeHelper.java`(`SharedPreferences` 자유볼륨 `0~15`, `mono_audio=0`, `volumelimit_on=0`, `FLAG_SHOW_UI_WARNINGS` + `3s WakeLock`), `S7BootAndVolumeReceiver.java`, `S7AudioGeneratorService.java`(API 26 즉시 `updateNotification()`, `AudioAttributes`, `AudioDeviceCallback`·`setPreferredDevice`·20ms 스피커 차단 `AUX Guard`), `MainActivity.java`(`startForegroundService`/`stopService`, 13 고정 제거).
- **형제 4종 앱 통일**: `vorta_s6_app` (`S6BootAndVolumeReceiver` `!isTargetDevice()` 격리 + 자유볼륨 + API 26), `smato_a30_app` (`A30VolumeHelper` `setStreamVolumeForDevice` 제거/자유볼륨 + `AUX Guard` + `AudioAttributes` + API 26), `vorta_freqgen_app` (`FreqGenVolumeHelper` `mono_audio=0`/자유볼륨 + `MainActivity`/`AudioEngineService` API 26 `startForegroundService`), `vorta_aegis_app` (`MainActivity` `stopService` 및 unbound 볼륨 `IllegalStateException` 가드).
- **S7 OS + 배치**: 5종 APK 빌드·서명(모두 exit 0), S7 설치(`vorta_aegis` `v2.2.0` 신규 포함), `WRITE_SECURE_SETTINGS`/`RUN_IN_BACKGROUND`/`deviceidle`/AOT `speed` 컴파일, `mono_audio=0`, `wireless_fast_charging=0`, `adaptive_fast_charging=0`, `protect_battery=1`, `stay_on_while_plugged_in=0`, `accessibility_display_magnification_enabled=0`, 비필수 블로트 18종 동결(`com.samsung.android.lool`/`sm.devicesecurity`/`sm.policy`는 사용자 요청에 따라 디바이스 관리 기능 유지를 위해 활성 복원 및 `ScoreBoardActivity` 정상 기동 확인; `디바이스 관리 > 배터리 > 절전 상태 예외 앱` 권장), CP949 배치 3종([갤럭시S7_정상화_및_안전장치_원클릭.bat](file:///d:/D_Workspace_NB/주파수/갤럭시S7_정상화_및_안전장치_원클릭.bat) 신설 + 통합 배치 2종 갱신).

## 3. 실기기 검증 결과 (`SM-G930S`, `ce021602916dbb3804`)
- **패키지 & 컴파일**: 5앱 모두 `targetSdk=33`, `status=kOatUpToDate`, `compilation_filter=speed`, `deviceidle whitelist` 등록, `MARs mRestrictedPackages=0`.
- **접근성(재바인딩 후 실측)**: `com.vorta.s7frequency/.S7AccessibilityService` 단독 연결, `eventTypes=TYPE_WINDOW_STATE_CHANGED`, `notificationTimeout=100`, `capabilities=1` (0-폴링).
- **자유 볼륨 실측 (접근성 재바인딩 후)**: `11` → `11`, `14` → `14`, `8` → `8`, `13` → `13` (하드락 0건).
- **오디오·안정성**: `S7AudioAudit` `SR=48000Hz`, `Peak=29200/32767(-1.0dBTP)`, `RMS=-4.19dBFS`, `DC=-0.00053`, `AudioOut_D` `UndFrmCnt=0`, `Delayed writes=0`, `0 Effect Chains`; 5앱 순차 실행 및 상호 양보(`Destroy ServiceRecord`), `Settings` 정상 실행, `logcat -d -b crash` **`0`건**.
- **백업·보고서**: `d:\D_Workspace_NB\주파수\.work\backup_20261120_s7_full_opt\`, [갤럭시S7_종료원인규명_및_Android8_보르타5종_최적화_보고서.md](file:///d:/D_Workspace_NB/주파수/갤럭시S7_종료원인규명_및_Android8_보르타5종_최적화_보고서.md).
