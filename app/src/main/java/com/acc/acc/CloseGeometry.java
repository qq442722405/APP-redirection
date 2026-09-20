package com.acc.acc;

/** Reject invalid or ambiguous bounds; never invent a car screen size. */
final class CloseGeometry {
    static float[] swipe(int l,int t,int r,int b,int screenW,int screenH){
        if(l<0||t<0||r>screenW||b>screenH||r-l<240||b-t<200)return null;
        if(screenW>=4000){int first=(int)((long)l*3/screenW),last=(int)((long)(r-1)*3/screenW);if(first!=last)return null;}
        return new float[]{l+(r-l)/2f,t+(b-t)*.28f,t+(b-t)*.82f,Math.min(90,(r-l)*.18f)};
    }
}
