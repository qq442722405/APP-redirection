package com.acc.acc;

import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** JSON pixels are design pixels, exactly as in the supplied HTML editor.
 * Re-measure native bounds instead of visually transforming the root: hit testing
 * and keyboard focus therefore use the same coordinates as the visible controls. */
final class DesignSurface extends FrameLayout {
    final Map<String,JSONObject> elements=new LinkedHashMap<>();
    final Map<View,JSONObject> placed=new LinkedHashMap<>();
    final Map<String,View> bound=new HashMap<>();
    final Map<View,Float> switchScales=new IdentityHashMap<>();
    float designWidth,designHeight,scale=1f,fontFactor=1f,maxScale=1f;
    boolean contentSize=false;

    DesignSurface(Context context,String page){
        super(context);
        try(InputStream in=context.getAssets().open("layouts/"+page+".json")){
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] bytes=new byte[4096]; int n;
            while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            JSONArray array=new JSONObject(out.toString("UTF-8")).getJSONArray("elements");
            for(int i=0;i<array.length();i++){
                JSONObject e=array.getJSONObject(i); elements.put(e.optString("id","e"+i),e);
            }
            JSONObject root=spec(0); designWidth=(float)root.getDouble("width"); designHeight=(float)root.getDouble("height");
        }catch(Exception e){throw new IllegalStateException("无法读取布局："+page,e);}
        setBackground(background(false));
        for(Map.Entry<String,JSONObject> entry:elements.entrySet()){
            JSONObject e=entry.getValue();
            if("text".equals(e.optString("type"))){
                TextView t=new TextView(context); t.setText(e.optString("content")); bind(entry.getKey(),t);
            }else if("window".equals(e.optString("type"))&&!"e0".equals(entry.getKey())){
                View panel=new View(context);panel.setBackground(background(false));bind(entry.getKey(),panel);
            }
        }
    }
    DesignSurface(Context context,float width,float height){super(context);designWidth=width;designHeight=height;}
    JSONObject spec(int index){return elements.get("e"+index);}
    static GradientDrawable background(boolean selected){
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                selected?new int[]{0xff0e7490,0xff155e75}:new int[]{0xff1e293b,0xff182234});
        bg.setCornerRadius(6); bg.setStroke(selected?2:1,selected?0xff67e8f9:0xff475569);return bg;
    }
    static GradientDrawable buttonBackground(){
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff3b82f6,0xff1d4ed8});
        bg.setCornerRadius(6);return bg;
    }
    static JSONObject rect(float x,float y,float w,float h,float font){
        JSONObject o=new JSONObject();try{o.put("left",x);o.put("top",y);o.put("width",w);o.put("height",h);o.put("fontSize",font);}catch(JSONException ignored){}return o;
    }
    void hide(int index){View old=bound.remove("e"+index);if(old!=null){placed.remove(old);removeView(old);}}
    <T extends View> T bind(int index,T view){return bind("e"+index,view);}
    <T extends View> T bind(String id,T view){
        JSONObject e=elements.get(id);if(e==null)throw new IllegalArgumentException("布局缺少控件："+id);
        View old=bound.get(id);if(old!=null){placed.remove(old);removeView(old);}
        bound.put(id,view);return place(view,e);
    }
    <T extends View> T place(T view,JSONObject e){
        if(view.getParent() instanceof ViewGroup)((ViewGroup)view.getParent()).removeView(view);
        if(view instanceof TextView){
            TextView t=(TextView)view; t.setTextColor(Color.WHITE);t.setIncludeFontPadding(false);
            t.setMinWidth(0);t.setMinHeight(0);t.setMinimumWidth(0);t.setMinimumHeight(0);
            t.setPadding(0,0,0,0);t.setGravity(view instanceof Button?Gravity.CENTER:Gravity.CENTER_VERTICAL);
            if(view instanceof Button){((Button)view).setAllCaps(false);view.setBackground(buttonBackground());}
            if(view instanceof EditText){view.setBackground(background(false));t.setPadding(5,0,5,0);}
        }
        view.setElevation(0);view.setTranslationZ(e.optBoolean("isBottom",false)?0:e.optInt("zIndex",100)*.001f);
        placed.put(view,e);addView(view,new FrameLayout.LayoutParams(1,1));return view;
    }
    Button action(int index,Runnable action){
        Button b=new Button(getContext());b.setText(spec(index).optString("text"));
        b.setOnClickListener(v->action.run());return bind(index,b);
    }
    void selected(View view,boolean value){view.setBackground(value?background(true):buttonBackground());}
    TextView label(String text,float x,float y,float w,float h,float font){
        TextView t=new TextView(getContext());t.setText(text);return place(t,rect(x,y,w,h,font));
    }
    RectF bounds(int... indices){
        RectF result=new RectF();boolean first=true;
        for(int i:indices){JSONObject e=spec(i);float x=(float)e.optDouble("left"),y=(float)e.optDouble("top");
            RectF r=new RectF(x,y,x+(float)e.optDouble("width"),y+(float)e.optDouble("height"));
            if(first){result.set(r);first=false;}else result.union(r);
        }return result;
    }
    void list(DesignSurface items,RectF bounds){
        PixelScroll scroll=new PixelScroll(getContext(),items);
        place(scroll,rect(bounds.left,bounds.top,bounds.width(),bounds.height(),16));
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int availableWidth=MeasureSpec.getSize(widthSpec),availableHeight=MeasureSpec.getSize(heightSpec);
        if(!contentSize){
            float sx=availableWidth/designWidth;
            float sy=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?sx:availableHeight/designHeight;
            scale=Math.max(.01f,Math.min(maxScale,Math.min(sx,sy)));
        }
        int w=contentSize?Math.round(designWidth*scale):availableWidth;
        int h=contentSize?Math.round(designHeight*scale):availableHeight;
        for(Map.Entry<View,JSONObject> item:placed.entrySet()){
            View v=item.getKey();JSONObject e=item.getValue();
            int cw=Math.max(1,Math.round((float)e.optDouble("width",1)*scale));
            int ch=Math.max(1,Math.round((float)e.optDouble("height",1)*scale));
            if(v instanceof TextView)((TextView)v).setTextSize(TypedValue.COMPLEX_UNIT_PX,(float)e.optDouble("fontSize",16)*scale*fontFactor);
            if(v instanceof Switch&&(!switchScales.containsKey(v)||switchScales.get(v)!=scale)){
                Switch toggle=(Switch)v;switchScales.put(v,scale);
                GradientDrawable thumb=new GradientDrawable();thumb.setColor(Color.WHITE);thumb.setCornerRadius(20*scale);thumb.setSize(Math.max(1,Math.round(32*scale)),Math.max(1,Math.round(32*scale)));
                StateListDrawable track=new StateListDrawable();
                for(boolean checked:new boolean[]{true,false}){
                    GradientDrawable shape=new GradientDrawable();shape.setColor(checked?0xff2563eb:0xff475569);shape.setCornerRadius(20*scale);shape.setSize(Math.max(1,Math.round(80*scale)),Math.max(1,Math.round(36*scale)));
                    track.addState(checked?new int[]{android.R.attr.state_checked}:new int[]{},shape);
                }
                toggle.setThumbDrawable(thumb);toggle.setTrackDrawable(track);toggle.setSwitchMinWidth(Math.round(80*scale));toggle.setSwitchPadding(0);toggle.setSplitTrack(false);
            }
            if(v instanceof DesignSurface){((DesignSurface)v).maxScale=scale;((DesignSurface)v).fontFactor=fontFactor;}
            if(v instanceof PixelScroll)((PixelScroll)v).setScale(scale,fontFactor);
            v.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w,h);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        for(Map.Entry<View,JSONObject> item:placed.entrySet()){
            View v=item.getKey();JSONObject e=item.getValue();
            int x=Math.round((float)e.optDouble("left")*scale),y=Math.round((float)e.optDouble("top")*scale);
            v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());
        }
    }
    static final class PixelScroll extends ScrollView {
        final DesignSurface items;
        PixelScroll(Context c,DesignSurface items){
            super(c);this.items=items;items.contentSize=true;
            HorizontalScrollView horizontal=new HorizontalScrollView(c);
            horizontal.setHorizontalScrollBarEnabled(false);
            horizontal.addView(items,new HorizontalScrollView.LayoutParams(-2,-2));
            addView(horizontal,new ScrollView.LayoutParams(-1,-2));setVerticalScrollBarEnabled(false);
        }
        void setScale(float scale,float font){items.scale=scale;items.fontFactor=font;}
    }
}
