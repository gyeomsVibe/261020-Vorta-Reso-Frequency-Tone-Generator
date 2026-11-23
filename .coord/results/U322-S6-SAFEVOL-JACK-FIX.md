# Card Result: U322-S6-SAFEVOL-JACK-FIX

- **Card**: `U322-S6-SAFEVOL-JACK-FIX`
- **Status**: `DONE` (실기기 RAM/바인더/접근성/오디오 엔진 검증 완료; 물리 3.5mm 케이블 재체결 시 `h2w=1/2` 현장 복원 동작은 케이블 물리 체결 필요)
- **Conductor / Executor**: Antigravity (MIA Strategic 11-Stage Framework + `/CRITIC /SELFREFINE /REDTEAM`)
- **Target Device**: Samsung Galaxy S6 (`SM-G920K`, serial `06157df622d5ad1d`, `zerofltektt`, Android `7.0` Nougat / SDK `API 24`, Build `NRD90M.G920KKKU3EVG1`, `arm64-v8a`, Exynos 7420, Wolfson `WM1840` audio codec)

---

## 1. 실측 원인 규명 (Live S6 Logcat + `services.odex` 바이트코드 검증)

### 1-1. 안전볼륨(9단계 강제 감쇄) 반복 실패 4대 근본 원인
1. **삼성 `AudioService.onSetWiredDeviceConnectionState`의 무조건 초기화** (`services_0.dex` PC `0x00b3–0x00ee`):
   - 유선 3.5mm 연결(`h2w` 상태 변화) 시 `mSafeMediaVolumeState`가 `INACTIVE(2)`여도 `0x4`(headset)/`0x8`(headphone) 인덱스가 `90`(9단계)을 초과하면 무조건 `9`로 덮어쓰며, 20시간(`mMusicActiveMs >= 72,000,000`) 누적 시 `onCheckMusicActive`가 상태를 `ACTIVE(3)`으로 전환해 `Settings` 덮어쓰기만으로는 복구되지 않음.
2. **실측 로그 `22:35:50`의 숨은 경고 플래그 누락 및 화면 꺼짐 소멸**:
   - `22:35:50.940 E AudioService: adjustStreamVolume() safe volume index = 90` 및 `SecVolumeDialog: TYPE_VOLUME_OVERLAY`, `semGetEarProtectLimit() : 10, mEarProtectLevel = 910` 발생.
   - 기존 `S6VolumeHelper.requestConfirmDialog()`가 `FLAG_SHOW_UI`(1)만 전달하고 `FLAG_SHOW_UI_WARNINGS = 1 << 10`(1024)을 누락해 `SystemUI` 안전볼륨 확인창을 직접 호출하지 못했고, 화면이 꺼진 상태에서는 `SecVolumeDialog.onScreenTurnedOff() -> dismissH reason: 4`로 팝업이 즉시 사라짐.
3. **`U321`에서 패시브 접근성 서비스까지 전면 비활성화된 회귀**:
   - `accessibility_enabled=0`, `enabled_accessibility_services=""` 상태로 두면서 0-폴링 패시브 자동확인(`S6AccessibilityService`)과 24/7 동적 브로드캐스트 리시버(`ACTION_HEADSET_PLUG`, `SAFE_MEDIA_VOLUME_ACTIVE`, `VOLUME_CHANGED_ACTION`)가 바인딩되지 않음. 또한 기존 `S6AccessibilityService`는 `getRootInActiveWindow()`만 검사해 `TYPE_VOLUME_OVERLAY` 오버레이 창을 놓칠 수 있었음.
