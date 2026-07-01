package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private TextView tvHomeStars, tvHomeTokens, tvHomeLeague;
    private FirebaseFirestore db;
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
        checkForRewards();

        NotificationHelper.createNotificationChannels(this);

        findViewById(R.id.btnNotifications).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, NotificationsActivity.class));
        });

        findViewById(R.id.btnStartMatch).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, WaitingRoomActivity.class);
            intent.putExtra("autoMatch", true);
            startActivity(intent);
        });

        findViewById(R.id.btnTournament).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, TournamentActivity.class));
        });

        findViewById(R.id.btnMissions).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, DailyMissionsActivity.class));
        });

        findViewById(R.id.btnFriendMatch).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, FriendsActivity.class));
        });

        findViewById(R.id.btnChat).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, ChatActivity.class));
        });

        findViewById(R.id.btnMap).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, MapActivity.class));
        });

        findViewById(R.id.btnChallenges).setOnClickListener(v -> {
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid != null) {
                db.collection("users").document(uid).get()
                        .addOnSuccessListener(doc -> {
                            String userRegion = doc.getString("region");
                            if (userRegion != null) {
                                Intent intent = new Intent(HomeActivity.this, ChallengeListActivity.class);
                                intent.putExtra("regionName", userRegion);
                                startActivity(intent);
                            } else {
                                android.widget.Toast.makeText(this, "Region nije pronađen.", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        findViewById(R.id.btnNavRankings).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, RankingActivity.class));
        });

        findViewById(R.id.btnNavFriends).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, FriendsActivity.class));
        });

        findViewById(R.id.btnNavProfile).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
        });
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

                    // Ako je u bazi greškom negativno, prikaži 0
                    if (stars < 0) stars = 0;
                    if (tokens < 0) tokens = 0;

                    tvHomeStars.setText("⭐ " + stars);
                    tvHomeTokens.setText("🎟️ " + tokens);
                    tvHomeLeague.setText("🏆 " + league);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null) userListener.remove();
    }

    private void checkForRewards() {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (userId == null) return;

        db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.contains("pendingReward")) {
                Map<String, Object> reward = (Map<String, Object>) doc.get("pendingReward");
                if (reward != null) {
                    showRewardDialog(reward);
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

        // Gradi poruku iz polja koja RankingManager upisuje: tokens, rank, period
        long tokens = rewardData.get("tokens") != null ? (long) rewardData.get("tokens") : 0;
        long rank = rewardData.get("rank") != null ? (long) rewardData.get("rank") : 0;
        String period = rewardData.get("period") != null ? (String) rewardData.get("period") : "";

        String[] ordinals = {"1.", "2.", "3.", "4.", "5.", "6.", "7.", "8.", "9.", "10."};
        String rankStr = (rank >= 1 && rank <= 10) ? ordinals[(int) rank - 1] + " mesto" : rank + ". mesto";
        tvMsg.setText(period + " rang lista je završena!\nZauzeo si " + rankStr + " i nagrađen si tokenima.");
        tvVal.setText("+" + tokens + " 🎟️");

        // Spec 4g: vizuelna animacija iskakanja (scale + fade in)
        container.setScaleX(0.4f);
        container.setScaleY(0.4f);
        container.setAlpha(0f);
        container.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(500)
                .withEndAction(() -> {
                    // Pulsiranje nagrade posle ulaska
                    tvVal.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300)
                            .withEndAction(() ->
                                    tvVal.animate().scaleX(1f).scaleY(1f).setDuration(300).start())
                            .start();
                })
                .start();

        // Spec 4g: zvučna animacija – sistemski notifikacioni zvuk
        try {
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(
                    getApplicationContext(),
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
        } catch (Exception ignored) {}

        btn.setOnClickListener(v -> {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            // Ukloni pendingReward da se dialog ne pojavi opet
            db.collection("users").document(userId)
                    .update("pendingReward", com.google.firebase.firestore.FieldValue.delete());
            dialog.dismiss();
        });

        dialog.show();
    }
}