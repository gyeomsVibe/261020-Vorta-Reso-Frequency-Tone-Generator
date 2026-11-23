# MIA 11단계 전략기획 · v5.2 UX 융합 · 갤럭시 A30 실기 비교분석서

> **카드 ID**: `U330-MIA-VORTA-V52-UX-SCOPE-A30`  
> **대상**: `보르타 통합앱 v5.2 (com.vorta.unified, versionCode=520)` 및 보존·최적화된 개별 스페어 4종 (`smato_a30_app`, `vorta_s7_app`, `vorta_s6_app`, `vorta_freqgen_app`)  
> **엄격 제외**: `vorta_aegis_app` 및 개인 기기 `Galaxy Note 20 Ultra 5G (SM-N986N)` 일절 미수정·무개입

---

## 1. MIA 11단계 표준 전략 실행 추적 (`Stage 0 ~ Stage 10`)

| 단계 | 수행 내용 | 검증 근거 및 판정 |
| :--- | :--- | :--- |
| **Stage 0. 사전 웹딥리서치** | 상용 주파수 발생기·오실로스코프·안드로이드 공식 Audio HAL 문서 심층 조사 (세부 URL 하단 표 참조) | 5대 UX/계측 기준 도출 (`PASS`) |
| **Stage 1~2. 기획 · 검증** | 할루시네이션 0 실시간 파형/THD/변조 표기, `320dp` 겹침 제거, 구버전 5탭 + 전량 168 시나리오 복원 + Pro `변조` 순수 3채널(FM/AM/RM) 고정 및 12종 시나리오 `프리셋` 이전 설계 | 요구사항 전 항목 매핑 (`PASS`) |
| **Stage 3~4. 큰계획 · 검증** | `PureDspCore` / `VortaAudioService` / `LiveWaveformView` + `ModulationProfile`(순수 3채널 고정) + `ScenarioCatalog.proScenarios()`(12종) + `MainActivity` 출력창 `mTvTeleMod` 설계 | `vorta_aegis_app`/개인기기 격리 및 스페어 4종 보존 검증 (`PASS`) |
| **Stage 5~6. 세부계획 · 검증** | `320dp` S6 폭(`48k FLOAT` + `변조:` 1줄 무잘림), `650/1000ms` 무플리커 틱, `-1.0 dBTP` 클리핑/THD 최소자승 실측, `VortaAudioService` 자가치유 가드, `Harsh_G10~G12` 설계 | `minSdk=21` (`!isNaN && !isInfinite`) 호환 검증 (`PASS`) |
| **Stage 7~8. 구현계획 · 검증** | 수정 전 원본 `.work/backup_261122/` 백업 선행 및 순수 변조(`ModulationProfile.java`)·시나리오(`ScenarioCatalog.java`)·UI(`MainActivity.java`) 분리 구현 | 백업 무결성 확보 (`PASS`) |
| **Stage 9. 구현** | Pro `변조` 탭 순수 3채널(`FM`/`AM`/`RM`, 0 SeekBar, 0 시나리오)+`전체 OFF`, `프리셋` `시간·상황·전술 시나리오 (12종)` 이전, 출력창 `mTvTeleMod` + 자가진단 `현재 변조:` 반영 | 5개 프로젝트 7개 APK 전량 `apksigner verify` `exit 0` (`PASS`) |
| **Stage 10. 디버깅 · 실기 검증** | 호스트 JVM(`Epoch1` `21/21`, `Harsh` `13/13`, `183/183` 스펙트럼) + 실기 `verify_all_devices_harsh.py` (S6·A30 `2/2` + S7 `1/1` Oreo PID 보강) + 잠금 해제 S6(`5/5`)·S7·A30 실화면 무음 UI·변조 표기 실증 | 전용 발진기 3종(S6·S7·A30) 전량 실기 UI/오디오 PASS (`DONE/S6-S7-A30-ALL-3-VERIFIED`) |

