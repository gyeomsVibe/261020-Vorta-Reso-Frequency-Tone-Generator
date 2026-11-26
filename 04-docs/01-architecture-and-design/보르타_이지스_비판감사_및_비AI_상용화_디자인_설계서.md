# 보르타 이지스 (Vorta Aegis Pro) 비판·레드팀 전수감사 및 상용화 비(非)AI 디자인 최적화 설계서

> **대상 시스템**: `com.vorta.aegis` (`vorta_aegis_app`, Target: Galaxy Note 20 Ultra Android 13 / Back-compat: Galaxy S6 Android 7.0)  
> **감사 기준**: v1.0.0 실코드 정밀 분석 · Note 20 Ultra 실기기 스크린샷(`note20u_aegis_screen.png` 등 5종) · M3/One UI 5.1/HIG/NN/G 공식 규격  
> **문서 구분**: 유지관리 정본 (Maintained Architecture & Design Spec)

---

## PART 1. `/CRITIC` · `/SELFREFINE` · `/REDTEAM` 전수 감사 보고서

### 1-1. 심각도별 결함 전수 목록 (v1.0.0 실코드·실기기 검증 근거)

| ID | 분류 | 심각도 | 실측 위치 및 근거 | 결함 내용 | v2.0.0 해결책 |
|---|---|---|---|---|---|
| **C-01** | DSP 음향 결함 | **P1** | `AegisAudioService.java` L278–281 | `if (p.isAntiPhase) sampleR = -Math.sin(mPhaseL);` 분기가 `mPhaseR`(`freq2`)을 완전히 무시하여, 안티 단파 01/02/04/07/09의 핵심 안정화 버퍼 주파수(`10Hz`, `7.83Hz`, `3Hz`, `528Hz`, `56.2Hz`)가 실제 출력에서 **전혀 소리가 나지 않았음**. 또한 스마트폰 스테레오 스피커에서 좌우 동일 파형을 $180^\circ$ 반전 송출 시 근접장 음향 다이폴 자기상쇄(Acoustic Dipole Self-Cancellation)로 방사 에너지가 급감함. | `isAntiPhase`일 때도 `binauralBeat > 0`이면 양 채널 역위상 차음 유지, `freq2 > 0`이면 `-0.65·sin(φL) + 0.35·sin(φR)` 코히어런트 믹싱, 단독이면 코히어런트 동위상 역전파(`-sin(φL)`) 출력으로 모든 설계 주파수를 무손실 송출. |
| **C-02** | 동시성·상태 동기화 | **P1** | `AegisAudioService.java` L45, L135, L188, L320–342 | ① `mCurrentPreset`가 `volatile`이 아님. ② `stopPlayback()`(`mFadeOut=true`) 직후 0.5초 이내 새 프리셋을 누르면 `loadAndPlayPreset`가 `mFadeOut`을 해제하지 않아 재생 도중 강제 종료됨. ③ `mFadeOut` 완료 시 `break` 이후 버퍼 뒷부분(과거 프레임 잔여 데이터)까지 `AudioTrack.write`로 송출해 틱/글리치 발생. ④ 알림창 "발진 중지"나 형제 앱 양보로 서비스가 종료돼도 `MainActivity`에 브로드캐스트를 보내지 않아 UI LED가 거짓 `🟢 발진 중`으로 남음. | `volatile PresetModel`, `loadAndPlayPreset` 시 `mFadeOut = false` 복원, 중지 시 실제 렌더링된 샘플 길이만 쓰거나 0 패딩 후 안전 종료, `ACTION_STATE_CHANGED` 로컬 브로드캐스트로 UI 즉시 동기화. |
| **C-03** | 독립 볼륨 엔진 부재 | **P1** | `AegisAudioService.java` L56, L202, L330 | `mMasterGain = 0.8`이 UI 어디에도 연결되지 않음. 단순 선형 곱셈이라 청감상 음량 조절이 부자연스럽고, 스무딩 필터가 없어 드래그 시 지퍼 노이즈(Zipper Noise) 위험이 있으며, 앱 재시작 시 볼륨이 초기화됨. | `-40dB ~ 0dB` 로그 감쇄 곡선(`VolumeCurve`), 샘플당 1차 IIR 지수 평활화(`alpha=0.0014`, ~15ms), 뮤트, `SharedPreferences` 영구 저장, NN/G 하이브리드 슬라이더+스테퍼+디텐트 UI 탑재. |
| **C-04** | DSP 파형 아티팩트 & 메모리 | **P2** | `AegisAudioService.java` L235, L301–304, `PresetModel.java` L54 | ① 스윕이 `0→1` 톱니형으로 감기면서 주기 끝에 주파수가 급락(`4.5→1.0Hz` 등)해 청각 충격 유발. ② `totalFramesRendered`가 프리셋 전환 시 초기화되지 않아 스윕이 중간 주파수부터 시작함. ③ 잠자리 날갯짓(`noiseType==3`)이 `1.0 / 0.2` 사각파 계단 전환이라 고조파 클릭 노이즈 유발. ④ `getAllPresets()`가 매 호출마다 48개 객체를 새로 할당함. | 삼각 핑퐁 스윕(`0→1→0`)으로 연속 왕복, 프리셋 전환 시 `mPresetFrameCounter = 0` 초기화, Raised-Cosine(`0.6 + 0.4·sin`) 연속 잠자리 변조, `PresetModel` 싱글톤 캐싱. |
| **C-05** | Android 13 / One UI 안정성 | **P2** | `AegisAudioService.java`, `AndroidManifest.xml`, `MainActivity.java` | `WAKE_LOCK` 권한만 선언하고 `PARTIAL_WAKE_LOCK` 미획득(딥슬립 시 스레드 스로틀링). `AudioFocusRequest` 및 이어폰 분리 감지(`ACTION_AUDIO_BECOMING_NOISY`) 미구현. `startForeground`에 `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` 상수 미전달. 헤더에 `SM-N986N` 문자열 하드코딩. | `PARTIAL_WAKE_LOCK` 획득/해제 관리, `AudioFocusRequest`(덕킹 `0.3x`·손실 시 정지), `ACTION_AUDIO_BECOMING_NOISY` 즉시 정지 안전장치, API 29+ 포그라운드 타입 명시, `Build.MODEL` 동적 표시. |
| **C-06** | AI Slop UI/UX 결함 | **P2** | `MainActivity.java`, `note20u_aegis_screen.png` | ① 6종 고채도 네온 색상 남발. ② 12종 이상 이모지 난립. ③ 카드 제목이 우측 배지(`★보르타 A30/FreqGen 대응`)와 한 줄에 놓여 4줄로 심하게 찌그러짐. ④ 연타 시 Toast 알림 겹침. ⑤ 현재 어떤 카드가 발진 중인지 카드 리스트에 하이라이트가 없고, 스윕 실시간 주파수가 대시보드에 표시되지 않음. | Obsidian + Amber 2-레이어 톤 시스템, 이모지 0개, 배지 상단 분리로 제목 전체폭 확보, 토스트 스팸 제거 및 햅틱+인라인 활성 카드 테두리 하이라이트, 250ms 라이브 Hz 계측기 도입. |
| **RT-01** | 보안 경계 (CWE-926) | **P1** | `AndroidManifest.xml` L39 | `AegisAudioService`에 `android:exported="true"`가 설정되어 악성 외부 앱이 임의의 `ACTION_START`/`ACTION_STOP` 인텐트로 스피커를 무단 제어 가능함. | `android:exported="false"`로 즉시 잠금 (형제 앱 양보는 앱 내부 리시버가 아니라 `ACTION_EXCLUSIVE_AUDIO_START` 브로드캐스트로 처리되므로 기능 영향 0). |
| **RT-02** | 음향 물리 정직성 | **정보/UX** | 물리 음향 한계 | 스마트폰 마이크로 스피커는 물리 크기 한계로 150~200Hz 미만 방사가 급감하며, 외부 마이크로 벽면 위상을 실측하지 않는 개방 공간 180° 반전파는 외장 앰프·스피커 결선이나 음향 마스킹·바이오피드백 보조 용도로 운용해야 함. | 앱 하단 안내 및 설계서에 `<60Hz: 외장 우퍼/앰프 결선`, `바이노럴 비트: 이어폰/헤드폰`, `174~852Hz·마스킹: 폰 스피커 직접 재생` 출력 경로 가이드를 명확히 분리 표기. |
| **RT-03** | 상용 스토어 정책 & 청력 보호 | **컴플라이언스** | `PresetModel.java` 의료 효능 표현 | Google Play 출시 시 "우울증을 없애주는", "자연 마취", "DNA 복구" 등 직접적 의료 효능 단정은 의료기기/허위 과장 정책 심사 거절 위험이 있음. 또한 고출력·초음파 재생 시 이어폰 분리 안전장치가 필수임. | 상용 스토어 배포용 카피 가이드라인(이완·집중·휴식 보조 표현) 수립 및 `ACTION_AUDIO_BECOMING_NOISY`·기본 `75% (-10.0 dB)` 안전 헤드룸 강제 적용. |
| **RT-04** | 기기 배포 드리프트 | **운영** | `adb shell dumpsys package com.vorta.aegis` | Note 20 Ultra 실기기에 `versionCode=1 minSdk=26` 구버전(11/15)이 남아 있어 U321의 `minSdk=21`·`1920` 프레임 버퍼·상호 양보 패치가 반영되지 않았음. | U321 호환성(`minSdk=21`, `d8 --min-api 21`, `1920` 프레임)을 모두 계승한 `v2.0.0 (versionCode=2)` 단일 정본으로 즉시 업그레이드. |

