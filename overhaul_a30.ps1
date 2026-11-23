# ==============================================================================
# 🛠️ 갤럭시 A30 보르타 레조 vNext 전용 스마트폰 OS 환경 전수개조 스크립트
# 대상 기기: Samsung Galaxy A30 (SM-A305N, Android 11 One UI 3.1)
# 연결 앰프: 스마토 우퍼 전용 파워 앰프 (최대 120W / 정격 60W RMS, 12V 5A)
# 물리 결선: 3.5mm AUX 헤드폰 잭 (C타입은 24시간 상시 충전 전용 분리)
# ==============================================================================

param (
    [string]$DeviceId = "R59N105TEYF"
)

$adb = "C:\Users\Kimyoongyeom\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "C:\platform-tools\adb.exe"
}

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " [A30 전용 스마트폰 전수개조] 스마토 120W 앰프 최적화 OS 환경 개조 ($DeviceId)" -ForegroundColor Yellow
Write-Host "==================================================================" -ForegroundColor Cyan

# 1. 삼성 음장 효과(SoundAlive) 및 DRC/EQ 완전 동결
Write-Host "[1/7] 삼성 SoundAlive, Hearing Adjust, GOS 패키지 완전 동결..." -ForegroundColor Green
& $adb -s $DeviceId shell pm disable-user --user 0 com.sec.android.app.soundalive
& $adb -s $DeviceId shell pm disable-user --user 0 com.sec.hearingadjust
& $adb -s $DeviceId shell pm disable-user --user 0 com.samsung.android.game.gos

# 2. 안전 볼륨 제한(Safe Volume) 영구 무력화 (볼륨 강제고정 루프 없음!)
Write-Host "[2/7] 시스템 안전 볼륨 제한 팝업 및 자동 감쇄 영구 해제..." -ForegroundColor Green
& $adb -s $DeviceId shell settings put global audio_safe_volume_state 2
& $adb -s $DeviceId shell settings put system volume_limiter_enable 0
& $adb -s $DeviceId shell settings put system volume_limiter_value 15
& $adb -s $DeviceId shell setprop audio.safemedia.bypass true

# 3. 24시간 상시 충전 배터리 스웰링 방지 모드 (Android 11 전용 85% 상한)
Write-Host "[3/7] 24/7 상시 전원 연결 배터리 보호 모드(85% 캡) 활성화..." -ForegroundColor Green
& $adb -s $DeviceId shell settings put global protect_battery 1

# 4. 충전 중 화면 끄기 허용 (stay_on = 0, S7/S6 오류 재발 방지)
Write-Host "[4/7] 충전 중 전원 버튼 누름 시 화면 즉시 절전 허용 (stay_on=0)..." -ForegroundColor Green
& $adb -s $DeviceId shell settings put global stay_on_while_plugged_in 0
# screen_off_timeout은 사용자의 10분(600000ms) 설정을 100% 보존함!

# 5. 백그라운드 Doze 및 절전 킬러 완전 차단 (백그라운드 포그라운드 서비스 보호)
Write-Host "[5/7] Doze 화이트리스트 및 저전력 모드 해제..." -ForegroundColor Green
& $adb -s $DeviceId shell dumpsys deviceidle whitelist +com.smato.a30frequency
& $adb -s $DeviceId shell settings put global sem_low_power_mode 0
& $adb -s $DeviceId shell settings put global low_power 0

# 6. 시스템 터치음/알림음 무음화 (120W 우퍼로 시스템 삑소리 유입 차단)
Write-Host "[6/7] 터치음, 다이얼톤, 알림 스트림 무음화..." -ForegroundColor Green
& $adb -s $DeviceId shell settings put system sound_effects_enabled 0
& $adb -s $DeviceId shell settings put system dtmf_tone 0
& $adb -s $DeviceId shell settings put system haptic_feedback_enabled 0
& $adb -s $DeviceId shell media volume --stream 5 --set 0 # 알림 볼륨 0
& $adb -s $DeviceId shell media volume --stream 1 --set 0 # 시스템 볼륨 0

# 7. 미디어 볼륨 기본 최적 레벨 설정 (12단계, 80% THD 방어선, 수동 조절 자유)
Write-Host "[7/7] 미디어 스트림 권장 음량(12/15) 설정 (수동 조절 100% 자유)..." -ForegroundColor Green
& $adb -s $DeviceId shell media volume --stream 3 --set 12

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " [개조 완료] 갤럭시 A30이 스마토 120W 앰프 전용 머신으로 개조되었습니다." -ForegroundColor Green
Write-Host "==================================================================" -ForegroundColor Cyan
