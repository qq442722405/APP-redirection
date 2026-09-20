package com.acc.acc;

import android.content.*;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import java.util.*;

/** A modal view inside the Activity window. No secondary Window or coordinate offsets. */
final class InAppDialog extends AlertDialog {
    private final MainActivity activity;
    private View body;
    private CharSequence title="",message="";
    private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    private final Map<Integer,CharSequence> captions=new LinkedHashMap<>();
    private final Map<Integer,DialogInterface.OnClickListener> actions=new LinkedHashMap<>();
    private DialogInterface.OnDismissListener dismissed;
    private DialogInterface.OnCancelListener cancelled;
    private FrameLayout layer;
    private boolean showing,cancelable=true;
    static final Map<MainActivity,ArrayList<InAppDialog>> stacks=new WeakHashMap<>();
    InAppDialog(MainActivity a){super(a);activity=a;}
    @Override public void setTitle(CharSequence value){title=value==null?"":value;}
    @Override public void setMessage(CharSequence value){message=value==null?"":value;}
    @Override public void setView(View view){body=view;}
    @Override public void setView(View view,int l,int t,int r,int b){body=view;}
    @Override public void setOnDismissListener(DialogInterface.OnDismissListener listener){dismissed=listener;}
    @Override public void setOnCancelListener(DialogInterface.OnCancelListener listener){cancelled=listener;}
    @Override public void setCancelable(boolean value){cancelable=value;}
    @Override public boolean isShowing(){return showing;}
    @Override public Button getButton(int which){return buttons.get(which);}
    @Override public void show(){
        if(showing||activity.isFinishing())return;
        DesignSurface panel;
        if(body instanceof DesignSurface)panel=(DesignSurface)body;
        else{
            panel=new DesignSurface(activity,1000,700);panel.setBackground(DesignTheme.panel(activity));panel.fontFactor=activity.mainFontScale();
            panel.label(title.toString(),30,25,940,65,34);
            if(body!=null)panel.place(body,DesignSurface.rect(35,108,930,475,24));
            else {TextView text=panel.label(message.toString(),45,110,910,440,26);text.setMovementMethod(new android.text.method.ScrollingMovementMethod());}
            int x=40;for(int which:captions.keySet()){
                Button b=new Button(activity);b.setText(captions.get(which));buttons.put(which,b);panel.place(b,DesignSurface.rect(x,620,210,52,24));x+=230;
                b.setOnClickListener(v->{DialogInterface.OnClickListener action=actions.get(which);dismiss();if(action!=null)action.onClick(this,which);});
            }
        }
        final DesignSurface content=panel;content.setClickable(true);
        layer=new FrameLayout(activity){
            @Override protected void onMeasure(int ws,int hs){
                int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);float s=Math.min(1f,Math.min(w/content.designWidth,h/content.designHeight));
                content.maxScale=s;content.measure(MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(content.designWidth*s)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(content.designHeight*s)),MeasureSpec.EXACTLY));setMeasuredDimension(w,h);
            }
            @Override protected void onLayout(boolean c,int l,int t,int r,int b){int x=(getWidth()-content.getMeasuredWidth())/2,y=(getHeight()-content.getMeasuredHeight())/2;content.layout(x,y,x+content.getMeasuredWidth(),y+content.getMeasuredHeight());}
        };
        layer.setBackgroundColor(0x99000000);layer.setClickable(true);layer.setFocusableInTouchMode(true);
        if(content.getParent() instanceof ViewGroup)((ViewGroup)content.getParent()).removeView(content);
        layer.addView(content);ViewGroup root=activity.findViewById(android.R.id.content);
        ArrayList<InAppDialog> stack=stacks.computeIfAbsent(activity,k->new ArrayList<>());
        layer.setTranslationZ(100+stack.size());root.addView(layer,new ViewGroup.LayoutParams(-1,-1));stack.add(this);showing=true;layer.requestFocus();
    }
    @Override public void dismiss(){
        if(!showing)return;showing=false;ArrayList<InAppDialog> stack=stacks.get(activity);if(stack!=null){stack.remove(this);if(stack.isEmpty())stacks.remove(activity);}
        if(layer!=null&&layer.getParent() instanceof ViewGroup)((ViewGroup)layer.getParent()).removeView(layer);layer=null;
        if(dismissed!=null)dismissed.onDismiss(this);
    }
    @Override public void cancel(){if(!cancelable)return;if(cancelled!=null)cancelled.onCancel(this);dismiss();}
    static boolean back(MainActivity a){ArrayList<InAppDialog> stack=stacks.get(a);if(stack==null||stack.isEmpty())return false;stack.get(stack.size()-1).cancel();return true;}
    static void clear(MainActivity a){ArrayList<InAppDialog> stack=stacks.get(a);if(stack!=null)for(InAppDialog d:new ArrayList<>(stack))d.dismiss();stacks.remove(a);}
    static final class Builder {
        final InAppDialog dialog;
        Builder(MainActivity a){dialog=new InAppDialog(a);}
        Builder setTitle(CharSequence s){dialog.setTitle(s);return this;}
        Builder setMessage(CharSequence s){dialog.setMessage(s);return this;}
        Builder setView(View v){dialog.setView(v);return this;}
        Builder setNegativeButton(CharSequence s,DialogInterface.OnClickListener l){return button(BUTTON_NEGATIVE,s,l);}
        Builder setPositiveButton(CharSequence s,DialogInterface.OnClickListener l){return button(BUTTON_POSITIVE,s,l);}
        Builder button(int which,CharSequence s,DialogInterface.OnClickListener l){dialog.captions.put(which,s);dialog.actions.put(which,l);return this;}
        Builder setItems(String[] items,DialogInterface.OnClickListener listener){
            DesignSurface p=new DesignSurface(dialog.activity,1000,700);p.setBackground(DesignTheme.panel(dialog.activity));p.fontFactor=dialog.activity.mainFontScale();p.label(dialog.title.toString(),30,25,940,65,34);
            DesignSurface rows=new DesignSurface(dialog.activity,920,Math.max(500,items.length*82));
            for(int i=0;i<items.length;i++){final int index=i;Button b=new Button(dialog.activity);b.setText(items[i]);rows.place(b,DesignSurface.rect(0,i*82,920,68,26));b.setOnClickListener(v->{dialog.dismiss();listener.onClick(dialog,index);});}
            p.list(rows,new RectF(40,110,960,660));dialog.setView(p);return this;
        }
        InAppDialog create(){return dialog;}
        InAppDialog show(){dialog.show();return dialog;}
    }
}