---

## PART 2. 상용화 "비(非)AI 티" 앱 디자인 시스템 최적화 설계서

### 2-1. 디자인 철학: "Precision Acoustic Instrument" (정밀 음향 계측기)
> Teenage Engineering(OP-1), Braun(Dieter Rams), Endel, Dark Noise, Samsung One UI 5.1의 공통 분모를 추출하여, 장식적 네온 그라데이션을 제거하고 **"신뢰할 수 있는 물리적 음향 기기"**의 감각을 구현한다.

1. **탈(脫) AI Slop 5대 원칙**:
   - **원칙 1 (단일 포인트 + 톤 계층)**: 배경은 `#0E0F11`(Obsidian Charcoal), 카드는 `#17181C`(Surface Container), 활성 요소는 `#202228`(Surface High)으로 M3 Tonal Elevation을 형성한다. 포인트 컬러는 산업용 앰버(`#E5A93C`) 단일 색으로 절제하고, 신호등 상태색(`#00E676` 가동 / `#FF3B5C` 정지)은 LED와 정지 버튼에만 엄격히 제한한다.
   - **원칙 2 (이모지 전면 퇴출)**: UI 텍스트 어디에도 컬러 이모지를 쓰지 않는다. 모든 아이콘은 동일 선폭(`1.75dp`, 24dp 뷰포트)의 벡터 드로어블(`ic_aegis_shield`, `ic_volume_on`, `ic_volume_mute`, `ic_stop_square`)로 통일한다.
   - **원칙 3 (고정폭 숫자 `tnum` 계기판)**: 스윕 주파수(`18.98 Hz`)와 볼륨(`75% · -10.0 dB`)은 `Typeface.MONOSPACE` + `fontFeatureSettings="tnum"`을 적용해 수치 변화 시 글자 흔들림(Jitter)을 0으로 만든다.
   - **원칙 4 (One UI 5.1 엄지 인체공학)**: 좌우 여백 `20dp`, 볼륨·중지·카테고리 필 등 주요 터치 타깃 최소 높이 `44~48dp`, 탭 선택 시 고대비 흑백 반전(`#F2F3F5` 배경 / `#0E0F11` 텍스트) 적용.
   - **원칙 5 (텍스트 숨통 확보)**: 카드 배지는 제목 우측에서 떼어내 카드 최상단 Eyebrow 행으로 분리하고, 제목이 화면 전체 너비를 쓰도록 하여 1~2줄 내 완벽히 읽히게 한다.

