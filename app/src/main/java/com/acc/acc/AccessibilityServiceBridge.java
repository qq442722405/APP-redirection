package com.acc.acc;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Context;
import android.content.Intent;
import android.app.ActivityOptions;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 无障碍桥接：负责当前前台 APP 检测，以及“关闭 APP 后重新打开”时执行自动任务。
 *
 * 逻辑与主界面双击启动完全分离：
 * - 主界面双击：普通启动；
 * - 自动任务：只由开机任务或本服务检测到目标 APP 重新进入前台时触发。
 */
public class AccessibilityServiceBridge extends AccessibilityService {
    private static AccessibilityServiceBridge instance;
    private static String currentPackageName;
    private static String lastForegroundPackage;
    private static long lastAutoLaunchAt;
    private static String lastAutoLaunchPackage;
    private static final long AUTO_RELAUNCH_COOLDOWN_MS=5000L;

    @Override public void onServiceConnected(){
        instance=this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null) return;
        CharSequence cs=event.getPackageName();
        if(cs==null) return;
        String pkg=cs.toString();
        if(pkg.length()==0) return;

        boolean foregroundEvent=
                event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        currentPackageName=pkg;

        if(foregroundEvent && !pkg.equals(lastForegroundPackage)){
            lastForegroundPackage=pkg;
            checkAndRunAutoTask(pkg);
        }
    }

    private void checkAndRunAutoTask(String pkg){
        if(instance==null || pkg==null || pkg.equals(getPackageName())) return;
        long now=System.currentTimeMillis();
        if(pkg.equals(lastAutoLaunchPackage) && now-lastAutoLaunchAt<AUTO_RELAUNCH_COOLDOWN_MS) return;

        try{
            android.content.SharedPreferences p=getSharedPreferences(MainActivity.PREF,Context.MODE_PRIVATE);
            if(!p.getBoolean("auto_start_enabled",false)) return;
            JSONArray arr=new JSONArray(p.getString("auto_start_items","[]"));
            JSONObject task=null;
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.optJSONObject(i);
                if(o!=null && pkg.equals(o.optString("pkg",""))){ task=o; break; }
            }
            if(task==null) return;

            // 自动任务优先使用保存时的预设快照；兼容旧任务的 preset 下标。
            int x=0,y=0,w=1,h=1,displayId=-1,mode=1;
            boolean hasPreset=false;
            if(task.has("preset_x") && task.has("preset_y") && task.has("preset_w") && task.has("preset_h")){
                x=task.optInt("preset_x",0);
                y=task.optInt("preset_y",0);
                w=task.optInt("preset_w",1);
                h=task.optInt("preset_h",1);
                displayId=task.optInt("preset_displayId",-1);
                mode=task.optInt("preset_mode",1);
                hasPreset=true;
            }else{
                int pi=task.optInt("preset",-1);
                if(pi>=0){
                    JSONArray presets=new JSONArray(p.getString(MainActivity.PRESETS,"[]"));
                    if(pi<presets.length()){
                        JSONObject pr=presets.optJSONObject(pi);
                        if(pr!=null){
                            x=pr.optInt("x",0); y=pr.optInt("y",0);
                            w=pr.optInt("w",1); h=pr.optInt("h",1);
                            displayId=pr.optInt("displayId",-1);
                            mode=pr.optInt("mode",1);
                            hasPreset=true;
                        }
                    }
                }
            }

            Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch==null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            if(!hasPreset){
                lastAutoLaunchPackage=pkg;
                lastAutoLaunchAt=now;
                try{startActivity(launch);}catch(Exception ignored){}
                return;
            }

            android.graphics.Point real=getRealScreenSize();
            int screenW=Math.max(1,real.x), screenH=Math.max(1,real.y);
            int left=Math.max(0,Math.min(x,screenW-1));
            int top=Math.max(0,Math.min(y,screenH-1));
            int right=Math.max(left+1,Math.min(x+Math.max(1,w),screenW));
            int bottom=Math.max(top+1,Math.min(y+Math.max(1,h),screenH));
            boolean fullscreen=mode==6;
            if(fullscreen){left=0;top=0;right=screenW;bottom=screenH;}

            launch.putExtra("com.acc.acc.target_x",left);
            launch.putExtra("com.acc.acc.target_y",top);
            launch.putExtra("com.acc.acc.target_w",right-left);
            launch.putExtra("com.acc.acc.target_h",bottom-top);
            launch.putExtra("com.acc.acc.target_display_id",displayId);
            launch.putExtra("com.acc.acc.fullscreen",fullscreen);

            ActivityOptions options=ActivityOptions.makeBasic();
            options.setLaunchBounds(new Rect(left,top,right,bottom));
            if(Build.VERSION.SDK_INT>=26 && displayId>=0){
                try{options.setLaunchDisplayId(displayId);}catch(Exception ignored){}
            }

            lastAutoLaunchPackage=pkg;
            lastAutoLaunchAt=now;
            try{
                startActivity(launch,options.toBundle());
            }catch(Exception e){
                try{startActivity(launch);}catch(Exception ignored){}
            }
        }catch(Exception ignored){}
    }

    private android.graphics.Point getRealScreenSize(){
        try{
            android.view.WindowManager wm=(android.view.WindowManager)getSystemService(android.content.Context.WINDOW_SERVICE);
            android.view.Display d=wm.getDefaultDisplay();
            android.graphics.Point p=new android.graphics.Point();
            d.getRealSize(p);
            return p;
        }catch(Exception e){
            android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
            return new android.graphics.Point(Math.max(1,dm.widthPixels),Math.max(1,dm.heightPixels));
        }
    }

    @Override public void onInterrupt(){}
    @Override public void onDestroy(){
        if(instance==this){instance=null;currentPackageName=null;lastForegroundPackage=null;}
        super.onDestroy();
    }
    public static String getCurrentPackage(){return currentPackageName;}
    public static boolean isTargetForeground(String pkg){return instance!=null && pkg!=null && pkg.equals(currentPackageName);}
    public static boolean performBackForTarget(String pkg, boolean close){
        if(!isTargetForeground(pkg) || Build.VERSION.SDK_INT<16)return false;
        performBackThen(close);
        return true;
    }
    public static void perform(Context c,int action){
        if(instance==null)return;
        if(action==1 && Build.VERSION.SDK_INT>=16) instance.performGlobalAction(GLOBAL_ACTION_BACK);
        else if(action==2 && Build.VERSION.SDK_INT>=16) instance.performGlobalAction(GLOBAL_ACTION_HOME);
        else if(action==3 && Build.VERSION.SDK_INT>=16) instance.performGlobalAction(GLOBAL_ACTION_RECENTS);
    }
    public static void performBackThen(boolean close){
        if(instance==null || Build.VERSION.SDK_INT<16)return;
        instance.performGlobalAction(GLOBAL_ACTION_BACK);
        if(close){
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                if(instance!=null)instance.performGlobalAction(GLOBAL_ACTION_BACK);
            },220);
        }
    }
}
