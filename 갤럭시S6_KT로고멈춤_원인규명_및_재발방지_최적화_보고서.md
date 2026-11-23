# 🛡️ 갤럭시 S6 (SM-G920K, Android 7.0 API 24) KT 로고 멈춤 원인 규명, 4중 안전장치 및 보르타 5종 전 제품군 맞춤 최적화 보고서

> **수행 프레임워크**: MIA 전략절차 11단계 표준 프레임워크 (Stage 0~10 완결)  
> **대상 기기**: 삼성 갤럭시 S6 (`SM-G920K`, 시리얼 `06157df622d5ad1d`, Android `7.0` Nougat / SDK `API 24`, 빌드 `NRD90M.G920KKKU3EVG1`, `arm64-v8a`, Exynos 7420)  
> **작성일**: 2026-11-16

---

## 1. 장애 현상 및 근본 원인 규명 (실측 로그 기반)

### 1-1. 직접 원인: 장기 무재부팅 누적 커널 DRAM 페이지 캐시 손상
- 연결 당시 기기는 하드웨어 벽돌(Hard Brick)이 아니었으며, 리눅스 커널이 **재부팅 없이 `4일 23시간 53분(431,611초)` 연속 가동**되고 있었습니다 (`MemTotal: 2,740,412 kB`, `Cached: 1,402,640 kB`, `Active(file): 898,804 kB`, 이력 최고 배터리 온도 `55.7°C` [`mSavedBatteryMaxTemp: 557`]).
- 이 과정에서 읽기 전용 `/system` 파티션(`/dev/block/sda15`)의 파일을 메모리에 올려두는 **커널 DRAM 페이지 캐시** 일부 페이지가 손상되며 두 가지 연쇄 치명 오류를 유발했습니다.
  1. **`mediaserver` / `cameraserver` 5초 주기 연속 강제 종료 (`SIGABRT`)**:
     `/system/lib/libLLVM.so` 링크 과정에서 `.dynstr` 오프셋 손상 오류(`strtab out of bounds error; STRSZ=923581, name=-1463457798`)로 5초마다 사망.
  2. **`system_server` 약 110초 주기 `SIGILL` 소프트 부트루프**:
     `zygote64`가 `Incomplete boot detected`를 기록하고 시스템 APK 17개를 `dex2oat`로 재컴파일한 뒤, `StartUserManagerService` 단계에서 `/system/framework/arm64/boot-framework.oat` (`offset 0x020edd08`, `ApplicationPackageManager.getApplicationInfoAsUser+148`) 실행 중 `Fatal signal 4 (SIGILL), code 1 (ILL_ILLOPC)`로 즉시 사망.
- 디스크 원본 MD5 검증 결과 `/system/framework/arm64/boot-framework.oat` (`54,856,488B`, `daafcdbf95681fa907820c678d3a70ef`)와 `/system/lib/libLLVM.so` (`12,004,980B`, `018554156726cc275f522596f28ce8dc`)는 정상 무결 상태로 확인되어, **플래시 손상이 아닌 휘발성 RAM 페이지 캐시 손상**임이 확정되었습니다.

### 1-2. 구조적 고착 원인: Android 7.0 `RescueParty` 부재로 인한 소프트 부트루프
- Android 8.0 이상에는 `system_server`가 반복 사망하면 자동으로 커널 레벨 재부팅·복구를 수행하는 `RescueParty`가 존재하나, **Android 7.0(API 24)에는 해당 메커니즘이 없습니다.**
- 따라서 `system_server`가 죽어도 커널은 재부팅하지 않고 `zygote64`만 소프트 재시작하며 통신사 부팅 애니메이션(`init.svc.bootanim=running`, KT 로고)을 유지하므로, 손상된 DRAM 페이지 캐시가 RAM에서 영구히 비워지지 않아 바탕화면(`LauncherActivity`) 진입이 무한 차단되었습니다.

### 1-3. 시스템 과부하 및 3대 근본 유발 요인
1. **타 기종 보르타 앱 중복 설치 및 동시 기동 경합**:
   기존 일괄 배치 파일이 S6에도 S7/A30/FreqGen 앱을 모두 설치했고, 각 앱의 `BootReceiver`가 `SM-G920K`를 전용 기기로 인식해 `com.vorta.s6frequency`(PID 5090), `com.smato.a30frequency`(PID 7432), `com.vorta.s7frequency`(PID 7446)가 동시에 기동·경합함.
