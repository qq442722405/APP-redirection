package com.acc.acc;

import android.app.ActivityOptions;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.util.DisplayMetrics;
import org.json.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences p=context.getSharedPreferences(MainActivity.PREF,Context.MODE_PRIVATE);
        boolean appBoot=p.getBoolean("app_boot_enabled",false);
        boolean taskBoot=p.getBoolean("auto_start_enabled",false);
        try{
            JSONArray arr=new JSONArray(p.getString("auto_start_items","[]"));
            int interval=Math.max(1,p.getInt("auto_start_interval",1));
            int bootDelay=Math.max(0,p.getInt("boot_delay_seconds",0));
            if(!appBoot && (!taskBoot || arr.length()==0)) return;
            PendingResult result=goAsync();
            Handler h=new Handler(Looper.getMainLooper());

            if(appBoot){
                h.postDelayed(()->{
                    try{
                        Intent main=new Intent(context,MainActivity.class);
                        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(main);
                    }catch(Exception ignored){}
                },(long)bootDelay*1000L);
            }

            if(taskBoot && arr.length()>0){
                for(int i=0;i<arr.length();i++){
                    final JSONObject item=arr.getJSONObject(i);
                    final long delay=(long)bootDelay*1000L+(long)i*interval*1000L;
                    h.postDelayed(()->launchOne(context,p,item),delay);
                }
            }
            long finishDelay=(long)bootDelay*1000L + (taskBoot?((long)arr.length()*interval*1000L):0L) + 3000L;
            h.postDelayed(result::finish,finishDelay);
        }catch(Exception ignored){}
    }

    void launchOne(Context context,SharedPreferences p,JSONObject item){
        try{
            String pkg=item.optString("pkg","");
            if(pkg.isEmpty()) return;
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch==null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

            int x=-1,y=-1,w=-1,h=-1,displayId=-1,mode=1;
            boolean hasPreset=false;

            // 优先使用创建自动任务时保存的预设快照，避免预设后来移动/删除导致下标变化。
            if(item.has("preset_x") && item.has("preset_y") && item.has("preset_w") && item.has("preset_h")){
                x=item.optInt("preset_x",0);
                y=item.optInt("preset_y",0);
                w=item.optInt("preset_w",1);
                h=item.optInt("preset_h",1);
                displayId=item.optInt("preset_displayId",-1);
                mode=item.optInt("preset_mode",1);
                hasPreset=true;
            }else{
                // 兼容旧版本已经保存的自动任务。
                int presetIndex=item.optInt("preset",-1);
                if(presetIndex>=0){
                    JSONArray presets=new JSONArray(p.getString(MainActivity.PRESETS,"[]"));
                    if(presetIndex<presets.length()){
                        JSONObject pr=presets.getJSONObject(presetIndex);
                        x=pr.optInt("x",0); y=pr.optInt("y",0);
                        w=pr.optInt("w",1); h=pr.optInt("h",1);
                        displayId=pr.optInt("displayId",-1);
                        mode=pr.optInt("mode",1);
                        hasPreset=true;
                    }
                }
            }

            if(!hasPreset){
                context.startActivity(launch);
                return;
            }

            DisplayMetrics dm=context.getResources().getDisplayMetrics();
            int screenW=Math.max(1,dm.widthPixels);
            int screenH=Math.max(1,dm.heightPixels);
            boolean fullscreen=(mode==6);
            int left=Math.max(0,Math.min(x,screenW-1));
            int top=Math.max(0,Math.min(y,screenH-1));
            int right=Math.max(left+1,Math.min(x+Math.max(1,w),screenW));
            int bottom=Math.max(top+1,Math.min(y+Math.max(1,h),screenH));
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
            try{
                context.startActivity(launch,options.toBundle());
            }catch(Exception e){
                // 某些车机对后台 ActivityOptions 限制较严，至少再次带着窗口参数启动。
                try{context.startActivity(launch);}catch(Exception ignored){}
            }
        }catch(Exception ignored){}
    }
}
