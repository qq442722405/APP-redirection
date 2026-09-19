package com.acc.acc;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Bounded local diagnostics, exported only through the document picker. */
final class LabLog {
    private static final ArrayDeque<String> lines=new ArrayDeque<>();
    private static File file;
    static synchronized void init(Context c){
        if(file!=null)return;file=new File(c.getFilesDir(),"lab-current.log");
        if(file.isFile())try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){String line;while((line=in.readLine())!=null){lines.addLast(line);while(lines.size()>1000)lines.removeFirst();}}catch(IOException ignored){}
    }
    static synchronized void write(String id,String message){
        String text=message==null?"":message.replace('\r',' ').replace('\n',' ');
        if(text.length()>1800)text=text.substring(0,1800)+"…";
        String line=new SimpleDateFormat("MM-dd HH:mm:ss.SSS",Locale.ROOT).format(new Date())+" ["+id+"] "+text;
        lines.addLast(line);while(lines.size()>1000)lines.removeFirst();
        if(file!=null)try{
            if(file.length()>1024*1024){File old=new File(file.getParentFile(),"lab-previous.log");if(!old.exists()||old.delete())file.renameTo(old);}
            try(Writer out=new OutputStreamWriter(new FileOutputStream(file,file.length()<=1024*1024),"UTF-8")){out.write(line+"\n");}
        }catch(IOException ignored){}
    }
    static synchronized String tail(int count){StringBuilder b=new StringBuilder();int skip=Math.max(0,lines.size()-count),i=0;for(String s:lines)if(i++>=skip)b.append(s).append('\n');return b.toString();}
    static String error(Throwable e){while(e.getCause()!=null&&e.getCause()!=e)e=e.getCause();return e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
    static String report(Context c){
        return "APP窗口启动器 · 功能测试报告\nAndroid="+Build.VERSION.RELEASE+" SDK="+Build.VERSION.SDK_INT+" 设备="+Build.MANUFACTURER+" "+Build.MODEL+
            "\n屏幕数据（像素；不是网页缩放尺寸）：\n"+LabProfile.inventory(c)+"\n测试参数：\n"+c.getSharedPreferences("lab_settings",0).getAll()+
            "\n本次进程记录（最多1000行；REQUEST不等于成功，OBSERVED仅说明收到前台事件）：\n"+tail(1000);
    }
    static void verdict(String verdict){if(!LabSession.lastTest.isEmpty())write(LabSession.lastTest,"MANUAL 用户标记："+verdict);}
}