### 2-2. 컬러 토큰 레지스트리 (`colors.xml`)
| 토큰명 | Hex | 역할 (M3 / One UI) | 대비 및 용도 |
|---|---|---|---|
| `bg_amoled` | `#0E0F11` | `surface` | 전체 화면 기본 바탕 (OLED 스미어링 방지) |
| `surface_container` | `#17181C` | `surface-container` | 대시보드 및 기본 프리셋 카드 배경 |
| `surface_high` | `#202228` | `surface-container-high` | 활성 프리셋 카드, 스테퍼/디텐트 비활성 필 |
| `outline_subtle` | `#2A2D35` | `outline-variant` | 1dp 미세 경계선 및 구분선 |
| `accent_amber` | `#E5A93C` | `primary` | 실시간 주파수(Hz), 볼륨(dB), 시커바 진행 트랙 |
| `accent_amber_bg` | `#262015` | `primary-container` | 활성 볼륨 디텐트 칩 배경 |
| `status_active` | `#00E676` | `semantic-running` | LED 점등, `[발진 중]` 상태 라벨, 활성 카드 1.5dp 테두리 |
| `status_stopped` | `#FF3B5C` | `semantic-stopped` | 정지/뮤트 상태, 발진 중지 버튼 |
| `text_primary` | `#F2F3F5` | `on-surface` | 제목, 주요 라벨 (대비비 > 15:1) |
| `text_secondary` | `#9A9FA8` | `on-surface-variant` | 설명, 보조 메타데이터 (대비비 > 6.5:1) |
| `text_tertiary` | `#656B76` | `outline` | Eyebrow, 푸터 안내, 비활성 계측 수치 |

### 2-3. 별도 독립 볼륨 조절부 UX 아키텍처
- **핵심 원칙**: Android 시스템 미디어 볼륨(`STREAM_MUSIC`)을 절대 건드리지 않으면서(영구불변원칙 1조), 오디오 엔진(`AegisAudioService`) 내부 출력 게인을 독립 제어한다.
- **신호 처리 경로**:
  $$\text{Slider } p \in [0, 100] \xrightarrow{\text{Log Curve}} \text{dB} = -40 \cdot \left(1 - \frac{p}{100}\right) \longrightarrow G_{\text{target}} = 10^{\text{dB}/20}$$
  $$G[n] = G[n-1] + 0.0014 \cdot \left(G_{\text{target}} \cdot M_{\text{mute}} \cdot M_{\text{duck}} - G[n-1]\right)$$
- **NN/G 4중 제어 인터페이스**:
  1. **상단 수치 계측**: `75% · -10.0 dB` (고정폭 `tnum`) + 우측 시스템 볼륨 읽기 전용 표시 `시스템 12/15`.
  2. **커스텀 트랙/썸 SeekBar**: 손가락 오클루전을 피하기 위해 트랙 위에 실시간 수치를 고정 배치, 25%/50%/75%/100% 통과 시 미세 햅틱 틱.
  3. **정밀 스테퍼 & 원터치 뮤트**: 좌측 뮤트 버튼 + `−` / `+` (`5%` 단위 미세 조정).
  4. **빠른 디텐트 칩 4종**: `25% 야간` · `50% 배경` · `75% 권장` · `100% 최대` (원탭 스냅).

### 2-4. 상용화 로드맵 (v2.0.0 즉시 적용 vs v3.0.0 스토어 확장)
- **v2.0.0 (금일 즉시 구현·실기기 배포)**:
  - `C-01~C-06`, `RT-01`, `RT-04` 전수 해결, 독립 볼륨 DSP + UI, 비AI Obsidian/Amber 디자인 시스템, Android 7.0~13(`minSdk 21 ~ targetSdk 33`) 통합 호환.
- **v3.0.0 (Google Play 정식 스토어 출시용 차기 마일스톤)**:
  - Jetpack Compose + Material 3 `DynamicTheme` 옵션, Room DB 즐겨찾기/최근 사용, 자동 수면 타이머(15/30/60분 페이드아웃), 실시간 FFT/오실로스코프 파형 캔버스, 스토어 심사 대응 비의료 웰니스 문구 정제 및 첫 실행 면책 온보딩.

---

## PART 3. 최종 마감 `/CRITIC · /SELFREFINE · /REDTEAM` 검증 결과 (v2.1.0 → v2.2.0)

