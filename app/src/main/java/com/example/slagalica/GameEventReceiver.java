package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class GameEventReceiver extends BroadcastReceiver {
    public static final String ACTION_GAME_FINISHED = "com.example.slagalica.GAME_FINISHED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_GAME_FINISHED.equals(intent.getAction())) {
            String title = intent.getStringExtra("title");
            String message = intent.getStringExtra("message");
            String channelId = intent.getStringExtra("channelId");
            if (channelId == null) channelId = NotificationHelper.CHANNEL_OTHER;
            NotificationHelper.sendRealNotification(context, title, message, channelId);
        }
    }
}