### Stage 0 웹딥리서치 벤치마크 출처
1. **Keuwlsoft Dual Channel Function Generator**: https://play.google.com/store/apps/details?id=com.keuwl.functiongenerator — 듀얼 톤/스윕/변조 상태 한눈에 띄우는 다크 계측 UI 및 원탭 마스터 정지 밸브 벤치마크.
2. **RWTH Aachen Phyphox**: https://phyphox.org/ — 센서·신호 실험의 정직한 실측값 표기 및 비측정 항목 과장 배제 원칙 벤치마크.
3. **Spectroid**: https://play.google.com/store/apps/details?id=com.intoorbit.spectrum — 저노이즈 OLED 다크 배경 위 고대비 실시간 신호 시각화 및 불필요한 장식 배제 설계.
4. **AudioTool**: https://play.google.com/store/apps/details?id=com.julian.apps.AudioTool — 모바일 화면에서 계측 수치와 제어 버튼 간 층 분리를 통한 오조작·겹침 방지 패턴.
5. **billthefarmer/scope (`Canvas` 오실로스코프 참조)**: https://github.com/billthefarmer/scope — `onDraw()` 내 객체 할당 없이 Zero-Cross 상승 트리거로 정현파를 고정 그려내는 순수 Android `Canvas`/`Path` 패턴.
6. **Android `AudioTrack` (`ENCODING_PCM_FLOAT` / `getUnderrunCount`) 공식 문서**: https://developer.android.com/reference/android/media/AudioTrack — API 24+ `getUnderrunCount()` 기반 OS HAL 버퍼 언더런 실측 및 32-bit IEEE Float 파이프라인 적용 근거.

---

## 2. 3자 UX 비교 분석 · 전량 168+12 시나리오 · 순수 변조 고정 · 출력창 변조 표기

> **핵심 대상 명확화**: 본 UX 개편의 직접 목표는 **보르타 통합앱 v5.2 (`com.vorta.unified`)**이며, 4종의 기존 개별 앱(`smato_a30_app`, `vorta_s7_app`, `vorta_s6_app`, `vorta_freqgen_app`)은 사용자 스페어로 삭제 없이 보존·최적화했습니다.

| UX 축 | 구버전 개별앱 장점 | 웹딥리서치 상용 계측기 패턴 | 보르타 통합앱 v5.2 (`MainActivity.java`) 최종 융합 |
| :--- | :--- | :--- | :--- |
| **1. 기기별 시나리오 5탭 & 전량 복원** | 상단 `메인·서브1·서브2·공통·기초` 1탭 즉시 전환 + 기종별 전체 레거시 풀셋 | 평탄한 단일 계층 탭(다중 아코디언 중첩 배제) | 중첩 아코디언 완전 폐기, **`메인 \| 서브1 \| 서브2 \| 공통 \| 기초`** 직결 5탭 + **`168`종 기기 시나리오 전량 복원**(`S6=75`: `20+11+10+12+22`, `S7=86`: `20+16+16+12+22`, `A30=75`: `20+11+10+12+22`) 및 각 기기 `실증 연구 확장 (3)`을 `메인` 인덱스 4..6에 배치 |
| **2. Pro `변조` 순수 고정 & 12종 시나리오 `프리셋` 이전** | 수동 변조 설정 + 스윕·프리셋 분리 | 오조작 차단용 검증 파라미터 고정 스위치 + 스케줄 프리셋 분리 | `변조` 탭에서 **`SeekBar`와 모든 시나리오를 완전 제거(`0 SeekBars`, 시나리오 0건)**하고 순수 3채널(**`FM 3.5%(1.50Hz)`**, **`AM 15%(4.00Hz)`**, **`RM 25%(12.50Hz)`**) + `전체 OFF`만 고정 탑재(`스윕` 상단에 현재 변조 요약·`변조 설정` 이동 바 제공). 기존 12종 시간·상황·전술 시나리오는 Pro **`프리셋`** 탭 **`시간·상황·전술 시나리오 (12종)`** 기본 접힘 아코디언으로 이전 |
| **3. 출력창·자가진단 실시간 변조 표기** | 상단 계측창 중심 모니터링 | 현재 출력 주파수와 함께 활성 변조 파라미터 즉시 노출 | 상단 출력창(`mTvTeleMod`)과 `실측 자가진단` 모달(`현재 변조:`)에 실시간 변조 상태 표시 (`변조: OFF (순수 정현파)`, `변조: FM 3.5%(1.50Hz) · 고정`, `변조: AM 15%(4.00Hz) · 고정`, `변조: FM 10.0%(7.03Hz) · 고정`, `변조: 시나리오별 최적값 자동 적용`) |
| **4. 시나리오 카드 1탭 실행 & 타이포** | 카드 어디든 눌러 즉시 발진 + 깔끔한 그룹 헤더 | `(NEW)`, `[ISO xxxx]`, 이모지·특수 기호(`↔` 등) 제거 | 슬림 카드 전체 1탭 즉시 `재생`/`중지` 바인딩, 1줄 섹션 헤더, **`(NEW)`·`[ISO]`·`↔` 블루박스·이모지 0건** |
| **5. 텔레메트리 층 분리 & 대기 안정성** | 원형 신호등 LED 점 상태 인지 | 계측 수치 + 파형 + 제어 버튼 층 분리 | Row 1(`10dp` LED + 상태 + 볼륨가드), Row 2(`22sp` Hz + `76dp` `전체 정지`), Row 3(`52dp` `LiveWaveformView`), Row 4(`48k FLOAT · THD/복합파형 · dB · 끊김`), Row 5(`mTvTeleMod` 실시간 변조줄), Row 6(설명), Row 7(`화면유지` + `실측 자가진단`) |