| ID | 분류 | 실측 근거 및 문제 | v2.2.0 해결책 |
|---|---|---|---|
| **U-01** | 삼성 뮤직 동시재생 (U324) | v2.0.0 `requestAudioFocus(AUDIOFOCUS_GAIN)` 호출로 삼성 뮤직이 `AUDIOFOCUS_LOSS`를 받고 정지됨 | `requestAudioFocus`/`AudioFocusRequest` 전면 제거 → `AudioTrack piid` 무포커스 동시 믹싱 실측 검증(`requestAudioFocus` `0`건) |
| **U-02** | 음악 병행 볼륨 & UX 라이팅 (U324) | `100% · -0.0 dB` 부호 버그 및 추상 타이틀("활력") | `-48 dB` 곡선·`0.0 dB` 교정, `20% 배경`/`45% 병행` 디텐트, 상황 기반 48종(`웨이트 트레이닝 · 고중량 몰입` 등) |
| **U-03** | 용어 통일 (U325) | UI/알림 9곳에 남아 있던 전문 용어 `"발진 중"` | UI·알림 전수 `"재생 중"` / `"재생 중지"` / `"프리셋을 선택하면 즉시 재생됩니다"`로 통일 |
| **F-01** | `0%` 음소거 해제 무음 (U325) | `0%` 상태에서 뮤트 버튼 재탭 시 `0%`로 해제되어 소리 안 남 | `0%`에서 음소거 해제 시 기본값 `45% (-26.4 dB)` 자동 복귀 |
| **F-02** | 프리셋 즉시 전환 미세 클릭 (U325) | `p.isAntiPhase` 반전이나 `freq1` 변경 첫 샘플에 미세 진폭 단차 가능 | 1샘플 48kHz 지수 감쇠 디클릭커(`declickL/R *= 0.993`) 탑재 |
| **F-03** | FM 변조 시 Hz 숫자 떨림 (U325) | v2.1.0 실화면(`19.72 Hz`)처럼 FM 변조 순시값이 기본 캐리어(`18.98 Hz`)를 가림 | `mCurrentLiveFreqHz`를 FM 오프셋 이전 캐리어 기준으로 추출하고 우측 배지에 `+ 10.0 Hz`/`FM`/`AM` 분리 표기 |
| **F-04** | 이어폰 초음파 청력 보호 (U325) | `16~21.5 kHz` 초음파 프리셋을 이어폰 착용 상태에서 `100%` 실행 시 청력 부담 | 출력 장치 감지(`isPersonalAudioConnected()`) 시 초음파(`noiseType==2`) 출력 `-12 dB (0.25x)` 자동 감쇄 + 콘솔 안내 |
| **F-05** | WakeLock · AudioTrack 복구 (U325) | 무기한 `WakeLock` 및 `AudioTrack.write` 음수 에러 미처리 | 8시간 상한 `acquire(8h)` + `writeResult < 0` 안전 종료 가드 |

---

## PART 4. 실사용 평가 기반 재생-탭 동기화 UX 비판 분석 및 v2.3.0 개선 (U326)

### 4-1. Stage 0 레퍼런스 및 결함 진단 (`D-01`~`D-03`)
- **레퍼런스**: Android Bound Service(`developer.android.com` `bindService`/`onServiceConnected` + `HorizontalScrollView` 레이아웃 완료 후 `View.post`/`OnGlobalLayoutListener` 중앙 정렬), Spotify / Apple Music / Material 3 오디오 UX(재진입 시 활성 컨텍스트·카테고리 즉시 복원, 콜드 스타트 무음 대기 상태 유지, 포그라운드 수동 탐색 무간섭, 원탭 재생 위치 이동).

| ID | 결함 원인(실사용 괴리) | v2.3.0 개선 설계 |
|---|---|---|
| **D-01** | `MainActivity.onCreate` L156의 무조건 `selectTab(0)` 고정 호출과 `onServiceConnected`/`onResume`/`onNewIntent`의 탭 동기화 부재로, 어떤 탭 주파수를 재생해도 앱 재오픈 시 항상 `안티방어 12`가 선택됨 | `AegisAudioService.loadAndPlayPreset`에서 `PREF_LAST_TAB` 즉시 영속화 + `TabSyncPolicy.resolveTabOnEntry`로 재오픈·백그라운드 복귀·알림 탭 시 재생 탭 우선 자동 동기화(유휴 시 마지막 사용 탭 복원) |
| **D-02** | `HorizontalScrollView tabScrollView`와 `ScrollView`가 로컬 변수로만 생성되어 `4~7`번 탭(`두통·통증`, `운동·피로`, `기분·힐링`, `모기·벌레`)이 선택되어도 화면 오른쪽 바깥에 가려짐 | `mTabScrollView`/`mMainScrollView` 필드 보관 + `OnGlobalLayoutListener`/`View.post` + `TabSyncPolicy.computeCenteredScrollX`로 선택 탭 pill 뷰포트 중앙 자동 정렬 |
| **D-03** | 다른 탭 탐색 중 어느 탭에서 소리가 나고 있는지 탭 바에서 구분 불가하며, 콘솔에서 해당 프리셋 카드로 바로 돌아가는 동선 부재 | 재생 소유 탭 pill에 에메랄드 LED 점(`●`)·테두리 부여(`refreshTabStyles`), 콘솔 우측에 `재생 탭 ›`/`위치 보기 ›` 점프 배지(`jumpToPlayingPreset`) + 해당 프리셋 카드 자동 스크롤 |

