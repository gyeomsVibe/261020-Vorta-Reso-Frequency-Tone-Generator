# 갤럭시 A30 × 본카운터(BoneCounter Kinter MA-170) 무작동 전수 원인분석 및 조치 보고서

- **분석 일시**: 2026-11-15 05:13 ~ 05:25 KST (MIA 전략절차 Stage 0~10 기반)
- **대상 단말**: Samsung Galaxy A30 (`SM-A305N`, ADB `R59N105TEYF`, `device:a30`, `product:a30ks`)
- **대상 앰프**: 본카운터 골전도 앰프 1기 — 제품 실물 라벨 기준 **Kinter MA-170** (2-Channel Hi-Fi Stereo Amplifier, KC 인증, 저음/고음 톤 조절, 정격 출력 RMS 최대 30W, 부하 임피던스 8Ω)

---

## 1. 최종 진단 요약 (Root Cause 4대 요인)

이번 무작동은 **단일 앱 고장이 아니라 ① A30 3.5mm 잭 감지 이탈(`h2w disconnected`) + ② 내장 스피커 우회 시 삼성 `SoundBoosterPlus` 저역 차단 + ③ Kinter MA-170 앰프의 20Hz 미만 통과대역/열보호 한계 + ④ 앱 모두 정지 상태(`Standby`)** 가 겹쳐 발생한 복합 원인입니다.

| 순위 | 구분 | 실측 근거 (로그·덤프) | 영향 |
|---|---|---|---|
| **1 (치명)** | **핸드폰/결선 문제 (`h2w` 23초 만에 인식 해제)** | `04:53:29.534` `h2w connected` → **`04:53:52.905` `h2w disconnected`** (`Codec3035` `SwitchValues: 0`, `mCurDeviceTypeSet: 0x2`) | 3.5mm AUX 단자로 **출력 0%**, 오디오가 폰 내장 스피커(`0x2`)로만 우회 |
| **2 (치명)** | **OS DSP 필터 (`SoundBoosterPlus` `-91 dBFS` 컷)** | `05:00:49~05:01:08` `com.smato.a30frequency` (`17.3Hz`, `9.2Hz`) 재생 중 `AudioOut_D Signal power history` **`-88.8 ~ -94.8 dBFS`** | 내장 스피커 보호용 삼성 `libsamsungSoundbooster_plus.so`가 초저주파를 사실상 무음화 |
| **3 (핵심)** | **본카운터 앰프(Kinter MA-170) 주파수·열 한계** | 제품 스펙: `8Ω / 최대 30W RMS`, Class-AB 20Hz~20kHz 대역 (입력 커플링 커패시터 + 150°C 열 차단 IC) | 스마토 120W용 `<20Hz`(`9.2Hz`, `17.3Hz`) 입력 시 감쇠 및 발열만 발생, 진동 무출력 |
| **4 (상태)** | **앱 정지 상태 + 앱 내 부가 결함 3건** | `05:01:08.136` 이후 4개 앱 서비스 모두 미실행 (`(nothing)`), `volume_music_headset=8`, `A30`/`S7` `auditBuffer` 페이드인 첫 80ms만 기록 | A30은 `8/15` 볼륨·로그 왜곡 상태였고 현재 앱 미가동 |

---

## 2. 갤럭시 A30 실시간 정밀 타임라인 (`dumpsys audio`, `AudioFlinger`, `logcat`)

1. **전날 밤 정상 가동 (`11-14 23:37:39 ~ 11-15 04:49:48`)**
   - `23:37:39.920`: `setWiredDeviceConnectionState(type:8 state:DEVICE_STATE_AVAILABLE name:h2w)` — 3.5mm 유선 헤드폰(`0x8`) 정상 인식.
   - `23:38:48.610 ~ 04:49:48.523`: `com.vorta.s6frequency` (`piid:71`)가 `53.15 ~ 54.88 Hz` 대역을 `Peak=29200/32767`, `RMS=20571~20979 (-3.87 ~ -4.04 dBFS)`로 5시간 11분간 중단 없이 송출.
