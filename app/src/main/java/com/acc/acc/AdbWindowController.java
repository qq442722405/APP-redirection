package com.acc.acc;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * ADB/shell 窗口控制器。
 * 优先尝试 su，其次尝试普通 shell；普通第三方 APK 在未被系统授予 shell/root 能力时会失败，
 * 这是 Android 安全模型限制，并不是 Java API 能绕过的。
 */
public final class AdbWindowController {
    private static final Handler H=new Handler(Looper.getMainLooper());
    private static WindowManager wm;
    private static TextView closeView;
    private static WindowManager.LayoutParams closeLp;
    private static String currentPkg;
    private static Rect currentBounds;

    private AdbWindowController(){}

    /**
     * 自动选择可用的高权限通道：
     * 1) root/su；2) 车机本地 adb client -> adb shell；3) 普通 sh。
     * 注意：开启 USB/TCP ADB 并不会自动给普通 APK 授予 shell UID；
     * 只有车机本身提供可调用的 adb client/bridge/root 时才能真正执行 shell。
     */
    static String shell(String command){
        String r=exec(new String[]{"su","-c",command});
        if(ok(r)) return r;

        // 尝试车机本地 adb 客户端。不同车机路径可能不同。
        String[] adbPaths={"adb","/system/bin/adb","/system/xbin/adb","/vendor/bin/adb"};
        for(String adb:adbPaths){
            r=exec(new String[]{adb,"shell",command});
            if(ok(r)) return r;
            // 某些系统 adb daemon 只开放本机 5555。
            r=exec(new String[]{adb,"-s","127.0.0.1:5555","shell",command});
            if(ok(r)) return r;
        }

        return exec(new String[]{"sh","-c",command});
    }

    private static boolean ok(String r){
        return r!=null && !r.startsWith("__ERR__");
    }

    /** 返回当前能用的执行通道，供权限与诊断使用。 */
    public static String diagnose(){
        String r=exec(new String[]{"su","-c","id"});
        if(ok(r) && r.contains("uid=0")) return "ROOT（uid=0）";
        String[] adbPaths={"adb","/system/bin/adb","/system/xbin/adb","/vendor/bin/adb"};
        for(String adb:adbPaths){
            r=exec(new String[]{adb,"shell","id"});
            if(ok(r) && r.contains("uid=2000")) return "ADB SHELL（uid=2000）";
            r=exec(new String[]{adb,"-s","127.0.0.1:5555","shell","id"});
            if(ok(r) && r.contains("uid=2000")) return "ADB SHELL（127.0.0.1:5555）";
        }
        r=exec(new String[]{"sh","-c","id"});
        if(ok(r)) return "普通 APP Shell："+r.trim();
        return "不可用";
    }

    public static boolean launchAndResize(Context c,String pkg,Rect bounds,boolean floating){
        if(Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(c)) return false;
        Intent i=c.getPackageManager().getLaunchIntentForPackage(pkg);
        if(i==null || i.getComponent()==null)return false;
        String component=i.getComponent().flattenToShortString();
        shell("settings put global enable_freeform_support 1");
        String out=shell("am start --user 0 --windowingMode freeform -n "+component);
        if(out==null || out.startsWith("__ERR__")) return false;
        currentPkg=pkg; currentBounds=new Rect(bounds);
        H.postDelayed(()->{
            int task=findTaskId(pkg);
            if(task>=0){
                resizeTask(task,bounds);
                showCloseHandle(c,task,bounds,floating);
            }
        },900);
        return true;
    }

    private static int findTaskId(String pkg){
        String s=shell("dumpsys activity activities");
        if(s==null || s.startsWith("__ERR__"))return -1;
        Pattern[] ps={Pattern.compile("Task\\{[^}]*#(\\d+)[^}]*\\}.*?"+Pattern.quote(pkg),Pattern.DOTALL),Pattern.compile("Task[^\n]*#(\\d+)[^\n]*"+Pattern.quote(pkg))};
        for(Pattern p:ps){Matcher m=p.matcher(s); if(m.find())try{return Integer.parseInt(m.group(1));}catch(Exception ignored){}}
        Matcher m=Pattern.compile("#(\\d+):.*?"+Pattern.quote(pkg)).matcher(s);
        if(m.find())try{return Integer.parseInt(m.group(1));}catch(Exception ignored){}
        return -1;
    }

    private static boolean resizeTask(int task,Rect b){
        String q="am task resize "+task+" "+b.left+" "+b.top+" "+b.right+" "+b.bottom;
        String r=shell(q);
        if(r!=null && !r.startsWith("__ERR__"))return true;
        r=shell("cmd activity task resize "+task+" "+b.left+" "+b.top+" "+b.right+" "+b.bottom);
        return r!=null && !r.startsWith("__ERR__");
    }

    private static void showCloseHandle(Context c,int task,Rect b,boolean floating){
        if(wm==null)wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
        removeHandle();
        closeView=new TextView(c); closeView.setText("×"); closeView.setTextColor(Color.WHITE); closeView.setTextSize(20); closeView.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xFFB71C1C); bg.setCornerRadius(20); closeView.setBackground(bg);
        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        closeLp=new WindowManager.LayoutParams(48,48,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        closeLp.gravity=Gravity.TOP|Gravity.LEFT; closeLp.x=Math.max(0,b.right-24); closeLp.y=Math.max(0,b.top);
        final int[] down={0,0}; final int[] start={closeLp.x,closeLp.y}; final boolean[] moved={false};
        closeView.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){down[0]=(int)e.getRawX();down[1]=(int)e.getRawY();start[0]=closeLp.x;start[1]=closeLp.y;moved[0]=false;return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){int dx=(int)e.getRawX()-down[0],dy=(int)e.getRawY()-down[1];if(Math.abs(dx)>8||Math.abs(dy)>8){moved[0]=true;closeLp.x=start[0]+dx;closeLp.y=start[1]+dy;try{wm.updateViewLayout(closeView,closeLp);}catch(Exception ignored){} if(floating){Rect nb=new Rect(closeLp.x-b.width()+24,closeLp.y,closeLp.x+24,closeLp.y+b.height()); currentBounds=nb;resizeTask(task,nb);}}return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){if(!moved[0]){shell("am force-stop "+currentPkg);removeHandle();}return true;}
            return true;
        });
        try{wm.addView(closeView,closeLp);}catch(Exception ignored){}
    }
    private static void removeHandle(){if(wm!=null&&closeView!=null){try{wm.removeView(closeView);}catch(Exception ignored){}}closeView=null;}
}
