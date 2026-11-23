package com.vorta.aegis;

/**
 * Deterministic tab-synchronization and viewport-scroll calculation policy for Vorta Aegis Pro v2.3.0.
 * Kept pure-Java (zero Android framework imports) for host JVM unit verification.
 */
public final class TabSyncPolicy {
    public static final int TAB_COUNT = 8;

    private TabSyncPolicy() {}

    /** Clamps any candidate tab index into [0, TAB_COUNT - 1], defaulting invalid values to 0. */
    public static int clampTabIndex(int tabIndex) {
        return (tabIndex >= 0 && tabIndex < TAB_COUNT) ? tabIndex : 0;
    }

    /**
     * Resolves which category tab should be displayed:
     * - On entry / foreground return / notification open (shouldAutoSync=true):
     *   1) live playing preset's tab wins over any stale tab;
     *   2) when idle, restores the last-used persisted tab without auto-playing audio.
     * - During live foreground browsing / 250ms telemetry ticks (shouldAutoSync=false):
     *   preserves the user's current manual tab selection without hijacking.
     */
    public static int resolveTabOnEntry(
            boolean isPlaying,
            int playingTabIndex,
            int persistedTabIndex,
            int currentTabIndex,
            boolean shouldAutoSync) {
        if (shouldAutoSync) {
            if (isPlaying && playingTabIndex >= 0 && playingTabIndex < TAB_COUNT) {
                return playingTabIndex;
            }
            return clampTabIndex(persistedTabIndex);
        }
        return clampTabIndex(currentTabIndex);
    }

    /** Computes horizontal scroll X that centers the target tab pill inside the viewport. */
    public static int computeCenteredScrollX(int childLeft, int childWidth, int viewportWidth, int contentWidth) {
        if (viewportWidth <= 0 || contentWidth <= viewportWidth) {
            return 0;
        }
        int maxScroll = contentWidth - viewportWidth;
        int target = childLeft - (viewportWidth - childWidth) / 2;
        return Math.max(0, Math.min(maxScroll, target));
    }

    /** Computes vertical scroll Y that reveals the active preset card inside the ScrollView. */
    public static int computeCardRevealScrollY(int cardTopInRoot, int viewportHeight, int rootHeight, int topMarginPx) {
        if (viewportHeight <= 0 || rootHeight <= viewportHeight) {
            return 0;
        }
        int maxScroll = rootHeight - viewportHeight;
        int target = cardTopInRoot - Math.max(0, topMarginPx);
        return Math.max(0, Math.min(maxScroll, target));
    }

    /** Returns true when audio is playing and the user is currently browsing a different tab. */
    public static boolean isViewingDifferentTab(boolean isPlaying, int playingTabIndex, int currentTabIndex) {
        return isPlaying
                && playingTabIndex >= 0
                && playingTabIndex < TAB_COUNT
                && playingTabIndex != currentTabIndex;
    }
}
