package com.vorta.unified;

/**
 * Compile-time hardware & Android 7.0 (API 24) profile for Galaxy S6 (Wolfson WM1840).
 */
public final class BuildProfile {
    public static final String FLAVOR_ID = "S6_API24";
    public static final VortaModule DEFAULT_MODULE = VortaModule.S6_BTL;
    public static final String TARGET_OS_LABEL = "Android 7.0 API 24 · WM1840";
    public static final boolean ENABLE_S6_DIRECT_BINDER = true;
    public static final boolean ENABLE_S7_LOCKSCREEN_UNLOCK = false;
    public static final boolean ENABLE_VOLUME_GUARD = true;
    public static final boolean USE_SET_PREFERRED_DEVICE = false; // Native WM1840 media-headset routing

    private BuildProfile() {}
}
