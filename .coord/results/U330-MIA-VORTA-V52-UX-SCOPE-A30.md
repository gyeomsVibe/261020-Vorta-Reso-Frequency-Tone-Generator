# Card U330-MIA-VORTA-V52-UX-SCOPE-A30 결과 보고서

- **카드 상태**: `DONE/S6-S7-A30_1-A30_2-ALL-4-VERIFIED` (코드·`VortaVolumeGuard.java` `global.dolby_atmos_mode=0` 재빌드·호스트 JVM `21/21` + `13/13`·전용 발진기 4대 **S6·S7·A30-1·A30-2** 실기 헤드리스 `verify_all_devices_harsh.py` & 잠금 해제 실화면 UI·변조 표기·오실로스코프·안전볼륨 가드·스페어 4종 보존/업그레이드 전량 `PASS`)
- **대상 패키지**:
  - `com.vorta.unified` (`v5.2.0`, `versionCode=520`) — 전량 168+12 시나리오, 5탭+1탭 카드, Pro `변조` 순수 3채널(FM/AM/RM) 고정(`0 SeekBars`), 12종 시나리오 `프리셋` 이전, 출력창 `mTvTeleMod` 실시간 변조 표기, 실시간 오실로스코프·자가진단, `VortaAudioService` 자가치유 가드 적용
  - 보존·최적화 스페어 4종: `com.smato.a30frequency`, `com.vorta.s7frequency`, `com.vorta.s6frequency`, `com.vorta.freqgen`
- **제외 범위**: `vorta_aegis_app` 및 개인 기기 `Galaxy Note 20 Ultra 5G (SM-N986N)` 일절 미수정·무개입

---

## 1. 핵심 구현 내역

1. **Pro `변조` 순수 3채널 고정 & 12종 시나리오 `프리셋` 이전 (`ModulationProfile.java`, `ScenarioCatalog.java`, `SweepStore.java`)**:
   - **Pro `변조` 순수 3채널 고정 (`0 SeekBars`, 시나리오 0건)**: `SeekBar`와 시나리오 항목을 모두 제거하고 `FM 3.5%(1.50Hz)`·`AM 15%(4.00Hz)`·`RM 25%(12.50Hz)` 3개 순수 변조 카드와 `전체 OFF` 버튼만 유지(`SweepStore.savePureModulationToggles` 영구 저장, `스윕` 탭 상단 현재 변조 요약·`변조 설정` 바로가기 제공).
   - **12종 시나리오 `프리셋` 이전 & 기기별 168종 복원**: 시간·상황·전술 12종(`시간대별 4`+`상황별 4`+`전술별 4`, 각 시나리오에 최적 변조 내장)을 Pro `프리셋` 탭 `시간·상황·전술 시나리오 (12종)` 기본 접힘 아코디언(`총 5대 아코디언`)으로 이전하고, S6(`75`)·S7(`86`)·A30(`75`) 총 `168`종 5탭 시나리오(`메인|서브1|서브2|공통|기초`)를 유지.
2. **출력창·자가진단 실시간 변조 표기 & 320dp/360dp UX 융합 (`MainActivity.java`, `VortaAudioService.java`)**:
   - **상단 출력창 `mTvTeleMod` & 자가진단 `현재 변조:`**: 대기·발진 상태 및 Pro/기기 시나리오 전환에 따라 실시간 변조 상태를 즉시 표기 (`변조: OFF (순수 정현파)`, `변조: FM 3.5%(1.50Hz) · 고정`, `변조: AM 15%(4.00Hz) · 고정`, `변조: AM 15%(3.20Hz) · 고정`, `변조: FM 10.0%(7.03Hz) · 고정`, `변조: 시나리오별 최적값 자동 적용`).
   - **무겹침·1탭 카드**: Row 1~7 층 분리(`320dp` S6 / `360dp` S7·A30 겹침·잘림 0건), `(NEW)`·`[ISO]`·`↔`·이모지 0건, 대기 UI 재그리기 억제.
