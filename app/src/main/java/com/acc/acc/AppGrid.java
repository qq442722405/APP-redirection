package com.acc.acc;

final class AppGrid {
    static int columns(float width,float tile,float gap,int limit){return Math.max(1,Math.min(Math.max(1,limit),(int)Math.floor((width+gap)/(tile+gap))));}
    static int rows(int count,int columns){return Math.max(1,(count+columns-1)/columns);}
}
