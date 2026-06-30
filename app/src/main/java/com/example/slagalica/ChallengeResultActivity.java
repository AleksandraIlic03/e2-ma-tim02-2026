package com.example.slagalica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spec 9e: Nakon završetka izazova, prikazati rezultat na stranici.
 * Sluša Firestore u real-time dok svi učesnici ne završe (status -> "finished"),
 * zatim prikazuje konačan plasman i raspodelu nagrada.
 */
public class ChallengeResultActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String challengeId;
    private String currentUserId;

    private ProgressBar progressWaiting;
    private TextView tvWaitingStatus;
    private LinearLayout llResultsContainer;
    private View resultsScrollView;

    private ListenerRegistration challengeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_result);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();
        challengeId = getIntent().getStringExtra("challengeId");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Rezultati izazova");
        }

        progressWaiting = findViewById(R.id.progressWaitingResults);
        tvWaitingStatus = findViewById(R.id.tvWaitingResultsStatus);
        llResultsContainer = findViewById(R.id.llResultsContainer);
        resultsScrollView = findViewById(R.id.svResults);

        findViewById(R.id.btnBackToRegion).setOnClickListener(v -> finish());

        attachChallengeListener();
    }

    private void attachChallengeListener() {
        challengeListener = db.collection("challenges").document(challengeId)
                .addSnapshotListener((doc, e) -> {
                    if (doc == null || !doc.exists()) return;

                    String status = doc.getString("status");
                    Map<String, Object> participants = (Map<String, Object>) doc.get("participants");

                    if (!"finished".equals(status)) {
                        // Spec 9e: dok god nisu svi završili, prikaži stanje čekanja
                        int finishedCount = 0;
                        int total = participants != null ? participants.size() : 0;
                        if (participants != null) {
                            for (Object pObj : participants.values()) {
                                Map<String, Object> p = (Map<String, Object>) pObj;
                                if (p != null && Boolean.TRUE.equals(p.get("finished"))) finishedCount++;
                            }
                        }
                        showWaitingState(finishedCount, total);
                        return;
                    }

                    showFinalResults(doc.getData());
                });
    }

    private void showWaitingState(int finishedCount, int total) {
        progressWaiting.setVisibility(View.VISIBLE);
        tvWaitingStatus.setVisibility(View.VISIBLE);
        resultsScrollView.setVisibility(View.GONE);
        tvWaitingStatus.setText("Čeka se da svi igrači završe partiju...\n(" + finishedCount + "/" + total + ")");
    }

    private void showFinalResults(Map<String, Object> challenge) {
        progressWaiting.setVisibility(View.GONE);
        tvWaitingStatus.setVisibility(View.GONE);
        resultsScrollView.setVisibility(View.VISIBLE);

        llResultsContainer.removeAllViews();

        Map<String, Object> participants = (Map<String, Object>) challenge.get("participants");
        Map<String, Object> results = (Map<String, Object>) challenge.get("results");
        if (participants == null) return;

        // Sortiraj učesnike po bodovima opadajuće (isti redosled kao distributeRewards)
        List<Map.Entry<String, Long>> ranked = new ArrayList<>();
        for (String uid : participants.keySet()) {
            Map<String, Object> p = (Map<String, Object>) participants.get(uid);
            long score = 0;
            if (p != null && p.get("score") != null) score = ((Number) p.get("score")).longValue();
            ranked.add(new java.util.AbstractMap.SimpleEntry<>(uid, score));
        }
        ranked.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        long stakeStars = challenge.get("stakeStars") != null ? ((Number) challenge.get("stakeStars")).longValue() : 0;
        long stakeTokens = challenge.get("stakeTokens") != null ? ((Number) challenge.get("stakeTokens")).longValue() : 0;
        long totalStars = stakeStars * participants.size();
        long totalTokens = stakeTokens * participants.size();

        TextView tvPotInfo = findViewById(R.id.tvPotInfo);
        tvPotInfo.setText("Ukupan ulog: " + totalStars + " ⭐  " + totalTokens + " 🎟️");

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < ranked.size(); i++) {
            String uid = ranked.get(i).getKey();
            long score = ranked.get(i).getValue();
            Map<String, Object> p = (Map<String, Object>) participants.get(uid);
            String name = p != null && p.get("name") != null ? (String) p.get("name") : "Igrač";

            long starsWon = 0, tokensWon = 0;
            if (results != null && results.get(uid) != null) {
                Map<String, Object> r = (Map<String, Object>) results.get(uid);
                starsWon = r.get("starsWon") != null ? ((Number) r.get("starsWon")).longValue() : 0;
                tokensWon = r.get("tokensWon") != null ? ((Number) r.get("tokensWon")).longValue() : 0;
            }

            View row = inflater.inflate(R.layout.item_challenge_result, llResultsContainer, false);

            TextView tvRank = row.findViewById(R.id.tvResultRank);
            TextView tvName = row.findViewById(R.id.tvResultName);
            TextView tvScore = row.findViewById(R.id.tvResultScore);
            TextView tvPayout = row.findViewById(R.id.tvResultPayout);

            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "•";
            tvRank.setText(medal + " " + (i + 1) + ".");
            tvName.setText(name + (uid.equals(currentUserId) ? " (ti)" : ""));
            tvScore.setText(score + " bodova");

            if (starsWon > 0 || tokensWon > 0) {
                tvPayout.setText("+" + starsWon + " ⭐  +" + tokensWon + " 🎟️");
                tvPayout.setTextColor(0xFF4CAF50);
            } else {
                tvPayout.setText("Bez nagrade");
                tvPayout.setTextColor(0xFF877777);
            }

            if (uid.equals(currentUserId)) {
                row.setBackgroundResource(R.drawable.bubble_received);
            }

            llResultsContainer.addView(row);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (challengeListener != null) challengeListener.remove();
    }
}