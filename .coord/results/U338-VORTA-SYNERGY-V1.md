# Card U338-VORTA-SYNERGY-V1 결과 보고서

- **카드 상태**: `DONE/SYNERGY-V200-ALL-4-DEVICES-VERIFIED` (`v2.0.0` 전면 리뉴얼 · S6(`CH2`) · S7(`CH1`) · 본카운터 A30(`CH4`) · 트랜스 메인 A30(`CH3`) 전 4대 실기기 전수 버튼 검증 완료)
- **신규 앱 패키지**: `com.vorta.synergy` (`v2.0.0`, `versionCode=200`, `vorta_synergy_app/`)
- **기존 앱 보존**: `vorta_unified_app` (`com.vorta.unified`, `versionCode=521`) 및 `보르타_통합앱_OS버전별_설치_원클릭.bat` 100% 무수정 보존

---

## 1. 기존 통합앱 4대 결함 원인 규명 및 수정 내역

| 번호 | 기존 현상 | 규명된 근본 원인 | 보르타 시너지 v1.0.0 해결책 |
|---|---|---|---|
| **1** | S7/S6에서 스윕 저장 확인이 안 뜨고 연속 저장 시 스크롤이 오르락내리락함 | ① Android 7.0/8.0(API 24/26)에서 알림 설정이 꺼지거나 포커스 경합 시 `Toast.makeText`가 시스템에서 억제됨 ② 카드 내부 인라인 `EditText` 다수 배치 + `windowSoftInputMode="adjustPan"` + `commit()` 반환 미확인 | ① `Toast.makeText` 전면 제거 → 루트 `FrameLayout` 하단 인앱 배너(`showBanner`) 도입 + `SynergyStore.saveStepsNow`/`saveNamedSet` 동기 `commit()` 확인 ② 카드 내 인라인 `EditText` 전면 제거(`AlertDialog` + `SynergyDialogTheme` + `adjustResize`) 및 `FOCUS_BEFORE_DESCENDANTS` + `scrollTo` 복원 |
| **2** | A30 우측 상단에 `xx/15단` 볼륨 표시가 없고 `"자유볼륨"`/`"해당없음"`/`"순음 고정"` 등 내부 용어 노출 | `buildGuardStatusText()`가 `ENABLE_VOLUME_GUARD == false`일 때 볼륨 조회를 생략하고 내부 문구를 반환함 | `SynergyVolumeGuard.formatLiveVolumeBadge()`가 **모든 기기(A30/S7/S6)** 에서 읽기 전용 `AudioManager.getStreamVolume`/`getStreamMaxVolume`을 조회해 `AUX · 12/15` / `SPK · 12/15`를 실시간 표시(A30 OS 볼륨 쓰기 0건). 내부·특정기기 용어(`자유볼륨`, `해당없음`, `순음 고정`, `스마토`, `트랜스2`, `본카운터` 등) 0건 상용 일반화 |
| **3** | 글자 잘림 및 문장 줄 불규칙 | `sp` 단위(OS `fontScale=1.3` 확대 시 넘침) 및 1줄 고정 폭 경합 | `TypedValue.COMPLEX_UNIT_DIP` 폰트 고정, `BREAK_STRATEGY_BALANCED` 2줄 균형 줄바꿈, 3칩 텔레메트리 스트립 분리 |
| **4** | S7/S6 안전볼륨 해제 비판적 재검토 | ① S7 `sConsecutiveDialogRetries >= 3` 도달 시 `HEADSET_PLUG` 재삽입 전까지 영구 잠금 ② 사용자 수동 볼륨 하향(`10→9`)을 OS 클램프로 오인 가능 ③ S6 부팅 이미 해제 상태(`state==2`)에서도 `volume_controller_service_component` 반복 기록 ④ `com.vorta.unified`와 공존 시 `.SafeVolAuth` 잔존 경쟁 ⑤ 접근성 미연결/긴 한국어 키워드 누락 | ① **5분(`300s`) 쿨다운 후 재시도 카운터 자동 리셋** ② `state==2`에서 수동 `10→9` 감쇠 존중 ③ 이미 `state==2` & 언클램프 시 Binder/Settings 터치 생략 ④ `.SafeVolAuth` 복원 시 null 정화 ⑤ 접근성 키워드 확장(`"음량 제한"`, `"최대 음량"`, `"미디어 음량"` 등) 및 `4500ms` 확인 대기 |

