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

    float uiScale(){ return Math.max(0.80f, Math.min(1.25f, prefs==null?1.0f:prefs.getFloat("ui_scale",1.0f))); }
    float fontScale(){ return Math.max(0.80f, Math.min(1.30f, prefs==null?1.0f:prefs.getFloat("font_scale",1.0f))); }

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
        ArrayList<String> req=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=33){
            if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
                req.add("android.permission.POST_NOTIFICATIONS");
            // 媒体权限仅用于用户主动选择/读取媒体；不在 Android 12 上请求。
            if(checkSelfPermission("android.permission.READ_MEDIA_IMAGES")!=PackageManager.PERMISSION_GRANTED)
                req.add("android.permission.READ_MEDIA_IMAGES");
            if(checkSelfPermission("android.permission.READ_MEDIA_VIDEO")!=PackageManager.PERMISSION_GRANTED)
                req.add("android.permission.READ_MEDIA_VIDEO");
            if(checkSelfPermission("android.permission.READ_MEDIA_AUDIO")!=PackageManager.PERMISSION_GRANTED)
                req.add("android.permission.READ_MEDIA_AUDIO");
        }else if(Build.VERSION.SDK_INT>=23){
            if(checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE")!=PackageManager.PERMISSION_GRANTED)
                req.add("android.permission.READ_EXTERNAL_STORAGE");
            if(checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")!=PackageManager.PERMISSION_GRANTED)
                req.add("android.permission.WRITE_EXTERNAL_STORAGE");
        }
        if(!req.isEmpty()) requestPermissions(req.toArray(new String[0]),REQ_RUNTIME_PERMS);
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
        appGrid=new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.HORIZONTAL);
        appScroll.addView(appGrid);
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

    void updateScreenInfo(TextView view){
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        android.graphics.Point rs=getRealScreenSize();
        view.setText("屏幕 " + rs.x + " × " + rs.y + "\nDPI " + dm.densityDpi + "   density " + String.format(java.util.Locale.US,"%.2f",dm.density));
    }

    void showSettingsMenu(){
        final AlertDialog[] settingsDialog={null};
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24),dp(8),dp(24),dp(8));

        // 第一排：开机启动 + 开机延迟启动
        LinearLayout firstRow=new LinearLayout(this);
        firstRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView appBootLabel=text("开机启动",15);
        firstRow.addView(appBootLabel,new LinearLayout.LayoutParams(0,dp(52),1));
        Switch appBoot=new Switch(this);
        appBoot.setChecked(prefs.getBoolean("app_boot_enabled",false));
        appBoot.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("app_boot_enabled",checked).apply());
        firstRow.addView(appBoot,new LinearLayout.LayoutParams(dp(58),dp(52)));

        TextView delayLabel=text("开机延迟启动（秒）",15);
        LinearLayout.LayoutParams delayLabelLp=new LinearLayout.LayoutParams(0,dp(52),1);
        delayLabelLp.setMargins(dp(18),0,0,0);
        firstRow.addView(delayLabel,delayLabelLp);
        EditText bootDelay=numberField("0",String.valueOf(prefs.getInt("boot_delay_seconds",0)));
        LinearLayout.LayoutParams delayLp=new LinearLayout.LayoutParams(dp(88),dp(52));
        firstRow.addView(bootDelay,delayLp);
        box.addView(firstRow,new LinearLayout.LayoutParams(-1,dp(58)));

        // 第二排：主界面字体大小、主界面界面大小，均手动输入，点击保存后生效
        LinearLayout sizeRow=new LinearLayout(this);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView fontLabel=text("主界面字体大小",14);
        sizeRow.addView(fontLabel,new LinearLayout.LayoutParams(0,dp(52),1));
        EditText fontInput=numberField("100",String.valueOf(Math.round(prefs.getFloat("font_scale",1.0f)*100)));
        fontInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sizeRow.addView(fontInput,new LinearLayout.LayoutParams(dp(78),dp(52)));
        TextView fontPct=text("%",14);
        sizeRow.addView(fontPct,new LinearLayout.LayoutParams(dp(24),dp(52)));

        TextView uiLabel=text("主界面界面大小",14);
        LinearLayout.LayoutParams uiLabelLp=new LinearLayout.LayoutParams(0,dp(52),1);
        uiLabelLp.setMargins(dp(12),0,0,0);
        sizeRow.addView(uiLabel,uiLabelLp);
        EditText uiInput=numberField("100",String.valueOf(Math.round(prefs.getFloat("ui_scale",1.0f)*100)));
        uiInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sizeRow.addView(uiInput,new LinearLayout.LayoutParams(dp(78),dp(52)));
        TextView uiPct=text("%",14);
        sizeRow.addView(uiPct,new LinearLayout.LayoutParams(dp(24),dp(52)));
        box.addView(sizeRow,new LinearLayout.LayoutParams(-1,dp(58)));

        Button saveSize=button("保存设置");
        saveSize.setOnClickListener(v->{
            try{
                float fs=Float.parseFloat(fontInput.getText().toString().trim());
                float us=Float.parseFloat(uiInput.getText().toString().trim());
                if(fs<80 || fs>130 || us<80 || us>120) throw new Exception();
                prefs.edit().putFloat("font_scale",fs/100f).putFloat("ui_scale",us/100f).apply();
                Toast.makeText(this,"字体大小和界面大小已保存并生效",Toast.LENGTH_SHORT).show();
                if(settingsDialog[0]!=null) settingsDialog[0].dismiss();
                buildUI();
            }catch(Exception e){
                Toast.makeText(this,"请输入有效数值：字体 80-130%，界面 80-120%",Toast.LENGTH_LONG).show();
            }
        });
        box.addView(saveSize,new LinearLayout.LayoutParams(-1,dp(48)));

        // 悬浮窗口：方向按钮放在开关左边
        LinearLayout floatRow=new LinearLayout(this);
        floatRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView fl=text("悬浮窗口",15);
        floatRow.addView(fl,new LinearLayout.LayoutParams(0,dp(52),1));

        Button floatLayout=button(prefs.getBoolean("floating_vertical",false)?"竖向":"横向");
        LinearLayout.LayoutParams directionLp=new LinearLayout.LayoutParams(dp(82),dp(46));
        directionLp.setMargins(0,0,dp(8),0);
        floatRow.addView(floatLayout,directionLp);

        Switch fs=new Switch(this);
        fs.setChecked(prefs.getBoolean("floating_enabled",false));
        fs.setOnCheckedChangeListener((v,checked)->{
            prefs.edit().putBoolean("floating_enabled",checked).apply();
            if(checked){ if(hasOverlayPermission()) startFloatingService(); else { fs.setChecked(false); openOverlaySettings(); } }
            else stopFloatingService();
        });
        floatRow.addView(fs,new LinearLayout.LayoutParams(dp(58),dp(52)));
        box.addView(floatRow);

        floatLayout.setOnClickListener(v->{
            boolean vertical=!prefs.getBoolean("floating_vertical",false);
            prefs.edit().putBoolean("floating_vertical",vertical).apply();
            floatLayout.setText(vertical?"竖向":"横向");
            if(prefs.getBoolean("floating_enabled",false)){
                stopFloatingService();
                new Handler().postDelayed(this::startFloatingService,200);
            }
        });

        LinearLayout autoRow=new LinearLayout(this); autoRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView al=text("自动启动",15); autoRow.addView(al,new LinearLayout.LayoutParams(0,dp(52),1));
        Switch as=new Switch(this); as.setChecked(prefs.getBoolean("auto_start_enabled",false));
        as.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean("auto_start_enabled",checked).apply());
        autoRow.addView(as); box.addView(autoRow);

        Button add=button("管理自动启动项目（支持多个任务）"); add.setOnClickListener(v->showAutoStartEditor());
        box.addView(add,new LinearLayout.LayoutParams(-1,dp(50)));

        // 设置页面直接列出当前加入自动启动的 APP
        LinearLayout autoList=new LinearLayout(this); autoList.setOrientation(LinearLayout.VERTICAL);
        JSONArray currentTasks=loadAutoTasks();
        if(currentTasks.length()==0){
            TextView emptyAuto=text("当前没有自动启动任务",12); emptyAuto.setTextColor(Color.GRAY);
            autoList.addView(emptyAuto,new LinearLayout.LayoutParams(-1,dp(34)));
        }else{
            for(int i=0;i<currentTasks.length();i++){
                JSONObject o=currentTasks.optJSONObject(i); if(o==null) continue;
                LinearLayout ar=new LinearLayout(this); ar.setGravity(Gravity.CENTER_VERTICAL);
                ImageView aiIcon=new ImageView(this);
                try{aiIcon.setImageDrawable(getPackageManager().getApplicationIcon(o.optString("pkg","")));}catch(Exception ignored){}
                ar.addView(aiIcon,new LinearLayout.LayoutParams(dp(32),dp(32)));
                TextView an=text((i+1)+". "+o.optString("name",o.optString("pkg","APP")),12);
                ar.addView(an,new LinearLayout.LayoutParams(0,dp(38),1));
                autoList.addView(ar,new LinearLayout.LayoutParams(-1,dp(38)));
            }
        }
        box.addView(autoList,new LinearLayout.LayoutParams(-1,Math.min(dp(150),Math.max(dp(38),dp(38)*Math.max(1,currentTasks.length())))));

        bootDelay.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){
                try{ prefs.edit().putInt("boot_delay_seconds",Math.max(0,Integer.parseInt(s.toString().trim()))).apply(); }catch(Exception ignored){}
            }
            public void afterTextChanged(android.text.Editable e){}
        });

        Button perm=button("权限与诊断"); perm.setOnClickListener(v->showPermissionPreparation());
        box.addView(perm,new LinearLayout.LayoutParams(-1,dp(50)));
        settingsDialog[0]=new AlertDialog.Builder(this).setTitle("设置").setView(box).setNegativeButton("关闭",null).create();
        settingsDialog[0].show();
    }

    void showPermissionPreparation(){
        String overlay=hasOverlayPermission()?"✓":"未授权";
        String storage=hasAllFilesPermission()?"✓":"未授权";
        String usage=hasUsageAccess()?"✓":"未授权";
        StringBuilder msg=new StringBuilder();
        msg.append("悬浮窗：").append(overlay).append("\n");
        msg.append("完整文件访问：").append(storage).append("\n");
        msg.append("使用情况访问：").append(usage).append("\n");
        msg.append("\n普通权限会在首次启动时自动申请。\n");
        msg.append("特殊权限需要系统授权，Manifest 已提前声明。\n");
        new AlertDialog.Builder(this).setTitle("权限准备")
            .setItems(new String[]{"授权悬浮窗","授权完整文件访问","授权使用情况访问","忽略电池优化"},(d,w)->{
                if(w==0) openOverlaySettings();
                else if(w==1) openAllFilesSettings();
                else if(w==2) openUsageSettings();
                else requestBatteryOptimization();
            }).setMessage(msg.toString()).setNegativeButton("关闭",null).show();
    }

    void refresh(){
        refreshPresets(); refreshApps();
        if(selectedPackage==null) info.setText("请选择 APP，然后点击窗口预设启动；双击 APP 可直接启动");
        else info.setText("已选择： "+selectedName+"    → 双击直接启动，或点击窗口预设启动");
    }

    void refreshPresets(){
        presetRow.removeAllViews();
        for(int i=0;i<presets.size();i++){
            final int index=i; Preset p=presets.get(i);
            String modeText=p.mode==6?"全屏模式":"模式"+p.mode;
            Button b=button(p.name+"\n左间距 "+p.x+"  上间距 "+p.y+"   "+p.w+" × "+p.h+"\n"+modeText);
            b.setGravity(Gravity.CENTER); b.setTextSize(12);
            b.setOnClickListener(v->{
                if(selectedPackage==null){
                    Toast.makeText(this,"请先选择 APP",Toast.LENGTH_SHORT).show(); return;
                }
                launchApp(p);
            });
            b.setOnLongClickListener(v->{presetMenu(index);return true;});
            presetRow.addView(b,new LinearLayout.LayoutParams(dp(220),dp(100)));
        }
    }

    void refreshApps(){
        appGrid.removeAllViews();
        for(AppItem item:apps){
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER);
            card.setPadding(dp(5),dp(5),dp(5),dp(5));
            card.setBackgroundResource(item.pkg.equals(selectedPackage)?R.drawable.card_selected:R.drawable.card);
            ImageView icon=new ImageView(this);
            try{
                ApplicationInfo ai=getPackageManager().getApplicationInfo(item.pkg,0);
                icon.setImageDrawable(getPackageManager().getApplicationIcon(ai));
            }catch(Exception ignored){}
            card.addView(icon,new LinearLayout.LayoutParams(dp(46),dp(46)));
            TextView name=text(item.name,12); name.setGravity(Gravity.CENTER); name.setMaxLines(1);
            card.addView(name,new LinearLayout.LayoutParams(dp(90),dp(30)));
            card.setOnClickListener(v->{
                selectedPackage=item.pkg; selectedName=item.name;
                long now=System.currentTimeMillis(); Object last=v.getTag(); v.setTag(now);
                refreshApps();
                if(last instanceof Long && now-(Long)last<350) launchAppDirect(item.pkg,item.name);
                else info.setText("已选择： "+item.name+"    → 双击直接启动，或点击窗口预设启动");
            });
            card.setOnLongClickListener(v->{appLongMenu(item);return true;});
            appGrid.addView(card,new LinearLayout.LayoutParams(dp(100),dp(88)));
        }
        if(apps.isEmpty()){
            TextView empty=text("点击左侧“＋”添加 APP",15); empty.setGravity(Gravity.CENTER);
            appGrid.addView(empty,new LinearLayout.LayoutParams(dp(260),dp(90)));
        }
    }

    void appLongMenu(AppItem item){
        new AlertDialog.Builder(this).setTitle(item.name)
                .setItems(new String[]{"关闭 APP","删除快捷方式"},(d,w)->{if(w==0)closeApp(item.pkg);else deleteApp(item);}).show();
    }

    void closeApp(String pkg){
        try{
            android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
            am.killBackgroundProcesses(pkg);
        }catch(Exception ignored){}
        Toast.makeText(this,"已发送关闭请求："+selectedOrName(pkg),Toast.LENGTH_SHORT).show();
    }

    String selectedOrName(String pkg){for(AppItem a:apps)if(a.pkg.equals(pkg))return a.name;return pkg;}

    void deleteApp(AppItem item){
        new AlertDialog.Builder(this).setTitle(item.name).setMessage("删除这个 APP 快捷方式？")
                .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
                    apps.remove(item);
                    if(item.pkg.equals(selectedPackage)){selectedPackage=null;selectedName=null;}
                    saveApps(); refresh();
                }).show();
    }

    void chooseApp(){
        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> list=new ArrayList<>();
        for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(!ai.packageName.equals(getPackageName())&&pm.getLaunchIntentForPackage(ai.packageName)!=null) list.add(ai);
        }
        Collections.sort(list,(a,b)->pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));

        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8),dp(4),dp(8),dp(4));
        EditText search=textField("搜索 APP",""); box.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        // 添加 APP 默认选中“用户”，不再显示“默认用户分类”字样。
        // 仍保留“系统”按钮，方便需要时添加车机系统 APP。
        String[] cats={"用户","系统"};
        Button[] tabBtns=new Button[cats.length];
        for(int i=0;i<cats.length;i++){
            Button b=button(cats[i]); b.setTextSize(12); tabBtns[i]=b;
            tabs.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));
        }
        tabBtns[0].setBackgroundResource(R.drawable.card_selected);
        box.addView(tabs);
        LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this); scroll.addView(rows); box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加 APP").setView(box).setNegativeButton("关闭",null).create();
        final int[] category={0};
        Runnable refreshAppPicker=()->{
            rows.removeAllViews(); String q=search.getText().toString().trim().toLowerCase(); int count=0;
            for(ApplicationInfo ai:list){
                boolean system=(ai.flags & ApplicationInfo.FLAG_SYSTEM)!=0;
                boolean updated=(ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;
                boolean show=(category[0]==0 && !system) || (category[0]==1 && system);
                String name=pm.getApplicationLabel(ai).toString();
                if(!show || (!q.isEmpty()&&!name.toLowerCase().contains(q))) continue;
                LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8),dp(3),dp(8),dp(3));
                ImageView icon=new ImageView(this); try{icon.setImageDrawable(pm.getApplicationIcon(ai));}catch(Exception ignored){}
                row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
                LinearLayout texts=new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL); texts.setPadding(dp(10),0,dp(4),0);
                TextView n=text(name,14); TextView pkg=text(ai.packageName,10); pkg.setTextColor(Color.GRAY);
                texts.addView(n,new LinearLayout.LayoutParams(-1,dp(27))); texts.addView(pkg,new LinearLayout.LayoutParams(-1,dp(20)));
                row.addView(texts,new LinearLayout.LayoutParams(0,dp(52),1));
                row.setBackgroundResource(R.drawable.card);
                row.setOnClickListener(v->{ boolean exists=false; for(AppItem a:apps)if(a.pkg.equals(ai.packageName)){exists=true;break;} if(!exists){apps.add(new AppItem(ai.packageName,name));saveApps();} selectedPackage=ai.packageName; selectedName=name; refresh(); dialog.dismiss(); });
                rows.addView(row,new LinearLayout.LayoutParams(-1,dp(56))); if(++count>=80) break;
            }
        };
        for(int i=0;i<tabBtns.length;i++){ final int ci=i; tabBtns[i].setOnClickListener(v->{category[0]=ci; for(int j=0;j<tabBtns.length;j++)tabBtns[j].setBackgroundResource(j==ci?R.drawable.card_selected:R.drawable.button); refreshAppPicker.run();}); }
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){refreshAppPicker.run();} public void afterTextChanged(android.text.Editable e){}});
        dialog.show(); refreshAppPicker.run();
    }

    void showNotes(){
        EditText edit=new EditText(this); edit.setText(prefs.getString("notes","")); edit.setTextColor(Color.WHITE); edit.setHintTextColor(Color.GRAY); edit.setGravity(Gravity.TOP|Gravity.LEFT); edit.setHint("在这里记录内容……"); edit.setSingleLine(false); edit.setMinLines(12); edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); edit.setPadding(dp(12),dp(12),dp(12),dp(12));
        new AlertDialog.Builder(this).setTitle("记事本").setView(edit).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{prefs.edit().putString("notes",edit.getText().toString()).apply(); Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show();}).show();
    }

    void presetMenu(int index){
        Preset p=presets.get(index);
        new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"编辑预设","删除预设"},(d,w)->{
            if(w==0)editPreset(index);else{presets.remove(index);savePresets();refresh();}
        }).show();
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
        dialog.show();
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
        new AlertDialog.Builder(this).setTitle("屏幕诊断")
                .setMessage(s).setPositiveButton("新建预设",(x,w)->editPreset(-1))
                .setNegativeButton("关闭",null).show();
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
        dialog.show();
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
            new AlertDialog.Builder(this).setTitle("选择窗口预设").setItems(items,(d,w)->{preset[0]=w-1;presetPick.setText(w==0?items[0]:items[w]);}).show();
        });
        new AlertDialog.Builder(this).setTitle("添加自动启动任务").setView(box)
                .setNegativeButton("取消",null).setPositiveButton("添加",(d,w)->{
                    if(pkg[0]==null){Toast.makeText(this,"请选择 APP",Toast.LENGTH_SHORT).show();return;}
                    try{
                        JSONObject o=new JSONObject(); o.put("pkg",pkg[0]); o.put("name",name[0]); o.put("preset",preset[0]);
                        tasks[0].put(o); refresh.run();
                    }catch(Exception ignored){}
                }).show();
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
        String[] names=new String[list.size()]; for(int i=0;i<list.size();i++) names[i]=list.get(i).name;
        new AlertDialog.Builder(this).setTitle("选择 APP").setItems(names,(d,w)->callback.onChoose(list.get(w))).show();
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