3. **무할루시네이션 실시간 신호 검증기 & S6/S7 자가치유 가드 (`PureDspCore.java`, `LiveWaveformView.java`, `VortaAccessibilityService.java`)**:
   - `SCOPE_RING_SIZE = 4096` 실측 송출 버퍼 기반 `LiveWaveformView`(`64 ms`, `256`pt, 상승 영점 트리거) + `480 ms` 주기 `measureLiveBufferQuality()`(`THD+N%`, Peak, RMS dBFS, DC 오프셋, 클리핑/NaN, API 24+ `AudioTrack.getUnderrunCount()`), `buildSelfDiagnosticReport()` 모달에 DAC 직전 `32-bit Float PCM`/HAL 범위와 외부 물리 앰프·스피커 경계 명시.
   - `VortaAudioService.onCreate()`에서 `VortaVolumeGuard.startBackgroundGuards(this)` 기동 및 `onDestroy()`에서 `!VortaAccessibilityService.isConnected()` 조건부 중지로 `am force-stop` 직후에도 안전볼륨 자동 해제 보장.
4. **S7 (`SM-G930S`) · A30 (`SM-A305N`) 실기 해부 & 백업**:
   - **S7 (`SM-G930S`, `ce021602916dbb3804`)**: `Exynos 8890`, `G930SKSU3EVG2`, `warranty_bit=0`, Android `8.0.0` (`API 26`, `2020-10-01`, `SKC`), `MARs 2.10`, `1080x1920`(`480dpi`, `360dp`), `CS47L91` (`mixer_gains_clearwater.xml` `Noise Gate Switch=0`, `HPOUT1L/R Impedance Volume=113`), `AudioOut_D` `48000 Hz` `ENCODING_PCM_FLOAT` (`Master mono: off`, `partial=0 empty=0`), Android 8.0 Oreo `AudioFlinger` UID/PID 추적 확인 및 `verify_all_devices_harsh.py` 보강.
   - **A30-1 (`SM-A305N`, `R59N105TEYF`, `SKC`) · A30-2 (`SM-A305N`, `R59N303R80N`, `KTC`)**: `Exynos 7904`, `MARs 6.00`, `Override density: 480`(`360dp`), `ABOX`(`AudioOut_D`+`AudioOut_15`), `mixer_gains.xml`(`DAC Gain=140`, `AVCBypass=58`), `dolby_atmos_mode=0`(`system`+`global` 교차 잠금 추가 반영, `VortaVolumeGuard.java`)·`multisound_state=0` 전 기기 가드 교차 반영. 수정 전 원본 `.work/backup_261122/`·`.work/backup_261123/` 보존.

---

## 2. 빌드 · 호스트 JVM · 실기기 검증 근거 (`exit 0`)

- **APK 빌드 및 `apksigner verify` PASS**:
  - `vorta_unified_s6_api24.apk`: `308,095 B` (`versionCode=520`)
  - `vorta_unified_s7_api26.apk`: `308,095 B` (`versionCode=520`)
  - `vorta_unified_a30_api30.apk`: `308,095 B` (`versionCode=520`)
  - 스페어 4종: `smato_a30_pro.apk` (`308,095 B`), `vorta_s7_pro.apk` (`291,711 B`), `vorta_s6_pro.apk` (`291,711 B`), `vorta_freqgen_pro.apk` (`177,023 B`)
- **호스트 JVM 검수 (`exit 0`)**:
  - `TestVortaEpoch1Audit`: `PASSED=21, FAILED=0` (`Gate5_TotalCatalog168 total=168`)
  - `TestVortaHarshAudit`: `PASSED=13, FAILED=0`, 전 `183` 스펙트럼 시나리오 `PASSED=183, FAILED=0`, `MaxPeak=0.89125`, `WorstPureSineTHD+N=0.000692%`, `Harsh_G10` `mTvTeleMod`·`modLockedNoSeekBar=true`·`pureModNoScenarios=true`, `Harsh_G11` `cleanTHD=0.00000% clip=0 | corruptTHD=16.86% clip=1`, `Harsh_G12` `channels=3 combos=8 proScenarios=12 pureTHD=0.000002% maxPeak=0.89125`