---

## 2. 4채널 시너지 구조 & 핵심 기능

1. **4채널 하드웨어 대역 가드레일 (`SynergyChannel.java`)**:
   - `CH 1 · 극저역 우퍼`: `1.0 ~ 21.0 Hz` 하드 클램프 (주력 `4.5~20.5 Hz`)
   - `CH 2 · 저역 우퍼`: `10.0 ~ 40.0 Hz` 하드 클램프 (주력 `22.0~39.5 Hz`)
   - `CH 3 · 골전도 메인`: `40.0 ~ 63.0 Hz` 하드 클램프 (**핵심 주력 `52.0~63.0 Hz` 최대 출력 연속 메인**, `0 dB` 트림)
   - `CH 4 · 골전도 보조`: `40.0 ~ 63.0 Hz` 하드 클램프 (**열보호 보조 `40.0~51.0 Hz` / `CH3 - 4 Hz` 맥놀이**, 기본 열보호 듀티 `20분/10분`·`15/15분`, 노브 12시 이하 안내)
2. **3대 탭 + 20×4 자동 시너지 + 반수동 + 기초**:
   - **자동 시너지 (`S-01`~`S-20`)**: 시간대(4)·상황(4)·효과(4)·기능(4)·역할(4) 총 20종 × 4채널 = 80개 프로그램. 서로소 스윕 주기(`240~1201s`), 스텝 체류(`STEPPED_DWELL`), `CH3·CH4` 간 `+4 Hz` 제어 맥놀이, 24시간 RTC 자동 전환 내장.
   - **반수동**: 긴급 1탭 4종(`+4Hz 맥놀이`, `스텝 체류`, `주파수 시프트`, `저발열 -3dB`), 채널별 빠른 주파수 4종, 사용자 혼합 체인 편집기(`단일음`/`스윕`/`스텝 체류`/`냉각 휴지`), 세트 저장/불러오기/삭제, `fleet_v1` JSON 내보내기·가져오기, 순수 3채널 변조(`FM 3.5%`, `AM 15%`, `RM 25%`), 내성 지터 ON/OFF, 열보호 듀티 전환, 디지털 트림(`0/-2/-4/-6 dB`).
   - **기초**: 대역 스윕 10종 + 단독 공진 12종(총 22종), 채널 범위 초과 시 안전 보정 모달 제공.

---

## 3. 불안정 기기 보정·보강 엔진 (v1.1.0 추가, `HardwareStabilizer.java`)

1. **채널별 변위·부하 보호 (`computeContourGainDb` + `softStartSec`)**:
   - `CH 1`: `2.0s` 소프트스타트 + `60ms` 슬루 + 양단(`1.0~3.5 Hz` 최대 `-2.0 dB`, `17.5~21.0 Hz` 최대 `-2.5 dB`) 변위 감쇄로 우퍼 찢어짐·이탈 억제.
   - `CH 2`: `2.0s` 소프트스타트 + 병렬 이중 드라이버 보호 기본 `-1.5 dB` 트림 + 상단(`34.0~40.0 Hz`) 최대 `-3.0 dB` 감쇄.
   - `CH 4`: `1.8s` 소프트스타트 + 기본 `-2.0 dB` 트림 + 하단(`40.0~45.0 Hz`) `-1.5 dB` 감쇄 + Class-AB 1차 RC 열모델(`tauHeat=600s`, `tauCool=300s`, 65% 이상 최대 `-3.0 dB` 자동 감쇄).
