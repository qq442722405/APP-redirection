package com.example.appwindowcontainer;

import android.content.*;
import android.app.*;
import android.os.*;
import android.content.pm.PackageManager;
import org.json.*;

public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
   if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
   android.content.SharedPreferences p=c.getSharedPreferences(MainActivity.PREF,0);
   if(!p.getBoolean("auto_start_enabled",false)) return;
   try{
     JSONArray a=new JSONArray(p.getString("auto_start_items","[]"));
     if(a.length()==0)return;
     JSONObject o=a.getJSONObject(0); String pkg=o.getString("pkg");
     Intent launch=c.getPackageManager().getLaunchIntentForPackage(pkg); if(launch==null)return;
     launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
     c.startActivity(launch);
   }catch(Exception ignored){}
 }
}
