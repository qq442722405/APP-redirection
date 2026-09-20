package com.acc.acc;

import java.text.SimpleDateFormat;
import java.util.*;

/** Bounded local diagnostics; no logcat, audio recording, or network upload. */
final class CloseTestLog {
    private static final ArrayDeque<String> lines=new ArrayDeque<>();
    static synchronized void add(String stage,String detail){
        String message=detail==null?"":detail.replace('\r',' ').replace('\n',' ');
        if(message.length()>1400)message=message.substring(0,1400)+"…";
        lines.addLast(new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())+" ["+stage+"] "+message);
        while(lines.size()>60)lines.removeFirst();
    }
    static synchronized String text(){StringBuilder result=new StringBuilder();for(String line:lines)result.append(line).append('\n');return result.toString();}
    static synchronized void clear(){lines.clear();}
}
