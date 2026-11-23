# U327-S6-S7-SAFEVOL-ROOTFIX — Galaxy S6 (API 24) & Galaxy S7 (API 26) 완전 안전볼륨 해제 및 자유볼륨 전환 결과

- **일시**: 2026-11-22
- **상태**: S6 실기기 핵심 검증 완료 / S7 실기기 및 S6 3.5mm 물리 유선 잭 연결 테스트는 미실행(장비 미연결)

## 1. 근본 원인
1. `AudioService.onConfigureSafeVolume()` (`services_0.dex` / `services.vdex`)는 `mSafeMediaVolumeIndex=90`(9단)을 하드코딩하며 `audio.safemedia.bypass`를 `isFactoryMode()`(`false`)로 덮어씀 -> non-root `setprop` 및 `unsafe_volume_music_active_ms=0` 설정만으로 RAM `mMusicActiveMs`(20h = 72,000,000ms) 누적 후 `SAFE_MEDIA_VOLUME_ACTIVE(3)` 재진입을 막지 못함.
2. 기존 구형 구현(`v4.2` 이하 / `.bat`/`.sh` 7종)이 `13`단(85%) 강제 고정과 `ON_AFTER_RELEASE` WakeLock, `<= 9` 클램프 오인, `ACTION_USER_PRESENT` 이후 뒤늦은 팝업 확인에 의존함.

## 2. 수정 내역
- **S6 (`vorta_s6_app`, `versionCode=450`, `versionName=4.5`)**:
  - `S6VolumeHelper.java`: `sTargetVolumeStep=-1` 센티널 및 Device-Protected `wired_target_vol` 도입, `enforceDefaultVolume85()` 무력화(자유볼륨 `0..15`), `cur == 9` 정밀 클램프 판정(`0..8` 수동 감쇠 보존), `enforceSystemFlags()`의 DB `state==3` 마스킹 방지.
  - `S6VolGuardThread` + 비재진입 `ContentObserver`(`volume_music_headset`, `volume_music_headphone`, `audio_safe_volume_state`) + 30s failsafe + `LOCKED_BOOT_COMPLETED`/`BOOT_COMPLETED` 기반 S6 전용 **Direct-Binder** `IAudioService.disableSafeMediaVolume()` 선제 해제 (`volume_controller_service_component` 임시 위임 후 `finally`에서 원복 `null`), 필요 시 `ON_AFTER_RELEASE` 없는 잠금화면 관통 다이얼로그 폴백.
  - `S6AccessibilityService.java`, `S6BootAndVolumeReceiver.java`, `AndroidManifest.xml` 동기화.
- **S7 (`vorta_s7_app`, `versionCode=450`, `versionName=4.5`)**:
  - `S7VolumeHelper.java`, `S7AccessibilityService.java`, `S7BootAndVolumeReceiver.java`, `AndroidManifest.xml`: `sTargetVolumeStep=-1`, DE `wired_target_vol`, `enforceDefaultVolume85()` 무력화, `cur == 9` 정밀 클램프 판정, `enforceSystemFlags()` DB `state==3` 마스킹 방지, `S7VolGuardThread` + `ContentObserver` + `pm.isInteractive()`+250ms 대기 및 `getWindows()` 능동 스캔 잠금화면 관통 다이얼로그 해제.
- **공통 보조 앱 (`vorta_freqgen_app` v3.2.0 `320`, `smato_a30_app` v4.5 `450`)**:
  - `FreqGenVolumeHelper.java`, `A30VolumeHelper.java`: `-1` 센티널, `wired_target_vol`(DE storage on SDK>=24), `sLastSyncedWired` 스피커 덮어쓰기 차단, `enforceDefaultVolume85()` 무력화, S6/S7 기기에서 권위 있는 `S6AccessibilityService`/`S7AccessibilityService`로 `ACTION_SAFE_VOLUME_CHECK` 브로드캐스트 위임(1.5s 비블로킹 폴백).
