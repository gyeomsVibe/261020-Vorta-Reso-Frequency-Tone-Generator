package com.vorta.synergy;

/**
 * 4-channel hardware role & strict frequency band guardrail model.
 * Zero Android dependencies so host JVM audit tests execute exact rules.
 */
public enum SynergyChannel {
    CH1_SUB_LOW(
            "CH 1 · 극저역 우퍼",
            "CH 1",
            "1~21 Hz",
            1.0f,
            21.0f,
            4.5f,
            20.5f,
            0,
            0,
            false,
            "극저역 진동 전담 · 21 Hz 상한 보호"
    ),
    CH2_LOW_MID(
            "CH 2 · 저역 우퍼",
            "CH 2",
            "10~40 Hz",
            10.0f,
            40.0f,
            22.0f,
            39.5f,
            0,
            0,
            false,
            "저역 공진 전담 · 22~39.5 Hz 주력"
    ),
    CH3_TACTILE_MAIN(
            "CH 3 · 골전도 메인",
            "CH 3",
            "40~63 Hz",
            40.0f,
            63.0f,
            52.0f,
            63.0f,
            0,
            0,
            false,
            "최대 출력 메인 · 52~63Hz 핵심 연속"
    ),
    CH4_TACTILE_SUB(
            "CH 4 · 골전도 보조",
            "CH 4",
            "40~63 Hz",
            40.0f,
            63.0f,
            40.0f,
            51.0f,
            20,
            10,
            true,
            "열보호 보조(20/10분) · 40~51Hz / -4Hz 맥놀이"
    );

    public final String displayName;
    public final String shortLabel;
    public final String bandLabel;
    public final float minHz;
    public final float maxHz;
    public final float sweetMinHz;
    public final float sweetMaxHz;
    public final int defaultDutyOnMin;
    public final int defaultDutyCoolMin;
    public final boolean recommendVolumeLimitHalf;
    public final String roleSubtitle;

    SynergyChannel(
            String displayName,
            String shortLabel,
            String bandLabel,
            float minHz,
            float maxHz,
            float sweetMinHz,
            float sweetMaxHz,
            int defaultDutyOnMin,
            int defaultDutyCoolMin,
            boolean recommendVolumeLimitHalf,
            String roleSubtitle) {
        this.displayName = displayName;
        this.shortLabel = shortLabel;
        this.bandLabel = bandLabel;
        this.minHz = minHz;
        this.maxHz = maxHz;
        this.sweetMinHz = sweetMinHz;
        this.sweetMaxHz = sweetMaxHz;
        this.defaultDutyOnMin = defaultDutyOnMin;
        this.defaultDutyCoolMin = defaultDutyCoolMin;
        this.recommendVolumeLimitHalf = recommendVolumeLimitHalf;
        this.roleSubtitle = roleSubtitle;
    }

    public float clampHz(float hz) {
        if (Float.isNaN(hz) || Float.isInfinite(hz)) return sweetMinHz;
        if (hz < minHz) return minHz;
        if (hz > maxHz) return maxHz;
        return hz;
    }

    public boolean isInBand(float hz) {
        return !Float.isNaN(hz) && !Float.isInfinite(hz) && hz >= minHz && hz <= maxHz;
    }

    public float[] quickPresets() {
        if (this == CH1_SUB_LOW) {
            return new float[]{7.0f, 10.5f, 16.0f, 19.0f};
        }
        if (this == CH2_LOW_MID) {
            return new float[]{24.0f, 28.0f, 31.5f, 37.0f};
        }
        if (this == CH3_TACTILE_MAIN) {
            return new float[]{48.0f, 52.0f, 55.0f, 60.0f};
        }
        return new float[]{42.0f, 45.0f, 48.0f, 51.0f};
    }

    public static SynergyChannel fromName(String name, SynergyChannel fallback) {
        if (name != null) {
            SynergyChannel[] vals = values();
            for (int i = 0; i < vals.length; i++) {
                if (vals[i].name().equals(name)) {
                    return vals[i];
                }
            }
        }
        return fallback;
    }
}
