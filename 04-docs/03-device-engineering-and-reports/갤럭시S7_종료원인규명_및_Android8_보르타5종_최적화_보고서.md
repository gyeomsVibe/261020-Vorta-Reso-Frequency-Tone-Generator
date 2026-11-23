# 갤럭시 S7 (SM-G930S, Android 8.0 Oreo API 26) 종료 원인 규명 및 보르타 5종 전수 최적화 보고서

- **작업 카드 ID**: `U323-S7-FULL-AUDIT-OPT`
- **대상 기기**: Samsung Galaxy S7 (`SM-G930S`, 시리얼 `ce021602916dbb3804`, `herolteskt`, `samsungexynos8890` `arm64-v8a`)
- **OS / 오디오 HAL**: **Android `8.0.0` (SDK API `26`)** / Build `R16NW.G930SKSU3EVG2` / `AudioOut_D` `48,000 Hz`, `PCM_16_BIT`, HAL `960` 프레임, `MultiSound isEnabled:false`, `mSafeMediaVolumeIndex=90`(9/15단계)
- **연결 앰프 환경**: 스마토 #2 Stereo 240W 2채널 우퍼
- **수행 프레임워크**: MIA 전략절차(Stage 0~10) + `/CRITIC /SELFREFINE /REDTEAM`

---

## 1. 기기·앱 빈번 종료 및 오작동 근본 원인 규명 (실측 근거)

### 1-1. 앱 강제 종료·무반응(ANR)·크래시 원인
1. **구형 `com.vorta.s7frequency` ANR 무한 경합 루프 (`dumpsys dropbox` 실측 23+건)**:
   - 기존 S7에는 구형 `targetSdk=22` APK 4종만 설치되어 있었고 `vorta_aegis`는 미설치 상태였음.
   - `2026-11-10`(다수) 및 `2026-11-18 03:49:11` `data_app_anr` 스택에서 확인된 바와 같이, 구형 `S7AccessibilityService$2.onChange`(`ContentObserver`)가 `Settings` 쓰기 이벤트에 재진입하며 `S7VolumeHelper.ensureTargetVolumeIfNeeded`(`S7VolumeHelper.java:404`)가 `IAudioService.getStreamVolume` 동기 Binder 호출을 반복하고 `VOLUME_CHANGED_ACTION` 정적 리시버·`TYPE_WINDOW_CONTENT_CHANGED`/`TYPE_WINDOWS_CHANGED`(`notificationTimeout=50`)와 맞물려 `system_server 70%`, `com.vorta.freqgen 22%`, `com.vorta.s7frequency 17%`, `audioserver 16%` CPU 과부하 및 ANR 강제 종료를 유발함.
2. **삼성 `SmartManager_v5`(`com.samsung.android.lool`) 및 `MARsPolicyManager` 백그라운드 강제 종료**:
   - 실시간 `logcat -b events`에서 `SMWidgetOneButton`(`com.samsung.android.lool`)이 `0.2초` 만에 28개 백그라운드 프로세스를 일괄 `am_kill` 처리하는 것을 포착.
   - `dumpsys activity mars` 실측에서 `com.vorta.s6frequency`가 이미 `AUTO_EXPIRED` → `mRestrictedPackages (--POL 1)`로 제한 목록에 편입되었음을 확인.
3. **Android 8.0 (API 26) 백그라운드 실행 제한 미준수 및 구형 APK 포맷 크래시**:
   - `smato_a30frequency` 구버전 APK는 `2026-11-09` `MainActivity.syncTelemetryUi`에서 `UnknownFormatConversionException`으로 3회 전경 크래시 발생.
   - `targetSdk=33`을 선언한 `vorta_s7_app`, `vorta_s6_app`, `smato_a30_app`, `vorta_freqgen_app`이 Android 8.0(API 26)에서 서비스 시작 시 `startService()`를 호출하거나 `intent == null`/`action="stop"` 경로에서 즉시 `startForeground()`를 보장하지 않아 `IllegalStateException`/`RemoteServiceException` 위험에 노출되어 있었음.

