package com.acc.acc;

import android.app.ActivityOptions;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import org.json.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences p=context.getSharedPreferences(MainActivity.PREF,Context.MODE_PRIVATE);
        if(p.getBoolean("simo_enabled",false)&&p.getBoolean("simo_boot",false))SimoVoiceService.sync(context);
        boolean appBoot=p.getBoolean("app_boot_enabled",false);
        boolean taskBoot=p.getBoolean("auto_start_enabled",false);
        try{
            JSONArray arr=new JSONArray(p.getString("auto_start_items","[]"));
            int interval=Math.max(1,p.getInt("auto_start_interval",1));
            int bootDelay=Math.max(0,p.getInt("boot_delay_seconds",0));
            if(!appBoot && (!taskBoot || arr.length()==0)) return;
            PendingResult result=goAsync();
            Handler h=new Handler(Looper.getMainLooper());

            // 用户开启“本 APP 开机启动”时，先启动启动器自身。
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
            String pkg=item.optString("pkg","");if(pkg.isEmpty())return;
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(pkg);if(launch==null)return;
            int index=item.optInt("preset",-1);
            if(index>=0){
                JSONObject preset=new JSONArray(p.getString(MainActivity.PRESETS,"[]")).optJSONObject(index);
                if(preset==null)throw new IllegalArgumentException("开机任务的窗口预设已失效");
                android.graphics.Rect bounds=WindowLaunch.start(context,launch,preset.optInt("x"),preset.optInt("y"),preset.optInt("w"),preset.optInt("h"),preset.optInt("displayId",-1),preset.optInt("mode",1)==6,true);
                JSONObject saved=new JSONObject(p.getString("app_last_bounds","{}")),value=new JSONObject();
                value.put("x",bounds.left);value.put("y",bounds.top);value.put("w",bounds.width());value.put("h",bounds.height());
                value.put("displayId",preset.optInt("displayId",-1));value.put("fullscreen",preset.optInt("mode",1)==6);saved.put(pkg,value);
                p.edit().putString("app_last_bounds",saved.toString()).apply();
            }else{WindowLaunch.prepare(launch,false);context.startActivity(launch);}
        }catch(Exception e){android.util.Log.w("WindowLaunch","开机任务未启动："+e.getMessage());}
    }
}
