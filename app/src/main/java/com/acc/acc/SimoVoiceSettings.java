package com.acc.acc;

import android.content.*;
import android.graphics.RectF;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import java.util.*;

final class SimoVoiceSettings {
    static void show(MainActivity a){
        DesignSurface p=a.design.page("SIMO语音启动");AlertDialog d=a.design.dialog(p);
        Switch enabled=new Switch(a),boot=new Switch(a);enabled.setChecked(a.prefs.getBoolean("simo_enabled",false));boot.setChecked(a.prefs.getBoolean("simo_boot",false));
        p.bind(3,enabled);p.bind(5,boot);
        boolean[] all={a.prefs.getBoolean("simo_all_apps",false)};Button[] scope=new Button[2];
        Runnable highlight=()->{p.selected(scope[0],!all[0]);p.selected(scope[1],all[0]);};
        scope[0]=p.action(7,()->{all[0]=false;highlight.run();});scope[1]=p.action(8,()->{all[0]=true;highlight.run();});highlight.run();
        TextView state=(TextView)p.bound.get("e9");state.setMaxLines(5);
        p.action(10,()->apps(a));p.action(11,()->{SimoVoiceService.sync(a);a.design.toast(a.prefs.getBoolean("simo_enabled",false)?"正在重新连接或登记口令":"请先开启并保存");});
        p.action(12,a::showScreenDiagnostics);p.action(14,d::dismiss);
        p.action(13,()->{
            a.prefs.edit().putBoolean("simo_enabled",enabled.isChecked()).putBoolean("simo_boot",boot.isChecked()).putBoolean("simo_all_apps",all[0]).apply();
            SimoVoiceService.sync(a);a.design.toast("SIMO 设置已保存");
        });
        a.design.show(d,p);
        Handler handler=new Handler(Looper.getMainLooper());Runnable update=new Runnable(){public void run(){if(!d.isShowing())return;state.setText(SimoVoiceService.status+"\n"+SimoVoiceService.lastHit);handler.postDelayed(this,1000);}};handler.post(update);
        d.setOnDismissListener(dialog->{handler.removeCallbacksAndMessages(null);a.design.openDialogs.remove(d);});
    }
    static void apps(MainActivity a){
        DesignSurface p=new DesignSurface(a,1000,700);p.fontFactor=a.mainFontScale();p.setBackground(DesignTheme.panel(a));
        p.label("SIMO · APP 口令",30,30,920,48,36);p.label("按已保存范围列出 APP；说“打开＋名称/别名”。修改立即保存。",40,91,920,42,22);
        DesignSurface rows=new DesignSurface(a,920,440);AlertDialog d=a.design.dialog(p);
        Runnable[] refresh={null};refresh[0]=()->{
            rows.removeAllViews();rows.placed.clear();List<VoiceCommands.App> apps=SimoApps.list(a,true);rows.designHeight=Math.max(440,apps.size()*82);
            for(int i=0;i<apps.size();i++){
                VoiceCommands.App app=apps.get(i);int y=i*82;
                TextView name=rows.label(app.name,0,y,340,64,25);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                Button alias=a.design.button(app.alias.isEmpty()?"设置别名":app.alias);rows.place(alias,DesignSurface.rect(355,y,360,64,24));alias.setSingleLine(true);alias.setEllipsize(android.text.TextUtils.TruncateAt.END);alias.setOnClickListener(v->alias(a,app,refresh[0]));
                Switch on=new Switch(a);on.setText("");on.setChecked(!a.prefs.getBoolean("simo_exclude_"+app.pkg,false));rows.place(on,DesignSurface.rect(755,y,130,64,22));
                on.setOnCheckedChangeListener((button,value)->a.prefs.edit().putBoolean("simo_exclude_"+app.pkg,!value).apply());
            }
            if(apps.isEmpty())rows.label("暂无 APP，请先在主界面添加，或保存“全部第三方 APP”范围",0,0,900,110,25);
            rows.requestLayout();
        };refresh[0].run();p.list(rows,new RectF(40,155,960,585));
        Button close=a.design.button("关闭");p.place(close,DesignSurface.rect(53,606,120,50,24));close.setOnClickListener(v->d.dismiss());a.design.show(d,p);
    }
    static void alias(MainActivity a,VoiceCommands.App app,Runnable refreshed){
        DesignSurface p=new DesignSurface(a,1000,500);p.fontFactor=a.mainFontScale();p.setBackground(DesignTheme.panel(a));AlertDialog d=a.design.dialog(p);
        TextView title=p.label("语音别名："+app.name,30,30,940,60,32);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        p.label("填写名称，不加“打开”。原 APP 名称仍可使用；清空可删除别名。",50,116,900,70,23);
        EditText value=a.textField("例如：音乐",app.alias);value.setSingleLine(true);p.place(value,DesignSurface.rect(50,218,890,60,28));
        Button cancel=a.design.button("取消"),save=a.design.button("保存");p.place(cancel,DesignSurface.rect(53,390,120,50,24));p.place(save,DesignSurface.rect(193,390,120,50,24));
        cancel.setOnClickListener(v->d.dismiss());save.setOnClickListener(v->{
            String alias=VoiceCommands.normalize(value.getText().toString());
            if(alias.length()>60){a.design.toast("别名最多 60 个字符");return;}
            if(!alias.isEmpty())for(VoiceCommands.App other:SimoApps.list(a,false))if(!other.pkg.equals(app.pkg)&&(alias.equals(VoiceCommands.normalize(other.name))||alias.equals(VoiceCommands.normalize(other.alias)))){a.design.toast("名称与其他 APP 重复，请换一个别名");return;}
            a.prefs.edit().putString("simo_alias_"+app.pkg,alias).apply();refreshed.run();d.dismiss();
        });a.design.show(d,p);
    }
}
