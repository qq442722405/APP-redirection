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
    final ArrayList<Integer> floatingPresets=new ArrayList<>();
    int selectedPresetIndex=-1;

    boolean vertical;
    int buttonSpacingPx=6, iconSizePx=44, backgroundOpacity=80;
    boolean addBack=false, addHome=false, addMenu=false;
    boolean singleIconMode=false, positionLocked=false;
    String singleIconShape="rounded";
    final int ACTION_BACK=1,ACTION_HOME=2,ACTION_MENU=3;
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
                    floatingPresets.add(o.optInt("preset",-1));
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
                o.put("preset",i<floatingPresets.size()?floatingPresets.get(i):-1);
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

        TextView drag=baseButton("☰");
        drag.setTextSize(20*fontScale());
        TextView plus=baseButton("＋");
        plus.setTextSize(22*fontScale());
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
            // 删除独立的拖拽“☰”按钮；“＋”本身同时承担添加和拖动功能。
            addView(plus,iconSizePx,iconSizePx);
            plus.setContentDescription("添加悬浮项目（点击添加，拖动移动）");
            final int[] plusDown={0,0};
            final long[] plusDownTime={0};
            final boolean[] plusMoved={false};
            plus.setOnTouchListener((v,e)->{
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        plusDown[0]=(int)e.getRawX(); plusDown[1]=(int)e.getRawY();
                        plusDownTime[0]=System.currentTimeMillis(); plusMoved[0]=false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx=(int)e.getRawX()-plusDown[0], dy=(int)e.getRawY()-plusDown[1];
                        if(Math.abs(dx)>8 || Math.abs(dy)>8){
                            plusMoved[0]=true;
                            if(!positionLocked){
                                lp.x += dx; lp.y += dy;
                                plusDown[0]=(int)e.getRawX(); plusDown[1]=(int)e.getRawY();
                                try{wm.updateViewLayout(panel,lp);}catch(Exception ignored){}
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(!plusMoved[0]){
                            showAddMenu();
                        }else if(!positionLocked){
                            getSharedPreferences(MainActivity.PREF,0).edit()
                                    .putInt("floating_position_x",lp.x).putInt("floating_position_y",lp.y).apply();
                        }
                        return true;
                }
                return true;
            });
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

        drag.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                downX=(int)e.getRawX(); downY=(int)e.getRawY();
                startX=lp.x; startY=lp.y;
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                if(positionLocked)return true;
                int dx=(int)e.getRawX()-downX;
                int dy=(int)e.getRawY()-downY;
                lp.x=startX+dx; lp.y=startY+dy;
                try{wm.updateViewLayout(panel,lp);}catch(Exception ignored){}
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){
                if(positionLocked)return true;
                getSharedPreferences(MainActivity.PREF,0).edit()
                        .putInt("floating_position_x",lp.x)
                        .putInt("floating_position_y",lp.y).apply();
                return true;
            }
            return true;
        });
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
        if(action.startsWith("app:")){
            String pkg=action.substring(4); Intent in=getPackageManager().getLaunchIntentForPackage(pkg);
            if(in!=null){in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); try{startActivity(in);}catch(Exception ignored){}}
        }
    }

    int downX,downY,startX,startY;
    void addView(View v,int w,int h){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(w),dp(h));
        p.setMargins(dp(buttonSpacingPx),dp(buttonSpacingPx),dp(buttonSpacingPx),dp(buttonSpacingPx));
        panel.addView(v,p);
    }

    void launchFloatingApp(String pkg,int presetIndex){
        try{
            Intent in=getPackageManager().getLaunchIntentForPackage(pkg);
            if(in==null){Toast.makeText(this,"APP 未安装或无法启动",Toast.LENGTH_SHORT).show();return;}
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if(presetIndex>=0){
                JSONArray arr=new JSONArray(getSharedPreferences(MainActivity.PREF,0).getString(MainActivity.PRESETS,"[]"));
                if(presetIndex<arr.length()){
                    JSONObject p=arr.optJSONObject(presetIndex);
                    if(p!=null){
                        int x=Math.max(0,p.optInt("x",0)),y=Math.max(0,p.optInt("y",0));
                        int w=Math.max(1,p.optInt("w",1)),h=Math.max(1,p.optInt("h",1));
                        if(p.optInt("mode",1)==6){
                            android.util.DisplayMetrics dm=new android.util.DisplayMetrics(); wm.getDefaultDisplay().getRealMetrics(dm);
                            x=0;y=0;w=dm.widthPixels;h=dm.heightPixels;
                        }
                        android.app.ActivityOptions opt=android.app.ActivityOptions.makeBasic();
                        opt.setLaunchBounds(new android.graphics.Rect(x,y,x+w,y+h));
                        try{startActivity(in,opt.toBundle());return;}catch(Exception ignored){}
                    }
                }
            }
            startActivity(in);
        }catch(Exception e){Toast.makeText(this,"启动 APP 失败",Toast.LENGTH_SHORT).show();}
    }

    void rebuildButtons(){
        if(panel==null)return;
        while(panel.getChildCount()>2)panel.removeViewAt(2);

        // 桌面悬浮窗上的 APP 按钮只显示名称第一个字/字母，不显示真实图标。
        for(int i=0;i<floatingPkgs.size();i++){
            final String pkg=floatingPkgs.get(i);
            final String displayName=(i<floatingNames.size()?floatingNames.get(i):pkg);
            TextView b=baseButton(displayName==null||displayName.trim().isEmpty()?"A":displayName.trim().substring(0,1).toUpperCase(Locale.ROOT));
            b.setTextSize(Math.max(12,Math.min(28,iconSizePx*0.48f))*fontScale());
            b.setGravity(Gravity.CENTER);
            final int floatingIndex=i;
            b.setOnClickListener(v->launchFloatingApp(pkg,floatingIndex<floatingPresets.size()?floatingPresets.get(floatingIndex):-1));
            b.setOnLongClickListener(v->{removeFloatingApp(pkg);return true;});
            addView(b,iconSizePx,iconSizePx);
        }

        if(addBack) addSystemButton(R.drawable.ic_back,"返回",ACTION_BACK);
        if(addHome) addSystemButton(R.drawable.ic_home,"首页",ACTION_HOME);
        if(addMenu) addSystemButton(R.drawable.ic_menu,"菜单",ACTION_MENU);
    }

    void addSystemButton(int icon,String desc,int action){
        ImageButton b=iconButton(icon);
        b.setContentDescription(desc);
        b.setOnClickListener(v->globalAction(action));
        addView(b,iconSizePx,iconSizePx);
    }

    void globalAction(int action){
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
            if(i<floatingPresets.size()) floatingPresets.remove(i);
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
            Toast.makeText(this,"请先授权悬浮窗权限",Toast.LENGTH_SHORT).show(); return;
        }
        closeOverlay();

        FrameLayout root=new FrameLayout(this);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFF202020); bg.setCornerRadius(dp(16));
        root.setBackground(bg); root.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout titleRow=new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this); title.setText("添加到悬浮窗口"); title.setTextColor(Color.WHITE); title.setTextSize(20*fontScale());
        titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        ImageButton close=iconButton(android.R.drawable.ic_menu_close_clear_cancel);
        close.setOnClickListener(v->{closeOverlay();showPanel();}); titleRow.addView(close,new LinearLayout.LayoutParams(dp(46),dp(46)));
        box.addView(titleRow);

        EditText search=new EditText(this); search.setHint("第一排：搜索 APP 名称或包名"); search.setHintTextColor(Color.GRAY); search.setTextColor(Color.WHITE); search.setSingleLine(true);
        box.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));

        LinearLayout tabs=new LinearLayout(this); tabs.setGravity(Gravity.CENTER_VERTICAL);
        String[] cats={"全部","用户","系统"}; Button[] tabBtns=new Button[cats.length]; final int[] category={0};
        for(int i=0;i<cats.length;i++){
            final int ci=i; Button tb=baseMenuButton(cats[i]); tabBtns[i]=tb; tabs.addView(tb,new LinearLayout.LayoutParams(0,dp(46),1));
            tb.setAlpha(ci==0?1f:.55f);
        }
        box.addView(tabs,new LinearLayout.LayoutParams(-1,dp(50)));

        Button presetButton=baseMenuButton("窗口预设：默认（不填）");
        box.addView(presetButton,new LinearLayout.LayoutParams(-1,dp(50)));

        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setGravity(Gravity.CENTER_HORIZONTAL);
        sv.addView(rows,new ScrollView.LayoutParams(-1,-2)); box.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        PackageManager pm=getPackageManager(); ArrayList<ApplicationInfo> all=new ArrayList<>();
        try{ for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){ CharSequence label=pm.getApplicationLabel(ai); if(label!=null&&label.toString().trim().length()>0) all.add(ai); } }catch(Exception ignored){}
        Collections.sort(all,(a,b)->String.valueOf(pm.getApplicationLabel(a)).compareToIgnoreCase(String.valueOf(pm.getApplicationLabel(b))));

        // Java requires the runnable target to be assigned before listeners execute.
        final Runnable[] refreshHolder={null};
        refreshHolder[0]=()->{
            rows.removeAllViews(); String q=search.getText().toString().trim().toLowerCase(Locale.ROOT); int count=0,inRow=0; LinearLayout row=null;
            int columns=5; int tileW=Math.max(dp(112),dp(600)/columns);
            for(ApplicationInfo ai:all){
                boolean system=(ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0 || (ai.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;
                if(category[0]==1&&system)continue; if(category[0]==2&&!system)continue;
                String name=String.valueOf(pm.getApplicationLabel(ai)); String pkg=ai.packageName;
                if(!q.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(q)&&!pkg.toLowerCase(Locale.ROOT).contains(q))continue;
                if(inRow==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER);rows.addView(row,new LinearLayout.LayoutParams(-1,dp(124)));}
                LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(5),dp(5),dp(5),dp(5));tile.setBackgroundResource(R.drawable.floating_app_card);
                ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);try{icon.setImageDrawable(pm.getApplicationIcon(ai));}catch(Exception ignored){}
                tile.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));
                TextView nv=new TextView(this);nv.setText(name);nv.setTextColor(Color.WHITE);nv.setTextSize(11*fontScale());nv.setGravity(Gravity.CENTER);nv.setMaxLines(2);nv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(nv,new LinearLayout.LayoutParams(-1,dp(38)));
                final String selectedPkg=pkg, selectedName=name;
                tile.setOnClickListener(v->{
                    try{
                        if(!floatingPkgs.contains(selectedPkg)){floatingPkgs.add(selectedPkg);floatingNames.add(selectedName);floatingPresets.add(selectedPresetIndex);saveFloatingApps();}
                        else {int ix=floatingPkgs.indexOf(selectedPkg);if(ix>=0){while(floatingPresets.size()<=ix)floatingPresets.add(-1);floatingPresets.set(ix,selectedPresetIndex);saveFloatingApps();}}
                        closeOverlay();showPanel();
                    }catch(Exception e){Toast.makeText(this,"添加 APP 失败",Toast.LENGTH_SHORT).show();}
                });
                LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(tileW,dp(112));glp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(tile,glp);inRow++;count++;if(inRow>=columns)inRow=0;
            }
            if(count==0){TextView empty=new TextView(this);empty.setText("没有找到 APP");empty.setTextColor(Color.GRAY);empty.setGravity(Gravity.CENTER);rows.addView(empty,new LinearLayout.LayoutParams(-1,dp(80)));}
        };
        // bind the category/search listeners after the runnable is created
        for(int j=0;j<3;j++){final int jj=j; tabBtns[j].setOnClickListener(v->{category[0]=jj;for(int k=0;k<3;k++)tabBtns[k].setAlpha(k==jj?1f:.55f);refreshHolder[0].run();});}
        tabBtns[0].setAlpha(1f);tabBtns[1].setAlpha(.55f);tabBtns[2].setAlpha(.55f);

        presetButton.setOnClickListener(v->showPresetChooser(presetButton,refreshHolder[0]));
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){refreshHolder[0].run();}public void afterTextChanged(android.text.Editable e){}});

        overlayView=root; overlayLp=new WindowManager.LayoutParams(dp(1000),dp(800),overlayType(),WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);
        overlayLp.gravity=Gravity.CENTER;overlayLp.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try{wm.addView(overlayView,overlayLp);refreshHolder[0].run();}catch(Exception e){overlayView=null;Toast.makeText(this,"打开 APP 列表失败",Toast.LENGTH_SHORT).show();}
    }

    Button baseMenuButton(String text){
        Button b=new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(15*fontScale()); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
        GradientDrawable g=new GradientDrawable();g.setColor(0xFF303030);g.setCornerRadius(dp(8));b.setBackground(g);return b;
    }

    void showPresetChooser(Button target,Runnable refreshApps){
        closeOverlay();
        FrameLayout root=new FrameLayout(this);GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF202020);bg.setCornerRadius(dp(16));root.setBackground(bg);root.setPadding(dp(16),dp(12),dp(16),dp(12));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);root.addView(box,new FrameLayout.LayoutParams(-1,-1));
        TextView title=new TextView(this);title.setText("选择窗口预设");title.setTextColor(Color.WHITE);title.setTextSize(20*fontScale());box.addView(title,new LinearLayout.LayoutParams(-1,dp(50)));
        ScrollView sv=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list,new ScrollView.LayoutParams(-1,-2));box.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        Button def=baseMenuButton("默认（不填窗口预设）");def.setOnClickListener(v->{selectedPresetIndex=-1;target.setText("窗口预设：默认（不填）");closeOverlay();showApps();});list.addView(def,new LinearLayout.LayoutParams(-1,dp(56)));
        try{
            JSONArray arr=new JSONArray(getSharedPreferences(MainActivity.PREF,0).getString(MainActivity.PRESETS,"[]"));
            for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o==null)continue;final int idx=i;String name=o.optString("name","窗口预设");Button b=baseMenuButton((i+1)+". "+name+"   "+o.optInt("w",0)+"×"+o.optInt("h",0));b.setOnClickListener(v->{selectedPresetIndex=idx;target.setText("窗口预设："+name);closeOverlay();showApps();});list.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));}
        }catch(Exception ignored){}
        overlayView=root;overlayLp=new WindowManager.LayoutParams(dp(1000),dp(800),overlayType(),WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.OPAQUE);overlayLp.gravity=Gravity.CENTER;try{wm.addView(overlayView,overlayLp);}catch(Exception ignored){overlayView=null;}
    }

    @Override public void onDestroy(){
        closeOverlay();
        removePanel();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i){return null;}
}
