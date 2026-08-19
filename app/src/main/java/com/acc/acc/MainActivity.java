package com.acc.acc;

import com.acc.acc.R;
import android.app.ActivityOptions;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import org.json.*;
import java.util.*;
import java.io.*;
import android.content.res.AssetFileDescriptor;

public class MainActivity extends AppCompatActivity {

    static final String PREF="container_prefs";
    static final String APPS="apps";
    static final String PRESETS="presets";

    SharedPreferences prefs;
    LinearLayout presetRow, appGrid;
    TextView info;
    String selectedPackage=null;
    String selectedName=null;
    Boolean lastOverlayState=null;

    // 容器本身固定避让车机原生区域
    static final int TOP_BLANK=80;
    static final int BOTTOM_BLANK=120;

    ArrayList<AppItem> apps=new ArrayList<>();
    ArrayList<Preset> presets=new ArrayList<>();

    static class AppItem {
        String pkg,name;
        AppItem(String p,String n){pkg=p;name=n;}
    }

    static class Preset {
        String name;
        int x,y,w,h,displayId,mode;
        Preset(String n,int x,int y,int w,int h){this(n,x,y,w,h,-1,1);}
        Preset(String n,int x,int y,int w,int h,int displayId,int mode){
            this.name=n; this.x=x; this.y=y; this.w=w; this.h=h; this.displayId=displayId; this.mode=mode;
        }
    }

    float uiScale(){
        float saved = prefs==null ? 1.0f : prefs.getFloat("ui_scale",1.0f);
        try{
            android.graphics.Point p=getRealScreenSize();
            float density=getResources().getDisplayMetrics().density;
            // 车机不同 Display 的 density 差异很大；按实际像素密度归一化，
            // 避免 1920×1080 等 Display 因 density 较高而出现界面超出屏幕。
            float densityFactor = density<=0 ? 1.0f : (1.0f/density);
            float heightFactor = p.y<=0 ? 1.0f : Math.min(1.0f, p.y/1080.0f);
            return Math.max(0.50f, Math.min(1.60f, saved * Math.max(0.72f, densityFactor) * heightFactor));
        }catch(Exception ignored){
            return Math.max(0.50f, Math.min(1.60f, saved));
        }
    }
    boolean mainUiContext=false;

    float mainFontScale(){
        float saved = prefs==null ? 1.0f : prefs.getFloat("main_font_scale",1.0f);
        return Math.max(0.20f, Math.min(3.0f, saved));
    }

    float menuFontScale(){
        float saved = prefs==null ? 1.0f : prefs.getFloat("font_scale",1.0f);
        return Math.max(0.20f, Math.min(3.0f, saved));
    }

    float fontScale(){
        return mainUiContext ? mainFontScale() : menuFontScale();
    }

    int touchOffsetLeft(){ return prefs==null?0:prefs.getInt("touch_offset_left_px",0); }
    int touchOffsetTop(){ return prefs==null?0:prefs.getInt("touch_offset_top_px",0); }

    int dp(int v){
        return (int)(v*getResources().getDisplayMetrics().density*uiScale()+.5f);
    }

    /**
     * 获取车机当前 Activity 所在物理 Display 的真实像素尺寸。
     * 不使用 resources.getDisplayMetrics()，避免车机状态栏/导航栏和 density
     * 导致的尺寸偏差。
     */
    android.graphics.Point getRealScreenSize(){
        return getRealScreenSize(getWindow().getWindowManager().getDefaultDisplay());
    }

    android.graphics.Point getRealScreenSize(android.view.Display display){
        android.graphics.Point out = new android.graphics.Point();
        if(display == null){
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            out.x = dm.widthPixels;
            out.y = dm.heightPixels;
            return out;
        }
        try{
            display.getRealSize(out);
        }catch(Exception e){
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            display.getMetrics(dm);
            out.x = dm.widthPixels;
            out.y = dm.heightPixels;
        }
        return out;
    }

    TextView text(String s,float size){
        TextView t=new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(size*fontScale());
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    // 主界面专用文字：只受“主界面字体大小”控制。
    TextView mainText(String s,float size){
        TextView t=new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(size*mainFontScale());
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    int adaptiveBoxHeight(int baseDp){
        float scale=fontScale();
        return dp(Math.max(baseDp, Math.round(baseDp*scale)));
    }

    void adaptDialogBoxes(AlertDialog dialog){
        if(dialog==null) return;
        View root=dialog.getWindow()==null?null:dialog.getWindow().getDecorView();
        if(root instanceof ViewGroup) adaptViewBoxes((ViewGroup)root);
    }

    void adaptViewBoxes(ViewGroup group){
        for(int i=0;i<group.getChildCount();i++){
            View v=group.getChildAt(i);
            if(v instanceof TextView){
                TextView tv=(TextView)v;
                if(tv.getTextSize()>0 && v.getLayoutParams()!=null && v.getLayoutParams().height>0){
                    int old=v.getLayoutParams().height;
                    float scale=fontScale();
                    int min=(int)(old*scale);
                    if(scale>1.0f){ v.getLayoutParams().height=Math.max(old,min); v.requestLayout(); }
                }
            }
            if(v instanceof ViewGroup) adaptViewBoxes((ViewGroup)v);
        }
    }

    Button button(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14*fontScale());
        b.setMinHeight(adaptiveBoxHeight(44));
        b.setAllCaps(false); b.setBackgroundResource(R.drawable.button);
        return b;
    }

    TextView plusButton(){
        TextView b=text("+",30);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.WHITE);
        b.setBackgroundResource(R.drawable.button);
        return b;
    }

    EditText numberField(String label,String value){
        EditText e=new EditText(this);
        e.setHint(label); e.setText(value); e.setTextColor(Color.WHITE); e.setTextSize(14*fontScale()); e.setTextSize(14*fontScale());
        e.setHintTextColor(Color.GRAY); e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }

    // 数字输入框右侧快速调整：+100 / -100 / +10 / -10 / 归零。
    LinearLayout labeledNumberField(String label, EditText input){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l=text(label,14);
        row.addView(l,new LinearLayout.LayoutParams(dp(115),dp(54)));
        row.addView(input,new LinearLayout.LayoutParams(0,dp(54),1));
        String[] labels={"+100","-100","+10","-10","归零"};
        int[] deltas={100,-100,10,-10,0};
        for(int i=0;i<labels.length;i++){
            final int delta=deltas[i];
            Button b=button(labels[i]);
            b.setTextSize(10*fontScale()); b.setMinWidth(0); b.setPadding(0,0,0,0);
            b.setOnClickListener(v->{ if(delta==0) input.setText("0"); else adjustNumber(input,delta); input.setSelection(input.length()); });
            row.addView(b,new LinearLayout.LayoutParams(dp(50),dp(44)));
        }
        return row;
    }

    // 普通数字设置行：悬浮窗口自适应尺寸/位置不再提供加减和归零快捷按钮。
    LinearLayout labeledSimpleNumberField(String label, EditText input){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l=text(label,14);
        row.addView(l,new LinearLayout.LayoutParams(dp(150),dp(54)));
        row.addView(input,new LinearLayout.LayoutParams(0,dp(54),1));
        return row;
    }

    void adjustNumber(EditText input,int delta){
        int value=number(input,0)+delta;
        input.setText(String.valueOf(value));
        input.setSelection(input.length());
    }

