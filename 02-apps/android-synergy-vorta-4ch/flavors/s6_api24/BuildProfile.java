package com.vorta.synergy;

/**
 * Compile-time hardware & Android 7.0 (API 24) profile.
 */
public final class BuildProfile {
    public static final String FLAVOR_ID = "S6_API24";
    public static final SynergyChannel DEFAULT_CHANNEL = SynergyChannel.CH2_LOW_MID;
    public static final String TARGET_OS_LABEL = "Android 7.0 API 24 · 32-bit Float";
    public static final boolean ENABLE_S6_DIRECT_BINDER = true;
    public static final boolean ENABLE_S7_LOCKSCREEN_UNLOCK = false;
    public static final boolean ENABLE_VOLUME_GUARD = true;
    public static final boolean USE_SET_PREFERRED_DEVICE = false;

    private BuildProfile() {}
}
