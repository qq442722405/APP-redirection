package com.acc.acc;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class VehicleStatusService extends Service {
    static volatile String status="未开启";
    static final class Reading {final double value;final long time;final String error;Reading(double v,String e){value=v;error=e;time=SystemClock.elapsedRealtime();}}
    static final Map<String,Reading> readings=new ConcurrentHashMap<>();
    static String value(VehicleData.Field field){Reading r=readings.get(field.id);if(r==null)return "未获取";if(!r.error.isEmpty())return "未获取";if(SystemClock.elapsedRealtime()-r.time>15000)return "数据过期";return VehicleData.display(field,r.value);}
    static String details(){StringBuilder b=new StringBuilder(status);for(VehicleData.Field field:VehicleData.FIELDS){Reading r=readings.get(field.id);b.append('\n').append(field.label).append("：").append(value(field));if(r!=null&&!r.error.isEmpty())b.append(" · ").append(r.error);}return b.toString();}
    static void sync(Context c){Intent i=new Intent(c,VehicleStatusService.class);if(!c.getSharedPreferences(MainActivity.PREF,0).getBoolean("vehicle_overlay_enabled",false)||!Settings.canDrawOverlays(c)){c.stopService(i);status="未开启或未授予悬浮窗权限";return;}try{c.startForegroundService(i);}catch(RuntimeException e){status="启动失败："+e.getClass().getSimpleName();}}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor();
    private VehicleCapClient cap;private SharedPreferences prefs;private WindowManager wm;private LinearLayout overlay;private TextView connection;
    private final Map<String,TextView> labels=new LinkedHashMap<>();private WindowManager.LayoutParams params;private volatile boolean stopped;
    private final Runnable update=new Runnable(){public void run(){if(stopped)return;status=cap.status;for(VehicleData.Field f:VehicleData.FIELDS){TextView label=labels.get(f.id);if(label!=null)label.setText(f.label+"\n"+value(f));}if(connection!=null)connection.setText(status+"\n"+(prefs.getBoolean("vehicle_overlay_lock",false)?"位置已锁定，请到设置调整":"拖动标题移动位置"));main.postDelayed(this,1000);}};
    @Override public void onCreate(){
        super.onCreate();prefs=getSharedPreferences(MainActivity.PREF,0);wm=(WindowManager)getSystemService(WINDOW_SERVICE);cap=new VehicleCapClient(this);readings.clear();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("vehicle_overlay","车状态悬浮窗",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,32006,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,32007,new Intent(this,VehicleStatusService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"vehicle_overlay").setContentTitle("车状态悬浮窗").setContentText("读取车辆状态；可在设置或通知中关闭").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentIntent(open).addAction(new Notification.Action.Builder(null,"关闭悬浮窗",stop).build()).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(32006,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(32006,n);
        cap.connect();main.post(update);io.scheduleWithFixedDelay(this::poll,0,3,TimeUnit.SECONDS);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"stop".equals(intent.getAction()))prefs.edit().putBoolean("vehicle_overlay_enabled",false).apply();
        if(!prefs.getBoolean("vehicle_overlay_enabled",false)||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        try{buildOverlay();}catch(RuntimeException e){status="悬浮窗打开失败："+e.getClass().getSimpleName();stopSelf();}return START_STICKY;
    }
    private void poll(){if(stopped)return;for(VehicleData.Field f:VehicleData.FIELDS){if(stopped)return;if(!prefs.getBoolean("vehicle_field_"+f.id,true))continue;try{double value=VehicleData.read(cap.read(f),f);if(!stopped)readings.put(f.id,new Reading(value,""));}catch(Exception e){if(!stopped)readings.put(f.id,new Reading(0,e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage())));}}}
    private TextView label(String text,int font){TextView v=new TextView(this);v.setText(text);v.setTextColor(0xffffffff);DesignTypography.setPx(v,font);v.setPadding(10,5,10,5);v.setIncludeFontPadding(false);return v;}
    private void buildOverlay(){
        if(overlay!=null)wm.removeView(overlay);labels.clear();int font=Math.max(16,Math.min(48,prefs.getInt("vehicle_overlay_font",24)));
        overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setBackground(DesignTheme.panel(this));
        TextView title=label("车状态",font+2);overlay.addView(title,new LinearLayout.LayoutParams(-1,font+26));
        LinearLayout row=null;int count=0;for(VehicleData.Field f:VehicleData.FIELDS){if(!prefs.getBoolean("vehicle_field_"+f.id,true))continue;if(count++%2==0){row=new LinearLayout(this);overlay.addView(row,new LinearLayout.LayoutParams(-1,font*2+26));}TextView text=label(f.label+"\n"+value(f),font);labels.put(f.id,text);row.addView(text,new LinearLayout.LayoutParams(0,-1,1));}
        connection=label(status,Math.max(14,font-7));connection.setMaxLines(2);overlay.addView(connection,new LinearLayout.LayoutParams(-1,font*2+16));
        params=new WindowManager.LayoutParams(Math.max(360,font*23),WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.alpha=Math.max(.2f,Math.min(1f,prefs.getInt("vehicle_overlay_opacity",90)/100f));
        if(prefs.getBoolean("vehicle_overlay_lock",false)){
            params.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if(Build.VERSION.SDK_INT>=31){
                android.hardware.input.InputManager input=(android.hardware.input.InputManager)getSystemService(INPUT_SERVICE);
                params.alpha=Math.min(params.alpha,input==null?.8f:input.getMaximumObscuringOpacityForTouch());
            }
        }
        params.gravity=Gravity.TOP|Gravity.LEFT;params.x=prefs.getInt("vehicle_overlay_x",40);params.y=prefs.getInt("vehicle_overlay_y",100);clamp();
        title.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;public boolean onTouch(View v,android.view.MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN){x=event.getRawX();y=event.getRawY();ox=params.x;oy=params.y;return true;}if(event.getAction()==MotionEvent.ACTION_MOVE){params.x=ox+Math.round(event.getRawX()-x);params.y=oy+Math.round(event.getRawY()-y);clamp();wm.updateViewLayout(overlay,params);return true;}if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){prefs.edit().putInt("vehicle_overlay_x",params.x).putInt("vehicle_overlay_y",params.y).apply();return true;}return true;}});
        wm.addView(overlay,params);overlay.post(()->{if(overlay!=null&&!stopped){clamp();try{wm.updateViewLayout(overlay,params);}catch(RuntimeException ignored){}}});
    }
    private void clamp(){android.graphics.Point size=new android.graphics.Point();wm.getDefaultDisplay().getRealSize(size);params.width=Math.min(params.width,size.x);params.x=Math.max(0,Math.min(params.x,size.x-params.width));params.y=Math.max(0,Math.min(params.y,size.y-(overlay==null?300:Math.max(100,overlay.getHeight()))));}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){stopped=true;main.removeCallbacksAndMessages(null);io.shutdownNow();if(cap!=null)cap.close();if(overlay!=null)try{wm.removeView(overlay);}catch(RuntimeException ignored){}overlay=null;readings.clear();status="车状态悬浮窗已关闭";stopForeground(true);super.onDestroy();}
}
