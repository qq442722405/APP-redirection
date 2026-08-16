package com.example.appwindowcontainer;

import android.app.ActivityOptions;
import android.app.Dialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
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

public class MainActivity extends AppCompatActivity {

    static final String PREF="container_prefs";
    static final String APPS="apps";
    static final String PRESETS="presets";

    SharedPreferences prefs;
    LinearLayout presetRow, appGrid;
    TextView info;
    String selectedPackage=null;
    String selectedName=null;

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

    float uiScale(){ return Math.max(0.50f, Math.min(3.00f, prefs==null?1.0f:prefs.getFloat("ui_scale",1.0f))); }
    float fontScale(){ return Math.max(0.50f, Math.min(3.00f, prefs==null?1.0f:prefs.getFloat("font_scale",1.0f))); }

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

    Button button(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14*fontScale());
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
            b.setTextSize(10); b.setMinWidth(0); b.setPadding(0,0,0,0);
            b.setOnClickListener(v->{ if(delta==0) input.setText("0"); else adjustNumber(input,delta); input.setSelection(input.length()); });
            row.addView(b,new LinearLayout.LayoutParams(dp(50),dp(44)));
        }
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
        loadData();
        buildUI();
        requestRuntimePermissions();
    }

    static final int REQ_RUNTIME_PERMS = 19041;

    /**
     * 只申请本 APK 在 Android 12/13+ 上真正可以由用户授予的运行时权限。
     * 特殊权限不强行跳转，避免启动 APP 时被连续带离主界面；下面的 helper
     * 可以在需要时打开对应系统授权页。Manifest 已提前声明这些权限，便于
     * 在 ADB 仍可用时由系统/ADB 进行预授权。
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

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        // 主界面固定从顶部 80px 以下开始，避开车机状态栏/触控保留区。
        root.setPadding(dp(12),dp(TOP_BLANK),dp(12),dp(BOTTOM_BLANK));

        // “+”统一放在最左边
        LinearLayout presetHeader=new LinearLayout(this);
        presetHeader.setOrientation(LinearLayout.HORIZONTAL);
        presetHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView addPreset=plusButton();
        addPreset.setContentDescription("新建窗口预设");
        addPreset.setOnClickListener(v->editPreset(-1));
        presetHeader.addView(addPreset,new LinearLayout.LayoutParams(dp(52),dp(44)));
        TextView pt=text("窗口预设",17); pt.setTypeface(null,1);
        presetHeader.addView(pt,new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(presetHeader,new LinearLayout.LayoutParams(-1,dp(44)));

        ScrollView presetScroll=new ScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        presetRow=new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetScroll.addView(presetRow);
        root.addView(presetScroll,new LinearLayout.LayoutParams(-1,dp(168)));

        LinearLayout appHeader=new LinearLayout(this);
        appHeader.setOrientation(LinearLayout.HORIZONTAL);
        appHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView addApp=plusButton();
        addApp.setContentDescription("添加 APP");
        addApp.setOnClickListener(v->chooseApp());
        appHeader.addView(addApp,new LinearLayout.LayoutParams(dp(52),dp(44)));
        TextView at=text("已添加 APP",17); at.setTypeface(null,1);
        appHeader.addView(at,new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(appHeader,new LinearLayout.LayoutParams(-1,dp(44)));

        ScrollView appScroll=new ScrollView(this);
        appScroll.setFillViewport(true);
        appScroll.setVerticalScrollBarEnabled(true);
        appGrid=new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.VERTICAL);
        appScroll.addView(appGrid,new ScrollView.LayoutParams(-1,-2));
        root.addView(appScroll,new LinearLayout.LayoutParams(-1,0,1));

        // 底部：状态信息 + 当前屏幕分辨率/DPI + 设置图标。
        LinearLayout footer=new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        info=text("",12);
        info.setTextColor(Color.LTGRAY);
        info.setPadding(dp(8),dp(4),dp(4),dp(4));
        footer.addView(info,new LinearLayout.LayoutParams(0,dp(62),1));

        TextView screenInfo=text("",11);
        screenInfo.setGravity(Gravity.CENTER);
        screenInfo.setTextColor(Color.WHITE);
        footer.addView(screenInfo,new LinearLayout.LayoutParams(dp(250),dp(62)));
        updateScreenInfo(screenInfo);

        TextView note=plusButton();
        note.setText("📝");
        note.setTextSize(22);
        note.setContentDescription("记事本");
        note.setOnClickListener(v->showNotes());
        footer.addView(note,new LinearLayout.LayoutParams(dp(58),dp(58)));

        TextView settings=plusButton();
        settings.setText("⚙");
        settings.setTextSize(24);
        settings.setContentDescription("设置");
        settings.setOnClickListener(v->showSettingsMenu());
        footer.addView(settings,new LinearLayout.LayoutParams(dp(58),dp(58)));
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(64)));
        setContentView(root);
        refresh();
    }

    /**
     * 刷新主界面中的窗口预设和已添加 APP。
     * 保持主界面控件对象不变，只重建两个列表，避免重新 setContentView
     * 导致车机 ROM 出现焦点/触控坐标漂移。
     */
    void refresh(){
        if(presetRow!=null){
            presetRow.removeAllViews();
            for(int i=0;i<presets.size();i++){
                final int index=i;
                Preset p=presets.get(i);

                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(dp(10),dp(8),dp(10),dp(8));
                card.setBackgroundResource(R.drawable.card);

                TextView title=text(p.name,14);
                title.setGravity(Gravity.CENTER);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(title,new LinearLayout.LayoutParams(dp(150),dp(42)));

                TextView size=text(p.w+" × "+p.h,11);
                size.setTextColor(Color.LTGRAY);
                size.setGravity(Gravity.CENTER);
                card.addView(size,new LinearLayout.LayoutParams(dp(150),dp(28)));

                card.setOnClickListener(v->{
                    if(selectedPackage!=null){
                        launchApp(p);
                    }else{
                        presetMenu(index);
                    }
                });
                card.setOnLongClickListener(v->{presetMenu(index);return true;});

                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(170),dp(112));
                lp.setMargins(dp(5),dp(5),dp(5),dp(5));
                presetRow.addView(card,lp);
            }

            if(presets.isEmpty()){
                TextView empty=text("点击左侧“+”新建窗口预设",13);
                empty.setTextColor(Color.GRAY);
                empty.setGravity(Gravity.CENTER);
                presetRow.addView(empty,new LinearLayout.LayoutParams(-1,dp(112)));
            }
        }

        if(appGrid!=null){
            appGrid.removeAllViews();
            if(apps.isEmpty()){
                TextView empty=text("点击“+”添加 APP",14);
                empty.setTextColor(Color.GRAY);
                empty.setGravity(Gravity.CENTER);
                appGrid.addView(empty,new LinearLayout.LayoutParams(-1,dp(90)));
                return;
            }

            PackageManager pm=getPackageManager();
            // 已添加 APP 固定三列排列；超过三行后由外层 ScrollView 上下滚动。
            for(int base=0;base<apps.size();base+=3){
                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                for(int col=0;col<3;col++){
                    int index=base+col;
                    if(index>=apps.size()){
                        Space space=new Space(this);
                        row.addView(space,new LinearLayout.LayoutParams(0,dp(112),1));
                        continue;
                    }
                    final int itemIndex=index;
                    AppItem item=apps.get(index);
                    LinearLayout tile=new LinearLayout(this);
                    tile.setOrientation(LinearLayout.VERTICAL);
                    tile.setGravity(Gravity.CENTER);
                    tile.setPadding(dp(6),dp(6),dp(6),dp(6));
                    tile.setBackgroundResource(R.drawable.card);

                    ImageView icon=new ImageView(this);
                    icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    try{ icon.setImageDrawable(pm.getApplicationIcon(item.pkg)); }catch(Exception ignored){}
                    tile.addView(icon,new LinearLayout.LayoutParams(dp(60),dp(60)));

                    TextView name=text(item.name,12);
                    name.setGravity(Gravity.CENTER);
                    name.setMaxLines(2);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    name.setPadding(0,0,0,dp(2));
                    tile.addView(name,new LinearLayout.LayoutParams(-1,dp(34)));

                    if(item.pkg.equals(selectedPackage)) tile.setBackgroundResource(R.drawable.card_selected);
                    tile.setOnClickListener(v->{
                        selectedPackage=item.pkg;
                        selectedName=item.name;
                        info.setText("当前 APP："+item.name);
                        refresh();
                    });
                    tile.setOnLongClickListener(v->{
                        new AlertDialog.Builder(this).setTitle(item.name)
                                .setItems(new String[]{"选择","删除"},(d,w)->{
                                    if(w==0){
                                        selectedPackage=item.pkg; selectedName=item.name;
                                        info.setText("当前 APP："+item.name); refresh();
                                    }else{
                                        if(item.pkg.equals(selectedPackage)){selectedPackage=null;selectedName=null;}
                                        apps.remove(itemIndex); saveApps(); refresh();
                                    }
                                }).show();
                        return true;
                    });
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(112),1);
                    lp.setMargins(dp(5),dp(5),dp(5),dp(5));
                    row.addView(tile,lp);
                }
                appGrid.addView(row,new LinearLayout.LayoutParams(-1,dp(122)));
            }
        }
    }

    void updateScreenInfo(TextView view){
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        android.graphics.Point rs=getRealScreenSize();
        view.setText("屏幕 " + rs.x + " × " + rs.y + "\nDPI " + dm.densityDpi + "   density " + String.format(java.util.Locale.US,"%.2f",dm.density));
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
            w.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            // 所有系统弹窗统一使用不透明窗口，避免车机 ROM 对透明 Dialog
            // 的坐标/触摸区域进行特殊处理。
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.setBackgroundDrawable(new ColorDrawable(Color.rgb(24,24,24)));
            if(Build.VERSION.SDK_INT>=19){
                w.getDecorView().setSystemUiVisibility(0);
            }
            android.graphics.Point screen=getRealScreenSize();
            int left=Math.max(0,prefs.getInt("dialog_left_margin_px",40));
            int right=Math.max(0,prefs.getInt("dialog_right_margin_px",40));
            int available=screen.x-left-right;
            int minW=Math.min(dp(280),Math.max(dp(160),screen.x));
            int width=Math.max(minW,available);
            if(width>screen.x) width=screen.x;
            if(left+width>screen.x-right){
                width=Math.max(dp(160),screen.x-left-right);
            }
            WindowManager.LayoutParams lp=w.getAttributes();
            lp.gravity=Gravity.TOP | Gravity.LEFT;
            lp.x=left;
            lp.y=dp(TOP_BLANK);
            lp.width=width;
            lp.height=WindowManager.LayoutParams.WRAP_CONTENT;
            lp.dimAmount=0.55f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }catch(Exception ignored){}
    }

    void showDialogBelowTop(AlertDialog dialog){
        if(dialog==null) return;
        dialog.show();
        placeDialogBelowTop(dialog);
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

        LinearLayout floatRow=new LinearLayout(this); floatRow.setGravity(Gravity.CENTER_VERTICAL);
        floatRow.addView(text("悬浮窗口",15),new LinearLayout.LayoutParams(0,dp(52),1));
        Button floatLayout=button(prefs.getBoolean("floating_vertical",false)?"竖向":"横向");
        LinearLayout.LayoutParams directionLp=new LinearLayout.LayoutParams(dp(82),dp(46)); directionLp.setMargins(0,0,dp(8),0);
        floatRow.addView(floatLayout,directionLp);
        Switch fswitch=new Switch(this); fswitch.setChecked(prefs.getBoolean("floating_enabled",false));
        floatRow.addView(fswitch,new LinearLayout.LayoutParams(dp(58),dp(52)));
        box.addView(floatRow,new LinearLayout.LayoutParams(-1,dp(58)));
        floatLayout.setOnClickListener(v->{boolean vertical=!prefs.getBoolean("floating_vertical",false);prefs.edit().putBoolean("floating_vertical",vertical).apply();floatLayout.setText(vertical?"竖向":"横向");if(fswitch.isChecked()){stopFloatingService();startFloatingService();}});
        fswitch.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean("floating_enabled",checked).apply();if(checked)startFloatingService();else stopFloatingService();});

        Button permissions=button("权限与诊断"); permissions.setOnClickListener(v->{if(settingsDialog[0]!=null)settingsDialog[0].dismiss();showScreenDiagnostics();});
        box.addView(permissions,new LinearLayout.LayoutParams(-1,dp(48)));
        settingsDialog[0]=new AlertDialog.Builder(this).setTitle("设置").setView(box).setNegativeButton("关闭",null).create();
        showDialogBelowTop(settingsDialog[0]);
    }

    void showInterfaceOptionsDialog(){
        final AlertDialog[] dialogRef={null};
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24),dp(8),dp(24),dp(12));

        LinearLayout delayRow=new LinearLayout(this); delayRow.setGravity(Gravity.CENTER_VERTICAL);
        delayRow.addView(text("开机延迟启动（秒）",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText bootDelay=numberField("0",String.valueOf(prefs.getInt("boot_delay_seconds",0)));
        delayRow.addView(bootDelay,new LinearLayout.LayoutParams(dp(90),dp(52)));
        box.addView(delayRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout fontRow=new LinearLayout(this); fontRow.setGravity(Gravity.CENTER_VERTICAL);
        fontRow.addView(text("主界面字体大小",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText fontInput=numberField("100",String.valueOf(Math.round(prefs.getFloat("font_scale",1.0f)*100)));
        fontRow.addView(fontInput,new LinearLayout.LayoutParams(dp(82),dp(52)));
        fontRow.addView(text("%",14),new LinearLayout.LayoutParams(dp(28),dp(52)));
        box.addView(fontRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout uiRow=new LinearLayout(this); uiRow.setGravity(Gravity.CENTER_VERTICAL);
        uiRow.addView(text("主界面界面大小",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText uiInput=numberField("100",String.valueOf(Math.round(prefs.getFloat("ui_scale",1.0f)*100)));
        uiRow.addView(uiInput,new LinearLayout.LayoutParams(dp(82),dp(52)));
        uiRow.addView(text("%",14),new LinearLayout.LayoutParams(dp(28),dp(52)));
        box.addView(uiRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout leftRow=new LinearLayout(this); leftRow.setGravity(Gravity.CENTER_VERTICAL);
        leftRow.addView(text("弹窗左边距",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText leftMarginInput=numberField("40",String.valueOf(prefs.getInt("dialog_left_margin_px",40)));
        leftRow.addView(leftMarginInput,new LinearLayout.LayoutParams(dp(90),dp(52)));
        leftRow.addView(text("px",13),new LinearLayout.LayoutParams(dp(30),dp(52)));
        box.addView(leftRow,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout rightRow=new LinearLayout(this); rightRow.setGravity(Gravity.CENTER_VERTICAL);
        rightRow.addView(text("弹窗右边距",14),new LinearLayout.LayoutParams(0,dp(52),1));
        EditText rightMarginInput=numberField("40",String.valueOf(prefs.getInt("dialog_right_margin_px",40)));
        rightRow.addView(rightMarginInput,new LinearLayout.LayoutParams(dp(90),dp(52)));
        rightRow.addView(text("px",13),new LinearLayout.LayoutParams(dp(30),dp(52)));
        box.addView(rightRow,new LinearLayout.LayoutParams(-1,dp(56)));

        TextView hint=text("字体大小、界面大小支持 50-300%，保存后返回主界面生效。",11);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0,dp(4),0,dp(6));
        box.addView(hint,new LinearLayout.LayoutParams(-1,dp(38)));

        Button save=button("保存设置");
        save.setOnClickListener(v->{
            try{
                int delay=Integer.parseInt(bootDelay.getText().toString().trim());
                float fs=Float.parseFloat(fontInput.getText().toString().trim());
                float us=Float.parseFloat(uiInput.getText().toString().trim());
                int lm=Integer.parseInt(leftMarginInput.getText().toString().trim());
                int rm=Integer.parseInt(rightMarginInput.getText().toString().trim());
                if(delay<0||delay>3600||fs<50||fs>300||us<50||us>300||lm<0||rm<0||lm>3000||rm>3000) throw new Exception();
                prefs.edit().putInt("boot_delay_seconds",delay)
                        .putFloat("font_scale",fs/100f).putFloat("ui_scale",us/100f)
                        .putInt("dialog_left_margin_px",lm).putInt("dialog_right_margin_px",rm).apply();
                Toast.makeText(this,"设置已保存并生效",Toast.LENGTH_SHORT).show();
                if(dialogRef[0]!=null) dialogRef[0].dismiss();
                buildUI();
            }catch(Exception e){
                Toast.makeText(this,"请输入有效数值：延迟0-3600秒，字体50-300%，界面50-300%",Toast.LENGTH_LONG).show();
            }
        });
        box.addView(save,new LinearLayout.LayoutParams(-1,dp(50)));
        dialogRef[0]=new AlertDialog.Builder(this).setTitle("界面选项").setView(box).setNegativeButton("返回",null).create();
        showDialogBelowTop(dialogRef[0]);
    }

    /** 添加 APP：全部/用户/系统分类，正方形图标+名称；APP 很多时可上下滚动。 */
    void chooseApp(){
        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> list=new ArrayList<>();
        for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(ai.packageName.equals(getPackageName())) continue;
            if(pm.getLaunchIntentForPackage(ai.packageName)==null) continue;
            list.add(ai);
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
            final int ci=i; Button b=button(cats[i]); b.setTextSize(12); tabBtns[i]=b;
            tabs.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));
            b.setOnClickListener(v->{
                selectedAppCategory[0]=ci;
                for(int j=0;j<tabBtns.length;j++) tabBtns[j].setBackgroundResource(j==ci?R.drawable.card_selected:R.drawable.button);
                refreshHolder[0].run();
            });
        }
        box.addView(tabs,new LinearLayout.LayoutParams(-1,dp(46)));

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(true);
        GridLayout grid=new GridLayout(this);
        int columns=3;
        grid.setColumnCount(columns); grid.setUseDefaultMargins(false);
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加 APP").setView(box).setNegativeButton("关闭",null).create();
        Runnable refreshAppPicker=()->{
            grid.removeAllViews(); String q=search.getText().toString().trim().toLowerCase(Locale.ROOT); int count=0;
            int tileW=dp(118), tileH=dp(124);
            for(ApplicationInfo ai:list){
                boolean system=(ai.flags & ApplicationInfo.FLAG_SYSTEM)!=0 || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;
                if(selectedAppCategory[0]==1 && system) continue;
                if(selectedAppCategory[0]==2 && !system) continue;
                String name=pm.getApplicationLabel(ai).toString();
                if(!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !ai.packageName.toLowerCase(Locale.ROOT).contains(q)) continue;
                LinearLayout tile=new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(6),dp(6),dp(6),dp(6)); tile.setBackgroundResource(R.drawable.card);
                ImageView icon=new ImageView(this); icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                try{icon.setImageDrawable(pm.getApplicationIcon(ai));}catch(Exception ignored){}
                tile.addView(icon,new LinearLayout.LayoutParams(dp(64),dp(64)));
                TextView nv=text(name,11); nv.setGravity(Gravity.CENTER); nv.setMaxLines(2); nv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(nv,new LinearLayout.LayoutParams(dp(106),dp(38)));
                tile.setOnClickListener(v->{
                    boolean exists=false; for(AppItem a:apps) if(a.pkg.equals(ai.packageName)){exists=true;break;}
                    if(!exists){apps.add(new AppItem(ai.packageName,name));saveApps();}
                    selectedPackage=ai.packageName; selectedName=name; refresh(); dialog.dismiss();
                });
                GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=tileW; lp.height=tileH; lp.setMargins(dp(4),dp(4),dp(4),dp(4)); grid.addView(tile,lp); count++;
            }
            if(count==0){ TextView empty=text("没有找到可启动的 APP",14); empty.setGravity(Gravity.CENTER); GridLayout.LayoutParams ep=new GridLayout.LayoutParams(); ep.width=dp(460);ep.height=dp(100);grid.addView(empty,ep); }
        };
        refreshHolder[0]=refreshAppPicker;
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){refreshHolder[0].run();} public void afterTextChanged(android.text.Editable e){}});
        tabBtns[0].setBackgroundResource(R.drawable.card_selected);
        showDialogBelowTop(dialog);
        try{ Window w=dialog.getWindow(); if(w!=null){android.graphics.Point screen=getRealScreenSize(); int h=Math.max(dp(420),screen.y-dp(TOP_BLANK+BOTTOM_BLANK)); w.setLayout(w.getAttributes().width,h);} }catch(Exception ignored){}
        refreshHolder[0].run();
    }

    void showNotes(){
        EditText edit=new EditText(this); edit.setText(prefs.getString("notes","")); edit.setTextColor(Color.WHITE); edit.setHintTextColor(Color.GRAY); edit.setGravity(Gravity.TOP|Gravity.LEFT); edit.setHint("在这里记录内容……"); edit.setSingleLine(false); edit.setMinLines(12); edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); edit.setPadding(dp(12),dp(12),dp(12),dp(12));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("记事本").setView(edit).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{prefs.edit().putString("notes",edit.getText().toString()).apply(); Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show();}).create();
        showDialogBelowTop(dialog);
    }

    void presetMenu(int index){
        Preset p=presets.get(index);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"编辑预设","删除预设"},(d,w)->{
            if(w==0)editPreset(index);else{presets.remove(index);savePresets();refresh();}
        }).create();
        showDialogBelowTop(dialog);
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
            mb.setTextSize(11);
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
        showDialogBelowTop(dialog);
    }

    // 三区域车机按一个超宽 Display 处理，不再创建 Presentation。
    void showScreenDiagnostics(){
        android.view.Display d=getWindow().getWindowManager().getDefaultDisplay();
        android.graphics.Point p=getRealScreenSize(d);
        android.util.DisplayMetrics m=new android.util.DisplayMetrics(); d.getRealMetrics(m);
        String s="当前车机 Display\n\n"+
                "Display ID: "+d.getDisplayId()+"\n"+
                "真实分辨率: "+p.x+" × "+p.y+"\n"+
                "densityDpi: "+m.densityDpi+"\n"+
                "density: "+m.density+"\n"+
                "rotation: "+d.getRotation()+"\n\n"+
                "三区域按整块超宽屏坐标处理：\n"+
                "左区约 X=0\n中区约 X=2160\n右区约 X=4320";
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("屏幕诊断")
                .setMessage(s).setPositiveButton("新建预设",(x,w)->editPreset(-1))
                .setNegativeButton("关闭",null).create();
        showDialogBelowTop(dialog);
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
                Button del=button("删除"); del.setTextSize(11);
                row.addView(del,new LinearLayout.LayoutParams(dp(58),dp(42)));
                del.setOnClickListener(v->{
                    JSONArray next=new JSONArray();
                    for(int j=0;j<tasks[0].length();j++) if(j!=index) next.put(tasks[0].optJSONObject(j));
                    tasks[0]=next; refreshTasks[0].run();
                });
                listBox.addView(row,new LinearLayout.LayoutParams(-1,dp(54)));
            }
        };

        Button add=button("＋ 添加启动任务");
        box.addView(add,new LinearLayout.LayoutParams(-1,dp(48)));
        add.setOnClickListener(v->showAddAutoTaskDialog(tasks,refreshTasks[0]));
        refreshTasks[0].run();

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("自动启动项目")
                .setView(box).setNegativeButton("关闭",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                int sec=Math.max(1,number(interval,1));
                prefs.edit().putString("auto_start_items",tasks[0].toString())
                        .putInt("auto_start_interval",sec).putBoolean("auto_start_enabled",tasks[0].length()>0).apply();
                Toast.makeText(this,"自动启动项目已保存",Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        showDialogBelowTop(dialog);
    }

    JSONArray loadAutoTasks(){
        try{return new JSONArray(prefs.getString("auto_start_items","[]"));}
        catch(Exception e){return new JSONArray();}
    }

    void showAddAutoTaskDialog(JSONArray[] tasks,Runnable refresh){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(4),dp(8),dp(4));
        Button appPick=button("点击选择 APP");
        Button presetPick=button("直接启动（无窗口预设）");
        box.addView(appPick,new LinearLayout.LayoutParams(-1,dp(52)));
        box.addView(presetPick,new LinearLayout.LayoutParams(-1,dp(52)));
        final String[] pkg={null},name={null}; final int[] preset={-1};
        appPick.setOnClickListener(v->showAppChoiceDialog((a)->{pkg[0]=a.pkg;name[0]=a.name;appPick.setText(a.name);}));
        presetPick.setOnClickListener(v->{
            String[] items=new String[presets.size()+1]; items[0]="直接启动（无窗口预设）";
            for(int i=0;i<presets.size();i++) items[i+1]=presets.get(i).name;
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择窗口预设").setItems(items,(d,w)->{preset[0]=w-1;presetPick.setText(w==0?items[0]:items[w]);}).create();
            showDialogBelowTop(dialog);
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加自动启动任务").setView(box)
                .setNegativeButton("取消",null).setPositiveButton("添加",(d,w)->{
                    if(pkg[0]==null){Toast.makeText(this,"请选择 APP",Toast.LENGTH_SHORT).show();return;}
                    try{
                        JSONObject o=new JSONObject(); o.put("pkg",pkg[0]); o.put("name",name[0]); o.put("preset",preset[0]);
                        tasks[0].put(o); refresh.run();
                    }catch(Exception ignored){}
                }).create();
        showDialogBelowTop(dialog);
    }

    interface AppChoice { void onChoose(AppItem item); }

    void showAppChoiceDialog(AppChoice callback){
        PackageManager pm=getPackageManager();
        ArrayList<AppItem> list=new ArrayList<>();
        for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(ai.packageName.equals(getPackageName()) || pm.getLaunchIntentForPackage(ai.packageName)==null) continue;
            try{list.add(new AppItem(ai.packageName,pm.getApplicationLabel(ai).toString()));}catch(Exception ignored){}
        }
        Collections.sort(list,(a,b)->a.name.compareToIgnoreCase(b.name));
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),dp(6),dp(12),dp(6));
        ScrollView scroll=new ScrollView(this); LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows,new ScrollView.LayoutParams(-1,-2));
        for(AppItem item:list){ Button b=button(item.name); b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT); b.setPadding(dp(14),0,dp(8),0); b.setOnClickListener(v->{callback.onChoose(item);}); rows.addView(b,new LinearLayout.LayoutParams(-1,dp(52))); }
        box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选择 APP").setView(box).setNegativeButton("关闭",null).create();
        showDialogBelowTop(dialog);
        try{Window w=dialog.getWindow();if(w!=null){android.graphics.Point screen=getRealScreenSize();w.setLayout(w.getAttributes().width,Math.max(dp(420),screen.y-dp(TOP_BLANK+BOTTOM_BLANK)));}}catch(Exception ignored){}
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
        intent.putExtra("com.example.appwindowcontainer.target_x",left);
        intent.putExtra("com.example.appwindowcontainer.target_y",top);
        intent.putExtra("com.example.appwindowcontainer.target_w",right-left);
        intent.putExtra("com.example.appwindowcontainer.target_h",bottom-top);
                intent.putExtra("com.example.appwindowcontainer.target_display_id",targetDisplay==null?-1:targetDisplay.getDisplayId());
        intent.putExtra("com.example.appwindowcontainer.fullscreen",p.mode==6);

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
