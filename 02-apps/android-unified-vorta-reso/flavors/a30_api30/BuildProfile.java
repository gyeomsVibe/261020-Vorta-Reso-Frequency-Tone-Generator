package com.vorta.unified;

/**
 * Compile-time hardware & Android 11 (API 30) profile for Galaxy A30.
 * Zero volume manipulation; pure stereo effect lock only on dedicated devices.
 */
public final class BuildProfile {
    public static final String FLAVOR_ID = "A30_API30";
    public static final VortaModule DEFAULT_MODULE = VortaModule.A30_SWEEP;
    public static final String TARGET_OS_LABEL = "Android 11 API 30 · 순수 음원";
    public static final boolean ENABLE_S6_DIRECT_BINDER = false;
    public static final boolean ENABLE_S7_LOCKSCREEN_UNLOCK = false;
    public static final boolean ENABLE_VOLUME_GUARD = false;
    public static final boolean USE_SET_PREFERRED_DEVICE = true;

    private BuildProfile() {}
}
