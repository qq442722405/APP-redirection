package com.acc.acc;

import java.util.*;

final class VehicleData {
    static final class Field {
        final String id,label,group,method,unit;final String[] keys;
        Field(String id,String label,String group,String method,String unit,String... keys){this.id=id;this.label=label;this.group=group;this.method=method;this.unit=unit;this.keys=keys;}
    }
    static final Field[] FIELDS={
        new Field("soc","电量","power","getDisplaySoc","%","MWHVSOCDisplaySoc","displaySoc"),
        new Field("range","实估续航","power","getRealRemainMileage","km","sRealRemainMileage","displayRange"),
        new Field("cltc","CLTC续航","power","getCLTCRemainMileage","km","HVSOCRemainCLTCMileage"),
        new Field("speed","车速","vehiclemode","getSpeed","km/h","MWCarSpeed"),
        new Field("gear","挡位码","assisteddriving","getGearState","","MWCarGearType","GearState","gearState"),
        new Field("fl","左前胎压","vehiclemode","getTyreFLTirePressure","原始值","MWTyreFLTirePressure"),
        new Field("fr","右前胎压","vehiclemode","getTyreFRTirePressure","原始值","MWTyreFRTirePressure"),
        new Field("rl","左后胎压","vehiclemode","getTyreRLTirePressure","原始值","MWTyreRLTirePressure"),
        new Field("rr","右后胎压","vehiclemode","getTyreRRTirePressure","原始值","MWTyreRRTirePressure"),
        new Field("battery","电池温度","battery","getBatteryTemp","℃","batteryTemp")
    };
    static Double number(Object o){try{double d=o instanceof Number?((Number)o).doubleValue():Double.parseDouble(String.valueOf(o).trim());return Double.isNaN(d)||Double.isInfinite(d)?null:d;}catch(RuntimeException e){return null;}}
    static double read(Map<String,Object> map,Field field){
        for(String key:new String[]{"resultCode","result","ErrCode"})if(map.containsKey(key)){Double code=number(map.get(key));if(code!=null&&code!=0)throw new IllegalArgumentException(key+"="+code);}
        for(String key:field.keys)for(Map.Entry<String,Object> item:map.entrySet())if(item.getKey().equalsIgnoreCase(key)){
            Double value=number(item.getValue());if(value==null)throw new IllegalArgumentException("无效数值："+key);
            if(field.id.equals("soc")&&(value<0||value>100))throw new IllegalArgumentException("电量超出范围");
            return value;
        }
        throw new IllegalArgumentException("响应缺少 "+field.keys[0]);
    }
    static String format(double value){return String.format(Locale.ROOT,"%.1f",value);}
    static String display(Field f,double value){if(f.id.equals("fl")||f.id.equals("fr")||f.id.equals("rl")||f.id.equals("rr"))return String.format(Locale.ROOT,"%.2f bar",value/100d);return format(value)+" "+f.unit;}
}
