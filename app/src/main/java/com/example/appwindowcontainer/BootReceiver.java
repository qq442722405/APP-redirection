package com.example.appwindowcontainer;

import android.app.ActivityOptions;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import org.json.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences p=context.getSharedPreferences(MainActivity.PREF,Context.MODE_PRIVATE);
        if(!p.getBoolean("auto_start_enabled",false)) return;
        try{
            JSONArray arr=new JSONArray(p.getString("auto_start_items","[]"));
            if(arr.length()==0)return;
            int interval=Math.max(1,p.getInt("auto_start_interval",1));
            int bootDelay=Math.max(0,p.getInt("boot_delay_sec",5));
            PendingResult result=goAsync();
            Handler h=new Handler(Looper.getMainLooper());
            for(int i=0;i<arr.length();i++){
                final JSONObject item=arr.getJSONObject(i);
                final long delay=((long)bootDelay+(long)i*interval)*1000L;
                h.postDelayed(()->launchOne(context,p,item),delay);
            }
            h.postDelayed(result::finish,((long)bootDelay+(long)arr.length()*interval)*1000L+3000L);
        }catch(Exception ignored){}
    }
    void launchOne(Context context,SharedPreferences p,JSONObject item){
        try{
            String pkg=item.optString("pkg",""); if(pkg.isEmpty())return;
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(pkg); if(launch==null)return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            int presetIndex=item.optInt("preset",-1);
            if(presetIndex>=0){
                JSONArray presets=new JSONArray(p.getString(MainActivity.PRESETS,"[]"));
                if(presetIndex<presets.length()){
                    JSONObject pr=presets.getJSONObject(presetIndex); int mode=pr.optInt("mode",1);
                    int x=Math.max(0,pr.optInt("x",0)),y=Math.max(0,pr.optInt("y",0));
                    int w=Math.max(1,pr.optInt("w",1)),h=Math.max(1,pr.optInt("h",1));
                    launch.putExtra("com.example.appwindowcontainer.target_x",x);launch.putExtra("com.example.appwindowcontainer.target_y",y);launch.putExtra("com.example.appwindowcontainer.target_w",w);launch.putExtra("com.example.appwindowcontainer.target_h",h);launch.putExtra("com.example.appwindowcontainer.fullscreen",mode==6);
                    if(Build.VERSION.SDK_INT>=24){
                        ActivityOptions options=ActivityOptions.makeBasic();
                        if(mode==6){
                            android.util.DisplayMetrics dm=context.getResources().getDisplayMetrics(); options.setLaunchBounds(new Rect(0,0,dm.widthPixels,dm.heightPixels));
                        }else options.setLaunchBounds(new Rect(x,y,x+w,y+h));
                        try{context.startActivity(launch,options.toBundle());return;}catch(Exception ignored){}
                    }
                }
            }
            context.startActivity(launch);
        }catch(Exception ignored){}
    }
}
