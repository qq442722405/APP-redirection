package com.acc.acc;

import android.content.*;
import android.content.pm.ApplicationInfo;

final class SimoClose {
    static boolean close(Context c,String pkg){
        try{ApplicationInfo app=c.getPackageManager().getApplicationInfo(pkg,0);if(pkg.equals(c.getPackageName())||(app.flags&ApplicationInfo.FLAG_SYSTEM)!=0){SimoVoiceService.lastHit="不关闭启动器自身或系统 APP";return false;}}
        catch(Exception e){SimoVoiceService.lastHit="目标 APP 已卸载";return false;}
        return AccessibilityServiceBridge.closeVisibleWindow(pkg,message->SimoVoiceService.lastHit=message);
    }
}
