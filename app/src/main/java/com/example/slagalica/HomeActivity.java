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
                                Toast.makeText(this, "Region nije pronađen.", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toast.makeText(this, "Nisi ulogovan.", Toast.LENGTH_SHORT).show();
            }
        });
        
        findViewById(R.id.btnFriendMatch).setOnClickListener(v -> {
             Intent intent = new Intent(HomeActivity.this, FriendsActivity.class);
             startActivity(intent);
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

                    if (stars < 0) stars = 0;
                    if (tokens < 0) tokens = 0;

                    tvHomeStars.setText("⭐ " + stars);
                    tvHomeTokens.setText("🎟️ " + tokens);
                    tvHomeLeague.setText(RankingManager.getLeagueEmoji(league) + " " + league);
                });
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
        if (userListener != null) userListener.remove();
    }
}