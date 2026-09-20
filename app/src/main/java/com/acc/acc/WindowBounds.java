package com.acc.acc;

/** Screen coordinates, independent of Activity task state. */
final class WindowBounds {
    static int[] resolve(int x,int y,int width,int height,int screenWidth,int screenHeight,boolean fullscreen){
        if(screenWidth<=0||screenHeight<=0)throw new IllegalArgumentException("显示器尺寸无效");
        if(fullscreen)return new int[]{0,0,screenWidth,screenHeight};
        if(width<=0||height<=0)throw new IllegalArgumentException("窗口宽度和高度必须大于 0");
        int left=Math.max(0,Math.min(x,screenWidth-1)),top=Math.max(0,Math.min(y,screenHeight-1));
        int right=(int)Math.min((long)screenWidth,(long)left+width);
        int bottom=(int)Math.min((long)screenHeight,(long)top+height);
        return new int[]{left,top,right,bottom};
    }
}
