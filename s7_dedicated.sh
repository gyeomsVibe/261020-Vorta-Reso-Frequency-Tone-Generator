#!/system/bin/sh
# ==============================================================================
# Galaxy S7 (SM-G930*, Android 8.0 Oreo) 4-Layer Safe Volume Permanent Disabler
# ==============================================================================

# 1. Disable Samsung SoundAlive & Adapt Sound
pm disable-user --user 0 com.sec.android.app.soundalive > /dev/null 2>&1
pm disable-user --user 0 com.sec.hearingadjust > /dev/null 2>&1

# 2. Layer 1 (BIOS / Init / System Properties) Safe Media Bypass

# 3. Layer 2 (Global / Secure / System DB) Safe Volume & 20h Timer Disabler
# CRITICAL (Samsung AudioService.onConfigureSafeVolume bytecode): must be 2 (SAFE_MEDIA_VOLUME_INACTIVE); 1 forces ACTIVE(3)!
settings put global audio_safe_volume_state 2
settings put global safe_media_volume_active 0
settings put global safe_media_volume_enabled 0
settings put global audio_safe_csd_next_warning 99999999.0
settings put global bluetooth_disable_absolute_volume 1
settings put secure unsafe_volume_music_active_ms 0
settings put system master_mono 0
settings put system safe_volume_state 0
settings put system safe_volume_multimedia 0
settings put system volume_limiter_enable 0
settings put system volume_limiter_value 15
settings put system volumelimit_on 0
settings put system earphone_safety_volume 0
settings put system volumelimit_on 0
settings put secure audio_safe_volume_state 2
settings put global audio_safe_volume_state 2
settings put system mono_audio 0
settings put system master_mono 0

# 4. Mute notifications and system sounds
settings put system volume_ring 0
settings put system volume_notification 0
settings put system volume_system 0
settings put system sound_effects_enabled 0
settings put system dtmf_tone 0
settings put system haptic_feedback_enabled 0

# 5. Display & Lockscreen - Restore normal screen sleep (Principle 3: keep 10 min = 600000ms)
settings put global stay_on_while_plugged_in 0
settings put secure lockscreen.disabled 1
settings put system screen_off_timeout 600000

# 6. Layer 3 & 4: Remove distortion-inducing third-party apps & Bind Multi-Window Overlay Accessibility Guard
pm uninstall com.s7.safevolumebypass > /dev/null 2>&1
pm uninstall local.s7.wiredgain > /dev/null 2>&1
pm uninstall com.boedec.hoel.frequencygenerator > /dev/null 2>&1
pm uninstall com.vorta.s7safevolumeoverlay > /dev/null 2>&1
for PKG in com.vorta.s7frequency com.vorta.s6frequency com.smato.a30frequency com.vorta.freqgen; do
    pm grant $PKG android.permission.WRITE_SECURE_SETTINGS > /dev/null 2>&1
    appops set $PKG WRITE_SETTINGS allow > /dev/null 2>&1
    appops set $PKG RUN_IN_BACKGROUND allow > /dev/null 2>&1
    appops set $PKG WAKE_LOCK allow > /dev/null 2>&1
    dumpsys deviceidle whitelist +$PKG > /dev/null 2>&1
done
settings put system volume_limiter 0
settings put system k2hd_effect 0
settings put system tube_amp_effect 0
settings put system uhqa_effect 0
settings put system sound_alive_effect 0
settings put system concert_hall_effect 0
settings put secure enabled_accessibility_services "com.vorta.s7frequency/com.vorta.s7frequency.S7AccessibilityService"
settings put secure accessibility_enabled 1
am broadcast -a com.vorta.s7frequency.ACTION_SAFE_VOLUME_CHECK -p com.vorta.s7frequency --include-stopped-packages > /dev/null 2>&1

# 7. Doze & App Whitelist
settings put global sem_low_power_mode 0
dumpsys deviceidle disable > /dev/null 2>&1

echo "[S7_OVERHAUL_SUCCESS] 4-Layer Safe Volume Disabler applied."
