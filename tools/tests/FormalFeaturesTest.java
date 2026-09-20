package com.acc.acc;
import java.util.*;
public final class FormalFeaturesTest {
    static int count;static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;}
    static void reject(Map<String,Object> values,VehicleData.Field field){boolean rejected=false;try{VehicleData.read(values,field);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid telemetry must not be displayed as zero");}
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
        Map<String,Object> data=new HashMap<>();VehicleData.Field soc=VehicleData.FIELDS[0];data.put("result",0);data.put("MWHVSOCDisplaySoc",0);check(VehicleData.read(data,soc)==0,"actual zero accepted");
        data.put("MWHVSOCDisplaySoc","85.4");check(VehicleData.read(data,soc)==85.4,"numeric string accepted");
        data.put("result",-1);reject(data,soc);data.put("result",0);data.put("ErrCode",3);reject(data,soc);data.remove("ErrCode");
        data.put("MWHVSOCDisplaySoc","NaN");reject(data,soc);data.put("MWHVSOCDisplaySoc",101);reject(data,soc);data.remove("MWHVSOCDisplaySoc");reject(data,soc);
        data.put("displaySoc",55);check(VehicleData.read(data,soc)==55,"known alternate field accepted");
        check(VehicleData.display(VehicleData.FIELDS[5],250).equals("2.50 bar"),"CAP tire pressure converted exactly as observed APK");
        System.out.println("Formal features: "+count+" checks passed.");
    }
}
