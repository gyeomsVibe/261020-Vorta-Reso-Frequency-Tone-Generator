package com.vorta.unified;

/**
 * Compile-time hardware & Android 8.0 Oreo (API 26) profile for Galaxy S7.
 */
public final class BuildProfile {
    public static final String FLAVOR_ID = "S7_API26";
    public static final VortaModule DEFAULT_MODULE = VortaModule.S7_ANCHOR;
    public static final String TARGET_OS_LABEL = "Android 8.0 API 26 · AudioOut_D";
    public static final boolean ENABLE_S6_DIRECT_BINDER = false;
    public static final boolean ENABLE_S7_LOCKSCREEN_UNLOCK = true;
    public static final boolean ENABLE_VOLUME_GUARD = true;
    public static final boolean USE_SET_PREFERRED_DEVICE = true;

    private BuildProfile() {}
}