2. **케이블 교체 및 23.4초 만의 인식 끊김 (`04:53:14 ~ 04:53:53`)**
   - `04:53:14.552`: `WiredAccessoryManager: newName=h2w newState=0 headsetState=0 prev headsetState=2` (기존 선 분리).
   - `04:53:29.534`: `WiredAccessoryManager: bits=SW_HEADPHONE_INSERT mask=4`, `newName=h2w newState=2` → `AS.AudioDeviceInventory: handleDeviceConnection(true dev:8 address: name:h2w)` (본카운터 연결 인식).
   - **`04:53:52.905`**: 연결 **23.4초 후** `WiredAccessoryManager: newName=h2w newState=0` / `headsetName: h2w disconnected` → `04:53:52.912 ACTION_AUDIO_BECOMING_NOISY` → `04:53:53.820 AudioFlinger: setCurDevice() 0x2`.
   - 이후 `05:13 ~ 05:25` 전수 점검 시점까지 `dumpsys input`의 `Codec3035 Headset Events`가 **`SwitchValues: 0`** 이며 `Connected devices`는 **`[DeviceInfo: type:0x2 (speaker)]`** 만 존재.
   - **원인 해석**: A30의 `Codec3035` 잭 감지는 3.5mm L/R/GND/MIC 핀 임피던스로 삽입 여부를 판정합니다. ① 3.5mm 플러그가 폰 케이스 간섭 등으로 1mm가량 덜 꽂혀 접촉이 빠졌거나(`04:54:00` 기기 이동/회전 직전 이탈), ② Kinter MA-170 앰프 전원이 꺼져 있거나 입력단 직렬 AC 커플링 커패시터(47kΩ 입력)가 개방 상태로 감지되면 `h2w`가 끊깁니다.
3. **내장 스피커 우회 상태에서의 재생 시도 (`05:00:49 ~ 05:01:08`)**
   - 사용자가 `com.smato.a30frequency`로 `17.30Hz`(`piid:87`, `piid:95`) 및 `9.20Hz`(`piid:103`)를 짧은 간격으로 시험 가동했으나, `APM_AudioPolicyManager`가 모두 `{type:0x2,@:}`(내장 스피커)로 라우팅.
   - `dumpsys media.audio_flinger`의 `AudioOut_D` `EffectStages` 중 `primary_output_normal (-1303)`에 **`SoundBoosterPlus` (Samsung `/vendor/lib/soundfx/libsamsungSoundbooster_plus.so`)** 가 걸려 있어, `9.2Hz / 17.3Hz` 재생 시 실제 출력 신호 파워가 **`-88.8 ~ -94.8 dBFS`** 로 거의 완전 차단됨(이전 53~55Hz 계열 또는 상위 배음이 있던 `05:00:42`는 `-51.7 ~ -60.1 dBFS` 출력).
   - `05:01:08.136` 이후 `piid:103` 정지 후 현재까지 4개 앱 모두 `Standby: yes` 상태.

---

## 3. 본카운터(Kinter MA-170) 하드웨어 적합성 분석 (Stage 0 Web Deep Research 결과)

- 사진 및 제품 스펙 상 **Kinter MA-170**은 소형 2채널 Class-AB 하이파이 앰프(대표적으로 `TDA7297`/`TDA7377` 또는 호환 BTL/스테레오 앰프 IC 기반, 4~16Ω / 8Ω 정격, 20Hz~20kHz, 권장 DC 12V 2A~3A 어댑터)로 분류됩니다.
- **스마토 120W 서브우퍼 앰프 대비 본카운터 MA-170의 한계**:
  1. **주파수 하한 (20Hz 미만 차단)**: 입력단 DC 차단용 소용량 커플링 커패시터로 인해 20Hz 이하(`9.2Hz`, `17.3Hz`) 초저주파가 크게 감쇠됩니다. `smato_a30_app`의 프리셋(3.3~19.8Hz)을 넣으면 진동자 변위는 거의 없고 발열만 커집니다.
  2. **열 축적 및 과열 보호 차단(Thermal Shutdown)**: 사용자가 체감한 것처럼 MA-170은 방열판 크기가 작은 Class-AB 칩셋(~50~60% 효율)이어서 8Ω 골전도 진동자에 연속 정현파를 고출력 인가하면 칩셋 내부 접합 온도 ~150°C 도달 시 스스로 출력을 차단(또는 출력이 급락)합니다.
  3. **권장 운용 대역**: 본카운터 MA-170 연결 시에는 **`35 ~ 55Hz`** 대역(`보르타 레조 S6` 앱 또는 `주파수 발생기 Pro`의 `38~55Hz`, `53~55Hz`, `27~36Hz`)을 사용해야 앰프 통과대역 안에 들어와 진동이 전달됩니다.

---

## 4. 보르타(Vorta) 앱 4종 소스·상태 전수 점검 및 조치 내역

