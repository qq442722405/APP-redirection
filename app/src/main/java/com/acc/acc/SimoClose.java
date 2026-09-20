package com.acc.acc;

import android.content.*;
import android.content.pm.ApplicationInfo;

final class SimoClose {
    // An experiment applies only to the selected APP and only until this process exits.
    static volatile String testPackage="",testMode="gesture";
    static String methodFor(String pkg){return pkg.equals(testPackage)?testMode:"gesture";}
    static boolean close(Context c,String pkg){
        return run(c,pkg,methodFor(pkg),message->{SimoVoiceService.lastHit=message;CloseTestLog.add("语音执行",message);});
    }
    static boolean run(Context c,String pkg,String mode,AccessibilityServiceBridge.CloseResult result){
        if(!CloseTestModes.valid(mode)){result.result("未知关闭方式，未执行");return false;}
        if(!allowed(c,pkg,result))return false;
        if("receive_only".equals(mode)){result.result("已收到关闭口令；当前为只记录模式，未执行关闭："+pkg);return true;}
        if("delayed".equals(mode)){
            Context app=c.getApplicationContext();result.result("已匹配关闭，等待 2 秒让语音面板收起，再检查目标窗口");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                android.content.SharedPreferences prefs=app.getSharedPreferences(MainActivity.PREF,0);
                if(!prefs.getBoolean("simo_enabled",false)||!prefs.getBoolean("simo_close_enabled",false)||!"delayed".equals(methodFor(pkg))){result.result("延迟关闭已取消：语音开关或测试方式已改变");return;}
                if(allowed(app,pkg,result))AccessibilityServiceBridge.closeVisibleWindow(pkg,380,result);
            },2000);return true;
        }
        if("back".equals(mode)||"back_twice".equals(mode))return AccessibilityServiceBridge.testBack(pkg,"back_twice".equals(mode),result);
        return AccessibilityServiceBridge.closeVisibleWindow(pkg,"slow".equals(mode)?900:380,result);
    }
    static boolean allowed(Context c,String pkg,AccessibilityServiceBridge.CloseResult result){
        try{ApplicationInfo app=c.getPackageManager().getApplicationInfo(pkg,0);if(pkg.equals(c.getPackageName())||(app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0){result.result("不关闭启动器自身或系统 APP");return false;}}
        catch(Exception e){result.result("目标 APP 未安装或未选择");return false;}return true;
    }
}
