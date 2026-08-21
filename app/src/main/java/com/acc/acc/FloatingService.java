package com.acc.acc;

import com.acc.acc.R;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import org.json.*;

/**
 * 悬浮窗口服务。
 * 重要：本版本所有悬浮层都通过 WindowManager 创建，不再从 Service Context
 * 创建 AlertDialog，避免部分车机 Android 12 ROM 点击“添加 APP”直接崩溃。
 */
public class FloatingService extends Service {
    WindowManager wm;
    LinearLayout panel;
    WindowManager.LayoutParams lp;

    final ArrayList<String> floatingPkgs=new ArrayList<>();
    final ArrayList<String> floatingNames=new ArrayList<>();

    boolean vertical;
    int buttonSpacingPx=6, iconSizePx=44, backgroundOpacity=80;
    boolean addBack=false, addHome=false, addMenu=false, addClose=false;
    boolean singleIconMode=false, positionLocked=false;
    String singleIconShape="rounded";
    final int ACTION_BACK=1,ACTION_HOME=2,ACTION_MENU=3,ACTION_CLOSE=4;
    Handler gestureHandler=new Handler(Looper.getMainLooper());
    Runnable longPressRunnable;
    int touchDownX,touchDownY; long touchDownTime; boolean moved; int tapCount; int lastDragDx,lastDragDy;
    Runnable singleTapRunnable;