### 4-2. 레드팀 가드 매트릭스 (`RT-TAB-01`~`RT-TAB-07`)
- **RT-TAB-01 (포그라운드 탐색 납치 금지)**: `mStateReceiver` 및 250ms `mTelemetryTick`은 `allowTabAutoSync=false`로 동작해 사용자가 다른 탭을 탐색 중일 때 탭을 강제로 되돌리지 않음.
- **RT-TAB-02 (백그라운드 복귀 재정렬)**: `onStop()`에서 `mPendingEntryTabSync = true`, `onNewIntent()`에서도 재동기화 플래그를 세워 다른 탭을 보다가 홈/타 앱에 다녀와도 현재 재생 중인 탭으로 재정렬.
- **RT-TAB-03 (0프레임 Tab-0 깜빡임 제거)**: `onCreate()`에서 `PREF_LAST_TAB`을 선로드하여 바인딩 완료 전 첫 프레임부터 올바른 탭 렌더링.
- **RT-TAB-04 (미계측 스크롤 실패 방지)**: `mTabScrollView.getWidth() == 0` 또는 신규 카드 `getHeight() == 0` 상태에서는 `OnGlobalLayoutListener`가 1회 측정 완료 직후 스크롤 실행.
- **RT-TAB-05 (콜드 스타트 무음 원칙)**: 유휴 상태로 재실행 시 마지막 사용 탭 UI만 복원하고 `AudioTrack` 자동 재생은 수행하지 않음.
- **RT-TAB-06 (대비 유지)**: 선택된 밝은 `#F2F3F5` 탭 pill 위 재생 점은 진한 에메랄드(`#00A854`), 비선택 어두운 탭 pill 위에서는 `#00E676` 사용.
- **RT-TAB-07 (360dp 타이틀 겹침 방지)**: `mTvScenarioTitle`에 `weight=1`을 부여하고 우측 `mTvJumpCue` 배지와 수평 분리하여 좁은 화면에서도 잘림 방지.

---

## PART 5. Windows PC 네이티브 백그라운드 컴패니언 (`v3.0 PC`, Card U333) 설계 및 실증 결과

> 기존 `v2.3 PC` Edge `--app` + `pythonw` 래퍼(9개 프로세스, `~525.9 MB`)를 전면 대체하는 네이티브 Windows x64 컴패니언(`VortaAegisPC.exe`, `100,864 B`) 전환 설계서.

### 1) 비간섭·백그라운드 상주 아키텍처
- **컴패니언 보조 앱 원칙**: 메인 작업(영상, 브라우저, IDE, AI 도구)을 방해하지 않도록 토스트/풍선 알림(`ShowBalloonTip`) 0건, SMTC/미디어 키/전역 단축키 가로채기 0건, 닫기(`×`) 시 완전 종료 대신 트레이 백그라운드 유지(`HideToTray`, `TrimWorkingSet()`).
- **단일 인스턴스 & 트레이 제어**: Win32 `Mutex` + `NamedPipe`로 중복 실행 시 기존 창 복원; 트레이 좌클릭 창 토글, 우클릭 다크 컨텍스트 메뉴(재생/정지, 프리셋 재개, 미니 바 전환, 볼륨 프리셋, 완전 종료).
- **미니 바(`372×62`)**: 작업창 모서리에 겹치지 않고 즉시 음소거·정지·복귀만 제공하는 컴팩트 플로팅 바 제공.

### 2) Windows WASAPI 네이티브 출력 & PC 음향 보정 (3-프로파일)
- **네이티브 WASAPI 공유 모드**: `WasapiAudioEngine.cs`가 `IMMDeviceEnumerator` / `IAudioClient` / `IAudioRenderClient` COM vtable을 직접 호출해 이벤트 기반 32-bit IEEE Float 48kHz 스테레오로 믹싱, Windows 볼륨 믹서에 독립 `"보르타 이지스"` 세션으로 표시되며 출력 장치 선택·핫플러그 자동 추종 및 유휴 시 스레드 자동 해제.
- **3-모드 PC 음향 프로파일 (`AegisDspEngine.cs`)**:
  - `PC 스피커 보정`: 노트북·데스크톱 소형 스피커의 저역 한계를 보완하는 배음(Missing-Fundamental Harmonics) 합성 + `144 Hz` 초저주파 반송.
  - `이어폰·헤드폰`: 고주파 피로 방지 강제 `-12 dB` 초음파 가드 및 바이노럴 위상 정합.
  - `순수 원음·DAC`: 추가 하모닉스·가공 없이 `-1.0 dBTP` 기준 원음 정현파 직결 출력.

### 3) 실측 검증 수치 (Windows 11)
- 네이티브 단위·통합 검증(`test_u333_pc_native.py` / `--self-test`): `768194/0 PASS` (`devices=3`, 48kHz 32-bit IEEE Float, 48종 프리셋 100% 패리티, 3-프로파일·초음파 가드·디클리커·페이드 검증).
- 실창·트레이 메모리 실측(`verify_u333_live.py`, PID `23772`): 실창 표시 `103.1 MB` → 트레이 상주 전환 `8.5 MB`(`visible=False`, CPU 누적 `3.469s`) → 트레이 복귀 `19.3 MB`(`visible=True`, 캡처 `pc_aegis_v30_panel.png` `53,969 B` 시각 결함 0건).

