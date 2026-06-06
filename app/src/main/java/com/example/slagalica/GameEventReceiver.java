package com.example.slagalica;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.slagalica.models.Notification;
import com.example.slagalica.models.NotificationRepository;

public class GameEventReceiver extends BroadcastReceiver {
    public static final String ACTION_GAME_FINISHED = "com.example.slagalica.GAME_FINISHED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_GAME_FINISHED.equals(intent.getAction())) {
            String title = intent.getStringExtra("title");
            String message = intent.getStringExtra("message");
            String type = intent.getStringExtra("type");

            NotificationHelper.sendRealNotification(context, title, message, NotificationHelper.CHANNEL_REWARDS);

            NotificationRepository.addNotification(new Notification(
                    String.valueOf(System.currentTimeMillis()),
                    title,
                    message,
                    "sada",
                    type,
                    false
            ));
        }
    }
}
