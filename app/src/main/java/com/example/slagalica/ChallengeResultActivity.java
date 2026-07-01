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

                    // Uvek prikaži trenutno stanje — ne čekaj da svi završe
                    int finishedCount = 0;
                    int total = participants != null ? participants.size() : 0;
                    if (participants != null) {
                        for (Object pObj : participants.values()) {
                            Map<String, Object> p = (Map<String, Object>) pObj;
                            if (p != null && Boolean.TRUE.equals(p.get("finished"))) finishedCount++;
                        }
                    }

                    boolean allFinished = "finished".equals(status);
                    showResults(doc.getData(), finishedCount, total, allFinished);
                });
    }

    private void showResults(Map<String, Object> challenge, int finishedCount, int total, boolean allFinished) {
        Map<String, Object> participants = (Map<String, Object>) challenge.get("participants");
        Map<String, Object> results = (Map<String, Object>) challenge.get("results");
        if (participants == null) return;

        // Header — pokazuje da li su svi završili ili još čekamo
        progressWaiting.setVisibility(allFinished ? View.GONE : View.VISIBLE);
        if (!allFinished) {
            tvWaitingStatus.setVisibility(View.VISIBLE);
            tvWaitingStatus.setText("⏳ Čeka se " + (total - finishedCount) + " igrača da završi...");
        } else {
            tvWaitingStatus.setVisibility(View.GONE);
        }
        resultsScrollView.setVisibility(View.VISIBLE);

        llResultsContainer.removeAllViews();

        // Sortiraj po bodovima
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

        TextView tvPotInfo = findViewById(R.id.tvPotInfo);
        tvPotInfo.setText("Ukupan ulog: " + (stakeStars * participants.size()) + " ⭐  " + (stakeTokens * participants.size()) + " 🎟️");

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < ranked.size(); i++) {
            String uid = ranked.get(i).getKey();
            long score = ranked.get(i).getValue();
            Map<String, Object> p = (Map<String, Object>) participants.get(uid);
            String name = p != null && p.get("name") != null ? (String) p.get("name") : "Igrač";
            boolean finished = p != null && Boolean.TRUE.equals(p.get("finished"));

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
            tvName.setText(name + (uid.equals(currentUserId) ? " (ti)" : "") + (!finished ? " ⏳" : ""));
            tvScore.setText(finished ? score + " bodova" : "Još igra...");

            if (allFinished) {
                if (starsWon > 0 || tokensWon > 0) {
                    tvPayout.setText("+" + starsWon + " ⭐  +" + tokensWon + " 🎟️");
                    tvPayout.setTextColor(0xFF4CAF50);
                } else {
                    tvPayout.setText("Bez nagrade");
                    tvPayout.setTextColor(0xFF877777);
                }
            } else {
                tvPayout.setText(allFinished ? "" : "Privremeno");
                tvPayout.setTextColor(0xFFBBBBBB);
            }

            if (uid.equals(currentUserId)) {
                row.setBackgroundColor(0xFFEDE0F5);
                if (allFinished) showPersonalResult(i, starsWon, tokensWon);
            }

            llResultsContainer.addView(row);
        }
    }

    private void showPersonalResult(int rank, long starsWon, long tokensWon) {
        TextView tvPersonal = findViewById(R.id.tvPersonalResult);
        if (tvPersonal == null) return;
        String msg;
        if (rank == 0) {
            msg = "🎉 Pobedio si! +" + starsWon + " ⭐  +" + tokensWon + " 🎟️";
            tvPersonal.setTextColor(0xFF4CAF50);
        } else if (rank == 1) {
            msg = "Drugi si — ulog vraćen: +" + starsWon + " ⭐  +" + tokensWon + " 🎟️";
            tvPersonal.setTextColor(0xFF823FAB);
        } else {
            msg = "Nažalost, nisi se plasirao. Pokušaj ponovo!";
            tvPersonal.setTextColor(0xFF877777);
        }
        tvPersonal.setText(msg);
        tvPersonal.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (challengeListener != null) challengeListener.remove();
    }
}