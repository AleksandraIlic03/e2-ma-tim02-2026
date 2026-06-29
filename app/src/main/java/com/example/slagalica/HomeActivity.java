package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private TextView tvHomeStars, tvHomeTokens, tvHomeLeague;
    private FirebaseFirestore db;
    private boolean friendRequestsInitialized = false;
    private boolean acceptedRequestsInitialized = false;
    private boolean invitesInitialized = false;
    private ListenerRegistration userListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        db = FirebaseFirestore.getInstance();
        tvHomeStars = findViewById(R.id.tvHomeStars);
        tvHomeTokens = findViewById(R.id.tvHomeTokens);
        tvHomeLeague = findViewById(R.id.tvHomeLeague);

        listenToUserData();
        updateOnlineStatus(true);
        checkForRewards();
        grantDailyTokens();

        NotificationHelper.createNotificationChannels(this);

        findViewById(R.id.btnStartMatch).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, WaitingRoomActivity.class);
            intent.putExtra("autoMatch", true);
            startActivity(intent);
        });

        findViewById(R.id.btnNotifications).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnNavProfile).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnNavRankings).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, RankingActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnNavFriends).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, FriendsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnTournament).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, TournamentActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnMissions).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, DailyMissionsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnChat).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, ChatActivity.class));
        });

        findViewById(R.id.btnMap).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, MapActivity.class));
        });
        
        findViewById(R.id.btnFriendMatch).setOnClickListener(v -> {
             Intent intent = new Intent(HomeActivity.this, FriendsActivity.class);
             startActivity(intent);
        });
    }

    private void updateOnlineStatus(boolean online) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            db.collection("users").document(userId).update("isOnline", online, "isInGame", false);
        }
    }

    private void showGameInviteDialog(com.google.firebase.firestore.DocumentSnapshot inviteDoc) {
        String fromUsername = inviteDoc.getString("fromUsername");
        String roomId = inviteDoc.getString("roomId");
        String inviteId = inviteDoc.getId();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Poziv za partiju")
                .setMessage("Igrač " + fromUsername + " vas poziva na partiju.")
                .setPositiveButton("Prihvati", (d, w) -> {
                    FriendsManager.respondToGameInvite(inviteId, "accepted", new FriendsManager.ActionCallback() {
                        @Override
                        public void onSuccess() {
                            Intent intent = new Intent(HomeActivity.this, WaitingRoomActivity.class);
                            intent.putExtra("autoJoinRoomId", roomId);
                            startActivity(intent);
                        }
                        @Override
                        public void onFailure(Exception e) {}
                    });
                })
                .setNegativeButton("Odbij", (d, w) -> {
                    FriendsManager.respondToGameInvite(inviteId, "rejected", null);
                })
                .setCancelable(false)
                .create();

        dialog.show();

        new android.os.Handler().postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                FriendsManager.respondToGameInvite(inviteId, "expired", null);
            }
        }, 10000);
    }

    private void listenToUserData() {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                        FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (userId == null) return;

        userListener = db.collection("users").document(userId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;

                    long stars = snapshot.getLong("stars") != null ? snapshot.getLong("stars") : 0;
                    long tokens = snapshot.getLong("tokens") != null ? snapshot.getLong("tokens") : 0;
                    long league = snapshot.getLong("league") != null ? snapshot.getLong("league") : 0;

                    if (stars < 0) stars = 0;
                    if (tokens < 0) tokens = 0;

                    // Spec: Ako su zvezde promenjene spolja, uskladi ligu automatski
                    int calculatedLeague = RankingManager.calculateLeague(stars);
                    if (calculatedLeague != (int) league) {
                        // Umesto direktnog dijaloga, upisujemo u bazu da bi checkForRewards primetio
                        Map<String, Object> pending = new HashMap<>();
                        pending.put("oldLeague", league);
                        pending.put("newLeague", (long) calculatedLeague);
                        
                        db.collection("users").document(userId).update(
                            "league", (long) calculatedLeague,
                            "pendingLeagueChange", pending
                        );
                        
                        // Pozivamo proveru odmah da ne bismo čekali sledeći ulazak
                        checkForRewards();
                    }

                    tvHomeStars.setText("⭐ " + stars);
                    tvHomeTokens.setText("🎟️ " + tokens);
                    tvHomeLeague.setText(getLeagueEmoji(league) + " " + league);
                });
    }

    private void triggerLeagueNotification(int oldL, int newL) {
        String leagueName = getLeagueName(newL);
        String emoji = getLeagueEmoji(newL);
        String title = newL > oldL ? "Napredovali ste!" : "Obaveštenje o ligi";
        String body = "Sada ste u ligi: " + emoji + " " + leagueName;

        NotificationHelper.sendRealNotification(this, title, body, NotificationHelper.CHANNEL_RANKING);

        runOnUiThread(() -> showLeagueRewardDialog(oldL, newL));
    }

    private String getLeagueEmoji(long league) {
        switch ((int) league) {
            case 1: return "🐺";
            case 2: return "🐻";
            case 3: return "🦁";
            case 4: return "🦅";
            case 5: return "🐉";
            default: return "🐭";
        }
    }

    private void showLeagueRewardDialog(int oldL, int newL) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reward, null);
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_NoActionBar)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvRewardTitle);
        TextView tvMsg = dialogView.findViewById(R.id.tvRewardMessage);
        TextView tvVal = dialogView.findViewById(R.id.tvRewardValue);
        MaterialButton btn = dialogView.findViewById(R.id.btnCollectReward);
        View container = dialogView.findViewById(R.id.rewardContainer);

        String leagueName = getLeagueName(newL);
        String emoji = getLeagueEmoji(newL);
        
        if (newL > oldL) {
            tvTitle.setText("NOVA LIGA!");
            tvMsg.setText("Napredovali ste u novu ligu!");
            btn.setText("U REDU");
        } else {
            tvTitle.setText("OBAVEŠTENJE");
            tvMsg.setText("Nažalost, ispali ste u nižu ligu.");
            btn.setText("U REDU");
        }
        
        tvVal.setText(emoji + " " + leagueName);

        container.setScaleX(0.7f); container.setScaleY(0.7f); container.setAlpha(0f);
        container.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(500).start();

        btn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String getLeagueName(int league) {
        String[] names = {"Miš", "Vuk", "Medved", "Lav", "Orao", "Zmaj"};
        if (league >= 0 && league < names.length) return names[league];
        return "Nepoznato";
    }

    private void grantDailyTokens() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String lastGrant = doc.getString("lastTokenGrantDate");
                
                // Deli tokene samo ako danas već nisu podeljeni
                if (!today.equals(lastGrant)) {
                    Long league = doc.getLong("league");
                    long leagueVal = league != null ? league : 0L;
                    
                    // Spec 8.b: Dodaje tačno onoliko tokena kolika je liga (npr. +3 za Ligu 3)
                    if (leagueVal > 0) {
                        db.collection("users").document(uid).update(
                            "tokens", com.google.firebase.firestore.FieldValue.increment(leagueVal),
                            "lastTokenGrantDate", today
                        );
                    } else {
                        // Čak i ako je Liga 0, ažuriramo datum da ne bi pokušavao stalno
                        db.collection("users").document(uid).update("lastTokenGrantDate", today);
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateOnlineStatus(false);
        if (userListener != null) userListener.remove();
    }

    private void checkForRewards() {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                        FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (userId == null) return;

        db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;
            
            // 1. Provera nagrada (Tokeni)
            if (doc.contains("pendingReward")) {
                Map<String, Object> reward = (Map<String, Object>) doc.get("pendingReward");
                if (reward != null) showRewardDialog(reward);
            }
            
            // 2. Provera promene lige
            if (doc.contains("pendingLeagueChange")) {
                Map<String, Object> leagueData = (Map<String, Object>) doc.get("pendingLeagueChange");
                if (leagueData != null) {
                    long oldL = leagueData.get("oldLeague") != null ? (long) leagueData.get("oldLeague") : 0L;
                    long newL = leagueData.get("newLeague") != null ? (long) leagueData.get("newLeague") : 0L;
                    showLeagueRewardDialog((int) oldL, (int) newL);
                    
                    // Obriši obaveštenje iz baze nakon prikaza
                    db.collection("users").document(userId).update("pendingLeagueChange", com.google.firebase.firestore.FieldValue.delete());
                }
            }
        });
    }

    private void showRewardDialog(Map<String, Object> rewardData) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reward, null);
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_NoActionBar)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvMsg = dialogView.findViewById(R.id.tvRewardMessage);
        TextView tvVal = dialogView.findViewById(R.id.tvRewardValue);
        MaterialButton btn = dialogView.findViewById(R.id.btnCollectReward);
        View container = dialogView.findViewById(R.id.rewardContainer);

        long tokens = rewardData.get("tokens") != null ? (long) rewardData.get("tokens") : 0;
        long rank = rewardData.get("rank") != null ? (long) rewardData.get("rank") : 0;
        String period = rewardData.get("period") != null ? (String) rewardData.get("period") : "";

        String[] ordinals = {"1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9.", "10."};
        String rankStr = (rank >= 1 && rank <= 10) ? ordinals[(int) rank - 1] + " mesto" : rank + ". mesto";
        tvMsg.setText(period + " rang lista je završena!\nZauzeo si " + rankStr + " i nagrađen si tokenima.");
        tvVal.setText("+" + tokens + " 🎟️");

        container.setScaleX(0.4f);
        container.setScaleY(0.4f);
        container.setAlpha(0f);
        container.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(500)
                .withEndAction(() -> {
                    tvVal.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300)
                            .withEndAction(() ->
                                tvVal.animate().scaleX(1f).scaleY(1f).setDuration(300).start())
                            .start();
                })
                .start();

        try {
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(
                    getApplicationContext(),
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
        } catch (Exception ignored) {}

        btn.setOnClickListener(v -> {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            db.collection("users").document(userId)
                    .update("pendingReward", com.google.firebase.firestore.FieldValue.delete());
            dialog.dismiss();
        });

        dialog.show();
    }
}