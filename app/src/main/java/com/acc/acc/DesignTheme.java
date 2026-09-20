package com.acc.acc;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

final class DesignTheme {
    static ThemePalette palette(Context context){return ThemePalette.forId(context.getSharedPreferences(MainActivity.PREF,0).getString("design_theme","purple"));}
    static class Background extends GradientDrawable {
        final int role;
        Background(Context context,int role){this.role=role;setOrientation(Orientation.TL_BR);setCornerRadius(6);update(context);}
        void update(Context context){
            ThemePalette p=palette(context);
            setColors(role==2?new int[]{p.selected,p.button}:role==1?new int[]{p.button,p.buttonEnd}:new int[]{p.panel,p.panelEnd});
            setStroke(role==2?2:1,role==2?p.accent:p.border);
        }
    }
    static GradientDrawable panel(Context c){return new Background(c,0);}
    static GradientDrawable button(Context c,boolean selected){return new Background(c,selected?2:1);}
    static Drawable resource(Context c,int resource){
        if(resource==R.drawable.card_selected)return button(c,true);
        if(resource==R.drawable.button||resource==R.drawable.floating_app_card)return button(c,false);
        if(resource==R.drawable.card)return panel(c);
        return resource==0?null:c.getDrawable(resource);
    }
    static GradientDrawable swatch(String id,boolean selected){
        ThemePalette p=ThemePalette.forId(id);GradientDrawable drawable=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{p.button,p.buttonEnd});
        drawable.setCornerRadius(6);drawable.setStroke(selected?3:1,selected?0xffffffff:p.border);return drawable;
    }
    static void refresh(View view){
        if(view.getBackground() instanceof Background)((Background)view.getBackground()).update(view.getContext());
        if(view instanceof DesignSurface){((DesignSurface)view).switchScales.clear();view.requestLayout();}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)refresh(group.getChildAt(i));}
        view.invalidate();
    }
}
