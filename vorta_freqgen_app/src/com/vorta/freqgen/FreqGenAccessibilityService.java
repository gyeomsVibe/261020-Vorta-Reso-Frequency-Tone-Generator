package com.vorta.freqgen;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Disabled dummy service placeholder to prevent crashes if referenced.
 */
public class FreqGenAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
