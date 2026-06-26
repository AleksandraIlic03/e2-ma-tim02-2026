package com.example.slagalica;

import android.content.Context;

import com.example.slagalica.models.Notification;
import com.example.slagalica.models.NotificationRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Poziva se iz HomeActivity.onStart() — proverava propuštene chat poruke
 * i prikazuje lokalnu notifikaciju. Spec 8e bez Cloud Functions.
 */
public class ChatNotificationChecker {

    public static void checkAndNotify(Context context, String currentUserId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) return;

                    String region = userDoc.getString("region");
                    if (region == null || region.isEmpty()) return;

                    Timestamp lastSeen = userDoc.getTimestamp("lastSeenChat");
                    if (lastSeen == null) {
                        // Prvi put — samo postavi timestamp, nema propuštenih
                        db.collection("users").document(currentUserId)
                                .update("lastSeenChat", Timestamp.now());
                        return;
                    }

                    db.collection("chats")
                            .document(region)
                            .collection("messages")
                            .whereGreaterThan("timestamp", lastSeen)
                            .orderBy("timestamp", Query.Direction.ASCENDING)
                            .get()
                            .addOnSuccessListener(snapshots -> {
                                if (snapshots == null || snapshots.isEmpty()) return;

                                int count = 0;
                                String lastName = "";
                                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                                    String senderId = doc.getString("senderId");
                                    if (currentUserId.equals(senderId)) continue;
                                    count++;
                                    String name = doc.getString("senderName");
                                    if (name != null) lastName = name;
                                }

                                if (count == 0) return;

                                String title = "Čet — " + region;
                                String body  = count == 1
                                        ? lastName + " ti je poslao poruku"
                                        : count + " novih poruka u regionalnom četu";

                                // Snimi u istoriju notifikacija — Spec 11b
                                String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                                        .format(new Date());
                                NotificationRepository.addNotification(new Notification(
                                        String.valueOf(System.currentTimeMillis()),
                                        title, body, time, "chat", false
                                ));

                                NotificationHelper.sendRealNotification(
                                        context, title, body,
                                        NotificationHelper.CHANNEL_CHAT);
                            });
                });
    }
}