2. **기계적 잡음 대역 회피 노치 (`computeNotchCutDb`)**: `반수동 → 잡음 대역 회피`에서 중심 주파수·폭(`±Hz`)을 지정하면 Raised-Cosine `-12 dB` 부드러운 회피 필터 적용(`DeviceProtectedStorage` 영구 저장).
3. **기기 온도·배터리·시계 보호**: `43.0°C` 이상 `-3.0 dB` 안전 감쇄(`39.0°C` 해제 히스테리시스), 원클릭 설치 배치(`6,361 B`)에 `protect_battery=1`·고속충전 OFF·`stay_on=0`·S6 `< 2024년` 시계 점검 추가.

---

## 4. 호스트 JVM 8대 게이트 & APK 빌드 실측 결과 (`exit 0`)

- **`TestVortaSynergyAudit`**: **`PASSED=10, FAILED=0`** (`exit 0`)
  - `Gate1`: 20패키지 80프로그램 채널 대역 위반 `0건`, 클램프 경계 통과
  - `Gate2`: CH1~CH4 주파수 충돌 `0건`, 스윕 주기 서로소 100%(`nonCoprimeSweeps=0`)
  - `Gate3`: 80 시너지 + 22 기초(`102`종) DSP 전수 통과(`MaxPeak=0.89125` `-1.0 dBTP`, 순음 최대 `THD+N=0.000003%`, `-6 dB` 트림 `0.4467`, 백신 주입 `corruptTHD=22.22%` 적발)
  - `Gate4`: 전 20패키지 `CH4` 열보호 듀티 필수 탑재 + `C¹` 무클릭 페이드(`coolAbs=0.000000`, `maxDelta=0.00445`)
  - `Gate5`: `SynergyStore` 4종 스텝 + `FleetJson` 라운드트립 및 좀비 부활 0건
  - `Gate6`: 금지 내부 용어 `0건`, `Toast.makeText` `0건`, 인라인 `EditText` `0건`, 3매니페스트 `adjustResize` 적용
  - `Gate7`: `SynergyVolumeGuard` 5대 레드팀 정책 통과
  - `Gate8`: `HardwareStabilizer` 변위 롤오프·`CH3` `0 dB` 풀 헤드룸·`-12 dB` 노치·RC 열모델(`load=0.86` → `-1.84 dB` 감쇄, 무음 냉각 `0.12`, 고온 순음 `THD+N=0.002957%`)·배터리 온도 히스테리시스 전량 통과
- **APK 빌드 및 `apksigner verify` PASS (`versionCode=111`, `versionName=1.1.1`)**:
  - `vorta_synergy_s6_api24.apk`: `299,903 B` (`targetSdk=24`)
  - `vorta_synergy_s7_api26.apk`: `299,903 B` (`targetSdk=26`)
  - `vorta_synergy_a30_api30.apk`: `299,903 B` (`targetSdk=30`)
- **실기기(본카운터 A30 `R59N105TEYF`, Android 11) 라이브 검증 (`v1.1.1`)**:
  - **채널·UI·무결성**: `CH 4 · 골전도 보조` 선택/유지, `SPK · 15/15` 볼륨 배지 실시간 표시(OS 볼륨 쓰기 0건), 금지 내부 용어 `0건`, 유휴 리드로우 억제로 `uiautomator dump --compressed` 연속 정상 동작
  - **스크롤 점프 0건 저장**: 반수동 `세트 저장`(`TestSet111`) 후 `세트 저장` 버튼 Y좌표 `1386 -> 1386`(점프 `0px`) 확인
  - **실시간 48kHz Float 출력 & 정지**: `S-01`(`51.0 Hz`) 및 `24h ON` 심야 자동 전환 `S-04`(`46.50 Hz`) → `진동 출력 중`, `Float THD 0.00%`, `AudioFlinger 48000 Hz`, `전체 정지` → `대기 중`·`0.00 Hz` 확인
  - **시스템 불변성**: `com.vorta.unified=521` 덮어쓰기 업그레이드·공존 유지(글씨 크기 0건 변경 + `볼륨 15/15단`), `audio_safe_volume_state=1`, `stay_on_while_plugged_in=0`, `protect_battery=1`, `STREAM_MUSIC=15/15`, 크래시 `0건`
