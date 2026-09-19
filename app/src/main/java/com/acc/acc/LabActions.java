package com.acc.acc;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import java.lang.reflect.Method;

final class LabActions {
    static boolean targetAllowed(Context c,String pkg){
        try{ApplicationInfo info=c.getPackageManager().getApplicationInfo(pkg,0);return !pkg.equals(c.getPackageName())&&(info.flags&ApplicationInfo.FLAG_SYSTEM)==0&&c.getPackageManager().getLaunchIntentForPackage(pkg)!=null;}catch(Exception e){return false;}
    }
    static boolean run(Context c,String action){
        VoiceCommands.App app=LabSession.target;
        if(!LabSession.active||app==null||!targetAllowed(c,app.pkg)){LabLog.write("REJECT","请先选择已安装的第三方 APP");return false;}
        if(action.equals("close"))action=LabSession.closeMethod;
        String id=LabSession.begin(action,app.pkg);int generation=LabSession.generation;
        try{
            if(action.equals("back1")||action.equals("back2")){
                boolean twice=action.equals("back2");
                if(AccessibilityServiceBridge.isTargetForeground(app.pkg))back(id,generation,app.pkg,twice);
                else{
                    Intent launch=c.getPackageManager().getLaunchIntentForPackage(app.pkg);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(launch);
                    LabLog.write(id,"已请求打开目标 APP，最多等待4秒前台事件后才发送返回");
                    awaitForeground(id,generation,app.pkg,twice,SystemClock.elapsedRealtime()+4000);
                }
            }else if(action.equals("kill")){
                ((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).killBackgroundProcesses(app.pkg);
                LabLog.write(id,"API_RETURN 后台进程清理已请求；不代表前台 APP 已关闭，也不阻止重启");
            }else if(action.equals("force")){
                ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
                Method force=ActivityManager.class.getMethod("forceStopPackage",String.class);force.invoke(am,app.pkg);
                LabLog.write(id,"API_RETURN 强制停止调用返回，需人工确认；普通安装通常被系统拒绝");
            }else if(action.equals("details")){
                c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));LabLog.write(id,"已打开系统 APP 信息，需手动选择强行停止");
            }else if(action.equals("embed")){
                c.startActivity(new Intent(c,LabEmbedActivity.class).putExtra("pkg",app.pkg).putExtra("test",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }else{
                boolean free=action.equals("freeform");String key=free||action.equals("bounds")?"custom":action;
                if(!key.equals("custom")&&!key.equals("left")&&!key.equals("center")&&!key.equals("right")&&!key.equals("rear"))throw new IllegalArgumentException("未知测试操作");
                LabProfile profile=LabProfile.get(c,key);profile.validate(c);LabLog.write(id,"PARAM "+profile+" freeform="+free);
                ActivityOptions options=ActivityOptions.makeBasic();options.setLaunchDisplayId(profile.displayId);options.setLaunchBounds(profile.bounds());
                if(free)ActivityOptions.class.getMethod("setLaunchWindowingMode",int.class).invoke(options,5);
                Intent launch=c.getPackageManager().getLaunchIntentForPackage(app.pkg);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                if(Build.VERSION.SDK_INT>=29&&!((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).isActivityStartAllowedOnDisplay(c,profile.displayId,launch))throw new SecurityException("系统不允许向此 Display 启动目标 APP");
                c.startActivity(launch,options.toBundle());LabLog.write(id,"API_RETURN 区域启动请求已提交；系统可能复用原任务或忽略窗口参数，请人工确认位置");
            }
            return true;
        }catch(Exception e){LabLog.write(id,"ERROR "+LabLog.error(e));return false;}
    }
    private static void awaitForeground(String id,int g,String pkg,boolean twice,long deadline){
        LabSession.main.postDelayed(()->{
            if(!LabSession.valid(g,pkg)){LabLog.write(id,"CANCEL 测试已结束、目标已更换或有新测试");return;}
            if(AccessibilityServiceBridge.isTargetForeground(pkg))back(id,g,pkg,twice);
            else if(SystemClock.elapsedRealtime()<deadline)awaitForeground(id,g,pkg,twice,deadline);
            else LabLog.write(id,"ERROR 未确认目标 APP 位于前台；没有发送返回。检查无障碍服务/后台启动限制");
        },250);
    }
    private static void back(String id,int g,String pkg,boolean twice){
        if(!LabSession.valid(g,pkg))return;
        boolean sent=AccessibilityServiceBridge.performBackForTarget(pkg,false);LabLog.write(id,"BACK1 "+(sent?"已发送返回请求":"未发送：目标不在前台或服务不可用"));
        if(twice&&sent)LabSession.main.postDelayed(()->{
            if(!LabSession.valid(g,pkg)){LabLog.write(id,"CANCEL 第二次返回已取消");return;}
            LabLog.write(id,"BACK2 "+(AccessibilityServiceBridge.performBackForTarget(pkg,false)?"已发送返回请求":"跳过：目标已不在前台"));
        },600);
    }
}
