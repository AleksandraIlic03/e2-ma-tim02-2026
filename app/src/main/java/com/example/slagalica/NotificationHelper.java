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
    public static final String CHANNEL_CHAT    = "chat_channel";
    public static final String CHANNEL_RANKING = "ranking_updates_v3";
    public static final String CHANNEL_REWARDS = "rewards_channel";
    public static final String CHANNEL_OTHER   = "other_notifications_v2";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_CHAT, "Čet poruke", NotificationManager.IMPORTANCE_HIGH));

            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_RANKING, "Rangiranje", NotificationManager.IMPORTANCE_HIGH));
            manager.getNotificationChannel(CHANNEL_RANKING).setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_REWARDS, "Nagrade", NotificationManager.IMPORTANCE_HIGH));

            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_OTHER, "Ostalo", NotificationManager.IMPORTANCE_HIGH));
        }
    }

    public static void sendRealNotification(Context context, String title, String message, String channelId) {
        String type = "other";
        if (CHANNEL_CHAT.equals(channelId))    type = "chat";
        else if (CHANNEL_RANKING.equals(channelId)) type = "ranking";
        else if (CHANNEL_REWARDS.equals(channelId)) type = "rewards";

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        NotificationRepository.addNotification(new Notification(
                String.valueOf(System.currentTimeMillis()), title, message, time, type, false));

        Intent intent = new Intent(context, NotificationsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        int priority = (CHANNEL_OTHER.equals(channelId) || CHANNEL_REWARDS.equals(channelId) || CHANNEL_RANKING.equals(channelId))
                ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context)
                    .notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException ignored) {}
    }

    public static android.app.Notification createForegroundNotification(Context context) {
        Intent intent = new Intent(context, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(context, CHANNEL_OTHER)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    public static void showMissionCompletedDialog(android.app.Activity activity, String missionName) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            android.view.View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_reward, null);
            androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(
                    activity, android.R.style.Theme_Material_Light_Dialog_NoActionBar)
                    .setView(dialogView)
                    .setCancelable(true)
                    .create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            android.widget.TextView tvTitle   = dialogView.findViewById(R.id.tvRewardTitle);
            android.widget.TextView tvMessage = dialogView.findViewById(R.id.tvRewardMessage);
            android.widget.TextView tvValue   = dialogView.findViewById(R.id.tvRewardValue);
            com.google.android.material.button.MaterialButton btn = dialogView.findViewById(R.id.btnCollectReward);
            android.view.View container = dialogView.findViewById(R.id.rewardContainer);

            tvTitle.setText("MISIJA ZAVRŠENA!");
            tvMessage.setText(missionName);
            tvValue.setText("+3 ⭐");
            btn.setText("ODLIČNO!");

            container.setScaleX(0.7f);
            container.setScaleY(0.7f);
            container.setAlpha(0f);
            container.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(400).start();

            btn.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        });
    }
}