    View overlayView;
    WindowManager.LayoutParams overlayLp;

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    float fontScale(){
        try{
            float saved=getSharedPreferences(MainActivity.PREF,0).getFloat("font_scale",1.0f);
            return Math.max(0.20f,Math.min(3.0f,saved));
        }catch(Exception e){return 1.0f;}
    }


    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
            stopSelf();
            return;
        }
        loadFloatingApps();
        SharedPreferences p=getSharedPreferences(MainActivity.PREF,0);
        vertical=p.getBoolean("floating_vertical",false);
        buttonSpacingPx=p.getInt("floating_button_spacing_px",6);
        iconSizePx=p.getInt("floating_icon_size_px",44);
        backgroundOpacity=Math.max(0,Math.min(100,p.getInt("floating_background_opacity",80)));
        addBack=p.getBoolean("floating_back",false);
        addHome=p.getBoolean("floating_home",false);
        addMenu=p.getBoolean("floating_menu",false);
        addClose=p.getBoolean("floating_close",false);
        singleIconMode=p.getBoolean("floating_single_icon_mode",false);
        positionLocked=p.getBoolean("floating_position_locked",false);
        singleIconShape=p.getString("floating_single_icon_shape","rounded");

        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel("float","悬浮窗口",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
            Notification n=new Notification.Builder(this,"float")
                    .setContentTitle("APP窗口启动器")
                    .setContentText("悬浮窗口运行中")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .build();
            startForeground(31001,n);
        }
        showPanel();
    }

    int overlayType(){
        return Build.VERSION.SDK_INT>=26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    void loadFloatingApps(){
        try{
            JSONArray a=new JSONArray(getSharedPreferences(MainActivity.PREF,0).getString("floating_apps","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                String pkg=o.optString("pkg","");
                if(!pkg.isEmpty() && !floatingPkgs.contains(pkg) && getPackageManager().getLaunchIntentForPackage(pkg)!=null){
                    floatingPkgs.add(pkg);
                    String savedName=o.optString("name","");
                    if(savedName.isEmpty()){
                        try{savedName=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}
                        catch(Exception ignored){savedName=pkg;}
                    }
                    floatingNames.add(savedName);
                }
            }
        }catch(Exception ignored){}
    }

    void saveFloatingApps(){
        try{
            JSONArray a=new JSONArray();
            for(int i=0;i<floatingPkgs.size();i++){
                JSONObject o=new JSONObject();
                o.put("pkg",floatingPkgs.get(i));
                o.put("name",floatingNames.get(i));
                a.put(o);
            }
            getSharedPreferences(MainActivity.PREF,0).edit().putString("floating_apps",a.toString()).apply();
        }catch(Exception ignored){}
    }

    TextView baseButton(String label){
        TextView b=new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12*fontScale());
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        GradientDrawable g=new GradientDrawable();
        g.setColor(0xFF343434);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        return b;
    }

    Button button(String label){
        Button b=new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12*fontScale());
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setAllCaps(false);
        b.setPadding(dp(6),0,dp(6),0);
        b.setBackgroundResource(R.drawable.button);
        return b;
    }

    ImageButton iconButton(int icon){
        ImageButton b=new ImageButton(this);
        b.setImageResource(icon);
        b.setColorFilter(Color.WHITE);
        b.setBackgroundResource(R.drawable.button);
        int pad=Math.max(2,(iconSizePx-28)/2);
        b.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));
        return b;
    }

    void showPanel(){
        if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){stopSelf();return;}
        closeOverlay();
        panel=new LinearLayout(this);
        panel.setOrientation(vertical?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(5),dp(4),dp(5),dp(4));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xFF202020);
        bg.setCornerRadius(dp(18));
        panel.setBackground(bg);
        // 透明度作用于整个悬浮窗口（背景、按钮、APP图标等所有内容），而不是仅背景。
        panel.setAlpha(Math.max(0f, Math.min(1f, backgroundOpacity / 100f)));

        if(singleIconMode) {
            ImageButton single=iconButton(0);
            // 单图标模式的图形本身就是按钮，不显示任何 APP 图标。
            single.setImageDrawable(null);
            single.setContentDescription("单图标手势按钮");
            GradientDrawable singleBg=new GradientDrawable();
            singleBg.setColor(0xFF343434);
            singleBg.setStroke(dp(1),0x55666666);
            if("circle".equals(singleIconShape)){
                singleBg.setShape(GradientDrawable.OVAL);
            }else{
                singleBg.setShape(GradientDrawable.RECTANGLE);
                singleBg.setCornerRadius(dp(Math.max(8,iconSizePx/4)));
            }
            single.setBackground(singleBg);
            single.setPadding(dp(2),dp(2),dp(2),dp(2));
            addView(single,iconSizePx,iconSizePx);
            installSingleIconGesture(single);
        } else {
            // 普通模式：悬浮窗只显示用户配置的 APP / 系统按钮；不再显示“＋”和拖动手柄。
            rebuildButtons();
        }

        lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        SharedPreferences positionPrefs=getSharedPreferences(MainActivity.PREF,0);
        boolean hasSavedPosition=positionPrefs.contains("floating_position_x") && positionPrefs.contains("floating_position_y");
        lp.gravity=Gravity.TOP|Gravity.LEFT;
        lp.x=hasSavedPosition?positionPrefs.getInt("floating_position_x",0):0;
        lp.y=hasSavedPosition?positionPrefs.getInt("floating_position_y",0):0;

        try{
            wm.addView(panel,lp);
            if(!hasSavedPosition){
                panel.post(()->{
                    try{
                        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
                        wm.getDefaultDisplay().getRealMetrics(dm);
                        int sw=dm.widthPixels, sh=dm.heightPixels;
                        lp.x=Math.max(0,(sw-panel.getWidth())/2);
                        lp.y=Math.max(0,(sh-panel.getHeight())/2);
                        wm.updateViewLayout(panel,lp);
                        positionPrefs.edit().putInt("floating_position_x",lp.x).putInt("floating_position_y",lp.y).apply();
                    }catch(Exception ignored){}
                });
            }
        }catch(Exception e){stopSelf();}
    }

    void installSingleIconGesture(View v){
        v.setOnTouchListener((view,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    touchDownX=(int)event.getRawX(); touchDownY=(int)event.getRawY(); touchDownTime=System.currentTimeMillis(); moved=false; lastDragDx=0; lastDragDy=0;
                    if(longPressRunnable!=null)gestureHandler.removeCallbacks(longPressRunnable);
                    longPressRunnable=()->{ if(!moved)performConfiguredGesture("long"); };
                    gestureHandler.postDelayed(longPressRunnable,550);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx=(int)event.getRawX()-touchDownX, dy=(int)event.getRawY()-touchDownY;
                    if(Math.abs(dx)>24||Math.abs(dy)>24){
                        moved=true;
                        if(longPressRunnable!=null)gestureHandler.removeCallbacks(longPressRunnable);
                        // 单图标模式下，拖动要真正跟手移动，而不是等松手后才移动。
                        // 慢速移动优先视为拖动；快速大幅移动仍保留左右/上下滑动手势。
                        if(!positionLocked && System.currentTimeMillis()-touchDownTime>300){
                            movePanelBy(dx-(lastDragDx),dy-(lastDragDy));
                            lastDragDx=dx; lastDragDy=dy;
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(longPressRunnable!=null)gestureHandler.removeCallbacks(longPressRunnable);
                    int ux=(int)event.getRawX()-touchDownX, uy=(int)event.getRawY()-touchDownY;
                    long touchDuration=System.currentTimeMillis()-touchDownTime;
                    if(Math.abs(ux)>70||Math.abs(uy)>70){
                        String swipeKey=Math.abs(ux)>=Math.abs(uy)?(ux<0?"left":"right"):(uy<0?"up":"down");
                        String configured=getSharedPreferences(MainActivity.PREF,0).getString("floating_gesture_"+swipeKey,"none");
                        // 快速滑动且配置了对应功能时执行手势；否则当作拖动。
                        if(touchDuration<=300 && !"none".equals(configured)){
                            performConfiguredGesture(swipeKey);
                        } else if(!positionLocked){
                            movePanelBy(ux-lastDragDx,uy-lastDragDy);
                        }
                        lastDragDx=lastDragDy=0;
                        return true;
                    }
                    if(!moved){
                        tapCount++;
                        if(tapCount==1){
                            singleTapRunnable=()->{ if(tapCount==1)performConfiguredGesture("tap"); tapCount=0; };
                            gestureHandler.postDelayed(singleTapRunnable,280);
                        } else if(tapCount>=2){
                            if(singleTapRunnable!=null)gestureHandler.removeCallbacks(singleTapRunnable);
                            tapCount=0; performConfiguredGesture("double");
                        }
                    } else if(!positionLocked){
                        movePanelBy(ux-lastDragDx,uy-lastDragDy);
                    }
                    lastDragDx=lastDragDy=0;
                    return true;
            }
            return true;
        });
    }

    void movePanelBy(int dx,int dy){
        if(panel==null||lp==null||positionLocked)return;
        lp.x+=dx; lp.y+=dy;
        try{wm.updateViewLayout(panel,lp);}catch(Exception ignored){}
        getSharedPreferences(MainActivity.PREF,0).edit().putInt("floating_position_x",lp.x).putInt("floating_position_y",lp.y).apply();
    }

    void performConfiguredGesture(String key){
        String action=getSharedPreferences(MainActivity.PREF,0).getString("floating_gesture_"+key,"none");
        if(action==null||"none".equals(action))return;
        if("back".equals(action)){globalAction(ACTION_BACK);return;}
        if("home".equals(action)){globalAction(ACTION_HOME);return;}
        if("menu".equals(action)){globalAction(ACTION_MENU);return;}
        if("close".equals(action)){AccessibilityServiceBridge.performBackThen(true);return;}
        if(action.startsWith("app:")){
            launchFloatingApp(action.substring(4));
        }
    }

    int downX,downY,startX,startY;
    void addView(View v,int w,int h){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(w),dp(h));
        p.setMargins(dp(buttonSpacingPx),dp(buttonSpacingPx),dp(buttonSpacingPx),dp(buttonSpacingPx));
        panel.addView(v,p);
    }

    void launchFloatingApp(String pkg){
        Intent in=getPackageManager().getLaunchIntentForPackage(pkg);
        if(in==null)return;
        in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        try{
            android.content.SharedPreferences sp=getSharedPreferences(MainActivity.PREF,0);
            // 悬浮窗快捷键绑定了窗口预设时，每次点击都以该预设为准。
            // 不再优先使用旧的 app_last_bounds，这样切换/修改预设后可以立即应用新的位置和大小。
            int presetIndex=sp.getInt("floating_preset_"+pkg,-1);
            if(presetIndex>=0){
                JSONArray pa=new JSONArray(sp.getString(MainActivity.PRESETS,"[]"));
                JSONObject po=presetIndex<pa.length()?pa.optJSONObject(presetIndex):null;
                if(po!=null){
                    android.view.Display d=wm.getDefaultDisplay();
                    android.graphics.Point real=new android.graphics.Point();
                    try{d.getRealSize(real);}catch(Exception e){android.util.DisplayMetrics dm=new android.util.DisplayMetrics();d.getMetrics(dm);real.x=dm.widthPixels;real.y=dm.heightPixels;}
                    int x=Math.max(0,Math.min(po.optInt("x",0),real.x-1));
                    int y=Math.max(0,Math.min(po.optInt("y",0),real.y-1));
                    int w=Math.max(1,Math.min(po.optInt("w",real.x),real.x-x));
                    int h=Math.max(1,Math.min(po.optInt("h",real.y),real.y-y));
                    boolean fullscreen=po.optInt("mode",1)==6;
                    if(fullscreen){x=0;y=0;w=real.x;h=real.y;}
                    ActivityOptions ao=ActivityOptions.makeBasic();
                    ao.setLaunchBounds(new android.graphics.Rect(x,y,x+w,y+h));
                    if(Build.VERSION.SDK_INT>=26){try{java.lang.reflect.Method m=ActivityOptions.class.getMethod("setLaunchDisplayId",int.class);m.invoke(ao,d.getDisplayId());}catch(Exception ignored){}}
                    in.putExtra("com.acc.acc.target_x",x);in.putExtra("com.acc.acc.target_y",y);
                    in.putExtra("com.acc.acc.target_w",w);in.putExtra("com.acc.acc.target_h",h);
                    in.putExtra("com.acc.acc.target_display_id",d.getDisplayId());in.putExtra("com.acc.acc.fullscreen",fullscreen);
                    startActivity(in,ao.toBundle());
                    saveFloatingAppBounds(pkg,x,y,w,h,d.getDisplayId(),fullscreen);
                    return;
                }
            }

            // 没有绑定窗口预设时，恢复该 APP 最近一次窗口位置。
            String raw=sp.getString("app_last_bounds","{}");
            JSONObject all=new JSONObject(raw);
            JSONObject b=all.optJSONObject(pkg);
            if(b!=null){
                android.view.Display d=wm.getDefaultDisplay();
                android.graphics.Point real=new android.graphics.Point();
                try{d.getRealSize(real);}catch(Exception e){android.util.DisplayMetrics dm=new android.util.DisplayMetrics();d.getMetrics(dm);real.x=dm.widthPixels;real.y=dm.heightPixels;}
                int x=Math.max(0,Math.min(b.optInt("x",0),real.x-1));
                int y=Math.max(0,Math.min(b.optInt("y",0),real.y-1));
                int w=Math.max(1,Math.min(b.optInt("w",real.x),real.x-x));
                int h=Math.max(1,Math.min(b.optInt("h",real.y),real.y-y));
                boolean fullscreen=b.optBoolean("fullscreen",false);
                if(fullscreen){x=0;y=0;w=real.x;h=real.y;}
                ActivityOptions ao=ActivityOptions.makeBasic();
                ao.setLaunchBounds(new android.graphics.Rect(x,y,x+w,y+h));
                if(Build.VERSION.SDK_INT>=26){try{java.lang.reflect.Method m=ActivityOptions.class.getMethod("setLaunchDisplayId",int.class);m.invoke(ao,d.getDisplayId());}catch(Exception ignored){}}
                startActivity(in,ao.toBundle());
                return;
            }
            startActivity(in);
        }catch(Exception ignored){
            try{startActivity(in);}catch(Exception ignored2){}
        }
    }

    void saveFloatingAppBounds(String pkg,int x,int y,int w,int h,int displayId,boolean fullscreen){
        try{
            SharedPreferences sp=getSharedPreferences(MainActivity.PREF,0);
            JSONObject all=new JSONObject(sp.getString("app_last_bounds","{}"));
            JSONObject o=new JSONObject();
            o.put("x",x);o.put("y",y);o.put("w",w);o.put("h",h);o.put("displayId",displayId);o.put("fullscreen",fullscreen);
            all.put(pkg,o);sp.edit().putString("app_last_bounds",all.toString()).apply();
        }catch(Exception ignored){}
    }

    void rebuildButtons(){
        if(panel==null)return;
        // 重新构建时清空全部按钮，避免切换方向/刷新后只剩一个项目。
        while(panel.getChildCount()>0)panel.removeViewAt(0);

        for(int i=0;i<floatingPkgs.size();i++){
            final String pkg=floatingPkgs.get(i);
            final String displayName=(i<floatingNames.size()?floatingNames.get(i):pkg);
            TextView b=baseButton(displayName==null||displayName.trim().isEmpty()?"A":displayName.trim().substring(0,1).toUpperCase(Locale.ROOT));
            b.setTextSize(Math.max(12,Math.min(28,iconSizePx*0.48f))*fontScale());
            b.setGravity(Gravity.CENTER);
            installDraggableItem(b,()->launchFloatingApp(pkg),()->{
                new android.app.AlertDialog.Builder(this)
                        .setTitle("删除快捷键")
                        .setMessage("是否删除“"+displayName+"”这个悬浮窗口快捷键？")
                        .setNegativeButton("取消",null)
                        .setPositiveButton("删除",(d,w)->removeFloatingApp(pkg))
                        .show();
            });
            addView(b,iconSizePx,iconSizePx);
        }

        if(addBack) addSystemButton(R.drawable.ic_back,"返回",ACTION_BACK);
        if(addHome) addSystemButton(R.drawable.ic_home,"首页",ACTION_HOME);
        if(addMenu) addSystemButton(R.drawable.ic_menu,"菜单",ACTION_MENU);
        if(addClose) addSystemButton(android.R.drawable.ic_menu_close_clear_cancel,"关闭",ACTION_CLOSE);
    }

    /** 所有悬浮窗图标都可以拖动；未移动时才执行点击操作。 */
    void installDraggableItem(View v, final Runnable clickAction, final Runnable longAction){
        final int[] down={0,0};
        final int[] last={0,0};
        final boolean[] moved={false};
        final boolean[] longTriggered={false};
        final Runnable[] longTask={null};
        v.setOnTouchListener((view,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    down[0]=(int)event.getRawX(); down[1]=(int)event.getRawY();
                    last[0]=down[0]; last[1]=down[1]; moved[0]=false; longTriggered[0]=false;
                    if(longAction!=null){
                        longTask[0]=()->{ if(!moved[0]){longTriggered[0]=true; longAction.run();} };
                        gestureHandler.postDelayed(longTask[0],520);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx=(int)event.getRawX()-last[0], dy=(int)event.getRawY()-last[1];
                    int totalDx=(int)event.getRawX()-down[0], totalDy=(int)event.getRawY()-down[1];
                    if(Math.abs(totalDx)>8||Math.abs(totalDy)>8){
                        moved[0]=true;
                        if(longTask[0]!=null)gestureHandler.removeCallbacks(longTask[0]);
                        if(!positionLocked) movePanelBy(dx,dy);
                        last[0]=(int)event.getRawX(); last[1]=(int)event.getRawY();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(longTask[0]!=null)gestureHandler.removeCallbacks(longTask[0]);
                    if(!moved[0] && !longTriggered[0] && event.getActionMasked()==MotionEvent.ACTION_UP && clickAction!=null) clickAction.run();
                    return true;
            }
            return true;
        });
    }

    void addSystemButton(int icon,String desc,int action){
        ImageButton b=iconButton(icon);
        b.setContentDescription(desc);
        installDraggableItem(b,()->globalAction(action),null);
        addView(b,iconSizePx,iconSizePx);
    }

    void globalAction(int action){
        if(action==ACTION_CLOSE){AccessibilityServiceBridge.performBackThen(true);return;}
        AccessibilityServiceBridge.perform(this,action);
        if(action==ACTION_HOME){
            Intent i=new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try{startActivity(i);}catch(Exception ignored){}
        }
    }

    void removeFloatingApp(String pkg){
        int i=floatingPkgs.indexOf(pkg);
        if(i>=0){
            floatingPkgs.remove(i);
            floatingNames.remove(i);
            saveFloatingApps();
            rebuildButtons();
        }
    }

    /**
     * 添加菜单同样使用 WindowManager，彻底避免 Service Context Dialog 崩溃。
     * 其中返回/首页/菜单使用图标选择，并显示当前是否已加入。
     */
    void showAddMenu(){
        removePanel();
        closeOverlay();

        FrameLayout root=new FrameLayout(this);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xFF202020);
        bg.setCornerRadius(dp(16));
        root.setBackground(bg);
        root.setPadding(dp(18),dp(18),dp(18),dp(18));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box,new FrameLayout.LayoutParams(dp(560),-1));

        LinearLayout title=new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv=new TextView(this);
        tv.setText("添加到悬浮窗口");
        tv.setTextColor(Color.WHITE); tv.setTextSize(22*fontScale());
        title.addView(tv,new LinearLayout.LayoutParams(0,dp(48),1));
        ImageButton close=iconButton(android.R.drawable.ic_menu_close_clear_cancel);
        close.setContentDescription("关闭");
        close.setOnClickListener(v->{closeOverlay();showPanel();});
        title.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
        box.addView(title);

        addMenuItem(box,android.R.drawable.ic_menu_add,"添加 APP", "打开 APP 列表，显示图标和名称", v->{closeOverlay();showApps();});
        addMenuItem(box,R.drawable.ic_back,"返回按钮", addBack?"已添加，点击取消":"未添加，点击添加", v->{addBack=!addBack;saveButtonState();closeOverlay();showPanel();});
        addMenuItem(box,R.drawable.ic_home,"首页按钮", addHome?"已添加，点击取消":"未添加，点击添加", v->{addHome=!addHome;saveButtonState();closeOverlay();showPanel();});
        addMenuItem(box,R.drawable.ic_menu,"菜单按钮", addMenu?"已添加，点击取消":"未添加，点击添加", v->{addMenu=!addMenu;saveButtonState();closeOverlay();showPanel();});
        addMenuItem(box,android.R.drawable.ic_menu_rotate,"切换方向", vertical?"当前：竖向，点击切换横向":"当前：横向，点击切换竖向", v->{
            vertical=!vertical;
            getSharedPreferences(MainActivity.PREF,0).edit().putBoolean("floating_vertical",vertical).apply();
            closeOverlay();
            showPanel();
        });
        addMenuItem(box,android.R.drawable.ic_menu_mylocation,"重置悬浮窗口位置", "恢复到默认位置", v->{
            getSharedPreferences(MainActivity.PREF,0).edit().putInt("floating_position_x",30).putInt("floating_position_y",180).apply();
            closeOverlay(); showPanel();
        });

        overlayView=root;
        overlayLp=new WindowManager.LayoutParams(dp(600),dp(620),overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        overlayLp.gravity=Gravity.CENTER;
        overlayLp.x=0; overlayLp.y=0;
        try{wm.addView(overlayView,overlayLp);}catch(Exception e){overlayView=null;}
    }

    void addMenuItem(LinearLayout parent,int icon,String title,String sub,View.OnClickListener listener){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10),dp(8),dp(10),dp(8));
        row.setBackgroundResource(R.drawable.card);
        ImageButton ib=iconButton(icon);
        row.addView(ib,new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout textBox=new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView a=new TextView(this); a.setText(title); a.setTextColor(Color.WHITE); a.setTextSize(18*fontScale());
        TextView b=new TextView(this); b.setText(sub); b.setTextColor(0xFF9E9E9E); b.setTextSize(13*fontScale());
        textBox.addView(a,new LinearLayout.LayoutParams(-1,dp(30)));
        textBox.addView(b,new LinearLayout.LayoutParams(-1,dp(24)));
        row.addView(textBox,new LinearLayout.LayoutParams(0,dp(64),1));
        row.setOnClickListener(listener);
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(76)));
    }

    void saveButtonState(){
        getSharedPreferences(MainActivity.PREF,0).edit()
                .putBoolean("floating_back",addBack)
                .putBoolean("floating_home",addHome)
                .putBoolean("floating_menu",addMenu)
                .putBoolean("floating_close",addClose)
                .apply();
    }

    void closeOverlay(){
        try{if(overlayView!=null&&wm!=null)wm.removeView(overlayView);}catch(Exception ignored){}
        overlayView=null;
    }

    void removePanel(){
        try{if(panel!=null&&wm!=null)wm.removeView(panel);}catch(Exception ignored){}
        panel=null;
    }

    // ---------------- APP 选择 ----------------
    void showApps(){
        removePanel();
        if(wm==null)wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
            Toast.makeText(this,"请先授权悬浮窗权限",Toast.LENGTH_SHORT).show();
            return;
        }
        closeOverlay();

        FrameLayout root=new FrameLayout(this);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFF202020); bg.setCornerRadius(dp(16));
        root.setBackground(bg); root.setPadding(dp(10),dp(8),dp(10),dp(8));
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout titleRow=new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this); title.setText("添加到悬浮窗口"); title.setTextColor(Color.WHITE); title.setTextSize(18*fontScale());
        titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));
        ImageButton close=iconButton(android.R.drawable.ic_menu_close_clear_cancel);
        close.setOnClickListener(v->{closeOverlay();showPanel();});
        titleRow.addView(close,new LinearLayout.LayoutParams(dp(42),dp(42))); box.addView(titleRow);

        // 第一排：窗口预设。选择后，悬浮快捷键启动该 APP 时使用这个预设；不选择就是默认启动。
        TextView presetLabel=new TextView(this); presetLabel.setText("窗口预设"); presetLabel.setTextColor(0xFFCCCCCC); presetLabel.setTextSize(13*fontScale());
        presetLabel.setPadding(dp(4),dp(3),dp(4),dp(3)); box.addView(presetLabel,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout presetRow=new LinearLayout(this); presetRow.setOrientation(LinearLayout.HORIZONTAL); presetRow.setGravity(Gravity.CENTER_VERTICAL);
        Button defaultPreset=button("默认"); defaultPreset.setTextSize(12*fontScale());
        presetRow.addView(defaultPreset,new LinearLayout.LayoutParams(dp(92),dp(40)));
        ScrollView presetScroll=new ScrollView(this); presetScroll.setHorizontalScrollBarEnabled(false); presetScroll.setFillViewport(false);
        LinearLayout presetInner=new LinearLayout(this); presetInner.setOrientation(LinearLayout.HORIZONTAL); presetScroll.addView(presetInner,new ScrollView.LayoutParams(-2,-1));
        presetRow.addView(presetScroll,new LinearLayout.LayoutParams(0,dp(44),1)); box.addView(presetRow,new LinearLayout.LayoutParams(-1,dp(46)));

        final ArrayList<String> presetNames=new ArrayList<>(); final ArrayList<Integer> presetIndexes=new ArrayList<>();
        try{
            JSONArray pa=new JSONArray(getSharedPreferences(MainActivity.PREF,0).getString(MainActivity.PRESETS,"[]"));
            for(int i=0;i<pa.length();i++){JSONObject o=pa.optJSONObject(i); if(o!=null){presetNames.add(o.optString("name","预设"+(i+1)));presetIndexes.add(i);}}
        }catch(Exception ignored){}
        final int[] selectedPreset={-1};
        defaultPreset.setBackgroundResource(R.drawable.card_selected);
        defaultPreset.setOnClickListener(v->{selectedPreset[0]=-1; defaultPreset.setBackgroundResource(R.drawable.card_selected); for(int i=0;i<presetInner.getChildCount();i++)presetInner.getChildAt(i).setBackgroundResource(R.drawable.button);});
        for(int i=0;i<presetNames.size();i++){
            final int pi=presetIndexes.get(i); Button b=button(presetNames.get(i)); b.setTextSize(12*fontScale());
            presetInner.addView(b,new LinearLayout.LayoutParams(dp(110),dp(40)));
            b.setOnClickListener(v->{selectedPreset[0]=pi; defaultPreset.setBackgroundResource(R.drawable.button); for(int j=0;j<presetInner.getChildCount();j++)presetInner.getChildAt(j).setBackgroundResource(presetInner.getChildAt(j)==v?R.drawable.card_selected:R.drawable.button);});
        }

        // 第二排：应用分类，用户在前。
        final Runnable[] refreshApps={null};
        LinearLayout tabs=new LinearLayout(this); tabs.setGravity(Gravity.CENTER_VERTICAL);
        String[] cats={"用户","系统","全部"}; final int[] category={0}; Button[] tabButtons=new Button[cats.length];
        for(int i=0;i<cats.length;i++){final int ci=i; Button b=button(cats[i]); b.setTextSize(12*fontScale()); tabButtons[i]=b; tabs.addView(b,new LinearLayout.LayoutParams(0,dp(42),1)); b.setOnClickListener(v->{category[0]=ci; for(int j=0;j<tabButtons.length;j++)tabButtons[j].setBackgroundResource(j==ci?R.drawable.card_selected:R.drawable.button); refreshApps[0].run();});}
        tabButtons[0].setBackgroundResource(R.drawable.card_selected); box.addView(tabs,new LinearLayout.LayoutParams(-1,dp(44)));

        EditText search=new EditText(this); search.setHint("搜索 APP 名称或包名"); search.setHintTextColor(Color.GRAY); search.setTextColor(Color.WHITE); search.setSingleLine(true); search.setPadding(dp(10),0,dp(10),0);
        box.addView(search,new LinearLayout.LayoutParams(-1,dp(46)));

        ScrollView sv=new ScrollView(this); LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setGravity(Gravity.CENTER_HORIZONTAL); sv.addView(rows,new ScrollView.LayoutParams(-1,-2)); box.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        PackageManager pm=getPackageManager(); ArrayList<ApplicationInfo> list=new ArrayList<>();
        try{for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){if(!ai.packageName.equals(getPackageName()))list.add(ai);} Collections.sort(list,(a,b)->String.valueOf(pm.getApplicationLabel(a)).compareToIgnoreCase(String.valueOf(pm.getApplicationLabel(b))));}catch(Exception ignored){}
        refreshApps[0]=()->{
            rows.removeAllViews(); String q=search.getText().toString().trim().toLowerCase(Locale.ROOT); int count=0,inRow=0; LinearLayout row=null;
            for(ApplicationInfo ai:list){
                boolean system=(ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0 || (ai.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;
                if(category[0]==0&&system)continue; if(category[0]==1&&!system)continue;
                String name; try{name=pm.getApplicationLabel(ai).toString();}catch(Exception e){name=ai.packageName;}
                if(!q.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(q)&&!ai.packageName.toLowerCase(Locale.ROOT).contains(q))continue;
                if(inRow==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER);rows.addView(row,new LinearLayout.LayoutParams(-1,dp(104)));}
                LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(4),dp(4),dp(4),dp(4));tile.setBackgroundResource(R.drawable.floating_app_card);
                ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);try{icon.setImageDrawable(pm.getApplicationIcon(ai));}catch(Exception ignored){}
                tile.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54))); TextView nv=new TextView(this);nv.setText(name);nv.setTextColor(Color.WHITE);nv.setTextSize(11*fontScale());nv.setGravity(Gravity.CENTER);nv.setMaxLines(2);nv.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(nv,new LinearLayout.LayoutParams(-1,dp(34)));
                final String pkg=ai.packageName,nm=name; tile.setOnClickListener(v->{
                    try{
                        int idx=floatingPkgs.indexOf(pkg); if(idx<0){floatingPkgs.add(pkg);floatingNames.add(nm);} 
                        getSharedPreferences(MainActivity.PREF,0).edit().putInt("floating_preset_"+pkg,selectedPreset[0]).apply();
                        saveFloatingApps(); closeOverlay(); showPanel();
                    }catch(Exception e){Toast.makeText(this,"添加 APP 失败",Toast.LENGTH_SHORT).show();}
                });
                if(row!=null)row.addView(tile,new LinearLayout.LayoutParams(dp(92),dp(96))); inRow++;count++; if(inRow>=6)inRow=0;
            }
            if(count==0){TextView empty=new TextView(this);empty.setText("没有找到 APP");empty.setTextColor(Color.GRAY);empty.setGravity(Gravity.CENTER);rows.addView(empty,new LinearLayout.LayoutParams(-1,dp(80)));}
        };
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){refreshApps[0].run();} public void afterTextChanged(android.text.Editable e){}});
        overlayView=root; overlayLp=new WindowManager.LayoutParams(dp(600),dp(680),overlayType(),WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE); overlayLp.gravity=Gravity.CENTER; overlayLp.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try{wm.addView(overlayView,overlayLp);refreshApps[0].run();}catch(Exception e){overlayView=null;Toast.makeText(this,"打开 APP 列表失败",Toast.LENGTH_SHORT).show();}
    }

    @Override public void onDestroy(){
        closeOverlay();
        removePanel();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i){return null;}
}
