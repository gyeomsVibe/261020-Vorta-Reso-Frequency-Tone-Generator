package com.vorta.synergy;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Passive event-driven safe-volume dialog confirmer (S6/S7 builds only).
 */
public class SynergyAccessibilityService extends AccessibilityService {
    private static final String TAG = "SynergyA11y";
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String[] KEYWORDS = {
            "청력", "hearing", "권장 수준", "권장 레벨", "높은 볼륨", "높은 음량",
            "볼륨을 높", "음량을 높", "음량 제한", "최대 음량", "미디어 음량",
            "손상", "high volume", "recommended level", "listen at high",
            "volume limit", "earphones", "이어폰", "오랜 시간", "safe volume", "unsafe volume"
    };
    private static final long[] SCAN_DELAYS_MS = {80L, 220L, 480L, 900L, 1500L, 2400L};

    private static volatile SynergyAccessibilityService sInstance = null;

    public static boolean isConnected() {
        return sInstance != null;
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mScanConfirmed = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        if (!SynergyVolumeGuard.isTargetDevice()) return;
        SynergyVolumeGuard.syncTargetFromSystem(this);
        SynergyVolumeGuard.registerDynamicReceiver(this);
        SynergyVolumeGuard.startBackgroundGuards(this);
        SynergyVolumeGuard.enforceSystemFlags(this);
        SynergyVolumeGuard.scheduleMultiPulseEnforcement(this, "ACCESSIBILITY_CONNECTED");
    }

    public static void requestActiveConfirmScan() {
        final SynergyAccessibilityService svc = sInstance;
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
        if (!SynergyVolumeGuard.isTargetDevice() || event == null || event.getPackageName() == null) return;
        if (!SYSTEMUI_PKG.contentEquals(event.getPackageName())) return;
        if (scanAndConfirmSystemUiDialog(event)) mScanConfirmed = true;
    }

    private boolean scanAndConfirmSystemUiDialog(AccessibilityEvent event) {
        try {
            int safeState = Settings.Global.getInt(getContentResolver(), "audio_safe_volume_state", 0);
            if (safeState == 2 && event == null) return false;
        } catch (Throwable ignored) {}

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
                    SynergyVolumeGuard.onDialogConfirmed(this);
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
        SynergyVolumeGuard.unregisterDynamicReceiver(this);
        SynergyVolumeGuard.stopBackgroundGuards(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        mHandler.removeCallbacksAndMessages(null);
        SynergyVolumeGuard.unregisterDynamicReceiver(this);
        SynergyVolumeGuard.stopBackgroundGuards(this);
        super.onDestroy();
    }
}