2. **4초 주기 접근성 감시 루프 및 실시간 오디오 스레드 내 Binder IPC 과부하**:
   `S6VolumeHelper.enforceSystemFlags()`가 `S6AccessibilityService`(`accessibility_enabled=1`)를 강제 활성화하여 `4,000ms`마다 `SettingsProvider`/`AudioService`에 Binder IPC를 반복 호출(5일간 10만 회 이상 루프)했고, `S6AudioGeneratorService`의 `THREAD_PRIORITY_URGENT_AUDIO` 스레드 내부에서도 10초마다 `periodicGuardTick()`을 호출해 `system_server`와 불필요한 경합을 유발함.
3. **비필수 배경 미디어 인덱서·푸시 데몬 상주로 인한 메모리 압박 (`MemFree: 94,268 kB`)**:
   `com.samsung.faceservice`, `com.samsung.cmh:CMH`, `com.samsung.storyservice`, `com.sec.spp.push`, `com.ktpns.pa`, `com.sec.android.app.sns3`, `com.samsung.android.app.simplesharing`, `com.sec.android.diagmonagent` 8종이 110MB 이상의 RAM과 `libLLVM.so` 미디어 스캔·Swap(`vnswap0` 297MB 사용) 압박을 유발함.

---

## 2. 재발방지 4중 안전장치 및 Android 7.0(API 24) 맞춤 최적화