### 4) `v3.1 PC` 자유 리사이즈·실시간 파동 스코프·`120%` 클린 증폭 고도화 (Card U334)
- **시장 조사 및 아키텍처 근거**: 데스크톱 오디오 플러그인(FabFilter·Vital·FxSound) 및 `vorta_unified_app` `LiveWaveformView`의 록-프리 링버퍼 + WPF `StreamGeometry` 30fps(재생+가시 시에만 동작, 비활성 시 0% CPU) + `WindowChrome` 반응형 2단/1단 레이아웃 패턴 반영.
- **자유 리사이즈 & 반응형 2단/1단 UI**: `WindowStyle=None` + `WindowChrome`(`ResizeBorderThickness=7`, `MinWidth=400`, `MinHeight=540`, `760px` 이상 2단 스튜디오 `920×680`, `1080px` 이상 카드 2열, `760px` 미만 1단 슬림 자동 전환, `MiniBarWindow` 가로 리사이즈 `340..720px` + 위치·크기 원자적 저장). 전체 8개 카테고리 탭 `WrapPanel` 줄바꿈으로 어느 창 크기에서도 잘림 0건.
- **실시간 스테레오 파동 스코프 (`LiveWaveformScope`)**: `AegisDspEngine.SCOPE_RING_SIZE = 4096` L/R 링버퍼, 자동 타임베이스(`~3.5` 주기) 및 상승 영점 교차 트리거, 바이노럴 L(금색)+R(시안) 이중 파형·`180°` 역위상·`144Hz` 반송 상태 및 `-0.5 dBTP` 천장 직관 표기.
- **정보 전달 구조화 & 편의 기능**: 12세그먼트 LED VU 미터, 4칩 실시간 텔레메트리(`위상·채널`, `변조·보호`, `PC 음향`, `실출력 dBFS`), `[작용]`/`[상황]`/`[보정]` 구조화 설명 카드, 즐겨찾기(별표 필터·트레이 메뉴), 자동 정지 타이머(`15/30/60/90분`), 이전/다음 트랙 이동, 마우스 휠 볼륨(`±2%`), 창 내부 단축키(`Space`, `↑↓`, `M`, `Ctrl+F`, `Ctrl+1..8/0`, `Alt+←/→`).
- **`100%` ~ `120%` 무왜곡 클린 증폭 (`VolumeCurve`, `AegisDspEngine`)**: 비선형 `tanh` 클리핑 배제, `CLEAN_BOOST_CEILING = -0.50 dBTP`(`0.94406`, Windows WASAPI `CAudioLimiter` ~`0.984` 하단) 선형 헤드룸 + `180ms` 선형 피크 추종 리미터로 `120% (+1.6 dB)` 출력에서도 코히어런트 DFT `THD+N < 0.1%` 검증 통과. 6종 볼륨 디텐트(`15% 영상`/`30% 작업`/`50% 병행`/`80% 단독`/`100% 최대`/`120% 증폭`).
- **`v3.1 PC` 실측 결과**: `VortaAegisPC.exe`(`146,944 B`), `test_u333_pc_native.py` `1,352,423/0 PASS`(`--self-test` `1,352,351/0 PASS`), `verify_u333_live.py`(와이드 재생 `pc_aegis_v31_wide_playing.png` `94,511 B` · 슬림 바이노럴 재생 `pc_aegis_v31_compact_playing.png` `62,265 B` · 유휴 `pc_aegis_v30_panel.png` `89,275 B`, 트레이 백그라운드 `5.3 MB` / 복귀 `31.5 MB`, PID `28356`).

---

## PART 6. `v3.2 PC` 상용 스튜디오 고도화 · 7 Hz 스코프 물리 교정 · 모듈형 팩 & 라이선스 (Card U335)

### 1) "7 Hz 파동이 왜 거칠고 흔들렸는가" 물리 규명 및 교정
- **원인 3요소**:
  1. `PC 스피커 보정` 모드(`AegisDspEngine.cs`)는 소형 스피커의 `32 Hz` 미만 한계를 보완하기 위해 `7.00 Hz` 진폭 포락선에 **`144 Hz`(및 2배음) 가청 반송파**를 합성하므로, 실출력 PCM에 초당 144회의 잔물결(74.7 ms 창 내 약 10.8개)이 실제로 실려 있었음.
  2. `4096`샘플(@48 kHz = `85.3 ms`) 단일 링버퍼는 `7.00 Hz`의 1주기(`142.86 ms`)보다 짧아 반 주기만 표시되었음.
  3. 단일 샘플 점프 트리거(`prev <= 0 && cur > 0.002f`)는 완만한 `7 Hz` 영점 교차(샘플당 기울기 `~0.0009`)를 놓쳐 화면이 수평 표류함.
- **교정 아키텍처**:
  - **이중 샘플링 스코프 링(`AegisDspEngine`)**: 48 kHz 링(`4096`프레임) 외에 32× 박스카 데시메이션 `1500 Hz` 저역 전용 링(`4096`프레임 = `2.73초` 창)과 PC 보정 전 순수 기본파 탭(`fundL/R`)을 동시 기록.
  - **동적 히스테리시스 상승 영점 트리거(`LiveWaveformScope`)**: 스냅샷 피크의 `-15%` 임계치 이하 무장(`arm`) 후 첫 `0` 교차에서 고정 트리거 (`0.5 Hz`~`21.5 kHz` 전 대역 록).
  - **스코프 뷰 토글**: `< 55 Hz` 재생 시 자동 `1500 Hz` 창(`~3.0주기`, `7.00 Hz` = `428.7 ms` 3주기 매끄러운 정현파) 표시 + 우측 상단 **`기본 파동 | 실출력 보기`** 알약 버튼으로 `144 Hz` 반송 합성파와 즉시 비교 가능.

