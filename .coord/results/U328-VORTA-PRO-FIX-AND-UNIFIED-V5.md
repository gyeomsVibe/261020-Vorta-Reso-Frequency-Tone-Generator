# U328-VORTA-PRO-FIX-AND-UNIFIED-V5 — 보르타 Pro v3.3 수정 및 보르타 통합앱 v5.0 (OS별 3종) 개발·에폭1 검수 결과

- **일시**: 2026-11-22
- **상태**: 빌드·에폭1 호스트 검수(20/20 PASS)·S6 무음 AudioFlinger 32-bit Float 실측 완료 / S6 잠금 해제 후 실화면 UI 검수 및 S7·A30 실기기 배포 대기
- **제외 범위**: `vorta_aegis_app` 미수정·미병합 (요청에 따라 보르타 이지스 제외), 레거시 `vorta_s6_app`·`vorta_s7_app`·`smato_a30_app` 원본 보존

---

## 1. MIA 11단계 Stage 0~2 핵심 근거 및 하드웨어 코덱 한계 고지

### 1.1 순수 주파수 엔진 & 웹 딥리서치 근거 (ISO · Android 공식 문서)
1. **Android `AudioTrack` / `AudioFlinger` 고순도 파이프라인** (`developer.android.com`, `source.android.com`):
   - `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE`를 조회하여 하드웨어 네이티브 `48000 Hz`로 일치시켜 `AudioFlinger::AudioResampler` 리샘플링 보간 잡음 원천 차단.
   - 64-bit `double` NCO 위상 누산기(`wrapPhase` `0..2π`)로 장시간 운전 위상 드리프트 제거 후 IEEE 32-bit `ENCODING_PCM_FLOAT` 출력(미지원 단말용 삼각확률밀도함수 **TPDF** 16-bit 디더 폴백 내장).
   - `-1.0 dBTP`(`0.8912509381337456`) 정밀 헤드룸으로 인터샘플 피크 클리핑 및 DAC 단 고조파 왜곡 방지.
   - `PureDspEngine` / `PureDspCore` 1차 지수 슬루잉(`computeFreqSlewAlpha`, ~35ms 시정수)으로 주파수 급변경 시 클릭·팝(transient) 제거.
2. **갤럭시 S6 간헐적 3.5mm AUX 무음/끊김 근본 원인 및 펌웨어 사실 고지**:
   - **비루팅 한계 명시**: 일반 안드로이드 앱(non-root)은 `/vendor/firmware` 오디오 코덱 펌웨어나 `/system/etc/mixer_paths.xml` 커널 믹서 경로를 덮어쓸 수 없음.
   - **S6 하드웨어 실측 (`SM-G920K`, Wolfson `Pacific_WM1840`)**: `mixer_paths.xml` 점검 결과 `media-headset` 경로는 이미 `AIF1 -> HPOUT1`, `Digital Volume=128`(단위 게인), `Noise Gate Switch=0`(게이트 비활성)의 순수 경로로 구성되어 있음.
   - **간헐적 AUX 무음 원인 및 해결**: 기존 코드가 API 24 기기에서 `setPreferredDevice(wiredDev)`를 핀 고정한 뒤 분리 시 `setPreferredDevice(null)` 해제를 누락하고, `AudioTrack.write()`의 음수 반환(`ERROR_DEAD_OBJECT = -6`, `ERROR_INVALID_OPERATION = -3`)을 무시해 죽은 트랙에 헛쓰기를 반복함.
   - **적용 수정 (`AudioEngineService.java`, `VortaAudioService.java`)**: API 24~25에서 불필요한 `setPreferredDevice` 핀 제거, 분리 시 `setPreferredDevice(null)` 즉시 해제, 250ms 잭 접점 채터링 디바운스(`AUX_DISCONNECT_DEBOUNCE_MS = 250`) + 600ms 루프 자가복구 + `write() < 0` 감지 시 `AudioTrack` 무클릭 자동 재생성.

