/**
 * Shared pure-JS policy modules for Vorta Aegis Pro v2.3 PC:
 * exact parity with Android VolumeCurve.java and TabSyncPolicy.java.
 */

export const VolumeCurve = {
    MIN_DB: -48.0,
    DEFAULT_PERCENT: 45,

    clampPercent(percent) {
        const n = Math.round(Number(percent));
        if (!Number.isFinite(n)) return this.DEFAULT_PERCENT;
        if (n <= 0) return 0;
        if (n >= 100) return 100;
        return n;
    },

    percentToDb(percent) {
        const clamped = this.clampPercent(percent);
        if (clamped <= 0) return -Infinity;
        if (clamped >= 100) return 0.0;
        const db = this.MIN_DB * (1.0 - (clamped / 100.0));
        return Math.abs(db) < 0.0005 ? 0.0 : db;
    },

    percentToLinearGain(percent) {
        const clamped = this.clampPercent(percent);
        if (clamped <= 0) return 0.0;
        if (clamped >= 100) return 1.0;
        return Math.pow(10.0, this.percentToDb(clamped) / 20.0);
    },

    formatDb(percent, isMuted) {
        const clamped = this.clampPercent(percent);
        if (isMuted || clamped <= 0) {
            return "-\u221e dB";
        }
        let db = this.percentToDb(clamped);
        if (Math.abs(db) < 0.05) {
            db = 0.0;
        }
        return `${db.toFixed(1)} dB`;
    }
};

export const TAB_NAMES = [
    "안티방어 12",
    "수면 6",
    "집중·공부 6",
    "명상·안정 5",
    "두통·통증 5",
    "운동·피로 5",
    "기분·힐링 5",
    "모기·벌레 4"
];

export const TAB_SHORT_NAMES = [
    "안티방어",
    "수면",
    "집중·공부",
    "명상·안정",
    "두통·통증",
    "운동·피로",
    "기분·힐링",
    "모기·벌레"
];

export const TabSyncPolicy = {
    TAB_COUNT: 8,

    clampTabIndex(tabIndex) {
        const n = Number(tabIndex);
        return (Number.isInteger(n) && n >= 0 && n < this.TAB_COUNT) ? n : 0;
    },

    resolveTabOnEntry(isPlaying, playingTabIndex, persistedTabIndex, currentTabIndex, shouldAutoSync) {
        if (shouldAutoSync) {
            if (isPlaying && Number.isInteger(playingTabIndex) && playingTabIndex >= 0 && playingTabIndex < this.TAB_COUNT) {
                return playingTabIndex;
            }
            return this.clampTabIndex(persistedTabIndex);
        }
        return this.clampTabIndex(currentTabIndex);
    },

    isViewingDifferentTab(isPlaying, playingTabIndex, currentTabIndex) {
        return Boolean(isPlaying)
            && Number.isInteger(playingTabIndex)
            && playingTabIndex >= 0
            && playingTabIndex < this.TAB_COUNT
            && playingTabIndex !== currentTabIndex;
    }
};

export function buildPresetSpecLine(preset) {
    const parts = [];
    if (preset.isSweep) {
        parts.push(`스윕 ${preset.sweepStart.toFixed(1)}~${preset.sweepEnd.toFixed(1)} Hz · ${preset.sweepDurationSec.toFixed(0)}s 왕복`);
    } else if (preset.binauralBeat > 0) {
        parts.push(`바이노럴 ${preset.freq1.toFixed(1)} + ${preset.freq2.toFixed(1)} Hz (차 ${preset.binauralBeat.toFixed(2)} Hz)`);
    } else if (preset.freq2 > 0) {
        parts.push(`듀얼 ${preset.freq1.toFixed(2)} + ${preset.freq2.toFixed(2)} Hz`);
    } else {
        parts.push(`단일 ${preset.freq1.toFixed(2)} Hz`);
    }
    if (preset.isAntiPhase) {
        parts.push("180° 상쇄");
    }
    if (preset.fmMod) {
        parts.push(`FM ±${(preset.fmDepth * 100).toFixed(0)}%`);
    }
    if (preset.amMod) {
        parts.push(`AM ${(preset.amDepth * 100).toFixed(0)}%`);
    }
    return parts.join(" · ");
}