- **실기기(트랜스 메인 A30 `R59N303R80N`, Android 11) 라이브 검증 (`v1.1.1` + 구버전 `v5.2.1`)**:
  - **보르타 시너지 `v1.1.1` (`versionCode=111`)**: `CH 3 · 골전도 메인` 선택·영속화, `SPK · 15/15` 표시, 금지어 `0건`, `S-01` 실시간 출력(`진동 출력 중`, `56.03 Hz`, `FM 3.5%(1.5Hz)`, `AudioFlinger 48000 Hz`) 및 `전체 정지`(`대기 중`·`0.00 Hz`) 확인
  - **구버전 통합 `v5.2.1` (`versionCode=521`)**: 원본 키스토어 서명 일치 덮어쓰기 설치로 기존 설정 보존, `볼륨 15/15단` 실시간 표시·구버전 내부 문구 0건·2줄 균형 줄바꿈 적용(글씨 크기 유지)
  - **시스템 불변성**: `audio_safe_volume_state=1`, `stay_on_while_plugged_in=0`, `protect_battery=1`, `adaptive_fast_charging=0`, `STREAM_MUSIC=15/15` 변경 0건, 크래시 `0건`
- **실기기(갤럭시 S7 `ce021602916dbb3804`, `SM-G930S`, Android 8.0) 라이브 검증 (`v1.1.1` + 구버전 `v5.2.1`)**:
  - **보르타 시너지 `v1.1.1` (`versionCode=111`)**: `CH 1 · 극저역 우퍼(1~21 Hz)` 활성, `SPK · 13/15` 표시, `S-01`(`14.56 Hz`, `FM 4.0%`, `AudioFlinger 48000 Hz`) 출력 및 `전체 정지` 확인
  - **구버전 통합 `v5.2.1` (`versionCode=521`)**: 기존 사용자 스윕(`#취침-신체 18~25hz` 등) 100% 보존, S7 연타 `저장 완료` 인앱 배너 및 스크롤 무점프(`683→683`), `볼륨 13/15단` 표시(글씨 크기 유지)
  - **안전볼륨·접근성 보호**: `VortaAccessibilityService` + `SynergyAccessibilityService` 동시 바인딩(`accessibility_enabled=1`), `audio_safe_volume_state=2`, `stay_on_while_plugged_in=0`, `adaptive_fast_charging=0`, 크래시 `0건`
  - **S7 유선(헤드폰 잭) 실전 레드팀 검증 (`Devices: headphone`)**:
    - 하드웨어 키 조작(`12→13`), 수동 하향 존중(`10→9` 유지), 급강하 자동 복원(`13→9→13` `SAFE_MEDIA_VOLUME_INACTIVE`), 화면 꺼짐·잠금 상태에서 `ACTION_TEST_DIALOG_UNLOCK` 복원(`13`) 후 초기값 `12/15` 복귀 확인 (`KEYCODE_SLEEP` 이후 패턴 잠금 상태로 유선 UI 배지 스크린샷 검증만 사용자 잠금 해제 대기)
- **실기기(갤럭시 S6 `06157df622d5ad1d`, `SM-G920K`, Android 7.0) 라이브 검증 (`v1.1.1` + 구버전 `v5.2.1`, 유선 AUX 연결)**:
  - **보르타 시너지 `v1.1.1` (`versionCode=111`)**: `CH 2 · 저역 우퍼(10~40 Hz)` 활성, `AUX · 12/15` 배지, `S-01`(`28.00 Hz`, `AM 15%(3.2Hz)`, `AudioFlinger 48000 Hz`) 출력 및 `전체 정지` 확인
  - **구버전 통합 `v5.2.1` (`versionCode=521`)**: 기존 스윕(`수직공진 4~8Hz` 등) 100% 보존, S6 연타 `저장 완료: 수직공진 4~8Hz` 인앱 배너·무점프(`1018→1018`), `S6 · AUX`·`볼륨 12/15단` 확인
  - **Direct-Binder + 접근성 실전 레드팀 검증**: 키 조작(`12→13`), 수동 `10→9` 유지, 급강하 `13→9→13` 자동 복원, `state=3` 강제 잠금 시 Direct-Binder로 `audio_safe_volume_state=2`·`volume_controller_service_component=null`·`13/15` 즉시 해제 확인 후 `12/15` 복귀, `stay_on=0`, `adaptive_fast_charging=0`, 시계 정상(`2026-11-27`)
