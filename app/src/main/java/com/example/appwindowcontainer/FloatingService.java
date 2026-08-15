package com.example.appwindowcontainer;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

public class FloatingService extends Service {
    WindowManager wm;
    LinearLayout panel;
    int downX,downY,startX,startY;
    WindowManager.LayoutParams lp;
    final ArrayList<String> floatingPkgs=new ArrayList<>();
    final ArrayList<String> floatingNames=new ArrayList<>();

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onCreate(){
        super.onCreate();
        loadFloatingApps();
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel("float","悬浮窗口",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
            Notification n=new Notification.Builder(this,"float")
                    .setContentTitle("APP窗口容器").setContentText("悬浮窗口运行中")
                    .setSmallIcon(android.R.drawable.ic_menu_view).build();
            startForeground(31001,n);
        }
        showPanel();
    }

    void loadFloatingApps(){
        try{
            JSONArray a=new JSONArray(getSharedPreferences(MainActivity.PREF,0).getString("floating_apps","[]"));
            for(int i=0;i<a.length();i++){
                String pkg=a.getJSONObject(i).optString("pkg","");
                if(!pkg.isEmpty()&&!floatingPkgs.contains(pkg)){
                    floatingPkgs.add(pkg);
                    floatingNames.add(a.getJSONObject(i).optString("name",pkg));
                }
            }
        }catch(Exception ignored){}
    }

    void saveFloatingApps(){
        JSONArray a=new JSONArray();
        try{
            for(int i=0;i<floatingPkgs.size();i++){
                JSONObject o=new JSONObject(); o.put("pkg",floatingPkgs.get(i)); o.put("name",floatingNames.get(i)); a.put(o);
            }
        }catch(Exception ignored){}
        getSharedPreferences(MainActivity.PREF,0).edit().putString("floating_apps",a.toString()).apply();
    }

    TextView appButton(String name){
        TextView b=new TextView(this); b.setText(name); b.setTextColor(Color.WHITE); b.setTextSize(11); b.setGravity(Gravity.CENTER);
        b.setSingleLine(true); b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFF343434); bg.setCornerRadius(dp(12)); b.setBackground(bg);
        return b;
    }

    void showPanel(){
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){stopSelf();return;}
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        panel=new LinearLayout(this); panel.setOrientation(LinearLayout.HORIZONTAL); panel.setGravity(Gravity.CENTER_VERTICAL); panel.setPadding(dp(6),dp(4),dp(6),dp(4));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xDD202020); bg.setCornerRadius(dp(18)); panel.setBackground(bg);

        TextView plus=new TextView(this); plus.setText("+"); plus.setTextColor(Color.WHITE); plus.setTextSize(24); plus.setGravity(Gravity.CENTER);
        panel.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(48)));
        plus.setOnClickListener(v->showApps());
        rebuildButtons();

        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        lp=new WindowManager.LayoutParams(-2,dp(58),type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT; lp.x=30; lp.y=180;
        panel.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=(int)e.getRawX();downY=(int)e.getRawY();startX=lp.x;startY=lp.y;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){lp.x=startX+(int)e.getRawX()-downX;lp.y=startY+(int)e.getRawY()-downY;wm.updateViewLayout(panel,lp);return true;}
            return true;
        });
        wm.addView(panel,lp);
    }

    void rebuildButtons(){
        if(panel==null)return;
        while(panel.getChildCount()>1) panel.removeViewAt(1);
        for(int i=0;i<floatingPkgs.size();i++){
            final String pkg=floatingPkgs.get(i);
            TextView b=appButton(floatingNames.get(i));
            b.setOnClickListener(v->{
                Intent in=getPackageManager().getLaunchIntentForPackage(pkg);
                if(in!=null){in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(in);}
            });
            b.setOnLongClickListener(v->{removeFloatingApp(pkg);return true;});
            panel.addView(b,new LinearLayout.LayoutParams(dp(88),dp(48)));
        }
    }

    void removeFloatingApp(String pkg){
        int i=floatingPkgs.indexOf(pkg); if(i>=0){floatingPkgs.remove(i);floatingNames.remove(i);saveFloatingApps();rebuildButtons();}
    }

    void showApps(){
        final PopupWindow pw=new PopupWindow(this);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8),dp(8),dp(8),dp(8));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xF0202020); bg.setCornerRadius(dp(12)); box.setBackground(bg);
        TextView title=new TextView(this); title.setText("选择 APP 添加到悬浮窗口"); title.setTextColor(Color.WHITE); title.setTextSize(16); title.setGravity(Gravity.CENTER_VERTICAL); box.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));
        ScrollView sv=new ScrollView(this); LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); sv.addView(rows); box.addView(sv,new LinearLayout.LayoutParams(dp(300),dp(380)));
        PackageManager pm=getPackageManager(); ArrayList<android.content.pm.ApplicationInfo> list=new ArrayList<>();
        for(android.content.pm.ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)) if(!ai.packageName.equals(getPackageName())&&pm.getLaunchIntentForPackage(ai.packageName)!=null) list.add(ai);
        Collections.sort(list,(a,b)->pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        int count=0;
        for(android.content.pm.ApplicationInfo ai:list){
            String name=pm.getApplicationLabel(ai).toString(); Button b=new Button(this); b.setText(name); b.setAllCaps(false); b.setTextSize(13);
            b.setOnClickListener(v->{
                if(!floatingPkgs.contains(ai.packageName)){floatingPkgs.add(ai.packageName);floatingNames.add(name);saveFloatingApps();rebuildButtons();}
                pw.dismiss();
            });
            rows.addView(b,new LinearLayout.LayoutParams(-1,dp(48))); if(++count>=50)break;
        }
        pw.setContentView(box); pw.setWidth(dp(320)); pw.setHeight(dp(450)); pw.setBackgroundDrawable(bg); pw.setOutsideTouchable(true); pw.setFocusable(false);
        pw.showAtLocation(panel,Gravity.TOP|Gravity.LEFT,lp.x,lp.y+dp(64));
    }

    @Override public void onDestroy(){try{if(wm!=null&&panel!=null)wm.removeView(panel);}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
