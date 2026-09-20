package com.acc.acc;

import android.content.*;
import android.graphics.RectF;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;

final class VehicleStatusSettings {
    static void show(MainActivity a){
        DesignSurface p=a.design.page("车状态悬浮窗口");AlertDialog d=a.design.dialog(p);
        Switch enabled=new Switch(a),lock=new Switch(a);enabled.setChecked(a.prefs.getBoolean("vehicle_overlay_enabled",false));lock.setChecked(a.prefs.getBoolean("vehicle_overlay_lock",false));p.bind(3,enabled);p.bind(9,lock);
        EditText font=a.numberField("16–48",String.valueOf(a.prefs.getInt("vehicle_overlay_font",24))),opacity=a.numberField("20–100",String.valueOf(a.prefs.getInt("vehicle_overlay_opacity",90)));p.bind(5,font);p.bind(7,opacity);
        p.action(8,()->fields(a));p.action(14,d::dismiss);p.action(15,()->{a.prefs.edit().putInt("vehicle_overlay_x",40).putInt("vehicle_overlay_y",100).apply();VehicleStatusService.sync(a);});p.action(16,a::showScreenDiagnostics);
        p.action(13,()->{int f=a.number(font,-1),o=a.number(opacity,-1);if(f<16||f>48||o<20||o>100){a.design.toast("字号16–48，透明度20–100");return;}if(enabled.isChecked()&&!a.hasOverlayPermission()){a.design.toast("请先到权限与诊断开启悬浮窗权限");return;}a.prefs.edit().putBoolean("vehicle_overlay_enabled",enabled.isChecked()).putBoolean("vehicle_overlay_lock",lock.isChecked()).putInt("vehicle_overlay_font",f).putInt("vehicle_overlay_opacity",o).apply();VehicleStatusService.sync(a);a.design.toast("车状态悬浮窗设置已保存");});
        TextView values=(TextView)p.bound.get("e12");values.setMovementMethod(new android.text.method.ScrollingMovementMethod());a.design.show(d,p);
        Handler h=new Handler(Looper.getMainLooper());Runnable update=new Runnable(){public void run(){if(!d.isShowing())return;((TextView)p.bound.get("e11")).setText(VehicleStatusService.status);values.setText(VehicleStatusService.details());h.postDelayed(this,1000);}};h.post(update);d.setOnDismissListener(x->{h.removeCallbacksAndMessages(null);a.design.openDialogs.remove(d);});
    }
    private static void fields(MainActivity a){
        DesignSurface p=new DesignSurface(a,1000,700);p.fontFactor=a.mainFontScale();p.setBackground(DesignTheme.panel(a));AlertDialog d=a.design.dialog(p);p.label("车状态 · 显示项目",30,25,940,60,34);
        DesignSurface rows=new DesignSurface(a,920,VehicleData.FIELDS.length*70);for(int i=0;i<VehicleData.FIELDS.length;i++){VehicleData.Field f=VehicleData.FIELDS[i];rows.label(f.label,10,i*70,600,56,26);Switch toggle=new Switch(a);toggle.setChecked(a.prefs.getBoolean("vehicle_field_"+f.id,true));rows.place(toggle,DesignSurface.rect(760,i*70,130,56,24));toggle.setOnCheckedChangeListener((v,on)->{a.prefs.edit().putBoolean("vehicle_field_"+f.id,on).apply();VehicleStatusService.sync(a);});}p.list(rows,new RectF(40,110,960,595));Button close=a.design.button("返回");p.place(close,DesignSurface.rect(40,624,180,52,24));close.setOnClickListener(v->d.dismiss());a.design.show(d,p);
    }
}