- **원클릭 `.bat` (5종 CP949 CRLF) & `.sh` (2종 UTF-8 LF)**:
  - `갤럭시S6_정상화_및_안전장치_원클릭.bat`, `갤럭시S7_정상화_및_안전장치_원클릭.bat`, `A30_S7_S6_보르타_레조_환경설정_원클릭.bat`, `A30_S7_S6_주파수발생기_전수개조_원클릭.bat`, `261014_S7최대볼륨제한없애기\A30_S7_S6_보르타_레조_환경설정_원클릭.bat`, `s6_vorta_dedicated.sh`, `s7_dedicated.sh`에서 하드코딩된 `13`단 볼륨 강제/무효 `setprop audio.safemedia.*`/`ACTION_ENFORCE_VOLUME` 전면 제거 및 `ACTION_SAFE_VOLUME_CHECK` 연동.

## 3. 빌드 및 실기기 검증 근거
- **APK 빌드·서명 (`build_s6.py`, `build_s7.py`, `build_freqgen.py`, `build_a30.py`)**: 4종 모두 `exit 0` 및 `apksigner verify` 통과.
- **스크립트 정규식 검사**: `.bat` 5종 + `.sh` 2종에서 `volume_music_* 13`, `media volume --set 13`, `setprop audio.safemedia.*`, `ACTION_ENFORCE_VOLUME` 0건 (`exit 0`).
- **갤럭시 S6 (`06157df622d5ad1d`, `SM_G920K`, Android 7.0 API 24) 실측 (`exit 0`)**:
  - **설치/권한**: 4종 APK(`450`/`450`/`320`/`450`) 설치, `WRITE_SECURE_SETTINGS`·Doze 화이트리스트·AOT `speed` 컴파일·`S6AccessibilityService` 바인딩 완료.
  - **Test 1 (자유볼륨 0~15단, 85% 복귀 없음)**: 삼성 음량 UI `미디어` SeekBar로 `15` -> `14` -> `11` -> `8` -> `14`단계 각 3초 유지 확인 (`13` 스냅백 0회).
  - **Test 2 (콜드 재부팅 화면오프·잠금화면 Direct-Binder 해제)**: `audio_safe_volume_state=3` 기록 후 `adb reboot`, 잠금/화면 꺼짐(`mWakefulness=Asleep`, `Display Power: state=OFF`, `isStatusBarKeyguard=true`, `showing=true`) 상태에서 `LOCKED_BOOT_COMPLETED` Direct-Binder `attempt=1` 성공 -> `mSafeMediaVolumeState=SAFE_MEDIA_VOLUME_INACTIVE`, `mMusicActiveMs=0`, `safe_g=2`, `safe_s=2`, `vol_ctrl_comp=null`, `mVolumeController` 정상 유지, 잔류 VolumeUI 알림 0건.
  - **Test 3 (런타임 화면오프 `ContentObserver` Direct-Binder 해제)**: `mWakefulness=Asleep`·`showing=true` 상태에서 `settings put global audio_safe_volume_state 3` 주입 3초 후 `SETTINGS_OBSERVER_CLAMP` -> `S6 Direct-Binder disableSafeMediaVolume OK (attempt=2, screenInteractive=false)` -> `post_safe_g=2`, `vol_ctrl_comp=null`, 화면 깨움 0회.
  - **Test 4 (45초+ 무중단 안정성)**: `logcat -d -b crash` 및 `dumpsys dropbox` 내 앱 크래시·ANR 0건, Observer 무한루프 0건.
- **미검증 항목 (하드웨어 미연결)**:
  - S6 3.5mm 유선 잭 물리 삽입 상태(`EarJack bPluged : false`)에서의 유선(`4 headset`) 클램프 복구 및 `ACTION_TEST_DIALOG_UNLOCK` 폴백.
  - 갤럭시 S7 (`SM-G930S`, Android 8.0) USB 미연결로 인한 실기기 배포·잠금화면 관통 다이얼로그 해제 테스트.
