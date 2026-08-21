package com.acc.acc;

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

    /** 返回一次；关闭模式会再补一次返回，尽量退出当前 APP。 */
    public static void performBackThen(boolean close){
        if(instance==null || android.os.Build.VERSION.SDK_INT<16)return;
        instance.performGlobalAction(GLOBAL_ACTION_BACK);
        if(close){
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                if(instance!=null)instance.performGlobalAction(GLOBAL_ACTION_BACK);
            },220);
        }
    }
}
