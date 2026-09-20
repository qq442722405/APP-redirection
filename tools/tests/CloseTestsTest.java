package com.acc.acc;
public final class CloseTestsTest {
    static int count;
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);count++;}
    public static void main(String[] args)throws Exception{
        check(CloseTestModes.valid("receive_only"),"receipt-only diagnostic can be selected");
        check(CloseTestModes.valid("delayed"),"allow voice panel to disappear before swipe");
        check(!CloseTestModes.valid("force_stop")&&!CloseTestModes.valid(null),"unimplemented modes are rejected");
        check(CloseTestModes.IDS.length==CloseTestModes.LABELS.length,"every selector mode has a label");
        CloseTestLog.clear();for(int i=0;i<80;i++)CloseTestLog.add("test","entry-"+i);
        String log=CloseTestLog.text();check(log.split("\n").length==60,"diagnostics keep at most 60 entries");
        check(!log.contains("entry-19\n")&&log.contains("entry-79"),"keep newest observations");
        CloseTestLog.clear();CloseTestLog.add("input","line 1\nline 2\rline 3");
        check(CloseTestLog.text().split("\n").length==1,"a callback cannot forge extra log lines");
        CloseTestLog.clear();Thread a=new Thread(()->{for(int i=0;i<100;i++)CloseTestLog.add("a","value");});
        Thread b=new Thread(()->{for(int i=0;i<100;i++)CloseTestLog.add("b","value");});a.start();b.start();a.join();b.join();
        check(CloseTestLog.text().split("\n").length==60,"concurrent callback and manual results stay bounded");
        CloseTestLog.clear();check(CloseTestLog.text().isEmpty(),"clear result history");
        System.out.println("Close diagnostics: "+count+" checks passed.");
    }
}
