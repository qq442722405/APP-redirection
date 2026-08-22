package com.acc.acc;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class AccessibilityServiceBridge extends AccessibilityService {

    private static AccessibilityServiceBridge instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public static boolean performGlobalAction(int action) {
        if (instance != null) {
            return instance.performGlobalAction(action);
        }
        return false;
    }
}