- **원클릭 병행 설치 스크립트**: [보르타_시너지_OS버전별_설치_원클릭.bat](../../05-scripts/one-click-bat/보르타_시너지_OS버전별_설치_원클릭.bat) (`6,361 B`, CP949 CRLF)

---

## 5. Vorta Synergy v2.0.0 (`versionCode=200`) 전면 리뉴얼 및 S6 실기기 검증

1. **6대 실사용 결함 전면 제거 + 가독성/레이아웃 재보정**:
   - **S6(`CH 2`) 단조로움 제거**: `SynergyCatalog` S-01~S-20 전 패키지의 `CH 2`를 4Hz 이중음 맥놀이와 FM/AM/RM 동적 스윕으로 재설계(`s6Flat=0`).
   - **전 영역 자유 주파수(`0.5 ~ 200.0 Hz`)**: `SynergyDspCore.clampGlobalHz()` 및 `RenderRequest.enforceChannelClamp = false` 적용 — 반수동/기초 모두 채널 고정 클램프 제거.
   - **반수동 카드 `[재생]` 무동작 버그 근본 수정**: 기존 `isAnySourceActive()`가 `MODE_IDLE`을 `false`로 판정해 즉시 `stopAllPlayback()`을 호출하던 버그를 `SynergyAudioService.toggleManualStepPlayback()`으로 수정(`MODE_IDLE → MODE_SEMI_MANUAL` 즉시 전환).
   - **다이얼로그·팝업 슬림화**: `+ 스텝 추가`/`편집`을 `단일음`/`스윕` 2종과 순수 주파수 입력으로 단순화(`스텝 단계 수` 제거), `자유 주파수 직접 입력 · 재생` 대화상자 신설, 기초 탭 `"채널 대역 보호 안내"` 모달(`showBasicGuardDialog`) 영구 삭제.
   - **기본 풀파워 출력**: `sDutyMode = 1`(연속 풀파워), `sStabilizerEnabled = false`(`-1.0 dBTP` / `0.89125` 원음 직결), 필요 시에만 선택 활성화.
   - **UI 가독성(`320dp` 정밀 보정)**: `"4기 합동"` 문구 삭제(`no4ch=true`), `COMPLEX_UNIT_DIP` 기반 균형 폰트(`제목 14~14.5dp / 본문 12.5dp / 버튼 12.5~13dp`), 긴급 버튼 2×2 배치 및 자유 주파수 `+4Hz 이중음 재생` 내부 배치로 잘림(`...`) 제거.
2. **감사·APK 빌드 (`v2.0.0`, `versionCode=200`)**:
   - **`TestVortaSynergyAudit`**: **`PASSED=11, FAILED=0`** (`Gate9_V2UnrestrictedAndFullPower` 포함: `s6Flat=0`, `free55Peak=0.8913`, `free55THD=0.000002%`, `svcDefaultPower=true`).
   - 빌드 및 서명 완료: `vorta_synergy_s6_api24.apk` (`303,999 B`), `vorta_synergy_s7_api26.apk` (`299,903 B`), `vorta_synergy_a30_api30.apk` (`299,903 B`).
