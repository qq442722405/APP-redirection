package com.acc.acc;

import android.content.*;
import android.os.*;
import java.util.*;

/** Experiments are process-local opt-ins; they never become boot tasks. */
final class LabSession {
    static volatile boolean active,voice;
    static volatile VoiceCommands.App target;
    static volatile String closeMethod="back2",lastTest="";
    static volatile int generation;
    static final Handler main=new Handler(Looper.getMainLooper());
    static void open(Context c){LabLog.init(c);if(!active){active=true;LabLog.write("SESSION","开始功能测试；语音默认关闭");}}
    static boolean voiceActive(){return active&&voice&&target!=null;}
    static void select(Context c,VoiceCommands.App app){generation++;target=app;LabLog.write("SESSION","选择 APP："+app.name+" "+app.pkg);SimoVoiceService.sync(c);}
    static void end(Context c){active=false;voice=false;generation++;lastTest="";LabLog.write("SESSION","结束测试；已取消待执行操作、撤销测试口令、停止日志监听");c.stopService(new Intent(c,LabMonitorService.class));SimoVoiceService.sync(c);}
    static boolean valid(int g,String pkg){return active&&g==generation&&target!=null&&target.pkg.equals(pkg);}
    static String begin(String action,String pkg){generation++;String id=UUID.randomUUID().toString().substring(0,8);lastTest=id;LabLog.write(id,"REQUEST "+action+" pkg="+pkg);return id;}
    static List<VoiceCommands.Command> commands(){
        VoiceCommands.App app=target;if(!voiceActive()||app==null)return Collections.emptyList();
        ArrayList<VoiceCommands.Command> out=new ArrayList<>();
        for(String raw:Arrays.asList(app.name,app.alias)){
            String n=VoiceCommands.normalize(raw);if(n.isEmpty()||n.length()>60)continue;
            out.add(new VoiceCommands.Command(app,"关闭"+n,"lab.close"));
            String[] keys={"left","center","right","rear"},labels={"左屏","中屏","右屏","后排屏"};
            for(int i=0;i<keys.length;i++){
                out.add(new VoiceCommands.Command(app,"把"+n+"移到"+labels[i],"lab."+keys[i]));
                out.add(new VoiceCommands.Command(app,"在"+labels[i]+"打开"+n,"lab."+keys[i]));
            }
            out.add(new VoiceCommands.Command(app,"窗口打开"+n,"lab.freeform"));
        }
        return out;
    }
    static boolean accepts(VoiceCommands.Command cmd){
        if(!voiceActive())return false;
        for(VoiceCommands.Command current:commands())if(current.id.equals(cmd.id))return true;
        return false;
    }
}
