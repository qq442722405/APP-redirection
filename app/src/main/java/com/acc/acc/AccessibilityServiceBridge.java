package com.acc.acc;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Context;

public class AccessibilityServiceBridge extends AccessibilityService {
    private static AccessibilityServiceBridge instance;
    private static String currentPackageName;
    @Override public void onServiceConnected(){instance=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event!=null && event.getPackageName()!=null){
            String next=event.getPackageName().toString();
            if(LabSession.active&&!next.equals(currentPackageName))LabLog.write(LabSession.lastTest.isEmpty()?"A11Y":LabSession.lastTest,"OBSERVED 前台事件："+next+"；不表示窗口坐标或进程退出已验证");
            currentPackageName=next;
        }
    }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(instance==this){instance=null;currentPackageName=null;}super.onDestroy();}
    public static String getCurrentPackage(){return currentPackageName;}
    public static boolean isTargetForeground(String pkg){return instance!=null && pkg!=null && pkg.equals(currentPackageName);}
    public static boolean performBackForTarget(String pkg, boolean close){
        if(!isTargetForeground(pkg) || android.os.Build.VERSION.SDK_INT<16)return false;
        boolean sent=instance.performGlobalAction(GLOBAL_ACTION_BACK);
        if(close&&sent)new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(isTargetForeground(pkg))instance.performGlobalAction(GLOBAL_ACTION_BACK);},220);
        return sent;
    }
    public static void perform(Context c,int action){if(instance==null){return;} if(action==1&&android.os.Build.VERSION.SDK_INT>=16)instance.performGlobalAction(GLOBAL_ACTION_BACK);else if(action==2&&android.os.Build.VERSION.SDK_INT>=16)instance.performGlobalAction(GLOBAL_ACTION_HOME);else if(action==3&&android.os.Build.VERSION.SDK_INT>=16)instance.performGlobalAction(GLOBAL_ACTION_RECENTS);}

    /** 返回一次；关闭模式会再补一次返回，尽量退出当前 APP。 */
    public static void performBackThen(boolean close){
        if(instance==null || android.os.Build.VERSION.SDK_INT<16)return;
        performBackForTarget(currentPackageName,close);
    }
}