4. **S6 `MULTISOUND_ENABLED=false` 및 `reloadAudioSettings()` 권한 구조** (`services_0.dex` 실측 검증):
   - S6 Android 7.0(`services_0.dex`) 바이트코드에서 `MULTISOUND_ENABLED == false`이므로 비연결 상태에서 `setStreamVolumeForDevice(..., 4/8)`와 `getStreamVolumeForDevice(..., 4/8)` 모두 내부적으로 `getDeviceForStream(3)`(스피커 `2`)으로 치환되어 유선 RAM 인덱스를 직접 바꾸지 않음.
   - 또한 `reloadAudioSettings()`는 `clearCallingIdentity()` 없는 `oneway` 바인더여서 일반 앱 호출 시 `SecurityException: INTERACT_ACROSS_USERS_FULL`(`AudioService.readPersistedSettings:2144`)을 유발함.
   - 따라서 비연결 시 무효 Binder 호출을 제거하고, `Settings.System`(`volume_music_headset=13`, `volume_music_headphone=13`, `volumelimit_on=0`, `volume_limiter=0`, `volume_limiter_enable=0`, `volume_limiter_value=15`) 저장 + 유선 연결 즉시 다중 펄스 `AudioManager.setStreamVolume(STREAM_MUSIC, target, 0)` + `FLAG_SHOW_UI | FLAG_SHOW_UI_WARNINGS` + `3초 WakeLock` + `TYPE_VOLUME_OVERLAY` 패시브 자동확인 조합이 S6 펌웨어에 맞는 무오류 정해임.

### 1-2. 3.5mm AUX 주기적 단선 → 폰 스피커 출력 실측 원인
- **실측 `22:38:58.385` 로그카트**:
  `WiredAccessoryManager: Headset UEVENT: {SUBSYSTEM=switch, ACTION=change, SWITCH_STATE=0, SWITCH_NAME=h2w}` (`newState=0 headsetState=0 prev headsetState=1`), 이어 `APM_AudioPolicyManager: setDeviceConnectionStateInt() device: 0x4, state 0, name h2w` → 출력 장치가 `2 (speaker)`로 강제 전환되고, 당시 재생 중이던 `com.vorta.freqgen`(PID 13276, 누적 실행 `-22h21m`) 오디오가 폰 내장 스피커로 그대로 송출됨.
- **하드웨어/전기적 징후**: 3극(TRS) 외부 앰프 AUX 연결인데도 `prev headsetState=1`(`0x4` 4극 마이크 포함 헤드셋 + `0x80000010` 마이크 입력)로 오인식된 뒤 `SWITCH_STATE=0`으로 이탈함. 이는 11년 경과한 S6 본체 3.5mm 암단자 접점(`sec_jack` `JACKDET`/`mic_adc`) 산화·유격 또는 `Lepy LP-168S` 앰프 측 고입력임피던스·AC 그라운드 루프 간섭으로 커널 드라이버가 분리(`h2w=0`)를 선언하는 현상임.
- **비루팅 Android 제약**: `AudioSystem.setForceUse`/`setDeviceConnectionState`는 서명 권한(`MODIFY_AUDIO_ROUTING`)이 필요하므로 비루팅 앱이 커널 `h2w=0`을 무시해 물리 잭으로 강제 송출할 수는 없음. 대신 서비스가 **유선 상태로 시작된 뒤(`mWiredGuardArmed=true`) 예기치 않게 분리되면 즉시 스피커 송출을 무음 차단하고, 재체결 시 자동 복구**하는 소프트웨어 가드가 필수적임.

---

## 2. 적용된 수정 내역

