package com.vorta.unified;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure locked modulation channels (FM, AM, RM) with calibrated fixed parameters.
 * Operational scenarios belong strictly in ScenarioCatalog / Presets tabs.
 */
public final class ModulationProfile {
    public static final float LOCKED_FM_DEPTH = 0.035f;
    public static final float LOCKED_FM_RATE_HZ = 1.50f;

    public static final float LOCKED_AM_DEPTH = 0.150f;
    public static final float LOCKED_AM_RATE_HZ = 4.00f;

    public static final float LOCKED_RM_DEPTH = 0.250f;
    public static final float LOCKED_RM_RATE_HZ = 12.50f;

    public enum Channel {
        FM,
        AM,
        RM
    }

    public static final class ChannelSpec {
        public final Channel channel;
        public final String title;
        public final String lockedSpec;
        public final String desc;
        public final float depth;
        public final float rateHz;

        public ChannelSpec(Channel channel, String title, String lockedSpec, String desc, float depth, float rateHz) {
            this.channel = channel;
            this.title = title;
            this.lockedSpec = lockedSpec;
            this.desc = desc;
            this.depth = depth;
            this.rateHz = rateHz;
        }
    }

    private static final List<ChannelSpec> CHANNELS = buildChannels();

    private ModulationProfile() {
        throw new AssertionError("No empty construction");
    }

    public static List<ChannelSpec> channels() {
        return CHANNELS;
    }

    public static String formatActiveSummary(boolean fmOn, boolean amOn, boolean rmOn) {
        StringBuilder sb = new StringBuilder();
        if (fmOn) sb.append("FM 3.5%(1.5Hz)");
        if (amOn) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("AM 15%(4.0Hz)");
        }
        if (rmOn) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("RM 25%(12.5Hz)");
        }
        if (sb.length() == 0) {
            return "변조 OFF · 순수 정현파";
        }
        return sb.toString() + " · 고정";
    }

    private static List<ChannelSpec> buildChannels() {
        List<ChannelSpec> l = new ArrayList<ChannelSpec>();
        l.add(new ChannelSpec(
                Channel.FM,
                "FM 주파수 변조 (Vibrato)",
                "깊이 3.5% · 속도 1.50 Hz · 최적값 고정",
                "정재파·위상 상쇄 방지용 최적 미세 주파수 변조",
                LOCKED_FM_DEPTH,
                LOCKED_FM_RATE_HZ));
        l.add(new ChannelSpec(
                Channel.AM,
                "AM 진폭 변조 (Tremolo)",
                "깊이 15% · 속도 4.00 Hz · 최적값 고정",
                "과변조 없는 체성 맥동 진동용 최적 진폭 변조",
                LOCKED_AM_DEPTH,
                LOCKED_AM_RATE_HZ));
        l.add(new ChannelSpec(
                Channel.RM,
                "RM 링 변조 (사이드밴드 비트)",
                "깊이 25% · 캐리어 12.5 Hz · 최적값 고정",
                "양측 측파대(f±12.5Hz) 생성용 최적 링 변조",
                LOCKED_RM_DEPTH,
                LOCKED_RM_RATE_HZ));
        return Collections.unmodifiableList(l);
    }
}
