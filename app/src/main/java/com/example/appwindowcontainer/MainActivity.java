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
    FrameLayout screenRoot;
    TextView info;
    String selectedPackage=null;
    String selectedName=null;
    int selectedWindowMode=1;
    ArrayList<Button> modeButtons=new ArrayList<>();
    HashMap<String,Long> appClickTimes=new HashMap<>();

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

    // 数字输入框右侧快速调整按钮：+100 / -100 / +10 / -10 / 重置0。
    // 这些按钮只修改输入框的数值，不参与任何触控坐标纠正。
    LinearLayout labeledNumberField(String label, EditText input){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l=text(label,14);
        l.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        row.addView(l,new LinearLayout.LayoutParams(dp(115),dp(54)));
        row.addView(input,new LinearLayout.LayoutParams(0,dp(54),1));

        String[] captions={"+100","-100","+10","-10","重置0"};
        int[] deltas={100,-100,10,-10,0};
        for(int i=0;i<captions.length;i++){
            final int delta=deltas[i];
            Button b=button(captions[i]);
            b.setTextSize(11);
            b.setMinWidth(0);
            b.setMinHeight(0);
            b.setPadding(0,0,0,0);
            b.setOnClickListener(v->{
                if(delta==0) input.setText("0");
                else adjustNumber(input,delta);
                input.setSelection(input.length());
            });
            row.addView(b,new LinearLayout.LayoutParams(dp(58),dp(44)));
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
        selectedWindowMode=prefs.getInt("window_mode",1);
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

        // 主界面仍按车机实际显示区域避让：顶部80、底部120。
        // 注意：这两个留白只用于布局，不参与触控坐标换算。
        screenRoot=new FrameLayout(this);
        screenRoot.setBackgroundColor(Color.BLACK);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(12),dp(TOP_BLANK),dp(12),dp(BOTTOM_BLANK));

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

        // 窗口模式放在窗口预设后面，不占用预设卡片空间。
        // 当前模式会高亮，并保存为默认模式，下次启动直接使用。
        LinearLayout modeRow=new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView modeTitle=text("窗口模式",14);
        modeRow.addView(modeTitle,new LinearLayout.LayoutParams(dp(82),dp(40)));
        modeButtons.clear();
        String[] modeNames={"模式1","模式2","模式3","模式4","模式5"};
        for(int i=0;i<modeNames.length;i++){
            final int mode=i+1;
            Button mb=button(modeNames[i]);
            mb.setTextSize(12);
            modeButtons.add(mb);
            mb.setOnClickListener(v->{
                selectedWindowMode=mode;
                prefs.edit().putInt("window_mode",mode).apply();
                refreshModeButtons();
                refreshPresets();
                Toast.makeText(this,"已选择 "+modeNames[mode-1],Toast.LENGTH_SHORT).show();
            });
            modeRow.addView(mb,new LinearLayout.LayoutParams(0,dp(40),1));
        }
        root.addView(modeRow,new LinearLayout.LayoutParams(-1,dp(42)));

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
        info.setOnLongClickListener(v->{showScreenDiagnostics();return true;});
        info.setOnClickListener(v->showPermissionPreparation());

        screenRoot.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(screenRoot);
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
        refreshPresets(); refreshModeButtons(); refreshApps();
        if(selectedPackage==null) info.setText("请选择 APP，然后点击窗口预设启动；双击 APP 可直接启动");
        else info.setText("已选择： "+selectedName+"    → 双击直接启动，或点击窗口预设启动");
    }

    void refreshModeButtons(){
        for(int i=0;i<modeButtons.size();i++){
            Button b=modeButtons.get(i);
            b.setBackgroundResource((i+1)==selectedWindowMode ? R.drawable.card_selected : R.drawable.button);
            b.setTextColor(Color.WHITE);
        }
    }

    void refreshPresets(){
        presetRow.removeAllViews();
        for(int i=0;i<presets.size();i++){
            final int index=i; Preset p=presets.get(i);
            Button b=button(p.name+"\n位置 "+p.x+" , "+p.y+"   "+p.w+" × "+p.h+"\n"+"模式"+selectedWindowMode);
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
            card.setTag(item.pkg);

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
                Long last=appClickTimes.get(item.pkg);
                appClickTimes.put(item.pkg,now);
                updateAppSelectionVisuals();
                if(last!=null && now-last<500){
                    appClickTimes.remove(item.pkg);
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

    void updateAppSelectionVisuals(){
        for(int i=0;i<appGrid.getChildCount();i++){
            View v=appGrid.getChildAt(i);
            if(v instanceof LinearLayout){
                Object tag=v.getTag();
                if(tag instanceof String){
                    v.setBackgroundResource(tag.equals(selectedPackage)?R.drawable.card_selected:R.drawable.card);
                }
            }
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
        // 长按菜单中选择“删除快捷方式”后直接删除，不再弹二次确认。
        apps.remove(item);
        appClickTimes.remove(item.pkg);
        if(item.pkg.equals(selectedPackage)){selectedPackage=null;selectedName=null;}
        saveApps();
        refresh();
        Toast.makeText(this,"已删除快捷方式："+item.name,Toast.LENGTH_SHORT).show();
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

        final String[] category={"用户APP"};
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

    /**
     * 新建/编辑预设。
     *
     * 这里故意不再使用 AlertDialog：部分车机对独立 Dialog Window 的触摸坐标
     * 映射存在偏移，而主 Activity 的普通按钮触控是正常的。编辑器现在直接
     * 覆盖在主 Activity 的同一个 Window 内，因此不再做任何触控纠正/坐标补偿。
     */
    void showPresetEditor(int index,Preset old){
        final FrameLayout overlay=new FrameLayout(this);
        overlay.setBackgroundColor(0xEE000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);

        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24),dp(16),dp(24),dp(16));
        card.setBackgroundResource(R.drawable.card);

        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(
                -1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        cp.leftMargin=dp(80);
        cp.rightMargin=dp(80);
        cp.topMargin=dp(80);
        cp.bottomMargin=dp(120);

        TextView title=text(index<0?"新建窗口预设":"编辑窗口预设",20);
        title.setTypeface(null,1);
        title.setGravity(Gravity.CENTER);
        card.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));

        /*
         * 新建预设：
         * 1. 所有数值默认 0；
         * 2. 不立即加入 presets；
         * 3. 不自动保存；
         * 4. 点击“完成”后才真正写入配置。
         *
         * 编辑预设：
         * 使用副本编辑，点击取消/关闭不会修改原来的预设。
         */
        String defaultName=index<0?"":old.name;
        EditText name=textField("预设名称（支持中文）",defaultName);
        EditText x=numberField("左边间距",index<0?"0":String.valueOf(old.x));
        EditText y=numberField("上边间距",index<0?"0":String.valueOf(old.y));
        EditText width=numberField("窗口宽度",index<0?"0":String.valueOf(old.w));
        EditText height=numberField("窗口高度",index<0?"0":String.valueOf(old.h));

        card.addView(labeledField("预设名称",name));
        card.addView(labeledNumberField("左边间距",x));
        card.addView(labeledNumberField("上边间距",y));
        card.addView(labeledNumberField("窗口宽度",width));
        card.addView(labeledNumberField("窗口高度",height));

        TextView hint=text(
                "只修改窗口位置和大小，不进行任何触控偏移/DPI纠正。\\n"
                +"左边间距、上边间距、宽度、高度都可以使用右侧快捷按钮调整。",
                12
        );
        hint.setPadding(dp(115),dp(6),dp(6),dp(6));
        card.addView(hint,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);

        Button cancel=button("取消");
        Button save=button("完成");

        actions.addView(cancel,new LinearLayout.LayoutParams(dp(120),dp(48)));
        LinearLayout.LayoutParams saveLp=new LinearLayout.LayoutParams(dp(120),dp(48));
        saveLp.leftMargin=dp(12);
        actions.addView(save,saveLp);
        card.addView(actions,new LinearLayout.LayoutParams(-1,dp(58)));

        // 编辑时先复制，点击“完成”才写回；新建时点击“完成”才加入列表。
        final Preset working=new Preset(
                index<0?"新建预设":old.name,
                index<0?0:old.x,
                index<0?0:old.y,
                index<0?0:old.w,
                index<0?0:old.h,
                index<0?-1:old.displayId
        );

        cancel.setOnClickListener(v->{
            screenRoot.removeView(overlay);
        });

        save.setOnClickListener(v->{
            String n=name.getText().toString().trim();
            working.name=n.isEmpty()?"新建预设":n;
            working.x=Math.max(0,number(x,0));
            working.y=Math.max(0,number(y,0));
            working.w=Math.max(0,number(width,0));
            working.h=Math.max(0,number(height,0));

            if(index<0){
                presets.add(working);
            }else if(index<presets.size()){
                presets.set(index,working);
            }

            savePresets();
            screenRoot.removeView(overlay);
            refresh();
            Toast.makeText(this,"窗口预设已保存",Toast.LENGTH_SHORT).show();
        });

        overlay.addView(card,cp);
        screenRoot.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        overlay.bringToFront();
        card.bringToFront();
    }

    void launchApp(Preset p){
        if(selectedPackage==null){
            Toast.makeText(this,"请先选择 APP",Toast.LENGTH_SHORT).show(); return;
        }
        launchAppWithMode(selectedPackage,selectedName,p,selectedWindowMode);
    }

    void launchAppDirect(String pkg,String name){
        try{
            Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent==null){ Toast.makeText(this,"无法启动："+name,Toast.LENGTH_SHORT).show(); return; }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        }catch(Exception e){
            Toast.makeText(this,"启动失败："+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    void launchAppWithMode(String pkg,String name,Preset p,int mode){
        try{
            Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent==null){ Toast.makeText(this,"无法启动："+name,Toast.LENGTH_SHORT).show(); return; }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            ActivityOptions options=ActivityOptions.makeBasic();
            android.graphics.Rect bounds=new android.graphics.Rect(
                    Math.max(0,p.x), Math.max(0,p.y),
                    Math.max(1,p.x+p.w), Math.max(1,p.y+p.h));

            // 所有兼容性较敏感的 ActivityOptions API 均通过反射调用，
            // 避免不同 Android SDK / 厂商 SDK 在 javac 阶段直接报符号错误。
            setLaunchBoundsCompat(options,bounds);

            switch(mode){
                case 2:
                    // 模式2：窗口范围 + 当前显示屏
                    android.view.Display currentDisplay = getWindow().getDecorView().getDisplay();
                    if(currentDisplay != null){
                        setLaunchDisplayIdCompat(options, currentDisplay.getDisplayId());
                    }
                    break;
                case 3:
                    // 模式3：窗口范围 + Freeform（部分车机会忽略）
                    setLaunchWindowingMode(options,5);
                    break;
                case 4:
                    // 模式4：窗口范围 + Multi-window（部分车机会忽略）
                    setLaunchWindowingMode(options,2);
                    break;
                case 5:
                    // 模式5：先启动，短延时再次尝试窗口范围
                    break;
                case 1:
                default:
                    // 模式1：仅 LaunchBounds
                    break;
            }

            startActivity(intent,options.toBundle());

            if(mode==5){
                new Handler().postDelayed(()->{
                    try{
                        ActivityOptions retry=ActivityOptions.makeBasic();
                        setLaunchBoundsCompat(retry,bounds);
                        Intent retryIntent=getPackageManager().getLaunchIntentForPackage(pkg);
                        if(retryIntent!=null){
                            retryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(retryIntent,retry.toBundle());
                        }
                    }catch(Exception ignored){}
                },450);
            }
        }catch(Exception e){
            // 某些车机/全屏 APP 会拒绝 LaunchBounds；至少保证 APP 能正常启动。
            try{ launchAppDirect(pkg,name); }catch(Exception ignored){}
            Toast.makeText(this,"模式"+mode+"限制失败，已尝试普通启动",Toast.LENGTH_SHORT).show();
        }
    }

    void setLaunchBoundsCompat(ActivityOptions options, android.graphics.Rect bounds){
        try{
            java.lang.reflect.Method m=ActivityOptions.class.getMethod("setLaunchBounds",android.graphics.Rect.class);
            m.invoke(options,bounds);
        }catch(Exception ignored){}
    }

    void setLaunchDisplayIdCompat(ActivityOptions options,int displayId){
        try{
            java.lang.reflect.Method m=ActivityOptions.class.getMethod("setLaunchDisplayId",int.class);
            m.invoke(options,displayId);
        }catch(Exception ignored){}
    }

    void setLaunchWindowingMode(ActivityOptions options,int mode){
        try{
            java.lang.reflect.Method m=ActivityOptions.class.getMethod("setLaunchWindowingMode",int.class);
            m.invoke(options,mode);
        }catch(Exception ignored){}
    }

    void showScreenDiagnostics(){
        android.graphics.Point rs=getRealScreenSize();
        StringBuilder s=new StringBuilder();
        s.append("Activity Display：").append(rs.x).append(" × ").append(rs.y).append("\\n");
        s.append("当前窗口模式：模式").append(selectedWindowMode).append("\\n");
        s.append("已保存预设：").append(presets.size()).append("\\n");
        new AlertDialog.Builder(this).setTitle("屏幕诊断").setMessage(s.toString()).setPositiveButton("关闭",null).show();
    }
}
