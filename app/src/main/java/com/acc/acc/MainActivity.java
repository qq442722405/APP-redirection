package com.acc.acc;

import com.acc.acc.R;
import android.app.ActivityOptions;
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
import java.net.*;
import android.content.res.AssetFileDescriptor;


public class MainActivity extends AppCompatActivity {

    @Override protected void attachBaseContext(Context base){super.attachBaseContext(DesignTypography.fixedFonts(base));}

    /** Keep draggable APP/preset cards above other main-page layers. */
    private void bringMovableItemToFront(android.view.View view) {
        if (view == null) return;
        view.bringToFront();
        if (view.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent = (android.view.ViewGroup) view.getParent();
            parent.invalidate();
        }
        view.invalidate();
    }


    static final String PREF="container_prefs";
    static final String APPS="apps";
    static final String PRESETS="presets";

    SharedPreferences prefs;
    LauncherDesign design;


    LinearLayout presetRow, appGrid;
    FrameLayout mainFrame;
    long lastMainAppTapTime=0L;
    int lastMainAppTapIndex=-1;
    int presetCategoryFilter=0; // 0=左, 1=中, 2=右
    Button[] presetCategoryButtons;
    TextView info;
    String selectedPackage=null;
    String selectedName=null;
    Boolean lastOverlayState=null;

    // 容器本身固定避让车机原生区域
    static final int TOP_BLANK=80;

    ArrayList<AppItem> apps=new ArrayList<>();
    ArrayList<Preset> presets=new ArrayList<>();

    static class AppItem {
        String pkg,name;
        AppItem(String p,String n){pkg=p;name=n;}
    }

    static class Preset {
        String id=UUID.randomUUID().toString();
        String name;
        int x,y,w,h,displayId,mode,category;
        Preset(String n,int x,int y,int w,int h){this(n,x,y,w,h,-1,1,0);}
        Preset(String n,int x,int y,int w,int h,int displayId,int mode){this(n,x,y,w,h,displayId,mode,0);}
        Preset(String n,int x,int y,int w,int h,int displayId,int mode,int category){
            this.name=n; this.x=x; this.y=y; this.w=w; this.h=h; this.displayId=displayId; this.mode=mode; this.category=Math.max(0,Math.min(2,category));
        }
    }

    int inferPresetCategory(String name){
        String n=name==null?"":name.trim();
        if(n.startsWith("左")) return 0;
        if(n.startsWith("右")) return 2;
        return 1;
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
        float saved = prefs==null ? 1.0f : prefs.getFloat("design_font_scale",1.0f);
        return Math.max(0.20f, Math.min(3.0f, saved));
    }

    float menuFontScale(){
        float saved = prefs==null ? 1.0f : prefs.getFloat("design_font_scale",1.0f);
        return Math.max(0.20f, Math.min(3.0f, saved));
    }

