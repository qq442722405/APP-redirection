package com.acc.acc;

/** Pure validation: reject invalid requests instead of silently clamping them. */
final class LabBounds {
    static boolean fits(int x,int y,int w,int h,int screenW,int screenH){return x>=0&&y>=0&&w>0&&h>0&&(long)x+w<=screenW&&(long)y+h<=screenH;}
    static int[] third(int index,int w,int h){if(index<0||index>2||w<3||h<1)throw new IllegalArgumentException("无效屏幕尺寸");int l=(int)((long)w*index/3),r=(int)((long)w*(index+1)/3);return new int[]{l,0,r-l,h};}
}