### 1-2. 기기 재부팅·전원 꺼짐 원인 (커널 로그 + 배터리 SOH 실측 분리 판정)
- **커널 `SYSTEM_LAST_KMSG` 9건(`2026-11-06` ~ `2026-11-20`) 전수 조사**: 9건 모두 커널 패닉이나 워치독 리셋(`0`)이 아니라 `sec_reboot (0, userrequested)`로 기록됨(ANR·프리즈 체감에 따른 수동 재부팅 추정).
- **배터리 하드웨어 노후화 심각 (`dumpsys battery` + sysfs 실측)**:
  - 제조일 `LLB MAN: 20160307` (약 10.7년 경과), **`mSavedBatteryAsoc: 52` (잔존 수명 52%)**, **`mSavedBatteryUsage: 306214` (`3,062` 사이클)**, **`mSavedBatteryMaxTemp: 565` (`56.5°C` 피크)**.
  - 여기에 발열이 큰 **`wireless_fast_charging=1`** 이 켜져 있어, 고부하/고온 조건에서 노후 셀 전압 강하(Voltage Sag)로 기기가 꺼질 수 있는 물리적 위험 확인.

### 1-3. 스테레오 음향·볼륨 설정 결함 및 `services.vdex` 실측 규칙
- **스테레오 파손 설정**: `settings get system mono_audio`가 **`1`** 로 켜져 있어 스마토 #2(2채널 우퍼) 스테레오 분리 및 `L=-R` 180° 역위상 전단파 프레셋이 모노 합산 상쇄되고 있었음.
- **불필요 부하 설정**: `accessibility_display_magnification_enabled=1`, `power_sounds_enabled=1` 활성 상태 확인.
- **S7 `services.vdex` DEX 바이트코드 실측**:
  - `onSetWiredDeviceConnectionState`: 3.5mm 유선 연결 시 `9`단계(`mSafeMediaVolumeIndex=90`)로 초기화.
  - `onCheckMusicActive`: 누적 20시간 재생 시 `SAFE_MEDIA_VOLUME_ACTIVE` 브로드캐스트 송출 및 `9`단계 강제 클램프.
  - `disableSafeMediaVolume`: `mVolumeController`(SystemUI 확인창 확인) 필수. `reloadAudioSettings()`는 빈 메서드(구형 S6과 달리 비어 있음), `setStreamVolumeForDevice()`는 `setStreamVolume()` 위임(`MultiSound=false`).

---

## 2. 적용된 수정 및 최적화 내역

