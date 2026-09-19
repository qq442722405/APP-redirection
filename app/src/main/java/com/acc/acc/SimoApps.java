package com.acc.acc;

import android.content.*;
import android.content.pm.*;
import java.util.*;
import org.json.*;

final class SimoApps {
    static List<VoiceCommands.App> list(Context context,boolean includeExcluded){
        SharedPreferences p=context.getSharedPreferences(MainActivity.PREF,0);PackageManager pm=context.getPackageManager();
        Set<String> packages=new LinkedHashSet<>();
        if(p.getBoolean("simo_all_apps",false)){
            for(ApplicationInfo app:pm.getInstalledApplications(0))if((app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)packages.add(app.packageName);
        }else try{
            JSONArray apps=new JSONArray(p.getString(MainActivity.APPS,"[]"));
            for(int i=0;i<apps.length();i++)packages.add(apps.getString(i));
        }catch(JSONException ignored){}
        ArrayList<VoiceCommands.App> result=new ArrayList<>();
        for(String pkg:packages){
            if(pkg.equals(context.getPackageName())||pkg.startsWith("action:")||pkg.isEmpty())continue;
            if(!includeExcluded&&p.getBoolean("simo_exclude_"+pkg,false))continue;
            try{if(pm.getLaunchIntentForPackage(pkg)!=null)result.add(new VoiceCommands.App(pkg,pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)).toString(),p.getString("simo_alias_"+pkg,"")));}catch(PackageManager.NameNotFoundException ignored){}
        }
        Collections.sort(result,(x,y)->x.name.compareToIgnoreCase(y.name));return result;
    }
}