---

## 3. 무할루시네이션 실시간 신호 검증 아키텍처 & 백신 게이트 (`Harsh_G11`)

1. **실제 전송 버퍼 기반 수치 계측 (`PureDspCore.measureLiveBufferQuality`)**:
   - `VortaAudioService`가 `AudioTrack.write()`로 OS HAL에 전달하는 **동일 `floatBuf` 샘플**을 크기 `4096`(`SCOPE_RING_SIZE`) 링 버퍼에 기록하고, `480 ms` 주기로 직접 계측합니다.
   - 계측 항목: True Peak, RMS(dBFS), DC 오프셋, 클리핑/NaN(`|s| >= 0.999` 또는 비유한값) 건수, API 24+ OS `AudioTrack.getUnderrunCount()` 누적 버퍼 끊김 횟수.
   - 순수 단일음(`3x3` `[1, cos, sin]`)·이중음(`5x5`)일 때만 가우스 소거 결합 최소자승법으로 **`THD+N%`를 실측 표기**하며, 스윕/FM/AM/RM 복합 파형에서는 허위 THD 숫자를 만들지 않고 `-1.0`(`복합파형`)으로 정직하게 분기합니다 (A30 `아침` FM 스윕 실측 `48k FLOAT · 복합파형 · -3.9dB · 끊김 0` + `변조: FM 10.0%(7.03Hz) · 고정`, S6 `43.20 Hz` 순음 실측 `48k FLOAT · THD 0.000% · -4.2dB · 끊김 0` + `변조: OFF (순수 정현파)`).
2. **실시간 순수 Canvas 오실로스코프 (`LiveWaveformView.java`)**:
   - `64 ms`(`DISPLAY_WINDOW_SAMPLES = 3072`) 창에서 상승 영점 교차(`<= 0` → `> 0.005`) 트리거 후 `256`포인트 데시메이션 파형을 그립니다 (`±0.89125` `-1.0 dBTP` 기준선 포함; 순음 녹색 `#10B981`, 복합/스윕 호박색 `#F59E0B`, 클리핑 적색 `#EF4444`, 정지 회색 `#475569` 평선).
3. **자가진단 모달 (`VortaAudioService.buildSelfDiagnosticReport`)**:
   - `실측 자가진단` 버튼 터치 시 판정 상태, 타겟 OS 프로필(`Android 7.0 API 24 · WM1840` / `Android 11 API 30 · 순수 음원`), 샘플레이트/포맷(`48000 Hz / 32-bit Float`), `현재 주파수`, **`현재 변조`**, 피크/RMS/DC/THD+N/HAL 언더런/클리핑 실측값을 즉시 표시합니다.
4. **백신 테스트 실증 (`Harsh_G11_LiveSignalVerifierNoHallucination`)**:
   - 정상 `43.2 Hz` 순음 입력 시 `cleanTHD=0.00000%`, `clip=0`으로 통과하고, 인위적 3차 고조파(`0.15`) + 클리핑(`1.05f`) 주입 시 `corruptTHD=16.86%`, `clip=1`로 왜곡을 즉각 적발함을 입증했습니다.
