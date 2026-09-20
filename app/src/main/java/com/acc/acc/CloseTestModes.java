package com.acc.acc;

final class CloseTestModes {
    static final String[] IDS={"gesture","slow","delayed","back","back_twice","receive_only"};
    static final String[] LABELS={"三指下滑 380ms（原方式）","慢速三指 900ms","延迟 2 秒后三指下滑","返回一次","返回两次","只记录语音，不执行关闭"};
    static int index(String mode){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(mode))return i;return 0;}
    static String label(String mode){return LABELS[index(mode)];}
    static boolean valid(String mode){for(String id:IDS)if(id.equals(mode))return true;return false;}
}
