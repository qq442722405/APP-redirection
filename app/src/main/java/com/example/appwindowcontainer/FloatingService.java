package com.example.appwindowcontainer;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

public class FloatingService extends Service {
    WindowManager wm; LinearLayout panel; WindowManager.LayoutParams lp;
    final ArrayList<String> floatingPkgs=new ArrayList<>();
    final ArrayList<String> floatingNames=new ArrayList<>();
    int downX,downY,startX,startY; boolean moved;
    boolean vertical;
    boolean addBack=false, addHome=false, addMenu=false;
    final int ACTION_BACK=1,ACTION_HOME=2,ACTION_MENU=3;

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onCreate(){
        super.onCreate();
        loadFloatingApps();
        android.content.SharedPreferences p=getSharedPreferences(MainActivity.PREF,0);
        vertical=p.getBoolean("floating_vertical",false);
        addBack=p.getBoolean("floating_back",false);
        addHome=p.getBoolean("floating_home",false);
        addMenu=p.getBoolean("floating_menu",false);
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel("float","悬浮窗口",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
            Notification n=new Notification.Builder(this,"float").setContentTitle("APP窗口启动器").setContentText("悬浮窗口运行中").setSmallIcon(android.R.drawable.ic_menu_view).build();
            startForeground(31001,n);
        }
        showPanel();
    }

