package com.example.appwindowcontainer;

import android.app.ActivityOptions;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import org.json.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    static final String PREF="container_prefs";
    static final String APPS="apps";
    static final String PRESETS="presets";

    // 车机每个区域固定为 2032 × 960
    static final int AREA_WIDTH = 2032;
    static final int AREA_HEIGHT = 960;

    SharedPreferences prefs;
    LinearLayout presetRow, appGrid;
    TextView info;
    String selectedPackage = null;
    String selectedName = null;

    int containerDpi = 160;
    int topBlank = 80;
    int bottomBlank = 120;

    ArrayList<AppItem> apps = new ArrayList<>();
    ArrayList<Preset> presets = new ArrayList<>();

    static class AppItem {
        String pkg;
        String name;
        AppItem(String p,String n){pkg=p;name=n;}
    }

    static class Preset {
        String name;
        int x,y,w,h,dpi;

        Preset(String n,int x,int y,int w,int h,int dpi){
            this.name=n;
            this.x=x;
            this.y=y;
            this.w=w;
            this.h=h;
            this.dpi=dpi;
        }
    }

    int dp(int v){
        return (int)(v * getResources().getDisplayMetrics().density + .5f);
    }

    TextView text(String s,float size){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    Button button(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.button);
        return b;
    }

    EditText field(String hint,String value){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    int number(EditText e,int fallback){
        try{return Integer.parseInt(e.getText().toString().trim());}
        catch(Exception ex){return fallback;}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        prefs=getSharedPreferences(PREF,0);
        loadData();
        buildUI();
    }

    void loadData(){
        topBlank=prefs.getInt("topBlank",80);
        bottomBlank=prefs.getInt("bottomBlank",120);
        containerDpi=prefs.getInt("containerDpi",160);

        try{
            JSONArray a=new JSONArray(prefs.getString(APPS,"[]"));
            PackageManager pm=getPackageManager();

            for(int i=0;i<a.length();i++){
                String p=a.getString(i);
                try{
                    ApplicationInfo ai=pm.getApplicationInfo(p,0);
                    apps.add(new AppItem(
                            p,
                            pm.getApplicationLabel(ai).toString()
                    ));
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}

        try{
            JSONArray a=new JSONArray(prefs.getString(PRESETS,"[]"));

            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);

                presets.add(new Preset(
                        o.getString("name"),
                        o.getInt("x"),
                        o.getInt("y"),
                        o.getInt("w"),
                        o.getInt("h"),
                        o.optInt("dpi",160)
                ));
            }
        }catch(Exception ignored){}

    }

    void saveApps(){
        JSONArray a=new JSONArray();

        for(AppItem x:apps)
            a.put(x.pkg);

        prefs.edit().putString(APPS,a.toString()).apply();
    }

    void savePresets(){
        JSONArray a=new JSONArray();

        try{
            for(Preset p:presets){
                JSONObject o=new JSONObject();

                o.put("name",p.name);
                o.put("x",p.x);
                o.put("y",p.y);
                o.put("w",p.w);
                o.put("h",p.h);
                o.put("dpi",p.dpi);

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
        root.setPadding(dp(12),dp(6),dp(12),dp(6));

        // 顶部
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title=text("APP 窗口容器",20);
        title.setTypeface(null,1);

        header.addView(
                title,
                new LinearLayout.LayoutParams(0,dp(50),1)
        );

        Button settings=button("⚙ 设置");
        settings.setOnClickListener(v->showSettings());

        header.addView(
                settings,
                new LinearLayout.LayoutParams(dp(90),dp(46))
        );

        root.addView(header);

        // 预设
        TextView pt=text("窗口预设",17);
        pt.setTypeface(null,1);

        root.addView(
                pt,
                new LinearLayout.LayoutParams(-1,dp(34))
        );

        ScrollView presetScroll=new ScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);

        presetRow=new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);

        presetScroll.addView(presetRow);

        root.addView(
                presetScroll,
                new LinearLayout.LayoutParams(-1,dp(82))
        );

        // APP区域标题
        LinearLayout appTitle=new LinearLayout(this);
        appTitle.setGravity(Gravity.CENTER_VERTICAL);

        TextView at=text("已添加 APP",17);
        at.setTypeface(null,1);

        appTitle.addView(
                at,
                new LinearLayout.LayoutParams(0,dp(42),1)
        );

        Button add=button("＋ 添加 APP");
        add.setOnClickListener(v->chooseApp());

        appTitle.addView(
                add,
                new LinearLayout.LayoutParams(dp(120),dp(42))
        );

        root.addView(appTitle);

        // APP小方格
        ScrollView appScroll=new ScrollView(this);

        appGrid=new LinearLayout(this);
        appGrid.setOrientation(LinearLayout.HORIZONTAL);

        appScroll.addView(appGrid);

        root.addView(
                appScroll,
                new LinearLayout.LayoutParams(-1,0,1)
        );

        info=text("",14);
        info.setTextColor(Color.WHITE);
        info.setPadding(dp(10),dp(8),dp(10),dp(8));

        root.addView(
                info,
                new LinearLayout.LayoutParams(-1,dp(68))
        );

        setContentView(root);

        refresh();
    }

    void refresh(){
        refreshPresets();
        refreshApps();

        if(selectedPackage==null){
            info.setText(
                    "请选择 APP，然后点击上方窗口预设启动"
            );
        }else{
            info.setText(
                    "已选择： "+selectedName+
                    "    → 点击窗口预设启动"
            );
        }
    }

    void refreshPresets(){

        presetRow.removeAllViews();

        for(int i=0;i<presets.size();i++){

            final int index=i;
            Preset p=presets.get(i);

            Button b=button(
                    p.name+
                    "\n"+
                    p.w+" × "+p.h+
                    "   DPI "+p.dpi
            );

            b.setOnClickListener(v->{

                if(selectedPackage==null){
                    Toast.makeText(
                            this,
                            "请先选择 APP",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                launchApp(p);
            });

            b.setOnLongClickListener(v->{
                presetMenu(index);
                return true;
            });

            presetRow.addView(
                    b,
                    new LinearLayout.LayoutParams(
                            dp(190),
                            dp(58)
                    )
            );
        }

        Button add=button("＋ 新建预设");
        add.setOnClickListener(v->editPreset(-1));

        presetRow.addView(
                add,
                new LinearLayout.LayoutParams(
                        dp(100),
                        dp(58)
                )
        );
    }

    void refreshApps(){

        appGrid.removeAllViews();

        for(AppItem item:apps){

            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(5),dp(5),dp(5),dp(5));

            card.setBackgroundResource(
                    item.pkg.equals(selectedPackage)
                            ? R.drawable.card_selected
                            : R.drawable.card
            );

            ImageView icon=new ImageView(this);

            try{
                ApplicationInfo ai=
                        getPackageManager()
                                .getApplicationInfo(item.pkg,0);

                Drawable d=
                        getPackageManager()
                                .getApplicationIcon(ai);

                icon.setImageDrawable(d);

            }catch(Exception ignored){}

            card.addView(
                    icon,
                    new LinearLayout.LayoutParams(
                            dp(46),
                            dp(46)
                    )
            );

            TextView name=text(item.name,12);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);

            card.addView(
                    name,
                    new LinearLayout.LayoutParams(
                            dp(90),
                            dp(30)
                    )
            );

            card.setOnClickListener(v->{
                selectedPackage=item.pkg;
                selectedName=item.name;
                refreshApps();

                info.setText(
                        "已选择： "+item.name+
                        "    → 点击窗口预设启动"
                );
            });

            card.setOnLongClickListener(v->{
                appLongMenu(item);
                return true;
            });

            appGrid.addView(
                    card,
                    new LinearLayout.LayoutParams(
                            dp(100),
                            dp(88)
                    )
            );
        }

        if(apps.isEmpty()){

            TextView empty=text(
                    "点击右上角“＋ 添加 APP”",
                    15
            );

            empty.setGravity(Gravity.CENTER);

            appGrid.addView(
                    empty,
                    new LinearLayout.LayoutParams(
                            dp(260),
                            dp(90)
                    )
            );
        }
    }

    void appLongMenu(AppItem item){
        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(new String[]{"关闭 APP","删除快捷方式"},(d,w)->{
                    if(w==0) closeApp(item.pkg); else deleteApp(item);
                }).show();
    }

    void closeApp(String pkg){
        try{
            android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
            am.killBackgroundProcesses(pkg);
            Toast.makeText(this,"已请求关闭："+selectedOrName(pkg),Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            Toast.makeText(this,"无法直接关闭："+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    String selectedOrName(String pkg){
        for(AppItem a:apps) if(a.pkg.equals(pkg)) return a.name;
        return pkg;
    }

    void deleteApp(AppItem item){

        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setMessage("删除这个 APP 快捷方式？")
                .setNegativeButton("取消",null)
                .setPositiveButton("删除",(d,w)->{

                    apps.remove(item);

                    if(item.pkg.equals(selectedPackage)){
                        selectedPackage=null;
                        selectedName=null;
                    }

                    saveApps();
                    refresh();
                })
                .show();
    }

    void chooseApp(){

        PackageManager pm=getPackageManager();
        ArrayList<ApplicationInfo> list=new ArrayList<>();

        for(ApplicationInfo ai:
                pm.getInstalledApplications(
                        PackageManager.GET_META_DATA)){

            if(!ai.packageName.equals(getPackageName())
                    && pm.getLaunchIntentForPackage(
                            ai.packageName)!=null){

                list.add(ai);
            }
        }

        Collections.sort(
                list,
                (a,b)->pm.getApplicationLabel(a)
                        .toString()
                        .compareToIgnoreCase(
                                pm.getApplicationLabel(b)
                                        .toString()
                        )
        );

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        EditText search=field("搜索 APP","");

        box.addView(
                search,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(50)
                )
        );

        LinearLayout rows=new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll=new ScrollView(this);
        scroll.addView(rows);

        box.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(430)
                )
        );

        AlertDialog dialog=
                new AlertDialog.Builder(this)
                        .setTitle("添加 APP")
                        .setView(box)
                        .setNegativeButton("关闭",null)
                        .create();

        Runnable refreshList=()->{

            rows.removeAllViews();

            String q=search.getText()
                    .toString()
                    .trim()
                    .toLowerCase();

            int count=0;

            for(ApplicationInfo ai:list){

                String name=pm
                        .getApplicationLabel(ai)
                        .toString();

                if(!q.isEmpty()
                        && !name.toLowerCase()
                                .contains(q))
                    continue;

                Button b=button(name);

                b.setGravity(
                        Gravity.LEFT|
                        Gravity.CENTER_VERTICAL
                );

                b.setOnClickListener(v->{

                    boolean exists=false;

                    for(AppItem a:apps){
                        if(a.pkg.equals(ai.packageName)){
                            exists=true;
                            break;
                        }
                    }

                    if(!exists){
                        apps.add(
                                new AppItem(
                                        ai.packageName,
                                        name
                                )
                        );
                        saveApps();
                    }

                    selectedPackage=ai.packageName;
                    selectedName=name;

                    refresh();
                    dialog.dismiss();
                });

                rows.addView(
                        b,
                        new LinearLayout.LayoutParams(
                                -1,
                                dp(48)
                        )
                );

                if(++count>=40)
                    break;
            }
        };

        search.addTextChangedListener(
                new android.text.TextWatcher(){

                    public void beforeTextChanged(
                            CharSequence s,
                            int a,
                            int b,
                            int c){}

                    public void onTextChanged(
                            CharSequence s,
                            int a,
                            int b,
                            int c){
                        refreshList.run();
                    }

                    public void afterTextChanged(
                            android.text.Editable e){}
                }
        );

        refreshList.run();
        dialog.show();
    }

    void showSettings(){

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        EditText dpi=field(
                "容器 DPI",
                String.valueOf(containerDpi)
        );

        EditText top=field(
                "容器顶部空白 px",
                String.valueOf(topBlank)
        );

        EditText bottom=field(
                "容器底部空白 px",
                String.valueOf(bottomBlank)
        );

        box.addView(dpi);
        box.addView(top);
        box.addView(bottom);

        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        android.view.Display display=getWindowManager().getDefaultDisplay();
        android.graphics.Point realSize=new android.graphics.Point();
        display.getRealSize(realSize);
        TextView real=text(
                "当前区域真实分辨率："+realSize.x+" × "+realSize.y+" px\n"+
                "当前系统 Density："+dm.densityDpi+" DPI\n"+
                "默认安全区：上 80 px / 下 120 px", 13);
        real.setTextColor(Color.WHITE);
        real.setPadding(dp(4),dp(8),dp(4),dp(4));
        box.addView(real);
        TextView hint=text(
                "顶部/底部空白只控制本容器界面，用于避开车机状态栏和底部按钮。",12);

        hint.setPadding(
                dp(4),dp(8),dp(4),dp(4)
        );

        box.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle("容器设置")
                .setView(box)
                .setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{

                    containerDpi=Math.max(
                            1,
                            number(dpi,160)
                    );

                    topBlank=Math.max(
                            0,
                            number(top,0)
                    );

                    bottomBlank=Math.max(
                            0,
                            number(bottom,0)
                    );

                    prefs.edit()
                            .putInt(
                                    "containerDpi",
                                    containerDpi
                            )
                            .putInt(
                                    "topBlank",
                                    topBlank
                            )
                            .putInt(
                                    "bottomBlank",
                                    bottomBlank
                            )
                            .apply();

                    refresh();
                })
                .show();
    }

    void presetMenu(int index){

        Preset p=presets.get(index);

        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setItems(
                        new String[]{
                                "编辑预设",
                                "删除预设"
                        },
                        (d,w)->{

                            if(w==0){
                                editPreset(index);
                            }else{

                                if(presets.size()<=1){
                                    Toast.makeText(
                                            this,
                                            "至少保留一个预设",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    return;
                                }

                                presets.remove(index);
                                savePresets();
                                refresh();
                            }
                        }
                )
                .show();
    }

    void editPreset(int index){

        Preset old;

        if(index>=0){
            old=presets.get(index);
        }else{
            old=new Preset(
                    "",
                    0,
                    0,
                    AREA_WIDTH,
                    AREA_HEIGHT,
                    containerDpi
            );
        }

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        EditText name=field(
                "预设名称",
                old.name
        );

        EditText x=field(
                "X 左上位置",
                String.valueOf(old.x)
        );

        EditText y=field(
                "Y 上下位置",
                String.valueOf(old.y)
        );

        EditText width=field(
                "窗口宽度",
                String.valueOf(old.w)
        );

        EditText height=field(
                "窗口高度",
                String.valueOf(old.h)
        );

        EditText dpi=field(
                "APP DPI",
                String.valueOf(old.dpi)
        );

        box.addView(name);
        box.addView(x);
        box.addView(y);
        box.addView(width);
        box.addView(height);
        box.addView(dpi);

        TextView hint=text(
                "默认单区域大小：2032 × 960\n"+
                "X/Y 为 APP 窗口左上角位置。",
                12
        );

        box.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle(
                        index>=0
                                ?"编辑窗口预设"
                                :"新建窗口预设"
                )
                .setView(box)
                .setNegativeButton("取消",null)
                .setPositiveButton("保存",(d,w)->{

                    String n=name.getText()
                            .toString()
                            .trim();

                    if(n.isEmpty()){
                        Toast.makeText(
                                this,
                                "请输入预设名称",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    Preset p=new Preset(
                            n,
                            number(x,0),
                            number(y,0),
                            Math.max(
                                    1,
                                    number(width,AREA_WIDTH)
                            ),
                            Math.max(
                                    1,
                                    number(height,AREA_HEIGHT)
                            ),
                            Math.max(
                                    1,
                                    number(dpi,containerDpi)
                            )
                    );

                    if(index>=0)
                        presets.set(index,p);
                    else
                        presets.add(p);

                    savePresets();
                    refresh();
                })
                .show();
    }

    void launchApp(Preset p){

        Intent intent=
                getPackageManager()
                        .getLaunchIntentForPackage(
                                selectedPackage
                        );

        if(intent==null){
            Toast.makeText(
                    this,
                    "无法启动 APP",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        ActivityOptions options=
                ActivityOptions.makeBasic();

        // launchBounds 使用屏幕坐标；用当前容器窗口可见区域顶部偏移修正车机状态栏造成的上下位移。
        android.graphics.Rect visible=new android.graphics.Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int screenTop=visible.top;
        options.setLaunchBounds(
                new android.graphics.Rect(
                        p.x,
                        p.y-screenTop,
                        p.x+p.w,
                        p.y+p.h-screenTop
                )
        );

        info.setText(
                "启动："+selectedName+
                "\n预设："+p.name+
                "   "+p.w+" × "+p.h+
                "   DPI "+p.dpi
        );

        try{
            startActivity(
                    intent,
                    options.toBundle()
            );
        }catch(Exception e){

            info.setText(
                    "启动失败："+e.getMessage()
            );

            try{
                startActivity(intent);
            }catch(Exception ignored){}
        }
    }
}
