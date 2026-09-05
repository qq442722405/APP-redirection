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


    // ===== 网页设计器参数引擎 =====
    // 所有界面坐标、尺寸、字号均从 assets 中的 JSON 读取，单位直接按设计稿 px 使用。
    static class DesignElem {
        String type, text, content, name;
        int top,left,width,height,fontSize;
        DesignElem(JSONObject o){
            type=o.optString("type","");
            text=o.optString("text","");
            content=o.optString("content","");
            name=o.optString("name","");
            top=o.optInt("top",0); left=o.optInt("left",0);
            width=o.optInt("width",0); height=o.optInt("height",0);
            fontSize=o.optInt("fontSize",16);
        }
        String keyText(){
            if(!text.isEmpty()) return text;
            if(!content.isEmpty()) return content;
            return name;
        }
    }
    static class DesignSpec {
        int dpi=160;
        ArrayList<DesignElem> elements=new ArrayList<>();
        DesignElem find(String type,String label,int occurrence){
            int n=0;
            for(DesignElem e:elements){
                if(!type.equals(e.type)) continue;
                if(label!=null && !label.isEmpty() && !label.equals(e.keyText())) continue;
                if(n++==occurrence) return e;
            }
            return null;
        }
        DesignElem findAny(String type,String... labels){
            for(String l:labels){
                DesignElem e=find(type,l,0);
                if(e!=null)return e;
            }
            return null;
        }
    }
    DesignSpec loadDesign(String file){
        DesignSpec d=new DesignSpec();
        try{
            InputStream in=getAssets().open(file);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buf=new byte[4096]; int len;
            while((len=in.read(buf))>0) out.write(buf,0,len);
            in.close();
            JSONObject root=new JSONObject(out.toString("UTF-8"));
            d.dpi=root.optInt("globalDpi",160);
            JSONArray a=root.optJSONArray("elements");
            if(a!=null) for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i); if(o!=null)d.elements.add(new DesignElem(o));
            }
        }catch(Exception ignored){}
        return d;
    }
    int designPx(int v){ return Math.max(0,v); }
    void placeDesigned(View v, DesignElem e){
        if(v==null||e==null)return;
        v.setTag(e);
        if(v instanceof TextView){
            TextView tv=(TextView)v;
            if(e.fontSize>0) tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,e.fontSize*fontScale());
        }
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(designPx(e.width),designPx(e.height));
        lp.leftMargin=designPx(e.left); lp.topMargin=designPx(e.top);
        if(e.width<=0)lp.width=FrameLayout.LayoutParams.WRAP_CONTENT;
        if(e.height<=0)lp.height=FrameLayout.LayoutParams.WRAP_CONTENT;
        v.setLayoutParams(lp);
    }
    TextView designedText(DesignSpec d,String label,int occ){
        DesignElem e=d.find("text",label,occ);
        TextView v=text(label,e==null?16:e.fontSize);
        if(e!=null)placeDesigned(v,e);
        return v;
    }
    Button designedButton(DesignSpec d,String label,int occ){
        DesignElem e=d.find("button",label,occ);
        Button b=button(label);
        if(e!=null)placeDesigned(b,e);
        return b;
    }
    EditText designedEdit(DesignSpec d,String label,int occ,String value){
        DesignElem e=d.find("button",label,occ);
        EditText x=textField("",value);
        x.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(e==null?16:e.fontSize)*fontScale());
        if(e!=null)placeDesigned(x,e);
        return x;
    }
    FrameLayout designedRoot(DesignSpec d){
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(0xEE202020);
        DesignElem bg=d.find("window",null,0);
        int w=bg!=null&&bg.width>0?bg.width:1000, h=bg!=null&&bg.height>0?bg.height:700;
        root.setTag(new int[]{w,h});
        return root;
    }
    void showDesignedDialog(AlertDialog dialog, int width, int height){
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null){
            WindowManager.LayoutParams lp=w.getAttributes();
            lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
            lp.x=0; lp.y=0; lp.width=width; lp.height=height;
            w.setAttributes(lp);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        prefs=getSharedPreferences(PREF,0);
        // 新安装默认值：这些值来自用户确认过的配置，但不会读取/加载 JSON 文件。
        // 这样默认值与配置文件完全解耦，避免旧/无效 APP 包名导致启动闪退。
        SharedPreferences.Editor defaults=prefs.edit();
        if(!prefs.contains("font_scale") || prefs.getFloat("font_scale",1.0f) <= 0.2001f) defaults.putFloat("font_scale",1.20f);
        if(!prefs.contains("main_font_scale") || prefs.getFloat("main_font_scale",1.0f) <= 0.2001f) defaults.putFloat("main_font_scale",1.00f);
        if(!prefs.contains("ui_scale")) defaults.putFloat("ui_scale",1.30f);
        if(!prefs.contains("main_app_columns")) defaults.putInt("main_app_columns",10);
        if(!prefs.contains("main_top_blank")) defaults.putInt("main_top_blank",80);
        if(!prefs.contains("boot_delay_seconds")) defaults.putInt("boot_delay_seconds",0);
        if(!prefs.contains("hide_main_background_acc")) defaults.putBoolean("hide_main_background_acc",true);
        // 默认窗口预设直接写入 SharedPreferences；不保存任何默认 APP 包名。
        if(!prefs.contains(PRESETS)) defaults.putString(PRESETS, defaultPresetJson());
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
                String pn=o.optString("name","");
                int pc=o.has("category") ? o.optInt("category",1) : inferPresetCategory(pn);
                presets.add(new Preset(
                        pn,o.optInt("x",0),o.optInt("y",0),
                        o.optInt("w",0),o.optInt("h",0),o.optInt("displayId",-1),o.optInt("mode",1),pc
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
                o.put("w",p.w); o.put("h",p.h); o.put("displayId",p.displayId); o.put("mode",p.mode); o.put("category",p.category);
                a.put(o);
            }
        }catch(Exception ignored){}
        prefs.edit().putString(PRESETS,a.toString()).apply();
    }

    void buildUI(){
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        mainUiContext=true;

        DesignSpec d=loadDesign("主界面.json");
        FrameLayout frame=new FrameLayout(this);
        mainFrame=frame;
        frame.setBackgroundColor(Color.TRANSPARENT);

        // 设计稿本身即为主界面布局基准，不再用 LinearLayout 自动排版。
        DesignElem bg=d.find("window","主控制卡片",0);
        if(bg!=null){
            View card=new View(this);
            card.setBackgroundColor(Color.TRANSPARENT);
            placeDesigned(card,bg);
            frame.addView(card);
        }

        DesignElem top=d.find("window","离上80",0);
        if(top!=null){
            View v=new View(this); v.setBackgroundColor(Color.TRANSPARENT);
            placeDesigned(v,top); frame.addView(v);
        }

        TextView title=designedText(d,"窗口预设",0);
        title.setTypeface(null,android.graphics.Typeface.BOLD); frame.addView(title);

        TextView addPreset=designedText(d,"+",0);
        // JSON 中 + 是 button，不是 text；这里按 button 参数重新建立。
        frame.removeView(addPreset);
        Button presetPlus=designedButton(d,"+",0);
        presetPlus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,35*mainFontScale());
        presetPlus.setContentDescription("新建窗口预设");
        presetPlus.setOnClickListener(v->editPreset(-1));
        frame.addView(presetPlus);

        presetCategoryButtons=new Button[3];
        String[] cats={"左","中","右"};
        for(int i=0;i<3;i++){
            Button b=designedButton(d,cats[i],0);
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,35*mainFontScale());
            final int ci=i;
            b.setBackgroundResource(ci==presetCategoryFilter?R.drawable.card_selected:R.drawable.button);
            b.setOnClickListener(v->{presetCategoryFilter=ci; refresh();});
            frame.addView(b); presetCategoryButtons[i]=b;
        }

        Button floatingPick=designedButton(d,"悬浮窗选位",0);
        floatingPick.setOnClickListener(v->showFloatingPresetPicker());
        frame.addView(floatingPick);

        // 预设卡片区域严格对应设计稿 y=210 的 200×200 区域。
        presetRow=new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView ps=new HorizontalScrollView(this);
        ps.setHorizontalScrollBarEnabled(false);
        ps.addView(presetRow,new HorizontalScrollView.LayoutParams(-2,200));
        FrameLayout.LayoutParams pslp=new FrameLayout.LayoutParams(-1,200);
        pslp.leftMargin=30; pslp.topMargin=210;
        frame.addView(ps,pslp);

        Button addApp=designedButton(d,"+",1);
        addApp.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,35*mainFontScale());
        addApp.setContentDescription("添加 APP");
        addApp.setOnClickListener(v->chooseApp());
        frame.addView(addApp);

        TextView appTitle=designedText(d,"APP",0);
        appTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        frame.addView(appTitle);

        // APP 卡片区域对应设计稿 y=545，卡片 200×200。
        appGrid=new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.VERTICAL);
        appGrid.setGravity(Gravity.LEFT);
        HorizontalScrollView as=new HorizontalScrollView(this);
        as.setHorizontalScrollBarEnabled(false);
        as.addView(appGrid,new HorizontalScrollView.LayoutParams(-2,-2));
        FrameLayout.LayoutParams asl=new FrameLayout.LayoutParams(-1,420);
        asl.leftMargin=250; asl.topMargin=545;
        frame.addView(as,asl);

        String[] footer={"返回","关闭","首页","设置"};
        for(int i=0;i<footer.length;i++){
            Button b=designedButton(d,footer[i],0);
            if("返回".equals(footer[i])) b.setOnClickListener(v->controlSelectedApp(false));
            else if("关闭".equals(footer[i])) b.setOnClickListener(v->controlSelectedApp(true));
            else if("首页".equals(footer[i])) b.setOnClickListener(v->controlHome());
            else b.setOnClickListener(v->showSettingsMenu());
            frame.addView(b);
        }

        TextView screenInfo=designedText(d,"分辨率   DPI   报名  版本  versioncode",0);
        screenInfo.setText("");
        updateScreenInfo(screenInfo);
        frame.addView(screenInfo);

        setContentView(frame);
        refresh();
        mainUiContext=false;
    }

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

    void refresh(){
        mainUiContext=true;
        if(presetRow!=null){
            presetRow.removeAllViews();
            boolean has=false;
            for(int i=0;i<presets.size();i++){
                final int index=i; Preset p=presets.get(i);
                if(p.category!=presetCategoryFilter)continue;
                has=true;
                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER);
                card.setPadding(8,4,8,4); card.setBackgroundResource(R.drawable.card);
                TextView t=mainText(p.name,30); t.setGravity(Gravity.CENTER); t.setMaxLines(1);
                TextView sz=mainText(p.w+" × "+p.h,20); sz.setGravity(Gravity.CENTER);
                TextView pos=mainText("上 "+p.y+"    左 "+p.x,18); pos.setGravity(Gravity.CENTER);
                card.addView(t,new LinearLayout.LayoutParams(-1,70));
                card.addView(sz,new LinearLayout.LayoutParams(-1,50));
                card.addView(pos,new LinearLayout.LayoutParams(-1,45));
                card.setOnClickListener(v->{if(selectedPackage!=null)launchApp(p);else editPreset(index);});
                setMainItemLongClick(card,0,index);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(200,200);
                lp.setMargins(4,0,4,0); presetRow.addView(card,lp);
                bringMovableItemToFront(card);
            }
            if(!has){
                TextView empty=mainText(presets.isEmpty()?"点击“+”新建窗口预设":"当前分类暂无窗口预设",24);
                empty.setGravity(Gravity.CENTER);
                presetRow.addView(empty,new LinearLayout.LayoutParams(200,200));
            }
        }

        if(appGrid!=null){
            appGrid.removeAllViews();
            int columns=Math.max(1,prefs.getInt("main_app_columns",7));
            if(apps.isEmpty()){
                TextView e=mainText("点击“+”添加 APP",28);e.setTextColor(Color.GRAY);e.setGravity(Gravity.CENTER);
                appGrid.addView(e,new LinearLayout.LayoutParams(200,200));
            }else{
                PackageManager pm=getPackageManager(); LinearLayout row=null; int inRow=0;
                for(int i=0;i<apps.size();i++){
                    final int idx=i; AppItem item=apps.get(i);
                    if(inRow==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);appGrid.addView(row,new LinearLayout.LayoutParams(-1,200));}
                    LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);
                    tile.setPadding(8,8,8,8);
                    tile.setBackgroundResource(item.pkg.equals(selectedPackage)?R.drawable.card_selected:R.drawable.card);
                    ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    try{icon.setImageDrawable(pm.getApplicationIcon(item.pkg));}catch(Exception ignored){}
                    tile.addView(icon,new LinearLayout.LayoutParams(120,120));
                    TextView name=mainText(item.name,25);name.setGravity(Gravity.CENTER);name.setMaxLines(2);
                    tile.addView(name,new LinearLayout.LayoutParams(-1,65));
                    tile.setOnClickListener(v->{
                        long now=System.currentTimeMillis();
                        boolean dbl=(lastMainAppTapIndex==idx&&now-lastMainAppTapTime<=420);
                        lastMainAppTapIndex=idx;lastMainAppTapTime=now;
                        selectedPackage=item.pkg;selectedName=item.name;
                        prefs.edit().putString("selected_control_package",item.pkg).apply();
                        if(dbl)launchAppDirect(item.pkg,item.name);else refresh();
                    });
                    setMainItemLongClick(tile,1,idx);
                    row.addView(tile,new LinearLayout.LayoutParams(200,200));
                    inRow++; if(inRow>=columns)inRow=0;
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
            w.setBackgroundDrawable(new ColorDrawable(Color.rgb(24,24,24)));
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
                b.setTextSize(14*fontScale());
                b.setBackgroundResource(R.drawable.button);
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

    void showSettingsMenu(){
        DesignSpec d=loadDesign("设置.json");
        FrameLayout root=designedRoot(d);
        TextView title=designedText(d,"设置",0); root.addView(title);
        TextView bootLabel=designedText(d,"开机启动",0); root.addView(bootLabel);
        Switch boot=new Switch(this); boot.setChecked(prefs.getBoolean("app_boot_enabled",false));
        placeDesigned(boot,d.find("button","开关",0)); root.addView(boot);

        TextView taskLabel=designedText(d,"开机任务",0); root.addView(taskLabel);
        Switch task=new Switch(this); task.setChecked(prefs.getBoolean("auto_start_enabled",false));
        placeDesigned(task,d.find("button","开关",1)); root.addView(task);

        Button interfaceBtn=designedButton(d,"界面选项",0); interfaceBtn.setOnClickListener(v->showInterfaceOptionsDialog()); root.addView(interfaceBtn);
        Button autoBtn=designedButton(d,"自动任务",0); autoBtn.setOnClickListener(v->showAutoStartEditor()); root.addView(autoBtn);
        Button permBtn=designedButton(d,"权限与诊断",0); permBtn.setOnClickListener(v->showScreenDiagnostics()); root.addView(permBtn);
        Button exportBtn=designedButton(d,"导出配置",0); exportBtn.setOnClickListener(v->exportConfig()); root.addView(exportBtn);
        Button importBtn=designedButton(d,"导入配置",0); importBtn.setOnClickListener(v->importConfig()); root.addView(importBtn);
        Button floatBtn=designedButton(d,"悬浮窗口设置",0); floatBtn.setOnClickListener(v->showFloatingWindowSettingsDialog()); root.addView(floatBtn);

        Button cancel=designedButton(d,"取消",0); Button save=designedButton(d,"保存",0);
        root.addView(cancel); root.addView(save);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(root).create();
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{prefs.edit().putBoolean("app_boot_enabled",boot.isChecked()).putBoolean("auto_start_enabled",task.isChecked()).apply();Toast.makeText(this,"设置已保存",Toast.LENGTH_SHORT).show();dialog.dismiss();});
        showDesignedDialog(dialog,1000,700);
    }

    void showFloatingWindowSettingsDialog(){
        DesignSpec d=loadDesign("悬浮窗口设置.json");
        FrameLayout root=designedRoot(d);
        root.addView(designedText(d,"悬浮窗口设置",0));
        root.addView(designedText(d,"悬浮窗口开关",0));
        root.addView(designedText(d,"位置锁定",0));
        root.addView(designedText(d,"排列方向",0));
        root.addView(designedText(d,"透明度",0));
        root.addView(designedText(d,"按钮图标大小",0));
        root.addView(designedText(d,"按钮图标间隔",0));
        root.addView(designedText(d,"单图标模式",0));
        root.addView(designedText(d,"单图标形状",0));
        root.addView(designedText(d,"点击",0));root.addView(designedText(d,"双击",0));root.addView(designedText(d,"长按",0));
        root.addView(designedText(d,"左滑",0));root.addView(designedText(d,"右滑",0));root.addView(designedText(d,"上滑",0));root.addView(designedText(d,"下滑",0));

        Switch enable=new Switch(this);placeDesigned(enable,d.find("button","开关",0));enable.setChecked(prefs.getBoolean("floating_enabled",false));root.addView(enable);
        Switch lock=new Switch(this);placeDesigned(lock,d.find("button","开关",1));lock.setChecked(prefs.getBoolean("floating_position_locked",false));root.addView(lock);
        Button dir=designedButton(d,"横向",0);dir.setText(prefs.getBoolean("floating_vertical",false)?"竖向":"横向");root.addView(dir);
        EditText opacity=designedEdit(d,"数值",0,String.valueOf(prefs.getInt("floating_background_opacity",80)));root.addView(opacity);
        EditText icon=designedEdit(d,"数值",1,String.valueOf(prefs.getInt("floating_icon_size_px",44)));root.addView(icon);
        EditText spacing=designedEdit(d,"数值",2,String.valueOf(prefs.getInt("floating_button_spacing_px",6)));root.addView(spacing);
        Switch single=new Switch(this);placeDesigned(single,d.find("button","开关",2));single.setChecked(prefs.getBoolean("floating_single_icon_mode",false));root.addView(single);
        Button shape=designedButton(d,"圆形",0);shape.setText("circle".equals(prefs.getString("floating_single_icon_shape","rounded"))?"圆形":"圆角正方形");root.addView(shape);

        dir.setOnClickListener(v->{boolean x=!prefs.getBoolean("floating_vertical",false);dir.setText(x?"竖向":"横向");});
        shape.setOnClickListener(v->{boolean x=!"circle".equals(prefs.getString("floating_single_icon_shape","rounded"));shape.setText(x?"圆形":"圆角正方形");});

        String[] keys={"tap","double","long","left","right","up","down"};
        String[] names={"点击","双击","长按","左滑","右滑","上滑","下滑"};
        int[] occ={0,1,2,3,4,5,6};
        for(int i=0;i<keys.length;i++){
            Button b=designedButton(d,"无操作",occ[i]);
            b.setText(getGestureLabel(prefs.getString("floating_gesture_"+keys[i],"none")));
            final String key=keys[i], name=names[i];
            b.setOnClickListener(v->showGestureChooser(key,name,b));
            root.addView(b);
        }

        Button add=designedButton(d,"添加按钮",0);add.setOnClickListener(v->showFloatingAppChooser());root.addView(add);
        Button cancel=designedButton(d,"取消",0),save=designedButton(d,"保存",0);root.addView(cancel);root.addView(save);

        AlertDialog dialog=new AlertDialog.Builder(this).setView(root).create();
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            int op=number(opacity,80),ic=number(icon,44),sp=number(spacing,6);
            if(op<0||op>100||ic<20||ic>200||sp<0||sp>80){Toast.makeText(this,"参数范围错误",Toast.LENGTH_SHORT).show();return;}
            boolean vertical="竖向".equals(dir.getText().toString());
            String shapeVal="圆形".equals(shape.getText().toString())?"circle":"rounded";
            boolean old=prefs.getBoolean("floating_enabled",false);
            prefs.edit().putBoolean("floating_enabled",enable.isChecked()).putBoolean("floating_position_locked",lock.isChecked())
                .putBoolean("floating_vertical",vertical).putInt("floating_background_opacity",op)
                .putInt("floating_icon_size_px",ic).putInt("floating_button_spacing_px",sp)
                .putBoolean("floating_single_icon_mode",single.isChecked()).putString("floating_single_icon_shape",shapeVal).apply();
            if(enable.isChecked()){if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){Toast.makeText(this,"请先开启悬浮窗权限",Toast.LENGTH_LONG).show();openOverlaySettings();return;}stopFloatingService();startFloatingService();}
            else if(old)stopFloatingService();
            Toast.makeText(this,"悬浮窗口设置已保存",Toast.LENGTH_SHORT).show();
        });
        showDesignedDialog(dialog,1000,700);
    }

    void addCurrentButtonChip(LinearLayout row,String label,Runnable deleteAction){
        Button chip=button(label); chip.setTextSize(10);
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

    /** 添加悬浮框按钮/APP：采用添加APP.json的同一套位置尺寸字号。 */
    void showFloatingAppChooser(){
        DesignSpec d=loadDesign("添加APP.json");
        FrameLayout root=designedRoot(d);
        root.addView(designedText(d,"添加",0));root.addView(designedText(d,"搜索",0));root.addView(designedText(d,"分类",0));root.addView(designedText(d,"窗口预设",0));
        EditText search=designedEdit(d,"名称填框",0,"");search.setHint("搜索 APP");root.addView(search);
        final int[] cat={0};final int[] selectedPreset={-1};final String[] selected={""};final Runnable[] rr={null};
        Button[] cats=new Button[4];String[] cn={"用户","系统","全部","按钮"};
        for(int i=0;i<4;i++){final int ci=i;cats[i]=designedButton(d,cn[i],0);cats[i].setOnClickListener(v->{cat[0]=ci;for(int j=0;j<4;j++)cats[j].setBackgroundResource(j==ci?R.drawable.card_selected:R.drawable.button);if(rr[0]!=null)rr[0].run();});root.addView(cats[i]);}
        cats[0].setBackgroundResource(R.drawable.card_selected);
        Button preset=designedButton(d,"预设窗口下拉选项",0);root.addView(preset);
        preset.setOnClickListener(v->{String[] ns=new String[presets.size()+1];ns[0]="默认";for(int i=0;i<presets.size();i++)ns[i+1]=presets.get(i).name;new AlertDialog.Builder(this).setTitle("选择窗口预设").setItems(ns,(di,w)->{selectedPreset[0]=w-1;preset.setText(ns[w]);}).show();});
        FrameLayout lf=new FrameLayout(this);FrameLayout.LayoutParams lfp=new FrameLayout.LayoutParams(840,320);lfp.leftMargin=54;lfp.topMargin=270;root.addView(lf,lfp);
        ScrollView sv=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);sv.addView(rows,new ScrollView.LayoutParams(-1,-2));lf.addView(sv,new FrameLayout.LayoutParams(-1,-1));
        PackageManager pm=getPackageManager();ArrayList<ApplicationInfo> all=new ArrayList<>();
        try{for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){if(!ai.packageName.equals(getPackageName())&&pm.getLaunchIntentForPackage(ai.packageName)!=null)all.add(ai);}}catch(Exception ignored){}
        Collections.sort(all,(a,b)->getAppLabelSafe(a.packageName).compareToIgnoreCase(getAppLabelSafe(b.packageName)));
        rr[0]=()->{
            rows.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            if(cat[0]==3){String[] bn={"返回","主页","菜单","关闭"};for(String name:bn){Button b=button(name);b.setTextSize(18);final String id=name;b.setOnClickListener(v->{selected[0]="button:"+("返回".equals(id)?"back":"主页".equals(id)?"home":"菜单".equals(id)?"menu":"close");});rows.addView(b,new LinearLayout.LayoutParams(-1,70));}return;}
            LinearLayout row=null;int in=0;
            for(ApplicationInfo ai:all){boolean sys=(ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0||(ai.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;if(cat[0]==0&&sys)continue;if(cat[0]==1&&!sys)continue;String name=getAppLabelSafe(ai.packageName);if(!q.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(q))continue;
                if(in==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);rows.addView(row,new LinearLayout.LayoutParams(-1,160));}
                Button b=button(name);b.setTextSize(16);b.setGravity(Gravity.CENTER);try{b.setCompoundDrawablesWithIntrinsicBounds(null,pm.getApplicationIcon(ai),null,null);}catch(Exception ignored){}
                final String pp=ai.packageName;b.setOnClickListener(v->{selected[0]=pp;if(rr[0]!=null)rr[0].run();});row.addView(b,new LinearLayout.LayoutParams(150,150));in++;if(in>=5)in=0;
            }
        };
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){rr[0].run();}public void afterTextChanged(android.text.Editable e){}});
        Button copy=designedButton(d,"复制",0),paste=designedButton(d,"粘贴",0),cancel=designedButton(d,"取消",0),save=designedButton(d,"保存",0);
        root.addView(copy);root.addView(paste);root.addView(cancel);root.addView(save);
        AlertDialog dlg=new AlertDialog.Builder(this).setView(root).create();cancel.setOnClickListener(v->dlg.dismiss());
        save.setOnClickListener(v->{String pkg=selected[0];if(pkg.isEmpty()){Toast.makeText(this,"请先选择 APP 或按钮",Toast.LENGTH_SHORT).show();return;}try{
            if(pkg.startsWith("button:")){String id=pkg.substring(7);SharedPreferences.Editor ed=prefs.edit();if("back".equals(id))ed.putBoolean("floating_back",true);else if("home".equals(id))ed.putBoolean("floating_home",true);else if("menu".equals(id))ed.putBoolean("floating_menu",true);else ed.putBoolean("floating_close",true);ed.apply();}
            else{JSONArray a=new JSONArray(prefs.getString("floating_apps","[]"));boolean exists=false;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&pkg.equals(o.optString("pkg",""))){exists=true;break;}}if(!exists)a.put(new JSONObject().put("pkg",pkg).put("name",getAppLabelSafe(pkg)));prefs.edit().putString("floating_apps",a.toString()).putInt("floating_preset_"+pkg,selectedPreset[0]).apply();}
            restartFloatingServiceSafe();dlg.dismiss();Toast.makeText(this,"已添加",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"保存失败："+e.getMessage(),Toast.LENGTH_SHORT).show();}});
        showDesignedDialog(dlg,1000,700);rr[0].run();
    }

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
            if(value.equals(selectedPkg[0])) tile.setBackgroundResource(R.drawable.card_selected);
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
            if(pkg.equals(selectedPkg[0]))tile.setBackgroundResource(R.drawable.card_selected);
            tile.setOnClickListener(v->{
                selectedPkg[0]=pkg;
                refreshFloatingChooserApps(allApps,rows,category,selectedPkg);
            });
            row.addView(tile,new LinearLayout.LayoutParams(0,dp(108),1));
            col=(col+1)%6;
        }
    }

    void showGestureChooser(String key,String title,Button target){
        DesignSpec d=loadDesign("添加APP.json");
        FrameLayout root=designedRoot(d);
        root.addView(designedText(d,"添加",0));root.addView(designedText(d,"搜索",0));root.addView(designedText(d,"分类",0));
        EditText search=designedEdit(d,"名称填框",0,"");search.setHint("选择手势动作");root.addView(search);
        String[] actions={"无操作","返回按钮","首页按钮","菜单按钮"};
        FrameLayout listFrame=new FrameLayout(this);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(840,320);lp.leftMargin=54;lp.topMargin=215;root.addView(listFrame,lp);
        ScrollView sv=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list,new ScrollView.LayoutParams(-1,-2));listFrame.addView(sv,new FrameLayout.LayoutParams(-1,-1));
        final AlertDialog[] ref={null};
        for(String a:actions){Button b=button(a);b.setTextSize(18);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);list.addView(b,new LinearLayout.LayoutParams(-1,65));b.setOnClickListener(v->{String val="none";if("返回按钮".equals(a))val="back";else if("首页按钮".equals(a))val="home";else if("菜单按钮".equals(a))val="menu";prefs.edit().putString("floating_gesture_"+key,val).apply();target.setText(a);if(ref[0]!=null)ref[0].dismiss();});}
        try{JSONArray a=new JSONArray(prefs.getString("floating_apps","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String pkg=o.optString("pkg","");if(pkg.isEmpty())continue;String n=getAppLabelSafe(pkg);Button b=button(n);b.setTextSize(18);list.addView(b,new LinearLayout.LayoutParams(-1,65));b.setOnClickListener(v->{prefs.edit().putString("floating_gesture_"+key,"app:"+pkg).apply();target.setText(n);if(ref[0]!=null)ref[0].dismiss();});}}catch(Exception ignored){}
        Button cancel=designedButton(d,"取消",0),save=designedButton(d,"保存",0);root.addView(cancel);root.addView(save);
        AlertDialog dlg=new AlertDialog.Builder(this).setView(root).create();ref[0]=dlg;cancel.setOnClickListener(v->dlg.dismiss());save.setOnClickListener(v->dlg.dismiss());
        showDesignedDialog(dlg,1000,700);
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
        DesignSpec d=loadDesign("界面选项.json");
        FrameLayout root=designedRoot(d);
        root.addView(designedText(d,"界面选项",0));
        root.addView(designedText(d,"延迟启动",0));
        root.addView(designedText(d,"字体大小",0));
        root.addView(designedText(d,"界面大小",0));
        root.addView(designedText(d,"主界面每排数量",0));
        root.addView(designedText(d,"主界面壁纸",0));
        root.addView(designedText(d,"秒",0));
        root.addView(designedText(d,"%",0));
        root.addView(designedText(d,"%",1));
        root.addView(designedText(d,"个",0));
        root.addView(designedText(d,"路径",0));

        EditText delay=designedEdit(d,"开关",0,String.valueOf(prefs.getInt("boot_delay_seconds",0)));
        EditText font=designedEdit(d,"开关",1,String.valueOf(Math.round(mainFontScale()*100)));
        EditText size=designedEdit(d,"开关",2,String.valueOf(Math.round(uiScale()*100)));
        EditText cols=designedEdit(d,"开关",3,String.valueOf(prefs.getInt("main_app_columns",7)));
        EditText wall=designedEdit(d,"开关",4,prefs.getString("main_wallpaper_path",""));
        delay.setInputType(2);font.setInputType(2);size.setInputType(2);cols.setInputType(2);
        root.addView(delay);root.addView(font);root.addView(size);root.addView(cols);root.addView(wall);

        Button cancel=designedButton(d,"取消",0),save=designedButton(d,"保存",0);
        root.addView(cancel);root.addView(save);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(root).create();
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            try{
                int de=Integer.parseInt(delay.getText().toString().trim());
                int fs=Integer.parseInt(font.getText().toString().trim());
                int us=Integer.parseInt(size.getText().toString().trim());
                int c=Integer.parseInt(cols.getText().toString().trim());
                if(de<0||de>3600||fs<20||fs>300||us<50||us>300||c<1||c>20)throw new Exception();
                prefs.edit().putInt("boot_delay_seconds",de).putFloat("main_font_scale",fs/100f)
                    .putFloat("ui_scale",us/100f).putInt("main_app_columns",c)
                    .putString("main_wallpaper_path",wall.getText().toString()).apply();
                Toast.makeText(this,"界面选项已保存",Toast.LENGTH_SHORT).show();
                dialog.dismiss();buildUI();
            }catch(Exception e){Toast.makeText(this,"请输入有效数值",Toast.LENGTH_SHORT).show();}
        });
        showDesignedDialog(dialog,1000,700);
    }

    /** 添加 APP：严格采用添加APP.json的坐标、尺寸和字号。 */
    void chooseApp(){
        DesignSpec d=loadDesign("添加APP.json");
        FrameLayout root=designedRoot(d);
        root.addView(designedText(d,"添加",0));
        root.addView(designedText(d,"搜索",0));
        root.addView(designedText(d,"分类",0));
        root.addView(designedText(d,"窗口预设",0));

        EditText search=designedEdit(d,"名称填框",0,"");
        search.setHint("搜索 APP");
        root.addView(search);

        String[] cats={"用户","系统","全部"};
        final int[] cat={0};
        final Runnable[] refreshHolder={null};
        Button[] catBtns=new Button[3];
        for(int i=0;i<3;i++){
            final int ci=i;
            catBtns[i]=designedButton(d,cats[i],0);
            catBtns[i].setOnClickListener(v->{cat[0]=ci;for(int j=0;j<3;j++)catBtns[j].setBackgroundResource(j==ci?R.drawable.card_selected:R.drawable.button);if(refreshHolder[0]!=null)refreshHolder[0].run();});
            root.addView(catBtns[i]);
        }

        Button preset=designedButton(d,"预设窗口下拉选项",0);
        root.addView(preset);
        final int[] selectedPreset={-1};
        preset.setOnClickListener(v->{
            String[] names=new String[presets.size()+1];names[0]="默认";
            for(int i=0;i<presets.size();i++)names[i+1]=presets.get(i).name;
            new AlertDialog.Builder(this).setTitle("选择窗口预设").setItems(names,(di,which)->{
                selectedPreset[0]=which-1;preset.setText(which==0?"默认":names[which]);
            }).show();
        });

        FrameLayout listFrame=new FrameLayout(this);
        FrameLayout.LayoutParams lfp=new FrameLayout.LayoutParams(840,320);lfp.leftMargin=54;lfp.topMargin=270;
        root.addView(listFrame,lfp);
        ScrollView scroll=new ScrollView(this);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows,new ScrollView.LayoutParams(-1,-2));listFrame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        ArrayList<ApplicationInfo> all=new ArrayList<>();
        PackageManager pm=getPackageManager();
        try{for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(ai.packageName.equals(getPackageName()))continue;
            if(pm.getLaunchIntentForPackage(ai.packageName)!=null)all.add(ai);
        }}catch(Exception ignored){}
        Collections.sort(all,(a,b)->getAppLabelSafe(a.packageName).compareToIgnoreCase(getAppLabelSafe(b.packageName)));

        final String[] selectedPkg={""};
        Runnable refresh=()->{
            rows.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            LinearLayout row=null;int in=0,count=0;
            for(ApplicationInfo ai:all){
                boolean sys=(ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0||(ai.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;
                if(cat[0]==0&&sys)continue;if(cat[0]==1&&!sys)continue;
                String name=getAppLabelSafe(ai.packageName);
                if(!q.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(q)&&!ai.packageName.toLowerCase(Locale.ROOT).contains(q))continue;
                if(in==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);rows.addView(row,new LinearLayout.LayoutParams(-1,160));}
                Button tile=new Button(this);tile.setAllCaps(false);tile.setText(name);tile.setTextColor(Color.WHITE);tile.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16*fontScale());
                try{tile.setCompoundDrawablesWithIntrinsicBounds(null,pm.getApplicationIcon(ai),null,null);}catch(Exception ignored){}
                tile.setGravity(Gravity.CENTER);tile.setBackgroundResource(ai.packageName.equals(selectedPkg[0])?R.drawable.card_selected:R.drawable.card);
                final String pkg=ai.packageName;
                tile.setOnClickListener(v->{selectedPkg[0]=pkg;if(refreshHolder[0]!=null)refreshHolder[0].run();});
                LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(150,150);tlp.setMargins(2,5,2,5);row.addView(tile,tlp);
                in++;count++;if(in>=5)in=0;
            }
            if(count==0){TextView e=text("没有找到可显示的 APP",16);e.setGravity(Gravity.CENTER);rows.addView(e,new LinearLayout.LayoutParams(-1,150));}
        };
        refreshHolder[0]=refresh;
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh.run();}public void afterTextChanged(android.text.Editable e){}});
        catBtns[0].setBackgroundResource(R.drawable.card_selected);

        Button copy=designedButton(d,"复制",0),paste=designedButton(d,"粘贴",0),cancel=designedButton(d,"取消",0),save=designedButton(d,"保存",0),gesture=designedButton(d,"无操作",0);
        root.addView(copy);root.addView(paste);root.addView(cancel);root.addView(save);root.addView(gesture);
        copy.setOnClickListener(v->Toast.makeText(this,"已复制当前 APP 选择",Toast.LENGTH_SHORT).show());
        paste.setOnClickListener(v->Toast.makeText(this,"请从 APP 列表重新选择",Toast.LENGTH_SHORT).show());
        AlertDialog dialog=new AlertDialog.Builder(this).setView(root).create();
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            String pkg=selectedPkg[0];if(pkg.isEmpty()){Toast.makeText(this,"请先选择 APP",Toast.LENGTH_SHORT).show();return;}
            String name=getAppLabelSafe(pkg);boolean exists=false;for(AppItem a:apps)if(a.pkg.equals(pkg))exists=true;
            if(!exists){apps.add(new AppItem(pkg,name));saveApps();}
            selectedPackage=pkg;selectedName=name;prefs.edit().putString("selected_control_package",pkg).apply();
            refresh();dialog.dismiss();
        });
        showDesignedDialog(dialog,1000,700);refresh.run();
    }

    int rowCount(ViewGroup g){return g==null?0:g.getChildCount();}

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
            if(modeButtons!=null) for(int i=0;i<modeButtons.length;i++) modeButtons[i].setBackgroundResource(i==m-1?R.drawable.card_selected:R.drawable.button);
            int c=Math.max(0,Math.min(2,o.optInt("category",1))); categoryHolder[0]=c;
            if(categoryButtons!=null) for(int i=0;i<categoryButtons.length;i++) categoryButtons[i].setBackgroundResource(i==c?R.drawable.card_selected:R.drawable.button);
            Toast.makeText(this,"面板参数已粘贴",Toast.LENGTH_SHORT).show();
            return true;
        }catch(Exception e){
            Toast.makeText(this,"剪贴板不是有效的窗口预设参数",Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    void showPresetEditor(int index,Preset old){
        DesignSpec d=loadDesign("新建预设窗口.json");
        FrameLayout root=designedRoot(d);
        root.addView(designedText(d,"新建预设窗口",0));
        String[] labels={"名称","左距","宽度","上距","长度","分类","模式"};
        for(String l:labels)root.addView(designedText(d,l,0));

        EditText name=designedEdit(d,"名称填框",0,old.name);
        EditText x=designedEdit(d,"名称填框",1,String.valueOf(old.x));
        EditText y=designedEdit(d,"名称填框",2,String.valueOf(old.y));
        EditText w=designedEdit(d,"名称填框",3,String.valueOf(old.w));
        EditText h=designedEdit(d,"名称填框",4,String.valueOf(old.h));
        root.addView(name);root.addView(x);root.addView(y);root.addView(w);root.addView(h);

        // 五组快速数值按钮，严格使用设计稿位置。
        EditText[] fields={x,y,w,h};
        for(int fi=0;fi<4;fi++){
            String[] bs={"+100","-100","+10","-10","0"};
            for(int bi=0;bi<5;bi++){
                Button b=designedButton(d,bs[bi],fi);
                final EditText target=fields[fi]; final int delta;
                if(bi==0)delta=100;else if(bi==1)delta=-100;else if(bi==2)delta=10;else if(bi==3)delta=-10;else delta=0;
                b.setOnClickListener(v->{if(delta==0)target.setText("0");else adjustNumber(target,delta);target.setSelection(target.length());});
                root.addView(b);
            }
        }

        final int[] category={old.category};
        Button[] catBtns={designedButton(d,"左",0),designedButton(d,"中",0),designedButton(d,"右",0)};
        for(int i=0;i<3;i++){final int ci=i;catBtns[i].setBackgroundResource(ci==category[0]?R.drawable.card_selected:R.drawable.button);catBtns[i].setOnClickListener(v->{category[0]=ci;for(int j=0;j<3;j++)catBtns[j].setBackgroundResource(j==ci?R.drawable.card_selected:R.drawable.button);});root.addView(catBtns[i]);}
        final int[] mode={Math.max(1,Math.min(6,old.mode))};
        Button[] modes=new Button[6];String[] ml={"左","左","左","左","左","左"};
        for(int i=0;i<6;i++){modes[i]=designedButton(d,"左",i+1);modes[i].setBackgroundResource(i==mode[0]-1?R.drawable.card_selected:R.drawable.button);final int mi=i+1;modes[i].setOnClickListener(v->{mode[0]=mi;for(int j=0;j<6;j++)modes[j].setBackgroundResource(j==mi-1?R.drawable.card_selected:R.drawable.button);});root.addView(modes[i]);}

        Button copy=designedButton(d,"复制",0),paste=designedButton(d,"粘贴",0),cancel=designedButton(d,"取消",0),save=designedButton(d,"保存",0);
        root.addView(copy);root.addView(paste);root.addView(cancel);root.addView(save);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(root).create();
        copy.setOnClickListener(v->copyPresetToClipboard(name,x,y,w,h,mode[0],category[0]));
        paste.setOnClickListener(v->pastePresetFromClipboard(name,x,y,w,h,mode, modes,category,catBtns));
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            String n=name.getText().toString().trim();if(n.isEmpty()){Toast.makeText(this,"请输入预设名称",Toast.LENGTH_SHORT).show();return;}
            Preset p=new Preset(n,Math.max(0,number(x,old.x)),Math.max(0,number(y,old.y)),Math.max(0,number(w,old.w)),Math.max(0,number(h,old.h)),-1,mode[0],category[0]);
            if(index<0)presets.add(p);else presets.set(index,p);savePresets();refresh();dialog.dismiss();
        });
        showDesignedDialog(dialog,1000,700);
    }

    void showScreenDiagnostics(){
        DesignSpec d=loadDesign("权限与诊断.json");
        FrameLayout root=designedRoot(d);
        root.addView(designedText(d,"权限与诊断",0));
        StringBuilder sb=new StringBuilder();
        android.view.Display display=getWindow().getWindowManager().getDefaultDisplay();
        android.graphics.Point p=getRealScreenSize(display);
        android.util.DisplayMetrics m=new android.util.DisplayMetrics();display.getRealMetrics(m);
        sb.append("Display ID: ").append(display.getDisplayId()).append("\n")
          .append("真实分辨率: ").append(p.x).append(" × ").append(p.y).append("\n")
          .append("densityDpi: ").append(m.densityDpi).append("\n")
          .append("density: ").append(m.density).append("\n\n");
        sb.append(hasOverlayPermission()?"✓ 悬浮窗权限\n":"✗ 悬浮窗权限\n");
        sb.append(isAccessibilityServiceEnabled()?"✓ 无障碍服务\n":"✗ 无障碍服务\n");
        sb.append(hasUsageAccess()?"✓ 使用情况访问\n":"✗ 使用情况访问\n");
        sb.append(hasAllFilesPermission()?"✓ 所有文件访问\n":"✗ 所有文件访问\n");
        TextView info=text(sb.toString(),18);info.setTextColor(Color.WHITE);info.setGravity(Gravity.TOP|Gravity.LEFT);info.setPadding(10,10,10,10);
        DesignElem pe=d.find("window","权限信息",0);if(pe==null)pe=new DesignElem(new JSONObject());if(pe.width==0){pe.left=53;pe.top=92;pe.width=857;pe.height=496;}
        placeDesigned(info,pe);root.addView(info);
        Button overlay=designedButton(d,"悬浮权限",0);overlay.setOnClickListener(v->openOverlaySettings());root.addView(overlay);
        Button acc=designedButton(d,"无障碍权限",0);acc.setOnClickListener(v->openAccessibilitySettings());root.addView(acc);
        Button check=designedButton(d,"权限检查",0);check.setOnClickListener(v->{requestRuntimePermissions();Toast.makeText(this,"已检查权限",Toast.LENGTH_SHORT).show();});root.addView(check);
        Button back=designedButton(d,"返回",0);root.addView(back);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(root).create();back.setOnClickListener(v->dialog.dismiss());
        showDesignedDialog(dialog,1000,700);
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
        DesignSpec d=loadDesign("自动启动项目.json");
        FrameLayout root=designedRoot(d);
        root.addView(designedText(d,"自动启动项目",0));

        FrameLayout listFrame=new FrameLayout(this);
        FrameLayout.LayoutParams lfp=new FrameLayout.LayoutParams(850,360);lfp.leftMargin=45;lfp.topMargin=140;
        root.addView(listFrame,lfp);
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list,new ScrollView.LayoutParams(-1,-2));listFrame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        EditText interval=designedEdit(d,"数值",0,String.valueOf(prefs.getInt("auto_start_interval",1)));
        interval.setInputType(2);root.addView(interval);
        root.addView(designedText(d,"任务间隔",0));root.addView(designedText(d,"秒",0));

        final JSONArray[] tasks={loadAutoTasks()};
        final Runnable[] redraw={null};
        redraw[0]=()->{
            list.removeAllViews();
            for(int i=0;i<tasks[0].length();i++){
                JSONObject o=tasks[0].optJSONObject(i);if(o==null)continue;
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
                ImageView icon=new ImageView(this);
                try{icon.setImageDrawable(getPackageManager().getApplicationIcon(o.optString("pkg","")));}catch(Exception ignored){}
                row.addView(icon,new LinearLayout.LayoutParams(60,60));
                TextView n=text(o.optString("name","APP"),22);n.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,55*fontScale());n.setSingleLine(true);row.addView(n,new LinearLayout.LayoutParams(210,70));
                TextView pn=text(o.optString("preset_name","默认"),22);pn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,40*fontScale());pn.setSingleLine(true);row.addView(pn,new LinearLayout.LayoutParams(400,70));
                Button del=button("删除");del.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16*fontScale());row.addView(del,new LinearLayout.LayoutParams(100,50));
                final int idx=i;del.setOnClickListener(v->{JSONArray nA=new JSONArray();for(int j=0;j<tasks[0].length();j++)if(j!=idx)nA.put(tasks[0].optJSONObject(j));tasks[0]=nA;redraw[0].run();});
                list.addView(row,new LinearLayout.LayoutParams(-1,80));
            }
            if(tasks[0].length()==0){TextView e=text("暂无自动启动任务",18);e.setGravity(Gravity.CENTER);list.addView(e,new LinearLayout.LayoutParams(-1,80));}
        };
        Button add=button("＋ 添加任务");FrameLayout.LayoutParams alp=new FrameLayout.LayoutParams(180,50);alp.leftMargin=650;alp.topMargin=529;root.addView(add,alp);
        add.setOnClickListener(v->showAddAutoTaskDialog(tasks,redraw[0]));
        Button cancel=designedButton(d,"取消",0),save=designedButton(d,"保存",0);root.addView(cancel);root.addView(save);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(root).create();
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{prefs.edit().putString("auto_start_items",tasks[0].toString()).putInt("auto_start_interval",Math.max(1,number(interval,1))).apply();Toast.makeText(this,"自动启动项目已保存",Toast.LENGTH_SHORT).show();dialog.dismiss();});
        showDesignedDialog(dialog,1000,700);redraw[0].run();
    }

    JSONArray loadAutoTasks(){
        try{return new JSONArray(prefs.getString("auto_start_items","[]"));}
        catch(Exception e){return new JSONArray();}
    }

    /** 自动启动添加 APP 与“添加APP”共用同一套设计参数。 */
    void showAddAutoTaskDialog(JSONArray[] tasks,Runnable refresh){
        DesignSpec d=loadDesign("添加APP.json");
        FrameLayout root=designedRoot(d);
        root.addView(designedText(d,"添加",0));
        root.addView(designedText(d,"搜索",0));
        root.addView(designedText(d,"分类",0));
        root.addView(designedText(d,"窗口预设",0));
        EditText search=designedEdit(d,"名称填框",0,"");search.setHint("搜索 APP");root.addView(search);

        final int[] cat={0};final int[] selectedPreset={-1};final String[] pkg={""};
        final Runnable[] rr={null};
        Button[] cb=new Button[3];String[] cs={"用户","系统","全部"};
        for(int i=0;i<3;i++){final int ci=i;cb[i]=designedButton(d,cs[i],0);cb[i].setOnClickListener(v->{cat[0]=ci;for(int j=0;j<3;j++)cb[j].setBackgroundResource(j==ci?R.drawable.card_selected:R.drawable.button);if(rr[0]!=null)rr[0].run();});root.addView(cb[i]);}
        cb[0].setBackgroundResource(R.drawable.card_selected);

        Button preset=designedButton(d,"预设窗口下拉选项",0);root.addView(preset);
        preset.setOnClickListener(v->{String[] names=new String[presets.size()+1];names[0]="默认";for(int i=0;i<presets.size();i++)names[i+1]=presets.get(i).name;
            new AlertDialog.Builder(this).setTitle("选择窗口预设").setItems(names,(di,w)->{selectedPreset[0]=w-1;preset.setText(names[w]);}).show();});

        FrameLayout lf=new FrameLayout(this);FrameLayout.LayoutParams lfp=new FrameLayout.LayoutParams(840,320);lfp.leftMargin=54;lfp.topMargin=270;root.addView(lf,lfp);
        ScrollView sv=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);sv.addView(rows,new ScrollView.LayoutParams(-1,-2));lf.addView(sv,new FrameLayout.LayoutParams(-1,-1));
        PackageManager pm=getPackageManager();ArrayList<ApplicationInfo> all=new ArrayList<>();
        try{for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){if(!ai.packageName.equals(getPackageName())&&pm.getLaunchIntentForPackage(ai.packageName)!=null)all.add(ai);}}catch(Exception ignored){}
        Collections.sort(all,(a,b)->getAppLabelSafe(a.packageName).compareToIgnoreCase(getAppLabelSafe(b.packageName)));
        rr[0]=()->{
            rows.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);LinearLayout row=null;int in=0;
            for(ApplicationInfo ai:all){boolean sys=(ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0||(ai.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0;if(cat[0]==0&&sys)continue;if(cat[0]==1&&!sys)continue;
                String name=getAppLabelSafe(ai.packageName);if(!q.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(q)&&!ai.packageName.toLowerCase(Locale.ROOT).contains(q))continue;
                if(in==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);rows.addView(row,new LinearLayout.LayoutParams(-1,160));}
                Button tile=button(name);tile.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16*fontScale());tile.setGravity(Gravity.CENTER);tile.setBackgroundResource(ai.packageName.equals(pkg[0])?R.drawable.card_selected:R.drawable.card);
                try{tile.setCompoundDrawablesWithIntrinsicBounds(null,pm.getApplicationIcon(ai),null,null);}catch(Exception ignored){}
                final String pp=ai.packageName;tile.setOnClickListener(v->{pkg[0]=pp;if(rr[0]!=null)rr[0].run();});
                row.addView(tile,new LinearLayout.LayoutParams(150,150));in++;if(in>=5)in=0;
            }
        };
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){rr[0].run();}public void afterTextChanged(android.text.Editable e){}});
        Button copy=designedButton(d,"复制",0),paste=designedButton(d,"粘贴",0),cancel=designedButton(d,"取消",0),save=designedButton(d,"保存",0);
        root.addView(copy);root.addView(paste);root.addView(cancel);root.addView(save);
        AlertDialog dlg=new AlertDialog.Builder(this).setView(root).create();
        cancel.setOnClickListener(v->dlg.dismiss());
        save.setOnClickListener(v->{if(pkg[0].isEmpty()){Toast.makeText(this,"请先选择 APP",Toast.LENGTH_SHORT).show();return;}try{
            JSONObject o=new JSONObject();o.put("pkg",pkg[0]);o.put("name",getAppLabelSafe(pkg[0]));o.put("preset",selectedPreset[0]);
            if(selectedPreset[0]>=0&&selectedPreset[0]<presets.size()){Preset ap=presets.get(selectedPreset[0]);o.put("preset_name",ap.name);o.put("preset_x",ap.x);o.put("preset_y",ap.y);o.put("preset_w",ap.w);o.put("preset_h",ap.h);o.put("preset_displayId",ap.displayId);o.put("preset_mode",ap.mode);}
            tasks[0].put(o);refresh.run();dlg.dismiss();
        }catch(Exception e){Toast.makeText(this,"保存失败："+e.getMessage(),Toast.LENGTH_LONG).show();}});
        showDesignedDialog(dlg,1000,700);rr[0].run();
    }

    String getAppLabelSafe(String pkg){
        try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}
        catch(Exception e){return pkg;}
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
        // 主界面双击：只负责普通启动，不读取自动任务、不读取 app_last_bounds。
        Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null){
            Toast.makeText(this,"无法启动 APP",Toast.LENGTH_SHORT).show();
            return;
        }
        info.setText("直接启动："+name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
        if(Build.VERSION.SDK_INT>=21) intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try{
            startActivity(intent);
        }catch(Exception e){
            info.setText("启动失败："+e.getMessage());
        }
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