### 2) 상용 레퍼토리 가치 판단 및 모듈형 확장 팩 설계 (Go — Pivot to Curated Modular Packs)
- **시장 조사 및 가치 판단 (Brain.fm · Endel · myNoise · Noisli 기준)**:
  - 무분별한 트랙 난립(100+ 평면 목록)은 선택 피로(Choice Overload)와 이탈을 유발하므로 **No-Go**.
  - 반대로, 모바일 중심 48종 코어에 비어 있던 **PC 데스크톱/상용 핵심 수요 16종**(브라운 노이즈 `1/f²` `C01`, 40 Hz 감마+브라운 `C02`, 말소리 차폐 `C03`, 포모도로 25분 `C04`, 파워 낮잠 `C06`, 호흡 페이서 `C09`, 이명 대역 마스킹 `C12`, VDT 눈 휴식 `C13`, 60 Hz 험/L·R 위상 점검 `C15~C16`)을 **모듈형 팩 아키텍처**로 분리 탑재하는 전략은 상품성·업데이트 확장성 측면에서 **Go**.
- **업데이트 용이 구조 (`PresetCatalog.cs`)**:
  - `web/presets.json`(코어 48종 Android 패리티 100% 보존) + `packs/*.json`(번들 `vorta_pc_pro_pack_v1.json` 16종, 총 64종) + `%LOCALAPPDATA%\VortaAegis\packs\*.json`(외부 DLC 핫로드).
  - `schemaVersion == 1`, 주파수·ID 범위·중복 검증 및 손상 팩 안전 스킵(앱 무크래시). UI 상단 **팩 필터 칩(`전체 64` / `코어 48` / `PC Pro 16`)** 및 **`팩 폴더 열기 / 새로고침`** 내장.

### 3) 레이아웃·정보 위계·무클리핑 리미터 & 상용 브랜딩
- **좌우 자동·수동 분할(`GridSplitter`) & 하단 채움**: `760px` 이상 2단에서 중앙선 드래그 조절 + 더블클릭 자동 복원(`ConsolePaneWidth` 영구 저장), 우측 패널 자체 너비(`>= 620px`) 기준 카드 1열/2열 동적 전환. 좌측 하단 `세션 모니터 · 빠른 전환`(재생 경과, 활성 장치, `클리핑 0`, 즐겨찾기/최근 6종 원클릭 독)으로 세로 대형 창 공백 해소.
- **무클리핑(하드 클램프 0건) `120%` 증폭 교정**: `AegisDspEngine.cs` 하드 클램프 제거, PC 보정 정규화 및 `LIM_CEIL = -0.50 dBTP` 선형 리미터 추종으로 전 64프리셋 × 3프로파일(`192`조합) `OvershootCount == 0`, 4× 큐빅 트루피크 `<= -0.50 dBTP`, 다중 주파수(`40/200/528/1000 Hz`) 코히어런트 DFT `THD+N < 0.1%` 검증 완료.
- **상용 브랜딩·업데이트·라이선스 허브**: `AssemblyInfo.cs`(Win32 PE `VersionInfo` `3.2.0.0`), `UpdateChecker.cs`(GitHub `version.json` 비동기 HTTPS 확인), `AboutLicenseWindow`(Vorta/Aegis 브랜드 뜻, 버전/팩 관리, `LICENSE`/`NOTICE`/`THIRD_PARTY_NOTICES.md`/`SECURITY.md`/정식 인증 가이드 바로 연결).

---

## PART 7. 모바일 `v3.3.0` 전수점검 · 웹 딥리서치 기반 음향심리·일상 UX 고도화 (Card U337)

> 상세 분석 및 실측 근거는 [보르타 이지스 모바일 전수점검·시장조사·v3.3 고도화 보고서](../03-device-engineering-and-reports/보르타_이지스_모바일_전수점검_시장조사_및_v33_고도화_보고서.md) 참조.

1. **안정성·동시성 하드닝 (`AegisAudioService.java`, `AegisQuickTileService.java`)**:
   - `mSessionGen` 세대 토큰으로 중지/재시작 경합 차단, `AudioTrack.ERROR_DEAD_OBJECT` 최대 3회 자동 복구, API 24+ **빠른 설정 타일(`AegisQuickTileService`)** 및 `20초` 제곱 포락선 수면 타이머 페이드아웃 탑재.
2. **음향심리 DSP 고도화 (`AegisDspCore.java`)**:
   - **`출력 음향 보정` 4모드(`자동`/`스피커`/`이어폰`/`순수원음`)**: 폰 스피커 시 Schouten Missing Fundamental(32~85Hz 2·3배음), `<32Hz` 144Hz 반송파, 바이노럴 이소크로닉 펄스 보조; `noiseType=1` 스테레오 비상관 핑크 노이즈; **배경 사운드 블렌드(`꺼짐`/`브라운`/`핑크`/`빗소리` 0~60%)**; Post-AM/프로파일 전환 디클리커 및 `2π` 위상 래핑.
3. **비전문가 일상 UX & `360dp / 1.3x` 제로 클리핑 (`PresetAdvisor.java`, `MainActivity.java`)**:
   - **상시 노출 슬림 퀵 볼륨 바** + **`▶ 이어듣기`**, **시간대별 `지금 추천` 3종**, **동의어 지원 한국어 검색창**, **`최근` 필터**, **카드별 청취 가이드 배지**, **프리셋별 볼륨 기억 & 안전 전환**, **2줄 적층 디텐트·가로 스크롤 필터·무변경 무갱신 텔레메트리**, `설정·정보` 실시간 오디오·배터리 진단 시트 탑재.
4. **배포 위생 & 실측 검증**:
   - `build_aegis.py` 저장소 내 `smato_a30_app/debug.keystore` 우선 해석(`SHA-256: 7522fef8...`), `TestU337Aegis` `23,036,785/0 PASS`, `sync_presets.py` `48/48 PASS`, APK `173,553 B` 인플레이스 업그레이드(`Success`, `versionCode=7`, `3.3.0`), Note 20 Ultra 실기 `AudioOut_D 48kHz` 가동·크래시 0건 검증 완료.