---

## 2. 보르타 주파수 발생기 Pro (`vorta_freqgen_app` v3.3.0, `versionCode=330`) 수정 내역

1. **좀비 프리셋 1~3 부활 버그 근본 수정**:
   - **원인**: 기존 `MainActivity.java`가 `AudioEngineService.sSweepItems`를 RAM에만 유지하고 `SharedPreferences` 영구 저장/복원이 전혀 없어 앱 재시작 시 항상 고정 스케줄 1~3으로 초기화됨.
   - **수정**: [`SweepPresetStore.java`](file:///d:/D_Workspace_NB/주파수/vorta_freqgen_app/src/com/vorta/freqgen/SweepPresetStore.java) 신설. Device-Protected Storage(`createDeviceProtectedStorageContext()`) 기반 `vorta_freqgen_sweeps_v33`에 무의존 JSON 직렬화로 스윕 목록, 사용자 이름 지정 세트(`NamedSets`), 수동/이중/변조 상태 영구 저장. 최초 1회 실행만 `sweeps_initialized_v1` 센티널로 초기 3개 생성하며, 사용자가 전부 삭제(`[]`) 후 재시작해도 `[]` 상태(0개) 그대로 유지됨.
2. **프리셋 탭 기본 전체 접힘 & 비-AI / 360dp 무잘림 UI 개선**:
   - [`MainActivity.java`](file:///d:/D_Workspace_NB/주파수/vorta_freqgen_app/src/com/vorta/freqgen/MainActivity.java) 프리셋 탭 4개 아코디언 모두 `defaultExpanded = false` 강제(모두 접힌 상태로 시작, 개별 터치 또는 `모두 펼치기/닫기` 지원).
   - 7개 퀵 주파수 칩을 `4 + 3` 2행(`7.83`, `18.98`, `28 Hz`, `40 Hz` / `43.2 Hz`, `55 Hz`, `63 Hz`)으로 분리하고 `makeActionButton`·탭 버튼에 `setMinWidth(0)`·`setMinimumWidth(0)`·`dp(4)` 좌우 패딩 적용해 360dp 화면에서 한글 버튼 잘림 방지.
   - 스윕별 편집(제목·시작/끝 Hz·초·반복·개별 저장/삭제), `±0.01 / ±0.1 / ±1.0 / ±5.0 Hz` 정밀 스텝퍼, 이중 사인(`f1+f2`) 비트 토글, FM/AM/RM 실시간 슬라이더, 이모지 `0`건 정리.
3. **웹 딥리서치 기반 Pro 시나리오 +3 팩 추가**:
   - `ISO 16283` 슬래브 저역 3중 밴드(40 / 50 / 63 Hz), `ISO 2631` 전신 진동 WBV 생체 3종(5.5 Hz 척추 / 10 Hz 흉곽 / 25 Hz 두개골), `ISO 226` 선형 이중 사인 비트 2종(55+59.5 Hz / 28+32 Hz).

---

## 3. 보르타 통합앱 (`vorta_unified_app`, `com.vorta.unified`, v5.0.0 `versionCode=500`)

1. **4종 앱 단일 통합 & 좌측 상단 아코디언 메뉴 탭**:
   - [`VortaModule.java`](file:///d:/D_Workspace_NB/주파수/vorta_unified_app/src/com/vorta/unified/VortaModule.java): `S6_BTL` · `S7_ANCHOR` · `A30_SWEEP` · `PRO_MANUAL` 4대 모듈 통합 (`vorta_aegis_app` 제외).
   - [`MainActivity.java`](file:///d:/D_Workspace_NB/주파수/vorta_unified_app/src/com/vorta/unified/MainActivity.java): 앱 좌측 상단에 `≡  메뉴  |  <선택 모듈>  ▼` 아코디언 드로어 구현(기본 접힘), 모든 프리셋 아코디언 기본 접힘(`defaultExpanded=false`), 이모지 `0`건, `4+3` 퀵 칩 및 `setMinWidth(0)` 무잘림 적용.
2. **Android OS 버전별 3종 전용 플레이버 APK 빌드 (`build_unified.py`)**:
   - **갤럭시 S6 (`vorta_unified_s6_api24.apk`, `250,751 B`, `targetSdk=24`, `minSdk=21`)**: `ENABLE_S6_DIRECT_BINDER=true`, `USE_SET_PREFERRED_DEVICE=false`, `ENABLE_VOLUME_GUARD=true`, 기본 모듈 `S6_BTL`.
   - **갤럭시 S7 (`vorta_unified_s7_api26.apk`, `250,751 B`, `targetSdk=26`, `minSdk=24`)**: `ENABLE_S7_LOCKSCREEN_UNLOCK=true`, `USE_SET_PREFERRED_DEVICE=true`, `ENABLE_VOLUME_GUARD=true`, 기본 모듈 `S7_ANCHOR`.
   - **갤럭시 A30 (`vorta_unified_a30_api30.apk`, `246,655 B`, `targetSdk=30`, `minSdk=26`)**: `foregroundServiceType="mediaPlayback"`, `AccessibilityService` 미포함, `ENABLE_VOLUME_GUARD=false`(개인 기기 볼륨 무개입), 기본 모듈 `A30_SWEEP`.
   - 원클릭 배포 스크립트: [`보르타_통합앱_OS버전별_설치_원클릭.bat`](file:///d:/D_Workspace_NB/주파수/보르타_통합앱_OS버전별_설치_원클릭.bat) (`5,110 B`, CP949 CRLF).
3. **웹 딥리서치 실증 시나리오 추가 (각 모델 3종씩 총 12종 + 기존 36종 = 48종, Pro 팩 포함)**:
   - [`ScenarioCatalog.java`](file:///d:/D_Workspace_NB/주파수/vorta_unified_app/src/com/vorta/unified/ScenarioCatalog.java): S6/S7/A30 각각 `일일 스케줄 4` + `실전 대응 9` + `웹 리서치 실증 (NEW) 3` (ISO 16283 콘크리트 슬래브 1/3옥타브·공간평균, ISO 2631-1 척추/흉곽/두개골/파시니안 공진, ISO 226 상향 마스킹 및 선형 이중 사인 맥놀이).

---

## 4. 에폭 1(Epoch-1) 검수 및 실기기 검증 근거

### 4.1 빌드·정적 감사·호스트 JVM 테스트 (`exit 0`)
- **APK 서명·매니페스트 검증**:
  - `python d:\D_Workspace_NB\주파수\vorta_freqgen_app\build_freqgen.py` (`exit 0`, `177,023 B`, `versionCode='330' versionName='3.3.0'`, `apksigner verify` PASS)
  - `python d:\D_Workspace_NB\주파수\vorta_unified_app\build_unified.py` (`exit 0`, 3종 모두 `versionCode='500' versionName='5.0.0'`, `targetSdkVersion` 24/26/30, `apksigner verify` PASS — `s6_api24`: `254,847 B`, `s7_api26`: `250,751 B`, `a30_api30`: `250,751 B`)
- **정적 UI/이모지 검사**: `freqgen/MainActivity.java`, `unified/MainActivity.java`, `unified/ScenarioCatalog.java` 3파일 모두 `emojis=0`, `defaultExpandedTrue=0`, 양측 `MainActivity.java`에 `setMinWidthZero=2` 확인 (`exit 0`).
- **에폭 1 호스트 JVM 자동 감사 ([`TestVortaEpoch1Audit.java`](file:///d:/D_Workspace_NB/주파수/vorta_unified_app/tests/TestVortaEpoch1Audit.java), `exit 0`)**: **`PASSED=20, FAILED=0`**
  - 순수 정현파 `55.0 / 43.2 / 28.0 Hz` (Pro & Unified 48kHz, 240,000프레임 DFT): `THD+N = 0.000000% ~ 0.000033%`, `Peak = 0.89125` (`-1.0 dBTP`), `|DC| <= 1e-7`.
  - TPDF 16-bit 폴백: `maxAbs16 = 29204`, `dc16 = 0.000000`.
  - 이중 사인 선형 맥놀이 (`55.0 + 59.5 Hz`): `THD+N = 0.000000%`, `Peak = 0.89123`.
  - 무클릭 위상 연속 주파수 전환 (`55 → 28 Hz`): `boundaryJump = 0.00642` (`maxDelta = 0.00641`).
  - 좀비 프리셋 회귀(Pro & Unified): 초기 3개 생성 → 전체 삭제(`[]`) → 재초기화 후 `reloaded = 0` (`uniEmpty = []`), 커스텀 JSON 왕복 일치 (`count = 2`).
  - 카탈로그 무결성: 4모듈, S6/S7/A30 각 16종(48종) 이모지 0건, 주파수 `0.5~63.0 Hz` 범위 준수.
  - AUX 250ms 디바운스(`t0=false t200=false t260=true reset=false`) 및 `ERROR_DEAD_OBJECT (-6)` 재생성 판정 통과.

### 4.2 갤럭시 S6 (`06157df622d5ad1d`, `SM_G920K`, Android 7.0) 실측 결과
- **설치 및 안전장치 바인딩**:
  - `vorta_freqgen_pro.apk` (`330`) & `vorta_unified_s6_api24.apk` (`500`, `254,847 B`) 재설치(`Success`), `WRITE_SECURE_SETTINGS` 부여, Doze 화이트리스트 등록, AOT `speed` 컴파일, `com.vorta.unified/com.vorta.unified.VortaAccessibilityService` 바인딩 및 `audio_safe_volume_state = 2` 유지 확인.
- **무음(`0.0f`) 라이브 `AudioFlinger` 32-bit Float 파이프라인 실측** (`verify_audioflinger_s6.py`, `exit 0`):
  - `com.vorta.unified/.VortaAudioService` (PID `10435`): `AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`, `AudioOut_D` 활성 (`Sample rate: 48000 Hz`, `HAL frame count: 960`, `Normal mixer raw underrun counters: partial=0 empty=0`, `1 Tracks of which 1 are active`).
  - `com.vorta.freqgen/.AudioEngineService` (PID `10953`): `AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`, `AudioOut_D` 활성 (`48000 Hz`, `HAL frame count: 960`, `partial=0 empty=0`, `1 Tracks of which 1 are active`).
  - 종료 후 상태: 잔류 오디오 서비스 `[]`, 앱 크래시 `vorta_crash_lines: 0`.
- **S6 화면꺼짐 Direct-Binder `com.vorta.unified` 실측** (`exit 0`, A30 자유볼륨 동등성 검증):
  - `mWakefulness=Asleep` 상태 유지 중 `settings put global audio_safe_volume_state 3` 주입 즉시 `Proactive Direct-Binder disarm OK (FAILSAFE_30S_CLAMP, prevDb=3)` 실행.
  - 검증 결과: `val_immediate=2`, `POST_SAFE_STATE=2`, `POST_VOL_CTRL_COMP=null`, `mSafeMediaVolumeState=SAFE_MEDIA_VOLUME_INACTIVE`, `mMusicActiveMs=0`, 화면 깨움 `0`회(`mWakefulness=Asleep`), 크래시 `0`건.

### 4.3 S6·S7 대 A30 안전볼륨 구조 비교 (`services_0.dex` / `services.dex` 바이트코드 실측)
- **시스템 프레임워크 원인**: S6(`services_0.dex`)과 S7(`services.dex`)의 `AudioService`는 `onConfigureSafeVolume`에서 `mSafeMediaVolumeIndex=90`(9단)을 하드코딩하고 `isFactoryMode()`(`mIsFactoryMode`/`mIsFactorySim`)가 아니면 `audio.safemedia.bypass`를 무시해 부팅 시 `STATE=3`으로 진입함. 또한 `onSetWiredDeviceConnectionState`에서 `index+5 > 9`이면 9단으로 클램프하고, `onCheckMusicActive`가 60초(`0xea60`)마다 누적해 `0x044aa200`(20시간) 초과 시 `STATE 2→3`으로 재잠금함.
- **통합앱 [`VortaVolumeGuard.java`](file:///d:/D_Workspace_NB/주파수/vorta_unified_app/src/com/vorta/unified/VortaVolumeGuard.java) 3대 보강 반영**:
  1. `onHeadsetPlug` 분리(`state != 1`) 300ms 후 `enforceSystemFlags` + `applyWiredIndexes` 유지(재삽입 시 9단 초기화 차단).
  2. `registerDynamicReceiver`에 `AudioManager.ACTION_AUDIO_BECOMING_NOISY` 및 `android.media.STREAM_DEVICES_CHANGED_ACTION` 추가.
  3. `DIALOG_RETRY_WINDOW_MS = 30s` 통일 및 `requestConfirmDialog` 2,200ms 확인 후 `+5,000ms`(총 7,200ms > 4,000ms) 재시도 타이밍 수정(`recoverOnGuardThread` 성공 시 `sConsecutiveDialogRetries=0` 초기화).
- **비루팅 S6 vs S7 vs A30 최종 동작 차이**:
  - **갤럭시 S6 (API 24)**: `AudioService.enforceVolumeController`가 `mControllerService` UID 일치를 허용하므로 `Direct-Binder disableSafeMediaVolume()`이 **무팝업·무화면켜짐**으로 `STATE 3→2`·`mMusicActiveMs=0` 해제를 완료해 **A30과 체감상 동일한 자유볼륨(`0~15`단)**을 제공함.
  - **갤럭시 S7 (API 26)**: `services.dex`에서 `ControllerService` 우회가 제거되어 `android.permission.STATUS_BAR_SERVICE`를 강제함. 따라서 비루팅 환경에서는 부팅 직후·20시간 경과 최초 `>9`단 복구 시에만 잠금화면 관통 다이얼로그를 `VortaAccessibilityService`가 1~2초 내 자동 확인·해제하며(OS 펌웨어 수준의 완전 제거는 루팅/커스텀롬 영역), 평소 볼륨은 `0~15`단 자유볼륨으로 유지됨.
  - **갤럭시 A30 (API 30)**: 안전볼륨 강제 개입 없음(`ENABLE_VOLUME_GUARD=false`).

---

## 5. 미검증 항목 (사용자 확인 필요)
1. **갤럭시 S6 실화면 UI·터치 검증 (`verify_pro_s6.py`, `verify_unified_s6.py`)**: S6가 PIN 잠금화면(`isStatusBarKeyguard=true`, `showing=true`) 상태여서 잠금 해제 후 좌측 상단 아코디언 메뉴·프리셋 기본 접힘·좀비 삭제 UI 동작을 실화면으로 확인해야 함 (PIN/잠금 우회 미시도).
2. **갤럭시 S7 (`SM-G930S`, API 26) & 갤럭시 A30 (`SM-A305N`, API 30) 실기기 배포·실측**: 현재 PC에 USB 미연결(`adb devices -l` 기준 S6 `06157df622d5ad1d` 단독 연결). S7/A30 USB 연결 후 `보르타_통합앱_OS버전별_설치_원클릭.bat` 실행 필요.
3. **S6 3.5mm 물리 AUX 잭 삽입/발거 테스트**: S6 `EarJack bPluged : false` 상태로 물리 유선 케이블 삽입·이탈 재연결 테스트 미실행.
