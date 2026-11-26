# Card U338 Result: 보르타 이지스(Vorta Aegis Pro) Android v3.4.0 전 8탭 여정 시나리오·단계 배열·자동 코스 고도화

- **Card ID**: `U338-AEGIS-ANDROID-V34`
- **Status**: `DONE_PARTIAL_SCREEN_LOCKED` (코드·빌드·JVM 2,873만건·인플레이스 설치·실기 포그라운드 `AudioFlinger 48kHz` 실측 완료; 잠금화면 `showing=true`로 인해 잠금 해제 상태 UI 스크린샷 확인만 대기)
- **Target Device**: Samsung Galaxy Note 20 Ultra 5G (`R3CN800MV8R`, `SM-N986N`, Android 13 API 33)
- **Backup**: `.work/backup_20261127_aegis_v34/vorta_aegis_app/` (32 files)

## Key Changes
1. **`JourneyPackCatalog.java` (신규, Pure Java)**: 24종 여정 확장 프리셋(`ID 201..224`, `J01..J24`, 운동·피로 6 / 수면 3 / 집중·공부 3 / 명상·안정 3 / 두통·통증 3 / 기분·힐링 3 / 안티방어 2 / 모기·벌레 1) 추가 → 총 **88종** (`PresetModel` 48 & `ProPackCatalog` 16 무변경 보존).
2. **`JourneyOrderPolicy.java` (신규, Pure Java)**: 전 8탭 88종을 100% 누락·중복 없이 `1단계(준비) → 2단계(본활동) → 3단계(쿨다운) → 4단계(회복)` 순서로 구조화 + 4대 원탭 자동 코스(`웨이트 풀코스 65분`, `근비대·유산소 코스 55분`, `딥워크 풀코스 60분`, `꿀잠 숙면 코스 60분`).
3. **`AegisDspCore.java` & `PresetAdvisor.java`**: 바이노럴 진행 램프(`isSweep && freq1 > 0f`: `sweepStart → sweepEnd` 단조 진행 후 목표치 고정 유지, `getLiveBeatHz()`, 스피커 모드 이소크로닉 자동 보조), 여정 키워드/단계명 검색 확장.
4. **`AegisAudioService.java` & `MainActivity.java` & `AndroidManifest.xml`**: Doze 면역 프레임 카운트 자동 코스 시퀀서(`startRoutine`, `advanceRoutineStep`, 마지막 단계 20초 페이드아웃), 단계 섹션 헤더(`1단계 · 운동 전 준비` 등), `원탭 자동 코스` 스트립, 콘솔 `코스 N/M` + `다음 단계 ▶` 배너, `여정 24` 필터, `versionCode=8`, `versionName="3.4.0"`.

## Verification Receipts
- `TestU338Aegis`: `PASS: 28735931/0 U338 Android v3.4.0 checks verified` (exit 0; 88종×3프로파일 @120% `OvershootCount == 0`, 4x TP `<= -0.50 dBTP`, 8탭 88/88 단계 커버리지, 탭5 12종 순서, `J01`/`J05` 바이노럴 램프 홀드, `31`개 파일 정적 금지어/API 감사).
- `python vorta_aegis_pc/sync_presets.py`: `Synced 48 presets -> vorta_aegis_pc/web/presets.json (tabs=[12, 6, 6, 5, 5, 5, 5, 4])` (exit 0).
- `python vorta_aegis_app/build_aegis.py`: `202,225 bytes`, `apksigner verify` PASS (`SHA-256: 7522fef887fa912cf26a03e29d94de8032c178670db52c9afb566f3d4ae13e68`, exit 0).
- Note 20 Ultra (`R3CN800MV8R`): `adb install -r` `Success` (`versionCode=8`, `3.4.0`), `PID: 8870`, `isForeground=true`, `48000 Hz` Float(`Vol=75%`), `FATAL`/`AndroidRuntime`/`requestAudioFocus` `0`건, `STREAM_MUSIC=10/15`, `stay_on=0`.

## Full Report
- `docs/보르타_이지스_v34_여정시나리오_및_자동코스_고도화_보고서.md`
