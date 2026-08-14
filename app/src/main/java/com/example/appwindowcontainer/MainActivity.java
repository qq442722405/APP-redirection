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
        int x,y,w,h,dpi;
        Preset(String n,int x,int y,int w,int h,int dpi){
            this.name=n; this.x=x; this.y=y; this.w=w; this.h=h; this.dpi=dpi;
        }
    }

    int dp(int v){
        return (int)(v*getResources().getDisplayMetrics().density+.5f);
    }

    TextView text(String s,float size){
        TextView t=new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    Button button(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14);
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
        e.setHint(label); e.setText(value); e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY); e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return e;
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
                        o.getInt("w"),o.getInt("h"),o.optInt("dpi",160)
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
                o.put("w",p.w); o.put("h",p.h); o.put("dpi",p.dpi);
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

        info=text("",14);
        info.setTextColor(Color.WHITE);
        info.setPadding(dp(10),dp(8),dp(10),dp(8));
        root.addView(info,new LinearLayout.LayoutParams(-1,dp(68)));
        // 长按底部状态区可打开特殊权限准备页；主界面不增加设置按钮。
        info.setOnLongClickListener(v->{showPermissionPreparation();return true;});
        setContentView(root);
        refresh();
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
            Button b=button(p.name+"\n位置 "+p.x+" , "+p.y+"   "+p.w+" × "+p.h+"\nDPI "+p.dpi);
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
        PackageManager pm=getPackageManager(); ArrayList<ApplicationInfo> list=new ArrayList<>();
        for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(!ai.packageName.equals(getPackageName())&&pm.getLaunchIntentForPackage(ai.packageName)!=null)list.add(ai);
        }
        Collections.sort(list,(a,b)->pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        EditText search=textField("搜索 APP",""); box.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this); scroll.addView(rows); box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(430)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加 APP").setView(box).setNegativeButton("关闭",null).create();
        Runnable refreshList=()->{
            rows.removeAllViews(); String q=search.getText().toString().trim().toLowerCase(); int count=0;
            for(ApplicationInfo ai:list){
                String name=pm.getApplicationLabel(ai).toString();
                if(!q.isEmpty()&&!name.toLowerCase().contains(q))continue;
                Button b=button(name); b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
                b.setOnClickListener(v->{
                    boolean exists=false; for(AppItem a:apps)if(a.pkg.equals(ai.packageName)){exists=true;break;}
                    if(!exists){apps.add(new AppItem(ai.packageName,name));saveApps();}
                    selectedPackage=ai.packageName; selectedName=name; refresh(); dialog.dismiss();
                });
                rows.addView(b,new LinearLayout.LayoutParams(-1,dp(48))); if(++count>=40)break;
            }
        };
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){refreshList.run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        refreshList.run(); dialog.show();
    }

    void presetMenu(int index){
        Preset p=presets.get(index);
        new AlertDialog.Builder(this).setTitle(p.name).setItems(new String[]{"编辑预设","删除预设"},(d,w)->{
            if(w==0)editPreset(index);else{presets.remove(index);savePresets();refresh();}
        }).show();
    }

    // 新建预设：先进入全屏手动框选模式；编辑已有预设仍可直接修改数字。
    void editPreset(int index){
        if(index<0){startSelectionMode();return;}
        Preset old=presets.get(index);
        showPresetEditor(index,old);
    }

    void showPresetEditor(int index,Preset old){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        EditText name=textField("预设名称（支持中文）",old.name);
        EditText x=numberField("X 左上位置",String.valueOf(old.x));
        EditText y=numberField("Y 上下位置",String.valueOf(old.y));
        EditText width=numberField("窗口宽度",String.valueOf(old.w));
        EditText height=numberField("窗口高度",String.valueOf(old.h));
        EditText dpi=numberField("APP DPI",String.valueOf(old.dpi));
        box.addView(labeledField("预设名称",name));
        box.addView(labeledField("X 左上位置",x));
        box.addView(labeledField("Y 上下位置",y));
        box.addView(labeledField("窗口宽度",width));
        box.addView(labeledField("窗口高度",height));
        box.addView(labeledField("APP DPI",dpi));
        TextView hint=text("位置和尺寸来自车机真实屏幕坐标；新建预设可直接全屏手指框选。",12);
        box.addView(hint);
        new AlertDialog.Builder(this).setTitle("编辑窗口预设").setView(box).setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{
                    String n=name.getText().toString().trim(); if(n.isEmpty()){Toast.makeText(this,"请输入预设名称",Toast.LENGTH_SHORT).show();return;}
                    android.graphics.Point rs=getRealScreenSize();
                    Preset p=new Preset(n,Math.max(0,number(x,0)),Math.max(0,number(y,TOP_BLANK)),Math.max(1,number(width,rs.x)),Math.max(1,number(height,Math.max(1,rs.y-TOP_BLANK-BOTTOM_BLANK))),Math.max(1,number(dpi,getResources().getDisplayMetrics().densityDpi)));
                    presets.set(index,p);savePresets();refresh();
                }).show();
    }

    // 全屏框选：拖动手指，实时得到 X/Y/宽/高；松手后进入预设保存界面。
    void startSelectionMode(){
        final Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final SelectionView view=new SelectionView(this,dialog);
        dialog.setContentView(view);
        dialog.setCancelable(false);
        Window win=dialog.getWindow();
        if(win!=null){
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
            win.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            win.setDimAmount(0f);
        }
        dialog.show();
        if(win!=null){
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
            win.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    android.graphics.Point getRealScreenSize(){
        android.graphics.Point p=new android.graphics.Point();
        getWindowManager().getDefaultDisplay().getRealSize(p);
        return p;
    }

    class SelectionView extends View {
        Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),stroke=new Paint(Paint.ANTI_ALIAS_FLAG),txt=new Paint(Paint.ANTI_ALIAS_FLAG);
        Dialog dialog; float sx=-1,sy=-1,ex=-1,ey=-1; boolean drawing=false;
        android.graphics.Point real=getRealScreenSize();
        SelectionView(Context c,Dialog d){super(c);dialog=d;setBackgroundColor(Color.TRANSPARENT);
            fill.setColor(0x66000000); stroke.setColor(Color.WHITE); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(3));
            txt.setColor(Color.WHITE); txt.setTextSize(dp(16)); txt.setTypeface(Typeface.DEFAULT_BOLD);
        }
        float rx(float x){return x*real.x/Math.max(1,getWidth());}
        float ry(float y){return y*real.y/Math.max(1,getHeight());}
        float vx(float x){return x*getWidth()/Math.max(1,real.x);}
        float vy(float y){return y*getHeight()/Math.max(1,real.y);}
        protected void onDraw(Canvas c){
            super.onDraw(c);
            c.drawRect(0,0,getWidth(),getHeight(),fill);
            Paint guide=new Paint(Paint.ANTI_ALIAS_FLAG); guide.setColor(0xAA00FF66); guide.setStyle(Paint.Style.FILL);
            c.drawRect(0,vy(TOP_BLANK),getWidth(),vy(TOP_BLANK)+dp(2),guide);
            c.drawRect(0,vy(real.y-BOTTOM_BLANK)-dp(2),getWidth(),vy(real.y-BOTTOM_BLANK),guide);
            if(drawing){
                float l=Math.min(vx(sx),vx(ex)),r=Math.max(vx(sx),vx(ex));
                float t=Math.min(vy(sy),vy(ey)),b=Math.max(vy(sy),vy(ey));
                Paint clear=new Paint(); clear.setColor(0x2200FF66); c.drawRect(l,t,r,b,clear); c.drawRect(l,t,r,b,stroke);
                int ix=Math.round(Math.min(sx,ex)), iy=Math.round(Math.min(sy,ey));
                int iw=Math.round(Math.abs(ex-sx)), ih=Math.round(Math.abs(ey-sy));
                String s="X "+ix+"   Y "+iy+"   W "+iw+"   H "+ih;
                float tw=txt.measureText(s); c.drawText(s,Math.max(dp(10),(getWidth()-tw)/2f),dp(42),txt);
            }else{
                String s="手指拖动框选 APP 显示区域"; float tw=txt.measureText(s); c.drawText(s,(getWidth()-tw)/2f,dp(42),txt);
                txt.setTextSize(dp(13)); c.drawText("上方 80 px / 下方 120 px 为容器避让区",dp(18),vy(real.y-BOTTOM_BLANK)-dp(14),txt); txt.setTextSize(dp(16));
            }
            Paint small=new Paint(Paint.ANTI_ALIAS_FLAG); small.setColor(Color.WHITE); small.setTextSize(dp(13));
            c.drawText("松开手指后自动进入预设保存",dp(18),getHeight()-dp(24),small);
        }
        public boolean onTouchEvent(android.view.MotionEvent e){
            float x=rx(e.getX()),y=ry(e.getY());
            if(e.getAction()==MotionEvent.ACTION_DOWN){sx=ex=x;sy=ey=y;drawing=true;invalidate();return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){ex=x;ey=y;invalidate();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP){ex=x;ey=y;invalidate();
                int left=Math.round(Math.min(sx,ex)),top=Math.round(Math.min(sy,ey));
                int w=Math.round(Math.abs(ex-sx)),h=Math.round(Math.abs(ey-sy));
                if(w<20||h<20){Toast.makeText(MainActivity.this,"框选区域太小，请重新框选",Toast.LENGTH_SHORT).show();drawing=false;invalidate();return true;}
                dialog.dismiss();
                final Preset p=new Preset("",left,top,w,h,getResources().getDisplayMetrics().densityDpi);
                new Handler().postDelayed(()->showNewPresetEditor(p),180);
                return true;
            }
            return true;
        }
    }

    void showNewPresetEditor(Preset old){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        EditText name=textField("预设名称（支持中文）","");
        EditText x=numberField("X 左上位置",String.valueOf(old.x));
        EditText y=numberField("Y 上下位置",String.valueOf(old.y));
        EditText width=numberField("窗口宽度",String.valueOf(old.w));
        EditText height=numberField("窗口高度",String.valueOf(old.h));
        EditText dpi=numberField("APP DPI",String.valueOf(old.dpi));
        box.addView(labeledField("预设名称",name));
        box.addView(labeledField("X 左上位置",x));
        box.addView(labeledField("Y 上下位置",y));
        box.addView(labeledField("窗口宽度",width));
        box.addView(labeledField("窗口高度",height));
        box.addView(labeledField("APP DPI",dpi));
        TextView hint=text("已从全屏框选自动获取位置和分辨率，可在这里微调。",12); box.addView(hint);
        new AlertDialog.Builder(this).setTitle("新建窗口预设").setView(box).setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{
                    String n=name.getText().toString().trim();
                    if(n.isEmpty()){Toast.makeText(this,"请输入预设名称",Toast.LENGTH_SHORT).show();return;}
                    Preset p=new Preset(n,Math.max(0,number(x,old.x)),Math.max(0,number(y,old.y)),Math.max(1,number(width,old.w)),Math.max(1,number(height,old.h)),Math.max(1,number(dpi,old.dpi)));
                    presets.add(p);savePresets();refresh();
                }).show();
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

        android.graphics.Point real=getRealScreenSize();
        int left=Math.max(0,Math.min(p.x,real.x-1));
        int top=Math.max(0,Math.min(p.y,real.y-1));
        int right=Math.max(left+1,Math.min(p.x+p.w,real.x));
        int bottom=Math.max(top+1,Math.min(p.y+p.h,real.y));
        android.graphics.Rect bounds=new android.graphics.Rect(left,top,right,bottom);

        ActivityOptions options=ActivityOptions.makeBasic();
        options.setLaunchBounds(bounds);

        // 同一物理屏幕上明确指定当前 Display，避免车机多 Display/虚拟 Display
        // 环境下 Launcher 把 Activity 放到默认 Display。
        if(Build.VERSION.SDK_INT>=26){
            try{
                android.view.Display display=getDisplay();
                if(display!=null) options.setLaunchDisplayId(display.getDisplayId());
            }catch(Exception ignored){}
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.putExtra("com.example.appwindowcontainer.target_x",left);
        intent.putExtra("com.example.appwindowcontainer.target_y",top);
        intent.putExtra("com.example.appwindowcontainer.target_w",right-left);
        intent.putExtra("com.example.appwindowcontainer.target_h",bottom-top);
        intent.putExtra("com.example.appwindowcontainer.target_dpi",p.dpi);

        info.setText("启动："+selectedName+"\n"+p.name+"  X "+left+"  Y "+top+"  "+(right-left)+" × "+(bottom-top)+"  DPI "+p.dpi);

        try{
            startActivity(intent,options.toBundle());
            // 给车机 Launcher 一点时间完成 Activity 切换。这里不再尝试使用
            // 非公开 API 强制修改别的 APP，避免在 Android 12 上崩溃。
            new Handler().postDelayed(()->{
                Toast.makeText(MainActivity.this,
                        "已按预设请求窗口："+(right-left)+" × "+(bottom-top),
                        Toast.LENGTH_SHORT).show();
            },350);
        }catch(Exception e){
            info.setText("启动失败："+e.getMessage());
            try{startActivity(intent);}
            catch(Exception ignored){Toast.makeText(this,"APP 启动失败",Toast.LENGTH_SHORT).show();}
        }
    }
}
