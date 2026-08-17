package com.acc.acc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 接收 SIMO/VISO 语音动作并交给桥接层处理。 */
public class SimoVoiceReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        new SimoVoiceBridge(context).handleVISOIntent(intent);
    }
}