---

## PART 8. 모바일 `v3.4.0` 전 8탭 단계별 여정 배열 · 24종 확장 & 원탭 자동 코스 (Card U338)

> 상세 분석 및 전 8탭 단계표는 [보르타 이지스 v3.4 여정 시나리오·자동 코스 고도화 보고서](../03-device-engineering-and-reports/보르타_이지스_v34_여정시나리오_및_자동코스_고도화_보고서.md) 참조.

1. **여정 확장 24종 (`JourneyPackCatalog.java`, `J01..J24`, 총 88종)** & **8탭 단계별 배열 (`JourneyOrderPolicy.java`)**:
   - `PresetModel`(48)·`ProPackCatalog`(16) ID 무변경 보존 상태에서 운동·수면·집중·명상·두통·기분·안티방어·모기 탭의 빈 단계(준비·상황별 본활동·쿨다운·회복)를 24종으로 채우고, 모든 탭을 **1단계 → 2단계 → 3단계 → 4단계** 흐름으로 정렬(`이전`/`다음` 트랙 이동도 단계 순서 준수).
2. **바이노럴 진행 램프 엔진 (`AegisDspCore.java`)**:
   - `isSweep && freq1 > 0f` 시 `sweepStart → sweepEnd`를 `sweepDurationSec` 동안 단조 진행 후 목표 차주파수에 안정적으로 고정 유지(`J01 10→18Hz`, `J05 12→6Hz`, `J08 10→2Hz` 등), 폰 스피커 모드에서는 이소크로닉 펄스 보정 자동 연동.
3. **Doze 면역 자동 연계 코스 시퀀서 (`AegisAudioService.java`, `MainActivity.java`)**:
   - 상단 `원탭 자동 코스` 4종(`웨이트 풀코스 65분`, `근비대·유산소 55분`, `딥워크 60분`, `꿀잠 숙면 60분`), 콘솔 내 실시간 `코스 N/M · 단계명 (MM:SS)` + `다음 단계 ▶` 배너, 마지막 단계 20초 부드러운 페이드아웃 자동 종료.
4. **검증 영수증**: `TestU338Aegis` `28,735,931/0 PASS`, `sync_presets.py` `48/48 PASS`, `build_aegis.py` `202,225 B`(`SHA-256: 7522fef8...`, `apksigner verify` PASS), Note 20 Ultra 인플레이스 업그레이드(`Success`, `versionCode=8`, `3.4.0`, `PID 8870`, `48000 Hz` Float 실구동, 크래시/포커스 침해 `0`건).

---

## PART 9. PC `v3.4.0` 완전 패리티 · 쉬운 가이드 · 미니/스튜디오(`정보→파동→볼륨`) 통일 (Card U339)

> 상세 보고서는 [보르타 이지스 PC v3.4.0 & 모바일 v3.4.1~v3.4.4 고도화 보고서](../03-device-engineering-and-reports/보르타_이지스_PC_v34_고도화_보고서.md) 참조.

1. **PC `v3.4.0` 네이티브 88종 패리티 & DSP 확장 (`vorta_aegis_pc/native/*.cs`, `packs/vorta_journey_pack_v1.json`)**:
   - `JourneyAndAdvisor.cs` 및 `vorta_journey_pack_v1.json`으로 여정 24종(총 88종), 8탭 `1단계→4단계` 배열, 4대 원탭 자동 코스, 스테레오 앰비언트 블렌더(`꺼짐`/`브라운`/`핑크`/`빗소리` 0~60%), 바이노럴 진행 램프, Post-AM·프로파일 디클리커, 20초 제곱 포락선 종료, 프리셋별 볼륨 기억·고볼륨 안전 전환을 완전 이식했다.
2. **전문용어 제로화 & 1탭 쉬운 설명 (`v3.4.1` 모바일 + `v3.4.0` PC)**:
   - `포모도로(25분 집중·5분 휴식)` 등 약어·전문용어를 전 88종에서 일상 한국어로 정화하고, 모든 프리셋 카드의 `[설명]` 버튼과 상단 `[가이드]`/`[쉬운 가이드]` 팝업을 양 플랫폼에 동시 탑재했다.
3. **단일 통합 볼륨 & 미니/스튜디오 차별화 (`v3.4.2~v3.4.4` 모바일 + `v3.4.0` PC)**:
   - 모바일의 중복 볼륨 컨트롤(슬림 퀵바 + 스튜디오 내 볼륨)을 단일 완전 볼륨 블록으로 통일하고, **미니 모드에서는 파동 그래프를 숨겨(`View.GONE` / `MiniBarWindow`) 간결성을 극대화**, **스튜디오 모드에서는 양 플랫폼 모두 `정보표시 창 → 자동 파동 애니메이션(LiveWaveform) → 단일 통합 볼륨`** 순서로 배치해 차별성과 일관성을 동시에 확보했다. `440px` 슬림 PC 타이틀바 클리핑도 `mAboutBtn` 반응형 숨김 및 축약 라벨로 해소했다.
4. **검증 영수증**: PC `--self-test` `28,542,541/0 PASS`, `test_u339_pc_native.py` `764/0 PASS`, `verify_u339_live.py` 4종 PNG·트레이 복귀 PASS; Android `TestU338Aegis` `28,747,651/0 PASS`, Note 20 Ultra `v3.4.4`(`Build 12`, `PID 24591`) 미니·스튜디오 실기 캡처·크래시 `0`건.




