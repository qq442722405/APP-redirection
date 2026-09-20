package com.acc.acc;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Context;
import android.graphics.*;
import android.view.accessibility.*;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.accessibilityservice.GestureDescription;
import java.util.*;

public class AccessibilityServiceBridge extends AccessibilityService {
    private static AccessibilityServiceBridge instance;
    private static String currentPackageName;
    @Override public void onServiceConnected(){instance=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event!=null && event.getPackageName()!=null){
            String next=event.getPackageName().toString();
            currentPackageName=next;
        }
    }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(instance==this){instance=null;currentPackageName=null;}super.onDestroy();}
    public static String getCurrentPackage(){return currentPackageName;}
    interface CloseResult{void result(String message);}
    static boolean closeVisibleWindow(String pkg,CloseResult result){
        return closeVisibleWindow(pkg,380,result);
    }
    static boolean closeVisibleWindow(String pkg,long duration,CloseResult result){
        AccessibilityServiceBridge service=instance;if(service==null){result.result("关闭失败：请启用启动器的无障碍服务");return false;}
        try{
            Rect found=null;int displayId=Display.DEFAULT_DISPLAY,matches=0,targetLayer=0;
            List<AccessibilityWindowInfo> windows=new ArrayList<>();
            if(android.os.Build.VERSION.SDK_INT>=30){android.util.SparseArray<List<AccessibilityWindowInfo>> displays=service.getWindowsOnAllDisplays();for(int i=0;i<displays.size();i++)windows.addAll(displays.valueAt(i));}else windows.addAll(service.getWindows());
            for(AccessibilityWindowInfo window:windows){
                if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
                AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
                boolean target=pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName());root.recycle();
                if(target){Rect bounds=new Rect();window.getBoundsInScreen(bounds);if(bounds.isEmpty())continue;found=bounds;matches++;targetLayer=window.getLayer();if(android.os.Build.VERSION.SDK_INT>=30)displayId=window.getDisplayId();}
            }
            if(found==null||matches!=1){result.result("未关闭：目标可见窗口数量="+matches+"，需要恰好 1 个；目标="+pkg);return false;}
            Display display=((DisplayManager)service.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(displayId);if(display==null){result.result("未关闭：目标显示器已断开");return false;}
            Point size=new Point();display.getRealSize(size);float[] coords=CloseGeometry.swipe(found.left,found.top,found.right,found.bottom,size.x,size.y);
            if(coords==null){result.result("未关闭：手势跨区域或坐标不可靠；窗口="+found+"，显示器="+displayId+" "+size.x+"×"+size.y);return false;}
            Rect gestureArea=new Rect((int)(coords[0]-coords[3]),(int)coords[1],(int)(coords[0]+coords[3])+1,(int)coords[2]+1);
            for(AccessibilityWindowInfo other:windows){int otherDisplay=android.os.Build.VERSION.SDK_INT>=30?other.getDisplayId():Display.DEFAULT_DISPLAY;if(otherDisplay!=displayId||other.getLayer()<=targetLayer)continue;Rect bounds=new Rect();other.getBoundsInScreen(bounds);if(Rect.intersects(bounds,gestureArea)){result.result("未关闭：目标窗口被其他界面遮挡，请先返回目标 APP");return false;}}
            GestureDescription.Builder builder=new GestureDescription.Builder();if(android.os.Build.VERSION.SDK_INT>=30)builder.setDisplayId(displayId);
            for(int finger=-1;finger<=1;finger++){Path path=new Path();float x=coords[0]+finger*coords[3];path.moveTo(x,coords[1]);path.lineTo(x,coords[2]);builder.addStroke(new GestureDescription.StrokeDescription(path,0,duration));}
            boolean accepted=service.dispatchGesture(builder.build(),new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription g){result.result("已完成三指下滑："+pkg+"（收起窗口，不代表强制停止进程）");}
                @Override public void onCancelled(GestureDescription g){result.result("关闭手势被系统取消："+pkg);}
            },null);
            result.result(accepted?"已发送三指下滑 "+duration+"ms："+pkg+"；屏="+displayId+" 区域="+gestureArea:"系统拒绝关闭手势："+pkg);return accepted;
        }catch(RuntimeException e){result.result("关闭失败："+e.getClass().getSimpleName()+" "+e.getMessage());return false;}
    }
    static String activePackage(){
        AccessibilityServiceBridge service=instance;if(service==null)return "";
        try{AccessibilityNodeInfo root=service.getRootInActiveWindow();if(root==null)return "";try{return root.getPackageName()==null?"":root.getPackageName().toString();}finally{root.recycle();}}catch(RuntimeException e){return "";}
    }
    public static boolean isTargetForeground(String pkg){return pkg!=null&&!pkg.isEmpty()&&pkg.equals(activePackage());}
    static boolean connected(){return instance!=null;}
    static String inspect(String pkg){
        AccessibilityServiceBridge service=instance;if(service==null)return "无障碍服务未连接";
        StringBuilder report=new StringBuilder("无障碍已连接；活动窗口：").append(activePackage());
        try{
            List<AccessibilityWindowInfo> windows=new ArrayList<>();
            if(android.os.Build.VERSION.SDK_INT>=30){android.util.SparseArray<List<AccessibilityWindowInfo>> displays=service.getWindowsOnAllDisplays();for(int i=0;i<displays.size();i++)windows.addAll(displays.valueAt(i));}else windows.addAll(service.getWindows());
            int found=0;for(AccessibilityWindowInfo window:windows){AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;String owner;try{owner=String.valueOf(root.getPackageName());}finally{root.recycle();}if(!pkg.equals(owner))continue;found++;Rect bounds=new Rect();window.getBoundsInScreen(bounds);report.append("\n目标窗口：type=").append(window.getType()).append(" display=").append(android.os.Build.VERSION.SDK_INT>=30?window.getDisplayId():0).append(" layer=").append(window.getLayer()).append(" ").append(bounds);}
            report.append("\n目标窗口数：").append(found);
        }catch(RuntimeException e){report.append("\n读取失败：").append(e.getClass().getSimpleName());}return report.toString();
    }
    static boolean testBack(String pkg,boolean twice,CloseResult result){
        if(!isTargetForeground(pkg)){result.result("未发送返回：目标不是当前活动窗口；当前="+activePackage());return false;}
        AccessibilityServiceBridge service=instance;boolean sent=service.performGlobalAction(GLOBAL_ACTION_BACK);
        result.result("第一次返回："+(sent?"系统接受（需观察实际效果）":"系统拒绝"));
        if(sent&&twice)new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
            if(!isTargetForeground(pkg)){result.result("第二次返回已取消：目标已离开活动窗口");return;}
            result.result("第二次返回："+(instance.performGlobalAction(GLOBAL_ACTION_BACK)?"系统接受（需观察实际效果）":"系统拒绝"));
        },500);return sent;
    }
    public static boolean performBackForTarget(String pkg, boolean close){
        if(!isTargetForeground(pkg) || android.os.Build.VERSION.SDK_INT<16)return false;
        boolean sent=instance.performGlobalAction(GLOBAL_ACTION_BACK);
        if(close&&sent)new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(isTargetForeground(pkg))instance.performGlobalAction(GLOBAL_ACTION_BACK);},220);
        return sent;
    }
    public static void perform(Context c,int action){if(instance==null){return;} if(action==1&&android.os.Build.VERSION.SDK_INT>=16)instance.performGlobalAction(GLOBAL_ACTION_BACK);else if(action==2&&android.os.Build.VERSION.SDK_INT>=16)instance.performGlobalAction(GLOBAL_ACTION_HOME);else if(action==3&&android.os.Build.VERSION.SDK_INT>=16)instance.performGlobalAction(GLOBAL_ACTION_RECENTS);}

    /** 返回一次；关闭模式会再补一次返回，尽量退出当前 APP。 */
    public static void performBackThen(boolean close){
        if(instance==null || android.os.Build.VERSION.SDK_INT<16)return;
        performBackForTarget(currentPackageName,close);
    }
}