- **실기 헤드리스 `verify_all_devices_harsh.py` (4대 전량 PASS, `exit 0`)**:
  - **S6 (`06157df622d5ad1d`)**: `versionCode=520`, 스페어 보존, `6/6` `AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`, `AudioFlinger active=True 48kHz=True`, `RUNNING_CRITICAL` 생존, Direct-Binder `state 3->2` (`vol_ctrl=null`), `crash_hits=0`, `remaining_audio_services=[]`.
  - **A30-1 (`R59N105TEYF`, `SKC`)**: `versionCode=520`, 스페어 보존, `6/6` `AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`, `AudioFlinger active=True 48kHz=True`, `RUNNING_CRITICAL` 생존, `audio_safe_volume_state=1 mono_audio=0 sound_alive_effect=0 dolby_atmos_mode=0`, `crash_hits=0`, `remaining_audio_services=[]`.
  - **A30-2 (`R59N303R80N`, `KTC`)**: `versionCode=520` + 기존 구버전 스페어 4종 일괄 인플레이스 업그레이드(`vorta_freqgen` `310->330`, `smato_a30`/`s7`/`s6` `420->450`), `6/6` `AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`, `AudioFlinger active=True 48kHz=True`, `RUNNING_CRITICAL` 생존, `write_settings=True safe_vol=1 master_mono=0 mono=0 sound_alive=0 uhqa=0 tube=0 k2hd=0 concert=0 dolby_sys=0 dolby_global=0 multisound=0`, `crash_hits=0`, `remaining_audio_services=[]`.
  - **S7 (`ce021602916dbb3804`)**: `versionCode=520`, 스페어 보존(`vorta_aegis_app` 무개입), `6/6` `AudioTrack initialized: SR=48000Hz chunkFrames=2880 encoding=ENCODING_PCM_FLOAT`, Oreo PID 기반 `AudioFlinger active=True 48kHz=True`, `VortaAccessibilityService bound=True`, `write_settings=True`, `safe_vol=2`, `mono=0`, `sound_alive=0`, `uhqa=0`, `tube=0`, `k2hd=0`, `vol_lim=0`, `active_ms=0`, `dolby=null`, `crash_hits=0`, `remaining_audio_services=[]`.
- **잠금 해제 S6 실화면 5단계 검수 (`s6_ui_audit.py`, `exit 0`, `[ALL STEPS 1-5 VERIFIED PASS ON UNLOCKED S6]`)**:
  - 겹침 0건, `FLAG_KEEP_SCREEN_ON`, 드로어 순서(`1.Pro -> 2.A30 -> 3.S7 -> 4.S6`), 좀비 부활 0건, Pro `프리셋` 5아코디언 기본 접힘 + `시간·상황·전술 시나리오 (12종)`, `변조` 탭 `0 SeekBars`·`0` 시나리오 & 순수 FM/AM/RM↔출력창 `변조:` 동기화, 5탭, `43.20 Hz` 1탭 발진(`48k FLOAT · THD 0.000% · -4.2dB · 끊김 0`, `변조: OFF (순수 정현파)`), Step 5 `state=2 vol_ctrl=null`, `Crash hits=0`.
- **잠금 해제 S7 실화면 UI·변조·오디오 검수 (`s7_ui_audit.py`, `exit 0`, `[S7 LIVE UNLOCKED AUX-MUTED UI AUDIT PASS]`)**:
  - `360dp` 겹침 0건, `FLAG_KEEP_SCREEN_ON` OS 플래그 토글(`#81810100 -> #81810180 -> #81810100`), `Android 8.0 API 26 · AudioOut_D` + `현재 변조:` 자가진단, 드로어 순서, Pro `변조` `0 SeekBars`·`0` 시나리오 & `FM ON -> 변조: FM 3.5%(1.50Hz) · 고정` -> `전체 OFF`, `프리셋` 5아코디언 기본 접힘, `3. 갤럭시 S7` `서브1(16)`/`서브2(16)` 복원, `28.00 Hz` 1탭 무음 발진(`48k FLOAT · 복합파형 · -5.2dB · 끊김 0`, `변조: AM 15%(3.20Hz) · 고정`, `AudioFlinger 48kHz`), `safe_vol=2`, `crash_hits=0`.
