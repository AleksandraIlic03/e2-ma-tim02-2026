package com.example.slagalica;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.Map;

public class LeagueService extends Service {
    private ListenerRegistration userListener;
    private FirebaseFirestore db;

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start as foreground service (Vežbe 5, 1.2)
        startForeground(1001, NotificationHelper.createForegroundNotification(this));
        
        setupFirestoreListener();
        
        return START_STICKY;
    }

    private void setupFirestoreListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            stopSelf();
            return;
        }

        if (userListener != null) userListener.remove();

        userListener = db.collection("users").document(uid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;

                    // Provera promene lige (Vežbe 5, 2.1)
                    if (snapshot.contains("pendingLeagueChange")) {
                        Object leagueData = snapshot.get("pendingLeagueChange");
                        if (leagueData instanceof Map) {
                            handleLeagueChange((Map<String, Object>) leagueData);
                        }
                    }
                });
    }

    private void handleLeagueChange(Map<String, Object> data) {
        Object oldLObj = data.get("oldLeague");
        long oldL = oldLObj instanceof Long ? (Long) oldLObj : 0L;
        Object newLObj = data.get("newLeague");
        long newL = newLObj instanceof Long ? (Long) newLObj : 0L;

        String title = newL > oldL ? "Napredovali ste!" : "Obaveštenje o ligi";
        String emoji = RankingManager.getLeagueEmoji(newL);
        String name = RankingManager.calculateLeagueName((int) newL);
        String body = "Sada ste u ligi: " + emoji + " " + name;

        if (!SlagalicaApp.isAppInForeground()) {
            NotificationHelper.sendRealNotification(this, title, body, NotificationHelper.CHANNEL_RANKING);
        }

        // Obriši obaveštenje iz baze
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.collection("users").document(uid)
                    .update("pendingLeagueChange", com.google.firebase.firestore.FieldValue.delete());
        }
        
    }

    @Override
    public void onDestroy() {
        if (userListener != null) userListener.remove();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
