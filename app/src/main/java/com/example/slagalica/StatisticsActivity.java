package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        db = FirebaseFirestore.getInstance();
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        }

        NestedScrollView scrollOverview = findViewById(R.id.scrollOverview);
        NestedScrollView scrollGames = findViewById(R.id.scrollGames);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    scrollOverview.setVisibility(View.VISIBLE);
                    scrollGames.setVisibility(View.GONE);
                } else {
                    scrollOverview.setVisibility(View.GONE);
                    scrollGames.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadStatistics();
    }

    private void loadStatistics() {
        if (userId == null) return;

        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> stats = (Map<String, Object>) documentSnapshot.get("stats");
                        if (stats != null) {
                            populateOverview(stats);
                            populateGames(stats);
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Greška pri učitavanju statistike", Toast.LENGTH_SHORT).show());
    }

    private void populateOverview(Map<String, Object> stats) {
        long played = getLong(stats, "totalGamesPlayed");
        long wins = getLong(stats, "totalWins");
        long losses = getLong(stats, "totalLosses");
        long totalPoints = getLong(stats, "totalPoints");

        ((TextView) findViewById(R.id.tvTotalPlayed)).setText(String.valueOf(played));
        
        double winRate = played > 0 ? (wins * 100.0 / played) : 0;
        double lossRate = played > 0 ? (losses * 100.0 / played) : 0;
        double avgPoints = played > 0 ? (totalPoints * 1.0 / played) : 0;

        ((TextView) findViewById(R.id.tvWinRate)).setText(String.format(Locale.getDefault(), "%.0f%%", winRate));
        ((TextView) findViewById(R.id.tvLossRate)).setText(String.format(Locale.getDefault(), "%.0f%%", lossRate));
        ((TextView) findViewById(R.id.tvAvgPoints)).setText(String.format(Locale.getDefault(), "%.1f", avgPoints));

        ((TextView) findViewById(R.id.tvWinCount)).setText(String.format(Locale.getDefault(), "%d Pobede", wins));
        ((TextView) findViewById(R.id.tvLossCount)).setText(String.format(Locale.getDefault(), "%d Poraza", losses));

        setWeight(findViewById(R.id.viewWinBar), (float) Math.max(0.01, winRate));
        setWeight(findViewById(R.id.viewLossBar), (float) Math.max(0.01, lossRate));

        setupGameBar(stats, "kzz", R.id.tvAvgKZZ, R.id.barKZZ);
        setupGameBar(stats, "mb", R.id.tvAvgMB, R.id.barMB);
        setupGameBar(stats, "kpk", R.id.tvAvgKPK, R.id.barKPK);
        setupGameBar(stats, "sk", R.id.tvAvgSK, R.id.barSK);
        setupGameBar(stats, "as", R.id.tvAvgAS, R.id.barAS);
        setupGameBar(stats, "sp", R.id.tvAvgSP, R.id.barSP);
    }

    private void setupGameBar(Map<String, Object> stats, String prefix, int tvId, int barId) {
        long games = getLong(stats, prefix + "Games");
        long points = getLong(stats, prefix + "TotalPoints");
        double avg = games > 0 ? (points * 1.0 / games) : 0;
        
        ((TextView) findViewById(tvId)).setText(String.format(Locale.getDefault(), "%.1f", avg));
        
        View bar = findViewById(barId);
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        params.height = (int) (avg * 5); 
        bar.setLayoutParams(params);
    }

    private void populateGames(Map<String, Object> stats) {
        // KZZ
        long kzzCorrect = getLong(stats, "kzzCorrect");
        long kzzWrong = getLong(stats, "kzzWrong");
        long kzzTotal = kzzCorrect + kzzWrong;
        double kzzRate = kzzTotal > 0 ? (kzzCorrect * 100.0 / kzzTotal) : 0;
        ((TextView) findViewById(R.id.tvKZZCorrectPercent)).setText(String.format(Locale.getDefault(), "%.0f%% Tačno", kzzRate));
        setWeight(findViewById(R.id.kzzCorrectBar), (float) Math.max(0.01, kzzRate));
        setWeight(findViewById(R.id.kzzWrongBar), (float) Math.max(0.01, 100 - kzzRate));

        // MB
        long mbExact = getLong(stats, "mbExactHits");
        long mbGames = getLong(stats, "mbGames");
        int mbRate = mbGames > 0 ? (int)(mbExact * 100 / mbGames) : 0;
        ((CircularProgressIndicator) findViewById(R.id.mbProgress)).setProgress(mbRate);
        ((TextView) findViewById(R.id.tvMBPercent)).setText(String.format(Locale.getDefault(), "%d%%", mbRate));

        // KPK
        @SuppressWarnings("unchecked")
        Map<String, Object> kpkHits = (Map<String, Object>) stats.get("kpkStepHits");
        long kpkGames = getLong(stats, "kpkGames");
        if (kpkGames > 0 && kpkHits != null) {
            updateKPKProgress(R.id.pbKPK1, R.id.tvKPK1Percent, kpkHits, "0", kpkGames);
            updateKPKProgress(R.id.pbKPK2, R.id.tvKPK2Percent, kpkHits, "1", kpkGames);
            updateKPKProgress(R.id.pbKPK3, R.id.tvKPK3Percent, kpkHits, "2", kpkGames);
            updateKPKProgress(R.id.pbKPK4, R.id.tvKPK4Percent, kpkHits, "3", kpkGames);
            updateKPKProgress(R.id.pbKPK5, R.id.tvKPK5Percent, kpkHits, "4", kpkGames);
            updateKPKProgress(R.id.pbKPK6, R.id.tvKPK6Percent, kpkHits, "5", kpkGames);
            updateKPKProgress(R.id.pbKPK7, R.id.tvKPK7Percent, kpkHits, "6", kpkGames);
        }

        // AS
        long asSolved = getLong(stats, "asSolved");
        long asUnsolved = getLong(stats, "asUnsolved");
        long asTotal = asSolved + asUnsolved;
        double asRate = asTotal > 0 ? (asSolved * 100.0 / asTotal) : 0;
        ((TextView) findViewById(R.id.tvASSolvedPercent)).setText(String.format(Locale.getDefault(), "%.0f%% Rešeno", asRate));
        setWeight(findViewById(R.id.asSolvedBar), (float) Math.max(0.01, asRate));
        setWeight(findViewById(R.id.asUnsolvedBar), (float) Math.max(0.01, 100 - asRate));

        // SK
        @SuppressWarnings("unchecked")
        Map<String, Object> skHits = (Map<String, Object>) stats.get("skAttemptHits");
        long skGames = getLong(stats, "skGames");
        if (skGames > 0 && skHits != null) {
            updateSKText(R.id.tvSK1, "1. Pokušaj", skHits, "0", skGames);
            updateSKText(R.id.tvSK2, "2. Pokušaj", skHits, "1", skGames);
            updateSKText(R.id.tvSK3, "3. Pokušaj", skHits, "2", skGames);
            updateSKText(R.id.tvSK4, "4. Pokušaj", skHits, "3", skGames);
            updateSKText(R.id.tvSK5, "5. Pokušaj", skHits, "4", skGames);
            updateSKText(R.id.tvSK6, "6. Pokušaj", skHits, "5", skGames);
            updateSKText(R.id.tvSKSteal, "Šansa", skHits, "steal", skGames);
        }

        // SP
        long spCorrect = getLong(stats, "spCorrectPairs");
        long spTotal = getLong(stats, "spTotalPairs");
        int spRate = spTotal > 0 ? (int)(spCorrect * 100 / spTotal) : 0;
        ((CircularProgressIndicator) findViewById(R.id.spProgress)).setProgress(spRate);
        ((TextView) findViewById(R.id.tvSPPercent)).setText(String.format(Locale.getDefault(), "%d%%", spRate));
    }

    private void updateKPKProgress(int pbId, int tvId, Map<String, Object> hits, String key, long games) {
        long count = getLong(hits, key);
        int percent = (int)(count * 100 / games);
        ((ProgressBar) findViewById(pbId)).setProgress(percent);
        ((TextView) findViewById(tvId)).setText(String.format(Locale.getDefault(), "%d%%", percent));
    }

    private void updateSKText(int tvId, String label, Map<String, Object> hits, String key, long games) {
        long count = getLong(hits, key);
        ((TextView) findViewById(tvId)).setText(String.format(Locale.getDefault(), "%s: %.0f%%", label, count * 100.0 / games));
    }

    private void setWeight(View v, float weight) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) v.getLayoutParams();
        params.weight = weight;
        v.setLayoutParams(params);
    }

    private long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Long) return (Long) val;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Double) return ((Double) val).longValue();
        return 0;
    }
}
