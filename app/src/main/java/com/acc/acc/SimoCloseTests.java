package com.acc.acc;

import android.content.*;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import java.util.*;

/** User-triggered, single-target experiments; never cycles through methods automatically. */
final class SimoCloseTests {
    private static final Handler timer=new Handler(Looper.getMainLooper());
    private static Runnable pending;
    private static String pendingText="无待执行测试";
    private static void cancel(){if(pending!=null){timer.removeCallbacks(pending);pending=null;CloseTestLog.add("手动测试","待执行测试已取消");}pendingText="无待执行测试";}
    static void show(MainActivity a){
        DesignSurface p=a.design.page("SIMO关闭测试");AlertDialog d=a.design.dialog(p);
        String[] target={a.selectedPackage==null?"":a.selectedPackage};
        Button app=p.action(2,()->choose(a,target));
        p.action(3,()->CloseTestLog.add("窗口检查",AccessibilityServiceBridge.inspect(target[0])));
        Button method=p.action(4,()->{
            if(!SimoClose.allowed(a,target[0],a.design::toast))return;
            new InAppDialog.Builder(a).setTitle("仅对当前 APP 测试语音关闭；重启恢复原方式")
                .setItems(CloseTestModes.LABELS,(dialog,index)->{
                    SimoClose.testPackage=target[0];SimoClose.testMode=CloseTestModes.IDS[index];
                    CloseTestLog.add("测试方式",target[0]+" → "+CloseTestModes.LABELS[index]);
                }).show();
        });
        p.action(6,()->schedule(a,target[0],"back"));p.action(7,()->schedule(a,target[0],"back_twice"));
        p.action(8,()->schedule(a,target[0],"gesture"));p.action(9,()->schedule(a,target[0],"slow"));
        p.action(10,()->area(a,1));p.action(11,()->area(a,2));
        p.action(14,d::dismiss);
        p.action(15,()->{
            String report="SIMO 关闭测试\n目标："+target[0]+"\n语音方式："+CloseTestModes.label(SimoClose.methodFor(target[0]))+"\n总开关="+a.prefs.getBoolean("simo_enabled",false)+" 关闭开关="+a.prefs.getBoolean("simo_close_enabled",false)+" APP被排除="+a.prefs.getBoolean("simo_exclude_"+target[0],false)+"\n"+summary()+"\n最近结果："+SimoVoiceService.lastHit+"\n"+AccessibilityServiceBridge.inspect(target[0])+"\n"+CloseTestLog.text();
            ((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SIMO关闭测试结果",report));a.design.toast("结果已复制，可粘贴反馈");
        });
        p.action(16,CloseTestLog::clear);p.action(17,SimoCloseTests::cancel);
        p.action(18,()->{SimoClose.testPackage="";SimoClose.testMode="gesture";CloseTestLog.add("测试方式","已恢复全部 APP 的原三指方式");});
        p.action(19,()->{cancel();openTarget(a,target[0]);});
        TextView state=(TextView)p.bound.get("e12"),log=(TextView)p.bound.get("e13");
        log.setMovementMethod(new android.text.method.ScrollingMovementMethod());log.setTextIsSelectable(true);
        state.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        a.design.show(d,p);Handler updates=new Handler(Looper.getMainLooper());
        Runnable update=new Runnable(){String previous="";public void run(){if(!d.isShowing())return;
            app.setText(target[0].isEmpty()?"选择测试 APP":"目标："+a.getAppLabelSafe(target[0])+" ▾");
            method.setText("语音关闭测试方式："+CloseTestModes.label(SimoClose.methodFor(target[0]))+" ▾");
            state.setText(summary()+"\n总开关="+a.prefs.getBoolean("simo_enabled",false)+" 关闭开关="+a.prefs.getBoolean("simo_close_enabled",false)+" APP被排除="+a.prefs.getBoolean("simo_exclude_"+target[0],false)+"\n"+pendingText+"；无障碍："+(AccessibilityServiceBridge.connected()?"已连接":"未连接")+"\n"+SimoVoiceService.lastHit);
            String text=CloseTestLog.text();if(!text.equals(previous)){log.setText(text.isEmpty()?"暂无记录。先选“只记录语音”，对 SIMO 说“关闭＋APP 名称/别名”。":text);previous=text;}
            updates.postDelayed(this,500);
        }};updates.post(update);
        d.setOnDismissListener(dialog->{updates.removeCallbacksAndMessages(null);cancel();a.design.openDialogs.remove(d);});
    }
    private static String summary(){return SimoVoiceService.status+"；收到回调 "+SimoVoiceService.callbackCount+"，匹配关闭 "+SimoVoiceService.closeMatchCount;}
    private static void choose(MainActivity a,String[] target){
        List<VoiceCommands.App> apps=SimoApps.list(a,true);ArrayList<VoiceCommands.App> eligible=new ArrayList<>();
        for(VoiceCommands.App app:apps)if(SimoClose.allowed(a,app.pkg,message->{}))eligible.add(app);
        if(eligible.isEmpty()){a.design.toast("请先在主界面添加第三方 APP，或在 SIMO 设置保存全部第三方 APP 范围");return;}
        String[] names=new String[eligible.size()];for(int i=0;i<names.length;i++)names[i]=eligible.get(i).name+(eligible.get(i).alias.isEmpty()?"":"（"+eligible.get(i).alias+"）");
        new InAppDialog.Builder(a).setTitle("选择本次测试的 APP").setItems(names,(dialog,index)->{cancel();target[0]=eligible.get(index).pkg;if(!target[0].equals(SimoClose.testPackage)){SimoClose.testPackage="";SimoClose.testMode="gesture";}CloseTestLog.add("目标",names[index]+" / "+target[0]);}).show();
    }
    private static void schedule(MainActivity a,String pkg,String mode){
        if(!SimoClose.allowed(a,pkg,a.design::toast))return;
        if(!AccessibilityServiceBridge.connected()){a.design.toast("请先启用启动器的无障碍服务");a.openAccessibilitySettings();return;}
        cancel();
        if(!openTarget(a,pkg))return;
        CloseTestLog.add("手动测试","已打开 "+pkg+"；5 秒后执行 "+CloseTestModes.label(mode));
        long due=SystemClock.elapsedRealtime()+5000;
        pending=new Runnable(){public void run(){
            if(pending!=this)return;if(a.isDestroyed()||a.isFinishing()){cancel();return;}
            long remaining=due-SystemClock.elapsedRealtime();
            if(remaining>0){pendingText=(remaining+999)/1000+" 秒后："+CloseTestModes.label(mode);timer.postDelayed(this,500);return;}
            pending=null;pendingText="本次手动测试已提交";
            CloseTestLog.add("执行前",AccessibilityServiceBridge.inspect(pkg));
            SimoClose.run(a.getApplicationContext(),pkg,mode,message->CloseTestLog.add("手动执行",message));
        }};timer.post(pending);
    }
    private static boolean openTarget(MainActivity a,String pkg){
        if(!SimoClose.allowed(a,pkg,a.design::toast))return false;
        try{
            Intent launch=a.getPackageManager().getLaunchIntentForPackage(pkg);if(launch==null)throw new IllegalArgumentException("APP 没有启动入口");
            WindowLaunch.activateForControl(a,pkg,launch);
            // Remove this test page from the gesture path while the target is in front.
            a.moveTaskToBack(true);
            return true;
        }catch(Exception e){CloseTestLog.add("手动测试","无法显示目标："+e.getMessage());a.design.toast("目标 APP 未打开，请查看记录");return false;}
    }
    private static void area(MainActivity a,int area){
        String region=area==1?"中屏":"右屏";
        new InAppDialog.Builder(a).setTitle(region+"返回桌面测试")
            .setMessage("此按钮通过原软件使用的 CAP 接口，让整个"+region+"返回桌面，不限定刚才选择的 APP，也不等于强制停止进程。执行后请观察该屏，再回本页复制结果。")
            .setNegativeButton("取消",null).setPositiveButton("执行"+region+"测试",(dialog,which)->{cancel();CapHomeTest.start(a,area);}).show();
    }
}
