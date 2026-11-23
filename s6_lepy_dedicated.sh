#!/system/bin/sh
# ==============================================================================
# 갤럭시 S6 (SM-G920K) -> Lepy LP-168S (2.1ch 68W BTL) 앰프 전용 머신 전수개조
# ==============================================================================
echo "[1/6] 삼성 SoundAlive & 오디오 이펙트 패키지 완전 동결..."
pm disable-user --user 0 com.sec.android.app.soundalive 2>/dev/null
pm disable-user --user 0 com.sec.hearingadjust 2>/dev/null
pm disable-user --user 0 com.samsung.android.game.gos 2>/dev/null

echo "[2/6] 안드로이드 안전음량(Safe Volume) & 볼륨 리미터 영구 무력화..."
settings put global audio_safe_volume_state 2
settings put global safe_media_volume_active 0
settings put global safe_media_volume_enabled 0
settings put system safe_volume_state 0
settings put system volume_limiter_enable 0
settings put system volume_limiter_value 15
setprop audio.safemedia.bypass true

echo "[3/6] 사용자 자유 볼륨 조절 보장 (안전볼륨 해제, 강제 고정 없음)..."
# No forced volume 15 - user has 100% free volume control to prevent amplifier overload

echo "[4/6] 시스템 알림, 벨소리, 터치음, 진동 100% 무음화 (앰프 노이즈 유입 0화)..."
settings put system volume_ring 0
settings put system volume_notification 0
settings put system volume_system 0
settings put system sound_effects_enabled 0
settings put system dtmf_tone 0
settings put system haptic_feedback_enabled 0

echo "[5/6] 화면 정상 절전 모드 복원 (화면 끄기 및 자동 절전 정상화)..."
settings put global stay_on_while_plugged_in 0
settings put secure lockscreen.disabled 1
settings put system screen_off_timeout 60000

echo "[6/6] Doze 모드 화이트리스트 & 절전 킬러 무력화..."
settings put global sem_low_power_mode 0
dumpsys deviceidle whitelist +com.vorta.s6frequency 2>/dev/null
dumpsys deviceidle disable 2>/dev/null

echo "=== 갤럭시 S6 -> Lepy LP-168S 앰프 전용 머신 전수개조 완료 ==="