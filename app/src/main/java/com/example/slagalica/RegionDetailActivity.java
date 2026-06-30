package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.firestore.FirebaseFirestore;

public class RegionDetailActivity extends AppCompatActivity {

    private String regionName;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_region_detail);

        db = FirebaseFirestore.getInstance();
        regionName = getIntent().getStringExtra("regionName");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(regionName);
        }

        ((TextView) findViewById(R.id.tvRegionName)).setText(regionName);
        ((ImageView) findViewById(R.id.ivRegionIcon)).setImageResource(RegionUtils.getIconRes(regionName));

        findViewById(R.id.btnRegionChallenges).setOnClickListener(v -> {
            Intent intent = new Intent(this, ChallengeListActivity.class);
            intent.putExtra("regionName", regionName);
            startActivity(intent);
        });

        loadRegionStats();
    }

    private void loadRegionStats() {
        // 1. Osvojena mesta iz regions kolekcije
        db.collection("regions").document(regionName).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                long first = doc.getLong("firstPlaces") != null ? doc.getLong("firstPlaces") : 0;
                long second = doc.getLong("secondPlaces") != null ? doc.getLong("secondPlaces") : 0;
                long third = doc.getLong("thirdPlaces") != null ? doc.getLong("thirdPlaces") : 0;

                ((TextView) findViewById(R.id.tvFirstPlaces)).setText(String.valueOf(first));
                ((TextView) findViewById(R.id.tvSecondPlaces)).setText(String.valueOf(second));
                ((TextView) findViewById(R.id.tvThirdPlaces)).setText(String.valueOf(third));
            }
        });

        // 2. Broj igrača (aktivni = mesečne zvezde > 0)
        db.collection("users").whereEqualTo("region", regionName).get().addOnSuccessListener(snap -> {
            int total = snap.size();
            int active = 0;
            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                Long monthlyStars = doc.getLong("starsMonthly");
                if (monthlyStars != null && monthlyStars > 0) active++;
            }
            ((TextView) findViewById(R.id.tvTotalPlayers)).setText(String.valueOf(total));
            ((TextView) findViewById(R.id.tvActivePlayers)).setText(String.valueOf(active));
        }).addOnFailureListener(e -> Toast.makeText(this, "Greška pri učitavanju statistike", Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}