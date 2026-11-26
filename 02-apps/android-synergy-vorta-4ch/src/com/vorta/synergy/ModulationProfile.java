package com.vorta.synergy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Calibrated locked modulation channels (FM, AM, RM).
 * Zero Android dependencies.
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

    private ModulationProfile() {}

    public static List<ChannelSpec> channels() {
        return CHANNELS;
    }

    public static String formatCompact(
            boolean fmOn, float fmDepth, float fmRateHz,
            boolean amOn, float amDepth, float amRateHz,
            boolean rmOn, float rmDepth, float rmRateHz) {
        StringBuilder sb = new StringBuilder();
        if (fmOn && fmDepth > 0.0f) {
            sb.append(String.format(Locale.US, "FM %.1f%%(%.1fHz)", fmDepth * 100.0f, fmRateHz));
        }
        if (amOn && amDepth > 0.0f) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(String.format(Locale.US, "AM %.0f%%(%.1fHz)", amDepth * 100.0f, amRateHz));
        }
        if (rmOn && rmDepth > 0.0f) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(String.format(Locale.US, "RM %.0f%%(%.1fHz)", rmDepth * 100.0f, rmRateHz));
        }
        if (sb.length() == 0) {
            return "순수 정현파";
        }
        return sb.toString();
    }

    public static String formatLockedSummary(boolean fmOn, boolean amOn, boolean rmOn) {
        return formatCompact(
                fmOn, LOCKED_FM_DEPTH, LOCKED_FM_RATE_HZ,
                amOn, LOCKED_AM_DEPTH, LOCKED_AM_RATE_HZ,
                rmOn, LOCKED_RM_DEPTH, LOCKED_RM_RATE_HZ);
    }

    private static List<ChannelSpec> buildChannels() {
        List<ChannelSpec> l = new ArrayList<ChannelSpec>();
        l.add(new ChannelSpec(
                Channel.FM,
                "FM 주파수 변조",
                "깊이 3.5% · 속도 1.5 Hz",
                "정재파 절점 이동 및 순응 방지 미세 변조",
                LOCKED_FM_DEPTH,
                LOCKED_FM_RATE_HZ));
        l.add(new ChannelSpec(
                Channel.AM,
                "AM 진폭 변조",
                "깊이 15% · 속도 4.0 Hz",
                "4Hz 변동강도 피크 활용 맥동 진동 변조",
                LOCKED_AM_DEPTH,
                LOCKED_AM_RATE_HZ));
        l.add(new ChannelSpec(
                Channel.RM,
                "RM 링 변조",
                "깊이 25% · 속도 12.5 Hz",
                "상하 측파대 동시 생성 복합 링 변조",
                LOCKED_RM_DEPTH,
                LOCKED_RM_RATE_HZ));
        return Collections.unmodifiableList(l);
    }
}