    EditText textField(String label,String value){
        EditText e=new EditText(this);
        e.setHint(label); e.setText(value); e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY); e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        return e;
    }

    LinearLayout labeledField(String label,EditText input){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l=text(label,14);
        row.addView(l,new LinearLayout.LayoutParams(dp(115),dp(54)));
        row.addView(input,new LinearLayout.LayoutParams(0,dp(54),1));
        return row;
    }

    int number(EditText e,int fallback){
        try{return Integer.parseInt(e.getText().toString().trim());}
        catch(Exception ex){return fallback;}
    }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        prefs=getSharedPreferences(PREF,0);
        // 新安装默认界面参数；如果用户已经手动设置过，则保留用户设置。
        SharedPreferences.Editor defaults=prefs.edit();
        if(!prefs.contains("font_scale") || prefs.getFloat("font_scale",1.0f) <= 0.2001f) defaults.putFloat("font_scale",1.00f);
        if(!prefs.contains("main_font_scale") || prefs.getFloat("main_font_scale",1.0f) <= 0.2001f) defaults.putFloat("main_font_scale",1.00f);
        if(!prefs.contains("ui_scale")) defaults.putFloat("ui_scale",1.00f);
        if(!prefs.contains("touch_offset_top_px")) defaults.putInt("touch_offset_top_px",50);
        if(!prefs.contains("touch_offset_left_px")) defaults.putInt("touch_offset_left_px",50);
        defaults.apply();
        loadData();
        buildUI();
        lastOverlayState=hasOverlayPermission();
        requestRuntimePermissions();
    }

    @Override protected void onResume(){
        super.onResume();
        boolean now=hasOverlayPermission();
        if(lastOverlayState!=null && now!=lastOverlayState){
            lastOverlayState=now;
            buildUI();
        }
    }

    static final int REQ_RUNTIME_PERMS = 19041;
    static final int REQ_EXPORT_CONFIG = 19042;
    static final int REQ_IMPORT_CONFIG = 19043;

    /**
     * 只申请本 APK 在 Android 12/13+ 上真正可以由用户授予的运行时权限。
     * 特殊权限不强行跳转，避免启动 APP 时被连续带离主界面；下面的 helper
     * 可以在需要时打开对应系统授权页。Manifest 已提前声明这些权限，实际是否可授予由车机系统决定。
     */
    void requestRuntimePermissions(){
        // 启动阶段不要一次性申请媒体、存储等权限。部分 Android 模拟器
        // /定制车机 ROM 对 READ_MEDIA_* 的运行时请求处理不完整，可能导致
        // Activity 刚启动就闪退。真正需要时再由具体功能主动申请。
        if(Build.VERSION.SDK_INT>=33){
            try{
                if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        !=PackageManager.PERMISSION_GRANTED){
                    new Handler(Looper.getMainLooper()).postDelayed(()->{
                        try{
                            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},REQ_RUNTIME_PERMS);
                        }catch(SecurityException ignored){}
                        catch(Exception ignored){}
                    },800);
                }
            }catch(Exception ignored){}
        }
    }

    boolean hasOverlayPermission(){
        return Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this);
    }

    boolean hasAllFilesPermission(){
        return Build.VERSION.SDK_INT<30 || Environment.isExternalStorageManager();
    }

    boolean hasUsageAccess(){
        try{
            android.app.AppOpsManager ops=(android.app.AppOpsManager)getSystemService(APP_OPS_SERVICE);
            int mode=ops.unsafeCheckOpNoThrow("android:get_usage_stats",android.os.Process.myUid(),getPackageName());
            return mode==android.app.AppOpsManager.MODE_ALLOWED;
        }catch(Exception e){ return false; }
    }

    void openOverlaySettings(){
        try{ startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))); }
        catch(Exception e){ startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
    }

    void openAllFilesSettings(){
        if(Build.VERSION.SDK_INT>=30){
            try{ startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+getPackageName()))); }
            catch(Exception e){ startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        }
    }

    void openUsageSettings(){
        try{ startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }catch(Exception ignored){}
    }

    void requestBatteryOptimization(){
        try{
            android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);
            if(Build.VERSION.SDK_INT>=23 && !pm.isIgnoringBatteryOptimizations(getPackageName())){
                Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:"+getPackageName())); startActivity(i);
            }
        }catch(Exception ignored){}
    }

    void loadData(){
        try{
            JSONArray a=new JSONArray(prefs.getString(APPS,"[]"));
            PackageManager pm=getPackageManager();
            for(int i=0;i<a.length();i++){
                String p=a.getString(i);
                try{
                    ApplicationInfo ai=pm.getApplicationInfo(p,0);
                    apps.add(new AppItem(p,pm.getApplicationLabel(ai).toString()));
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}

        try{
            JSONArray a=new JSONArray(prefs.getString(PRESETS,"[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                presets.add(new Preset(
                        o.getString("name"),o.getInt("x"),o.getInt("y"),
                        o.getInt("w"),o.getInt("h"),o.optInt("displayId",-1),o.optInt("mode",1)
                ));
            }
        }catch(Exception ignored){}
        selectedPackage=prefs.getString("__selected_app_pkg",null);
        selectedName=prefs.getString("__selected_app_name",null);
        if(selectedPackage!=null){
            try{selectedName=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(selectedPackage,0)).toString();}
            catch(Exception ignored){}
        }
    }

    void saveApps(){
        JSONArray a=new JSONArray();
        for(AppItem x:apps)a.put(x.pkg);
        prefs.edit().putString(APPS,a.toString()).apply();
    }

    void savePresets(){
        JSONArray a=new JSONArray();
        try{
            for(Preset p:presets){
                JSONObject o=new JSONObject();
                o.put("name",p.name); o.put("x",p.x); o.put("y",p.y);
                o.put("w",p.w); o.put("h",p.h); o.put("displayId",p.displayId); o.put("mode",p.mode);
                a.put(o);
            }
        }catch(Exception ignored){}
        prefs.edit().putString(PRESETS,a.toString()).apply();
    }

    void buildUI(){
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout frame=new FrameLayout(this);
        boolean hideMainBgAcc=prefs.getBoolean("hide_main_background_acc",false);
        frame.setBackgroundColor(hideMainBgAcc?Color.TRANSPARENT:Color.BLACK);

        TextView accBg=new TextView(this);
        accBg.setText("Acc");
        accBg.setTextColor(0x22FFFFFF);
        accBg.setTextSize(720);
        accBg.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        accBg.setGravity(Gravity.CENTER);
        accBg.setSingleLine(true);
        accBg.setClickable(false);
        if(hideMainBgAcc) accBg.setVisibility(View.GONE);
        frame.addView(accBg,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        // 以下内容均属于主界面，统一使用“主界面字体大小”。
        mainUiContext=true;
        // 主界面固定从顶部 80px 以下开始，避开车机状态栏/触控保留区。
        int topBlank=prefs.getInt("main_top_blank",TOP_BLANK);
        int bottomBlank=prefs.getInt("main_bottom_blank",BOTTOM_BLANK);
        root.setPadding(dp(12),dp(topBlank),dp(12),dp(bottomBlank));
        ScrollView mainScroll=new ScrollView(this);
        mainScroll.setFillViewport(true);
        mainScroll.setVerticalScrollBarEnabled(false);
        mainScroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        frame.addView(mainScroll,new FrameLayout.LayoutParams(-1,-1));

        // “+”统一放在最左边
        LinearLayout presetHeader=new LinearLayout(this);
        presetHeader.setOrientation(LinearLayout.HORIZONTAL);
        presetHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView addPreset=plusButton();
        addPreset.setTextSize(42*mainFontScale());
        addPreset.setContentDescription("新建窗口预设");
        addPreset.setOnClickListener(v->editPreset(-1));
        presetHeader.addView(addPreset,new LinearLayout.LayoutParams(dp(60),dp(60)));
        TextView pt=mainText("窗口预设",51); pt.setTypeface(null,1);
        presetHeader.addView(pt,new LinearLayout.LayoutParams(-2,dp(60)));

        // 直接通过悬浮选位器创建/修改窗口预设：拖动红框到目标位置，
        // 在红框中央填写宽高，确认后自动回填到“新建窗口预设”。
        if(hasOverlayPermission()){
            Button floatingPick=button("悬浮窗选位");
            floatingPick.setTextSize(20*mainFontScale());
            floatingPick.setContentDescription("悬浮窗选位");
            floatingPick.setOnClickListener(v->showFloatingPresetPicker());
            LinearLayout.LayoutParams pickLp=new LinearLayout.LayoutParams(dp(150),dp(44));
            pickLp.setMargins(dp(18),0,0,0);
            presetHeader.addView(floatingPick,pickLp);
        }
        root.addView(presetHeader,new LinearLayout.LayoutParams(-1,dp(64)));

        HorizontalScrollView presetScroll=new HorizontalScrollView(this);
        presetScroll.setFillViewport(false);
        presetScroll.setHorizontalScrollBarEnabled(false);
        presetRow=new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetScroll.addView(presetRow,new HorizontalScrollView.LayoutParams(-2,-1));
        root.addView(presetScroll,new LinearLayout.LayoutParams(-1,dp(188)));

        LinearLayout appHeader=new LinearLayout(this);
        appHeader.setOrientation(LinearLayout.HORIZONTAL);
        appHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView addApp=plusButton();
        addApp.setTextSize(42*mainFontScale());
        addApp.setContentDescription("添加 APP");
        addApp.setOnClickListener(v->chooseApp());
        appHeader.addView(addApp,new LinearLayout.LayoutParams(dp(60),dp(60)));
        TextView at=mainText("APP",51); at.setTypeface(null,1);
        appHeader.addView(at,new LinearLayout.LayoutParams(0,dp(60),1));
        root.addView(appHeader,new LinearLayout.LayoutParams(-1,dp(64)));

        HorizontalScrollView appScroll=new HorizontalScrollView(this);
        appScroll.setFillViewport(true);
        appScroll.setHorizontalScrollBarEnabled(false);
        appGrid=new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.VERTICAL);
        appScroll.addView(appGrid,new HorizontalScrollView.LayoutParams(-2,-1));
        int appColumns=Math.max(1,prefs.getInt("main_app_columns",4));
        int appRows=Math.max(1,(apps.size()+appColumns-1)/appColumns);
        root.addView(appScroll,new LinearLayout.LayoutParams(-1,dp(Math.max(188,appRows*188))));

        // 底部右侧：记事本/设置按钮在上，分辨率等信息统一放在按钮下方，
        // 避免版本、包名等被按钮遮挡。
        LinearLayout footer=new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);

        info=text("",20);
        info.setTextColor(Color.LTGRAY);
        info.setPadding(dp(8),dp(4),dp(4),dp(4));
        footer.addView(info,new LinearLayout.LayoutParams(0,dp(86),1));

        LinearLayout rightFooter=new LinearLayout(this);
        rightFooter.setOrientation(LinearLayout.VERTICAL);
        rightFooter.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);

        LinearLayout actionRow=new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);

        TextView note=plusButton();
        note.setText("📝");
        note.setTextSize(22*mainFontScale());
        note.setContentDescription("记事本");
        note.setOnClickListener(v->showNotes());
        actionRow.addView(note,new LinearLayout.LayoutParams(dp(68),dp(50)));

        TextView closeApp=plusButton();
        closeApp.setText("×");
        closeApp.setTextSize(26*mainFontScale());
        closeApp.setContentDescription("关闭选中的 APP");
        closeApp.setOnClickListener(v->closeSelectedApp());
        actionRow.addView(closeApp,new LinearLayout.LayoutParams(dp(68),dp(50)));

        TextView settings=plusButton();
        settings.setText("⚙");
        settings.setTextSize(24*mainFontScale());
        settings.setContentDescription("设置");
        settings.setOnClickListener(v->showSettingsMenu());
        actionRow.addView(settings,new LinearLayout.LayoutParams(dp(68),dp(50)));

        rightFooter.addView(actionRow,new LinearLayout.LayoutParams(-2,dp(52)));

        // 信息固定在按钮下面，并整体靠右；一行显示，空间不足时从左侧开始裁剪。
        TextView screenInfo=mainText("",20);
        screenInfo.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        screenInfo.setTextColor(Color.WHITE);
        screenInfo.setPadding(dp(6),0,0,0);
        screenInfo.setSingleLine(true);
        screenInfo.setEllipsize(android.text.TextUtils.TruncateAt.START);
        updateScreenInfo(screenInfo);
        rightFooter.addView(screenInfo,new LinearLayout.LayoutParams(dp(900),dp(34)));

        footer.addView(rightFooter,new LinearLayout.LayoutParams(-2,dp(90)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(96)));
        setContentView(frame);
        refresh();
        mainUiContext=false;
    }

    /**
     * 刷新主界面中的窗口预设和已添加 APP。
     * 保持主界面控件对象不变，只重建两个列表，避免重新 setContentView
     * 导致车机 ROM 出现焦点/触控坐标漂移。
     */
    void refresh(){
        mainUiContext=true;
        if(presetRow!=null){
            presetRow.removeAllViews();
            for(int i=0;i<presets.size();i++){
                final int index=i;
                Preset p=presets.get(i);

                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(dp(6),dp(3),dp(6),dp(3));
                card.setBackgroundResource(R.drawable.card);

                TextView title=mainText(p.name,34);
                title.setGravity(Gravity.CENTER);
                title.setMaxLines(1);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(title,new LinearLayout.LayoutParams(-1,dp(54)));

                TextView size=mainText(p.w+" × "+p.h,20);
                size.setTextColor(Color.LTGRAY);
                size.setGravity(Gravity.CENTER);
                card.addView(size,new LinearLayout.LayoutParams(-1,dp(40)));

                TextView pos=mainText("上 "+p.y+"    左 "+p.x,20);
                pos.setTextColor(Color.LTGRAY);
                pos.setGravity(Gravity.CENTER);
                card.addView(pos,new LinearLayout.LayoutParams(-1,dp(40)));

                card.setOnClickListener(v->{
                    if(selectedPackage!=null){
                        launchApp(p);
                    }else{
                        presetMenu(index);
                    }
                });
                card.setOnLongClickListener(v->{presetMenu(index);return true;});

                // 窗口预设选框固定为 200×30dp，超出屏幕后横向滑动选择。
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(200),dp(180));
                lp.setMargins(dp(4),dp(4),dp(4),dp(4));
                presetRow.addView(card,lp);
            }

            if(presets.isEmpty()){
                TextView empty=mainText("点击左侧“+”新建窗口预设",39);
                empty.setTextColor(Color.GRAY);
                empty.setGravity(Gravity.CENTER);
                presetRow.addView(empty,new LinearLayout.LayoutParams(dp(200),dp(180)));
            }
        }

        if(appGrid!=null){
            appGrid.removeAllViews();
            if(apps.isEmpty()){
                TextView empty=mainText("点击“+”添加 APP",42);
                empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER);
                appGrid.addView(empty,new LinearLayout.LayoutParams(dp(200),dp(180)));
            }else{
                PackageManager pm=getPackageManager();
                LinearLayout row=null;
                int inRow=0;
                for(int index=0;index<apps.size();index++){
                    final int itemIndex=index;
                    AppItem item=apps.get(index);
                    if(inRow==0){
                        row=new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        appGrid.addView(row,new LinearLayout.LayoutParams(-1,dp(188)));
                    }
                    LinearLayout tile=new LinearLayout(this);
                    tile.setOrientation(LinearLayout.VERTICAL);
                    tile.setGravity(Gravity.CENTER);
                    tile.setPadding(dp(8),dp(8),dp(8),dp(8));
                    tile.setBackgroundResource(item.pkg.equals(selectedPackage)?R.drawable.card_selected:R.drawable.card);

                    ImageView icon=new ImageView(this);
                    icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    try{icon.setImageDrawable(pm.getApplicationIcon(item.pkg));}catch(Exception ignored){}
                    tile.addView(icon,new LinearLayout.LayoutParams(dp(82),dp(82)));

                    TextView name=mainText(item.name,36);
                    name.setGravity(Gravity.CENTER);
                    name.setMaxLines(2);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    tile.addView(name,new LinearLayout.LayoutParams(-1,dp(66)));

                    tile.setOnClickListener(v->{
                        long now=System.currentTimeMillis();
                        String lastPkg=prefs.getString("last_app_click_pkg","");
                        long lastTime=prefs.getLong("last_app_click_time",0);
                        selectedPackage=item.pkg; selectedName=item.name;
                        prefs.edit().putString("__selected_app_pkg",item.pkg).putString("__selected_app_name",item.name).apply();
                        info.setText("当前 APP："+item.name);
                        if(item.pkg.equals(lastPkg) && now-lastTime<=420){
                            prefs.edit().remove("last_app_click_pkg").remove("last_app_click_time").apply();
                            launchAppDirect(item.pkg,item.name);
                        }else{
                            prefs.edit().putString("last_app_click_pkg",item.pkg).putLong("last_app_click_time",now).apply();
                            refresh();
                        }
                    });
                    tile.setOnLongClickListener(v->{
                        new AlertDialog.Builder(this).setTitle(item.name)
                            .setItems(new String[]{"删除 APP"},(d,w)->{if(w==0){if(item.pkg.equals(selectedPackage)){selectedPackage=null;selectedName=null;prefs.edit().remove("__selected_app_pkg").remove("__selected_app_name").apply();}apps.remove(itemIndex);saveApps();refresh();}}).show();
                        return true;
                    });
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(200),dp(180));
                    lp.setMargins(dp(4),dp(4),dp(4),dp(4));
                    row.addView(tile,lp);
                    inRow++;
                    int columns=Math.max(1,prefs.getInt("main_app_columns",4));
                    if(inRow>=columns) inRow=0;
                }
            }
        }
        mainUiContext=false;
    }

    void updateScreenInfo(TextView view){
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        android.graphics.Point rs=getRealScreenSize();

        String packageName=getPackageName();
        String versionName="未知";
        long versionCode=0;
        String signature="未知";

        try{
            android.content.pm.PackageManager pm=getPackageManager();
            android.content.pm.PackageInfo pi;
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P){
                pi=pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                versionCode=pi.getLongVersionCode();
                if(pi.versionName!=null) versionName=pi.versionName;

                android.content.pm.SigningInfo si=pi.signingInfo;
                if(si!=null){
                    android.content.pm.Signature[] sigs=si.hasMultipleSigners()
                            ? si.getApkContentsSigners()
                            : si.getSigningCertificateHistory();
                    if(sigs!=null && sigs.length>0){
                        java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");
                        byte[] digest=md.digest(sigs[0].toByteArray());
                        StringBuilder sb=new StringBuilder();
                        for(byte b:digest){
                            sb.append(String.format(java.util.Locale.US,"%02X",b));
                        }
                        String fullSignature=sb.toString();
                        signature=fullSignature.substring(0,Math.min(16,fullSignature.length()));
                    }
                }
            }else{
                pi=pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES);
                versionCode=pi.versionCode;
                if(pi.versionName!=null) versionName=pi.versionName;
                if(pi.signatures!=null && pi.signatures.length>0){
                    java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");
                    byte[] digest=md.digest(pi.signatures[0].toByteArray());
                    StringBuilder sb=new StringBuilder();
                    for(byte b:digest){
                        sb.append(String.format(java.util.Locale.US,"%02X",b));
                    }
                    String fullSignature=sb.toString();
                    signature=fullSignature.substring(0,Math.min(16,fullSignature.length()));
                }
            }
        }catch(Exception ignored){}

        view.setText(
                "分辨率 " + rs.x + " × " + rs.y + "    DPI " + dm.densityDpi
                + "    包名 " + packageName
                + "    版本 " + versionName + "    versionCode " + versionCode
        );
    }

    /**
     * 统一处理所有弹出窗口的坐标。
     *
     * 车机触控偏移的核心问题是：之前把 Dialog Window 做成整屏
     * FLAG_LAYOUT_IN_SCREEN，并把内容通过 DecorView padding 向下推。
     * 在部分车机 ROM 上，视觉坐标与触摸坐标因此不在同一个窗口坐标系。
     *
     * 现在改成普通应用 Dialog：窗口本身直接位于顶部 80px 以下，
     * 不再使用 FLAG_LAYOUT_IN_SCREEN / LAYOUT_FULLSCREEN。这样窗口的
     * 左上角就是它实际接收触摸事件的左上角，视觉位置和点击位置保持一致。
     * 同时限制左右宽度，避免超宽车机上弹窗铺满整个屏幕。
     */
    void placeDialogBelowTop(Dialog dialog){
        if(dialog==null || dialog.getWindow()==null) return;
        Window w=dialog.getWindow();
        try{
            // 关键修复：不再使用 TOP/LEFT + lp.x/lp.y。
            // 车机超宽屏上 Window 坐标与触摸坐标可能存在状态栏/安全区偏移，
            // 这是之前所有弹窗点击位置偏移的主要来源。
            w.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.setBackgroundDrawable(new ColorDrawable(Color.rgb(24,24,24)));
            if(Build.VERSION.SDK_INT>=19){
                w.getDecorView().setSystemUiVisibility(0);
            }

            android.graphics.Point screen=getRealScreenSize();
            int left=Math.max(0,prefs.getInt("dialog_left_margin_px",40));
            int right=Math.max(0,prefs.getInt("dialog_right_margin_px",40));

            // 弹窗默认缩小：不再占满超宽屏；同时保留设置中的左右边距。
            int available=Math.max(dp(320),screen.x-left-right);
            int maxWidth=(int)(screen.x*0.70f);
            maxWidth=Math.min(maxWidth,dp(1200));
            int width=Math.min(available,maxWidth);
            width=Math.max(dp(320),width);
            width=Math.min(width,Math.max(dp(320),screen.x-dp(20)));

            // 统一居中，不设置 lp.x/lp.y。这样 Dialog 的绘制坐标和触摸坐标
            // 都由同一个 Window 管理，避免车机 ROM 造成上下/左右触控偏移。
            WindowManager.LayoutParams lp=w.getAttributes();
            lp.gravity=Gravity.CENTER;
            // 触控纠正不能再通过 lp.x/lp.y 移动整个弹窗。
            // 那样只会改变视觉位置，车机 ROM 的触控坐标偏移依然存在。
            // 这里保持弹窗视觉位置不变，真正修正 Window.Callback 收到的触摸坐标。
            lp.x=0;
            lp.y=0;
            lp.width=width;
            lp.height=WindowManager.LayoutParams.WRAP_CONTENT;
            lp.dimAmount=0.55f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            installTouchCorrection(dialog);
        }catch(Exception ignored){}
    }

    /**
     * 真正修正车机弹窗触控坐标。
     * 正值“上”表示把触点坐标向上修正，正值“左”表示把触点坐标向左修正。
     * 主界面完全不经过这里，因此主界面触控不会受到影响。
     */
    void installTouchCorrection(Dialog dialog){
        if(dialog==null || dialog.getWindow()==null) return;
        final Window window=dialog.getWindow();
        final Window.Callback original=window.getCallback();
        if(original==null || original instanceof TouchCorrectingCallback) return;
        window.setCallback(new TouchCorrectingCallback(original, touchOffsetLeft(), touchOffsetTop()));
    }

    static class TouchCorrectingCallback implements Window.Callback{
        final Window.Callback delegate;
        final int left, top;
        TouchCorrectingCallback(Window.Callback d,int l,int t){delegate=d;left=l;top=t;}
        public boolean dispatchKeyEvent(KeyEvent e){return delegate.dispatchKeyEvent(e);}
        public boolean dispatchKeyShortcutEvent(KeyEvent e){return delegate.dispatchKeyShortcutEvent(e);}
        public boolean dispatchTouchEvent(MotionEvent e){
            MotionEvent copy=MotionEvent.obtain(e);
            try{
                // 修正的是触摸坐标，不移动弹窗视觉位置。
                copy.offsetLocation(-left,-top);
                return delegate.dispatchTouchEvent(copy);
            }finally{copy.recycle();}
        }
        public boolean dispatchTrackballEvent(MotionEvent e){return delegate.dispatchTrackballEvent(e);}
        public boolean dispatchGenericMotionEvent(MotionEvent e){return delegate.dispatchGenericMotionEvent(e);}
        public boolean dispatchPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent e){return delegate.dispatchPopulateAccessibilityEvent(e);}
        public android.view.View onCreatePanelView(int featureId){return delegate.onCreatePanelView(featureId);}
        public boolean onCreatePanelMenu(int featureId, android.view.Menu menu){return delegate.onCreatePanelMenu(featureId,menu);}
        public boolean onPreparePanel(int featureId, android.view.View view, android.view.Menu menu){return delegate.onPreparePanel(featureId,view,menu);}
        public boolean onMenuOpened(int featureId, android.view.Menu menu){return delegate.onMenuOpened(featureId,menu);}
        public boolean onMenuItemSelected(int featureId, android.view.MenuItem item){return delegate.onMenuItemSelected(featureId,item);}
        public void onWindowAttributesChanged(WindowManager.LayoutParams attrs){delegate.onWindowAttributesChanged(attrs);}
        public void onContentChanged(){delegate.onContentChanged();}
        public void onWindowFocusChanged(boolean hasFocus){delegate.onWindowFocusChanged(hasFocus);}
        public void onAttachedToWindow(){delegate.onAttachedToWindow();}
        public void onDetachedFromWindow(){delegate.onDetachedFromWindow();}
        public void onPanelClosed(int featureId, android.view.Menu menu){delegate.onPanelClosed(featureId,menu);}
        public boolean onSearchRequested(){return delegate.onSearchRequested();}
        public boolean onSearchRequested(SearchEvent event){return Build.VERSION.SDK_INT>=23 ? delegate.onSearchRequested(event) : false;}
        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback){return delegate.onWindowStartingActionMode(callback);}
        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback,int type){return Build.VERSION.SDK_INT>=23 ? delegate.onWindowStartingActionMode(callback,type) : null;}
        public void onActionModeStarted(ActionMode mode){delegate.onActionModeStarted(mode);}
        public void onActionModeFinished(ActionMode mode){delegate.onActionModeFinished(mode);}
    }

    void styleDialogActionButtons(AlertDialog dialog){
        if(dialog==null) return;
        try{
            Button negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button neutral=dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            Button[] buttons={negative,positive,neutral};
            for(Button b:buttons){
                if(b==null) continue;
                b.setAllCaps(false);
                b.setTextColor(Color.WHITE);
                b.setTextSize(14*fontScale());
                b.setBackgroundResource(R.drawable.button);
                b.setMinHeight(dp(46));
                b.setMinWidth(dp(100));
                b.setPadding(dp(14),0,dp(14),0);
            }
        }catch(Exception ignored){}
    }

    void showDialogBelowTop(AlertDialog dialog){
        if(dialog==null) return;
        dialog.show();
        styleDialogActionButtons(dialog);
        adaptDialogBoxes(dialog);
        placeDialogBelowTop(dialog);
    }

    // 用户指定的标准设置/编辑页面尺寸：1000×800 物理像素。
    // 在显示器小于该尺寸时保持居中并由系统裁剪，避免根据屏幕 density 改变页面设计尺寸。
    void showFixed1000x800(AlertDialog dialog){
        if(dialog==null) return;
        dialog.show();
        styleDialogActionButtons(dialog);
        adaptDialogBoxes(dialog);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setLayout(1000,800);
            w.setGravity(Gravity.CENTER);
        }
    }

    void showSettingsMenu(){
        final AlertDialog[] settingsDialog={null};
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24),dp(10),dp(24),dp(12));

        LinearLayout bootRow=new LinearLayout(this); bootRow.setGravity(Gravity.CENTER_VERTICAL);
        bootRow.addView(text("开机启动",15),new LinearLayout.LayoutParams(0,dp(52),1));
        Switch appBoot=new Switch(this);
        appBoot.setChecked(prefs.getBoolean("app_boot_enabled",false));
        appBoot.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("app_boot_enabled",checked).apply());
        bootRow.addView(appBoot,new LinearLayout.LayoutParams(dp(58),dp(52)));
        box.addView(bootRow,new LinearLayout.LayoutParams(-1,dp(58)));

        Button interfaceButton=button("界面选项  ›");
        interfaceButton.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        interfaceButton.setPadding(dp(14),0,dp(14),0);
        interfaceButton.setOnClickListener(v->showInterfaceOptionsDialog());
        box.addView(interfaceButton,new LinearLayout.LayoutParams(-1,dp(52)));

        Button autoButton=button("自动启动任务  ›");
        autoButton.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        autoButton.setPadding(dp(14),0,dp(14),0);
        autoButton.setOnClickListener(v->showAutoStartEditor());
        box.addView(autoButton,new LinearLayout.LayoutParams(-1,dp(52)));

        if(hasOverlayPermission()){
            Button floatSettings=button("悬浮窗口设置  ›");
            floatSettings.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            floatSettings.setPadding(dp(14),0,dp(14),0);
            floatSettings.setOnClickListener(v->showFloatingWindowSettingsDialog());
            box.addView(floatSettings,new LinearLayout.LayoutParams(-1,dp(52)));
        }

        Button permissions=button("权限与诊断");
        permissions.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        permissions.setPadding(dp(14),0,dp(14),0);
        permissions.setOnClickListener(v->{if(settingsDialog[0]!=null)settingsDialog[0].dismiss();showScreenDiagnostics();});
        box.addView(permissions,new LinearLayout.LayoutParams(-1,dp(48)));

        Button exportButton=button("导出配置");
        exportButton.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        exportButton.setPadding(dp(14),0,dp(14),0);
        exportButton.setOnClickListener(v->exportConfig());
        box.addView(exportButton,new LinearLayout.LayoutParams(-1,dp(48)));

        Button importButton=button("导入配置");
        importButton.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        importButton.setPadding(dp(14),0,dp(14),0);
        importButton.setOnClickListener(v->importConfig());
        box.addView(importButton,new LinearLayout.LayoutParams(-1,dp(48)));

        settingsDialog[0]=new AlertDialog.Builder(this).setTitle("设置").setView(box).setNegativeButton("关闭",null).create();
        showFixed1000x800(settingsDialog[0]);
    }

    void showFloatingWindowSettingsDialog(){
        // 悬浮窗口尺寸由内容自动适应，位置只通过拖动调整。保存后窗口保持打开。
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(6),dp(18),dp(10));

        ScrollView scroll=new ScrollView(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6),dp(4),dp(6),dp(8));

        LinearLayout enableRow=new LinearLayout(this); enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.addView(text("悬浮窗口开关",14),new LinearLayout.LayoutParams(0,dp(52),1));
        Switch enable=new Switch(this);
        enable.setChecked(prefs.getBoolean("floating_enabled",false));
        enableRow.addView(enable,new LinearLayout.LayoutParams(dp(58),dp(52)));
        box.addView(enableRow,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout direction=new LinearLayout(this); direction.setGravity(Gravity.CENTER_VERTICAL);
        direction.addView(text("排列方向",14),new LinearLayout.LayoutParams(0,dp(52),1));
        Button dirBtn=button(prefs.getBoolean("floating_vertical",false)?"竖向":"横向");
        direction.addView(dirBtn,new LinearLayout.LayoutParams(dp(110),dp(48)));
        dirBtn.setOnClickListener(v->{
            boolean vertical=!prefs.getBoolean("floating_vertical",false);
            prefs.edit().putBoolean("floating_vertical",vertical).apply();
            dirBtn.setText(vertical?"竖向":"横向");
        });
        box.addView(direction,new LinearLayout.LayoutParams(-1,dp(58)));

        EditText spacing=numberField("6",String.valueOf(prefs.getInt("floating_button_spacing_px",6)));
        box.addView(labeledSimpleNumberField("按钮图标间距",spacing),new LinearLayout.LayoutParams(-1,dp(54)));

        EditText icon=numberField("44",String.valueOf(prefs.getInt("floating_icon_size_px",44)));
        box.addView(labeledSimpleNumberField("按钮图标大小",icon),new LinearLayout.LayoutParams(-1,dp(54)));

        EditText opacity=numberField("80",String.valueOf(prefs.getInt("floating_background_opacity",80)));
        box.addView(labeledSimpleNumberField("悬浮窗口透明度",opacity),new LinearLayout.LayoutParams(-1,dp(54)));
        TextView opacityHint=text("0 = 完全透明，100 = 完全不透明。悬浮窗口四角保持圆角透明效果。",10);
        opacityHint.setTextColor(Color.GRAY);
        box.addView(opacityHint,new LinearLayout.LayoutParams(-1,dp(36)));

        // 单图标模式：整个悬浮窗口只显示一个可配置 APP 图标。
        LinearLayout singleRow=new LinearLayout(this); singleRow.setGravity(Gravity.CENTER_VERTICAL);
        singleRow.addView(text("单图标模式",14),new LinearLayout.LayoutParams(0,dp(52),1));
        Switch singleMode=new Switch(this);
        singleMode.setChecked(prefs.getBoolean("floating_single_icon_mode",false));
        singleRow.addView(singleMode,new LinearLayout.LayoutParams(dp(58),dp(52)));
        box.addView(singleRow,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout shapeRow=new LinearLayout(this); shapeRow.setGravity(Gravity.CENTER_VERTICAL);
        shapeRow.addView(text("单图标形状",14),new LinearLayout.LayoutParams(0,dp(52),1));
        Button shapeBtn=button("圆角正方形".equals(prefs.getString("floating_single_icon_shape","rounded"))?"圆角正方形":"圆形");
        shapeBtn.setGravity(Gravity.CENTER);
        shapeRow.addView(shapeBtn,new LinearLayout.LayoutParams(dp(140),dp(48)));
        shapeBtn.setOnClickListener(v->{
            String next="rounded".equals(prefs.getString("floating_single_icon_shape","rounded"))?"circle":"rounded";
            prefs.edit().putString("floating_single_icon_shape",next).apply();
            shapeBtn.setText("circle".equals(next)?"圆形":"圆角正方形");
        });
        box.addView(shapeRow,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout lockRow=new LinearLayout(this); lockRow.setGravity(Gravity.CENTER_VERTICAL);
        lockRow.addView(text("锁定悬浮窗口位置",14),new LinearLayout.LayoutParams(0,dp(52),1));
        Switch lockSwitch=new Switch(this); lockSwitch.setChecked(prefs.getBoolean("floating_position_locked",false));
        lockRow.addView(lockSwitch,new LinearLayout.LayoutParams(dp(58),dp(52)));
        box.addView(lockRow,new LinearLayout.LayoutParams(-1,dp(58)));

        TextView gestureTitle=text("单图标手势功能",14); gestureTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        box.addView(gestureTitle,new LinearLayout.LayoutParams(-1,dp(42)));
        String[] gestureKeys={"tap","double","long","left","right","up","down"};
        String[] gestureNames={"点击","双击","长按","左滑","右滑","上滑","下滑"};
        for(int gi=0;gi<gestureKeys.length;gi++){
            final String gk=gestureKeys[gi], gn=gestureNames[gi];
            LinearLayout gr=new LinearLayout(this); gr.setGravity(Gravity.CENTER_VERTICAL);
            gr.addView(text(gn,13),new LinearLayout.LayoutParams(0,dp(48),1));
            Button gb=button(getGestureLabel(prefs.getString("floating_gesture_"+gk,"none")));
            gb.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT); gb.setPadding(dp(10),0,dp(8),0);
            gr.addView(gb,new LinearLayout.LayoutParams(dp(190),dp(46)));
            gb.setOnClickListener(v->showGestureChooser(gk,gn,gb));
            box.addView(gr,new LinearLayout.LayoutParams(-1,dp(50)));
        }

        TextView hint=text("悬浮窗口大小自动适应内容，不需要设置宽度和高度。悬浮窗口可以直接拖动位置。保存后本窗口不会关闭，可以继续调整。",11);
        hint.setTextColor(Color.GRAY); hint.setPadding(0,dp(4),0,dp(8));
        box.addView(hint,new LinearLayout.LayoutParams(-1,dp(58)));
        scroll.addView(box,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actionBar=new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER);
        actionBar.setPadding(dp(4),dp(8),dp(4),0);

        Button back=button("返回");
        Button save=button("保存");
        actionBar.addView(back,new LinearLayout.LayoutParams(0,dp(50),1));
        actionBar.addView(save,new LinearLayout.LayoutParams(0,dp(50),1));
        root.addView(actionBar,new LinearLayout.LayoutParams(-1,dp(64)));

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("悬浮窗口设置")
                .setView(root)
                .create();

        back.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            int sp=number(spacing,6), ic=number(icon,44), op=number(opacity,80);
            if(sp<0||sp>80||ic<20||ic>200||op<0||op>100){
                Toast.makeText(this,"范围：间距0-80，图标20-200，透明度0-100",Toast.LENGTH_LONG).show();
                return;
            }
            boolean oldEnabled=prefs.getBoolean("floating_enabled",false);
            prefs.edit().putBoolean("floating_enabled",enable.isChecked())
                    .putInt("floating_button_spacing_px",sp)
                    .putInt("floating_icon_size_px",ic)
                    .putInt("floating_background_opacity",op)
                    .putBoolean("floating_single_icon_mode",singleMode.isChecked())
                    .putBoolean("floating_position_locked",lockSwitch.isChecked())
                    .apply();

            if(enable.isChecked()){
                if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
                    enable.setChecked(false);
                    prefs.edit().putBoolean("floating_enabled",false).apply();
                    Toast.makeText(this,"请先允许本 APP 显示在其他应用上层",Toast.LENGTH_LONG).show();
                    openOverlaySettings();
                    return;
                }
                stopFloatingService();
                startFloatingService();
            }else if(oldEnabled){
                stopFloatingService();
            }
            Toast.makeText(this,"悬浮窗口设置已保存，可继续调整",Toast.LENGTH_SHORT).show();
            // 不 dismiss，保持设置窗口。
        });
        showFixed1000x800(dialog);
    }

    String getAppLabelSafe(String pkg){
        try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}
        catch(Exception e){return pkg;}
    }

    String getGestureLabel(String value){
        if(value==null||value.isEmpty()||"none".equals(value))return "无操作";
        if("back".equals(value))return "返回按钮";
        if("home".equals(value))return "首页按钮";
        if("menu".equals(value))return "菜单按钮";
        if(value.startsWith("app:"))return getAppLabelSafe(value.substring(4));
        return value;
    }

    void showGestureChooser(String key,String title,Button target){
        ArrayList<String> values=new ArrayList<>();
        ArrayList<String> labels=new ArrayList<>();
        values.add("none"); labels.add("无操作");
        values.add("back"); labels.add("返回按钮");
        values.add("home"); labels.add("首页按钮");
        values.add("menu"); labels.add("菜单按钮");

        // 已加入悬浮窗口的 APP
        try{
            JSONArray a=new JSONArray(prefs.getString("floating_apps","[]"));
            for(int i=0;i<a.length();i++){
                String pkg=a.getJSONObject(i).optString("pkg","");
                if(!pkg.isEmpty() && getPackageManager().getLaunchIntentForPackage(pkg)!=null
                        && !values.contains("app:"+pkg)){
                    values.add("app:"+pkg);
                    labels.add(getAppLabelSafe(pkg));
                }
            }
        }catch(Exception ignored){}

        final ArrayList<String> actionValues=values;
        final ArrayList<String> actionLabels=labels;
        final AlertDialog[] dialogRef={null};

        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18),dp(8),dp(18),dp(12));

        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<actionLabels.size();i++){
            final int index=i;
            Button item=button(actionLabels.get(i));
            item.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            item.setPadding(dp(16),0,dp(12),0);
            LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(-1,dp(48));
            ilp.setMargins(0,dp(3),0,dp(3));
            list.addView(item,ilp);
            item.setOnClickListener(v->{
                prefs.edit().putString("floating_gesture_"+key,actionValues.get(index)).apply();
                target.setText(actionLabels.get(index));
                dialogRef[0].dismiss();
            });
        }
        content.addView(list,new LinearLayout.LayoutParams(-1,-2));

        // “＋增加APP操作”使用与其它按钮完全一致的按钮样式，并靠左排列。
        Button add=button("＋增加APP操作");
        add.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        add.setPadding(dp(16),0,dp(12),0);
        LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(-1,dp(48));
        addLp.setMargins(0,dp(8),0,0);
        content.addView(add,addLp);

        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle(title+"功能")
                .setView(content)
                .setNegativeButton("取消",null)
                .create();
        dialogRef[0]=dlg;
        add.setOnClickListener(v->showGestureAppChooser(key,title,target,dlg));
        dlg.show();
        adaptDialogBoxes(dlg);
    }

    void showGestureAppChooser(String key,String title,Button target,AlertDialog parent){
        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> list=new ArrayList<>();
        for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(ai.packageName.equals(getPackageName()))continue;
            if(pm.getLaunchIntentForPackage(ai.packageName)==null)continue;
            list.add(ai);
        }
        Collections.sort(list,(a,b)->getAppLabelSafe(a.packageName).compareToIgnoreCase(getAppLabelSafe(b.packageName)));
        String[] labels=new String[list.size()];
        for(int i=0;i<list.size();i++)labels[i]=getAppLabelSafe(list.get(i).packageName);

        new AlertDialog.Builder(this)
                .setTitle("为“"+title+"”增加 APP 操作")
                .setItems(labels,(d,w)->{
                    String pkg=list.get(w).packageName;
                    prefs.edit().putString("floating_gesture_"+key,"app:"+pkg).apply();
                    target.setText(labels[w]);
                    if(parent!=null)parent.dismiss();
                })
                .setNegativeButton("取消",null)
                .show();
    }

    void showInterfaceOptionsDialog(){
        final AlertDialog[] dialogRef={null};

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(8),dp(24),dp(10));

        // 设置项区域可上下滚动，底部返回/保存按钮固定可见。
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout delayRow=new LinearLayout(this); delayRow.setGravity(Gravity.CENTER_VERTICAL);
        delayRow.addView(text("开机延迟启动（秒）",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText bootDelay=numberField("0",String.valueOf(prefs.getInt("boot_delay_seconds",0)));
        delayRow.addView(bootDelay,new LinearLayout.LayoutParams(dp(90),dp(52)));
        box.addView(delayRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout fontRow=new LinearLayout(this); fontRow.setGravity(Gravity.CENTER_VERTICAL);
        fontRow.addView(text("主界面字体大小",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText fontInput=numberField("100",String.valueOf(Math.round(prefs.getFloat("main_font_scale",1.00f)*100)));
        fontRow.addView(fontInput,new LinearLayout.LayoutParams(dp(82),dp(52)));
        fontRow.addView(text("%",14),new LinearLayout.LayoutParams(dp(28),dp(52)));
        box.addView(fontRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout menuFontRow=new LinearLayout(this); menuFontRow.setGravity(Gravity.CENTER_VERTICAL);
        menuFontRow.addView(text("菜单字体大小",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText menuFontInput=numberField("100",String.valueOf(Math.round(prefs.getFloat("font_scale",1.00f)*100)));
        menuFontRow.addView(menuFontInput,new LinearLayout.LayoutParams(dp(82),dp(52)));
        menuFontRow.addView(text("%",14),new LinearLayout.LayoutParams(dp(28),dp(52)));
        box.addView(menuFontRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout uiRow=new LinearLayout(this); uiRow.setGravity(Gravity.CENTER_VERTICAL);
        uiRow.addView(text("主界面界面大小",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText uiInput=numberField("100",String.valueOf(Math.round(prefs.getFloat("ui_scale",1.0f)*100)));
        uiRow.addView(uiInput,new LinearLayout.LayoutParams(dp(82),dp(52)));
        uiRow.addView(text("%",14),new LinearLayout.LayoutParams(dp(28),dp(52)));
        box.addView(uiRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout columnsRow=new LinearLayout(this); columnsRow.setGravity(Gravity.CENTER_VERTICAL);
        columnsRow.addView(text("主界面 APP 每排数量",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText columnsInput=numberField("4",String.valueOf(prefs.getInt("main_app_columns",4)));
        columnsRow.addView(columnsInput,new LinearLayout.LayoutParams(dp(82),dp(52)));
        columnsRow.addView(text("个",14),new LinearLayout.LayoutParams(dp(28),dp(52)));
        box.addView(columnsRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout topBlankRow=new LinearLayout(this); topBlankRow.setGravity(Gravity.CENTER_VERTICAL);
        topBlankRow.addView(text("主界面上空白",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText topBlankInput=numberField("80",String.valueOf(prefs.getInt("main_top_blank",80)));
        topBlankRow.addView(topBlankInput,new LinearLayout.LayoutParams(dp(82),dp(52)));
        topBlankRow.addView(text("px",13),new LinearLayout.LayoutParams(dp(30),dp(52)));
        box.addView(topBlankRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout bottomBlankRow=new LinearLayout(this); bottomBlankRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomBlankRow.addView(text("主界面下空白",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText bottomBlankInput=numberField("120",String.valueOf(prefs.getInt("main_bottom_blank",120)));
        bottomBlankRow.addView(bottomBlankInput,new LinearLayout.LayoutParams(dp(82),dp(52)));
        bottomBlankRow.addView(text("px",13),new LinearLayout.LayoutParams(dp(30),dp(52)));
        box.addView(bottomBlankRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout hideBgRow=new LinearLayout(this); hideBgRow.setGravity(Gravity.CENTER_VERTICAL);
        hideBgRow.addView(text("隐藏主界面背景和Acc",14),new LinearLayout.LayoutParams(0,dp(52),1));
        Switch hideBgSwitch=new Switch(this);
        hideBgSwitch.setChecked(prefs.getBoolean("hide_main_background_acc",false));
        hideBgRow.addView(hideBgSwitch,new LinearLayout.LayoutParams(dp(70),dp(52)));
        box.addView(hideBgRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout touchTopRow=new LinearLayout(this); touchTopRow.setGravity(Gravity.CENTER_VERTICAL);
        touchTopRow.addView(text("触控纠正位置上",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText touchTopInput=numberField("0",String.valueOf(touchOffsetTop()));
        touchTopRow.addView(touchTopInput,new LinearLayout.LayoutParams(dp(90),dp(52)));
        touchTopRow.addView(text("px",13),new LinearLayout.LayoutParams(dp(30),dp(52)));
        box.addView(touchTopRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout touchLeftRow=new LinearLayout(this); touchLeftRow.setGravity(Gravity.CENTER_VERTICAL);
        touchLeftRow.addView(text("触控纠正位置左",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText touchLeftInput=numberField("0",String.valueOf(touchOffsetLeft()));
        touchLeftRow.addView(touchLeftInput,new LinearLayout.LayoutParams(dp(90),dp(52)));
        touchLeftRow.addView(text("px",13),new LinearLayout.LayoutParams(dp(30),dp(52)));
        box.addView(touchLeftRow,new LinearLayout.LayoutParams(-1,dp(56)));

        TextView hint=text("触控纠正只作用于弹窗，主界面不受影响。正值向下/向右，负值向上/向左。",11);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0,dp(4),0,dp(6));
        box.addView(hint,new LinearLayout.LayoutParams(-1,dp(38)));

        scroll.addView(box,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actionBar=new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("返回");
        Button save=button("保存");
        LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(0,dp(50),1);
        actionLp.setMargins(dp(4),dp(4),dp(4),0);
        actionBar.addView(back,new LinearLayout.LayoutParams(0,dp(50),1));
        actionBar.addView(save,new LinearLayout.LayoutParams(0,dp(50),1));
        root.addView(actionBar,new LinearLayout.LayoutParams(-1,dp(58)));

        save.setOnClickListener(v->{
            try{
                int delay=Integer.parseInt(bootDelay.getText().toString().trim());
                float fs=Float.parseFloat(fontInput.getText().toString().trim());
                float mfs=Float.parseFloat(menuFontInput.getText().toString().trim());
                float us=Float.parseFloat(uiInput.getText().toString().trim());
                int columns=Integer.parseInt(columnsInput.getText().toString().trim());
                int topBlank=Integer.parseInt(topBlankInput.getText().toString().trim());
                int bottomBlank=Integer.parseInt(bottomBlankInput.getText().toString().trim());
                int touchTop=Integer.parseInt(touchTopInput.getText().toString().trim());
                int touchLeft=Integer.parseInt(touchLeftInput.getText().toString().trim());
                if(delay<0||delay>3600||fs<20||fs>300||mfs<20||mfs>300||us<50||us>300||
                   columns<1||columns>20||topBlank<0||topBlank>2000||bottomBlank<0||bottomBlank>2000||
                   touchTop<-2000||touchTop>2000||touchLeft<-2000||touchLeft>2000) throw new Exception();

                prefs.edit().putInt("boot_delay_seconds",delay)
                        .putFloat("main_font_scale",fs/100f)
                        .putFloat("font_scale",mfs/100f)
                        .putFloat("ui_scale",us/100f)
                        .putInt("main_app_columns",columns)
                        .putInt("main_top_blank",topBlank)
                        .putInt("main_bottom_blank",bottomBlank)
                        .putBoolean("hide_main_background_acc",hideBgSwitch.isChecked())
                        .putInt("touch_offset_top_px",touchTop)
                        .putInt("touch_offset_left_px",touchLeft)
                        // 清理旧版本已经删除的弹窗左右距离配置。
                        .remove("dialog_left_margin_px")
                        .remove("dialog_right_margin_px")
                        .apply();

                Toast.makeText(this,"设置已保存并生效",Toast.LENGTH_SHORT).show();
                if(dialogRef[0]!=null) dialogRef[0].dismiss();
                buildUI();
            }catch(Exception e){
                Toast.makeText(this,"请输入有效数值：延迟0-3600秒，主界面字体20-300%，菜单字体20-300%，界面50-300%",Toast.LENGTH_LONG).show();
            }
        });

        dialogRef[0]=new AlertDialog.Builder(this).setTitle("界面选项").setView(root).create();
        back.setOnClickListener(v->dialogRef[0].dismiss());
        showFixed1000x800(dialogRef[0]);
    }

    /** 添加 APP：全部/用户/系统分类，正方形图标+名称；APP 很多时可上下滚动。 */
    void chooseApp(){
        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> list=new ArrayList<>();
        for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(ai.packageName.equals(getPackageName())) continue;
            try{
                CharSequence label=pm.getApplicationLabel(ai);
                if(label!=null && label.toString().trim().length()>0) list.add(ai);
            }catch(Exception ignored){}
        }
        Collections.sort(list,(a,b)->pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));

        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12),dp(6),dp(12),dp(6));
        EditText search=textField("搜索 APP","");
        box.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));

        final int[] selectedAppCategory={0};
        final Runnable[] refreshHolder={null};
        LinearLayout tabs=new LinearLayout(this); tabs.setGravity(Gravity.CENTER_VERTICAL);
        String[] cats={"全部","用户","系统"}; Button[] tabBtns=new Button[cats.length];
        for(int i=0;i<cats.length;i++){
            final int ci=i; Button b=button(cats[i]); b.setTextSize(12*fontScale()); tabBtns[i]=b;
            tabs.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));
            b.setOnClickListener(v->{
                selectedAppCategory[0]=ci;
                for(int j=0;j<tabBtns.length;j++) tabBtns[j].setBackgroundResource(j==ci?R.drawable.card_selected:R.drawable.button);
                refreshHolder[0].run();
            });
        }
        box.addView(tabs,new LinearLayout.LayoutParams(-1,dp(46)));

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(true);
        LinearLayout appRows=new LinearLayout(this);
        appRows.setOrientation(LinearLayout.VERTICAL);
        appRows.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(appRows,new ScrollView.LayoutParams(-1,-2));
        box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加 APP").setView(box).setNegativeButton("关闭",null).create();
        Runnable refreshAppPicker=()->{
            appRows.removeAllViews();
            String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int count=0;
            int availableDp=Math.max(560,(int)(getRealScreenSize().x/getResources().getDisplayMetrics().density/uiScale())-28);
            int tileDp=Math.max(104,Math.min(150,(availableDp-48)/5));
            LinearLayout row=null;
            int inRow=0;
            for(ApplicationInfo ai:list){
                boolean system=(ai.flags & ApplicationInfo.FLAG_SYSTEM)!=0 || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;
                if(selectedAppCategory[0]==1 && system) continue;
                if(selectedAppCategory[0]==2 && !system) continue;
                String name=pm.getApplicationLabel(ai).toString();
                if(!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !ai.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
                if(inRow==0){
                    row=new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER);
                    appRows.addView(row,new LinearLayout.LayoutParams(-1,dp(124)));
                }
                LinearLayout tile=new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(6),dp(6),dp(6),dp(6)); tile.setBackgroundResource(R.drawable.card);
                ImageView icon=new ImageView(this); icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                try{icon.setImageDrawable(pm.getApplicationIcon(ai));}catch(Exception ignored){}
                tile.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));
                TextView nv=text(name,11); nv.setGravity(Gravity.CENTER); nv.setMaxLines(2); nv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(nv,new LinearLayout.LayoutParams(dp(tileDp-12),dp(38)));
                tile.setOnClickListener(v->{
                    boolean exists=false; for(AppItem a:apps) if(a.pkg.equals(ai.packageName)){exists=true;break;}
                    if(!exists){apps.add(new AppItem(ai.packageName,name));saveApps();}
                    selectedPackage=ai.packageName; selectedName=name; refresh(); dialog.dismiss();
                });
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(tileDp),dp(112));
                lp.setMargins(dp(4),dp(4),dp(4),dp(4));
                row.addView(tile,lp);
                inRow++; count++;
                if(inRow==5) inRow=0;
            }
            if(count==0){ TextView empty=text("没有找到可显示的 APP",14); empty.setGravity(Gravity.CENTER); appRows.addView(empty,new LinearLayout.LayoutParams(-1,dp(100))); }
        };
        refreshHolder[0]=refreshAppPicker;
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){refreshHolder[0].run();} public void afterTextChanged(android.text.Editable e){}});
        tabBtns[0].setBackgroundResource(R.drawable.card_selected);
        showFixed1000x800(dialog);
        try{ Window w=dialog.getWindow(); if(w!=null){android.graphics.Point screen=getRealScreenSize(); int h=Math.max(dp(420),screen.y-dp(TOP_BLANK+BOTTOM_BLANK)); w.setLayout(w.getAttributes().width,h);} }catch(Exception ignored){}
        refreshHolder[0].run();
    }

    void showNotes(){
        EditText edit=new EditText(this); edit.setText(prefs.getString("notes","")); edit.setTextColor(Color.WHITE); edit.setHintTextColor(Color.GRAY); edit.setGravity(Gravity.TOP|Gravity.LEFT); edit.setHint("在这里记录内容……"); edit.setSingleLine(false); edit.setMinLines(12); edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); edit.setPadding(dp(12),dp(12),dp(12),dp(12));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("记事本").setView(edit).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{prefs.edit().putString("notes",edit.getText().toString()).apply(); Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show();}).create();
        showFixed1000x800(dialog);
    }

    void presetMenu(int index){
        Preset p=presets.get(index);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"编辑预设","删除预设"},(d,w)->{
            if(w==0)editPreset(index);else{presets.remove(index);savePresets();refresh();}
        }).create();
        showDialogBelowTop(dialog);
    }

    /**
     * 悬浮窗口选位器：在屏幕上显示一个红色边框窗口。
     * 用户可以拖动红框改变位置，在中央输入宽高并保存尺寸，点击“确定”后
     * 自动把 x/y/w/h 回填到新建窗口预设编辑框。
     */
    void showFloatingPresetPicker(){
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)){
            Toast.makeText(this,"请先开启“显示在其他应用上层”权限",Toast.LENGTH_SHORT).show();
            try{ startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName()))); }catch(Exception ignored){}
            return;
        }

        final WindowManager pickerWm=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(pickerWm==null) return;

        final FrameLayout picker=new FrameLayout(this);
        GradientDrawable border=new GradientDrawable();
        border.setColor(0x12000000);
        border.setCornerRadius(dp(10));
        border.setStroke(dp(3),Color.RED);
        picker.setBackground(border);

        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable controlBg=new GradientDrawable();
        controlBg.setColor(0xEE202020);
        controlBg.setCornerRadius(dp(14));
        controls.setBackground(controlBg);

        TextView title=text("悬浮窗选位",15); title.setGravity(Gravity.CENTER); title.setTypeface(null,1);
        controls.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));

        LinearLayout sizeRow=new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText widthInput=numberField("宽度", "" );
        EditText heightInput=numberField("高度", "" );
        widthInput.setHint("宽度 px"); heightInput.setHint("高度 px");
        sizeRow.addView(widthInput,new LinearLayout.LayoutParams(0,dp(50),1));
        sizeRow.addView(heightInput,new LinearLayout.LayoutParams(0,dp(50),1));
        controls.addView(sizeRow,new LinearLayout.LayoutParams(-1,dp(54)));

        TextView positionInfo=text("上距离：0 px    左距离：0 px",11);
        positionInfo.setTextColor(Color.WHITE);
        positionInfo.setGravity(Gravity.CENTER);
        controls.addView(positionInfo,new LinearLayout.LayoutParams(-1,dp(34)));

        TextView tip=text("拖动红框到目标位置，再点击“确定”自动回填",10);
        tip.setTextColor(Color.LTGRAY); tip.setGravity(Gravity.CENTER);
        controls.addView(tip,new LinearLayout.LayoutParams(-1,dp(28)));

        LinearLayout actionRow=new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button saveSize=button("保存");
        Button cancel=button("取消");
        Button confirm=button("确定");
        actionRow.addView(saveSize,new LinearLayout.LayoutParams(0,dp(46),1));
        actionRow.addView(cancel,new LinearLayout.LayoutParams(0,dp(46),1));
        actionRow.addView(confirm,new LinearLayout.LayoutParams(0,dp(46),1));
        controls.addView(actionRow,new LinearLayout.LayoutParams(-1,dp(54)));

        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(330),dp(300),Gravity.CENTER);
        picker.addView(controls,cp);

        android.graphics.Point screen=getRealScreenSize();
        int initialW=600, initialH=400;
        int currentX=30, currentY=180;
        if(!presets.isEmpty()){
            Preset last=presets.get(presets.size()-1);
            if(last.w>0) initialW=last.w;
            if(last.h>0) initialH=last.h;
            currentX=last.x; currentY=last.y;
        }
        initialW=Math.max(220,Math.min(initialW,Math.max(220,screen.x-20)));
        initialH=Math.max(160,Math.min(initialH,Math.max(160,screen.y-120)));
        widthInput.setText(String.valueOf(initialW));
        heightInput.setText(String.valueOf(initialH));

        final WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                initialW,initialH,
                Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT;
        lp.x=Math.max(0,currentX); lp.y=Math.max(0,currentY);
        positionInfo.setText("上距离："+lp.y+" px    左距离："+lp.x+" px");

        // 上下左右精准移动，每次 1px。拖动仍然可以进行大范围定位。
        LinearLayout moveRow=new LinearLayout(this);
        moveRow.setGravity(Gravity.CENTER);
        Button left=button("←"), up=button("↑"), down=button("↓"), right=button("→");
        int moveSize=50;
        moveRow.addView(left,new LinearLayout.LayoutParams(dp(moveSize),dp(44)));
        moveRow.addView(up,new LinearLayout.LayoutParams(dp(moveSize),dp(44)));
        moveRow.addView(down,new LinearLayout.LayoutParams(dp(moveSize),dp(44)));
        moveRow.addView(right,new LinearLayout.LayoutParams(dp(moveSize),dp(44)));
        controls.addView(moveRow,new LinearLayout.LayoutParams(-1,dp(52)));
        final Runnable updatePosition=()->{
            lp.x=Math.max(0,Math.min(lp.x,Math.max(0,screen.x-lp.width)));
            lp.y=Math.max(0,Math.min(lp.y,Math.max(0,screen.y-lp.height)));
            try{pickerWm.updateViewLayout(picker,lp);}catch(Exception ignored){}
            positionInfo.setText("上距离："+lp.y+" px    左距离："+lp.x+" px");
        };
        left.setOnClickListener(v->{lp.x--;updatePosition.run();});
        right.setOnClickListener(v->{lp.x++;updatePosition.run();});
        up.setOnClickListener(v->{lp.y--;updatePosition.run();});
        down.setOnClickListener(v->{lp.y++;updatePosition.run();});

        saveSize.setOnClickListener(v->{
            int w=Math.max(180,number(widthInput,lp.width));
            int h=Math.max(120,number(heightInput,lp.height));
            lp.width=w; lp.height=h;
            try{pickerWm.updateViewLayout(picker,lp);}catch(Exception ignored){}
            Toast.makeText(this,"悬浮窗大小已保存："+w+" × "+h,Toast.LENGTH_SHORT).show();
        });

        final float[] dragStart={0,0};
        final int[] start={lp.x,lp.y};
        picker.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                dragStart[0]=e.getRawX(); dragStart[1]=e.getRawY();
                start[0]=lp.x; start[1]=lp.y;
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                lp.x=start[0]+(int)(e.getRawX()-dragStart[0]);
                lp.y=start[1]+(int)(e.getRawY()-dragStart[1]);
                lp.x=Math.max(0,Math.min(lp.x,Math.max(0,screen.x-lp.width)));
                lp.y=Math.max(0,Math.min(lp.y,Math.max(0,screen.y-lp.height)));
                try{pickerWm.updateViewLayout(picker,lp);}catch(Exception ignored){}
                positionInfo.setText("上距离："+Math.max(0,lp.y)+" px    左距离："+Math.max(0,lp.x)+" px");
                return true;
            }
            return true;
        });

        cancel.setOnClickListener(v->{ try{pickerWm.removeView(picker);}catch(Exception ignored){} });
        confirm.setOnClickListener(v->{
            int w=Math.max(180,number(widthInput,lp.width));
            int h=Math.max(120,number(heightInput,lp.height));
            lp.width=w; lp.height=h;
            try{pickerWm.updateViewLayout(picker,lp);}catch(Exception ignored){}
            try{pickerWm.removeView(picker);}catch(Exception ignored){}
            // 直接打开新建窗口预设，并把选位结果填入编辑框。
            Preset old=new Preset("",Math.max(0,lp.x),Math.max(0,lp.y),w,h,-1,1);
            showPresetEditor(-1,old);
        });

        try{pickerWm.addView(picker,lp);}
        catch(Exception e){Toast.makeText(this,"悬浮窗选位启动失败："+e.getMessage(),Toast.LENGTH_SHORT).show();}
    }

    // 新建/编辑预设统一使用手动输入。
    // 三区域车机部分设备只暴露一个超宽 Display（例如 6480×960），
    // 因此不再依赖 Presentation/多 Display 全屏框选。
    void editPreset(int index){
        if(index<0){
            android.graphics.Point rs=getRealScreenSize();
            Preset old=new Preset("",0,0,0,0,-1,1);
            showPresetEditor(-1,old);
            return;
        }
        Preset old=presets.get(index);
        showPresetEditor(index,old);
    }

    String presetClipboardText(EditText name,EditText x,EditText y,EditText width,EditText height,int mode){
        try{
            JSONObject o=new JSONObject();
            o.put("name",name.getText().toString());
            o.put("x",number(x,0)); o.put("y",number(y,0));
            o.put("w",number(width,0)); o.put("h",number(height,0));
            o.put("mode",mode);
            return o.toString();
        }catch(Exception e){ return ""; }
    }

    void copyPresetToClipboard(EditText name,EditText x,EditText y,EditText width,EditText height,int mode){
        String data=presetClipboardText(name,x,y,width,height,mode);
        try{
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("窗口预设参数",data));
            Toast.makeText(this,"面板参数已复制",Toast.LENGTH_SHORT).show();
        }catch(Exception e){ Toast.makeText(this,"复制失败",Toast.LENGTH_SHORT).show(); }
    }

    boolean pastePresetFromClipboard(EditText name,EditText x,EditText y,EditText width,EditText height, int[] modeHolder, Button[] modeButtons){
        try{
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(!cm.hasPrimaryClip()) { Toast.makeText(this,"剪贴板没有窗口参数",Toast.LENGTH_SHORT).show(); return false; }
            CharSequence cs=cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            JSONObject o=new JSONObject(cs.toString());
            name.setText(o.optString("name",""));
            x.setText(String.valueOf(o.optInt("x",0))); y.setText(String.valueOf(o.optInt("y",0)));
            width.setText(String.valueOf(o.optInt("w",0))); height.setText(String.valueOf(o.optInt("h",0)));
            int m=Math.max(1,Math.min(6,o.optInt("mode",1))); modeHolder[0]=m;
            if(modeButtons!=null) for(int i=0;i<modeButtons.length;i++) modeButtons[i].setBackgroundResource(i==m-1?R.drawable.card_selected:R.drawable.button);
            Toast.makeText(this,"面板参数已粘贴",Toast.LENGTH_SHORT).show();
            return true;
        }catch(Exception e){
            Toast.makeText(this,"剪贴板不是有效的窗口预设参数",Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    void showPresetEditor(int index,Preset old){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24),dp(6),dp(24),dp(6));
        EditText name=textField("预设名称（支持中文）",old.name);
        EditText x=numberField("左间距",String.valueOf(old.x));
        EditText y=numberField("上间距",String.valueOf(old.y));
        EditText width=numberField("窗口宽度",String.valueOf(old.w));
        EditText height=numberField("窗口高度",String.valueOf(old.h));

        box.addView(labeledField("预设名称",name));
        box.addView(labeledNumberField("左间距",x));
        box.addView(labeledNumberField("上间距",y));
        box.addView(labeledNumberField("窗口宽度",width));
        box.addView(labeledNumberField("窗口高度",height));

        TextView modeTitle=text("启动模式",14);
        modeTitle.setPadding(dp(115),dp(6),0,dp(2));
        box.addView(modeTitle,new LinearLayout.LayoutParams(-1,dp(32)));
        LinearLayout modeRow=new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(dp(115),0,dp(4),dp(4));
        Button[] modeButtons=new Button[6];
        final int[] modeHolder={old.mode};
        for(int m=1;m<=6;m++){
            final int mm=m;
            Button mb=button(m==6?"全屏模式":"模式"+m);
            mb.setTextSize(11*fontScale());
            modeButtons[m-1]=mb;
            if(old.mode==mm) mb.setBackgroundResource(R.drawable.card_selected);
            mb.setOnClickListener(v->{
                old.mode=mm; modeHolder[0]=mm;
                for(Button q:modeButtons) q.setBackgroundResource(q==v?R.drawable.card_selected:R.drawable.button);
                if(mm==6){
                    x.setText("0"); y.setText("0");
                    width.setText("0"); height.setText("0");
                }
            });
            modeRow.addView(mb,new LinearLayout.LayoutParams(0,dp(42),1));
        }
        box.addView(modeRow,new LinearLayout.LayoutParams(-1,dp(48)));

        // 自定义底部按钮：复制、粘贴放在“取消”左边，方便整套面板参数快速导入。
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(box,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actionRow=new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(8),dp(6),dp(8),dp(6));
        Button copy=button("复制"); Button paste=button("粘贴"); Button cancel=button("取消"); Button save=button("保存");
        actionRow.addView(copy,new LinearLayout.LayoutParams(0,dp(48),1));
        actionRow.addView(paste,new LinearLayout.LayoutParams(0,dp(48),1));
        actionRow.addView(cancel,new LinearLayout.LayoutParams(0,dp(48),1));
        actionRow.addView(save,new LinearLayout.LayoutParams(0,dp(48),1));
        content.addView(actionRow,new LinearLayout.LayoutParams(-1,dp(62)));

        final Button[] modeButtonsHolder=modeButtons;
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(index<0?"新建窗口预设":"编辑窗口预设")
                .setView(content).create();
        copy.setOnClickListener(v->copyPresetToClipboard(name,x,y,width,height,modeHolder[0]));
        paste.setOnClickListener(v->pastePresetFromClipboard(name,x,y,width,height,modeHolder,modeButtonsHolder));
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            String n=name.getText().toString().trim();
            if(n.isEmpty()){Toast.makeText(this,"请输入预设名称",Toast.LENGTH_SHORT).show();return;}
            Preset p=new Preset(n,
                    Math.max(0,number(x,old.x)),
                    Math.max(0,number(y,old.y)),
                    Math.max(0,number(width,old.w)),
                    Math.max(0,number(height,old.h)),
                    -1,modeHolder[0]);
            if(index<0) presets.add(p); else presets.set(index,p);
            savePresets(); refresh(); dialog.dismiss();
        });
        showFixed1000x800(dialog);
    }

    // 三区域车机按一个超宽 Display 处理，不再创建 Presentation。
    void showScreenDiagnostics(){
        android.view.Display d=getWindow().getWindowManager().getDefaultDisplay();
        android.graphics.Point p=getRealScreenSize(d);
        android.util.DisplayMetrics m=new android.util.DisplayMetrics(); d.getRealMetrics(m);
        StringBuilder s=new StringBuilder();
        s.append("当前车机 Display\n\n")
         .append("Display ID: ").append(d.getDisplayId()).append("\n")
         .append("真实分辨率: ").append(p.x).append(" × ").append(p.y).append("\n")
         .append("densityDpi: ").append(m.densityDpi).append("\n")
         .append("density: ").append(m.density).append("\n")
         .append("rotation: ").append(d.getRotation()).append("\n\n")
         .append("=== 权限情况 ===\n");

        String[][] perms={
                {"相机","android.permission.CAMERA"},{"麦克风/录音","android.permission.RECORD_AUDIO"},
                {"精确定位","android.permission.ACCESS_FINE_LOCATION"},{"大致定位","android.permission.ACCESS_COARSE_LOCATION"},
                {"蓝牙扫描","android.permission.BLUETOOTH_SCAN"},{"蓝牙连接","android.permission.BLUETOOTH_CONNECT"},{"蓝牙广播","android.permission.BLUETOOTH_ADVERTISE"},
                {"读取电话状态","android.permission.READ_PHONE_STATE"},{"拨打电话","android.permission.CALL_PHONE"},{"接听电话","android.permission.ANSWER_PHONE_CALLS"},
                {"读取通话记录","android.permission.READ_CALL_LOG"},{"写入通话记录","android.permission.WRITE_CALL_LOG"},
                {"读取联系人","android.permission.READ_CONTACTS"},{"写入联系人","android.permission.WRITE_CONTACTS"},
                {"读取日历","android.permission.READ_CALENDAR"},{"写入日历","android.permission.WRITE_CALENDAR"},
                {"活动识别","android.permission.ACTIVITY_RECOGNITION"},{"身体传感器","android.permission.BODY_SENSORS"},
                {"发送短信","android.permission.SEND_SMS"},{"接收短信","android.permission.RECEIVE_SMS"},{"读取短信","android.permission.READ_SMS"},
                {"接收彩信","android.permission.RECEIVE_MMS"},{"接收 WAP 推送","android.permission.RECEIVE_WAP_PUSH"},
                {"NFC","android.permission.NFC"},{"通知","android.permission.POST_NOTIFICATIONS"},
                {"读取外部存储","android.permission.READ_EXTERNAL_STORAGE"},{"写入外部存储","android.permission.WRITE_EXTERNAL_STORAGE"},
                {"读取图片","android.permission.READ_MEDIA_IMAGES"},{"读取视频","android.permission.READ_MEDIA_VIDEO"},{"读取音频","android.permission.READ_MEDIA_AUDIO"},
                {"互联网","android.permission.INTERNET"},{"网络状态","android.permission.ACCESS_NETWORK_STATE"},{"Wi-Fi 状态","android.permission.ACCESS_WIFI_STATE"},
                {"保持唤醒","android.permission.WAKE_LOCK"},{"开机广播","android.permission.RECEIVE_BOOT_COMPLETED"},{"安装应用包","android.permission.REQUEST_INSTALL_PACKAGES"},
                {"查询所有应用","android.permission.QUERY_ALL_PACKAGES"},{"后台定位","android.permission.ACCESS_BACKGROUND_LOCATION"},
                {"修改 Wi-Fi","android.permission.CHANGE_WIFI_STATE"},{"修改网络","android.permission.CHANGE_NETWORK_STATE"},{"修改音频设置","android.permission.MODIFY_AUDIO_SETTINGS"},
                {"请求忽略电池优化","android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"}
        };
        for(String[] item:perms){
            String label=item[0], perm=item[1];
            try{
                int state=Build.VERSION.SDK_INT<23?PackageManager.PERMISSION_GRANTED:checkSelfPermission(perm);
                s.append(state==PackageManager.PERMISSION_GRANTED?"✓ ":"✗ ").append(label).append("（").append(perm.substring(perm.lastIndexOf('.')+1)).append("）\n");
            }catch(Exception e){
                s.append("— ").append(label).append("（系统不支持/不可查询）\n");
            }
        }
        s.append("\n特殊权限：\n")
         .append(hasOverlayPermission()?"✓ 悬浮窗\n":"✗ 悬浮窗\n")
         .append(hasUsageAccess()?"✓ 使用情况访问\n":"✗ 使用情况访问\n")
         .append(hasAllFilesPermission()?"✓ 所有文件访问\n":"✗ 所有文件访问\n")
         .append(Build.VERSION.SDK_INT<23 || Settings.System.canWrite(this)?"✓ 修改系统设置\n":"✗ 修改系统设置\n")
         .append("\n触控纠正：上=").append(touchOffsetTop()).append("px，左=").append(touchOffsetLeft()).append("px");

        TextView msg=text(s.toString(),11);
        msg.setPadding(dp(4),dp(4),dp(4),dp(4));
        LinearLayout diagBox=new LinearLayout(this);
        diagBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(msg,new ScrollView.LayoutParams(-1,-2));
        diagBox.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actionRow=new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.VERTICAL);
        actionRow.setPadding(dp(4),dp(6),dp(4),0);
        Button overlayButton=button(hasOverlayPermission()?"悬浮窗权限已开启（重新授权）":"开启悬浮窗权限");
        overlayButton.setOnClickListener(v->openOverlaySettings());
        actionRow.addView(overlayButton,new LinearLayout.LayoutParams(-1,dp(48)));

        Button checkButton=button("权限检查");
        checkButton.setOnClickListener(v->{
            if(Build.VERSION.SDK_INT>=23 && !hasOverlayPermission()){
                openOverlaySettings();
            }else{
                requestRuntimePermissions();
                Toast.makeText(this,"已重新检查并请求可申请的权限",Toast.LENGTH_SHORT).show();
            }
        });
        actionRow.addView(checkButton,new LinearLayout.LayoutParams(-1,dp(48)));
        Button back=button("返回");
        actionRow.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));
        diagBox.addView(actionRow,new LinearLayout.LayoutParams(-1,dp(158)));

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("权限与诊断")
                .setView(diagBox).create();
        back.setOnClickListener(v->dialog.dismiss());
        showFixed1000x800(dialog);
    }

    JSONObject buildConfigJson(){
        JSONObject root=new JSONObject();
        try{
            root.put("version",2);
            root.put("export_time",System.currentTimeMillis());
            root.put("apps",new JSONArray(prefs.getString(APPS,"[]")));
            root.put("presets",new JSONArray(prefs.getString(PRESETS,"[]")));
            root.put("floating_apps",new JSONArray(prefs.getString("floating_apps","[]")));
            JSONArray keys=new JSONArray();
            Map<String,?> all=prefs.getAll();
            for(String k:all.keySet()){
                if(k.equals(APPS)||k.equals(PRESETS)) continue;
                Object v=all.get(k);
                if(v instanceof String || v instanceof Integer || v instanceof Long || v instanceof Float || v instanceof Boolean){
                    JSONObject item=new JSONObject(); item.put("key",k); item.put("value",v); keys.put(item);
                }
            }
            root.put("settings",keys);
            if(selectedPackage!=null) root.put("selected_app_pkg",selectedPackage);
            if(selectedName!=null) root.put("selected_app_name",selectedName);
        }catch(Exception ignored){}
        return root;
    }

    void exportConfig(){
        try{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE,"APP窗口启动器配置.json");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i,REQ_EXPORT_CONFIG);
        }catch(Exception e){Toast.makeText(this,"无法打开导出界面",Toast.LENGTH_SHORT).show();}
    }

    void importConfig(){
        try{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","text/json","application/octet-stream"});
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,REQ_IMPORT_CONFIG);
        }catch(Exception e){Toast.makeText(this,"无法打开导入界面",Toast.LENGTH_SHORT).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try{
            if(requestCode==REQ_EXPORT_CONFIG){
                OutputStream out=getContentResolver().openOutputStream(uri,"w");
                if(out==null) throw new IOException("无法打开导出文件");
                byte[] bytes=buildConfigJson().toString(2).getBytes("UTF-8");
                out.write(bytes);
                out.flush();
                out.close();
                Toast.makeText(this,"配置导出成功",Toast.LENGTH_SHORT).show();
            }else if(requestCode==REQ_IMPORT_CONFIG){
                InputStream in=getContentResolver().openInputStream(uri);
                if(in==null) throw new IOException("无法读取配置文件");
                ByteArrayOutputStream buf=new ByteArrayOutputStream();
                byte[] b=new byte[8192]; int n;
                while((n=in.read(b))!=-1) buf.write(b,0,n);
                in.close();

                // 兼容部分车机文件管理器/编辑器给 JSON 文件添加 UTF-8 BOM 的情况。
                String text=new String(buf.toByteArray(),"UTF-8");
                if(text.length()>0 && text.charAt(0)=='\ufeff') text=text.substring(1);
                text=text.trim();
                if(text.isEmpty()) throw new IOException("配置文件为空");

                JSONObject root=new JSONObject(text);
                // 必须是本程序的配置对象，避免误选其它 JSON 后直接报一堆设置错误。
                if(!root.has("apps") && !root.has("presets") && !root.has("settings") && !root.has("floating_apps")){
                    throw new JSONException("不是 APP窗口启动器配置文件");
                }

                JSONArray appsJson=root.optJSONArray("apps");
                JSONArray presetsJson=root.optJSONArray("presets");
                JSONArray floatingJson=root.optJSONArray("floating_apps");
                SharedPreferences.Editor ed=prefs.edit();
                if(appsJson!=null) ed.putString(APPS,appsJson.toString());
                if(presetsJson!=null) ed.putString(PRESETS,presetsJson.toString());
                if(floatingJson!=null) ed.putString("floating_apps",floatingJson.toString());

                JSONArray settings=root.optJSONArray("settings");
                if(settings!=null){
                    for(int i=0;i<settings.length();i++){
                        JSONObject item=settings.optJSONObject(i);
                        if(item==null) continue;
                        String key=item.optString("key","");
                        if(key.isEmpty()) continue;

                        // 已删除的旧配置不再导入，防止旧配置文件把删除的项目重新带回来。
                        if("popup_left_margin".equals(key) || "popup_right_margin".equals(key)) continue;

                        Object value=item.opt("value");
                        if(value==null || value==JSONObject.NULL) continue;
                        Object old=prefs.getAll().get(key);
                        try{
                            if(old instanceof Boolean) ed.putBoolean(key,Boolean.parseBoolean(String.valueOf(value)));
                            else if(old instanceof Integer) ed.putInt(key,Integer.parseInt(String.valueOf(value)));
                            else if(old instanceof Long) ed.putLong(key,Long.parseLong(String.valueOf(value)));
                            else if(old instanceof Float) ed.putFloat(key,Float.parseFloat(String.valueOf(value)));
                            else if(old instanceof Double) ed.putFloat(key,Float.parseFloat(String.valueOf(value)));
                            else if(old instanceof String) ed.putString(key,String.valueOf(value));
                            else if(value instanceof Boolean) ed.putBoolean(key,(Boolean)value);
                            else if(value instanceof Number) ed.putFloat(key,((Number)value).floatValue());
                            else ed.putString(key,String.valueOf(value));
                        }catch(Exception ignored){
                            // 单个设置格式异常不影响其它配置继续导入。
                        }
                    }
                }

                if(root.has("selected_app_pkg")){
                    String sp=root.optString("selected_app_pkg","");
                    if(!sp.isEmpty()) ed.putString("__selected_app_pkg",sp);
                }
                if(root.has("selected_app_name")){
                    String sn=root.optString("selected_app_name","");
                    if(!sn.isEmpty()) ed.putString("__selected_app_name",sn);
                }
                if(!ed.commit()) throw new IOException("保存配置失败");
                selectedPackage=prefs.getString("__selected_app_pkg",null);
                selectedName=prefs.getString("__selected_app_name",null);
                apps.clear();
                presets.clear();
                loadData();
                refresh();
                Toast.makeText(this,"配置导入成功",Toast.LENGTH_LONG).show();
            }
        }catch(JSONException e){
            Toast.makeText(this,"导入失败：配置文件格式错误或不是本程序导出的配置",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            String msg=e.getMessage();
            if(msg==null || msg.trim().isEmpty()) msg="无法读取或保存配置文件";
            Toast.makeText(this,"配置处理失败："+msg,Toast.LENGTH_LONG).show();
        }
    }

    void startFloatingService(){
        try{
            Intent i=new Intent(this,FloatingService.class);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        }catch(Exception e){Toast.makeText(this,"悬浮窗口启动失败："+e.getMessage(),Toast.LENGTH_SHORT).show();}
    }

    void stopFloatingService(){
        try{stopService(new Intent(this,FloatingService.class));}catch(Exception ignored){}
    }

    void showAutoStartEditor(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(4),dp(8),dp(4));

        TextView hint=text("自动启动任务（可添加多个）",14);
        hint.setTextColor(Color.LTGRAY);
        box.addView(hint,new LinearLayout.LayoutParams(-1,dp(34)));

        LinearLayout listBox=new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(listBox);
        box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(220)));

        EditText interval=numberField("启动间隔（秒）",String.valueOf(prefs.getInt("auto_start_interval",1)));
        box.addView(labeledNumberField("任务间隔",interval));

        final JSONArray[] tasks={loadAutoTasks()};
        final Runnable[] refreshTasks=new Runnable[1];
        refreshTasks[0]=()->{
            listBox.removeAllViews();
            if(tasks[0].length()==0){
                TextView empty=text("暂无自动启动项目，点击下面按钮添加",13);
                empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER);
                listBox.addView(empty,new LinearLayout.LayoutParams(-1,dp(70)));
                return;
            }
            for(int i=0;i<tasks[0].length();i++){
                final int index=i;
                JSONObject o=tasks[0].optJSONObject(i);
                if(o==null) continue;
                String name=o.optString("name",o.optString("pkg","APP"));
                int pi=o.optInt("preset",-1);
                String presetName="直接启动";
                if(pi>=0 && pi<presets.size()) presetName=presets.get(pi).name;
                LinearLayout row=new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8),dp(3),dp(4),dp(3));
                ImageView icon=new ImageView(this);
                try{icon.setImageDrawable(getPackageManager().getApplicationIcon(o.optString("pkg","")));}catch(Exception ignored){}
                row.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
                TextView tv=text((index+1)+". "+name+"  ·  "+presetName,13);
                row.addView(tv,new LinearLayout.LayoutParams(0,dp(50),1));
                Button del=button("删除"); del.setTextSize(11*fontScale());
                row.addView(del,new LinearLayout.LayoutParams(dp(58),dp(42)));
                del.setOnClickListener(v->{
                    JSONArray next=new JSONArray();
                    for(int j=0;j<tasks[0].length();j++) if(j!=index) next.put(tasks[0].optJSONObject(j));
                    tasks[0]=next; refreshTasks[0].run();
                });
                listBox.addView(row,new LinearLayout.LayoutParams(-1,dp(54)));
            }
        };

        // 自动任务总开关：关闭时即使保存了任务，开机也不会执行。
        LinearLayout taskActionRow=new LinearLayout(this);
        taskActionRow.setGravity(Gravity.CENTER_VERTICAL);
        Switch taskSwitch=new Switch(this);
        taskSwitch.setText("自动任务");
        taskSwitch.setTextColor(Color.WHITE);
        taskSwitch.setTextSize(14*fontScale());
        taskSwitch.setChecked(prefs.getBoolean("auto_start_enabled",false));
        taskActionRow.addView(taskSwitch,new LinearLayout.LayoutParams(0,dp(50),1));
        Button add=button("＋ 添加启动任务");
        taskActionRow.addView(add,new LinearLayout.LayoutParams(dp(150),dp(48)));
        box.addView(taskActionRow,new LinearLayout.LayoutParams(-1,dp(54)));
        add.setOnClickListener(v->showAddAutoTaskDialog(tasks,refreshTasks[0]));
        refreshTasks[0].run();

        LinearLayout actionBar=new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("返回");
        Button save=button("保存");
        actionBar.addView(back,new LinearLayout.LayoutParams(0,dp(50),1));
        actionBar.addView(save,new LinearLayout.LayoutParams(0,dp(50),1));
        box.addView(actionBar,new LinearLayout.LayoutParams(-1,dp(58)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("自动启动项目")
                .setView(box).create();
        back.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            int sec=Math.max(1,number(interval,1));
            prefs.edit().putString("auto_start_items",tasks[0].toString())
                    .putInt("auto_start_interval",sec).putBoolean("auto_start_enabled",taskSwitch.isChecked()).apply();
            Toast.makeText(this,"自动启动项目已保存",Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        showFixed1000x800(dialog);
    }

    JSONArray loadAutoTasks(){
        try{return new JSONArray(prefs.getString("auto_start_items","[]"));}
        catch(Exception e){return new JSONArray();}
    }

    void showAddAutoTaskDialog(JSONArray[] tasks,Runnable refresh){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(4),dp(8),dp(4));
        Button appPick=button("点击选择 APP（图标 + 名称）");
        Button presetPick=button("直接启动（无窗口预设）");
        box.addView(appPick,new LinearLayout.LayoutParams(-1,dp(52)));
        box.addView(presetPick,new LinearLayout.LayoutParams(-1,dp(52)));
        final String[] pkg={null},name={null}; final int[] preset={-1};
        appPick.setOnClickListener(v->showAppChoiceDialog((a)->{pkg[0]=a.pkg;name[0]=a.name;appPick.setText(a.name);}));
        presetPick.setOnClickListener(v->{
            String[] items=new String[presets.size()+1]; items[0]="直接启动（无窗口预设）";
            for(int i=0;i<presets.size();i++) items[i+1]=presets.get(i).name;
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择窗口预设").setItems(items,(d,w)->{preset[0]=w-1;presetPick.setText(w==0?items[0]:items[w]);}).create();
            showFixed1000x800(dialog);
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加自动启动任务").setView(box)
                .setNegativeButton("取消",null).setPositiveButton("添加",(d,w)->{
                    if(pkg[0]==null){Toast.makeText(this,"请选择 APP",Toast.LENGTH_SHORT).show();return;}
                    try{
                        JSONObject o=new JSONObject(); o.put("pkg",pkg[0]); o.put("name",name[0]); o.put("preset",preset[0]);
                        tasks[0].put(o); refresh.run();
                    }catch(Exception ignored){}
                }).create();
        showFixed1000x800(dialog);
    }

    interface AppChoice { void onChoose(AppItem item); }

    void showAppChoiceDialog(AppChoice callback){
        PackageManager pm=getPackageManager();
        ArrayList<AppItem> list=new ArrayList<>();
        for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(ai.packageName.equals(getPackageName())) continue;
            try{
                CharSequence label=pm.getApplicationLabel(ai);
                if(label!=null && label.toString().trim().length()>0){
                    list.add(new AppItem(ai.packageName,label.toString()));
                }
            }catch(Exception ignored){}
        }
        Collections.sort(list,(a,b)->a.name.compareToIgnoreCase(b.name));
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),dp(6),dp(12),dp(6));
        ScrollView scroll=new ScrollView(this); LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows,new ScrollView.LayoutParams(-1,-2));
        final AlertDialog[] dialogRef=new AlertDialog[1];
        for(AppItem item:list){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),0,dp(10),0);
            row.setBackgroundResource(R.drawable.card);

            ImageView icon=new ImageView(this);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            try{ icon.setImageDrawable(pm.getApplicationIcon(item.pkg)); }catch(Exception ignored){}
            row.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));

            TextView name=text(item.name,14);
            name.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(0,dp(52),1);
            nameLp.setMargins(dp(10),0,0,0);
            row.addView(name,nameLp);

            row.setOnClickListener(v->{callback.onChoose(item); if(dialogRef[0]!=null) dialogRef[0].dismiss();});
            rows.addView(row,new LinearLayout.LayoutParams(-1,dp(52)));
        }
        box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择 APP").setView(box).setNegativeButton("关闭",null).create();
        dialogRef[0]=dialog;
        showFixed1000x800(dialog);
        try{Window w=dialog.getWindow();if(w!=null){android.graphics.Point screen=getRealScreenSize();w.setLayout(w.getAttributes().width,Math.max(dp(420),screen.y-dp(TOP_BLANK+BOTTOM_BLANK)));}}catch(Exception ignored){}
    }

    /** 在普通第三方权限下向系统请求结束目标 APP 的后台进程。
     * Android 不保证普通 APP 能强制终止另一个前台 APP，因此这里采用系统允许的最佳努力方式。 */
    void closeSelectedApp(){
        if(selectedPackage==null || selectedPackage.trim().isEmpty()){
            Toast.makeText(this,"请先选择 APP",Toast.LENGTH_SHORT).show(); return;
        }
        String pkg=selectedPackage, name=selectedName==null?pkg:selectedName;
        boolean attempted=false;
        try{
            ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            if(am!=null){ am.killBackgroundProcesses(pkg); attempted=true; }
        }catch(Exception ignored){}
        try{
            // 发送一个显式的“退出/关闭”广播不是 Android 通用标准，不能假定目标 APP 支持。
            // 这里不发送不存在的私有指令，避免误伤其它应用。
        }catch(Exception ignored){}
        Toast.makeText(this,attempted?"已向 "+name+" 发送关闭请求（系统是否允许由车机决定）":"无法发送关闭请求",Toast.LENGTH_LONG).show();
    }

    void launchAppDirect(String pkg,String name){
        Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null){Toast.makeText(this,"无法启动 APP",Toast.LENGTH_SHORT).show();return;}
        info.setText("直接启动："+name);
        try{startActivity(intent);}catch(Exception e){info.setText("启动失败："+e.getMessage());}
    }

    /**
     * 按预设启动目标 APP。
     *
     * 重要：ActivityOptions.setLaunchBounds() 是 Android 公共 API，只有当车机的
     * WindowManager/Launcher 允许目标 Activity 使用可调整大小/多窗口时才会真正
     * 控制目标窗口。某些车机把导航、地图、视频等 APP 标记为强制全屏/特殊窗口，
     * 这种情况下目标 APP 可以被系统重新布局，普通第三方 APK 无法用 Java API
     * 强制改变它的窗口边界。
     */
    void launchApp(Preset p){
        if(selectedPackage==null){
            Toast.makeText(this,"请先选择 APP",Toast.LENGTH_SHORT).show();
            return;
        }

        PackageManager pm=getPackageManager();
        Intent intent=pm.getLaunchIntentForPackage(selectedPackage);
        if(intent==null){
            Toast.makeText(this,"无法启动 APP",Toast.LENGTH_SHORT).show();
            return;
        }

        // 三区域车机按一个超宽 Display 处理，窗口位置使用整块屏幕的绝对坐标。
        android.view.Display targetDisplay=getWindow().getWindowManager().getDefaultDisplay();
        android.graphics.Point real=getRealScreenSize(targetDisplay);
        int left=Math.max(0,Math.min(p.x,real.x-1));
        int top=Math.max(0,Math.min(p.y,real.y-1));
        int right=Math.max(left+1,Math.min(p.x+p.w,real.x));
        int bottom=Math.max(top+1,Math.min(p.y+p.h,real.y));
        if(p.mode==6){
            left=0; top=0; right=real.x; bottom=real.y;
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        android.graphics.Rect bounds=new android.graphics.Rect(left,top,right,bottom);

        ActivityOptions options=ActivityOptions.makeBasic();
        options.setLaunchBounds(bounds);

        // 同一物理屏幕上明确指定当前 Display，避免车机多 Display/虚拟 Display
        // 环境下 Launcher 把 Activity 放到默认 Display。
        if(Build.VERSION.SDK_INT>=26 && targetDisplay!=null){
            // 通过反射调用 Android 8.0+ 的 setLaunchDisplayId，避免部分车机 SDK
            // 或定制编译环境缺少该公开方法声明时导致编译失败。
            try{
                java.lang.reflect.Method m=ActivityOptions.class.getMethod("setLaunchDisplayId",int.class);
                m.invoke(options,targetDisplay.getDisplayId());
            }catch(Exception ignored){}
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.putExtra("com.acc.acc.target_x",left);
        intent.putExtra("com.acc.acc.target_y",top);
        intent.putExtra("com.acc.acc.target_w",right-left);
        intent.putExtra("com.acc.acc.target_h",bottom-top);
                intent.putExtra("com.acc.acc.target_display_id",targetDisplay==null?-1:targetDisplay.getDisplayId());
        intent.putExtra("com.acc.acc.fullscreen",p.mode==6);

        info.setText("启动："+selectedName+"\n"+p.name+"  左间距 "+left+"  上间距 "+top+"  "+(right-left)+" × "+(bottom-top)+"  "+(p.mode==6?"全屏":"模式"+p.mode));

        try{
            startActivity(intent,options.toBundle());
            // 给车机 Launcher 一点时间完成 Activity 切换。这里不再尝试使用
            // 非公开 API 强制修改别的 APP，避免在 Android 12 上崩溃。
            Toast.makeText(this,
                    "已按预设请求窗口："+(right-left)+" × "+(bottom-top),
                    Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            info.setText("启动失败："+e.getMessage());
            try{startActivity(intent);}
            catch(Exception ignored){Toast.makeText(this,"APP 启动失败",Toast.LENGTH_SHORT).show();}
        }
    }
}