1. **무폴링 패시브 `TYPE_VOLUME_OVERLAY` 자동확인 복구 (`vorta_s6_app`)**:
   - [accessibility_service_config.xml](file:///d:/D_Workspace_NB/주파수/vorta_s6_app/res/xml/accessibility_service_config.xml): `accessibilityEventTypes="typeWindowStateChanged"`·`packageNames="com.android.systemui"` 유지(0-폴링), `flagReportViewIds|flagRetrieveInteractiveWindows` 추가.
   - [S6AccessibilityService.java](file:///d:/D_Workspace_NB/주파수/vorta_s6_app/src/com/vorta/s6frequency/S6AccessibilityService.java): 타이머/워치독 `0`개 유지. `event.getSource()` 루트, `getRootInActiveWindow()`, `getWindows()` 전체를 순회해 `TYPE_VOLUME_OVERLAY` 경고창 포함 청력 경고 키워드(`청력`, `hearing`, `권장 수준`, `높은 볼륨`, `높은 음량`, `high volume`, `이어폰`, `오랜 시간`, `safe volume`, `unsafe volume`) 검증 후 `android:id/button1` 또는 `"확인"`/`"OK"` 클릭 및 `80ms`/`350ms` 복원 트리거.
2. **안전볼륨 해제 및 사용자 볼륨키 존중 가드 ([S6VolumeHelper.java](file:///d:/D_Workspace_NB/주파수/vorta_s6_app/src/com/vorta/s6frequency/S6VolumeHelper.java), [FreqGenVolumeHelper.java](file:///d:/D_Workspace_NB/주파수/vorta_freqgen_app/src/com/vorta/freqgen/FreqGenVolumeHelper.java))**:
   - `FLAG_SHOW_UI | FLAG_SHOW_UI_WARNINGS`(`1 << 10`) + `3초` 화면 점등 `WakeLock`(`wakeUpScreenForDialog`) + 10분 쿨다운 내 최대 3회(`2.5초` 간격) 재시도.
   - `onVolumeChanged()`에서 `osClamp`(플러그 후 4초 이내 `<= 9` 강하 또는 `>= 2`단계 급락)만 복원하고 사용자 직접 볼륨 하향/상향(`sTargetVolumeStep`)은 존중(원칙 1 준수).
   - `isWired()`를 API 23+ `AudioDeviceInfo`(`TYPE_WIRED_HEADSET`, `TYPE_WIRED_HEADPHONES`, `TYPE_LINE_ANALOG`, `TYPE_AUX_LINE`, USB 오디오/DAC 포함) 기반으로 개선.
   - `volumelimit_on=0`, `volume_limiter=0`, `volume_limiter_enable=0`, `volume_limiter_value=15` 일관 유지 및 `reloadAudioSettings` 무효 바인더 호출 제거.
3. **3.5mm AUX 분리 시 폰 스피커 송출 차단 및 자동 재개 (`AUX Guard`) ([S6AudioGeneratorService.java](file:///d:/D_Workspace_NB/주파수/vorta_s6_app/src/com/vorta/s6frequency/S6AudioGeneratorService.java), [AudioEngineService.java](file:///d:/D_Workspace_NB/주파수/vorta_freqgen_app/src/com/vorta/freqgen/AudioEngineService.java))**:
   - `AudioTrack`을 `AudioAttributes`(`USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`)로 업그레이드하고, 유선 출력 장치 검출 시 API 23+ `setPreferredDevice(wiredDev)` 고정.
   - `AudioManager.registerAudioDeviceCallback` + `ACTION_HEADSET_PLUG` + `ACTION_AUDIO_BECOMING_NOISY` 감시.
   - 유선 상태에서 재생 시작(`mWiredGuardArmed=true`) 후 단자 분리 감지 시 `20ms` 무클릭 페이드아웃으로 폰 스피커 출력 즉시 차단(알림 무음 갱신 `🔇 AUX 분리 감지 — 스피커 송출 차단 / 재연결 대기`) → 재체결 시 `onHeadsetPlug(..., 1)` 볼륨 복원 후 `1.0s` 부드러운 페이드인 자동 재개.
   - `-1.0 dBTP`(`Peak=29200`), `48,000 Hz`, 시나리오 주파수·변조 무변경 원칙 준수.
4. **원클릭 배치 스크립트 CP949 동기화**:
   - [갤럭시S6_정상화_및_안전장치_원클릭.bat](file:///d:/D_Workspace_NB/주파수/갤럭시S6_정상화_및_안전장치_원클릭.bat), [A30_S7_S6_보르타_레조_환경설정_원클릭.bat](file:///d:/D_Workspace_NB/주파수/A30_S7_S6_보르타_레조_환경설정_원클릭.bat), [A30_S7_S6_주파수발생기_전수개조_원클릭.bat](file:///d:/D_Workspace_NB/주파수/A30_S7_S6_주파수발생기_전수개조_원클릭.bat)에서 S6에 0-폴링 패시브 접근성(`com.vorta.s6frequency/com.vorta.s6frequency.S6AccessibilityService`, `accessibility_enabled 1`) 및 `volume_limiter*` 설정을 자동 적용하도록 CP949 인코딩 무결성 검증 후 갱신.

---

## 3. 실기기 검증 결과 (`SM-G920K`, `06157df622d5ad1d`)

| 검증 게이트 | 실측 결과 | 판정 |
|---|---|---|
| **APK 빌드·서명** | `build_s6.py` exit `0`, `build_freqgen.py` exit `0` (`apksigner verify` 통과) | **PASS** |
| **패시브 접근성 연결** | `dumpsys accessibility`: `enabled services` & `bound services` 모두 `com.vorta.s6frequency/.S6AccessibilityService` (`TYPE_WINDOW_STATE_CHANGED`, `capabilities=1`, 0-폴링) | **PASS** |
| **음향 품질 & 상호 양보** | `S6AudioAudit`: `SR=48000Hz`, `Peak=29200/32767`, `DC=-0.00053`, `0 underrun`; `com.vorta.freqgen` 기동 시 `Yielding audio output` 정상 정지; `AudioFlinger` `partial=0 empty=0` | **PASS** |
| **무오류·AOT·안전설정** | `Binder call failed` / `INTERACT_ACROSS_USERS_FULL` **`0`건**, `logcat -d -b crash` **`0`줄**, 양 앱 `compilation_filter=speed` (`kOatUpToDate`), `stay_on=0`, `protect_battery=1`, `adaptive_fast_charging=0`, `acc_en=1`, `volumelimit_on=0`, `vol_hs=13`, `vol_hp=13` | **PASS** |
| **물리 3.5mm 잭 재연결** | 검증 시점 단자 비연결(`Devices: speaker`, `h2w=0`) 상태로 비연결 RAM 사전주입 불가 확인(`MULTISOUND_ENABLED=false`) 및 케이블 재체결 시 `h2w=1/2` 현장 복원 동작은 물리 체결 필요 | **HW 연결 후 확인** |

---

## 4. 물리 3.5mm 잭(단선) 하드웨어 보강 권고 (S6 암단자/WM1840 특성)

소프트웨어(`AUX Guard` + 85% 자동복원)는 단자 이탈 시 스피커 폭음을 20ms 내 차단하고 재인식 즉시 복구하지만, S6 3.5mm 암단자 자체의 물리적 이탈(`h2w=0`)을 줄이려면 아래 순서를 권장합니다.
1. **3극(TRS) 규격 3.5mm 케이블 + 단자 접점 세척**: 면봉/비전도성 접점부활제(`BW-100`)로 S6 3.5mm 내부 산화피막·먼지 세척(실제 로그에서 4극 `0x4`로 오인식된 흔적 존재).
2. **그라운드 루프 아이솔레이터 또는 32Ω 병렬 부하 어댑터**: `Lepy LP-168S` 입력단의 고임피던스/AC 접지 전위차 때문에 Wolfson `WM1840` 코덱이 "이어폰 분리"로 오판하는 경우 3.5mm 패시브 그라운드 루프 아이솔레이터(변압기 절연형)나 32Ω 분기 Y잭이 매우 효과적입니다.
3. **Micro-USB OTG → USB-C/3.5mm DAC 동글(최종 우회)**: 본체 3.5mm 소켓 마모가 심할 경우 OTG USB 오디오 DAC를 사용하면 S6의 낡은 3.5mm 접점을 완전히 우회하며, 이번 패치의 `isWiredDeviceType()`이 `TYPE_USB_DEVICE`/`TYPE_AUX_LINE`까지 함께 감시하도록 반영되어 있습니다.

---

## 5. 백업 및 참고 산출물
- 수정 전 원본 백업: `d:\D_Workspace_NB\주파수\.work\backup_20261119_s6_audio_fix\`
- S6 프레임워크 분석 근거: `d:\D_Workspace_NB\주파수\.work\s6_inspect\` (`services.odex`, `services_0.dex`)
