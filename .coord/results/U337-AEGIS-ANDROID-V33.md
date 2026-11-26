# Card U337 Result: 보르타 이지스(Vorta Aegis Pro) Android v3.3.0 전수점검·시장조사·DSP/UX 고도화

- **Card ID**: `U337-AEGIS-ANDROID-V33`
- **Status**: `DONE_PARTIAL_SCREEN_LOCKED` (코드·빌드·JVM 2,303만건·인플레이스 설치·실기 포그라운드 `AudioFlinger 48kHz` 실측 완료; 잠금화면 `showing=true`로 인해 잠금 해제 후 터치 UI 스크린샷만 사용자 확인 대기)
- **Target Device**: Samsung Galaxy Note 20 Ultra 5G (`R3CN800MV8R`, `SM-N986N`, Android 13 API 33)
- **Backup**: `.work/backup_20261127_aegis_v33/vorta_aegis_app/` (25 files)

## Key Changes
1. **`PresetAdvisor.java` (신규, Pure Java)**: 시간대별 `지금 추천` 3종, 일상 한국어·동의어·Hz 통합 검색, 카드별 청취 가이드 배지, 프리셋별 권장 볼륨 및 고볼륨→수면/휴식 전환 안전 감지.
2. **`AegisDspCore.java` (고도화)**: 출력 음향 보정 4모드(`PROFILE_AUTO`/`PHONE_SPEAKER`/`HEADPHONE`/`PURE`), 스테레오 비상관 핑크 노이즈(`noiseType=1`), 절차적 배경 사운드 블렌드(`OFF`/`BROWN`/`PINK`/`RAIN` 0~60%), Post-AM/프로파일 전환 디클리커, `2π` 위상 래핑, 20초 제곱 포락선 부드러운 수면 페이드아웃(`requestGentleStop`). `-0.50 dBTP` 리미터 및 `OvershootCount == 0` 100% 유지.
3. **`AegisAudioService.java` + `AegisQuickTileService.java` + `AndroidManifest.xml` + Vector 5종**:
   - `mSessionGen` 세션 세대 가드, `AudioTrack.ERROR_DEAD_OBJECT` 3회 자동 복구, API 24+ 빠른 설정 타일(`BIND_QUICK_SETTINGS_TILE`), 프리셋별 볼륨 기억·최근 재생 8종 기록, 알림창 요약 실시간 동기화, 배터리 최적화/언더런 진단 API (`versionCode=7`, `versionName="3.3.0"`).
4. **`MainActivity.java` + `build_aegis.py` + `deploy_note20u.ps1`**:
   - 상시 퀵 볼륨 바, `▶ 이어듣기`, 시간대 추천 3종, 한국어 검색창, 4모드 출력 보정·배경 사운드 블렌드 스튜디오, `최근` 필터, 2줄 적층 디텐트·가로 스크롤 필터(`360dp / 1.3x` 클리핑 제거), 무변경 무갱신 텔레메트리(`1000ms` 유휴 틱), `설정·정보` 통합 진단 시트.
   - `build_aegis.py` 저장소 내 `smato_a30_app/debug.keystore`(`SHA-256: 7522fef8...`) 우선 해석 교정 및 `deploy_note20u.ps1` 상대 경로화.

## Verification Receipts
- `TestU337Aegis`: `PASS: 23036785/0 U337 Android v3.3.0 checks verified` (exit 0; 64종×3프로파일 + 배경음 60% @120% `OvershootCount == 0`, 4x Catmull-Rom TP `<= -0.50 dBTP`, Pure DFT `THD+N < 0.1%`, 29개 파일(`src 10 + res 18 + AndroidManifest.xml 1`) 정적 금지어/API 감사).
- `python vorta_aegis_pc/sync_presets.py`: `Synced 48 presets -> vorta_aegis_pc/web/presets.json (tabs=[12, 6, 6, 5, 5, 5, 5, 4])` (exit 0).
- `python vorta_aegis_app/build_aegis.py`: `173,553 bytes`, `apksigner verify` PASS (`SHA-256: 7522fef887fa912cf26a03e29d94de8032c178670db52c9afb566f3d4ae13e68`, exit 0).
- Note 20 Ultra (`R3CN800MV8R`): `adb install -r` `Success` (`versionCode=7`, `3.3.0`), `PID: 4078`, `isForeground=true`, `AudioOut_D 48000 Hz`, `FATAL`/`AndroidRuntime`/`requestAudioFocus` `0`건, `STREAM_MUSIC=10/15`, `stay_on_while_plugged_in=0`.

## Full Audit Report
- `docs/보르타_이지스_모바일_전수점검_시장조사_및_v33_고도화_보고서.md`
