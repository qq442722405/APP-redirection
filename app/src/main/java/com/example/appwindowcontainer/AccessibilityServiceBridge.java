package com.example.appwindowcontainer;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Context;

public class AccessibilityServiceBridge extends AccessibilityService {
    private static AccessibilityServiceBridge instance;
    @Override public void onServiceConnected(){instance=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
    public static void perform(Context c,int action){if(instance==null){return;} if(action==1&&android.os.Build.VERSION.SDK_INT>=16)instance.performGlobalAction(GLOBAL_ACTION_BACK);else if(action==2&&android.os.Build.VERSION.SDK_INT>=16)instance.performGlobalAction(GLOBAL_ACTION_HOME);else if(action==3&&android.os.Build.VERSION.SDK_INT>=16)instance.performGlobalAction(GLOBAL_ACTION_RECENTS);}
}
