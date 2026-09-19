package com.acc.acc;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.Display;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import java.io.*;
import java.util.*;

final class LabUi {
    static final int EXPORT=19301;
    static final String[] KEYS={"left","center","right","rear","custom"},NAMES={"左屏","中屏","右屏","后排屏","自定义窗口"};
    static final String[] CLOSE_KEYS={"back1","back2","kill","force"},CLOSE_NAMES={"返回一次","连续返回两次","清理后台进程","强制停止接口"};
    private static String pendingReport;
    static void show(MainActivity a){
        LabSession.open(a);DesignSurface p=a.design.page("功能测试");AlertDialog d=a.design.dialog(p);
        p.action(3,()->chooseApp(a));p.action(5,()->closePage(a));p.action(6,()->windows(a));p.action(7,()->voice(a));p.action(8,()->logs(a));
        p.action(10,()->mark(a,"有效"));p.action(11,()->mark(a,"无效"));p.action(12,()->mark(a,"待定"));p.action(13,d::dismiss);
        p.action(14,()->{LabSession.end(a);a.design.toast("测试已结束");d.dismiss();});p.action(15,()->export(a));
        watch(a,d,p,()->{text(p,4,target());text(p,9,"最近测试："+(LabSession.lastTest.isEmpty()?"暂无":LabSession.lastTest)+"\n"+(LabSession.active?"返回后仍可接收测试口令；完成后请点“结束测试”。":"测试已结束，请重新打开此窗口开始。"));});
    }
    private static void closePage(MainActivity a){
        DesignSurface p=a.design.page("关闭APP测试");AlertDialog d=a.design.dialog(p);
        String[] actions={"back1","back2","kill","force","details"};for(int i=0;i<actions.length;i++){final String action=actions[i];p.action(4+i,()->run(a,action));}
        p.action(11,d::dismiss);p.action(12,()->mark(a,"有效"));p.action(13,()->mark(a,"无效"));
        watch(a,d,p,()->{text(p,3,target());text(p,10,last());});
    }
    private static void windows(MainActivity a){
        DesignSurface p=a.design.page("窗口屏幕测试");AlertDialog d=a.design.dialog(p);
        for(int i=0;i<4;i++){final String key=KEYS[i];p.action(4+i,()->run(a,key));}
        p.action(8,()->profiles(a));p.action(9,()->run(a,"bounds"));p.action(10,()->run(a,"freeform"));p.action(11,()->run(a,"embed"));
        p.action(13,d::dismiss);p.action(14,()->mark(a,"有效"));p.action(15,()->mark(a,"无效"));
        watch(a,d,p,()->{text(p,3,target());text(p,12,last());});
    }
    private static void voice(MainActivity a){
        DesignSurface p=a.design.page("SIMO功能测试");AlertDialog d=a.design.dialog(p);Switch enabled=new Switch(a);enabled.setChecked(LabSession.voiceActive());p.bind(4,enabled);
        enabled.setOnCheckedChangeListener((b,on)->{
            if(on&&(!LabSession.active||LabSession.target==null)){a.design.toast("请先在测试首页选择 APP");enabled.setChecked(false);return;}
            LabSession.voice=on;LabLog.write("SIMO",on?"启用当前 APP 的测试口令":"关闭测试口令");SimoVoiceService.sync(a);
        });
        Button method=p.action(6,()->closeMethod(a));p.action(7,()->alias(a));p.action(10,d::dismiss);p.action(11,()->SimoVoiceService.sync(a));
        TextView commands=(TextView)p.bound.get("e8");commands.setMovementMethod(new android.text.method.ScrollingMovementMethod());commands.setVerticalScrollBarEnabled(true);
        watch(a,d,p,()->{
            text(p,3,target());method.setText("关闭方式："+closeName());StringBuilder phrases=new StringBuilder();
            if(LabSession.voiceActive())for(VoiceCommands.Command c:LabSession.commands())phrases.append(c.phrase).append('\n');
            else phrases.append("开启后显示可喊口令。\n示例：关闭音乐 / 把音乐移到左屏 / 在后排屏打开音乐 / 窗口打开音乐\n左中右/后排与窗口坐标使用测试配置，移动可能被系统忽略。");
            if(!commands.getText().toString().equals(phrases.toString()))commands.setText(phrases.toString());
            text(p,9,SimoVoiceService.status+"\n"+SimoVoiceService.lastHit);
            if(enabled.isChecked()!=LabSession.voiceActive())enabled.setChecked(LabSession.voiceActive());
        });
    }
    private static void logs(MainActivity a){
        DesignSurface p=a.design.page("测试日志");AlertDialog d=a.design.dialog(p);
        p.action(3,()->monitor(a,false));p.action(4,()->monitor(a,true));p.action(5,()->a.stopService(new Intent(a,LabMonitorService.class)));p.action(6,()->export(a));p.action(9,d::dismiss);p.action(10,()->inventory(a));
        TextView log=(TextView)p.bound.get("e8");log.setMovementMethod(new android.text.method.ScrollingMovementMethod());log.setTextIsSelectable(true);log.setVerticalScrollBarEnabled(true);
        watch(a,d,p,()->{text(p,7,LabMonitorService.state+" · 最近测试 "+LabSession.lastTest);String s=LabLog.tail(30);if(!log.getText().toString().equals(s))log.setText(s);});
    }
    private static void monitor(MainActivity a,boolean system){
        if(!LabSession.active){a.design.toast("请重新打开测试窗口");return;}
        if(!"未监听".equals(LabMonitorService.state)){a.design.toast("正在监听，请先停止再切换范围");return;}
        if(system&&a.checkSelfPermission("android.permission.READ_LOGS")!=PackageManager.PERMISSION_GRANTED){LabLog.write("MONITOR","ERROR 未授予 READ_LOGS，无法读取系统日志");a.design.toast("系统日志需要 READ_LOGS 授权；本 APP 日志可直接测试");return;}
        try{a.startForegroundService(new Intent(a,LabMonitorService.class).putExtra("system",system));}catch(RuntimeException e){LabLog.write("MONITOR","ERROR "+LabLog.error(e));a.design.toast("监听启动失败，已记录原因");}
    }
    private static void run(MainActivity a,String action){boolean submitted=LabActions.run(a,action);a.design.toast(submitted?"请求已提交，请实车确认效果":"未执行，请查看测试日志");}
    private static String target(){VoiceCommands.App app=LabSession.target;return app==null?"当前 APP：未选择":"当前 APP："+app.name+(app.alias.isEmpty()?"":"（别名 "+app.alias+"）")+"\n"+app.pkg;}
    private static String last(){return "测试 "+LabSession.lastTest+"\n"+LabLog.tail(2);}
    private static void mark(MainActivity a,String result){if(LabSession.lastTest.isEmpty()){a.design.toast("请先执行一项测试");return;}LabLog.verdict(result);a.design.toast("测试 "+LabSession.lastTest+" 已标记："+result);}
    private static String closeName(){for(int i=0;i<CLOSE_KEYS.length;i++)if(CLOSE_KEYS[i].equals(LabSession.closeMethod))return CLOSE_NAMES[i];return CLOSE_NAMES[1];}
    private static void text(DesignSurface p,int id,String value){((TextView)p.bound.get("e"+id)).setText(value);}
    private static void watch(MainActivity a,AlertDialog d,DesignSurface p,Runnable update){
        a.design.show(d,p);Handler h=new Handler(Looper.getMainLooper());Runnable r=new Runnable(){public void run(){if(!d.isShowing())return;update.run();h.postDelayed(this,800);}};h.post(r);
        d.setOnDismissListener(dialog->{h.removeCallbacksAndMessages(null);a.design.openDialogs.remove(d);});
    }
    private static DesignSurface panel(MainActivity a,String title){DesignSurface p=new DesignSurface(a,1000,700);p.fontFactor=a.mainFontScale();p.setBackground(DesignTheme.panel(a));p.label(title,30,25,940,60,34);return p;}
    private static Button button(MainActivity a,DesignSurface p,String title,int x,int y,int w,int h,Runnable click){Button b=a.design.button(title);p.place(b,DesignSurface.rect(x,y,w,h,24));b.setOnClickListener(v->click.run());return b;}
    private static void chooseApp(MainActivity a){
        DesignSurface p=panel(a,"选择测试 APP");AlertDialog d=a.design.dialog(p);p.label("只列出能启动的第三方 APP。切换目标会取消待执行返回操作。",40,88,920,56,22);
        List<VoiceCommands.App> apps=new ArrayList<>();for(ApplicationInfo info:a.getPackageManager().getInstalledApplications(0))if(LabActions.targetAllowed(a,info.packageName))apps.add(new VoiceCommands.App(info.packageName,String.valueOf(info.loadLabel(a.getPackageManager())),a.getSharedPreferences("lab_settings",0).getString("alias_"+info.packageName,"")));
        apps.sort((x,y)->x.name.compareToIgnoreCase(y.name));DesignSurface rows=new DesignSurface(a,920,Math.max(440,apps.size()*86));
        for(int i=0;i<apps.size();i++){VoiceCommands.App app=apps.get(i);Button b=button(a,rows,app.name+"\n"+app.pkg,0,i*86,910,72,()->{LabSession.select(a,app);d.dismiss();});b.setMaxLines(2);b.setEllipsize(android.text.TextUtils.TruncateAt.END);}
        if(apps.isEmpty())rows.label("没有找到可测试的第三方 APP",0,0,910,100,26);p.list(rows,new RectF(40,154,960,590));button(a,p,"返回",40,622,160,50,d::dismiss);a.design.show(d,p);
    }
    private static void closeMethod(MainActivity a){
        DesignSurface p=panel(a,"SIMO 关闭 APP · 选择测试方式");AlertDialog d=a.design.dialog(p);p.label("返回方式需要无障碍服务。强制停止通常会被权限拒绝。",40,100,920,70,23);
        for(int i=0;i<CLOSE_KEYS.length;i++){final int n=i;button(a,p,CLOSE_NAMES[i],60,195+i*85,870,65,()->{LabSession.closeMethod=CLOSE_KEYS[n];LabLog.write("SIMO","关闭口令方式改为 "+CLOSE_NAMES[n]);d.dismiss();});}
        button(a,p,"返回",40,622,160,50,d::dismiss);a.design.show(d,p);
    }
    private static void alias(MainActivity a){
        VoiceCommands.App target=LabSession.target;if(target==null){a.design.toast("请先选择 APP");return;}
        DesignSurface p=panel(a,"测试语音别名");AlertDialog d=a.design.dialog(p);p.label("例如填写“音乐”。仅用于测试口令，最多60字，不加“打开/关闭”。",40,115,920,100,24);
        EditText value=a.textField("别名",target.alias);value.setSingleLine(true);p.place(value,DesignSurface.rect(40,260,920,70,28));
        button(a,p,"保存",230,622,160,50,()->{String alias=VoiceCommands.normalize(value.getText().toString());if(alias.length()>60){a.design.toast("别名最多60个字符");return;}a.getSharedPreferences("lab_settings",0).edit().putString("alias_"+target.pkg,alias).apply();LabSession.select(a,new VoiceCommands.App(target.pkg,target.name,alias));d.dismiss();});button(a,p,"取消",40,622,160,50,d::dismiss);a.design.show(d,p);
    }
    private static void profiles(MainActivity a){
        DesignSurface p=panel(a,"测试屏幕与窗口参数");AlertDialog d=a.design.dialog(p);p.label("左中右默认三等分主显示器，仅供试验。后排需选择实际 Display ID。",40,92,920,72,22);
        Button[] choices=new Button[KEYS.length];for(int i=0;i<KEYS.length;i++){final int n=i;choices[i]=button(a,p,NAMES[i]+" · "+LabProfile.get(a,KEYS[i]),40,178+i*78,920,64,()->profile(a,KEYS[n],NAMES[n]));}
        button(a,p,"返回",40,622,160,50,d::dismiss);button(a,p,"显示器列表",230,622,220,50,()->inventory(a));watch(a,d,p,()->{for(int i=0;i<KEYS.length;i++)choices[i].setText(NAMES[i]+" · "+LabProfile.get(a,KEYS[i]));});
    }
    private static void profile(MainActivity a,String key,String name){
        DesignSurface p=panel(a,name+" · 测试参数（单位：像素）");AlertDialog d=a.design.dialog(p);LabProfile initial=LabProfile.get(a,key);int[] display={initial.displayId};
        EditText[] fields=new EditText[4];int[] values={initial.x,initial.y,initial.w,initial.h};String[] labels={"X","Y","宽度","高度"};
        for(int i=0;i<4;i++){int x=40+(i%2)*470,y=240+(i/2)*120;p.label(labels[i],x,y,100,60,26);fields[i]=a.numberField(labels[i],String.valueOf(values[i]));p.place(fields[i],DesignSurface.rect(x+115,y,300,60,28));}
        Button pick=button(a,p,"显示器 ID："+display[0]+"（点击选择）",40,115,920,70,()->displayPicker(a,chosen->{display[0]=chosen;Display out=LabProfile.display(a,chosen);Point size=new Point();out.getRealSize(size);fields[0].setText("0");fields[1].setText("0");fields[2].setText(String.valueOf(size.x));fields[3].setText(String.valueOf(size.y));}));
        p.label("选择显示器后先填充全屏尺寸。自行调整；保存时检查越界，不修改正式预设。",40,473,920,95,23);
        button(a,p,"取消",40,622,160,50,d::dismiss);button(a,p,"保存参数",225,622,220,50,()->{try{LabProfile next=new LabProfile(display[0],Integer.parseInt(fields[0].getText().toString()),Integer.parseInt(fields[1].getText().toString()),Integer.parseInt(fields[2].getText().toString()),Integer.parseInt(fields[3].getText().toString()));next.save(a,key);LabLog.write("CONFIG",name+" "+next);d.dismiss();}catch(Exception e){a.design.toast("未保存："+LabLog.error(e));}});
        watch(a,d,p,()->pick.setText("显示器 ID："+display[0]+"（点击选择）"));
    }
    private interface DisplayChoice{void chosen(int id);}
    private static void displayPicker(MainActivity a,DisplayChoice choice){
        DesignSurface p=panel(a,"选择实际显示器");AlertDialog d=a.design.dialog(p);p.label("显示器名称由车机提供。仅列出系统报告的显示器；不猜测后排编号。",40,92,920,70,23);
        List<Display> displays=new ArrayList<>();for(Display display:LabProfile.displays(a))if(!display.getName().startsWith("ACC_LAB_"))displays.add(display);
        DesignSurface rows=new DesignSurface(a,920,Math.max(420,displays.size()*100));for(int i=0;i<displays.size();i++){Display item=displays.get(i);Point size=new Point();item.getRealSize(size);button(a,rows,"ID "+item.getDisplayId()+" · "+item.getName()+"\n"+size.x+"×"+size.y,0,i*100,910,84,()->{choice.chosen(item.getDisplayId());d.dismiss();});}p.list(rows,new RectF(40,172,960,592));button(a,p,"返回",40,622,160,50,d::dismiss);a.design.show(d,p);
    }
    private static void inventory(MainActivity a){DesignSurface p=panel(a,"显示器与权限诊断");AlertDialog d=a.design.dialog(p);String info=LabProfile.inventory(a)+"\nAndroid "+Build.VERSION.RELEASE+" / SDK "+Build.VERSION.SDK_INT+"\n悬浮窗："+android.provider.Settings.canDrawOverlays(a)+"\n前台事件："+AccessibilityServiceBridge.getCurrentPackage()+"\nREAD_LOGS："+(a.checkSelfPermission("android.permission.READ_LOGS")==PackageManager.PERMISSION_GRANTED)+"\n显示器尺寸为设备真实像素；网页字体以160 dpi设计像素显示。";TextView view=p.label(info,40,112,920,470,24);view.setMovementMethod(new android.text.method.ScrollingMovementMethod());LabLog.write("DEVICE",info);button(a,p,"返回",40,622,160,50,d::dismiss);a.design.show(d,p);}
    static void export(MainActivity a){
        pendingReport=LabLog.report(a);try{a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"APP窗口启动器-测试报告-"+System.currentTimeMillis()+".txt"),EXPORT);}catch(RuntimeException e){a.design.toast("无法打开文件保存器："+LabLog.error(e));}
    }
    static void exportResult(MainActivity a,Uri uri){final String report=pendingReport==null?LabLog.report(a):pendingReport;pendingReport=null;new Thread(()->{try(OutputStream out=a.getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("无法打开文件");out.write(report.getBytes("UTF-8"));a.runOnUiThread(()->a.design.toast("测试报告已导出"));}catch(Exception e){LabLog.write("EXPORT","ERROR "+LabLog.error(e));a.runOnUiThread(()->a.design.toast("导出失败，请查看日志"));}},"lab-export").start();}
}
