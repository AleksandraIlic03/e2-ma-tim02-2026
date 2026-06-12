package com.example.slagalica;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {
    public static final String CHANNEL_CHAT = "chat_messages";
    public static final String CHANNEL_RANKING = "ranking_updates";
    public static final String CHANNEL_REWARDS = "rewards_channel";
    public static final String CHANNEL_OTHER = "other_notifications";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel chat = new NotificationChannel(CHANNEL_CHAT, "Čet poruke", NotificationManager.IMPORTANCE_DEFAULT);
            
            NotificationChannel ranking = new NotificationChannel(CHANNEL_RANKING, "Rangiranje", NotificationManager.IMPORTANCE_LOW);
            
            NotificationChannel rewards = new NotificationChannel(CHANNEL_REWARDS, "Nagrade", NotificationManager.IMPORTANCE_HIGH);
            
            NotificationChannel other = new NotificationChannel(CHANNEL_OTHER, "Ostalo", NotificationManager.IMPORTANCE_DEFAULT);

            manager.createNotificationChannel(chat);
            manager.createNotificationChannel(ranking);
            manager.createNotificationChannel(rewards);
            manager.createNotificationChannel(other);
        }
    }

    public static void sendRealNotification(Context context, String title, String message, String channelId) {
        String type = "other";
        if (CHANNEL_CHAT.equals(channelId)) type = "chat";
        else if (CHANNEL_RANKING.equals(channelId)) type = "ranking";
        else if (CHANNEL_REWARDS.equals(channelId)) type = "rewards";

        NotificationDbHelper dbHelper = new NotificationDbHelper(context);
        dbHelper.addNotification(new com.example.slagalica.models.Notification(
                null, title, message, "danas", type, false
        ));

        android.content.Intent intent = new android.content.Intent(context, NotificationsActivity.class);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {

        }
    }
}
