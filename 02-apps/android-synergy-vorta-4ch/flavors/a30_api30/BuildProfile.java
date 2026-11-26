package com.vorta.synergy;

/**
 * Compile-time hardware & Android 11 (API 30) profile.
 */
public final class BuildProfile {
    public static final String FLAVOR_ID = "A30_API30";
    public static final SynergyChannel DEFAULT_CHANNEL = SynergyChannel.CH3_TACTILE_MAIN;
    public static final String TARGET_OS_LABEL = "Android 11 API 30 · 32-bit Float";
    public static final boolean ENABLE_S6_DIRECT_BINDER = false;
    public static final boolean ENABLE_S7_LOCKSCREEN_UNLOCK = false;
    public static final boolean ENABLE_VOLUME_GUARD = false;
    public static final boolean USE_SET_PREFERRED_DEVICE = true;

    private BuildProfile() {}
}
