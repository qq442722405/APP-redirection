package com.acc.acc;

import android.app.Dialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import org.json.*;
import java.util.*;

/** Binds real launcher actions to the editable, stable eN IDs in the design JSON. */
final class LauncherDesign {
    final MainActivity a;
    DesignSurface main;
    View presetList,appList;
    LauncherDesign(MainActivity activity){a=activity;}
    DesignSurface page(String name){return new DesignSurface(a,name);}
    void toast(String message){Toast.makeText(a,message,Toast.LENGTH_SHORT).show();}
    Button button(String title){Button b=new Button(a);b.setText(title);return b;}
    void show(AlertDialog dialog,DesignSurface page){
        dialog.setTitle(null);dialog.setView(page,0,0,0,0);dialog.show();
        Window w=dialog.getWindow();if(w==null)return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.getDecorView().setPadding(0,0,0,0);
        w.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        View root=a.getWindow().getDecorView();
        int width=root.getWidth()>0?root.getWidth():a.getRealScreenSize().x;
        int height=root.getHeight()>0?root.getHeight():a.getRealScreenSize().y;
        float s=Math.min(1f,Math.min(width/page.designWidth,height/page.designHeight));
        page.maxScale=s;
        WindowManager.LayoutParams lp=w.getAttributes();lp.gravity=Gravity.CENTER;
        lp.x=0;lp.y=0;lp.width=Math.round(page.designWidth*s);lp.height=Math.round(page.designHeight*s);
        lp.dimAmount=.55f;w.setAttributes(lp);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    AlertDialog dialog(DesignSurface p){return new AlertDialog.Builder(a).setView(p).create();}
    void buildMain(){
        main=page("主界面");
        main.maxScale=a.prefs.getFloat("design_ui_scale",1f);
        main.fontFactor=a.prefs.getFloat("design_font_scale",1f);
        a.mainFrame=main;
        String wallpaper=a.prefs.getString("design_wallpaper","");
        if(!wallpaper.isEmpty())try{
            ImageView bg=new ImageView(a);bg.setScaleType(ImageView.ScaleType.CENTER_CROP);bg.setImageURI(Uri.parse(wallpaper));
            main.place(bg,DesignSurface.rect(0,0,main.designWidth,main.designHeight,16));
            for(View v:new ArrayList<>(main.placed.keySet()))if(v!=bg)v.bringToFront();
        }catch(Exception ignored){}
        main.action(1,()->a.editPreset(-1));main.action(8,a::chooseApp);
        a.presetCategoryButtons=new Button[3];
        for(int i=0;i<3;i++){
            final int cat=i;Button b=main.action(4+i,()->{a.presetCategoryFilter=cat;refreshMain();});a.presetCategoryButtons[i]=b;
        }
        main.action(31,a::showFloatingPresetPicker);
        main.action(12,()->a.controlSelectedApp(false));main.action(13,()->a.controlSelectedApp(true));
        main.action(14,a::controlHome);main.action(15,a::showSettingsMenu);
        TextView screenInfo=new TextView(a);a.updateScreenInfo(screenInfo);
        screenInfo.setSingleLine(true);screenInfo.setEllipsize(android.text.TextUtils.TruncateAt.END);
        main.bind(30,screenInfo);
        // The metadata example has a short placeholder width; reveal the full value on tap.
        screenInfo.setOnClickListener(v->a.showScreenDiagnostics());
        a.info=main.label("",510,765,1250,35,22);
        a.getWindow().setStatusBarColor(Color.BLACK);a.getWindow().setNavigationBarColor(Color.BLACK);
        a.setContentView(main);refreshMain();
    }
    void removeList(View view){if(view!=null){main.placed.remove(view);main.removeView(view);}}
    View mountList(DesignSurface items,RectF box){
        DesignSurface.PixelScroll scroll=new DesignSurface.PixelScroll(a,items);
        main.place(scroll,DesignSurface.rect(box.left,box.top,box.width(),box.height(),16));return scroll;
    }
    DesignSurface card(float width,float height,String title,String detail,Drawable icon,boolean selected,float font){
        DesignSurface c=new DesignSurface(a,width,height);c.setBackground(selected?DesignSurface.background(true):DesignSurface.buttonBackground());
        if(icon!=null){ImageView iv=new ImageView(a);iv.setImageDrawable(icon);iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            c.place(iv,DesignSurface.rect(width*.24f,12,width*.52f,height*.50f,16));}
        TextView name=c.label(title,8,icon!=null?height*.61f:height*.16f,width-16,height*.32f,font);
        name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if(detail!=null){TextView d=c.label(detail,7,height*.55f,width-14,height*.35f,font*.60f);d.setGravity(Gravity.CENTER);d.setMaxLines(2);}
        c.setContentDescription(title);return c;
    }
    void refreshMain(){
        if(main==null)return;
        for(int i=0;i<3;i++)main.selected(a.presetCategoryButtons[i],i==a.presetCategoryFilter);
        removeList(presetList);removeList(appList);
        int[] presetSlots={7,23,24,25,26,27,28,29};int[] appSlots={9,16,17,18,19,20,21,22};
        RectF pb=main.bounds(presetSlots),ab=main.bounds(appSlots);
        ArrayList<Integer> visible=new ArrayList<>();for(int i=0;i<a.presets.size();i++)if(a.presets.get(i).category==a.presetCategoryFilter)visible.add(i);
        float pitch=(float)(main.spec(23).optDouble("left")-main.spec(7).optDouble("left"));
        DesignSurface ps=new DesignSurface(a,Math.max(pb.width(),visible.size()*pitch),pb.height());
        for(int n=0;n<visible.size();n++){
            final int index=visible.get(n);MainActivity.Preset p=a.presets.get(index);
            JSONObject slot=main.spec(presetSlots[n%presetSlots.length]);
            float w=(float)slot.optDouble("width"),h=(float)slot.optDouble("height");
            DesignSurface c=card(w,h,p.name,p.w+" × "+p.h+"\n左 "+p.x+"  上 "+p.y,null,false,(float)slot.optDouble("fontSize",35));
            c.setOnClickListener(v->{if(a.selectedPackage!=null)a.launchApp(p);else a.editPreset(index);});a.setMainItemLongClick(c,0,index);
            ps.place(c,DesignSurface.rect((float)slot.optDouble("left")-pb.left+(n/presetSlots.length)*presetSlots.length*pitch,(float)slot.optDouble("top")-pb.top,w,h,35));
        }
        if(visible.isEmpty())ps.label("点击 + 新建窗口预设",0,0,500,pb.height(),30);
        presetList=mountList(ps,pb);
        int columns=Math.max(1,a.prefs.getInt("design_app_columns",8));
        int rows=Math.max(1,(a.apps.size()+columns-1)/columns);
        float appPitch=(float)(main.spec(16).optDouble("left")-main.spec(9).optDouble("left"));
        DesignSurface grid=new DesignSurface(a,Math.max(ab.width(),columns*appPitch-20),rows*(ab.height()+20)-20);
        for(int n=0;n<a.apps.size();n++){
            final int index=n;MainActivity.AppItem item=a.apps.get(n);Drawable icon=null;
            try{icon=a.getPackageManager().getApplicationIcon(item.pkg);}catch(Exception ignored){}
            JSONObject slot=main.spec(appSlots[(n%columns)%appSlots.length]);
            float w=(float)slot.optDouble("width"),h=(float)slot.optDouble("height");
            DesignSurface tile=card(w,h,item.name,null,icon,item.pkg.equals(a.selectedPackage),(float)slot.optDouble("fontSize",35));
            tile.setOnClickListener(v->{if(item.pkg.startsWith("action:")){runAction(item.pkg.substring(7));return;}long now=System.currentTimeMillis();boolean twice=a.lastMainAppTapIndex==index&&now-a.lastMainAppTapTime<=420;
                a.lastMainAppTapIndex=index;a.lastMainAppTapTime=now;a.selectedPackage=item.pkg;a.selectedName=item.name;
                a.prefs.edit().putString("selected_control_package",item.pkg).apply();a.info.setText("当前 APP："+item.name);
                if(twice)launchSelection(item.pkg,item.name);else refreshMain();});
            a.setMainItemLongClick(tile,1,index);
            float x=(float)slot.optDouble("left")-ab.left+((n%columns)/appSlots.length)*appSlots.length*appPitch;
            grid.place(tile,DesignSurface.rect(x,(float)slot.optDouble("top")-ab.top+(n/columns)*(ab.height()+20),w,h,35));
        }
        if(a.apps.isEmpty())grid.label("点击 + 添加 APP",0,0,500,ab.height(),30);
        appList=mountList(grid,ab);
    }
    void launchSelection(String pkg,String name){
        if(pkg.startsWith("action:")){runAction(pkg.substring(7));return;}
        String presetName=a.prefs.getString("design_app_preset_"+pkg,"");
        for(MainActivity.Preset p:a.presets)if(p.name.equals(presetName)){a.launchApp(p);return;}
        a.launchAppDirect(pkg,name);
    }
    void runAction(String action){
        if(!a.isAccessibilityServiceEnabled()){a.openAccessibilitySettings();return;}
        if("back".equals(action))AccessibilityServiceBridge.perform(a,1);
        else if("home".equals(action))a.controlHome();
        else if("menu".equals(action))AccessibilityServiceBridge.perform(a,3);
        else if("close".equals(action))a.controlSelectedApp(true);
    }
    void settings(){
        DesignSurface p=page("设置");AlertDialog d=dialog(p);
        Switch boot=new Switch(a),tasks=new Switch(a);
        boot.setChecked(a.prefs.getBoolean("app_boot_enabled",false));tasks.setChecked(a.prefs.getBoolean("auto_start_enabled",false));
        p.bind(5,boot);p.bind(7,tasks);
        p.action(8,a::showInterfaceOptionsDialog);p.action(9,a::showAutoStartEditor);p.action(10,a::showScreenDiagnostics);
        p.action(11,a::exportConfig);p.action(12,a::importConfig);p.action(13,a::showFloatingWindowSettingsDialog);
        p.action(3,d::dismiss);p.action(4,()->{a.prefs.edit().putBoolean("app_boot_enabled",boot.isChecked()).putBoolean("auto_start_enabled",tasks.isChecked()).apply();d.dismiss();});
        show(d,p);
    }
    void interfaceOptions(){
        DesignSurface p=page("界面选项");AlertDialog d=dialog(p);
        EditText delay=a.numberField("秒",String.valueOf(a.prefs.getInt("boot_delay_seconds",0)));
        EditText font=a.numberField("%",String.valueOf(Math.round(a.prefs.getFloat("design_font_scale",1f)*100)));
        EditText size=a.numberField("%",String.valueOf(Math.round(a.prefs.getFloat("design_ui_scale",1f)*100)));
        EditText columns=a.numberField("个",String.valueOf(a.prefs.getInt("design_app_columns",8)));
        EditText wallpaper=a.textField("图片路径 / content URI",a.prefs.getString("design_wallpaper",""));
        p.bind(5,delay);p.bind(7,font);p.bind(9,size);p.bind(11,columns);p.bind(13,wallpaper);
        wallpaper.setOnLongClickListener(v->{a.pickDesignWallpaper(wallpaper);return true;});
        p.action(3,d::dismiss);p.action(4,()->{
            int de=a.number(delay,-1),fs=a.number(font,-1),us=a.number(size,-1),co=a.number(columns,-1);
            if(de<0||de>3600||fs<20||fs>300||us<50||us>300||co<1||co>20){toast("请输入有效数值：延迟 0–3600 秒，字体 20–300%，界面 50–300%，每排 1–20 个");return;}
            a.prefs.edit().putInt("boot_delay_seconds",de).putFloat("design_font_scale",fs/100f).putFloat("design_ui_scale",us/100f)
                .putInt("design_app_columns",co).putString("design_wallpaper",wallpaper.getText().toString().trim()).apply();
            d.dismiss();buildMain();toast("设置已保存");
        });show(d,p);
    }

    void chooseApp(){
        DesignSurface p=page("添加APP");AlertDialog d=dialog(p);
        EditText search=a.textField("搜索 APP","");p.bind(8,search);
        Spinner preset=new Spinner(a);ArrayList<String> names=new ArrayList<>();names.add("直接启动");
        for(MainActivity.Preset item:a.presets)names.add(item.name);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,names){
            @Override public View getView(int position,View old,ViewGroup parent){
                TextView label=(TextView)super.getView(position,old,parent);label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(float)p.spec(23).optDouble("fontSize",16)*p.scale);
                label.setTextColor(Color.WHITE);label.setPadding(0,0,0,0);label.setSingleLine(true);label.setIncludeFontPadding(false);return label;
            }
        };preset.setAdapter(adapter);p.bind(23,preset);
        final String[] selected={"",""};final int[] category={0};final View[] current={null};final Runnable[] refresh={null};
        int[] tabs={9,10,11,24};Button[] tabButtons=new Button[4];
        ArrayList<ApplicationInfo> installed=new ArrayList<>(a.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA));
        Collections.sort(installed,(x,y)->a.getAppLabelSafe(x.packageName).compareToIgnoreCase(a.getAppLabelSafe(y.packageName)));
        for(int i=0;i<tabs.length;i++){final int index=i;tabButtons[i]=p.action(tabs[i],()->{category[0]=index;refresh[0].run();});}
        refresh[0]=()->{
            if(current[0]!=null){p.placed.remove(current[0]);p.removeView(current[0]);}
            for(int i=0;i<tabs.length;i++)p.selected(tabButtons[i],category[0]==i);
            ArrayList<String[]> choices=new ArrayList<>();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            if(category[0]==3){
                String[][] actions={{"action:back","返回"},{"action:home","首页"},{"action:menu","最近任务"},{"action:close","关闭当前 APP"}};
                for(String[] item:actions)if(query.isEmpty()||item[1].contains(query))choices.add(item);
            }else for(ApplicationInfo app:installed){
                if(app.packageName.equals(a.getPackageName()))continue;
                boolean system=(app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
                if(category[0]==0&&system||category[0]==1&&!system)continue;
                String name=a.getAppLabelSafe(app.packageName);
                if(!query.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(query)&&!app.packageName.toLowerCase(Locale.ROOT).contains(query))continue;
                choices.add(new String[]{app.packageName,name});
            }
            RectF bounds=p.bounds(12,13,14,15,16,17,18,19,20,21);
            float rowPitch=(float)(p.spec(17).optDouble("top")-p.spec(12).optDouble("top"));
            DesignSurface grid=new DesignSurface(a,bounds.width(),Math.max(bounds.height(),((choices.size()+4)/5)*rowPitch-20));
            for(int i=0;i<choices.size();i++){
                String[] item=choices.get(i);JSONObject slot=p.spec(12+i%10);Drawable icon=null;
                try{icon=a.getPackageManager().getApplicationIcon(item[0]);}catch(Exception ignored){}
                float width=(float)slot.optDouble("width"),height=(float)slot.optDouble("height");
                DesignSurface tile=card(width,height,item[1],null,icon,item[0].equals(selected[0]),(float)slot.optDouble("fontSize",16));
                tile.setOnClickListener(v->{selected[0]=item[0];selected[1]=item[1];refresh[0].run();});
                grid.place(tile,DesignSurface.rect((float)slot.optDouble("left")-bounds.left,(float)slot.optDouble("top")-bounds.top+(i/10)*rowPitch*2,width,height,16));
            }
            if(choices.isEmpty())grid.label("没有找到 APP",0,0,bounds.width(),100,28);
            DesignSurface.PixelScroll list=new DesignSurface.PixelScroll(a,grid);p.place(list,DesignSurface.rect(bounds.left,bounds.top,bounds.width(),bounds.height(),16));current[0]=list;
        };
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(android.text.Editable s){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh[0].run();}});
        p.action(6,d::dismiss);
        p.action(7,()->{
            if(selected[0].isEmpty()){toast("请先选择 APP 或按钮");return;}
            boolean exists=false;for(MainActivity.AppItem item:a.apps)if(item.pkg.equals(selected[0]))exists=true;
            if(!exists)a.apps.add(new MainActivity.AppItem(selected[0],selected[1]));
            a.prefs.edit().putString("design_app_preset_"+selected[0],preset.getSelectedItemPosition()==0?"":names.get(preset.getSelectedItemPosition())).apply();
            a.saveApps();a.refresh();d.dismiss();
        });
        p.action(25,()->{selected[0]="";selected[1]="";preset.setSelection(0);refresh[0].run();});
        p.action(4,()->{try{
            JSONObject data=new JSONObject();data.put("type","launcher_app_choice");data.put("pkg",selected[0]);data.put("name",selected[1]);data.put("preset",names.get(preset.getSelectedItemPosition()));
            ((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("APP 选择",data.toString()));toast("已复制");
        }catch(Exception e){toast("复制失败");}});
        p.action(5,()->{try{
            ClipboardManager cm=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
            if(!cm.hasPrimaryClip())throw new IllegalArgumentException();
            JSONObject data=new JSONObject(cm.getPrimaryClip().getItemAt(0).coerceToText(a).toString());
            if(!"launcher_app_choice".equals(data.optString("type")))throw new IllegalArgumentException();
            String pkg=data.getString("pkg");
            if(!pkg.isEmpty()&&!pkg.startsWith("action:"))a.getPackageManager().getApplicationInfo(pkg,0);
            selected[0]=pkg;selected[1]=data.optString("name",a.getAppLabelSafe(pkg));
            preset.setSelection(Math.max(0,names.indexOf(data.optString("preset","直接启动"))));refresh[0].run();toast("已粘贴");
        }catch(Exception e){toast("剪贴板不是有效的 APP 选择，或 APP 尚未安装");}});
        refresh[0].run();show(d,p);
    }

    void autoTasks(){
        DesignSurface p=page("自动启动项目");p.hide(8);p.hide(9);AlertDialog d=dialog(p);
        final JSONArray[] tasks={a.loadAutoTasks()};final View[] old={null};final Runnable[] refresh={null};
        EditText interval=a.numberField("秒",String.valueOf(a.prefs.getInt("auto_start_interval",1)));p.bind(5,interval);
        refresh[0]=()->{
            if(old[0]!=null){p.placed.remove(old[0]);p.removeView(old[0]);}
            RectF b=p.bounds(7,8,9,10);float rowHeight=b.height()+24;
            DesignSurface list=new DesignSurface(a,b.width(),Math.max(300,tasks[0].length()*rowHeight));
            for(int i=0;i<tasks[0].length();i++){
                final int index=i;JSONObject task=tasks[0].optJSONObject(i);if(task==null)continue;
                int pi=task.optInt("preset",-1);String pn=pi>=0&&pi<a.presets.size()?a.presets.get(pi).name:"直接启动";
                for(int id:new int[]{7,8,9,10}){
                    JSONObject slot=p.spec(id);View child;
                    if(id==10){Button del=button("删除");del.setOnClickListener(v->{tasks[0].remove(index);refresh[0].run();});child=del;}
                    else {TextView text=new TextView(a);text.setText(id==7?String.valueOf(i+1):id==8?task.optString("name","APP"):pn);text.setMaxLines(1);text.setEllipsize(android.text.TextUtils.TruncateAt.END);child=text;}
                    list.place(child,DesignSurface.rect((float)slot.optDouble("left")-b.left,(float)slot.optDouble("top")-b.top+i*rowHeight,(float)slot.optDouble("width"),(float)slot.optDouble("height"),(float)slot.optDouble("fontSize",16)));
                }
            }
            if(tasks[0].length()==0)list.label("暂无任务，点击右上角添加",0,0,b.width(),90,26);
            DesignSurface.PixelScroll scroll=new DesignSurface.PixelScroll(a,list);p.place(scroll,DesignSurface.rect(b.left,b.top,b.width(),340,16));old[0]=scroll;
        };
        Button add=button("＋ 添加任务");add.setOnClickListener(v->a.showAddAutoTaskDialog(tasks,refresh[0]));p.place(add,DesignSurface.rect(710,30,200,44,20));
        p.action(2,d::dismiss);p.action(3,()->{
            int seconds=a.number(interval,-1);if(seconds<1||seconds>3600){toast("任务间隔请输入 1–3600 秒");return;}
            a.prefs.edit().putString("auto_start_items",tasks[0].toString()).putInt("auto_start_interval",seconds).apply();d.dismiss();toast("自动启动项目已保存");
        });refresh[0].run();show(d,p);
    }
}
