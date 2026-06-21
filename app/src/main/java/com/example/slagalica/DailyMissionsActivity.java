package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.adapters.MissionAdapter;
import com.example.slagalica.models.Mission;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DailyMissionsActivity extends AppCompatActivity {

    private RecyclerView rvMissions;
    private MaterialCardView cardBonus;
    private MissionAdapter adapter;
    private List<Mission> missionList = new ArrayList<>();
    private FirebaseFirestore db;
    private String userId;
    private String today;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_missions);

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        rvMissions = findViewById(R.id.rvMissions);
        cardBonus = findViewById(R.id.cardBonus);

        rvMissions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MissionAdapter(missionList);
        rvMissions.setAdapter(adapter);

        loadMissions();
    }

    private void loadMissions() {
        if (userId == null) return;

        db.collection("users").document(userId).collection("dailyMissions").document(today)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> data = documentSnapshot.getData();
                        updateMissionList(data);
                    } else {
                        initializeMissions();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Greška pri učitavanju misija", Toast.LENGTH_SHORT).show());
    }

    private void initializeMissions() {
        Map<String, Object> missions = new HashMap<>();
        missions.put("win_game", false);
        missions.put("send_chat", false);
        missions.put("friend_game", false);
        missions.put("tournament_win", false);
        missions.put("bonus_claimed", false);

        db.collection("users").document(userId).collection("dailyMissions").document(today)
                .set(missions)
                .addOnSuccessListener(aVoid -> updateMissionList(missions));
    }

    private void updateMissionList(Map<String, Object> data) {
        missionList.clear();
        missionList.add(new Mission("win_game", "Pobedi partiju", 3));
        missionList.add(new Mission("send_chat", "Pošalji poruku u čet", 3));
        missionList.add(new Mission("friend_game", "Odigraj prijateljsku partiju", 3));
        missionList.add(new Mission("tournament_win", "Pobedi partiju u turniru", 3));

        boolean allCompleted = true;
        for (Mission m : missionList) {
            Boolean completed = (Boolean) data.get(m.getId());
            m.setCompleted(completed != null && completed);
            if (!m.isCompleted()) allCompleted = false;
        }

        if (allCompleted) {
            cardBonus.setVisibility(View.VISIBLE);
            // Proveri da li je bonus već dodat, ako nije dodaj tokene i zvezde
            if (data.get("bonus_claimed") != null && !(Boolean) data.get("bonus_claimed")) {
                claimBonus();
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void claimBonus() {
        db.collection("users").document(userId).collection("dailyMissions").document(today)
                .update("bonus_claimed", true);

        // +3 stars through RankingManager so starsWeekly, starsMonthly and league update too
        RankingManager.updateStars(userId, 3);

        // +2 tokens separately (RankingManager handles only stars)
        db.collection("users").document(userId)
                .update("tokens", com.google.firebase.firestore.FieldValue.increment(2))
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Bonus preuzet! +2 Tokena, +3 Zvezde", Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
