package com.vorta.unified;

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
 * Passive event-driven safe-volume dialog confirmer + host for background guards (S6/S7 builds only).
 */
public class VortaAccessibilityService extends AccessibilityService {
    private static final String TAG = "VortaA11y";
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String[] KEYWORDS = {
            "청력", "hearing", "권장 수준", "권장 레벨", "높은 볼륨", "높은 음량",
            "볼륨을 높", "음량을 높", "손상", "high volume", "recommended level",
            "listen at high", "이어폰", "오랜 시간", "safe volume", "unsafe volume",
            "음량 제한", "최대 음량", "미디어 음량", "헤드폰", "유선 이어폰", "볼륨 제한"
    };
    private static final long[] SCAN_DELAYS_MS = {100L, 280L, 550L, 900L, 1400L, 2200L, 3200L, 4500L};

    private static volatile VortaAccessibilityService sInstance = null;

    public static boolean isConnected() {
        return sInstance != null;
    }
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mScanConfirmed = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        if (!VortaVolumeGuard.isTargetDevice()) return;
        VortaVolumeGuard.syncTargetFromSystem(this);
        VortaVolumeGuard.registerDynamicReceiver(this);
        VortaVolumeGuard.startBackgroundGuards(this);
        VortaVolumeGuard.enforceSystemFlags(this);
        VortaVolumeGuard.scheduleMultiPulseEnforcement(this, "ACCESSIBILITY_CONNECTED");
    }

    public static void requestActiveConfirmScan() {
        final VortaAccessibilityService svc = sInstance;
        if (svc == null) return;
        svc.mHandler.post(new Runnable() {
            @Override
            public void run() {
                svc.mScanConfirmed = false;
                for (final long d : SCAN_DELAYS_MS) {
                    svc.mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (svc.mScanConfirmed) return;
                            if (svc.scanAndConfirmSystemUiDialog(null)) svc.mScanConfirmed = true;
                        }
                    }, d);
                }
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!VortaVolumeGuard.isTargetDevice() || event == null || event.getPackageName() == null) return;
        if (!SYSTEMUI_PKG.contentEquals(event.getPackageName())) return;
        if (scanAndConfirmSystemUiDialog(event)) mScanConfirmed = true;
    }

    private boolean scanAndConfirmSystemUiDialog(AccessibilityEvent event) {
        List<AccessibilityNodeInfo> candidates = new ArrayList<AccessibilityNodeInfo>();
        try {
            if (event != null) {
                AccessibilityNodeInfo src = walkToRoot(event.getSource());
                if (src != null) candidates.add(src);
            }
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null) candidates.add(active);
            if (Build.VERSION.SDK_INT >= 21) {
                List<AccessibilityWindowInfo> wins = getWindows();
                if (wins != null) {
                    for (int i = 0; i < wins.size(); i++) {
                        AccessibilityWindowInfo w = wins.get(i);
                        if (w != null && w.getRoot() != null) candidates.add(w.getRoot());
                    }
                }
            }
            for (int i = 0; i < candidates.size(); i++) {
                AccessibilityNodeInfo root = candidates.get(i);
                if (root == null || root.getPackageName() == null) continue;
                if (!SYSTEMUI_PKG.contentEquals(root.getPackageName().toString())) continue;
                if (!hasWarningKeyword(root)) continue;
                if (clickConfirmButton(root)) {
                    Log.i(TAG, "safe-volume dialog confirmed");
                    VortaVolumeGuard.onDialogConfirmed(this);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            recycleList(candidates);
        }
        return false;
    }

    private static AccessibilityNodeInfo walkToRoot(AccessibilityNodeInfo node) {
        if (node == null) return null;
        AccessibilityNodeInfo cur = node;
        for (int depth = 0; depth < 24; depth++) {
            AccessibilityNodeInfo p = cur.getParent();
            if (p == null) break;
            if (cur != node) cur.recycle();
            cur = p;
        }
        return cur;
    }

    private static boolean hasWarningKeyword(AccessibilityNodeInfo root) {
        for (String kw : KEYWORDS) {
            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(kw);
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
                for (AccessibilityNodeInfo n : byId) {
                    if (tryClickSelfOrParent(n)) return true;
                }
            }
        } finally {
            recycleList(byId);
        }
        for (String label : new String[]{"확인", "OK", "계속", "허용"}) {
            List<AccessibilityNodeInfo> hits = root.findAccessibilityNodeInfosByText(label);
            try {
                if (hits != null) {
                    for (AccessibilityNodeInfo n : hits) {
                        CharSequence txt = n.getText();
                        String s = txt != null ? txt.toString().trim().toUpperCase(Locale.US) : "";
                        if (!"확인".equals(s) && !"OK".equals(s) && !"계속".equals(s) && !"허용".equals(s)) continue;
                        if (tryClickSelfOrParent(n)) return true;
                    }
                }
            } finally {
                recycleList(hits);
            }
        }
        return false;
    }

    private static boolean tryClickSelfOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        for (int depth = 0; cur != null && depth < 4; depth++) {
            if (cur.isClickable() && cur.isEnabled() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                if (cur != node) cur.recycle();
                return true;
            }
            AccessibilityNodeInfo p = cur.getParent();
            if (cur != node) cur.recycle();
            cur = p;
        }
        return false;
    }

    private static void recycleList(List<AccessibilityNodeInfo> list) {
        if (list == null) return;
        for (AccessibilityNodeInfo n : list) {
            if (n != null) {
                try { n.recycle(); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        sInstance = null;
        VortaVolumeGuard.unregisterDynamicReceiver(this);
        VortaVolumeGuard.stopBackgroundGuards(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        mHandler.removeCallbacksAndMessages(null);
        VortaVolumeGuard.unregisterDynamicReceiver(this);
        VortaVolumeGuard.stopBackgroundGuards(this);
        super.onDestroy();
    }
}
