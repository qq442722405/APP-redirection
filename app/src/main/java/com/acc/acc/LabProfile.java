package com.acc.acc;

import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.view.Display;

final class LabProfile {
    final int displayId,x,y,w,h;
    LabProfile(int displayId,int x,int y,int w,int h){this.displayId=displayId;this.x=x;this.y=y;this.w=w;this.h=h;}
    Rect bounds(){return new Rect(x,y,x+w,y+h);}
    static Display[] displays(Context c){return ((DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE)).getDisplays();}
    static Display display(Context c,int id){return ((DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(id);}
    static LabProfile get(Context c,String key){
        SharedPreferences p=c.getSharedPreferences("lab_settings",0);int id=p.getInt(key+"_display",key.equals("rear")?-1:Display.DEFAULT_DISPLAY);
        Display d=display(c,id);Point size=new Point();if(d!=null)d.getRealSize(size);
        int[] defaults={0,0,size.x,size.y};
        if(!key.equals("rear")&&!key.equals("custom")&&size.x>=3&&size.y>0)defaults=LabBounds.third(key.equals("left")?0:key.equals("center")?1:2,size.x,size.y);
        if(key.equals("custom"))defaults=new int[]{Math.max(0,size.x/8),Math.max(0,size.y/8),Math.max(1,size.x*3/4),Math.max(1,size.y*3/4)};
        return new LabProfile(id,p.getInt(key+"_x",defaults[0]),p.getInt(key+"_y",defaults[1]),p.getInt(key+"_w",defaults[2]),p.getInt(key+"_h",defaults[3]));
    }
    void validate(Context c){Display d=display(c,displayId);if(d==null||d.getName().startsWith("ACC_LAB_"))throw new IllegalArgumentException("请先选择当前实际显示器，后排屏不能猜测编号");Point s=new Point();d.getRealSize(s);if(!LabBounds.fits(x,y,w,h,s.x,s.y))throw new IllegalArgumentException("坐标超出 Display "+displayId+" 的 "+s.x+"×"+s.y+" 范围");}
    void save(Context c,String key){validate(c);c.getSharedPreferences("lab_settings",0).edit().putInt(key+"_display",displayId).putInt(key+"_x",x).putInt(key+"_y",y).putInt(key+"_w",w).putInt(key+"_h",h).apply();}
    public String toString(){return "Display="+displayId+" x="+x+" y="+y+" w="+w+" h="+h;}
    static String inventory(Context c){StringBuilder b=new StringBuilder();for(Display d:displays(c)){DisplayMetrics m=new DisplayMetrics();d.getRealMetrics(m);b.append("ID ").append(d.getDisplayId()).append(" · ").append(d.getName()).append(" · ").append(m.widthPixels).append('×').append(m.heightPixels).append(" · dpi ").append(m.densityDpi).append(" · state ").append(d.getState()).append(" · flags ").append(d.getFlags()).append('\n');}return b.toString();}
}
