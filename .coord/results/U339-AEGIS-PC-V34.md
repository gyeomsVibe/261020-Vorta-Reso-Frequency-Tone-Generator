# Card U339 Result: PC v3.4.0 고도화 · Android v3.4.1~v3.4.4 쉬운 가이드·단일 볼륨·미니/스튜디오 차별화

- **Card ID**: `U339-AEGIS-PC-V34`
- **Status**: `DONE`
- **Targets**:
  - Windows PC Native: `vorta_aegis_pc/VortaAegisPC.exe` (`v3.4.0`, `3.4.0.0`, `259,072 bytes`)
  - Android Mobile: `com.vorta.aegis` (`v3.4.4` `versionCode=12`, `210,417 bytes`, Galaxy Note 20 Ultra `R3CN800MV8R`)
- **Backups**: `.work/backup_20261127_aegis_v341/vorta_aegis_app/`, `.work/backup_20261127_aegis_pc_v34/vorta_aegis_pc/`

## Key Changes
1. **PC `v3.4.0` 완전 패리티 & DSP/팩 고도화**:
   - `vorta_journey_pack_v1.json` (24종 `J01..J24`) + `vorta_pc_pro_pack_v1.json` (`v1.1.0`) + `JourneyAndAdvisor.cs` 추가 → 모바일과 동일한 **88종** 카탈로그, 8탭 `1단계→4단계` 순서, 4대 원탭 자동 코스(`웨이트 풀코스`, `근비대·유산소 코스`, `깊은 집중 풀코스`, `꿀잠 숙면 코스`), 트레이 코스 서브메뉴 구현.
   - `AegisDspEngine.cs`, `WasapiAudioEngine.cs`, `AppStateStore.cs`, `Program.cs`: 바이노럴 진행 램프(`LiveBeatHz`), 스테레오 비상관 핑크 노이즈, 스테레오 앰비언트 블렌더(`꺼짐`/`브라운`/`핑크`/`빗소리` 0~60%), Post-AM·프로파일 전환 디클리커, 20초 제곱 포락선 부드러운 종료, 프리셋별 볼륨 기억·고볼륨 안전 전환.
2. **비전문가 일상 UX 정화 (`v3.4.1` 모바일 + `v3.4.0` PC)**:
   - `포모도로`, `CNS`, `HRV`, `DOMS`, `VDT`, `HIIT` 등 설명 없는 전문 약어를 전 88종 카드에서 일상 한국어로 풀고, 카드 `[설명]` 팝업 + 상단 `[가이드]`/`[쉬운 가이드]` 용어집 대화상자 추가.
3. **단일 통합 볼륨 & 미니/스튜디오 차별화 (`v3.4.2~v3.4.4` 모바일 + `v3.4.0` PC)**:
   - Android `MainActivity.java`: 슬림 `quickBar`(`mBtnQuickMute`, `mSeekQuickVolume`, `mTvQuickVolPct`)와 스튜디오 내부 중복 볼륨 제거, **미니 모드에서는 `mScopeSection` (`View.GONE`)을 숨겨 간결한 `miniHeader → 단일 통합 볼륨`으로 운용**, **`스튜디오 ▼` 펼침 시에만 `miniHeader → mScopeSection (자동 애니메이션 LiveWaveformView) → 단일 통합 볼륨 → mConsoleDetailBody`**가 나타나도록 구현.
   - PC `CompanionUi.cs`: `BuildConsoleCard` 순서를 `infoBox → mScope(LiveWaveformScope) → volSection → telGrid`로 재정렬(`MiniBarWindow`는 파동 없는 미니 바), `< 520px` 슬림 모드 타이틀바 버튼(`mAboutBtn` 숨김 + `가이드`/`미니` 축약)으로 좌측 타이틀 클리핑 해소.

## Verification Receipts
- `VortaAegisPC.exe --self-test` (via `test_u339_pc_native.py`): **`PASS: 28542541/0 native checks verified`** (`WASAPI 48kHz 32-bit float`, exit 0).
- `test_u339_pc_native.py`: **`PC_PARITY_VACCINE: 764/0 checks passed`** (88종 1:1 Android-PC 일치, 금지어/이모지/전문약어 0건, 단일 볼륨·`Info->Waveform->Volume` 순서 검증, `version.json 3.4.0`, PE `3.4.0.0`).
- `verify_u339_live.py`: **`PASS: verify_u339_live all checks verified`** (Desktop/Start Menu `.lnk` `v3.4`, 4종 PNG 캡처·시각 검수, `WM_CLOSE` 트레이 상주 `visible=False`, 단축아이콘 재실행 동일 `PID=17664 visible=True`).
- `TestU338Aegis` + Android 실기 배포: **`PASS: 28747651/0 U338 Android v3.4.0 checks verified`**, `build_aegis.py` `210,417 bytes`, Note 20 Ultra (`R3CN800MV8R`) `adb install -r Success` (`versionCode=12`, `3.4.4`, `PID=24591`, 크래시 `0`건, 미니·스튜디오 양모드 실기 스크린샷 확인).

## Full Report
- `docs/보르타_이지스_PC_v34_고도화_보고서.md`
