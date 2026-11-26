# Card U339 Result: PC v3.4.0 고도화 · Android v3.4.1~v3.4.6 기본 간결/스튜디오 차별화·실시간 VU 파동

- **Card ID**: `U339-AEGIS-PC-V34`
- **Status**: `DONE`
- **Targets**:
  - Windows PC Native: `vorta_aegis_pc/VortaAegisPC.exe` (`v3.4.0`, `3.4.0.0`, `260,608 bytes`)
  - Android Mobile: `com.vorta.aegis` (`v3.4.6` `versionCode=14`, `210,417 bytes`, Galaxy Note 20 Ultra `R3CN800MV8R`)
- **Backups**: `.work/backup_20261127_aegis_v341/vorta_aegis_app/`, `.work/backup_20261127_aegis_pc_v34/vorta_aegis_pc/`

## Key Changes
1. **PC `v3.4.0` 완전 패리티 & DSP/팩 고도화**:
   - `vorta_journey_pack_v1.json` (24종 `J01..J24`) + `vorta_pc_pro_pack_v1.json` (`v1.1.0`) + `JourneyAndAdvisor.cs` 추가 → 모바일과 동일한 **88종** 카탈로그, 8탭 `1단계→4단계` 순서, 4대 원탭 자동 코스(`웨이트 풀코스`, `근비대·유산소 코스`, `깊은 집중 풀코스`, `꿀잠 숙면 코스`), 트레이 코스 서브메뉴 구현.
   - `AegisDspEngine.cs`, `WasapiAudioEngine.cs`, `AppStateStore.cs`, `Program.cs`: 바이노럴 진행 램프(`LiveBeatHz`), 스테레오 비상관 핑크 노이즈, 스테레오 앰비언트 블렌더(`꺼짐`/`브라운`/`핑크`/`빗소리` 0~60%), Post-AM·프로파일 전환 디클리커, 20초 제곱 포락선 부드러운 종료, 프리셋별 볼륨 기억·고볼륨 안전 전환.
2. **비전문가 일상 UX 정화 (`v3.4.1` 모바일 + `v3.4.0` PC)**:
   - `포모도로`, `CNS`, `HRV`, `DOMS`, `VDT`, `HIIT` 등 설명 없는 전문 약어를 전 88종 카드에서 일상 한국어로 풀고, 카드 `[설명]` 팝업 + 상단 `[가이드]`/`[쉬운 가이드]` 용어집 대화상자 추가.
3. **기본 간결 모드 vs 스튜디오 모드 명확한 차별화 (`v3.4.2~v3.4.6` 모바일 + `v3.4.0` PC)**:
   - Android `MainActivity.java`: **기본 간결 모드**는 `miniHeader(정보+VU) → 간결한 버튼 볼륨(음소거/∓/슬라이더) → 원탭 자동 코스 → 8탭·시나리오 카드`만 노출(파동 그래프 `mScopeSection`, 5단 프리셋 `mDetentRow`, 추천 볼륨 배너, 시간대 추천 `mRecSectionView`, 검색창 `mSearchBarView`, 필터 `mFilterChipSectionView` 모두 `View.GONE`), **`스튜디오 ▼` 펼침 시에만** `정보 → 자동 파동 → 전체 볼륨 → 추천·검색·스튜디오`로 전환.
   - PC `CompanionUi.cs`: **슬림(`< 760px`) 모드**에서 `mScope`, `mSweepPanel`, `mDetentGrid`, `mQuickDockCard`, `mBrowserTopBar`(검색/필터)를 숨겨 기본 간결 루트로 바로 조작·선택 가능하며, **와이드(`>= 760px`) 스튜디오**는 `infoBox → mScope → volSection(6단 디텐트) → telGrid` + 퀵독 + 검색을 모두 표시. `< 520px` 타이틀바 클리핑 해소.
4. **상단 12단 VU 파동 막대 실시간 애니메이션 (`v3.4.5+` 모바일 + `v3.4.0` PC)**:
   - 지각 음량(`dBFS + 50.0 / 49.5`) 정규화 + 진행파 고조파(`mVuWavePhase`) 실시간 높이 애니메이션(Android `20fps setScaleY` / PC `30fps Height 4..14px`).

## Verification Receipts
- `VortaAegisPC.exe --self-test` (via `test_u339_pc_native.py`): **`PASS: 28542541/0 native checks verified`** (`WASAPI 48kHz 32-bit float`, exit 0).
- `test_u339_pc_native.py`: **`PC_PARITY_VACCINE: 767/0 checks passed`** (88종 일치, 금지어/이모지/전문약어 0건, 슬림/와이드 차별화·`Info->Waveform->Volume` 순서·dBFS VU 파동 검증, PE `3.4.0.0`).
- `verify_u339_live.py`: **`PASS: verify_u339_live all checks verified`** (`PC_VU_PIXEL_DIFF=36`, 5종 PNG 캡처, `WM_CLOSE` 트레이 상주 `visible=False`, 단축아이콘 재실행 동일 `PID=13888 visible=True`).
- `TestU338Aegis` + Android 실기 배포: **`PASS: 28751541/0 U338 Android v3.4.0 checks verified`**, `build_aegis.py` `210,417 bytes`, Note 20 Ultra (`R3CN800MV8R`) `adb install -r Success` (`versionCode=14`, `3.4.6`, `PID=28721`, `ANDROID_VU_PIXEL_DIFF=236`, 크래시 `0`건).

## Full Report
- `docs/보르타_이지스_PC_v34_고도화_보고서.md`
