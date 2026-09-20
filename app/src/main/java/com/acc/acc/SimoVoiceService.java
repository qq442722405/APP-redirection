package com.acc.acc;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Keeps user-enabled APP voice commands registered while the launcher is in the background. */
public final class SimoVoiceService extends Service {
    static volatile String status="未开启",lastHit="尚未收到语音口令";
    static final String EXTRA_TICKET="com.acc.acc.voice.ticket";
    private static final Map<String,Ticket> tickets=new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong epochs=new java.util.concurrent.atomic.AtomicLong();
    private final long epoch=epochs.incrementAndGet();
    private static final class Ticket {
        final VoiceCommands.Command command;final long created=SystemClock.elapsedRealtime();
        Ticket(VoiceCommands.Command command){this.command=command;}
    }
    static VoiceCommands.Command takeLaunch(Context context,String token){
        Ticket t=token==null?null:tickets.remove(token);
        return t!=null&&SystemClock.elapsedRealtime()-t.created<10000&&context.getSharedPreferences(MainActivity.PREF,0).getBoolean("simo_enabled",false)?t.command:null;
    }
    static void sync(Context context){
        Intent intent=new Intent(context,SimoVoiceService.class);
        if(!requested(context)){context.stopService(intent);status="未开启";return;}
        try{if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);}
        catch(RuntimeException e){status="无法启动后台服务，请打开启动器重试："+e.getClass().getSimpleName();}
    }
    private static boolean requested(Context c){return c.getSharedPreferences(MainActivity.PREF,0).getBoolean("simo_enabled",false);}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private volatile IBinder remote;
    private volatile VoiceCommands registry=new VoiceCommands(Collections.emptyList());
    private volatile byte[] registeredPage;
    private volatile boolean destroyed;
    private boolean bound;
    private volatile int serviceUid=-1;
    private int retries;
    private long lastDispatch;
    private String lastCommand="";
    private final Runnable refresh=()->io.execute(this::publish);
    private final Runnable retry=()->{if(remote==null){unbind();connect();}};
    private final Runnable timeout=()->{if(remote==null&&!destroyed){status="等待 VISO 连接超时，将自动重试";scheduleRetry();}};
    private final SharedPreferences.OnSharedPreferenceChangeListener changed=(p,key)->{
        if(key==null)return;
        if(key.equals("simo_enabled")&&!requested(this)){registry=new VoiceCommands(Collections.emptyList());stopSelf();return;}
        if(key.equals("simo_close_enabled")||key.equals("simo_enabled")||key.equals(MainActivity.APPS)||key.equals("simo_all_apps")||key.startsWith("simo_alias_")||key.startsWith("simo_exclude_"))refreshSoon();
    };
    private final BroadcastReceiver packages=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refreshSoon();if(remote==null)connect();}};
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(destroyed)return;main.removeCallbacks(timeout);main.removeCallbacks(retry);remote=binder;retries=0;io.execute(()->{
                try{if(!SimoVoiceProtocol.DESCRIPTOR.equals(binder.getInterfaceDescriptor())){status="VISO 接口版本不匹配，未发送口令";main.post(()->{remote=null;unbind();});return;}publish();}
                catch(Exception e){status="读取 VISO 接口失败："+e.getClass().getSimpleName();main.post(()->{remote=null;scheduleRetry();});}
            });
        }
        @Override public void onServiceDisconnected(ComponentName name){lost();}
        @Override public void onBindingDied(ComponentName name){lost();}
        @Override public void onNullBinding(ComponentName name){lost();}
        private void lost(){if(destroyed)return;remote=null;registeredPage=null;registry=new VoiceCommands(Collections.emptyList());status="VISO 连接中断，将自动重连";scheduleRetry();}
    };
    private final Binder callback=new Binder(){
        {attachInterface(null,SimoVoiceProtocol.CALLBACK);}
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(SimoVoiceProtocol.CALLBACK);return true;}
            if(code!=1)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(SimoVoiceProtocol.CALLBACK);
            if(Binder.getCallingUid()!=serviceUid){lastHit="回调来源与 VISO 服务不一致，已拒绝执行";throw new SecurityException("Unexpected VISO callback caller");}
            Bundle input=data.readInt()!=0?Bundle.CREATOR.createFromParcel(data):new Bundle();
            // Return promptly: launch and the synchronous result transaction run outside this Binder call.
            main.post(()->handle(input));if(reply!=null)reply.writeNoException();return true;
        }
    };
    @Override public void onCreate(){
        super.onCreate();prefs=getSharedPreferences(MainActivity.PREF,0);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("simo_voice","SIMO 语音启动",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,32002,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(this,"simo_voice").setContentTitle("SIMO 语音启动已开启").setContentText("登记 APP 口令中，可在启动器设置中关闭").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(32002,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(32002,notice);
        prefs.registerOnSharedPreferenceChangeListener(changed);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packages,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(packages,filter);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(!requested(this)){stopSelf();return START_NOT_STICKY;}
        if(remote==null)connect();else refreshSoon();return START_STICKY;
    }
    private void connect(){
        if(destroyed||bound||!requested(this))return;
        try{
            serviceUid=getPackageManager().getApplicationInfo(SimoVoiceProtocol.PACKAGE,0).uid;
            Intent intent=new Intent("android.intent.action.VISO").setComponent(new ComponentName(SimoVoiceProtocol.PACKAGE,SimoVoiceProtocol.SERVICE));
            status="正在连接极越 VISO 语音服务…";bound=bindService(intent,connection,Context.BIND_AUTO_CREATE);
            if(!bound){status="VISO 拒绝连接：服务未导出或车机不兼容";scheduleRetry();}
            else main.postDelayed(timeout,8000);
        }catch(PackageManager.NameNotFoundException e){status="未检测到极越 VISO 服务，此设备暂不支持";scheduleRetry();}
        catch(RuntimeException e){status="VISO 连接失败："+e.getClass().getSimpleName();scheduleRetry();}
    }
    private void scheduleRetry(){
        if(destroyed)return;main.removeCallbacks(timeout);main.removeCallbacks(retry);main.postDelayed(retry,Math.min(60000,2000L<<Math.min(retries++,5)));
    }
    private void unbind(){if(bound){try{unbindService(connection);}catch(IllegalArgumentException ignored){}bound=false;}}
    private void refreshSoon(){if(!destroyed){main.removeCallbacks(refresh);main.postDelayed(refresh,600);}}
    private void publish(){
        IBinder binder=remote;if(destroyed||binder==null||!requested(this))return;
        try{
            VoiceCommands base=new VoiceCommands(prefs.getBoolean("simo_enabled",false)?SimoApps.list(this,false):Collections.emptyList());
            VoiceCommands next=new VoiceCommands(base,prefs.getBoolean("simo_close_enabled",false)?VoiceCommands.closeCommands(SimoApps.list(this,false)):Collections.emptyList());
            byte[] bytes=SimoVoiceProtocol.page(this,next),old=registeredPage;
            // Remove the previous page first so removed APP names do not remain registered.
            registry=new VoiceCommands(Collections.emptyList());
            if(old!=null)SimoVoiceProtocol.sendPage(this,binder,old,null,true);
            registeredPage=null;
            if(destroyed||binder!=remote||!requested(this))return;
            if(next.commands.isEmpty()){status="已连接 VISO，但没有可登记口令；请添加 APP 或设置不重名的别名";return;}
            registry=next;SimoVoiceProtocol.sendPage(this,binder,bytes,callback,false);registeredPage=bytes;
            status="已连接 VISO，已发送 "+next.commands.size()+" 条口令（等待实际喊话验证）";
            if(!next.conflicts.isEmpty())status+="；跳过 "+next.conflicts.size()+" 个重名口令";
            if(next.omitted>0)status+="；超过 500 条，省略 "+next.omitted+" 条";
        }catch(Exception e){registry=new VoiceCommands(Collections.emptyList());status="口令登记失败："+e.getClass().getSimpleName()+" "+String.valueOf(e.getMessage());}
    }
    private static String string(Bundle b,String... names){for(String n:names){Object v=b.get(n);if(v instanceof String&&!((String)v).isEmpty())return (String)v;}return "";}
    private void handle(Bundle input){
        if(destroyed)return;int result=-100;IBinder binder=remote;
        try{
            String owner=string(input,"viso_appname"),panel=string(input,"viso_pageid"),action=string(input,"viso_action","action");
            VoiceCommands.Command command=registry.match(string(input,"viso_selfid","viewWordId"),string(input,"viso_hotword","hotWord"));
            boolean allowed=command!=null&&prefs.getBoolean("simo_enabled",false)&&(command.action.equals("open")||(command.action.equals("close")&&prefs.getBoolean("simo_close_enabled",false)));
            if(allowed&&(owner.isEmpty()||owner.equals(getPackageName()))&&(panel.isEmpty()||panel.equals(SimoVoiceProtocol.PANEL))&&(action.isEmpty()||action.equals("visoClick"))){
                long now=SystemClock.elapsedRealtime();
                if(command.id.equals(lastCommand)&&now-lastDispatch<1500){result=0;}
                else if(command.action.equals("close")){
                    boolean submitted=SimoClose.close(this,command.pkg);
                    lastCommand=command.id;lastDispatch=now;result=submitted?0:-100;
                }else if(getPackageManager().getLaunchIntentForPackage(command.pkg)!=null){
                    tickets.entrySet().removeIf(e->now-e.getValue().created>10000);
                    String token=UUID.randomUUID().toString();tickets.put(token,new Ticket(command));
                    Intent launch=new Intent(this,MainActivity.class).putExtra(EXTRA_TICKET,token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    try{startActivity(launch);}catch(RuntimeException e){tickets.remove(token);throw e;}
                    lastCommand=command.id;lastDispatch=now;lastHit="收到："+command.phrase+"；已请求启动";result=0;
                    main.postDelayed(()->{if(tickets.remove(token)!=null)lastHit="收到："+command.phrase+"；系统未打开启动器，请检查悬浮窗权限/后台启动限制";},10000);
                }else lastHit="APP 已卸载或没有启动入口："+command.name;
            }else lastHit="收到未匹配的口令，未启动 APP";
        }catch(RuntimeException e){lastHit="语音启动请求失败："+e.getClass().getSimpleName();}
        final int code=result;
        if(binder!=null&&!destroyed)io.execute(()->{try{SimoVoiceProtocol.result(binder,input,code);}catch(Exception e){lastHit+="；结果回报失败："+e.getClass().getSimpleName();}});
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        destroyed=true;main.removeCallbacksAndMessages(null);prefs.unregisterOnSharedPreferenceChangeListener(changed);
        try{unregisterReceiver(packages);}catch(IllegalArgumentException ignored){}
        registry=new VoiceCommands(Collections.emptyList());tickets.clear();IBinder binder=remote;remote=null;
        io.execute(()->{byte[] old=registeredPage;if(epochs.get()==epoch&&binder!=null&&old!=null)try{SimoVoiceProtocol.sendPage(this,binder,old,null,true);}catch(Exception ignored){}main.post(this::unbind);});
        io.shutdown();status="语音服务已停止";stopForeground(true);super.onDestroy();
    }
}
