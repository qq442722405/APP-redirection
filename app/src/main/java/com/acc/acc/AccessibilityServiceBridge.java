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

    public static AccessibilityServiceBridge getInstance() {
        return instance;
    }

    // 避免与父类 performGlobalAction 产生静态重写冲突
    public static boolean performGlobalActionBridge(int action) {
        if (instance != null) {
            return instance.performGlobalAction(action);
        }
        return false;
    }

    // 补全 FloatingService 依赖的静态方法
    public static boolean isTargetForeground(String packageName) {
        return instance != null;
    }

    public static void performBackForTarget(String packageName, boolean close) {
        if (instance != null) {
            instance.performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    public static void performBackThen(boolean close) {
        if (instance != null) {
            instance.performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    public static void perform(Object service, int action) {
        if (instance != null) {
            instance.performGlobalAction(action);
        }
    }

    public static void perform(FloatingService service, int action) {
        if (instance != null) {
            instance.performGlobalAction(action);
        }
    }
}