3. **갤럭시 S6 (`06157df622d5ad1d`, Android 7.0) 전수 버튼 실터치 검증 (`ALL PASS`)**:
   - **자동 `S-01`**: `4기 합동` 없음 확인, 카드 터치 → `AudioFlinger 48000 Hz` 활성 트랙 + `27.14~28.42 Hz` 라이브 파형 (`s6_v200_auto.png`).
   - **반수동 IDLE → `55.0 Hz` 카드 `[재생]` & 자유 입력**: `"스텝 단계 수"` 없음 확인, `Solo55Test(55.0 Hz)` 저장(클램프 없음) 후 `IDLE` 상태에서 카드 `[재생]` → `55.00 Hz` 라이브 출력 및 `[정지]` 전환(`s6_v200_semi_solo55.png`), 자유 주파수 팝업 버튼 정상.
   - **기초 1탭 무제한 재생**: `단독 12: 55.0 Hz` 터치 시 팝업 없이 즉시 `53.82 Hz` (FM 3.2% 기준 `55±1.76 Hz`) 제한 없이 재생 (`s6_v200_basic55.png`).
   - **시스템 상태**: `REAL_CRASH_HITS=0`, `VortaAccessibilityService` + `SynergyAccessibilityService` 동시 바인딩 유지, `com.vorta.synergy=200` / `com.vorta.unified=521` 공존 확인.
4. **갤럭시 S7 (`ce021602916dbb3804`, Android 8.0, `CH 1`) 전수 버튼 실터치 검증 (`ALL PASS`)**:
   - `vorta_synergy_s7_api26.apk` (`versionCode=200`) 설치, 대기 화면 `"선택하세요"` 문구 없음 확인.
   - 자동 `S-01` → `AudioFlinger` 활성 트랙, 반수동 IDLE `Solo55Test` 카드 `[재생]` → `55.00 Hz` 라이브(`s7_v200_live_2.png`, `CH 1` 1~21Hz 대역 밖 무제한), 기초 `단독 12` 팝업 없이 즉시 `55.83 Hz`(FM 3.2%) (`s7_v200_live_3.png`).
   - 참고: Android 8에서는 파형 애니메이션 중 `uiautomator` 덤프가 막혀 재생 중 근거를 `AudioFlinger` + 스크린샷으로 대체.
   - `REAL_CRASH_HITS=0`, 접근성 2/2 바인딩, `com.vorta.unified=521` 공존.
5. **본카운터 A30 (`R59N105TEYF`, Android 11, `CH 4`) 전수 버튼 실터치 검증 (`ALL PASS`)**:
   - `vorta_synergy_a30_api30.apk` (`versionCode=200`) 설치, `CH 4 · 골전도 보조`, `SPK · 15/15`, 대기 `"선택하세요"` 및 `"4기 합동"` 0건 확인.
   - 자동 `S-01` → `51.23 Hz` (`AudioFlinger` 활성, `a30_bone_v200_live_1.png`), 반수동 자유입력 팝업 검증 + IDLE `Solo55Test` 카드 `[재생]` → `55.00 Hz` (`a30_bone_v200_live_2.png`), 기초 `단독 06: 22.0 Hz` (`CH 4` 대역 밖) 팝업 없이 `20.05 Hz` (`22.0Hz ± 9% FM`, `a30_bone_v200_live_3.png`) 즉시 재생.
   - `REAL_CRASH_HITS=0`, `com.vorta.unified=521` 공존 확인.
6. **트랜스 메인 A30 (`R59N303R80N`, Android 11, `CH 3`) 전수 버튼 실터치 검증 (`ALL PASS`)**:
   - `vorta_synergy_a30_api30.apk` (`versionCode=200`) 설치, `CH 3 · 골전도 메인`, `SPK · 14/15`, 대기 `"선택하세요"` 및 `"4기 합동"` 0건 확인.
   - 자동 `S-01` → `53.11 Hz` (`AudioFlinger` 활성, `a30_trans_v200_live_1.png`), 반수동 자유입력 팝업 검증 + IDLE `Solo55Test` 카드 `[재생]` → `52.39 Hz`(`55.0Hz ± FM`, `a30_trans_v200_live_2.png`), 기초 `단독 06: 22.0 Hz` (`CH 3` 대역 밖) 팝업 없이 `20.17 Hz` (`22.0Hz ± 9% FM`, `a30_trans_v200_live_3.png`) 즉시 재생.
   - `REAL_CRASH_HITS=0`, `com.vorta.unified=521` 공존 확인. (**4/4 전 기기 `v2.0.0` 실검증 완료**)
