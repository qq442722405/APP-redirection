package com.acc.acc;

import android.app.ActivityOptions;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.view.Display;
import org.json.JSONObject;

/** All bounded starts retain their options even when a launch fails. */
final class WindowLaunch {
    static void prepare(Intent intent,boolean freshTask){
        int reuse=Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                |Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TASK
                |Intent.FLAG_ACTIVITY_MULTIPLE_TASK|Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
        int flags=(intent.getFlags()&~reuse)|Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS;
        if(freshTask)flags|=Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
        intent.setFlags(flags);
    }
    static Rect start(Context context,Intent intent,int x,int y,int width,int height,int displayId,boolean fullscreen,boolean freshTask){
        DisplayManager manager=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        Display display=manager.getDisplay(displayId<0?Display.DEFAULT_DISPLAY:displayId);
        if(display==null)throw new IllegalArgumentException("预设显示器不可用："+displayId);
        Point screen=new Point();display.getRealSize(screen);
        int[] coordinates=WindowBounds.resolve(x,y,width,height,screen.x,screen.y,fullscreen);
        Rect bounds=new Rect(coordinates[0],coordinates[1],coordinates[2],coordinates[3]);
        ActivityOptions options=ActivityOptions.makeBasic();
        options.setLaunchBounds(bounds);options.setLaunchDisplayId(display.getDisplayId());
        prepare(intent,freshTask);
        intent.putExtra("com.acc.acc.target_x",bounds.left);intent.putExtra("com.acc.acc.target_y",bounds.top);
        intent.putExtra("com.acc.acc.target_w",bounds.width());intent.putExtra("com.acc.acc.target_h",bounds.height());
        intent.putExtra("com.acc.acc.target_display_id",display.getDisplayId());intent.putExtra("com.acc.acc.fullscreen",fullscreen);
        context.startActivity(intent,options.toBundle());
        return bounds;
    }
    /** Focusing a target for BACK must not reset its task or request another instance. */
    static void activateForControl(Context context,String pkg,Intent intent)throws Exception{
        JSONObject all=new JSONObject(context.getSharedPreferences(MainActivity.PREF,0).getString("app_last_bounds","{}"));
        JSONObject saved=all.optJSONObject(pkg);
        if(saved!=null){
            start(context,intent,saved.optInt("x"),saved.optInt("y"),saved.optInt("w"),saved.optInt("h"),
                    saved.optInt("displayId",-1),saved.optBoolean("fullscreen",false),false);
        }else{prepare(intent,false);context.startActivity(intent);}
    }
}
