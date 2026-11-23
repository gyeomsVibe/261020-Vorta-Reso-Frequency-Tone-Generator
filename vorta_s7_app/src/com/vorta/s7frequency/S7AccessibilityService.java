package com.vorta.s7frequency;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Passive event-driven safe-volume dialog confirmer + host for S7 background volume guards.
 * Inspects ONLY Samsung SystemUI windows (including TYPE_VOLUME_OVERLAY) when
 * typeWindowStateChanged fires or when on-demand active scan is requested by S7VolumeHelper.
 */
public class S7AccessibilityService extends AccessibilityService {
    private static final String TAG = "S7AccessibilityService";
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String[] KEYWORDS = {
            "청력", "hearing", "권장 수준", "권장 레벨", "높은 볼륨", "높은 음량",
            "볼륨을 높", "음량을 높", "손상", "high volume", "recommended level",
            "listen at high", "이어폰", "오랜 시간", "safe volume", "unsafe volume"
    };
    private static final long[] ACTIVE_SCAN_DELAYS_MS = {100L, 280L, 550L, 900L, 1400L};

    private static volatile S7AccessibilityService sInstance = null;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mScanConfirmed = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        if (!S7VolumeHelper.isTargetDevice()) {
            Log.w(TAG, "not the target phone - idle");
            return;
        }
        S7VolumeHelper.syncTargetFromSystem(this);
        S7VolumeHelper.registerDynamicReceiver(this);
        S7VolumeHelper.startBackgroundGuards(this);
        S7VolumeHelper.enforceSystemFlags(this);
        S7VolumeHelper.scheduleMultiPulseEnforcement(this, "ACCESSIBILITY_CONNECTED");
    }

    /** Triggers targeted short-lived window scans after waking screen for dialog confirmation. */
    public static void requestActiveConfirmScan() {
        final S7AccessibilityService svc = sInstance;
        if (svc == null) return;
        svc.mHandler.post(new Runnable() {
            @Override
            public void run() {
                svc.mScanConfirmed = false;
                for (final long d : ACTIVE_SCAN_DELAYS_MS) {
                    svc.mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (svc.mScanConfirmed) return;
                            if (svc.scanAndConfirmSystemUiDialog(null)) {
                                svc.mScanConfirmed = true;
                            }
                        }
                    }, d);
                }
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!S7VolumeHelper.isTargetDevice()) return;
        if (event == null || event.getPackageName() == null) return;
        if (!SYSTEMUI_PKG.contentEquals(event.getPackageName())) return;
        if (scanAndConfirmSystemUiDialog(event)) {
            mScanConfirmed = true;
        }
    }

    private boolean scanAndConfirmSystemUiDialog(AccessibilityEvent event) {
        List<AccessibilityNodeInfo> candidates = new ArrayList<AccessibilityNodeInfo>();
        try {
            if (event != null) {
                AccessibilityNodeInfo sourceRoot = walkToRoot(event.getSource());
                if (sourceRoot != null) candidates.add(sourceRoot);
            }

            AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
            if (activeRoot != null) candidates.add(activeRoot);

            if (Build.VERSION.SDK_INT >= 21) {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (int i = 0; i < windows.size(); i++) {
                        AccessibilityWindowInfo win = windows.get(i);
                        if (win == null) continue;
                        AccessibilityNodeInfo winRoot = win.getRoot();
                        if (winRoot != null) candidates.add(winRoot);
                    }
                }
            }

            for (int i = 0; i < candidates.size(); i++) {
                AccessibilityNodeInfo root = candidates.get(i);
                if (root == null) continue;
                CharSequence pkg = root.getPackageName();
                if (pkg == null || !SYSTEMUI_PKG.contentEquals(pkg.toString())) continue;
                if (!hasWarningKeyword(root)) continue;
                if (clickConfirmButton(root)) {
                    Log.i(TAG, "safe-volume warning confirmed");
                    S7VolumeHelper.onDialogConfirmed(this);
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "scanAndConfirmSystemUiDialog: " + t.getMessage());
        } finally {
            recycleList(candidates);
        }
        return false;
    }

    private static AccessibilityNodeInfo walkToRoot(AccessibilityNodeInfo node) {
        if (node == null) return null;
        AccessibilityNodeInfo cur = node;
        int depth = 0;
        while (depth < 24) {
            AccessibilityNodeInfo parent = cur.getParent();
            if (parent == null) break;
            if (cur != node) cur.recycle();
            cur = parent;
            depth++;
        }
        return cur;
    }

    private static boolean hasWarningKeyword(AccessibilityNodeInfo root) {
        for (int i = 0; i < KEYWORDS.length; i++) {
            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(KEYWORDS[i]);
            if (hits != null && !hits.isEmpty()) {
                recycleList(hits);
                return true;
            }
            recycleList(hits);
        }
        return false;
    }

    private static boolean clickConfirmButton(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId("android:id/button1");
        try {
            if (byId != null) {
                for (int i = 0; i < byId.size(); i++) {
                    if (tryClickSelfOrParent(byId.get(i))) return true;
                }
            }
        } finally {
            recycleList(byId);
        }

        for (String label : new String[]{"확인", "OK", "예", "YES"}) {
            List<AccessibilityNodeInfo> byText = root.findAccessibilityNodeInfosByText(label);
            try {
                if (byText != null) {
                    for (int i = 0; i < byText.size(); i++) {
                        AccessibilityNodeInfo n = byText.get(i);
                        if (n == null || n.getText() == null) continue;
                        String t = n.getText().toString().trim().toLowerCase(Locale.US);
                        if (!"확인".equals(t) && !"ok".equals(t) && !"예".equals(t) && !"yes".equals(t)) continue;
                        if (tryClickSelfOrParent(n)) return true;
                    }
                }
            } finally {
                recycleList(byText);
            }
        }
        return false;
    }

    private static boolean tryClickSelfOrParent(AccessibilityNodeInfo node) {
        if (node == null || !node.isEnabled()) return false;
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        AccessibilityNodeInfo parent = node.getParent();
        try {
            if (parent != null && parent.isEnabled() && parent.isClickable()) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        } finally {
            if (parent != null) parent.recycle();
        }
        return false;
    }

    private static void recycleList(List<AccessibilityNodeInfo> nodes) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.size(); i++) {
            AccessibilityNodeInfo n = nodes.get(i);
            if (n != null) {
                try { n.recycle(); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        if (sInstance == this) sInstance = null;
        mHandler.removeCallbacksAndMessages(null);
        S7VolumeHelper.stopBackgroundGuards(this);
        S7VolumeHelper.unregisterDynamicReceiver(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) sInstance = null;
        mHandler.removeCallbacksAndMessages(null);
        S7VolumeHelper.stopBackgroundGuards(this);
        S7VolumeHelper.unregisterDynamicReceiver(this);
        super.onDestroy();
    }
}
