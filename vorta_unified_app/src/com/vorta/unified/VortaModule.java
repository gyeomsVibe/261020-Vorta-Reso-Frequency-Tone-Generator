package com.vorta.unified;

/**
 * Merged 4 non-Aegis Vorta frequency generator modules selectable via the top-left accordion menu.
 * Order required by user: 1. 보르타 Pro -> 2. 갤럭시 A30 -> 3. 갤럭시 S7 -> 4. 갤럭시 S6.
 */
public enum VortaModule {
    PRO_MANUAL("1. 보르타 Pro · 스윕·수동 정밀", "1. 보르타 Pro", "멀티 스윕 스케줄러 · 수동 정밀 발진 · 순수 변조 · 프리셋"),
    A30_SWEEP("2. 갤럭시 A30 · 광역 스윕 120W", "2. 갤럭시 A30", "Exynos 7904 · 120W 모노 우퍼 · 트랜스+본 광역 스윕"),
    S7_ANCHOR("3. 갤럭시 S7 · 스마토 앵커", "3. 갤럭시 S7", "Exynos 8890 CS47L91 · 240W 앵커 직타 및 저역 코어"),
    S6_BTL("4. 갤럭시 S6 · 트랜스2 68W BTL", "4. 갤럭시 S6", "Exynos 7420 WM1840 · BTL 모노 서밍 · 68W 골전도");

    public final String fullTitle;
    public final String shortLabel;
    public final String hardwareSubtitle;

    VortaModule(String fullTitle, String shortLabel, String hardwareSubtitle) {
        this.fullTitle = fullTitle;
        this.shortLabel = shortLabel;
        this.hardwareSubtitle = hardwareSubtitle;
    }

    public static VortaModule fromName(String name, VortaModule fallback) {
        if (name != null) {
            for (int i = 0; i < values().length; i++) {
                if (values()[i].name().equals(name)) {
                    return values()[i];
                }
            }
        }
        return fallback;
    }
}
