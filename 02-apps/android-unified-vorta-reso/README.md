# 보르타 통합 v5.2.1 (`com.vorta.unified`, A30 · S7 · S6 공용)

기존 사용자 설정·저장 스윕·큰 글씨 크기를 **100% 보존**하면서 A30 실시간 볼륨 표시(`볼륨 xx/15단`)와 4대 레거시 결함을 수정한 유지보수 업데이트(`versionCode=521`)입니다.

## `v5.2.0 → v5.2.1` 주요 개선

1. **A30 실시간 볼륨 표시 (`VortaVolumeGuard.formatLiveVolumeStepText`)**:
   - OS 볼륨 쓰기 개입 `0건` 원칙을 유지한 채 `AudioManager.getStreamVolume` 을 읽기 전용으로 조회해 모든 기기(A30/S7/S6)에 **`볼륨 xx/15단`** 실시간 표기 (`VOLUME_CHANGED_ACTION` 즉시 반영).
2. **글씨 크기(`setTextSize`) 100% 보존 + 2줄 균형 줄바꿈 (`applyBalancedWrap`)**:
   - 기존 68개 `setTextSize(...)` 호출을 단 1건도 축소/변경하지 않고 `maxLines=2` + `BREAK_STRATEGY_BALANCED` 로 말줄임·불규칙 줄바꿈만 제거.
3. **S7·S6 저장 무반응 & 스크롤 점프 해결**:
   - `Toast.makeText` 전면 제거 → `FrameLayout` 하단 인앱 배너(`showBanner`), `adjustResize` + `FOCUS_BEFORE_DESCENDANTS` + `scrollTo` 위치 잠금, `SweepStore.commit()` 동기 반환 확인.
4. **S7·S6 안전볼륨 해제 레드팀 보강 & 원본 키스토어 서명 복구**:
   - 5분 재시도 쿨다운, 수동 `10→9` 감쇠 존중, `.SafeVolAuth` 정화, 22종 한국어 키워드·`4500ms` 능동 스캔, `build_unified.py` 원본 `smato_a30_app/debug.keystore` 상대경로 복구.

## 빌드 & 검증

```powershell
# APK 3종 (S6 API 24 / S7 API 26 / A30 API 30) 빌드 & 검증
python build_unified.py
```

- 호스트 JVM 회귀 및 글씨 크기 불변 감사: `tests/TestVortaUnifiedV521Audit.java` (`PASSED=4 FAILED=0`)
