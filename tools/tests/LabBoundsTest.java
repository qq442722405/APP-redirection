package com.acc.acc;

public final class LabBoundsTest {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    public static void main(String[] args){
        check(LabBounds.fits(0,0,6480,960,6480,960),"whole display valid");
        check(!LabBounds.fits(-1,0,10,10,100,100),"negative x rejected");
        check(!LabBounds.fits(0,-1,10,10,100,100),"negative y rejected");
        check(!LabBounds.fits(0,0,0,10,100,100),"zero width rejected");
        check(!LabBounds.fits(0,0,10,-10,100,100),"negative height rejected");
        check(!LabBounds.fits(99,0,2,10,100,100),"right edge overflow rejected");
        check(!LabBounds.fits(0,99,10,2,100,100),"bottom edge overflow rejected");
        check(!LabBounds.fits(Integer.MAX_VALUE,0,100,10,100,100),"integer overflow rejected");
        int end=0;for(int i=0;i<3;i++){int[] area=LabBounds.third(i,1001,601);check(area[0]==end,"thirds have no gap or overlap");check(LabBounds.fits(area[0],area[1],area[2],area[3],1001,601),"each third fits actual display");end=area[0]+area[2];}
        check(end==1001,"thirds cover odd width entirely");
        boolean rejected=false;try{LabBounds.third(3,1000,700);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"rear display cannot be inferred as fourth third");
        System.out.println("Lab bounds: "+checks+" checks passed.");
    }
}
