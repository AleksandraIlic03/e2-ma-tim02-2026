package com.example.slagalica;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.slagalica.models.Notification;
import com.example.slagalica.models.NotificationRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationHelper {
    public static final String CHANNEL_CHAT = "chat_messages";
    public static final String CHANNEL_RANKING = "ranking_updates";
    public static final String CHANNEL_REWARDS = "rewards_channel";
    public static final String CHANNEL_OTHER = "other_notifications_v2";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel chat = new NotificationChannel(CHANNEL_CHAT, "Čet poruke", NotificationManager.IMPORTANCE_DEFAULT);
            
            NotificationChannel ranking = new NotificationChannel(CHANNEL_RANKING, "Rangiranje", NotificationManager.IMPORTANCE_LOW);
            
            NotificationChannel rewards = new NotificationChannel(CHANNEL_REWARDS, "Nagrade", NotificationManager.IMPORTANCE_HIGH);
            
            NotificationChannel other = new NotificationChannel(CHANNEL_OTHER, "Ostalo", NotificationManager.IMPORTANCE_HIGH);

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

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        NotificationRepository.addNotification(new Notification(
                String.valueOf(System.currentTimeMillis()), title, message, time, type, false));

        Intent intent = new Intent(context, NotificationsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        int priority = NotificationHelper.CHANNEL_OTHER.equals(channelId) || NotificationHelper.CHANNEL_REWARDS.equals(channelId)
                ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
        }
    }
}
