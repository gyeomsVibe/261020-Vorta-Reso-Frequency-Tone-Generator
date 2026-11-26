# 보르타 이지스(Vorta Aegis Pro) PC v3.4.0 & 모바일 v3.4.1~v3.4.6 공통 스튜디오 · 여정 고도화 보고서

> **대상 소프트웨어**:
> - Windows PC 네이티브: `VortaAegisPC.exe` (`v3.4.0`, `.NET Framework 4.8` C# 5.0 x64, WASAPI IEEE Float 48 kHz)
> - Android 모바일: `com.vorta.aegis` (`v3.4.6` `Build 14`, API 21~33, Galaxy Note 20 Ultra `SM-N986N` 배포)
> **작업 카드**: `U339` (PC v3.4 고도화 + 모바일 v3.4.1~v3.4.6 쉬운 가이드·단일 볼륨·12단 실시간 VU 파동·기본 간결 모드 vs 스튜디오 모드 차별화)

---

## 1. 핵심 고도화 내용 요약

### A. PC 앱 `v3.4.0` 모바일 1:1 완전 패리티 & 음향 고도화
1. **88종 전 프리셋 1:1 패리티**: 기존 `코어 48종` + `PC Pro 16종(v1.1.0)`에 **`여정 확장 24종(J01~J24, vorta_journey_pack_v1.json)`**을 결합해 PC·모바일 모두 정확히 **88종**(`ID`, `tabIndex`, `title`, `badge` 100% 일치)을 제공한다.
2. **8탭 단계별 배열 & 4대 원탭 자동 코스 (`JourneyAndAdvisor.cs`, `Program.cs`)**:
   - 전 8탭을 **`1단계(준비) → 2단계(본활동) → 3단계(쿨다운) → 4단계(회복)`** 섹션 헤더로 구조화하고 `이전`/`다음` 트랙 이동도 단계 순서를 따른다.
   - 상단 `원탭 코스` 4종(`웨이트 풀코스 65분`, `근비대·유산소 코스 55분`, `깊은 집중 풀코스 60분`, `꿀잠 숙면 코스 60분`) + 트레이 메뉴 서브항목 + 1초 정밀 자동 코스 시퀀서 + `다음 단계`/`해제` 배너를 제공한다.
3. **DSP 오디오 엔진 업그레이드 (`AegisDspEngine.cs`, `WasapiAudioEngine.cs`)**:
   - **바이노럴 진행 램프**: `IsBinauralRamp` 활성화 시 `SweepStart → SweepEnd`를 지정 시간 동안 선형 진행 후 목표 주파수를 안정적으로 고정 유지(왕복 없음), `PcSpeaker`에서는 이소크로닉 보조 펄스를 자동 적용한다.
   - **진정한 스테레오 핑크 노이즈 & 앰비언트 블렌더**: `noiseType == 1` L/R 독립 화이트 소스 디코릴레이션 + **배경음 믹서(`꺼짐`/`브라운`/`핑크`/`빗소리` 0~60%, 초음파 프리셋 자동 우회)**.
   - **디클리커 & 20초 제곱 포락선 부드러운 종료**: Post-AM 및 프로파일 전환 디클리커, 타이머/코스 완료 시 `20초` 제곱 포락선 페이드아웃(`RequestGentleStop(20.0)`), `-0.50 dBTP` 천장 리미터(`OvershootCount == 0`).
   - **프리셋별 볼륨 기억 & 고볼륨 보호 (`AppStateStore.cs`, `Program.cs`)**: 수면·두통 계열 전환 시 `75%` 초과 고볼륨을 추천 안전 수준으로 자동 완화한다.

### B. 비전문가 일상 UX 공통 개선 (Android v3.4.1~v3.4.6 & PC v3.4.0)
1. **쉬운 한국어 정화 & 1탭 `[설명]` / `[가이드]` (`PresetAdvisor`, `CompanionUi`)**:
   - `포모도로`, `CNS`, `HRV`, `DOMS`, `VDT`, `HIIT`, `SMR`, `딥워크`, `1/f²`, `롤오프`, `캘리브레이션` 등 전문 약어를 일상 한국어로 풀어쓰고, 모든 카드의 **`[설명]`** 버튼과 상단 **`[가이드]`/`[쉬운 가이드]`** 팝업으로 원리·활용법을 즉시 확인할 수 있다.
2. **중복 볼륨 UI 제거 및 단일 통합 볼륨 구현 (`MainActivity.java`, `CompanionUi.cs`)**:
   - 정보창 아래 단일 완전 볼륨 컨트롤을 두고, 중복 볼륨 영역을 제거했다.
3. **기본 간결 모드 vs 스튜디오 모드 명확한 차별화 (`MainActivity.java` v3.4.6, `CompanionUi.cs` v3.4.0)**:
   - **기본 간결 모드 (모바일 접힘 기본 / PC 슬림 `< 760px`)**: **① 정보표시 + 12단 실시간 VU 파동 → ② 간결한 버튼(`음소거`/`−`/`+`) + 슬라이더 볼륨 → ③ 원탭 자동 코스 → ④ 8대 카테고리 탭 & 시나리오 카드**만 첫 화면에 한눈에 노출한다. 파동 그래프·볼륨 5~6단 프리셋/추천 볼륨 칩·시간대 추천(`아침 추천 · 원탭 시작`)·검색/필터·세션 퀵독은 모두 숨긴다.
   - **스튜디오 모드 (모바일 `스튜디오 ▼` 펼침 / PC 와이드 `>= 760px`)**: **`① 정보표시 창 → ② 자동 파동 애니메이션(LiveWaveform) → ③ 전체 볼륨 + 디텐트 5~6단·추천 볼륨 → ④ 음향 보정·배경음·시간대 추천·검색·필터`**가 모두 활성화된다.
4. **상단 12단 VU 파동 막대 실시간 애니메이션 (`MainActivity.java` v3.4.5+, `CompanionUi.cs` v3.4.0)**:
   - 정적 피크 비율/동결 캐시로 멈춰 있던 상단 12단 VU 막대를 **지각 음량(dBFS) 정규화 + 진행파 고조파(`mVuWavePhase`) 실시간 높이 가변(`setScaleY` 20fps / WPF `Height` 30fps)**으로 개선했다.

---

## 2. 실측 검증 영수증

| 검증 항목 | 결과 | 세부 근거 |
|---|---|---|
| **PC 네이티브 `--self-test`** | **PASS** | `PASS: 28542541/0 native checks verified` (`WASAPI 48000 Hz 2ch 32-bit float bufFrames=1056`, exit 0) |
| **PC·모바일 88종 패리티·백신 스캔** | **PASS** | `test_u339_pc_native.py`: `767/0 checks passed` (`88/88` title/badge/tab 일치, `Info->Waveform->Volume` 순서, 슬림/와이드 차별화, dBFS VU 파동, C# 5.0·`version.json 3.4.0`·PE `3.4.0.0`) |
| **PC 라이브 UI·VU 모션·트레이 복귀** | **PASS** | `verify_u339_live.py`: `v3.4` 바로가기, 5종 PNG 캡처(`PC_VU_PIXEL_DIFF=36`), `WM_CLOSE` 트레이 상주(`visible=False`) 및 재실행 복귀(`PID=13888 visible=True`) |
| **모바일 Host JVM + 실기 배포·VU 모션** | **PASS** | `TestU338Aegis`: `28751541/0 checks verified`, `build_aegis.py` `210,417 B` APK, Note 20 Ultra (`R3CN800MV8R`) `versionCode=14` (`3.4.6`), `PID=28721`, `ANDROID_VU_PIXEL_DIFF=236`, 크래시 `0`건 |
