# Card U329-VORTA-UNIFIED-V51-HARSH-AUDIT-DOSSIER 결과 보고서

- **카드 상태**: `DONE (S6 실기 검증 통과 / S7·A30 금일 USB 연결 대기)`
- **대상 패키지**: `com.vorta.unified` (`v5.1.0`, `versionCode=510`) 및 `com.vorta.freqgen` (`v3.3.0`, `versionCode=330`)
- **제외 범위**: `vorta_aegis_app` 및 개인 기기 `Galaxy Note 20 Ultra 5G (SM-N986N)` 미수정·대상 제외

---

## 1. 핵심 구현 내역

1. **보르타 Pro 완전 통합 및 아코디언 메뉴 순서 확정 (`VortaModule.java`, `MainActivity.java`, `SweepStore.java`)**:
   - `VortaModule.java` 열거형 및 좌측 상단 아코디언 탭 순서를 엄격히 재배열:
     `1. 보르타 Pro` (`PRO_MANUAL`) → `2. 갤럭시 A30` (`A30_SWEEP`) → `3. 갤럭시 S7` (`S7_ANCHOR`) → `4. 갤럭시 S6` (`S6_BTL`).
   - `SweepStore.java`: `deleteNamedSet`, `loadKeepScreenOn`, `saveKeepScreenOn` 추가.
   - `MainActivity.java`: 스윕 카드 `반복`/`1회` 루프 토글, `f2 적용` 버튼, Pro 프리셋 4대 아코디언(ISO 3팩 / 5밴드 개별카드+배치 / 12타겟 공진매트릭스 / 3 HW 스위트) 기본 접힘 구현, 모든 재생 액션에 `startServiceCompat` 연결, 이모지 0건, `setMinWidth(0)` 적용.
2. **가혹 환경 DSP 순도·위상 연속성 개선 (`PureDspCore.java`, `VortaAudioService.java`, `PureDspEngine.java`, `AudioEngineService.java`)**:
   - 공유 `renderUnifiedChunk` 엔진 적용 및 시나리오 전환 시 클릭 없는 핫스위칭(`scenDualMix` 40ms 램프 + `scenModOffset` 지수 브리지) 구현.
   - 정지 및 20ms AUX 음소거 페이드아웃 구간의 위상 동결 DC 스텝 결함을 `matchTailPhase` + 진행형 `tailPhase`(C¹ 직교 위상 매칭)로 수정 (통합앱 v5.1 및 Pro v3.3 동시 패치).
   - 알림 라벨 `VORTA v5.1` 갱신 및 매니페스트 3종(`s6_api24`, `s7_api26`, `a30_api30`) `versionCode="510"`, `versionName="5.1.0"`, 라벨 `"보르타 통합 v5.1 (S6/S7/A30)"` 일괄 업데이트.
3. **수동 전원 버튼 외 화면 꺼짐 방지 구현 및 역설계 (`MainActivity.java`, `PowerManagerService`)**:
   - `MainActivity.java` 텔레메트리 스트립에 `FLAG_KEEP_SCREEN_ON` 1탭 토글(`화면유지 OFF/ON`, 기본값 `false`) 추가 및 영구 저장(`keep_screen_on_v1`).
   - 시스템 전역 설정(`screen_off_timeout=2147483647` / 원복 `600000`) 및 삼성 `mSecuredLockTimeoutSetting` / `mMaximumScreenOffTimeoutFromDeviceAdmin` 역설계 비교 문서화 완료.

---

## 2. 빌드·호스트 JVM·스펙트럼·S6 실기 검증 결과

- **APK 빌드 및 서명 (`build_unified.py`, `build_freqgen.py`, `exit 0`, `apksigner verify` PASS)**:
  - `vorta_unified_s6_api24.apk`: `267,135 B` (`versionCode=510`)
  - `vorta_unified_s7_api26.apk`: `263,039 B` (`versionCode=510`)
  - `vorta_unified_a30_api30.apk`: `263,039 B` (`versionCode=510`)
  - `vorta_freqgen_pro.apk`: `177,023 B` (`versionCode=330`)
- **호스트 JVM & 에폭-2 전 시나리오 스펙트럼 감사 (`exit 0`)**:
  - `TestVortaEpoch1Audit`: `PASSED=20, FAILED=0`
  - `TestVortaHarshAudit`: `PASSED=11, FAILED=0`, 전 63개 시나리오 `PASSED=63, FAILED=0`, `MaxPeak=0.89125`, `WorstPureSineTHD+N=0.000692%` (`[1, cos, sin]` 결합 최소자승 계측으로 비정수 주파수 창 오차 해결)
  - C¹ 페이드아웃(`Harsh_G2`): `stopJump=0.00001`, `tailDC=0.000025`, `end=0.0`, `phaseMatch=true`
  - 극한 부하 게이트: 24h 위상 랩 `THD=0.000002%`, 50ms 48시나리오 슬램 `maxDelta=0.00484`, 9소스 중첩 `Peak=0.49957`, 4스레드 경쟁 `errors=0 / chunks=834`, 16-bit TPDF `THD=0.002423%`
  - 메뉴 순서(`Harsh_G7`) & 정적 UI/Pro 동등성(`Harsh_G10`): PASS
- **실기 Galaxy S6 (`SM-G920K`, `06157df622d5ad1d`) 가혹 검증 (`python verify_all_devices_harsh.py`, `exit 0`)**:
  - `versionCode=510` 설치, 5회 고속 Start/Stop + `RUNNING_CRITICAL` 트림 생존 (`6/6 AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`), `AudioFlinger active=True 48kHz=True`
  - 화면 꺼짐(`mWakefulness=Asleep`) 상태 Direct-Binder `state 3->2` 해제(`vol_ctrl=null`), `crash_hits=0`, `remaining_audio_services=[]`

---

## 3. 산출물 및 대기 작업

- **사전조사서**: [등록기기_4종_펌웨어_부트로더_안드로이드_역설계_사전조사서.md](file:///d:/D_Workspace_NB/주파수/docs/등록기기_4종_펌웨어_부트로더_안드로이드_역설계_사전조사서.md)
- **에폭-2 스펙트럼 표**: [epoch2_all_scenarios_spectral_report.md](file:///d:/D_Workspace_NB/주파수/.work/epoch2_all_scenarios_spectral_report.md)
- **멀티 기기 실기 검증기**: [verify_all_devices_harsh.py](file:///d:/D_Workspace_NB/주파수/verify_all_devices_harsh.py)
- **S6 실기 스냅샷**: [device_snapshot_SM-G920K_06157df622d5ad1d.txt](file:///d:/D_Workspace_NB/주파수/.work/device_snapshot_SM-G920K_06157df622d5ad1d.txt)
- **금일 USB 연결 대기**: `Galaxy S7 (SM-G930S)`, `Galaxy A30 (SM-A305N)` 실기 연결 및 `S6` 잠금 해제 후 UI/화면유지 육안 확인 (`python verify_all_devices_harsh.py`, `보르타_통합앱_OS버전별_설치_원클릭.bat` 실행).
