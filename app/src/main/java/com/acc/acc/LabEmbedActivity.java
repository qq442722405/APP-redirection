package com.acc.acc;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.hardware.display.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** A real virtual-display experiment, not a screenshot or a WebView. */
public final class LabEmbedActivity extends Activity implements SurfaceHolder.Callback {
    private VirtualDisplay display;
    private String pkg,test;
    private TextView state;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);pkg=getIntent().getStringExtra("pkg");test=getIntent().getStringExtra("test");
        if(!LabSession.active||LabSession.target==null||!LabSession.target.pkg.equals(pkg)||!LabActions.targetAllowed(this,pkg)){finish();return;}
        DesignSurface p=new DesignSurface(this,1000,700);p.setBackground(DesignTheme.panel(this));
        p.label("窗口内显示 APP · 实验",30,20,940,50,32);
        state=p.label("独立虚拟屏，160 dpi；不转发触摸。若黑屏/跳到主屏，请记录无效。",30,78,940,72,22);
        SurfaceView surface=new SurfaceView(this);p.place(surface,DesignSurface.rect(30,164,940,440,20));surface.getHolder().addCallback(this);
        Button close=new Button(this);close.setText("关闭实验窗口");p.place(close,DesignSurface.rect(30,625,240,50,24));close.setOnClickListener(v->finish());
        Button good=new Button(this);good.setText("记录：能显示");p.place(good,DesignSurface.rect(290,625,230,50,24));good.setOnClickListener(v->{LabLog.write(test,"MANUAL 内嵌显示有效（不代表支持触摸）");state.setText("已记录：能够在窗口内显示。触摸控制未实现。");});
        Button bad=new Button(this);bad.setText("记录：无效");p.place(bad,DesignSurface.rect(540,625,230,50,24));bad.setOnClickListener(v->{LabLog.write(test,"MANUAL 内嵌显示无效");state.setText("已记录：内嵌显示无效。");});
        setContentView(p);
    }
    @Override public void surfaceCreated(SurfaceHolder holder){
        if(display!=null)return;
        try{
            display=((DisplayManager)getSystemService(DISPLAY_SERVICE)).createVirtualDisplay("ACC_LAB_EMBED",1280,720,160,holder.getSurface(),DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC|DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
            if(display==null)throw new IllegalStateException("系统未创建虚拟屏");
            int id=display.getDisplay().getDisplayId();Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            if(android.os.Build.VERSION.SDK_INT>=29&&!((ActivityManager)getSystemService(ACTIVITY_SERVICE)).isActivityStartAllowedOnDisplay(this,id,launch))throw new SecurityException("系统拒绝在虚拟屏启动第三方 APP");
            ActivityOptions options=ActivityOptions.makeBasic().setLaunchDisplayId(id);startActivity(launch,options.toBundle());
            LabLog.write(test,"API_RETURN 内嵌显示请求：Display="+id+" 1280×720 dpi160；需人工确认是否显示");state.setText("已请求在虚拟屏 "+id+" 显示 APP。不能触摸；请确认画面是否出现。");
        }catch(Exception e){state.setText("内嵌显示失败："+LabLog.error(e));LabLog.write(test,"ERROR 内嵌 "+LabLog.error(e));release();}
    }
    @Override public void surfaceChanged(SurfaceHolder h,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder h){release();}
    private void release(){if(display!=null){display.release();display=null;LabLog.write(test,"虚拟显示器已释放");}}
    @Override protected void onDestroy(){release();super.onDestroy();}
}
