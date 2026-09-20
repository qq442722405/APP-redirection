package com.acc.acc;
import java.util.*;
public final class FormalFeaturesTest {
    static int count;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;}
    static void invalidBounds(int w,int h,int sw,int sh){boolean rejected=false;try{WindowBounds.resolve(0,0,w,h,sw,sh,false);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid window must fail rather than become fullscreen");}
    public static void main(String[] args){
        check(AppGrid.columns(1972,200,20,8)==8,"configured row maximum respected");
        check(AppGrid.columns(600,200,20,8)==2,"overflow wraps instead of horizontal scrolling");
        check(AppGrid.columns(1972,200,20,20)==9,"physical space limits columns");
        check(AppGrid.rows(9,8)==2,"ninth APP goes to next row");
        check(AppGrid.rows(17,8)==3,"third row retained");
        float[] gesture=CloseGeometry.swipe(2160,0,4320,960,6480,960);check(gesture!=null&&gesture[0]==3240,"center APP gesture remains in center");
        check(CloseGeometry.swipe(0,0,4320,960,6480,960)==null,"cross-region close rejected");
        check(CloseGeometry.swipe(-10,0,1000,960,6480,960)==null,"negative close coordinates rejected");
        check(CloseGeometry.swipe(4320,0,7000,960,6480,960)==null,"out of bounds close rejected");
        check(CloseGeometry.swipe(0,0,200,180,6480,960)==null,"tiny ambiguous windows rejected");
        check(CloseGeometry.swipe(2288,80,4448,852,6480,960)!=null,"existing center preset must not be blocked merely for crossing the estimated third");
        check(CloseGeometry.swipe(105,0,2288,960,6480,960)!=null,"existing left preset gesture remains inside its region");
        check(CloseGeometry.swipe(1900,0,2400,960,6480,960)==null,"actual three-finger paths must not straddle a region boundary");
        check(Arrays.equals(WindowBounds.resolve(2288,80,2160,772,6480,960,false),new int[]{2288,80,4448,852}),"center preset keeps exact position and size");
        check(Arrays.equals(WindowBounds.resolve(4320,0,2160,960,6480,960,false),new int[]{4320,0,6480,960}),"right preset reaches display edge");
        check(Arrays.equals(WindowBounds.resolve(10,20,Integer.MAX_VALUE,Integer.MAX_VALUE,6480,960,false),new int[]{10,20,6480,960}),"overflow-safe clipping");
        check(Arrays.equals(WindowBounds.resolve(-10,-20,600,400,6480,960,false),new int[]{0,0,600,400}),"negative origin clamps while preserving size");
        check(Arrays.equals(WindowBounds.resolve(2288,80,0,0,6480,960,true),new int[]{0,0,6480,960}),"only explicit fullscreen may ignore zero dimensions");
        check(Arrays.equals(WindowBounds.resolve(500,900,2000,900,1280,720,false),new int[]{500,719,1280,720}),"bounds use selected display dimensions");
        invalidBounds(0,400,6480,960);invalidBounds(600,0,6480,960);invalidBounds(-1,400,6480,960);invalidBounds(600,400,0,960);
        System.out.println("Formal features: "+count+" checks passed.");
    }
}