- **잠금 해제 A30-1 실화면 UI·오디오 검수 (`a30_ui_audit.py`, `exit 0`, `[A30 LIVE UNLOCKED UI AUDIT PASS]`)**:
  - Pro 화면 `변조: AM 15%(4.00Hz) · 고정` & 자가진단 모달 `현재 변조: AM 15%(4.00Hz) · 고정`, `2. 갤럭시 A30` `순음 고정(볼륨 무개입)`, `A30 · SPK`, 5탭, `17.39 Hz` 1탭 발진(`48k FLOAT · 복합파형 · -3.9dB · 끊김 0`, `변조: FM 10.0%(7.03Hz) · 고정`), `AudioFlinger 48kHz`, `audio_safe_volume_state=1`.
- **잠금 해제 A30-2 실화면 UI·오디오 검수 (`a30_2_ui_audit.py`, `exit 0`, `[A30-2 LIVE UNLOCKED 360DP SAFE-MUTED UI AUDIT PASS]`)**:
  - `1080x2340` (`480dpi`, `fontScale=1.3`, `360dp`) `화면유지`↔`0.00 Hz` 겹침 0건, `FLAG_KEEP_SCREEN_ON` OS 플래그 토글(`#81810100 -> #81810180 -> #81810100`), Pro `변조` `0 SeekBars`·`0` 시나리오 & `FM ON -> 변조: FM 3.5%(1.50Hz)` -> `전체 OFF`, `프리셋` `시간·상황·전술 시나리오 12종` 기본 접힘, `Android 11 API 30` 자가진단, `아침 (07~13)` 무음(`speaker=0->15` 복원) 1탭 발진(`17.38 Hz`, `48k FLOAT · 복합파형 · -3.9dB · 끊김 0`, `변조: FM 10.0%(7.03Hz) · 고정`), `AudioFlinger 48kHz`, `audio_safe_volume_state=1`, `crash_hits=0`.

---

## 3. 산출물 요약

- **MIA 전략·A30·전 기기 비교분석서**: [MIA_VORTA_V52_전략기획_및_A30_비교분석서.md](file:///d:/D_Workspace_NB/주파수/docs/MIA_VORTA_V52_전략기획_및_A30_비교분석서.md)
- **기기 역설계 사전조사서**: [등록기기_4종_펌웨어_부트로더_안드로이드_역설계_사전조사서.md](file:///d:/D_Workspace_NB/주파수/docs/등록기기_4종_펌웨어_부트로더_안드로이드_역설계_사전조사서.md)
- **실화면 검수 스크린샷 (S6·S7·A30-1·A30-2)**: `.work/s6_v52_*.png`, `.work/s7_v52_*.png`, `.work/a30_v52_*.png`, `.work/a30_2_v52_home.png`, `.work/a30_2_v52_pro_mod.png`, `.work/a30_2_v52_playing.png`, `.work/a30_2_v52_diag.png`
- **실기 스냅샷 (S6 / S7 / A30-1 / A30-2)**: [device_snapshot_SM-G920K_06157df622d5ad1d.txt](file:///d:/D_Workspace_NB/주파수/.work/device_snapshot_SM-G920K_06157df622d5ad1d.txt) / [device_snapshot_SM-G930S_ce021602916dbb3804.txt](file:///d:/D_Workspace_NB/주파수/.work/device_snapshot_SM-G930S_ce021602916dbb3804.txt) / [device_snapshot_SM-A305N_R59N105TEYF.txt](file:///d:/D_Workspace_NB/주파수/.work/device_snapshot_SM-A305N_R59N105TEYF.txt) / [device_snapshot_SM-A305N_R59N303R80N.txt](file:///d:/D_Workspace_NB/주파수/.work/device_snapshot_SM-A305N_R59N303R80N.txt)