5. **정직한 검증 경계 (Zero-Hallucination Disclosure)**:
   - 앱 내 파형·THD+N·언더런 지표는 **앱이 OS `AudioTrack` HAL로 송출하는 DAC 직전 32-bit Float PCM 신호와 HAL 버퍼 상태의 실측값**입니다. 외부 3.5mm 케이블, 레피/스마토 앰프, 트랜스/우퍼 스피커의 물리적 음압 및 아날로그 왜곡은 별도의 외장 마이크·오실로스코프 계측으로 확인해야 합니다.

---

## 4. 갤럭시 A30 (`SM-A305N`, `R59N105TEYF`) 실기 전수분석 및 S6/S7 교차 최적화

실기 연결(`device_snapshot_SM-A305N_R59N105TEYF.txt`)에서 직접 확인한 원천 팩트는 아래와 같습니다.

| 계측 항목 | 갤럭시 A30 실측값 (`SM-A305N`) | 갤럭시 S6 실측값 (`SM-G920K`) | S6/S7 교차 최적화 반영 |
| :--- | :--- | :--- | :--- |
| **SoC / ABI / 부트로더** | Exynos 7904 (`universal7904`, `arm64-v8a`), S-Boot `A305NKSS5CWA1`, `warranty_bit=0`, `vbmeta=locked` | Exynos 7420 (`samsungexynos7420`, `arm64-v8a`), S-Boot `G920KKKU3EVG1`, `warranty_bit=1` | 기기 모델별 Flavor(`s6_api24`/`s7_api26`/`a30_api30`) 엄격 유지 |
| **OS / CSC / 디스플레이** | Android `11` (`API 30`, 패치 `2023-02-01`, `SKC` / `A305NOKR5CWA1`), `1080x2340` (`360dp`), `mScreenOffTimeoutSetting=600000` | Android `7.0` (`API 24`, 패치 `2018-06-01`, `KTC`), `1440x2560` (`4.5` 스케일 = `320dp`), `600000` | 최협소 `320dp`(S6) 기준으로 UI·탭·출력창 변조줄 설계 (`360dp` A30 여유 확보) |
| **안전볼륨 상태** | `audio_safe_volume_state=1`, `mSafeMediaVolumeState=SAFE_MEDIA_VOLUME_DISABLED`, `SEC_AUDIO_SAFE_MEDIA_VOLUME=false` | `audio_safe_volume_state=3→2`, 20h 타이머 및 9단 유선 클램프 활성 | A30은 `ENABLE_VOLUME_GUARD=false`(`순음 고정(볼륨 무개입)`); S6/S7은 `VortaVolumeGuard` + `VortaAudioService.onCreate()` 자가치유 가드로 `am force-stop` 후에도 `state=2 vol_ctrl=null` 즉시 복원 |
| **`STREAM_MUSIC` 인덱스** | `speaker=15/150`, `headset=13/130`, `headphone=14/140`, `bt_a2dp=14/140`, `usb_headset=13/130` | 유선 클램프(`9/15`) 우회 및 사용자 볼륨 보존 | `persistWiredTargetVolume`로 수동 볼륨값만 보존, 85% 강제고정 배제 |
| **오디오 HAL / 믹서 게인** | Exynos `ABOX` (`ro.config.systemaudiodebug=abox`), `/vendor/etc/mixer_gains.xml` `gain-media-headset`: `DAC Gain=140`, `Headphone Volume=0`, `HP Analog Volume AVCBypass=58`, `48000 Hz` `chunkFrames=2880` `ENCODING_PCM_FLOAT` | Wolfson `WM1840` (`arizona`), `mixer_paths.xml` (`Digital Volume=128`, `Noise Gate=0`), `48000 Hz` `chunkFrames=2880` `ENCODING_PCM_FLOAT` | 전 기종 `48000 Hz` / `chunkFrames=2880` / `32-bit Float` + `AUX Guard`(250ms 디바운스·`setPreferredDevice(null)`) 통일 |
| **순수 음장 플래그 기준** | `dolby_atmos_mode=0`, `sound_alive_effect=0`, `tube_amp_effect=0`, `uhqa_effect=0`, `mono_audio=0` | 기존 12종 음장 차단 flags 활성 | **`dolby_atmos_mode=0` 신규 추출 → `VortaVolumeGuard.java` 및 `A30VolumeHelper.java` 전 기기 교차 반영 완료** |