### 2-1. `vorta_s7_app` 전수 개조 (S6 자유볼륨·패시브 가드·AUX Guard + API 26)
- [accessibility_service_config.xml](file:///d:/D_Workspace_NB/주파수/vorta_s7_app/res/xml/accessibility_service_config.xml): `accessibilityEventTypes="typeWindowStateChanged"`, `packageNames="com.android.systemui"`, `accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"`, `notificationTimeout="100"`(무폴링 0-루프).
- [S7AccessibilityService.java](file:///d:/D_Workspace_NB/주파수/vorta_s7_app/src/com/vorta/s7frequency/S7AccessibilityService.java): `ContentObserver`·주기 타이머 완전 제거. `event.getSource()`, `getRootInActiveWindow()`, `getWindows()`(`TYPE_VOLUME_OVERLAY` 포함) 검사 후 청력 경고 키워드 일치 시에만 `android:id/button1`/`"확인"`/`"OK"` 클릭 및 `80ms`/`350ms` 복원.
- [S7VolumeHelper.java](file:///d:/D_Workspace_NB/주파수/vorta_s7_app/src/com/vorta/s7frequency/S7VolumeHelper.java): **85%(13/15) 강제 고정 전면 제거**.
  - `SharedPreferences`(`vorta_s7_vol_prefs`) + `syncTargetFromSystem()`으로 사용자 수동 볼륨(`0~15`) 영구 존중.
  - 오직 유선 상태(`target > 9`)에서 OS가 `<= 9`로 강제 클램프할 때만 `FLAG_SHOW_UI | FLAG_SHOW_UI_WARNINGS`(`1 << 10`) + `3초 WakeLock`으로 안전볼륨 해제.
  - `enforceSystemFlags()`에 `mono_audio=0`, `master_mono=0`, `volumelimit_on=0` 및 각종 음장 OFF 고정 반영(무효 Binder 호출 없음).
- [S7BootAndVolumeReceiver.java](file:///d:/D_Workspace_NB/주파수/vorta_s7_app/src/com/vorta/s7frequency/S7BootAndVolumeReceiver.java): 부팅 시 S7 기종만 동작하며 `13` 강제 초기화 없이 `syncTargetFromSystem()` + `ensureTargetVolumeIfNeeded(..., false)` 호출.
- [S7AudioGeneratorService.java](file:///d:/D_Workspace_NB/주파수/vorta_s7_app/src/com/vorta/s7frequency/S7AudioGeneratorService.java):
  - API 26 대응: `onStartCommand()` 최상단 즉시 `updateNotification()` + 무음 `NotificationChannel`(`setSound(null, null)`, `enableVibration(false)`, `setShowBadge(false)`), `intent == null`/`"stop"` 안전 종료, `"vol_step"` 13 강제 제거.
  - `AudioAttributes`(`USAGE_MEDIA`/`CONTENT_TYPE_MUSIC`) + `AudioDeviceCallback` + `setPreferredDevice()` + `ACTION_AUDIO_BECOMING_NOISY` 기반 **20ms 무클릭 스피커 차단 및 재연결 1.0s 페이드인 `AUX Guard`** 이식(`48kHz`·`-1.0dBTP` `Peak 29200`·주파수/변조 원형 100% 보존).
- [MainActivity.java](file:///d:/D_Workspace_NB/주파수/vorta_s7_app/src/com/vorta/s7frequency/MainActivity.java): API 26+ `startForegroundService()` / 정지 `stopService()` 전환, `13` 강제 대입 제거, 실시간 볼륨·`AUX Guard` 상태 표기.

### 2-2. 형제 보르타 4종 앱 Android 8.0 + 자유볼륨 일괄 통일
- **`vorta_s6_app`** ([S6VolumeHelper.java](file:///d:/D_Workspace_NB/주파수/vorta_s6_app/src/com/vorta/s6frequency/S6VolumeHelper.java), [S6BootAndVolumeReceiver.java](file:///d:/D_Workspace_NB/주파수/vorta_s6_app/src/com/vorta/s6frequency/S6BootAndVolumeReceiver.java), [S6AudioGeneratorService.java](file:///d:/D_Workspace_NB/주파수/vorta_s6_app/src/com/vorta/s6frequency/S6AudioGeneratorService.java), [MainActivity.java](file:///d:/D_Workspace_NB/주파수/vorta_s6_app/src/com/vorta/s6frequency/MainActivity.java)): 부팅 리시버 `!isTargetDevice()`로 S6 단독 격리(S7 부팅 간섭 차단), `mono_audio=0` 고정, `SharedPreferences` 기반 자유볼륨 통일, API 26 `startForegroundService()` / `stopService()` 적용.
- **`smato_a30_app`** ([A30VolumeHelper.java](file:///d:/D_Workspace_NB/주파수/smato_a30_app/src/com/smato/a30frequency/A30VolumeHelper.java), [A30AudioGeneratorService.java](file:///d:/D_Workspace_NB/주파수/smato_a30_app/src/com/smato/a30frequency/A30AudioGeneratorService.java), [MainActivity.java](file:///d:/D_Workspace_NB/주파수/smato_a30_app/src/com/smato/a30frequency/MainActivity.java)): 구형 리플렉션(`setStreamVolumeForDevice`) 제거, `mono_audio=0`/`volumelimit_on=0`, API 23+ `AudioDeviceCallback`·`AudioAttributes`·`AUX Guard` 이식, `SharedPreferences` 자유볼륨 + API 26 `startForegroundService()`/`stopService()` 적용.
- **`vorta_freqgen_app`** ([FreqGenVolumeHelper.java](file:///d:/D_Workspace_NB/주파수/vorta_freqgen_app/src/com/vorta/freqgen/FreqGenVolumeHelper.java), [AudioEngineService.java](file:///d:/D_Workspace_NB/주파수/vorta_freqgen_app/src/com/vorta/freqgen/AudioEngineService.java), [MainActivity.java](file:///d:/D_Workspace_NB/주파수/vorta_freqgen_app/src/com/vorta/freqgen/MainActivity.java)): `mono_audio=0` 추가, `SharedPreferences` 자유볼륨 존중, API 26+ `startForegroundService()` & `intent == null` 즉시 포그라운드 보호.
- **`vorta_aegis_app`** ([MainActivity.java](file:///d:/D_Workspace_NB/주파수/vorta_aegis_app/src/com/vorta/aegis/MainActivity.java)): S7 최초 신규 설치(`v2.2.0`), 정지 경로 `stopService()` 전환 및 미바인드 볼륨 갱신 `IllegalStateException` 가드(자유 볼륨 기존 준수).

### 2-3. Galaxy S7 Android 8.0 OS 최적화 및 원클릭 스크립트(CP949)
- **OS 설정**: `mono_audio=0`, `master_mono=0`, `SoundAlive`/음장/알림음(`volume_ring/notification/system/alarm=0`) 차단, `wireless_fast_charging=0`, `adaptive_fast_charging=0`, `protect_battery=1`, `stay_on_while_plugged_in=0`, `screen_off_timeout=600000` 유지, `accessibility_display_magnification_enabled=0`, `audio_safe_volume_state=2`, `unsafe_volume_music_active_ms=0`, `volumelimit_on=0`.
- **비필수 블로트 동결 (18종)** 및 **디바이스 관리(`com.samsung.android.lool`) 복원 유지**: `faceservice`, `cmh`, `storyservice`, `spp.push`, `simplesharing`, `diagmonagent`, `vrsvc`, `wssyncmldm`, `soagent`, `skt.hps20client`, `skp.tstore.startup`, `rubin.app`, `intelligenceservice2`, `aodservice`, `game.gametools`, `vepreload`, `watchmanagerstub`, `drivelink.stub` 18종 동결(디바이스 관리·메모리/저장공간 최적화 앱 `com.samsung.android.lool`, `sm.devicesecurity`, `sm.policy`는 사용자 요청으로 활성 유지, 재가동 시 `pm enable` 복원 가능).
  - **보르타 절전 예외 팁**: 디바이스 관리(메모리·저장공간 최적화)의 "지금 최적화"나 배터리 절전 기능 사용 시 5종 앱이 자동 절전되지 않도록 `설정 > 디바이스 관리 > 배터리 > 절전 상태 예외 앱`에 보르타 5종 앱 추가(및 ADB `deviceidle whitelist` / `RUN_IN_BACKGROUND allow` 적용 상태)로 병행 운용 가능.
- **원클릭 배치 스크립트**:
  - 신설: [갤럭시S7_정상화_및_안전장치_원클릭.bat](file:///d:/D_Workspace_NB/주파수/갤럭시S7_정상화_및_안전장치_원클릭.bat) (S7 전용 CP949 원클릭 — 접근성 초기화 후 5종 설치·AOT `speed` 컴파일·`mono_audio=0`·자유볼륨·21종 동결)
  - 갱신: [A30_S7_S6_보르타_레조_환경설정_원클릭.bat](file:///d:/D_Workspace_NB/주파수/A30_S7_S6_보르타_레조_환경설정_원클릭.bat), [A30_S7_S6_주파수발생기_전수개조_원클릭.bat](file:///d:/D_Workspace_NB/주파수/A30_S7_S6_주파수발생기_전수개조_원클릭.bat)

---

## 3. 실기기 최종 검증 결과 (`SM-G930S`, `ce021602916dbb3804`)

| 검증 항목 | 실측 수치 / 로그 증거 | 판정 |
|---|---|---|
| **5종 APK 빌드·서명** | `build_s7`, `build_s6`, `build_a30`, `build_freqgen`, `build_aegis` 모두 **`exit 0`** + `apksigner verify` 통과 | **PASS** |
| **5종 설치 & AOT `speed`** | `s7`(`4.2`), `s6`(`4.2`), `a30`(`4.2`), `freqgen`(`3.1.0`), `aegis`(`2.2.0` 신규) 모두 `targetSdk=33`, `status=kOatUpToDate`, `compilation_filter=speed`, `RUN_IN_BACKGROUND=allow`, `deviceidle whitelist` 등록 | **PASS** |
| **무폴링 접근성 바인딩** | 재바인딩 후 `dumpsys accessibility`: `enabled`·`bound` 모두 `com.vorta.s7frequency/.S7AccessibilityService`, `eventTypes=TYPE_WINDOW_STATE_CHANGED`, `notificationTimeout=100`, `capabilities=1` | **PASS** |
| **자유 볼륨 유지 (85% 강제 해제)** | 접근성 연결 상태에서 `11` → `11`, `14` → `14`, `8` → `8`, `13` → `13` (각 2초 대기 후 변동 0건) | **PASS** |
| **스테레오·음향·언더런** | `mono_audio=0`, `S7AudioAudit` `SR=48000Hz` · `Peak=29200/32767(-1.0dBTP)` · `RMS=-4.19dBFS` · `DC=-0.00053`, `AudioOut_D` `UndFrmCnt=0`, `Flushed=0`, `Delayed writes=0`, `0 Effect Chains` | **PASS** |
| **5종 기동·상호 양보·안정성** | 5앱 모두 즉시 기동, `S6`/`A30` 전환 시 형제 오디오 서비스 자동 종료(`Destroy`), `MARs` `mRestrictedPackages=0`, `com.android.settings` 정상 실행, `logcat -d -b crash` **`0`건**, Vorta 예외/ANR **`0`건**, `MemAvailable=1,995,288 kB` | **PASS** |

---

## 4. 백업 및 하드웨어 유지관리 권고
1. **원본 백업 경로**: `d:\D_Workspace_NB\주파수\.work\backup_20261120_s7_full_opt\` (수정 전 20개 파일 보관) 및 프레임워크 역공학 자료 `d:\D_Workspace_NB\주파수\.work\s7_inspect\` (`services.odex`, `services.vdex`).
2. **하드웨어 배터리 권고 (중요)**: 이번 작업으로 소프트웨어 기반 ANR·MARs 킬·Android 8.0 백그라운드 종료·모노 왜곡·볼륨 강제 락 원인은 모두 제거했으나, S7 내장 배터리는 `2016-03-07` 제조(`Asoc 52%`, `3,062` 사이클) 노후 셀입니다. 이번 설정으로 무선고속충전(`0`)과 배터리 보호(`1`, 충전 85% 제한)를 활성화해 열화를 완화했으나, 고부하 시 배터리 자체 전압 강하로 전원이 꺼지는 증상이 재발하면 **유선 저발열 전원(5V/1A~2A)** 사용 및 **신품 정품 배터리 교체**를 권장합니다.
3. **일상 유지관리**: 펌웨어 업데이트나 초기화 후에는 PC에서 [갤럭시S7_정상화_및_안전장치_원클릭.bat](file:///d:/D_Workspace_NB/주파수/갤럭시S7_정상화_및_안전장치_원클릭.bat)만 실행하면 S7 최적 상태가 1회 실행으로 자동 재적용됩니다.
