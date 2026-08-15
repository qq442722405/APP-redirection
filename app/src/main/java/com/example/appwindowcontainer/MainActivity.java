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
        int x,y,w,h,displayId;
        Preset(String n,int x,int y,int w,int h){this(n,x,y,w,h,-1);}
        Preset(String n,int x,int y,int w,int h,int displayId){
            this.name=n; this.x=x; this.y=y; this.w=w; this.h=h; this.displayId=displayId;
        }
    }

    int dp(int v){
        return (int)(v*getResources().getDisplayMetrics().density+.5f);
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
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }

    // 数字输入框右侧快速调整按钮：每次 +10 / -10。
    LinearLayout labeledNumberField(String label, EditText input){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l=text(label,14);
        row.addView(l,new LinearLayout.LayoutParams(dp(115),dp(54)));
        row.addView(input,new LinearLayout.LayoutParams(0,dp(54),1));
        Button plus=button("+10");
        Button minus=button("-10");
        plus.setTextSize(12); minus.setTextSize(12);
        plus.setMinWidth(0); minus.setMinWidth(0);
        plus.setPadding(0,0,0,0); minus.setPadding(0,0,0,0);
        plus.setOnClickListener(v->adjustNumber(input,10));
        minus.setOnClickListener(v->adjustNumber(input,-10));
        row.addView(plus,new LinearLayout.LayoutParams(dp(54),dp(44)));
        row.addView(minus,new LinearLayout.LayoutParams(dp(54),dp(44)));
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
                        o.getInt("w"),o.getInt("h"),o.optInt("displayId",-1)
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
                o.put("w",p.w); o.put("h",p.h); o.put("displayId",p.displayId);
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

        // 针对全屏 APP 的兼容性测试。每个按钮代表不同的启动策略。
        String[] testNames={"全屏1","全屏2","全屏3","全屏4","全屏5"};
        for(int i=1;i<=5;i++){
            final int testNo=i;
            Button tb=button(testNames[i-1]);
            tb.setTextSize(11);
            tb.setMinWidth(0); tb.setPadding(0,0,0,0);
            tb.setOnClickListener(v->runWindowTest(testNo));
            presetHeader.addView(tb,new LinearLayout.LayoutParams(dp(62),dp(42)));
        }
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
        info.setOnLongClickListener(v->{showScreenDiagnostics();return true;});
        info.setOnClickListener(v->showPermissionPreparation());
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
            Button b=button(p.name+"\n位置 "+p.x+" , "+p.y+"   "+p.w+" × "+p.h);
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

    boolean isSystemApp(ApplicationInfo ai){
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
               (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    void refreshApps(){
        appGrid.removeAllViews();
        appGrid.setOrientation(LinearLayout.HORIZONTAL);
        for(AppItem item:apps){
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(5),dp(5),dp(5),dp(5));
            card.setBackgroundResource(item.pkg.equals(selectedPackage)?R.drawable.card_selected:R.drawable.card);

            ImageView icon=new ImageView(this);
            try{
                ApplicationInfo ai=getPackageManager().getApplicationInfo(item.pkg,0);
                icon.setImageDrawable(getPackageManager().getApplicationIcon(ai));
            }catch(Exception ignored){}
            card.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));

            TextView name=text(item.name,12);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(name,new LinearLayout.LayoutParams(dp(100),dp(28)));

            card.setOnClickListener(v->{
                selectedPackage=item.pkg;
                selectedName=item.name;
                long now=System.currentTimeMillis();
                Object last=v.getTag();
                v.setTag(now);
                refreshApps();
                if(last instanceof Long && now-(Long)last<350){
                    launchAppDirect(item.pkg,item.name);
                }else{
                    info.setText("已选择："+item.name+"  → 双击直接启动，或点击窗口预设");
                }
            });
            card.setOnLongClickListener(v->{appLongMenu(item);return true;});
            appGrid.addView(card,new LinearLayout.LayoutParams(dp(112),dp(92)));
        }
        if(apps.isEmpty()){
            TextView empty=text("点击左侧“＋”添加 APP",15);
            empty.setGravity(Gravity.CENTER);
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

    String appCategory(ApplicationInfo ai){
        return isSystemApp(ai) ? "系统APP" : "用户APP";
    }

    void chooseApp(){
        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> all=new ArrayList<>();
        for(ApplicationInfo ai:pm.getInstalledApplications(PackageManager.GET_META_DATA)){
            if(ai.packageName.equals(getPackageName())) continue;
            if(pm.getLaunchIntentForPackage(ai.packageName)!=null) all.add(ai);
        }
        Collections.sort(all,(a,b)->pm.getApplicationLabel(a).toString()
                .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(4),dp(8),dp(4));

        LinearLayout tabs=new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button allBtn=button("全部APP");
        Button sysBtn=button("系统APP");
        Button userBtn=button("用户APP");
        tabs.addView(allBtn,new LinearLayout.LayoutParams(0,dp(44),1));
        tabs.addView(sysBtn,new LinearLayout.LayoutParams(0,dp(44),1));
        tabs.addView(userBtn,new LinearLayout.LayoutParams(0,dp(44),1));
        box.addView(tabs);

        EditText search=textField("搜索 APP",""); 
        box.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));

        ScrollView scroll=new ScrollView(this);
        LinearLayout grid=new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(450)));

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("添加 APP")
                .setView(box)
                .setNegativeButton("关闭",null)
                .create();

        final String[] category={"全部APP"};
        Runnable render=()->{
            grid.removeAllViews();
            String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            ArrayList<ApplicationInfo> filtered=new ArrayList<>();
            for(ApplicationInfo ai:all){
                String cat=appCategory(ai);
                String name=pm.getApplicationLabel(ai).toString();
                if(!category[0].equals("全部APP")&&!cat.equals(category[0])) continue;
                if(!q.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(q)) continue;
                filtered.add(ai);
            }

            LinearLayout row=null;
            for(int i=0;i<filtered.size();i++){
                if(i%4==0){
                    row=new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    grid.addView(row,new LinearLayout.LayoutParams(-1,dp(118)));
                }
                ApplicationInfo ai=filtered.get(i);
                String name=pm.getApplicationLabel(ai).toString();

                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(dp(4),dp(4),dp(4),dp(4));
                card.setBackgroundResource(R.drawable.card);

                ImageView icon=new ImageView(this);
                try{ icon.setImageDrawable(pm.getApplicationIcon(ai)); }catch(Exception ignored){}
                card.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));

                TextView title=text(name,11);
                title.setGravity(Gravity.CENTER);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(title,new LinearLayout.LayoutParams(-1,dp(42)));

                card.setOnClickListener(v->{
                    boolean exists=false;
                    for(AppItem a:apps) if(a.pkg.equals(ai.packageName)){exists=true;break;}
                    if(!exists) apps.add(new AppItem(ai.packageName,name));
                    selectedPackage=ai.packageName;
                    selectedName=name;
                    saveApps();
                    refresh();
                    dialog.dismiss();
                });
                row.addView(card,new LinearLayout.LayoutParams(0,dp(106),1));
            }
            if(filtered.isEmpty()){
                TextView empty=text("没有找到符合条件的 APP",15);
                empty.setGravity(Gravity.CENTER);
                grid.addView(empty,new LinearLayout.LayoutParams(-1,dp(100)));
            }
        };

        View.OnClickListener tabListener=v->{
            if(v==allBtn) category[0]="全部APP";
            else if(v==sysBtn) category[0]="系统APP";
            else category[0]="用户APP";
            render.run();
        };
        allBtn.setOnClickListener(tabListener);
        sysBtn.setOnClickListener(tabListener);
        userBtn.setOnClickListener(tabListener);

        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){render.run();}
            public void afterTextChanged(android.text.Editable e){}
        });

        render.run();
        dialog.show();
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
            Preset old=new Preset("",0,TOP_BLANK,Math.min(2160,rs.x),
                    Math.max(1,rs.y-TOP_BLANK-BOTTOM_BLANK),-1);
            showPresetEditor(-1,old);
            return;
        }
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

        box.addView(labeledField("预设名称",name));
        box.addView(labeledNumberField("X 左上位置",x));
        box.addView(labeledNumberField("Y 上下位置",y));
        box.addView(labeledNumberField("窗口宽度",width));
        box.addView(labeledNumberField("窗口高度",height));

        android.graphics.Point rs=getRealScreenSize();
        TextView hint=text("车机当前实际屏幕："+rs.x+" × "+rs.y+"\n三区域直接按整块屏幕坐标输入，例如左区 X=0，中区 X=2160，右区 X=4320。\n+10 / -10 可快速微调。",12);
        hint.setPadding(dp(115),dp(4),dp(4),dp(4));
        box.addView(hint);

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(index<0?"新建窗口预设":"编辑窗口预设")
                .setView(box).setNegativeButton("取消",null).create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,"保存",(d,w)->{
            String n=name.getText().toString().trim();
            if(n.isEmpty()){Toast.makeText(this,"请输入预设名称",Toast.LENGTH_SHORT).show();return;}
            Preset p=new Preset(n,
                    Math.max(0,number(x,old.x)),
                    Math.max(0,number(y,old.y)),
                    Math.max(1,number(width,old.w)),
                    Math.max(1,number(height,old.h)),
                    -1);
            if(index<0) presets.add(p); else presets.set(index,p);
            savePresets(); refresh();
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


    /**
     * 车机兼容性测试。
     *
     * 所有测试统一使用：左=500、上=100、宽=1000、高=500。
     *
     * 测试1：标准 LaunchBounds。
     * 测试2：NEW_DOCUMENT + LaunchBounds，尽量创建独立任务。
     * 测试3：NEW_TASK + CLEAR_TOP + LaunchBounds。
     * 测试4：NEW_DOCUMENT + MULTIPLE_TASK + LaunchBounds。
     * 测试5：标准 LaunchBounds + 不传额外窗口参数，用于对比车机行为。
     */
    void runWindowTest(int testNo){
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

        android.view.Display display=getWindow().getWindowManager().getDefaultDisplay();
        android.graphics.Point real=getRealScreenSize(display);

        final int left=Math.max(0,Math.min(500,real.x-1));
        final int top=Math.max(0,Math.min(100,real.y-1));
        final int right=Math.max(left+1,Math.min(left+1000,real.x));
        final int bottom=Math.max(top+1,Math.min(top+500,real.y));
        android.graphics.Rect bounds=new android.graphics.Rect(left,top,right,bottom);

        ActivityOptions options=ActivityOptions.makeBasic();
        options.setLaunchBounds(bounds);

        // 车机全屏 APP 专用测试：
        // 1 = 标准 LaunchBounds
        // 2 = LaunchBounds + NEW_DOCUMENT
        // 3 = LaunchBounds + MULTIPLE_TASK
        // 4 = 通过 Android 8+ 隐藏 API 尝试请求 FREEFORM
        // 5 = FREEFORM + LaunchBounds + 独立任务
        // 这些方案只能“请求”系统 WindowManager，不能绕过系统权限。
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(testNo==2){
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        }else if(testNo==3){
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }else if(testNo==4){
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            trySetLaunchWindowingMode(options,5); // WINDOWING_MODE_FREEFORM
        }else if(testNo==5){
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            trySetLaunchWindowingMode(options,5);
        }

        if(Build.VERSION.SDK_INT>=26 && display!=null){
            try{
                java.lang.reflect.Method m=ActivityOptions.class.getMethod("setLaunchDisplayId",int.class);
                m.invoke(options,display.getDisplayId());
            }catch(Exception ignored){}
        }

        intent.putExtra("com.example.appwindowcontainer.test_mode",testNo);
        intent.putExtra("com.example.appwindowcontainer.target_x",left);
        intent.putExtra("com.example.appwindowcontainer.target_y",top);
        intent.putExtra("com.example.appwindowcontainer.target_w",right-left);
        intent.putExtra("com.example.appwindowcontainer.target_h",bottom-top);
        
        String mode=(testNo>=4)?"FREEFORM + LaunchBounds":"LaunchBounds";
        info.setText("全屏"+testNo+"："+selectedName+"\n"+
                "方案="+mode+"  X="+left+" Y="+top+"  "+(right-left)+" × "+(bottom-top)+
                "  不修改 DPI");
        try{
            startActivity(intent,options.toBundle());
        }catch(Exception e){
            info.setText("全屏"+testNo+"启动失败："+e.getMessage());
            try{startActivity(intent);}catch(Exception ignored){}
        }
    }

    void trySetLaunchWindowingMode(ActivityOptions options,int mode){
        if(Build.VERSION.SDK_INT<26) return;
        try{
            java.lang.reflect.Method m=ActivityOptions.class.getMethod("setLaunchWindowingMode",int.class);
            m.setAccessible(true);
            m.invoke(options,mode);
        }catch(Throwable ignored){
            // Android 12 对隐藏 API 可能阻止反射；这里静默失败，仍保留 LaunchBounds。
        }
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

        info.setText("启动："+selectedName+"\n"+p.name+"  X "+left+"  Y "+top+"  "+(right-left)+" × "+(bottom-top));

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
