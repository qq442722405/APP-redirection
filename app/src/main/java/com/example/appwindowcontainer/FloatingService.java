package com.example.appwindowcontainer;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
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

/**
 * 车机悬浮快捷窗口：
 * 1. 支持横向/竖向布局；
 * 2. APP 只显示图标，不显示 APP 名称；
 * 3. 返回/首页/菜单是可选项目，不再固定显示；
 * 4. “添加 APP”使用 Overlay 类型 Dialog，避免 Service Context 直接弹普通 Dialog 导致闪退。
 */
public class FloatingService extends Service {
    WindowManager wm; LinearLayout panel; WindowManager.LayoutParams lp;
    final ArrayList<String> floatingPkgs=new ArrayList<>();
    final ArrayList<String> floatingNames=new ArrayList<>();
    final ArrayList<String> actions=new ArrayList<>();
    int downX,downY,startX,startY;
    boolean moved;
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onCreate(){
        super.onCreate();
        loadData();
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel("float","悬浮窗口",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
            Notification n=new Notification.Builder(this,"float").setContentTitle("APP窗口启动器").setContentText("悬浮窗口运行中").setSmallIcon(android.R.drawable.ic_menu_view).build();
            startForeground(31001,n);
        }
        showPanel();
    }

    void loadData(){
        try{
            JSONArray a=new JSONArray(getSharedPreferences(MainActivity.PREF,0).getString("floating_apps","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i); String p=o.optString("pkg","");
                if(!p.isEmpty()){floatingPkgs.add(p);floatingNames.add(o.optString("name",p));}
            }
        }catch(Exception ignored){}
        try{
            JSONArray a=new JSONArray(getSharedPreferences(MainActivity.PREF,0).getString("floating_actions","[]"));
            for(int i=0;i<a.length();i++){String x=a.optString(i,"");if("back".equals(x)||"home".equals(x)||"menu".equals(x))actions.add(x);}
        }catch(Exception ignored){}
    }

    void saveData(){
        try{
            JSONArray a=new JSONArray();
            for(int i=0;i<floatingPkgs.size();i++){JSONObject o=new JSONObject();o.put("pkg",floatingPkgs.get(i));o.put("name",floatingNames.get(i));a.put(o);}
            JSONArray ac=new JSONArray(); for(String x:actions)ac.put(x);
            getSharedPreferences(MainActivity.PREF,0).edit().putString("floating_apps",a.toString()).putString("floating_actions",ac.toString()).apply();
        }catch(Exception ignored){}
    }

    TextView baseButton(String label){
        TextView b=new TextView(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setGravity(Gravity.CENTER);b.setSingleLine(true);
        GradientDrawable g=new GradientDrawable();g.setColor(0xFF343434);g.setCornerRadius(dp(12));b.setBackground(g);return b;
    }

    ImageView iconButton(Drawable icon,String desc){
        ImageView b=new ImageView(this);b.setImageDrawable(icon);b.setContentDescription(desc);b.setPadding(dp(10),dp(10),dp(10),dp(10));
        GradientDrawable g=new GradientDrawable();g.setColor(0xFF343434);g.setCornerRadius(dp(12));b.setBackground(g);return b;
    }

    void showPanel(){
        if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){stopSelf();return;}
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        panel=new LinearLayout(this);
        panel.setOrientation(isVertical()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(5),dp(4),dp(5),dp(4));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xDD202020);bg.setCornerRadius(dp(18));panel.setBackground(bg);

        TextView drag=baseButton("☰");drag.setTextSize(20);
        TextView plus=baseButton("＋");plus.setTextSize(22);
        addChild(drag,dp(48),dp(48)); addChild(plus,dp(48),dp(48));
        plus.setOnClickListener(v->showAddMenu());
        rebuildButtons();

        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        lp=new WindowManager.LayoutParams(-2,-2,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=30;lp.y=180;
        drag.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=(int)e.getRawX();downY=(int)e.getRawY();startX=lp.x;startY=lp.y;moved=false;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){int dx=(int)e.getRawX()-downX,dy=(int)e.getRawY()-downY;if(Math.abs(dx)+Math.abs(dy)>8)moved=true;lp.x=startX+dx;lp.y=startY+dy;wm.updateViewLayout(panel,lp);return true;}
            return true;
        });
        wm.addView(panel,lp);
    }

    boolean isVertical(){return getSharedPreferences(MainActivity.PREF,0).getBoolean("floating_vertical",false);}

    void addChild(View v,int w,int h){panel.addView(v,new LinearLayout.LayoutParams(dp(w),dp(h)));}

    void rebuildButtons(){
        if(panel==null)return;
        while(panel.getChildCount()>2)panel.removeViewAt(2);
        for(int i=0;i<floatingPkgs.size();i++){
            final String pkg=floatingPkgs.get(i);
            ImageView b=null;
            try{b=iconButton(getPackageManager().getApplicationIcon(pkg),floatingNames.get(i));}
            catch(Exception ignored){b=iconButton(getResources().getDrawable(android.R.drawable.sym_def_app_icon),floatingNames.get(i));}
            final ImageView appButton=b;
            appButton.setOnClickListener(v->{Intent in=getPackageManager().getLaunchIntentForPackage(pkg);if(in!=null){in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(in);}catch(Exception ignored){}}});
            appButton.setOnLongClickListener(v->{removeFloatingApp(pkg);return true;});
            addChild(appButton,52,52);
        }
        for(String action:new ArrayList<>(actions)) addSystemButton(action);
    }

    void addSystemButton(String action){
        ImageView b;
        if("back".equals(action)) b=iconButton(getResources().getDrawable(android.R.drawable.ic_media_previous),"返回");
        else if("home".equals(action)) b=iconButton(getResources().getDrawable(android.R.drawable.ic_menu_view),"首页");
        else b=iconButton(getResources().getDrawable(android.R.drawable.ic_menu_more),"菜单");
        b.setOnClickListener(v->globalAction(action));
        addChild(b,52,52);
    }

    void globalAction(String action){
        if("back".equals(action)) AccessibilityServiceBridge.perform(this,1);
        else if("home".equals(action)){AccessibilityServiceBridge.perform(this,2);Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_HOME);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(i);}catch(Exception ignored){}}
        else AccessibilityServiceBridge.perform(this,3);
    }

    void removeFloatingApp(String pkg){int i=floatingPkgs.indexOf(pkg);if(i>=0){floatingPkgs.remove(i);floatingNames.remove(i);saveData();rebuildButtons();}}

    void showAddMenu(){
        String[] items={
                "添加 APP",
                "添加返回按钮",
                "添加首页按钮",
                "添加菜单按钮",
                isVertical()?"切换为横向":"切换为竖向"
        };
        AlertDialog d=new AlertDialog.Builder(this).setTitle("悬浮窗口").setItems(items,(dlg,w)->{
            if(w==0)showApps();
            else if(w==1)addAction("back");
            else if(w==2)addAction("home");
            else if(w==3)addAction("menu");
            else toggleOrientation();
        }).setNegativeButton("关闭",null).create();
        prepareOverlayDialog(d);d.show();
    }

    void addAction(String action){
        if(!actions.contains(action)){actions.add(action);saveData();rebuildButtons();}
        else Toast.makeText(this,"该按钮已经添加",Toast.LENGTH_SHORT).show();
    }

    void toggleOrientation(){
        boolean v=!isVertical();
        getSharedPreferences(MainActivity.PREF,0).edit().putBoolean("floating_vertical",v).apply();
        if(panel!=null){panel.setOrientation(v?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);rebuildButtons();wm.updateViewLayout(panel,lp);}
    }

    /** Service 中直接 show 普通 Dialog 在部分车机上会因 token 问题闪退；给 Dialog 设置 Overlay Window 类型。 */
    void prepareOverlayDialog(Dialog d){
        d.setOnShowListener(x->{
            try{
                Window w=d.getWindow();
                if(w!=null){
                    if(Build.VERSION.SDK_INT>=26)w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                    else w.setType(WindowManager.LayoutParams.TYPE_PHONE);
                    w.setDimAmount(0.45f);
                }
            }catch(Exception ignored){}
        });
    }

    void showApps(){
        final Dialog dialog=new Dialog(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),dp(8),dp(8),dp(8));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xF0202020);bg.setCornerRadius(dp(14));box.setBackground(bg);
        TextView title=baseButton("选择 APP（仅显示图标）");title.setTextSize(16);box.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("搜索 APP");search.setTextColor(Color.WHITE);search.setHintTextColor(Color.GRAY);box.addView(search,new LinearLayout.LayoutParams(-1,dp(44)));
        ScrollView sv=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);sv.addView(rows);box.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> list=new ArrayList<>();
        for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(!ai.packageName.equals(getPackageName())&&pm.getLaunchIntentForPackage(ai.packageName)!=null)list.add(ai);
        }
        Collections.sort(list,(a,b)->pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        Runnable refresh=()->{
            rows.removeAllViews();String q=search.getText().toString().trim().toLowerCase();int count=0;
            for(ApplicationInfo ai:list){
                String name=pm.getApplicationLabel(ai).toString();if(!q.isEmpty()&&!name.toLowerCase().contains(q))continue;
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);row.setPadding(dp(8),dp(5),dp(8),dp(5));row.setBackgroundResource(R.drawable.card);
                ImageView icon=new ImageView(this);try{icon.setImageDrawable(pm.getApplicationIcon(ai));}catch(Exception ignored){}
                row.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));
                row.setContentDescription(name);
                row.setOnClickListener(v->{
                    if(!floatingPkgs.contains(ai.packageName)){floatingPkgs.add(ai.packageName);floatingNames.add(name);saveData();rebuildButtons();}
                    dialog.dismiss();
                });
                rows.addView(row,new LinearLayout.LayoutParams(-1,dp(62)));
                if(++count>=100)break;
            }
        };
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(android.text.Editable e){}});
        dialog.setContentView(box);
        prepareOverlayDialog(dialog);
        dialog.setOnShowListener(x->{
            try{Window w=dialog.getWindow();if(w!=null){if(Build.VERSION.SDK_INT>=26)w.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);else w.setType(WindowManager.LayoutParams.TYPE_PHONE);WindowManager.LayoutParams a=w.getAttributes();a.width=dp(360);a.height=dp(520);w.setAttributes(a);}}catch(Exception ignored){}
            refresh.run();
        });
        try{dialog.show();}catch(Exception e){Toast.makeText(this,"无法打开 APP 列表，请检查悬浮窗权限",Toast.LENGTH_SHORT).show();}
    }

    @Override public void onDestroy(){try{if(wm!=null&&panel!=null)wm.removeView(panel);}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
