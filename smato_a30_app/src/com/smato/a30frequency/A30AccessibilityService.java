package com.smato.a30frequency;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Galaxy A30 has no Safe Volume restriction (Permanent Principle Section 1).
 * Kept as a no-op stub only for source compatibility; not registered in AndroidManifest.xml.
 */
public class A30AccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
