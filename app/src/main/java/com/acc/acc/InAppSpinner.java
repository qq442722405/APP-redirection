package com.acc.acc;

import android.widget.*;

/** Keep picker choices in the same Activity coordinate space as other panels. */
final class InAppSpinner extends Spinner {
    private final MainActivity activity;
    InAppSpinner(MainActivity a){super(a);activity=a;}
    @Override public boolean performClick(){SpinnerAdapter adapter=getAdapter();if(adapter==null)return false;String[] options=new String[adapter.getCount()];for(int i=0;i<options.length;i++)options[i]=String.valueOf(adapter.getItem(i));new InAppDialog.Builder(activity).setTitle("选择窗口预设").setItems(options,(d,index)->setSelection(index)).show();return true;}
}