### 2-1. [제1중] 커널 콜드 재부팅 자동 감지 및 72시간 예방 복구 스크립트
- 신설: [갤럭시S6_정상화_및_안전장치_원클릭.bat](file:///d:/D_Workspace_NB/주파수/갤럭시S6_정상화_및_안전장치_원클릭.bat)
- `sys.boot_completed != 1`, `init.svc.bootanim == running`, `service check activity` 미응답, `logcat -d -b crash` 내 `SIGILL`/`libLLVM` 검출, 또는 **연속 가동 72시간(`259,200초`) 초과** 시 자동으로 커널 콜드 재부팅을 수행해 DRAM 페이지 캐시를 안전하게 초기화합니다.

### 2-2. [제2중] 보르타 시리즈 5종 전 제품군 Android 7.0(API 24) 맞춤 개조 및 부팅 격리
갤럭시 S6(`Android 7.0`, `API 24`, `arm64-v8a`) 환경에 맞춰 보르타 5종 앱을 전수 최적화하여 모두 공존 설치 가능하도록 개조·빌드했습니다.

| 앱 이름 (패키지명) | Android 7.0(API 24) 맞춤 최적화 및 개조 내역 | S6 AOT 컴파일 상태 |
|---|---|---|
| **보르타 레조 S6 v4.2**<br>(`com.vorta.s6frequency`) | `S6AccessibilityService` 4초 워치독 제거, `S6VolumeHelper` 접근성 강제 활성화 제거 및 5초 쓰로틀(`sLastFlagsCheckMs`), `S6AudioGeneratorService` 오디오 스레드 Binder 폴링 제거·무음 알림(`.setOnlyAlertOnce(true).setDefaults(0)`) | `speed` (`kOatUpToDate`) |
| **보르타 레조 S7 v4.2**<br>(`com.vorta.s7frequency`) | `S7BootAndVolumeReceiver`를 `isTargetDevice()`(S7 전용)로 격리해 S6 부팅 시 자동 실행 차단, 4초 워치독 및 오디오 스레드 폴링 제거 | `speed` (`kOatUpToDate`) |
| **보르타 레조 A30 v4.2**<br>(`com.smato.a30frequency`) | `A30BootAndVolumeReceiver`를 `isA30Device()`(A30 전용)로 격리해 S6 부팅 경합 차단, 오디오 스레드 `periodicGuardTick` 제거 | `speed` (`kOatUpToDate`) |
| **보르타 주파수 발생기 Pro v3.1.0**<br>(`com.vorta.freqgen`) | `BootReceiver`에서 전용 기기(`G920`/`G930`/`A30`) 부팅 개입 차단, 오디오 스레드 `periodicGuardTick` 제거 | `speed` (`kOatUpToDate`) |
| **보르타 이지스 Pro v1.0.0**<br>(`com.vorta.aegis`) | `minSdkVersion` `26 → 21` 및 D8 `--min-api 21` 하향 지원으로 Android 7.0 호환 확보, S6 Exynos 7420 `960`-프레임 HAL 배수(`1920` 프레임 버퍼) 최적화, `ACTION_EXCLUSIVE_AUDIO_START` 상호 오디오 양보 리시버 탑재 | `speed` (`kOatUpToDate`) |

### 2-3. [제3중] 0-폴링·무간섭 OS 및 배터리·발열 안전 최적화
- `accessibility_enabled=0`, `enabled_accessibility_services=""` 고정으로 불필요한 접근성 Binder 부하 완전 차단.
- **배터리 스웰링·열화 방지**: `protect_battery=1` (85% 충전 제한), `adaptive_fast_charging=0` (고속충전 발열 차단), `stay_on_while_plugged_in=0` (화면 상시 점등 방지).
- **오디오 안전장치**: `audio_safe_volume_state=2`, `unsafe_volume_music_active_ms=0`, 시스템/알림/벨소리 무음화 (`volume_ring=0`, `volume_notification=0`, `volume_system=0`), 순수 사인파 `-1.0 dBTP`(`Peak=29200/32767`) 유지.

### 2-4. [제4중] 비필수 배경 데몬 8종 동결 및 Android 7.0 `speed` 네이티브 AOT 최적화
- 전화·런처·SystemUI·설정·Wi-Fi·블루투스·오디오 등 본래 스마트폰 기능은 100% 그대로 보존하고, `libLLVM` 미디어 스캔 및 RAM 낭비를 유발하던 비필수 배경 데몬 8종(`com.samsung.faceservice`, `com.samsung.cmh`, `com.samsung.storyservice`, `com.sec.spp.push`, `com.ktpns.pa`, `com.sec.android.app.sns3`, `com.samsung.android.app.simplesharing`, `com.sec.android.diagmonagent`)만 `pm disable-user --user 0`로 안전 동결했습니다.
- Android 7.0 설치 기본값(`interpret-only`) 대신 5종 앱 모두 `cmd package compile -m speed -f`를 적용하여 64비트(`arm64-v8a`) 네이티브 기계어 실행 성능을 확보했습니다.

---

## 3. 실측 검증 결과 (본래 기능·성능·안정성 입증)

| 검증 항목 | 개조 전 (장애/초기 상태) | 개조·최적화 후 (최종 실측) | 판정 |
|---|---|---|---|
| **부팅·UI 상태** | KT 로고 무한 멈춤 (`bootanim=running`) | `sys.boot_completed=1`, `bootanim=stopped`, 5대 서비스 정상 | **PASS** |
| **시스템 크래시** | `SIGILL` / `libLLVM` `SIGABRT` 무한 루프 | `logcat -d -b crash` **`0`건** | **PASS** |
| **보르타 5종 설치·호환** | `vorta_aegis` 설치 불가 (`minSdk=26`), 부팅 4중 경합 | **5종 전원 `minSdk=21` + `compilation_filter=speed` (`kOatUpToDate`)** | **PASS** |
| **상호 오디오 양보·음향** | 스레드 내 Binder 주기 폴링 존재 | `AegisAudioService` ↔ `S6AudioGeneratorService` 자동 양보, `48,000Hz` `Peak=29200/32767` (`0 underrun`) | **PASS** |
| **가용 여유 RAM (`MemFree`)** | `94,268 kB` (`94 MB`) | **`358,612 kB` (`358 MB`, 약 3.8배 확보)** | **PASS** |
| **배치 자동화 도구** | 기종 미격리·배치 구문 오류 가능 | `갤럭시S6_정상화_및_안전장치_원클릭.bat` 종료 코드 **`0`** | **PASS** |

---

## 4. 원클릭 운용 및 향후 관리 가이드
- **S6 단일 점검/복구/5종 최신화**: [갤럭시S6_정상화_및_안전장치_원클릭.bat](file:///d:/D_Workspace_NB/주파수/갤럭시S6_정상화_및_안전장치_원클릭.bat) 실행 (72시간 이상 장기 가동 시 자동으로 안전 콜드 재부팅 수행)
- **A30/S7/S6 다중 기기 통합 설치**: [A30_S7_S6_보르타_레조_환경설정_원클릭.bat](file:///d:/D_Workspace_NB/주파수/A30_S7_S6_보르타_레조_환경설정_원클릭.bat) 또는 [A30_S7_S6_주파수발생기_전수개조_원클릭.bat](file:///d:/D_Workspace_NB/주파수/A30_S7_S6_주파수발생기_전수개조_원클릭.bat) 실행
- **원본 백업 보관 위치**: `d:\D_Workspace_NB\주파수\.work\backup_20261116_s6_safeguard\`