    void loadFloatingApps(){
        try{
            JSONArray a=new JSONArray(getSharedPreferences(MainActivity.PREF,0).getString("floating_apps","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i); String p=o.optString("pkg","");
                if(!p.isEmpty() && getPackageManager().getLaunchIntentForPackage(p)!=null){
                    floatingPkgs.add(p); floatingNames.add(o.optString("name",p));
                }
            }
        }catch(Exception ignored){}
    }
    void saveFloatingApps(){
        try{
            JSONArray a=new JSONArray();
            for(int i=0;i<floatingPkgs.size();i++){JSONObject o=new JSONObject();o.put("pkg",floatingPkgs.get(i));o.put("name",floatingNames.get(i));a.put(o);}
            getSharedPreferences(MainActivity.PREF,0).edit().putString("floating_apps",a.toString()).apply();
        }catch(Exception ignored){}
    }
    TextView baseButton(String label){
        TextView b=new TextView(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setGravity(Gravity.CENTER);b.setSingleLine(true);
        GradientDrawable g=new GradientDrawable();g.setColor(0xFF343434);g.setCornerRadius(dp(12));b.setBackground(g);return b;
    }
    ImageButton iconButton(int icon){
        ImageButton b=new ImageButton(this); b.setImageResource(icon); b.setColorFilter(Color.WHITE); b.setBackgroundResource(R.drawable.button); b.setPadding(dp(10),dp(10),dp(10),dp(10)); return b;
    }

    void showPanel(){
        if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){stopSelf();return;}
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        panel=new LinearLayout(this);
        panel.setOrientation(vertical?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(5),dp(4),dp(5),dp(4));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xDD202020);bg.setCornerRadius(dp(18));panel.setBackground(bg);

        TextView drag=baseButton("☰"); drag.setTextSize(20);
        TextView plus=baseButton("＋"); plus.setTextSize(22);
        addView(drag,dp(48),dp(48)); addView(plus,dp(48),dp(48));
        plus.setOnClickListener(v->showAddMenu());
        rebuildButtons();

        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        lp=new WindowManager.LayoutParams(-2,-2,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT; lp.x=30; lp.y=180;
        drag.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=(int)e.getRawX();downY=(int)e.getRawY();startX=lp.x;startY=lp.y;moved=false;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){int dx=(int)e.getRawX()-downX,dy=(int)e.getRawY()-downY;if(Math.abs(dx)+Math.abs(dy)>8)moved=true;lp.x=startX+dx;lp.y=startY+dy;try{wm.updateViewLayout(panel,lp);}catch(Exception ignored){}return true;}
            return true;
        });
        wm.addView(panel,lp);
    }
    void addView(View v,int w,int h){panel.addView(v,new LinearLayout.LayoutParams(dp(w),dp(h)));}

    void rebuildButtons(){
        if(panel==null)return;
        while(panel.getChildCount()>2)panel.removeViewAt(2);
        for(int i=0;i<floatingPkgs.size();i++){
            final String pkg=floatingPkgs.get(i);
            ImageButton b=iconButton(android.R.drawable.sym_def_app_icon);
            try{b.setImageDrawable(getPackageManager().getApplicationIcon(pkg));}catch(Exception ignored){}
            b.setContentDescription(floatingNames.get(i));
            b.setOnClickListener(v->{Intent in=getPackageManager().getLaunchIntentForPackage(pkg);if(in!=null){in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(in);}catch(Exception ignored){}}});
            b.setOnLongClickListener(v->{removeFloatingApp(pkg);return true;});
            addView(b,52,52);
        }
        if(addBack){ImageButton b=iconButton(android.R.drawable.ic_menu_revert);b.setContentDescription("返回");b.setOnClickListener(v->globalAction(ACTION_BACK));addView(b,52,52);}
        if(addHome){ImageButton b=iconButton(android.R.drawable.ic_menu_view);b.setContentDescription("首页");b.setOnClickListener(v->globalAction(ACTION_HOME));addView(b,52,52);}
        if(addMenu){ImageButton b=iconButton(android.R.drawable.ic_menu_more);b.setContentDescription("菜单");b.setOnClickListener(v->globalAction(ACTION_MENU));addView(b,52,52);}
    }

    void globalAction(int action){
        AccessibilityServiceBridge.perform(this,action);
        if(action==ACTION_HOME){Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_HOME);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(i);}catch(Exception ignored){}}
    }
    void removeFloatingApp(String pkg){int i=floatingPkgs.indexOf(pkg);if(i>=0){floatingPkgs.remove(i);floatingNames.remove(i);saveFloatingApps();rebuildButtons();}}

    void showAddMenu(){
        String[] items={"添加 APP","添加返回按钮","添加首页按钮","添加菜单按钮","切换悬浮窗方向"};
        new AlertDialog.Builder(this).setTitle("悬浮窗口").setItems(items,(d,w)->{
            if(w==0)showApps();
            else if(w==1){addBack=!addBack;saveButtonState();rebuildButtons();}
            else if(w==2){addHome=!addHome;saveButtonState();rebuildButtons();}
            else if(w==3){addMenu=!addMenu;saveButtonState();rebuildButtons();}
            else {vertical=!vertical;getSharedPreferences(MainActivity.PREF,0).edit().putBoolean("floating_vertical",vertical).apply();restartPanel();}
        }).show();
    }
    void saveButtonState(){getSharedPreferences(MainActivity.PREF,0).edit().putBoolean("floating_back",addBack).putBoolean("floating_home",addHome).putBoolean("floating_menu",addMenu).apply();}
    void restartPanel(){
        try{if(wm!=null&&panel!=null)wm.removeView(panel);}catch(Exception ignored){}
        showPanel();
    }

    // 使用 WindowManager 直接创建选择面板，不再从 Service Context 创建 Dialog。
    // 部分车机对 Service Context + Dialog 的 Window Token 处理不完整，点击“添加 APP”
    // 会直接崩溃；直接使用 TYPE_APPLICATION_OVERLAY 可以避开这个问题。
    View appPickerOverlay;
    WindowManager.LayoutParams appPickerLp;

    void closeAppPicker(){
        try{
            if(appPickerOverlay!=null && wm!=null) wm.removeView(appPickerOverlay);
        }catch(Exception ignored){}
        appPickerOverlay=null;
    }

    void showApps(){
        if(wm==null) wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
            Toast.makeText(this,"请先授权悬浮窗权限",Toast.LENGTH_SHORT).show();
            return;
        }
        closeAppPicker();

        FrameLayout root=new FrameLayout(this);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xF0202020); bg.setCornerRadius(dp(16));
        root.setBackground(bg); root.setPadding(dp(10),dp(10),dp(10),dp(10));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText("选择 APP 添加到悬浮窗口"); title.setTextColor(Color.WHITE); title.setTextSize(16);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        ImageButton close=iconButton(android.R.drawable.ic_menu_close_clear_cancel);
        close.setContentDescription("关闭");
        close.setOnClickListener(v->closeAppPicker());
        titleRow.addView(close,new LinearLayout.LayoutParams(dp(46),dp(46)));
        box.addView(titleRow);

        EditText search=new EditText(this);
        search.setHint("搜索 APP"); search.setHintTextColor(Color.GRAY); search.setTextColor(Color.WHITE);
        search.setSingleLine(true); search.setPadding(dp(10),0,dp(10),0);
        box.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));

        ScrollView sv=new ScrollView(this);
        LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        sv.addView(rows,new ScrollView.LayoutParams(-1,-2));
        box.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> list=new ArrayList<>();
        try{
            for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
                if(ai.packageName.equals(getPackageName())) continue;
                if(pm.getLaunchIntentForPackage(ai.packageName)==null) continue;
                list.add(ai);
            }
            Collections.sort(list,(a,b)->{
                String aa=String.valueOf(pm.getApplicationLabel(a));
                String bb=String.valueOf(pm.getApplicationLabel(b));
                return aa.compareToIgnoreCase(bb);
            });
        }catch(Exception ignored){}

        Runnable refresh=()->{
            rows.removeAllViews();
            String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int count=0;
            for(ApplicationInfo ai:list){
                String name;
                try{name=pm.getApplicationLabel(ai).toString();}catch(Exception e){name=ai.packageName;}
                if(!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !ai.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;

                LinearLayout row=new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8),dp(4),dp(8),dp(4));
                row.setBackgroundResource(R.drawable.card);

                ImageView icon=new ImageView(this);
                try{icon.setImageDrawable(pm.getApplicationIcon(ai));}catch(Exception ignored){}
                row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));

                TextView nameView=new TextView(this);
                nameView.setText(name); nameView.setTextColor(Color.WHITE); nameView.setTextSize(14);
                nameView.setGravity(Gravity.CENTER_VERTICAL); nameView.setPadding(dp(12),0,0,0);
                row.addView(nameView,new LinearLayout.LayoutParams(0,dp(56),1));

                TextView state=new TextView(this);
                state.setText(floatingPkgs.contains(ai.packageName)?"已添加":"添加");
                state.setTextColor(0xFFB0BEC5); state.setGravity(Gravity.CENTER);
                row.addView(state,new LinearLayout.LayoutParams(dp(60),dp(50)));

                row.setOnClickListener(v->{
                    try{
                        if(!floatingPkgs.contains(ai.packageName)){
                            floatingPkgs.add(ai.packageName);
                            floatingNames.add(name);
                            saveFloatingApps();
                            rebuildButtons();
                        }
                        closeAppPicker();
                    }catch(Exception e){
                        Toast.makeText(this,"添加 APP 失败："+e.getMessage(),Toast.LENGTH_SHORT).show();
                    }
                });
                rows.addView(row,new LinearLayout.LayoutParams(-1,dp(60)));
                if(++count>=150) break;
            }
            if(count==0){
                TextView empty=new TextView(this); empty.setText("没有找到可启动的 APP");
                empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER);
                rows.addView(empty,new LinearLayout.LayoutParams(-1,dp(80)));
            }
        };
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}
            public void afterTextChanged(android.text.Editable e){}
        });

        appPickerOverlay=root;
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        appPickerLp=new WindowManager.LayoutParams(
                dp(390),dp(540),type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        appPickerLp.gravity=Gravity.CENTER;
        appPickerLp.dimAmount=0.35f;
        if(Build.VERSION.SDK_INT>=21) appPickerLp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        try{
            wm.addView(appPickerOverlay,appPickerLp);
            refresh.run();
        }catch(Exception e){
            appPickerOverlay=null;
            Toast.makeText(this,"打开 APP 列表失败："+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onDestroy(){
        closeAppPicker();
        try{if(wm!=null&&panel!=null)wm.removeView(panel);}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
