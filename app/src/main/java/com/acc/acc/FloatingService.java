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
    boolean addBack=false, addHome=false, addMenu=false;
    boolean singleIconMode=false, positionLocked=false;
    String singleIconShape="rounded";
    final int ACTION_BACK=1,ACTION_HOME=2,ACTION_MENU=3;
    Handler gestureHandler=new Handler(Looper.getMainLooper());
    Runnable longPressRunnable;
    int touchDownX,touchDownY; long touchDownTime; boolean moved; int tapCount;
    Runnable singleTapRunnable;

    View overlayView;
    WindowManager.LayoutParams overlayLp;

    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

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
        b.setTextSize(12);
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
        drag.setTextSize(20);
        TextView plus=baseButton("＋");
        plus.setTextSize(22);
        if(singleIconMode) {
            ImageButton single=iconButton(android.R.drawable.transparent);
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
            addView(drag,iconSizePx,iconSizePx);
            addView(plus,iconSizePx,iconSizePx);
            plus.setContentDescription("添加悬浮项目");
            plus.setOnClickListener(v->showAddMenu());
            rebuildButtons();
        }

        lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT;
        lp.x=getSharedPreferences(MainActivity.PREF,0).getInt("floating_position_x",30);
        lp.y=getSharedPreferences(MainActivity.PREF,0).getInt("floating_position_y",180);

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
        try{wm.addView(panel,lp);}catch(Exception e){stopSelf();}
    }

    void installSingleIconGesture(View v){
        v.setOnTouchListener((view,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    touchDownX=(int)event.getRawX(); touchDownY=(int)event.getRawY(); touchDownTime=System.currentTimeMillis(); moved=false;
                    if(longPressRunnable!=null)gestureHandler.removeCallbacks(longPressRunnable);
                    longPressRunnable=()->{ if(!moved)performConfiguredGesture("long"); };
                    gestureHandler.postDelayed(longPressRunnable,550);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx=(int)event.getRawX()-touchDownX, dy=(int)event.getRawY()-touchDownY;
                    if(Math.abs(dx)>24||Math.abs(dy)>24){moved=true; if(longPressRunnable!=null)gestureHandler.removeCallbacks(longPressRunnable);}
                    return true;
                case MotionEvent.ACTION_UP:
                    if(longPressRunnable!=null)gestureHandler.removeCallbacks(longPressRunnable);
                    int ux=(int)event.getRawX()-touchDownX, uy=(int)event.getRawY()-touchDownY;
                    if(Math.abs(ux)>70||Math.abs(uy)>70){
                        if(Math.abs(ux)>=Math.abs(uy)) performConfiguredGesture(ux<0?"left":"right");
                        else performConfiguredGesture(uy<0?"up":"down");
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
                        // 非锁定状态下，小范围拖动移动单图标；明显滑动则执行手势功能。
                        movePanelBy(ux,uy);
                    }
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

    void rebuildButtons(){
        if(panel==null)return;
        while(panel.getChildCount()>2)panel.removeViewAt(2);

        // APP 只显示图标，不显示 APP 名称。
        for(int i=0;i<floatingPkgs.size();i++){
            final String pkg=floatingPkgs.get(i);
            final String displayName=(i<floatingNames.size()?floatingNames.get(i):pkg);
            ImageButton b=iconButton(android.R.drawable.sym_def_app_icon);
            // APP 图标必须保持原始彩色；iconButton 默认的白色 ColorFilter 只用于系统按钮。
            b.clearColorFilter();
            try{b.setImageDrawable(getPackageManager().getApplicationIcon(pkg));}catch(Exception ignored){}
            b.setContentDescription(displayName);
            b.setOnClickListener(v->{
                Intent in=getPackageManager().getLaunchIntentForPackage(pkg);
                if(in!=null){
                    in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    try{startActivity(in);}catch(Exception ignored){}
                }
            });
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
        root.setPadding(dp(10),dp(10),dp(10),dp(10));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box,new FrameLayout.LayoutParams(dp(300),-1));

        LinearLayout title=new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv=new TextView(this);
        tv.setText("添加到悬浮窗口");
        tv.setTextColor(Color.WHITE); tv.setTextSize(17);
        title.addView(tv,new LinearLayout.LayoutParams(0,dp(48),1));
        ImageButton close=iconButton(android.R.drawable.ic_menu_close_clear_cancel);
        close.setContentDescription("关闭");
        close.setOnClickListener(v->{closeOverlay();showPanel();});
        title.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
        box.addView(title);

        addMenuItem(box,android.R.drawable.ic_menu_add,"添加 APP", "选择一个 APP", v->{closeOverlay();showApps();});
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
        overlayLp=new WindowManager.LayoutParams(dp(320),dp(330),overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        overlayLp.gravity=Gravity.TOP|Gravity.LEFT;
        overlayLp.x=30; overlayLp.y=240;
        try{wm.addView(overlayView,overlayLp);}catch(Exception e){overlayView=null;}
    }

    void addMenuItem(LinearLayout parent,int icon,String title,String sub,View.OnClickListener listener){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6),dp(4),dp(6),dp(4));
        row.setBackgroundResource(R.drawable.card);
        ImageButton ib=iconButton(icon);
        row.addView(ib,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout textBox=new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView a=new TextView(this); a.setText(title); a.setTextColor(Color.WHITE); a.setTextSize(14);
        TextView b=new TextView(this); b.setText(sub); b.setTextColor(0xFF9E9E9E); b.setTextSize(10);
        textBox.addView(a,new LinearLayout.LayoutParams(-1,dp(26)));
        textBox.addView(b,new LinearLayout.LayoutParams(-1,dp(20)));
        row.addView(textBox,new LinearLayout.LayoutParams(0,dp(54),1));
        row.setOnClickListener(listener);
        parent.addView(row,new LinearLayout.LayoutParams(-1,dp(58)));
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
            Toast.makeText(this,"请先授权悬浮窗权限",Toast.LENGTH_SHORT).show();
            return;
        }
        closeOverlay();

        FrameLayout root=new FrameLayout(this);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xFF202020); bg.setCornerRadius(dp(16));
        root.setBackground(bg); root.setPadding(dp(10),dp(10),dp(10),dp(10));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        root.addView(box,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText("选择 APP"); title.setTextColor(Color.WHITE); title.setTextSize(17);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        ImageButton close=iconButton(android.R.drawable.ic_menu_close_clear_cancel);
        close.setContentDescription("关闭"); close.setOnClickListener(v->{closeOverlay();showPanel();});
        titleRow.addView(close,new LinearLayout.LayoutParams(dp(46),dp(46)));
        box.addView(titleRow);

        EditText search=new EditText(this);
        search.setHint("搜索 APP 名称或包名");
        search.setHintTextColor(Color.GRAY); search.setTextColor(Color.WHITE);
        search.setSingleLine(true); search.setPadding(dp(10),0,dp(10),0);
        box.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));

        ScrollView sv=new ScrollView(this);
        GridLayout rows=new GridLayout(this);
        float density=getResources().getDisplayMetrics().density;
        int availableDp=Math.max(300,(int)(getResources().getDisplayMetrics().widthPixels/density)-28);
        int columns=Math.max(2,availableDp/118);
        rows.setColumnCount(columns);
        sv.addView(rows,new ScrollView.LayoutParams(-1,-2));
        box.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> list=new ArrayList<>();
        try{
            for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
                if(ai.packageName.equals(getPackageName()))continue;
                if(pm.getLaunchIntentForPackage(ai.packageName)==null)continue;
                list.add(ai);
            }
            Collections.sort(list,(a,b)->String.valueOf(pm.getApplicationLabel(a)).compareToIgnoreCase(String.valueOf(pm.getApplicationLabel(b))));
        }catch(Exception ignored){}

        Runnable refresh=()->{
            rows.removeAllViews();
            String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int count=0;
            int tileW=Math.max(dp(108),dp(availableDp/Math.max(1,columns)));
            for(ApplicationInfo ai:list){
                String name;
                try{name=pm.getApplicationLabel(ai).toString();}catch(Exception e){name=ai.packageName;}
                if(!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !ai.packageName.toLowerCase(Locale.ROOT).contains(q))continue;

                LinearLayout tile=new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(6),dp(6),dp(6),dp(6)); tile.setBackgroundResource(R.drawable.floating_app_card);
                ImageView icon=new ImageView(this);
                try{icon.setImageDrawable(pm.getApplicationIcon(ai));}catch(Exception ignored){}
                tile.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));
                TextView nameView=new TextView(this);
                nameView.setText(name); nameView.setTextColor(Color.WHITE); nameView.setTextSize(11);
                nameView.setGravity(Gravity.CENTER); nameView.setMaxLines(2); nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(nameView,new LinearLayout.LayoutParams(-1,dp(34)));
                TextView state=new TextView(this);
                state.setText(floatingPkgs.contains(ai.packageName)?"已添加":"添加"); state.setTextColor(0xFFB0BEC5); state.setGravity(Gravity.CENTER);
                tile.addView(state,new LinearLayout.LayoutParams(-1,dp(22)));

                final String selectedPkg=ai.packageName, selectedAppName=name;
                tile.setOnClickListener(v->{
                    try{
                        if(!floatingPkgs.contains(selectedPkg)){floatingPkgs.add(selectedPkg);floatingNames.add(selectedAppName);saveFloatingApps();}
                        closeOverlay(); showPanel();
                    }catch(Exception e){Toast.makeText(this,"添加 APP 失败",Toast.LENGTH_SHORT).show();}
                });
                GridLayout.LayoutParams glp=new GridLayout.LayoutParams();
                glp.width=tileW; glp.height=dp(114); glp.setMargins(dp(3),dp(3),dp(3),dp(3));
                rows.addView(tile,glp);
                count++;
            }
            if(count==0){
                TextView empty=new TextView(this); empty.setText("没有找到可启动的 APP"); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER);
                GridLayout.LayoutParams ep=new GridLayout.LayoutParams(); ep.width=Math.max(dp(300),dp(availableDp)); ep.height=dp(80); rows.addView(empty,ep);
            }
        };

        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}
            public void afterTextChanged(android.text.Editable e){}
        });

        overlayView=root;
        overlayLp=new WindowManager.LayoutParams(dp(420),dp(560),overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        overlayLp.gravity=Gravity.CENTER;
        overlayLp.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try{
            wm.addView(overlayView,overlayLp);
            refresh.run();
        }catch(Exception e){
            overlayView=null;
            Toast.makeText(this,"打开 APP 列表失败",Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onDestroy(){
        closeOverlay();
        removePanel();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i){return null;}
}
