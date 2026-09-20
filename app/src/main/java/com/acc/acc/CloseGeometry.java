package com.acc.acc;

/** Reject invalid or ambiguous bounds; never invent a car screen size. */
final class CloseGeometry {
    static float[] swipe(int l,int t,int r,int b,int screenW,int screenH){
        if(l<0||t<0||r>screenW||b>screenH||r-l<240||b-t<200)return null;
        float center=l+(r-l)/2f,spacing=Math.min(90,(r-l)*.18f);
        // Presets can extend slightly across an estimated screen third. Only the actual
        // gesture must stay in one region; do not reject the entire window for that overlap.
        if(screenW>=4000){int first=(int)((center-spacing)*3/screenW),last=(int)((center+spacing)*3/screenW);if(first!=last)return null;}
        return new float[]{center,t+(b-t)*.28f,t+(b-t)*.82f,spacing};
    }
}
