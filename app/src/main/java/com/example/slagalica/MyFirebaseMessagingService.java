package com.example.slagalica;

import com.example.slagalica.models.Notification;
import com.example.slagalica.models.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getNotification() == null) return;

        String title   = remoteMessage.getNotification().getTitle();
        String body    = remoteMessage.getNotification().getBody();
        String channel = NotificationHelper.CHANNEL_CHAT;

        if (remoteMessage.getData().containsKey("type")) {
            String type = remoteMessage.getData().get("type");
            if ("ranking".equals(type))      channel = NotificationHelper.CHANNEL_RANKING;
            else if ("rewards".equals(type)) channel = NotificationHelper.CHANNEL_REWARDS;
            else if ("other".equals(type))   channel = NotificationHelper.CHANNEL_OTHER;
        }

        // Snimi u istoriju notifikacija — Spec 11b
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        NotificationRepository.addNotification(new Notification(
                String.valueOf(System.currentTimeMillis()),
                title != null ? title : "",
                body  != null ? body  : "",
                time,
                "chat",
                false
        ));

        NotificationHelper.sendRealNotification(this, title, body, channel);
    }

    @Override
    public void onNewToken(String token) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update("fcmToken", token);
    }
}