---

## 5. 최종 빌드 산출물 및 실증 테스트 결과

### 5.1 빌드 APK 일람 (`apksigner verify` 전수 PASS, `exit 0`)
- **보르타 통합앱 v5.2 (`com.vorta.unified`, `versionCode=520`, `versionName=5.2.0`)**:
  - `vorta_unified_app/vorta_unified_s6_api24.apk`: `308,095 B`
  - `vorta_unified_app/vorta_unified_s7_api26.apk`: `308,095 B`
  - `vorta_unified_app/vorta_unified_a30_api30.apk`: `308,095 B`
- **보존·최적화 개별 스페어 4종 (무삭제 보존)**:
  - `smato_a30_app/smato_a30_pro.apk`: `308,095 B` (`5탭 정비 + dolby_atmos_mode=0 반영`)
  - `vorta_s7_app/vorta_s7_pro.apk`: `291,711 B` (`5탭 정비 + 대기 타이머 틱 중지`)
  - `vorta_s6_app/vorta_s6_pro.apk`: `291,711 B` (`5탭 정비 + 대기 타이머 틱 중지`)
  - `vorta_freqgen_app/vorta_freqgen_pro.apk`: `177,023 B` (`ISO·(NEW) 태그 정리`)

### 5.2 호스트 JVM & 실기기 검증 결과 (`exit 0`)
- **`TestVortaEpoch1Audit`**: `PASSED=21, FAILED=0` (`exit 0`, `Gate5_TotalCatalog168 total=168`: `S6=75`, `S7=86`, `A30=75`)
- **`TestVortaHarshAudit`**: `PASSED=13, FAILED=0` (`exit 0`, 전 `183` 스펙트럼 시나리오 `PASSED=183, FAILED=0`, `MaxPeak=0.89125`, `WorstPureSineTHD+N=0.000692%`, `Harsh_G10` `mTvTeleMod`·`modLockedNoSeekBar=true`·`pureModNoScenarios=true`, `Harsh_G11` `cleanTHD=0.00000% clip=0 | corruptTHD=16.86% clip=1`, `Harsh_G12` `channels=3 combos=8 proScenarios=12 pureTHD=0.000002% maxPeak=0.89125`)
- **`verify_all_devices_harsh.py` (S6·A30·S7 전 3기종 실기 가혹 검증 `exit 0`)**:
  - **Galaxy S6 (`06157df622d5ad1d`, `SM-G920K`)**: `versionCode=520` + Pro + S6 스페어 보존, `6/6 AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`, `AudioFlinger active=True 48kHz=True`, `RUNNING_CRITICAL` 생존, Direct-Binder `state 3→2` (`vol_ctrl=null`), `crash_hits=0`, `remaining_audio_services=[]`.
  - **Galaxy A30 (`R59N105TEYF`, `SM-A305N`)**: `versionCode=520` + Pro + A30 스페어 보존, `6/6 AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`, `AudioFlinger active=True 48kHz=True`, `RUNNING_CRITICAL` 생존, `audio_safe_volume_state=1`, `mono_audio=0`, `sound_alive_effect=0`, `dolby_atmos_mode=0`, `crash_hits=0`, `remaining_audio_services=[]`.
  - **Galaxy S7 (`ce021602916dbb3804`, `SM-G930S`, Android 8.0 API 26)**: `versionCode=520` + Pro + S7 스페어 보존(`vorta_aegis_app` 무개입), `6/6 AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`, Oreo PID 기반 `AudioFlinger active=True 48kHz=True`, `VortaAccessibilityService bound=True`, `mono_audio=0`, `audio_safe_volume_state=2`, `crash_hits=0`, `remaining_audio_services=[]` (스냅샷: [device_snapshot_SM-G930S_ce021602916dbb3804.txt](file:///d:/D_Workspace_NB/주파수/.work/device_snapshot_SM-G930S_ce021602916dbb3804.txt)).
- **잠금 해제 S6 실화면 5단계 검수 (`s6_ui_audit.py`, `exit 0`, `[ALL STEPS 1-5 VERIFIED PASS ON UNLOCKED S6]`)**:
  - S6 `320dp` 겹침 0건(`hz_x2=954<=990`, `keep_y1=1239>=671`, `keep_x2=706<=733`), `FLAG_KEEP_SCREEN_ON` ON/OFF 토글, 드로어 순서(`1. 보르타 Pro -> 2. 갤럭시 A30 -> 3. 갤럭시 S7 -> 4. 갤럭시 S6`), 좀비 스윕 부활 0건, Pro `프리셋` 5아코디언 기본 접힘 및 `시간·상황·전술 시나리오 (12종)` 확인, `변조` 탭 `SeekBar` 0건 & 시나리오 0건 & 순수 FM/AM/RM·`전체 OFF`↔출력창 `변조:` 동기화, 5탭 전환, `43.20 Hz` 1탭 발진(`48k FLOAT · THD 0.000% · -4.2dB · 끊김 0`, `변조: OFF (순수 정현파)`), Step 5 `state=2 vol_ctrl=null`, `Crash hits=0`.
  - 실측 스크린샷 확인: `.work/s6_v52_pro_mod_locked.png`, `.work/s6_v52_pro_mod_fm_on.png`, `.work/s6_v52_pro_scen_moved.png`, `.work/s6_v52_playing_scope.png`, `.work/s6_v52_diag_dialog.png`.
- **잠금 해제 A30 실화면 UI·오디오 검수 (`a30_ui_audit.py`, `exit 0`, `[A30 LIVE UNLOCKED UI AUDIT PASS]`)**:
  - `1. 보르타 Pro` 대기 출력창 `변조: AM 15%(4.00Hz) · 고정` 및 자가진단 모달 `현재 변조: AM 15%(4.00Hz) · 고정`, `2. 갤럭시 A30` 전환 후 `순음 고정(볼륨 무개입)`, `A30 · SPK`, 5탭, 금지 태그 0건, `17.39 Hz` 1탭 발진(`48k FLOAT · 복합파형 · -3.9dB · 끊김 0`, `변조: FM 10.0%(7.03Hz) · 고정`), `AudioFlinger 48kHz`, `audio_safe_volume_state=1` 확인.
  - 실측 스크린샷 확인: `.work/a30_v52_home.png`, `.work/a30_v52_playing.png`, `.work/a30_v52_diag.png`.
- **잠금 해제 S7 실화면 무음 UI·변조·오실로스코프 검수 (`s7_ui_audit.py`, `exit 0`, `[S7 LIVE UNLOCKED AUX-MUTED UI AUDIT PASS]`)**:
  - S7 `1080x1920`(`360dp`) 겹침 0건, `FLAG_KEEP_SCREEN_ON` OS 플래그 토글(`#81810100 -> #81810180 -> #81810100`), `실측 자가진단` 모달(`Android 8.0 API 26 · AudioOut_D` + `현재 변조: OFF (순수 정현파)`), Pro `변조` 0 SeekBar·0 시나리오 & `FM ON -> 변조: FM 3.5%(1.50Hz) · 고정` -> `전체 OFF`, `프리셋` 5아코디언 기본 접힘(`시간·상황·전술 시나리오`), `3. 갤럭시 S7` `서브1(16)`/`서브2(16)` 복원 확인 및 `메인` `아침(28.00 Hz)` 1탭 무음 발진(`48k FLOAT · 복합파형 · -5.2dB · 끊김 0`, `변조: AM 15%(3.20Hz) · 고정`, `AudioFlinger 48kHz`), `safe_vol=2`, `crash_hits=0`.
  - 실측 스크린샷 확인: `.work/s7_v52_home.png`, `.work/s7_v52_pro_mod.png`, `.work/s7_v52_playing.png`, `.work/s7_v52_diag.png`.
- **운용 범위**: 전용 발진기 3종(**S6 · S7 · A30**) 전량 설치 및 실기 UI·오디오 검증 완료 (`Note 20 Ultra 5G` 개인 기기는 정책상 엄격 제외).
