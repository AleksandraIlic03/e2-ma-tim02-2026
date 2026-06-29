package com.example.slagalica;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import android.content.Intent;
import android.view.View;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class SlagalicaApp extends Application implements Application.ActivityLifecycleCallbacks {

    private FirebaseFirestore db;

    // Trenutno vidljivi (foreground) Activity - za prikaz in-app dijaloga
    @Nullable
    private Activity currentActivity;

    private int startedActivities = 0;

    // Activity-ji koji se racunaju kao "u igri"
    private static final Set<String> GAME_ACTIVITIES = new HashSet<>(Arrays.asList(
            "WaitingRoomActivity",
            "KoZnaZnaActivity",
            "SpojniceActivity",
            "AsocijacijeActivity",
            "SkockoActivity",
            "KorakPoKorakActivity",
            "MojBrojActivity"
    ));

    // Globalni listeneri
    private ListenerRegistration inviteListener, requestListener, acceptedListener;

    // Flagovi za preskakanje prvog (inicijalnog) snapshota
    private boolean invitesInitialized = false;
    private boolean friendRequestsInitialized = false;
    private boolean acceptedRequestsInitialized = false;

    private FirebaseAuth.AuthStateListener authStateListener;

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();

        // Kanali se prave jednom, na startu app-a
        NotificationHelper.createNotificationChannels(this);

        registerActivityLifecycleCallbacks(this);

        // Pokreni/zaustavi listenere u zavisnosti od toga da li je korisnik ulogovan
        authStateListener = firebaseAuth -> {
            if (firebaseAuth.getCurrentUser() != null) {
                // Login: ako je app u prvom planu -> online
                if (startedActivities > 0) {
                    writeStatus(true, isGameActivity(currentActivity));
                }
                startGlobalListeners(firebaseAuth.getCurrentUser().getUid());
            } else {
                // Logout/expired: samo gasimo listenere.
                // Offline se upisuje u SlagalicaApp.logout() PRE signOut-a.
                stopGlobalListeners();
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    // ----------------- Presence (online / in-game) -----------------

    private boolean isGameActivity(@Nullable Activity a) {
        return a != null && GAME_ACTIVITIES.contains(a.getClass().getSimpleName());
    }

    private void writeStatus(boolean online, boolean inGame) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).update("isOnline", online, "isInGame", inGame);
    }


    public static void logout(@Nullable Runnable onDone) {
        FirebaseFirestore fdb = FirebaseFirestore.getInstance();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            fdb.collection("users").document(uid)
                    .update("isOnline", false, "isInGame", false)
                    .addOnCompleteListener(task -> {
                        FirebaseAuth.getInstance().signOut();
                        if (onDone != null) onDone.run();
                    });
        } else {
            FirebaseAuth.getInstance().signOut();
            if (onDone != null) onDone.run();
        }
    }

    // ----------------- Globalni Firestore listeneri -----------------

    private void startGlobalListeners(String userId) {
        // Da ne dupliramo ako su vec aktivni
        stopGlobalListeners();
        invitesInitialized = false;
        friendRequestsInitialized = false;
        acceptedRequestsInitialized = false;

        // 1. Pozivi za partiju
        inviteListener = db.collection("gameInvites")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    if (!invitesInitialized) {
                        invitesInitialized = true;
                        return;
                    }
                    for (DocumentChange dc : snapshot.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            DocumentSnapshot doc = dc.getDocument();
                            String fromUsername = doc.getString("fromUsername");
                            if (fromUsername == null) fromUsername = "Igrac";

                            NotificationHelper.sendRealNotification(this,
                                    "Poziv za partiju",
                                    "Igrac " + fromUsername + " vas poziva na partiju.",
                                    NotificationHelper.CHANNEL_OTHER);

                            // In-app dijalog samo ako je neki ekran trenutno vidljiv
                            showGameInviteDialog(doc);
                        }
                    }
                });

        // 2. Dolazni zahtevi za prijateljstvo
        requestListener = db.collection("friendRequests")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    if (!friendRequestsInitialized) {
                        friendRequestsInitialized = true;
                        return;
                    }
                    for (DocumentChange dc : snapshot.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            String fromUsername = dc.getDocument().getString("fromUsername");
                            if (fromUsername == null) fromUsername = "Neko";
                            NotificationHelper.sendRealNotification(this,
                                    "Novi zahtev za prijateljstvo",
                                    "Korisnik " + fromUsername + " vam je poslao zahtev.",
                                    NotificationHelper.CHANNEL_OTHER);
                        }
                    }
                });

        // 3. Prihvaceni zahtevi (kad drugi prihvati nas zahtev)
        acceptedListener = db.collection("friendRequests")
                .whereEqualTo("fromUserId", userId)
                .whereEqualTo("status", "accepted")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    if (!acceptedRequestsInitialized) {
                        acceptedRequestsInitialized = true;
                        return;
                    }
                    for (DocumentChange dc : snapshot.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED
                                || dc.getType() == DocumentChange.Type.MODIFIED) {
                            String status = dc.getDocument().getString("status");
                            if ("accepted".equals(status)) {
                                NotificationHelper.sendRealNotification(this,
                                        "Zahtev prihvacen",
                                        "Vas zahtev za prijateljstvo je prihvacen!",
                                        NotificationHelper.CHANNEL_OTHER);
                            }
                        }
                    }
                });
    }

    private void stopGlobalListeners() {
        if (inviteListener != null) { inviteListener.remove(); inviteListener = null; }
        if (requestListener != null) { requestListener.remove(); requestListener = null; }
        if (acceptedListener != null) { acceptedListener.remove(); acceptedListener = null; }
    }

    private void showGameInviteDialog(DocumentSnapshot inviteDoc) {
        final Activity activity = currentActivity;
        // Ako nijedan ekran nije u prvom planu, samo system notifikacija (vec poslata)
        if (activity == null || activity.isFinishing()) return;

        final String fromUsername = inviteDoc.getString("fromUsername");
        final String roomId = inviteDoc.getString("roomId");
        final String inviteId = inviteDoc.getId();

        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;

            View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_game_invite, null);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            android.widget.TextView tvMsg = dialogView.findViewById(R.id.tvInviteMessage);
            com.google.android.material.button.MaterialButton btnAccept =
                    dialogView.findViewById(R.id.btnInviteAccept);
            com.google.android.material.button.MaterialButton btnReject =
                    dialogView.findViewById(R.id.btnInviteReject);
            View container = dialogView.findViewById(R.id.inviteContainer);

            tvMsg.setText("Igrac " + (fromUsername != null ? fromUsername : "Igrac")
                    + " vas poziva na partiju.");

            // Animacija pojavljivanja (kao reward dijalozi)
            container.setScaleX(0.7f);
            container.setScaleY(0.7f);
            container.setAlpha(0f);
            container.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start();

            btnAccept.setOnClickListener(v -> {
                dialog.dismiss();
                FriendsManager.respondToGameInvite(inviteId, "accepted", new FriendsManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        Intent intent = new Intent(activity, WaitingRoomActivity.class);
                        intent.putExtra("autoJoinRoomId", roomId);
                        activity.startActivity(intent);
                    }
                    @Override
                    public void onFailure(Exception e) {}
                });
            });

            btnReject.setOnClickListener(v -> {
                dialog.dismiss();
                FriendsManager.respondToGameInvite(inviteId, "rejected", null);
            });

            dialog.show();

            // Auto-istek nakon 10s ako korisnik ne reaguje
            new android.os.Handler().postDelayed(() -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                    FriendsManager.respondToGameInvite(inviteId, "expired", null);
                }
            }, 10000);
        });
    }

    // ----------------- ActivityLifecycleCallbacks -----------------

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = activity;
        // Foreground + (mozda) u igri, samo ako je korisnik ulogovan
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            writeStatus(true, isGameActivity(activity));
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (currentActivity == activity) currentActivity = null;
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        startedActivities++;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        startedActivities--;
        // App otisla u pozadinu -> offline (ako je jos ulogovan)
        if (startedActivities == 0 && FirebaseAuth.getInstance().getCurrentUser() != null) {
            writeStatus(false, false);
        }
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) {}
}