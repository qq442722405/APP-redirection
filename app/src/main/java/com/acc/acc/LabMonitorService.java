package com.acc.acc;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;

public final class LabMonitorService extends Service {
    static volatile String state="未监听";
    private volatile java.lang.Process process;
    private volatile boolean stopped;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean started;
    @Override public void onCreate(){
        super.onCreate();LabLog.init(this);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("lab_monitor","功能测试日志",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,32004,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,32005,new Intent(this,LabMonitorService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"lab_monitor").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("功能测试日志监听中").setContentText("仅保存在本机；点击停止可结束监听").setContentIntent(open).addAction(new Notification.Action.Builder(null,"停止监听",stop).build()).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(32004,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(32004,n);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||"stop".equals(intent.getAction())||!LabSession.active){stopSelf();return START_NOT_STICKY;}
        if(started)return START_NOT_STICKY;started=true;
        boolean system=intent.getBooleanExtra("system",false);
        if(system&&checkSelfPermission("android.permission.READ_LOGS")!=PackageManager.PERMISSION_GRANTED){LabLog.write("MONITOR","ERROR 没有 READ_LOGS 授权，系统日志不可读；可改用本 APP 日志");stopSelf();return START_NOT_STICKY;}
        state=system?"监听系统窗口/语音相关日志":"监听本 APP logcat";
        worker.execute(()->read(system));return START_NOT_STICKY;
    }
    private void read(boolean system){
        try{
            java.lang.Process p=(system?new ProcessBuilder("/system/bin/logcat","-v","threadtime","-T","1"):new ProcessBuilder("/system/bin/logcat","--pid="+android.os.Process.myPid(),"-v","threadtime","-T","1")).redirectErrorStream(true).start();
            process=p;if(stopped){p.destroy();return;}LabLog.write("MONITOR",state);
            try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),"UTF-8"))){
                String line;while(!stopped&&(line=r.readLine())!=null){
                    String lower=line.toLowerCase(java.util.Locale.ROOT);VoiceCommands.App target=LabSession.target;
                    if(!system||lower.contains("activitytaskmanager")||lower.contains("windowmanager")||lower.contains("viso")||lower.contains("simo")||(target!=null&&line.contains(target.pkg))||lower.contains("denied")||lower.contains("permission"))LabLog.write("LOGCAT",line);
                }
            }
            if(!stopped)LabLog.write("MONITOR","logcat 结束，exit="+p.waitFor()+"；无日志不代表测试有效");
        }catch(Exception e){if(!stopped)LabLog.write("MONITOR","ERROR "+LabLog.error(e));}
        finally{if(!stopped)stopSelf();}
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){stopped=true;java.lang.Process p=process;if(p!=null)p.destroy();worker.shutdownNow();state="未监听";LabLog.write("MONITOR","日志监听已停止");stopForeground(true);super.onDestroy();}
}