4개 앱 모두 오디오 합성 엔진(48,000Hz Stereo PCM16, True-Peak `-1.0 dBTP` 리미터 `29200/32767`) 자체의 무음 결함은 없었으나, 아래의 운영/코드 결함을 확인하고 패치했습니다.

| 패키지 / 앱 | 소스 점검 결과 | 발견된 이상 | 조치 결과 |
|---|---|---|---|
| `com.smato.a30frequency` (`smato_a30_app`, v4.2) | 48kHz PCM16 엔진 정상, A30 전용 볼륨 가드는 `if (isA30Device()) return;`로 비활성 설계됨 | ① `auditBuffer()`가 페이드인 첫 80ms(`mGain≈0.08`, `Peak=1997`, `RMS=-30.45dBFS`)만 로깅 후 미갱신 ② `ACTION_HEADSET_PLUG` 수신부 3회 중복 | `A30AudioGeneratorService.java` 중복 제거 및 페이드인 완료(`mGain >= 0.999`) 후 10초 주기 감사 추가, 빌드·재배포 완료 |
| `com.vorta.s7frequency` (`vorta_s7_app`, v4.2) | 48kHz PCM16 엔진 정상 | ① `auditBuffer()` 첫 80ms 청크만 로깅 후 미갱신 ② `ACTION_HEADSET_PLUG` 수신부 3회 중복 | `S7AudioGeneratorService.java` 중복 제거 및 `sGain >= 0.999` 10초 주기 감사 추가, 빌드·재배포 완료 |
| `com.vorta.s6frequency` (`vorta_s6_app`, v4.2) | 전날 밤 `53.15~54.88Hz` 정상 구동 확인됨 | ① `ACTION_HEADSET_PLUG` 3회 중복 ② 첫 80ms 감사 후 다음 감사까지 10초 대기 | `S6AudioGeneratorService.java` 중복 제거 및 `mGain >= 0.999` 즉시 후속 감사되도록 수정, 빌드·재배포 완료 |
| `com.vorta.freqgen` (`vorta_freqgen_app`, v3.1.0) | 48kHz 멀티스윕 엔진 정상 | 결함 없음 (단, 앱 실행 시 `SweepItem.isPlaying=false` 대기 상태이므로 프리셋/발진 버튼 터치 필요) | 유지 (정상) |

### A30 기기 볼륨·세이프볼륨 교정 (`adb -s R59N105TEYF`)
- 기존 상태: `volume_music_headset=8` (53%로 저하), `volume_music_usb_headset=7`, `audio_safe_volume_state=1`
- 조치 후 (`settings list system/global` 검증): `volume_music=13`, `volume_music_headphone=13`, `volume_music_headset=13`, `volume_music_usb_headset=13`, `audio_safe_volume_state=0` 적용 완료 (`0x8 headphone`은 실시간 AudioService RAM도 `13\130` 확인; `0x4 headset` RAM 값은 재부팅 시 반영).

---

## 5. 즉시 실행 및 물리 점검 체크리스트 (사용자 조치 항목)

1. **본카운터 앰프 전원·볼륨 먼저 ON → A30 3.5mm 단자 끝까지 재결착**:
   - 12V 어댑터 및 앰프 전원 스위치/LED가 켜진 상태에서, A30 케이스를 잠시 벗기거나 걸림 없이 **3.5mm AUX 플러그를 끝까지('딸깍' 감각) 재결착**하여 A30 상단바에 **유선 이어폰(헤드셋) 아이콘**이 뜨고 유지되는지 확인하십시오.
   - 만약 3.5mm 선을 손으로 살짝 흔들 때 상단 이어폰 아이콘이 깜빡이거나 사라진다면 **3.5mm↔RCA 변환 케이블 단선/접촉불량** 또는 **A30 3.5mm 잭 내부 접점 불량**입니다.
2. **주파수 및 앰프 발열 세팅**:
   - 본카운터 앰프(`Kinter MA-170`, 20Hz~20kHz)에서는 `smato_a30_app`의 `<20Hz`(`9.2Hz`, `17.3Hz`) 대신 **`보르타 레조 S6`** 또는 **`주파수 발생기 Pro`의 `38~55Hz` / `53~55Hz` 프리셋**으로 테스트하십시오.
   - Kinter MA-170의 **BASS 노브는 12시~2시, MASTER VOLUME은 60~75%** 위치에서 시작해 열 차단(150°C 보호)이 걸리지 않도록 연속 과부하를 피하십시오.