    float fontScale(){
        return mainUiContext ? mainFontScale() : menuFontScale();
    }

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
        t.setText(s); t.setTextColor(Color.WHITE); DesignTypography.setPx(t,size*fontScale());
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    // 主界面专用文字：只受“主界面字体大小”控制。
    TextView mainText(String s,float size){
        TextView t=new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); DesignTypography.setPx(t,size*mainFontScale());
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
        b.setText(s); b.setTextColor(Color.WHITE); DesignTypography.setPx(b,14*fontScale());
        b.setMinHeight(adaptiveBoxHeight(44));
        b.setAllCaps(false); b.setBackground(DesignTheme.resource(this,R.drawable.button));
        return b;
    }

    TextView plusButton(){
        TextView b=text("+",30);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.WHITE);
        b.setBackground(DesignTheme.resource(this,R.drawable.button));
        return b;
    }

    EditText numberField(String label,String value){
        EditText e=new EditText(this);
        e.setHint(label); e.setText(value); e.setTextColor(Color.WHITE); DesignTypography.setPx(e,14*fontScale());
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
            DesignTypography.setPx(b,10*fontScale()); b.setMinWidth(0); b.setPadding(0,0,0,0);
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
        DesignTypography.setPx(e,14*fontScale());
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
        prefs.edit().remove("design_wallpaper").apply();
        // 新安装默认值：这些值来自用户确认过的配置，但不会读取/加载 JSON 文件。
        // 这样默认值与配置文件完全解耦，避免旧/无效 APP 包名导致启动闪退。
        SharedPreferences.Editor defaults=prefs.edit();
        if(!prefs.contains("design_font_scale")) defaults.putFloat("design_font_scale",1.00f);
        if(!prefs.contains("ui_scale")) defaults.putFloat("ui_scale",1.30f);
        if(!prefs.contains("main_app_columns")) defaults.putInt("main_app_columns",10);
        if(!prefs.contains("main_top_blank")) defaults.putInt("main_top_blank",80);
        if(!prefs.contains("boot_delay_seconds")) defaults.putInt("boot_delay_seconds",0);
        if(!prefs.contains("hide_main_background_acc")) defaults.putBoolean("hide_main_background_acc",true);
        // 默认窗口预设直接写入 SharedPreferences；不保存任何默认 APP 包名。
        if(!prefs.contains(PRESETS)) defaults.putString(PRESETS, defaultPresetJson());
        defaults.apply();
        design=new LauncherDesign(this);
        loadData();
        buildUI();
        lastOverlayState=hasOverlayPermission();
        requestRuntimePermissions();
        SimoVoiceService.sync(this);
        handleSimoLaunch(getIntent());
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);handleSimoLaunch(intent);
    }
    void handleSimoLaunch(Intent intent){
        if(intent==null)return;
        VoiceCommands.Command command=SimoVoiceService.takeLaunch(this,intent.getStringExtra(SimoVoiceService.EXTRA_TICKET));
        intent.removeExtra(SimoVoiceService.EXTRA_TICKET);
        if(command!=null){apps.clear();presets.clear();loadData();design.refreshMain();design.launchSelection(command.pkg,command.name);}
    }

    @Override protected void onResume(){
        super.onResume();
        boolean now=hasOverlayPermission();
        if(lastOverlayState!=null && now!=lastOverlayState){
            lastOverlayState=now;
            buildUI();
        }
        if(design!=null)design.refreshPermissions();
    }

    static final int REQ_RUNTIME_PERMS = 19041;
    static final int REQ_EXPORT_CONFIG = 19042;
    static final int REQ_IMPORT_CONFIG = 19043;

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

    // 从已确认的配置提取窗口参数作为“程序默认值”。
    // 这里只返回窗口预设 JSON，不读取外部配置文件，也不包含 APP 列表。
    String defaultPresetJson(){
        JSONArray a=new JSONArray();
        try{
            a.put(new JSONObject().put("name","左 1/1").put("x",105).put("y",0).put("w",2183).put("h",960).put("displayId",-1).put("mode",1).put("category",0));
            a.put(new JSONObject().put("name","左 2/3").put("x",638).put("y",0).put("w",1650).put("h",960).put("displayId",-1).put("mode",1).put("category",0));
            a.put(new JSONObject().put("name","左 1/2").put("x",1088).put("y",0).put("w",1200).put("h",960).put("displayId",-1).put("mode",1).put("category",0));
            a.put(new JSONObject().put("name","左 1/4").put("x",1688).put("y",0).put("w",600).put("h",960).put("displayId",-1).put("mode",1).put("category",0));
            a.put(new JSONObject().put("name","中 上-80").put("x",2288).put("y",80).put("w",2160).put("h",772).put("displayId",-1).put("mode",1).put("category",1));
            a.put(new JSONObject().put("name","中 1/1").put("x",2288).put("y",0).put("w",2160).put("h",960).put("displayId",-1).put("mode",1).put("category",1));
            a.put(new JSONObject().put("name","右 1/1").put("x",4320).put("y",0).put("w",2160).put("h",960).put("displayId",-1).put("mode",1).put("category",2));
        }catch(Exception ignored){}
        return a.toString();
    }

    void loadData(){
        try{
            JSONArray a=new JSONArray(prefs.getString(APPS,"[]"));
            PackageManager pm=getPackageManager();
            for(int i=0;i<a.length();i++){
                String p=a.getString(i);
                if(p.startsWith("action:")){String key=p.substring(7);String label="back".equals(key)?"返回":"home".equals(key)?"首页":"menu".equals(key)?"最近任务":"关闭当前 APP";apps.add(new AppItem(p,label));continue;}
                try{
                    ApplicationInfo ai=pm.getApplicationInfo(p,0);
                    apps.add(new AppItem(p,pm.getApplicationLabel(ai).toString()));
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}

        try{
            boolean idsAdded=false;
            JSONArray a=new JSONArray(prefs.getString(PRESETS,"[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                String pn=o.optString("name","");
                int pc=o.has("category") ? o.optInt("category",1) : inferPresetCategory(pn);
                Preset preset=new Preset(
                        pn,o.optInt("x",0),o.optInt("y",0),
                        o.optInt("w",0),o.optInt("h",0),o.optInt("displayId",-1),o.optInt("mode",1),pc
                );
                String savedId=o.optString("id","");
                if(!savedId.isEmpty())preset.id=savedId;else idsAdded=true;
                presets.add(preset);
            }
            // Persist IDs only after the complete legacy array has parsed successfully.
            if(idsAdded)savePresets();
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
                o.put("id",p.id); o.put("name",p.name); o.put("x",p.x); o.put("y",p.y);
                o.put("w",p.w); o.put("h",p.h); o.put("displayId",p.displayId); o.put("mode",p.mode); o.put("category",p.category);
                a.put(o);
            }
        }catch(Exception ignored){}
        prefs.edit().putString(PRESETS,a.toString()).apply();
    }

    void buildUI(){ design.buildMain(); }

    /**
     * 刷新主界面中的窗口预设和已添加 APP。
     * 保持主界面控件对象不变，只重建两个列表，避免重新 setContentView
     * 导致车机 ROM 出现焦点/触控坐标漂移。
     */
    /** 主界面长按菜单：左移、右移、删除、取消。主界面不再进入拖动状态。 */
    void showMainItemMenu(final int type, final int index){
        if(type==0 && (index<0 || index>=presets.size())) return;
        if(type==1 && (index<0 || index>=apps.size())) return;
        String name = type==0 ? presets.get(index).name : apps.get(index).name;
        String[] items={"左移","右移","删除","取消"};
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle("操作："+name)
                .setItems(items,(d,which)->{
                    if(which==0) moveMainItem(type,index,-1);
                    else if(which==1) moveMainItem(type,index,1);
                    else if(which==2) deleteMainItem(type,index);
                }).create();
        showFixed900x960(dlg);
    }

    void moveMainItem(int type,int index,int direction){
        if(type==1){
            int target=index+direction;
            if(target<0 || target>=apps.size()) return;
            Collections.swap(apps,index,target);
            saveApps(); refresh();
            return;
        }
        // 窗口预设只在当前左/中/右分类内左右移动，避免跨分类。
        int target=-1;
        int step=direction<0?-1:1;
        for(int i=index+step;i>=0&&i<presets.size();i+=step){
            if(presets.get(i).category==presetCategoryFilter){ target=i; break; }
        }
        if(target<0) return;
        Collections.swap(presets,index,target);
        savePresets(); refresh();
    }

    void deleteMainItem(int type,int index){
        if(type==0){
            if(index<0||index>=presets.size()) return;
            String name=presets.get(index).name;
            new AlertDialog.Builder(this).setTitle("删除窗口预设")
                    .setMessage("确定删除“"+name+"”吗？")
                    .setNegativeButton("取消",null)
                    .setPositiveButton("删除",(d,w)->{
                        if(index>=0&&index<presets.size()){presets.remove(index);savePresets();refresh();}
                    }).show();
        }else{
            if(index<0||index>=apps.size()) return;
            AppItem a=apps.get(index);
            new AlertDialog.Builder(this).setTitle("删除 APP")
                    .setMessage("确定删除“"+a.name+"”吗？")
                    .setNegativeButton("取消",null)
                    .setPositiveButton("删除",(d,w)->{
                        if(index>=0&&index<apps.size()){
                            AppItem removed=apps.remove(index);
                            if(removed.pkg.equals(selectedPackage)){
                                selectedPackage=null; selectedName=null;
                                prefs.edit().remove("selected_control_package").apply();
                            }
                            prefs.edit().remove("design_app_preset_"+removed.pkg).remove("design_app_preset_id_"+removed.pkg).apply();
                            saveApps(); refresh();
                        }
                    }).show();
        }
    }

    void setMainItemLongClick(View view,int type,int index){
        view.setOnLongClickListener(v->{
            showMainItemMenu(type,index);
            return true;
        });
    }

    void refresh(){ design.refreshMain(); }

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
    /**
     * 所有弹窗统一采用与主界面相同的 Window 坐标体系：不做任何触摸坐标偏移，
     * 不使用 FLAG_LAYOUT_IN_SCREEN，也不通过 Window.Callback 改写 MotionEvent。
     * 这样主界面、设置页、APP选择、预设编辑等窗口使用同一套触控坐标。
     */
    void placeDialogBelowTop(Dialog dialog){
        if(dialog==null || dialog.getWindow()==null) return;
        Window w=dialog.getWindow();
        try{
            w.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.setBackgroundDrawable(DesignTheme.panel(this));
            if(Build.VERSION.SDK_INT>=19) w.getDecorView().setSystemUiVisibility(0);
            WindowManager.LayoutParams lp=w.getAttributes();
            lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
            lp.x=0; lp.y=0;
            lp.width=Math.min(dp(900),Math.max(dp(320),getRealScreenSize().x-dp(20)));
            lp.height=Math.min(dp(960),Math.max(dp(420),getRealScreenSize().y));
            lp.dimAmount=0.55f;
            w.setAttributes(lp);
            View content=dialog.findViewById(android.R.id.content);
            if(content!=null) content.setPadding(content.getPaddingLeft(),dp(80),content.getPaddingRight(),content.getPaddingBottom());
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }catch(Exception ignored){}
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
                DesignTypography.setPx(b,14*fontScale());
                b.setBackground(DesignTheme.resource(this,R.drawable.button));
                b.setMinHeight(dp(50));
                b.setMinWidth(dp(100));
                b.setMaxHeight(dp(50));
                b.setWidth(dp(100));
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

    // 统一设置/编辑窗口设计区域：900×960；实际可用内容从顶部 80px 开始。
    void showFixed900x960(AlertDialog dialog){
        if(dialog==null)return;
        dialog.show();
        styleDialogActionButtons(dialog);
        adaptDialogBoxes(dialog);
        // 统一 900×960 设计区；顶部 80px 是内容预留区，不移动整个 Window。
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(DesignTheme.panel(this));
            WindowManager.LayoutParams lp=w.getAttributes();
            lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
            lp.x=0; lp.y=0;
            lp.width=Math.min(dp(900),Math.max(dp(320),getRealScreenSize().x-dp(20)));
            lp.height=Math.min(dp(960),Math.max(dp(420),getRealScreenSize().y));
            w.setAttributes(lp);
            // 所有弹窗内容统一从顶部 80px 开始，避免车机顶部保留区造成视觉/触控错位。
            View content=dialog.findViewById(android.R.id.content);
            if(content!=null){
                content.setPadding(content.getPaddingLeft(),dp(80),content.getPaddingRight(),content.getPaddingBottom());
            }
        }
    }

    void showSettingsMenu(){ design.settings(); }

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
        final boolean[] directionDraft={prefs.getBoolean("floating_vertical",false)};
        final String[] shapeDraft={prefs.getString("floating_single_icon_shape","rounded")};
        Button dirBtn=button(prefs.getBoolean("floating_vertical",false)?"竖向":"横向");
        direction.addView(dirBtn,new LinearLayout.LayoutParams(dp(110),dp(48)));
        dirBtn.setOnClickListener(v->{
            boolean vertical=!directionDraft[0];
            directionDraft[0]=vertical;
            dirBtn.setText(vertical?"竖向":"横向");
        });
        box.addView(direction,new LinearLayout.LayoutParams(-1,dp(58)));

        // 悬浮窗按钮：左侧名称，中间显示当前已添加按钮，右侧 +。
        LinearLayout floatingAddRow=new LinearLayout(this);
        floatingAddRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView floatingAddTitle=text("悬浮窗按钮",14);
        floatingAddRow.addView(floatingAddTitle,new LinearLayout.LayoutParams(dp(100),dp(58)));

        HorizontalScrollView currentScroll=new HorizontalScrollView(this);
        currentScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout currentRow=new LinearLayout(this);
        currentRow.setGravity(Gravity.CENTER_VERTICAL);
        currentScroll.addView(currentRow,new HorizontalScrollView.LayoutParams(-2,dp(58)));
        floatingAddRow.addView(currentScroll,new LinearLayout.LayoutParams(0,dp(64),1));

        TextView floatingAdd=plusButton();
        floatingAdd.setText("+");
        DesignTypography.setPx(floatingAdd,30);
        floatingAdd.setContentDescription("添加到悬浮窗口");
        floatingAddRow.addView(floatingAdd,new LinearLayout.LayoutParams(dp(58),dp(58)));
        floatingAdd.setOnClickListener(v->showFloatingAppChooser());
        box.addView(floatingAddRow,new LinearLayout.LayoutParams(-1,dp(68)));

        // 当前按钮长按删除；APP 与系统按钮都显示在名称和 + 之间。
        final Runnable[] refreshCurrentButtons={null};
        refreshCurrentButtons[0]=()->{
            currentRow.removeAllViews();
            try{
                JSONArray a=new JSONArray(prefs.getString("floating_apps","[]"));
                for(int i=0;i<a.length();i++){
                    JSONObject o=a.optJSONObject(i); if(o==null)continue;
                    final String pkg=o.optString("pkg",""); if(pkg.isEmpty())continue;
                    String name=o.optString("name",getAppLabelSafe(pkg));
                    Button chip=button(name); DesignTypography.setPx(chip,10);
                    currentRow.addView(chip,new LinearLayout.LayoutParams(dp(100),dp(48)));
                    chip.setOnLongClickListener(v->{
                        try{
                            JSONArray cur=new JSONArray(prefs.getString("floating_apps","[]")); JSONArray out=new JSONArray();
                            for(int j=0;j<cur.length();j++){JSONObject x=cur.optJSONObject(j); if(x!=null&&!pkg.equals(x.optString("pkg","")))out.put(x);}
                            prefs.edit().putString("floating_apps",out.toString()).remove("floating_preset_"+pkg).apply();
                            refreshCurrentButtons[0].run();
                            restartFloatingServiceSafe();
                            Toast.makeText(this,"已删除："+name,Toast.LENGTH_SHORT).show();
                        }catch(Exception ignored){}
                        return true;
                    });
                }
            }catch(Exception ignored){}
            if(prefs.getBoolean("floating_back",false)) addCurrentButtonChip(currentRow,"返回",()->{prefs.edit().putBoolean("floating_back",false).apply();refreshCurrentButtons[0].run();restartFloatingServiceSafe();});
            if(prefs.getBoolean("floating_home",false)) addCurrentButtonChip(currentRow,"主页",()->{prefs.edit().putBoolean("floating_home",false).apply();refreshCurrentButtons[0].run();restartFloatingServiceSafe();});
            if(prefs.getBoolean("floating_menu",false)) addCurrentButtonChip(currentRow,"菜单",()->{prefs.edit().putBoolean("floating_menu",false).apply();refreshCurrentButtons[0].run();restartFloatingServiceSafe();});
            if(prefs.getBoolean("floating_close",false)) addCurrentButtonChip(currentRow,"关闭",()->{prefs.edit().putBoolean("floating_close",false).apply();refreshCurrentButtons[0].run();restartFloatingServiceSafe();});
            if(currentRow.getChildCount()==0){TextView empty=text("暂无",10);empty.setTextColor(Color.GRAY);currentRow.addView(empty,new LinearLayout.LayoutParams(dp(55),dp(48)));}
        };
        refreshCurrentButtons[0].run();
        design.floatingButtonsRefresh=refreshCurrentButtons[0];

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
        Button shapeBtn=button("rounded".equals(shapeDraft[0])?"圆角正方形":"圆形");
        shapeBtn.setGravity(Gravity.CENTER);
        shapeRow.addView(shapeBtn,new LinearLayout.LayoutParams(dp(140),dp(48)));
        shapeBtn.setOnClickListener(v->{
            String next="rounded".equals(shapeDraft[0])?"circle":"rounded";
            shapeDraft[0]=next;
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
        Button[] designGestures=new Button[7];
        String[] gestureKeys={"tap","double","long","left","right","up","down"};
        String[] gestureNames={"点击","双击","长按","左滑","右滑","上滑","下滑"};
        for(int gi=0;gi<gestureKeys.length;gi++){
            final String gk=gestureKeys[gi], gn=gestureNames[gi];
            LinearLayout gr=new LinearLayout(this); gr.setGravity(Gravity.CENTER_VERTICAL);
            gr.addView(text(gn,13),new LinearLayout.LayoutParams(0,dp(48),1));
            Button gb=button(getGestureLabel(prefs.getString("floating_gesture_"+gk,"none")));
            designGestures[gi]=gb;
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
        actionBar.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        actionBar.setPadding(dp(4),dp(8),dp(4),0);

        Button back=button("返回");
        Button save=button("保存");
        actionBar.addView(back,new LinearLayout.LayoutParams(dp(100),dp(50)));
        actionBar.addView(save,new LinearLayout.LayoutParams(dp(100),dp(50)));
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
                    .putBoolean("floating_vertical",directionDraft[0])
                    .putString("floating_single_icon_shape",shapeDraft[0])
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

        DesignSurface layout=design.page("悬浮窗口设置");
        layout.bind(5,enable);layout.bind(7,lockSwitch);layout.bind(9,dirBtn);layout.bind(11,opacity);
        layout.bind(13,icon);layout.bind(15,spacing);layout.bind(17,singleMode);layout.bind(20,shapeBtn);
        int[] gestureSlots={18,23,25,27,29,31,33};for(int i=0;i<7;i++)layout.bind(gestureSlots[i],designGestures[i]);
        layout.action(34,this::showFloatingAppChooser);
        layout.place(currentScroll,DesignSurface.rect(440,30,500,44,16));
        back.setText("取消");layout.bind(3,back);layout.bind(4,save);
        design.show(dialog,layout);
        dialog.setOnDismissListener(d->{design.openDialogs.remove(dialog);design.floatingButtonsRefresh=null;});
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

    void addCurrentButtonChip(LinearLayout row,String label,Runnable deleteAction){
        Button chip=button(label); DesignTypography.setPx(chip,10);
        row.addView(chip,new LinearLayout.LayoutParams(dp(90),dp(48)));
        chip.setOnLongClickListener(v->{
            new AlertDialog.Builder(this).setTitle("删除悬浮窗按钮").setMessage("是否删除“"+label+"”按钮？")
                    .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->deleteAction.run()).show();
            return true;
        });
    }

    void restartFloatingServiceSafe(){
        stopFloatingService();
        if(prefs.getBoolean("floating_enabled",false)) startFloatingService();
    }

    void showFloatingAppChooser(){ design.chooseFloatingApp(); }

    void refreshFloatingChooserButtons(LinearLayout rows, String[] selectedPkg){
        rows.removeAllViews();
        String[][] buttons={{"back","返回","返回上一级"},{"home","主页","返回车机主页"},{"menu","菜单","打开菜单操作"},{"close","关闭","关闭当前 APP"}};
        for(String[] item:buttons){
            final String value="button:"+item[0];
            LinearLayout tile=new LinearLayout(this); tile.setGravity(Gravity.CENTER_VERTICAL); tile.setPadding(dp(12),dp(6),dp(12),dp(6));
            ImageView iv=new ImageView(this);
            if("back".equals(item[0]))iv.setImageResource(R.drawable.ic_back);
            else if("home".equals(item[0]))iv.setImageResource(R.drawable.ic_home);
            else iv.setImageResource(R.drawable.ic_menu);
            tile.addView(iv,new LinearLayout.LayoutParams(dp(54),dp(54)));
            TextView tv=text(item[1]+"\n"+item[2],13); tv.setGravity(Gravity.CENTER_VERTICAL); tile.addView(tv,new LinearLayout.LayoutParams(0,dp(64),1));
            if(value.equals(selectedPkg[0])) tile.setBackground(DesignTheme.resource(this,R.drawable.card_selected));
            tile.setOnClickListener(v->{selectedPkg[0]=value; refreshFloatingChooserButtons(rows,selectedPkg);});
            rows.addView(tile,new LinearLayout.LayoutParams(-1,dp(76)));
        }
    }

    void refreshFloatingChooserApps(ArrayList<ApplicationInfo> allApps, LinearLayout rows, int[] category, String[] selectedPkg){
        rows.removeAllViews();
        ArrayList<ApplicationInfo> filtered=new ArrayList<>();
        for(ApplicationInfo ai:allApps){
            boolean system=(ai.flags & ApplicationInfo.FLAG_SYSTEM)!=0;
            if(category[0]==0 && system)continue;
            if(category[0]==1 && !system)continue;
            if(category[0]==3)continue;
            filtered.add(ai);
        }
        if(filtered.isEmpty()){
            TextView empty=text("没有找到 APP",12);
            empty.setGravity(Gravity.CENTER);
            rows.addView(empty,new LinearLayout.LayoutParams(-1,dp(70)));
            return;
        }
        LinearLayout row=null;
        int col=0;
        for(ApplicationInfo ai:filtered){
            if(col==0){
                row=new LinearLayout(this);
                row.setGravity(Gravity.CENTER);
                rows.addView(row,new LinearLayout.LayoutParams(-1,dp(112)));
            }
            final String pkg=ai.packageName;
            LinearLayout tile=new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(3),dp(3),dp(3),dp(3));
            ImageView iv=new ImageView(this);
            try{iv.setImageDrawable(getPackageManager().getApplicationIcon(ai));}catch(Exception ignored){}
            tile.addView(iv,new LinearLayout.LayoutParams(dp(54),dp(54)));
            TextView tv=text(getAppLabelSafe(pkg),11);
            tv.setGravity(Gravity.CENTER);
            tv.setMaxLines(2);
            tile.addView(tv,new LinearLayout.LayoutParams(-1,dp(38)));
            if(pkg.equals(selectedPkg[0]))tile.setBackground(DesignTheme.resource(this,R.drawable.card_selected));
            tile.setOnClickListener(v->{
                selectedPkg[0]=pkg;
                refreshFloatingChooserApps(allApps,rows,category,selectedPkg);
            });
            row.addView(tile,new LinearLayout.LayoutParams(0,dp(108),1));
            col=(col+1)%6;
        }
    }

    void showGestureChooser(String key,String title,Button target){ design.gestureChooser(key,title,target); }

    void showGestureAppChooser(String key,String title,Button target,AlertDialog parent){ design.chooseGestureApp(key,title,target,parent); }

    void showInterfaceOptionsDialog(){ design.interfaceOptions(); }

    /** 添加 APP：全部/用户/系统分类，正方形图标+名称；APP 很多时可上下滚动。 */
    void chooseApp(){ design.chooseApp(); }

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
            Preset old=new Preset("",0,0,0,0,-1,1,1);
            showPresetEditor(-1,old);
            return;
        }
        Preset old=presets.get(index);
        showPresetEditor(index,old);
    }

    String presetClipboardText(EditText name,EditText x,EditText y,EditText width,EditText height,int mode,int category){
        try{
            JSONObject o=new JSONObject();
            o.put("name",name.getText().toString());
            o.put("x",number(x,0)); o.put("y",number(y,0));
            o.put("w",number(width,0)); o.put("h",number(height,0));
            o.put("mode",mode);
            o.put("category",category);
            return o.toString();
        }catch(Exception e){ return ""; }
    }

    void copyPresetToClipboard(EditText name,EditText x,EditText y,EditText width,EditText height,int mode,int category){
        String data=presetClipboardText(name,x,y,width,height,mode,category);
        try{
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("窗口预设参数",data));
            Toast.makeText(this,"面板参数已复制",Toast.LENGTH_SHORT).show();
        }catch(Exception e){ Toast.makeText(this,"复制失败",Toast.LENGTH_SHORT).show(); }
    }

    boolean pastePresetFromClipboard(EditText name,EditText x,EditText y,EditText width,EditText height, int[] modeHolder, Button[] modeButtons, int[] categoryHolder, Button[] categoryButtons){
        try{
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(!cm.hasPrimaryClip()) { Toast.makeText(this,"剪贴板没有窗口参数",Toast.LENGTH_SHORT).show(); return false; }
            CharSequence cs=cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            JSONObject o=new JSONObject(cs.toString());
            name.setText(o.optString("name",""));
            x.setText(String.valueOf(o.optInt("x",0))); y.setText(String.valueOf(o.optInt("y",0)));
            width.setText(String.valueOf(o.optInt("w",0))); height.setText(String.valueOf(o.optInt("h",0)));
            int m=Math.max(1,Math.min(6,o.optInt("mode",1))); modeHolder[0]=m;
            if(modeButtons!=null) for(int i=0;i<modeButtons.length;i++) modeButtons[i].setBackground(DesignTheme.resource(this,i==m-1?R.drawable.card_selected:R.drawable.button));
            int c=Math.max(0,Math.min(2,o.optInt("category",1))); categoryHolder[0]=c;
            if(categoryButtons!=null) for(int i=0;i<categoryButtons.length;i++) categoryButtons[i].setBackground(DesignTheme.resource(this,i==c?R.drawable.card_selected:R.drawable.button));
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

        TextView categoryTitle=text("分类",14);
        categoryTitle.setPadding(dp(115),dp(6),0,dp(2));
        box.addView(categoryTitle,new LinearLayout.LayoutParams(-1,dp(32)));
        LinearLayout categoryRow=new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryRow.setPadding(dp(115),0,dp(4),dp(4));
        Button[] categoryButtons=new Button[3];
        final int[] categoryHolder={old.category};
        String[] categoryNames={"左","中","右"};
        for(int c=0;c<3;c++){
            final int cc=c;
            Button cb=button(categoryNames[c]);
            DesignTypography.setPx(cb,12*fontScale());
            categoryButtons[c]=cb;
            if(categoryHolder[0]==c) cb.setBackground(DesignTheme.resource(this,R.drawable.card_selected));
            cb.setOnClickListener(v->{
                categoryHolder[0]=cc;
                for(Button q:categoryButtons) if(q!=null) q.setBackground(DesignTheme.resource(this,q==v?R.drawable.card_selected:R.drawable.button));
            });
            categoryRow.addView(cb,new LinearLayout.LayoutParams(0,dp(42),1));
        }
        box.addView(categoryRow,new LinearLayout.LayoutParams(-1,dp(48)));

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
            DesignTypography.setPx(mb,11*fontScale());
            modeButtons[m-1]=mb;
            if(old.mode==mm) mb.setBackground(DesignTheme.resource(this,R.drawable.card_selected));
            mb.setOnClickListener(v->{
                modeHolder[0]=mm;
                for(Button q:modeButtons) q.setBackground(DesignTheme.resource(this,q==v?R.drawable.card_selected:R.drawable.button));
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
        ScrollView presetScroll=new ScrollView(this);
        presetScroll.setFillViewport(true);
        presetScroll.addView(box,new ScrollView.LayoutParams(-1,-2));
        content.addView(presetScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actionRow=new LinearLayout(this);
        actionRow.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(8),dp(6),dp(8),dp(6));
        Button copy=button("复制"); Button paste=button("粘贴"); Button cancel=button("取消"); Button save=button("保存");
        actionRow.addView(copy,new LinearLayout.LayoutParams(dp(100),dp(50)));
        actionRow.addView(paste,new LinearLayout.LayoutParams(dp(100),dp(50)));
        actionRow.addView(cancel,new LinearLayout.LayoutParams(dp(100),dp(50)));
        actionRow.addView(save,new LinearLayout.LayoutParams(dp(100),dp(50)));
        content.addView(actionRow,new LinearLayout.LayoutParams(-1,dp(62)));

        final Button[] modeButtonsHolder=modeButtons;
        final Button[] categoryButtonsHolder=categoryButtons;
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(index<0?"新建窗口预设":"编辑窗口预设")
                .setView(content).create();
        copy.setOnClickListener(v->copyPresetToClipboard(name,x,y,width,height,modeHolder[0],categoryHolder[0]));
        paste.setOnClickListener(v->pastePresetFromClipboard(name,x,y,width,height,modeHolder,modeButtonsHolder,categoryHolder,categoryButtonsHolder));
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            String n=name.getText().toString().trim();
            if(n.isEmpty()){Toast.makeText(this,"请输入预设名称",Toast.LENGTH_SHORT).show();return;}
            Preset p=new Preset(n,
                    Math.max(0,number(x,old.x)),
                    Math.max(0,number(y,old.y)),
                    Math.max(0,number(width,old.w)),
                    Math.max(0,number(height,old.h)),
                    -1,modeHolder[0],categoryHolder[0]);
            if(index<0) presets.add(p); else {p.id=old.id;presets.set(index,p);}
            savePresets(); refresh(); dialog.dismiss();
        });

        DesignSurface layout=design.page("新建预设窗口");
        layout.bind(3,name);layout.bind(5,x);layout.bind(9,y);layout.bind(7,width);layout.bind(11,height);
        for(int i=0;i<3;i++)layout.bind(14+i,categoryButtons[i]);
        for(int i=0;i<4;i++)layout.bind(17+i,modeButtons[i]);
        modeButtons[5].setText("全屏");layout.bind(21,modeButtons[5]);
        // Mode 5 already exists in saved presets; keep it accessible without displacing the supplied row.
        layout.place(modeButtons[4],DesignSurface.rect(853,491,120,41,16));
        for(int i=0;i<3;i++)layout.selected(categoryButtons[i],i==categoryHolder[0]);
        for(int i=0;i<6;i++)layout.selected(modeButtons[i],i+1==modeHolder[0]);
        layout.bind(22,copy);layout.bind(23,paste);layout.bind(24,cancel);layout.bind(25,save);
        int[][] quick={{31,32,33,34,35},{36,37,38,39,40},{41,42,43,44,45},{26,27,28,29,30}};
        for(int row=0;row<4;row++){
            LinearLayout oldRow=(LinearLayout)box.getChildAt(row+1);
            View[] buttons=new View[5];for(int j=0;j<5;j++)buttons[j]=oldRow.getChildAt(j+1);
            // Input was detached above; the original row now contains its label followed by five actions.
            for(int j=0;j<5;j++){((Button)buttons[j]).setText(j==4?"0":((Button)buttons[j]).getText());layout.bind(quick[row][j],buttons[j]);}
        }
        if(index>=0)((TextView)layout.bound.get("e1")).setText("编辑预设窗口");
        design.show(dialog,layout);
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
         .append(Build.VERSION.SDK_INT<23 || Settings.System.canWrite(this)?"✓ 修改系统设置\n":"✗ 修改系统设置\n");


        TextView msg=text(s.toString(),11);
        msg.setPadding(dp(4),dp(4),dp(4),dp(4));
        LinearLayout diagBox=new LinearLayout(this);
        diagBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(msg,new ScrollView.LayoutParams(-1,-2));
        diagBox.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actionRow=new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(4),dp(6),dp(4),dp(20));
        Button overlayButton=button(hasOverlayPermission()?"悬浮窗权限已开启":"开启悬浮窗权限");
        overlayButton.setOnClickListener(v->openOverlaySettings());

        Button accessibilityButton=button(isAccessibilityServiceEnabled()?"无障碍已开启":"开启无障碍服务");
        accessibilityButton.setOnClickListener(v->openAccessibilitySettings());

        Button checkButton=button("权限检查");
        checkButton.setOnClickListener(v->{
            if(Build.VERSION.SDK_INT>=23 && !hasOverlayPermission()){ openOverlaySettings(); }
            else { requestRuntimePermissions(); Toast.makeText(this,"已重新检查并请求可申请的权限",Toast.LENGTH_SHORT).show(); }
        });
        Button back=button("返回");
        Button[] diagButtons={overlayButton,accessibilityButton,checkButton,back};
        for(Button b:diagButtons) actionRow.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));
        diagBox.addView(actionRow,new LinearLayout.LayoutParams(-1,dp(80)));

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("权限与诊断")
                .setView(diagBox).create();
        back.setOnClickListener(v->dialog.dismiss());

        DesignSurface layout=design.page("权限与诊断");
        overlayButton.setText("悬浮权限");accessibilityButton.setText("无障碍权限");
        layout.bind(2,overlayButton);layout.bind(3,accessibilityButton);layout.bind(4,checkButton);layout.bind(5,back);
        layout.bind(6,scroll);DesignTypography.setPx(msg,22);
        design.show(dialog,layout);
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
                if(k.equals(APPS)||k.equals(PRESETS)||k.equals("design_wallpaper")) continue;
                Object v=all.get(k);
                if(v instanceof String || v instanceof Integer || v instanceof Long || v instanceof Float || v instanceof Boolean){
                    JSONObject item=new JSONObject(); item.put("key",k); item.put("value",v); keys.put(item);
                }
            }
            root.put("settings",keys);
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
        if(requestCode==LabUi.EXPORT){LabUi.exportResult(this,data.getData());return;}
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
                if(presetsJson!=null){
                    ed.putString(PRESETS,presetsJson.toString());
                    // A complete preset import replaces IDs. Restore only associations
                    // supplied by that import, including legacy name-only bindings below.
                    for(String key:prefs.getAll().keySet())if(key.startsWith("design_app_preset_"))ed.remove(key);
                }
                if(floatingJson!=null) ed.putString("floating_apps",floatingJson.toString());

                JSONArray settings=root.optJSONArray("settings");
                if(settings!=null){
                    for(int i=0;i<settings.length();i++){
                        JSONObject item=settings.optJSONObject(i);
                        if(item==null) continue;
                        String key=item.optString("key","");
                        if(key.isEmpty()) continue;

                        // 已删除的旧配置不再导入，防止旧配置文件把删除的项目重新带回来。
                        if("popup_left_margin".equals(key) || "popup_right_margin".equals(key) || "design_wallpaper".equals(key)) continue;
                        if(key.toLowerCase(Locale.US).contains("adb")) continue;

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

                if(!ed.commit()) throw new IOException("保存配置失败");
                apps.clear();
                presets.clear();
                loadData();
                buildUI();design.refreshOpenThemes();design.refreshPermissions();restartFloatingServiceSafe();
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

    void showAutoStartEditor(){ design.autoTasks(); }

    JSONArray loadAutoTasks(){
        try{return new JSONArray(prefs.getString("auto_start_items","[]"));}
        catch(Exception e){return new JSONArray();}
    }

    /** 自动启动添加：界面与悬浮窗添加 APP 保持一致：窗口预设 + 分类 + APP 图标/名称。 */
    void showAddAutoTaskDialog(JSONArray[] tasks,Runnable refresh){
        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> allApps=new ArrayList<>();
        try{
            for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
                if(ai.packageName.equals(getPackageName())) continue;
                if(pm.getLaunchIntentForPackage(ai.packageName)!=null) allApps.add(ai);
            }
        }catch(Exception ignored){}
        Collections.sort(allApps,(a,b)->getAppLabelSafe(a.packageName).compareToIgnoreCase(getAppLabelSafe(b.packageName)));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(4),dp(12),dp(8));

        TextView presetTitle=text("窗口预设",13);
        presetTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        root.addView(presetTitle,new LinearLayout.LayoutParams(-1,dp(34)));
        HorizontalScrollView presetScroll=new HorizontalScrollView(this);
        LinearLayout presetChooserRow=new LinearLayout(this);
        presetChooserRow.setGravity(Gravity.CENTER_VERTICAL);
        presetScroll.addView(presetChooserRow,new HorizontalScrollView.LayoutParams(-2,dp(48)));
        root.addView(presetScroll,new LinearLayout.LayoutParams(-1,dp(54)));

        final int[] selectedPreset={-1};
        ArrayList<Button> presetButtons=new ArrayList<>();
        Button defaultPreset=button("默认");
        presetButtons.add(defaultPreset);
        presetChooserRow.addView(defaultPreset,new LinearLayout.LayoutParams(dp(86),dp(44)));
        defaultPreset.setBackground(DesignTheme.resource(this,R.drawable.card_selected));
        defaultPreset.setOnClickListener(v->{
            selectedPreset[0]=-1;
            for(Button b:presetButtons)b.setBackground(DesignTheme.resource(this,b==v?R.drawable.card_selected:R.drawable.button));
        });
        for(int i=0;i<presets.size();i++){
            final int pi=i;
            Button pb=button(presets.get(i).name);
            DesignTypography.setPx(pb,11);
            presetButtons.add(pb);
            presetChooserRow.addView(pb,new LinearLayout.LayoutParams(dp(150),dp(44)));
            pb.setOnClickListener(v->{
                selectedPreset[0]=pi;
                for(Button b:presetButtons)b.setBackground(DesignTheme.resource(this,b==v?R.drawable.card_selected:R.drawable.button));
            });
        }

        LinearLayout categoryRow=new LinearLayout(this);
        categoryRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] categories={"用户","系统","全部"};
        final int[] category={0};
        final String[] selectedPkg={""};
        Button[] categoryButtons=new Button[categories.length];
        final LinearLayout[] appRowsHolder={null};
        for(int ci=0;ci<categories.length;ci++){
            final int cc=ci;
            Button cb=button(categories[ci]);
            DesignTypography.setPx(cb,12);
            categoryButtons[ci]=cb;
            if(ci==0)cb.setBackground(DesignTheme.resource(this,R.drawable.card_selected));
            categoryRow.addView(cb,new LinearLayout.LayoutParams(0,dp(44),1));
            cb.setOnClickListener(v->{
                category[0]=cc;
                for(Button b:categoryButtons)b.setBackground(DesignTheme.resource(this,b==v?R.drawable.card_selected:R.drawable.button));
                refreshFloatingChooserApps(allApps,appRowsHolder[0],category,selectedPkg);
            });
        }
        root.addView(categoryRow,new LinearLayout.LayoutParams(-1,dp(50)));

        ScrollView sv=new ScrollView(this);
        LinearLayout appRows=new LinearLayout(this);
        appRows.setOrientation(LinearLayout.VERTICAL);
        sv.addView(appRows,new ScrollView.LayoutParams(-1,-2));
        root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        appRowsHolder[0]=appRows;

        // 选中的 APP 明确显示“图标 + 名称”，和悬浮窗添加逻辑一致。
        TextView selectedInfo=text("未选择 APP",13);
        selectedInfo.setTextColor(Color.LTGRAY);
        selectedInfo.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(selectedInfo,new LinearLayout.LayoutParams(-1,dp(36)));

        refreshFloatingChooserApps(allApps,appRows,category,selectedPkg);

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        Button cancel=button("取消"), save=button("保存");
        actions.addView(cancel,new LinearLayout.LayoutParams(dp(100),dp(50)));
        actions.addView(save,new LinearLayout.LayoutParams(dp(100),dp(50)));
        root.addView(actions,new LinearLayout.LayoutParams(-1,dp(64)));

        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("添加自动启动任务").setView(root).create();
        cancel.setOnClickListener(v->dlg.dismiss());
        save.setOnClickListener(v->{
            String pkg=selectedPkg[0];
            if(pkg==null||pkg.isEmpty()){
                Toast.makeText(this,"请先选择 APP",Toast.LENGTH_SHORT).show();
                return;
            }
            try{
                JSONObject o=new JSONObject();
                o.put("pkg",pkg);
                o.put("name",getAppLabelSafe(pkg));
                o.put("preset",selectedPreset[0]);
                tasks[0].put(o);
                refresh.run();
                dlg.dismiss();
            }catch(Exception e){
                Toast.makeText(this,"保存失败："+e.getMessage(),Toast.LENGTH_LONG).show();
            }
        });
        showFixed900x960(dlg);
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
            row.setBackground(DesignTheme.resource(this,R.drawable.card));

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
        showFixed900x960(dialog);
    }

    /**
     * 控制主界面当前选中的 APP。
     *
     * 车机没有任务管理器，而且普通 Activity 无法直接把“返回键”发送给
     * 已经退到后台的第三方 APP。这里使用已启用的无障碍服务先把选中的 APP
     * 带回前台，再执行系统级返回，因此不会把返回键误发送给其他 APP。
     * 关闭操作在返回后再补一次返回，尽量让 APP 退出当前任务。
     */
    /**
     * 主界面返回/关闭：始终只针对当前选择的 APP。
     * 如果目标 APP 当前不在前台，则先启动它，再由无障碍服务执行返回/关闭。
     * 这样不会把按键发送给其他 APP，同时满足车机没有任务管理器的场景。
     */
    void controlSelectedApp(boolean close){
        if(selectedPackage==null || selectedPackage.isEmpty()){
            Toast.makeText(this,"请先选择 APP",Toast.LENGTH_SHORT).show();
            return;
        }
        final String pkg=selectedPackage;
        final String name=selectedName==null?getAppLabelSafe(pkg):selectedName;

        if(!isAccessibilityServiceEnabled()){
            new AlertDialog.Builder(this)
                    .setTitle("需要无障碍权限")
                    .setMessage("为了让返回/关闭只作用于当前选中的 APP，请先开启本 APP 的无障碍服务。")
                    .setNegativeButton("取消",null)
                    .setPositiveButton("去开启",(d,w)->openAccessibilitySettings())
                    .show();
            return;
        }

        // 已经在前台：直接执行。
        if(AccessibilityServiceBridge.isTargetForeground(pkg)){
            if(AccessibilityServiceBridge.performBackForTarget(pkg,close)){
                info.setText((close?"关闭":"返回")+"当前选中 APP："+name);
            }
            return;
        }

        // 不在前台：先激活选中的 APP，再执行对应操作。
        Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
        if(launch==null){
            Toast.makeText(this,"无法启动所选 APP："+name,Toast.LENGTH_SHORT).show();
            return;
        }
        try{
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                if(AccessibilityServiceBridge.isTargetForeground(pkg)){
                    AccessibilityServiceBridge.performBackForTarget(pkg,close);
                    info.setText((close?"关闭":"返回")+"当前选中 APP："+name);
                }else{
                    Toast.makeText(this,"已激活「"+name+"」，但无障碍服务未识别到前台窗口",Toast.LENGTH_SHORT).show();
                }
            },700);
        }catch(Exception e){
            Toast.makeText(this,"无法激活所选 APP："+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    /** 主界面“主页”：使用系统 HOME，让所有 APP 进入后台，不启动任何 APP。 */
    void controlHome(){
        if(!isAccessibilityServiceEnabled()){
            new AlertDialog.Builder(this)
                    .setTitle("需要无障碍权限")
                    .setMessage("为了让主页快捷键稳定生效，请先开启本 APP 的无障碍服务。")
                    .setNegativeButton("取消",null)
                    .setPositiveButton("去开启",(d,w)->openAccessibilitySettings())
                    .show();
            return;
        }
        AccessibilityServiceBridge.perform(this,2);
        try{
            Intent i=new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }catch(Exception ignored){}
        if(info!=null) info.setText("已返回主页，所有 APP 进入后台");
    }

    boolean isAccessibilityServiceEnabled(){
        try{
            int enabled=Settings.Secure.getInt(getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0);
            if(enabled!=1)return false;
            String services=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return services!=null && services.toLowerCase(Locale.US).contains(getPackageName().toLowerCase(Locale.US));
        }catch(Exception e){return false;}
    }

    void openAccessibilitySettings(){
        try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
        catch(Exception e){Toast.makeText(this,"无法打开无障碍设置",Toast.LENGTH_SHORT).show();}
    }

    JSONObject getSavedAppBounds(String pkg){
        try{
            JSONObject all=new JSONObject(prefs.getString("app_last_bounds","{}"));
            JSONObject o=all.optJSONObject(pkg);
            return o==null?null:o;
        }catch(Exception e){return null;}
    }

    void saveAppBounds(String pkg,int x,int y,int w,int h,int displayId,boolean fullscreen){
        try{
            JSONObject all=new JSONObject(prefs.getString("app_last_bounds","{}"));
            JSONObject o=new JSONObject();
            o.put("x",x); o.put("y",y); o.put("w",w); o.put("h",h);
            o.put("displayId",displayId); o.put("fullscreen",fullscreen);
            all.put(pkg,o);
            prefs.edit().putString("app_last_bounds",all.toString()).apply();
        }catch(Exception ignored){}
    }

    void launchIntentWithBounds(Intent intent,String pkg,int x,int y,int w,int h,int displayId,boolean fullscreen,String name){
        android.view.Display targetDisplay=getWindow().getWindowManager().getDefaultDisplay();
        android.graphics.Point real=getRealScreenSize(targetDisplay);
        int left=Math.max(0,Math.min(x,real.x-1));
        int top=Math.max(0,Math.min(y,real.y-1));
        int right=Math.max(left+1,Math.min(x+w,real.x));
        int bottom=Math.max(top+1,Math.min(y+h,real.y));
        if(fullscreen){left=0;top=0;right=real.x;bottom=real.y;}
        ActivityOptions options=ActivityOptions.makeBasic();
        options.setLaunchBounds(new android.graphics.Rect(left,top,right,bottom));
        if(Build.VERSION.SDK_INT>=26 && targetDisplay!=null){
            try{
                java.lang.reflect.Method m=ActivityOptions.class.getMethod("setLaunchDisplayId",int.class);
                m.invoke(options,targetDisplay.getDisplayId());
            }catch(Exception ignored){}
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        // 不使用 RESET_TASK_IF_NEEDED，避免车机 Launcher 把已有任务重新计算成左上角全屏。
        // MULTIPLE_TASK 让不同 APP 的任务彼此独立，切换第二个 APP 不会清掉第一个任务。
        intent.putExtra("com.acc.acc.target_x",left);
        intent.putExtra("com.acc.acc.target_y",top);
        intent.putExtra("com.acc.acc.target_w",right-left);
        intent.putExtra("com.acc.acc.target_h",bottom-top);
        intent.putExtra("com.acc.acc.target_display_id",targetDisplay==null?displayId:targetDisplay.getDisplayId());
        intent.putExtra("com.acc.acc.fullscreen",fullscreen);
        try{
            startActivity(intent,options.toBundle());
            saveAppBounds(pkg,left,top,right-left,bottom-top,targetDisplay==null?displayId:targetDisplay.getDisplayId(),fullscreen);
            info.setText("启动："+name+"\n已恢复窗口："+(right-left)+" × "+(bottom-top)+"  左 "+left+"  上 "+top);
        }catch(Exception e){
            try{startActivity(intent);}catch(Exception ignored){Toast.makeText(this,"APP 启动失败",Toast.LENGTH_SHORT).show();}
        }
    }

    void launchAppDirect(String pkg,String name){
        Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null){Toast.makeText(this,"无法启动 APP",Toast.LENGTH_SHORT).show();return;}
        JSONObject saved=getSavedAppBounds(pkg);
        if(saved!=null){
            launchIntentWithBounds(intent,pkg,
                    saved.optInt("x",0),saved.optInt("y",0),saved.optInt("w",getRealScreenSize().x),saved.optInt("h",getRealScreenSize().y),
                    saved.optInt("displayId",-1),saved.optBoolean("fullscreen",false),name);
            return;
        }
        info.setText("直接启动："+name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
        if(Build.VERSION.SDK_INT>=21) intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
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

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra("com.acc.acc.target_x",left);
        intent.putExtra("com.acc.acc.target_y",top);
        intent.putExtra("com.acc.acc.target_w",right-left);
        intent.putExtra("com.acc.acc.target_h",bottom-top);
                intent.putExtra("com.acc.acc.target_display_id",targetDisplay==null?-1:targetDisplay.getDisplayId());
        intent.putExtra("com.acc.acc.fullscreen",p.mode==6);

        info.setText("启动："+selectedName+"\n"+p.name+"  左间距 "+left+"  上间距 "+top+"  "+(right-left)+" × "+(bottom-top)+"  "+(p.mode==6?"全屏":"模式"+p.mode));

        try{
            startActivity(intent,options.toBundle());
            saveAppBounds(selectedPackage,left,top,right-left,bottom-top,targetDisplay==null?-1:targetDisplay.getDisplayId(),p.mode==6);
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
