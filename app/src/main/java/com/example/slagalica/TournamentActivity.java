package com.example.slagalica;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TournamentActivity extends AppCompatActivity {

    private TextView tvStatus, tvPlayerCount, tvResultTitle, tvResultDetail;
    private ProgressBar pbPlayers;
    private MaterialButton btnJoinTournament;
    private View player1, player2, player3, player4, finalist1, finalist2;
    private View layoutResult;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;
    private String tournamentId;
    private ListenerRegistration tournamentListener;

    // Sprečavamo dvostruko pokretanje igre i dvostruki prikaz rezultata
    private boolean gameStarted = false;
    private String resultShownForMatchType = "";
    private String lastMatchType = "";
    private boolean hasNavigatedHome = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        }

        initViews();
        btnJoinTournament.setOnClickListener(v -> checkTokensAndJoin());

        // MojBroj po završetku turnirske partije vraća se ovde s tournamentId
        String intentTid = getIntent().getStringExtra("tournamentId");
        if (intentTid != null) {
            tournamentId = intentTid;
            btnJoinTournament.setEnabled(false);
            listenToTournament();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Listener je već aktivan – promene u turniru automatski pozivaju checkMyResult()
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvPlayerCount = findViewById(R.id.tvPlayerCount);
        pbPlayers = findViewById(R.id.pbPlayers);
        btnJoinTournament = findViewById(R.id.btnJoinTournament);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultDetail = findViewById(R.id.tvResultDetail);
        layoutResult = findViewById(R.id.layoutResult);

        player1 = findViewById(R.id.player1);
        player2 = findViewById(R.id.player2);
        player3 = findViewById(R.id.player3);
        player4 = findViewById(R.id.player4);
        finalist1 = findViewById(R.id.finalist1);
        finalist2 = findViewById(R.id.finalist2);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (layoutResult != null) {
            layoutResult.setOnClickListener(v -> layoutResult.setVisibility(View.GONE));
        }
    }

    private void navigateToHome() {
        if (hasNavigatedHome || isFinishing()) return;
        hasNavigatedHome = true;
        if (tournamentListener != null) tournamentListener.remove();
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // ─── Prijava na turnir ───────────────────────────────────────────────────

    private void checkTokensAndJoin() {
        if (userId == null) return;
        db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
            long tokens = doc.getLong("tokens") != null ? doc.getLong("tokens") : 0;
            if (tokens >= 3) {
                joinTournament();
            } else {
                Toast.makeText(this, "Nemaš dovoljno tokena (potrebno 3).", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void joinTournament() {
        btnJoinTournament.setEnabled(false);
        tvStatus.setText("Traženje turnira...");

        db.collection("tournaments")
                .whereEqualTo("status", "WAITING")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        createNewTournament();
                    } else {
                        tournamentId = snap.getDocuments().get(0).getId();
                        addUserToTournament(tournamentId);
                    }
                });
    }

    private void createNewTournament() {
        Map<String, Object> tournament = new HashMap<>();
        tournament.put("playerIds", Arrays.asList(userId));
        tournament.put("status", "WAITING");
        tournament.put("createdAt", FieldValue.serverTimestamp());

        db.collection("tournaments").add(tournament).addOnSuccessListener(ref -> {
            tournamentId = ref.getId();
            deductTokens();
            listenToTournament();
        });
    }

    private void addUserToTournament(String tId) {
        db.collection("tournaments").document(tId)
                .update("playerIds", FieldValue.arrayUnion(userId))
                .addOnSuccessListener(v -> {
                    deductTokens();
                    listenToTournament();
                });
    }

    // Spec 10c: troši 3 tokena odmah, bez povraćaja pri odustajanju
    private void deductTokens() {
        db.collection("users").document(userId).update("tokens", FieldValue.increment(-3));
    }

    // ─── Firestore listener ──────────────────────────────────────────────────

    private void listenToTournament() {
        if (tournamentListener != null) tournamentListener.remove();
        tournamentListener = db.collection("tournaments").document(tournamentId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;
                    Map<String, Object> data = snapshot.getData();

                    List<String> playerIds = (List<String>) data.get("playerIds");
                    int count = playerIds != null ? playerIds.size() : 0;
                    pbPlayers.setProgress(count);
                    tvPlayerCount.setText(count + "/4 igrača");

                    if (count == 4 && "WAITING".equals(data.get("status"))) {
                        startTournament(playerIds);
                    }

                    updateBracketUI(data);

                    String status = (String) data.get("status");
                    if ("SEMI_FINALS".equals(status) || "FINAL".equals(status)) {
                        checkMyMatch(data);
                        checkMyResult(data);
                    } else if ("COMPLETED".equals(status)) {
                        checkMyResult(data);
                    }
                });
    }

    // ─── Nasumično uparivanje – Spec 10a/10b ────────────────────────────────

    private void startTournament(List<String> players) {
        if (!players.get(0).equals(userId)) return; // Samo prvi igrač kreira raspored

        List<String> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled); // Spec 10a: nasumično uparivanje

        Map<String, Object> updates = new HashMap<>();
        updates.put("semiFinal1", Arrays.asList(shuffled.get(0), shuffled.get(1)));
        updates.put("semiFinal2", Arrays.asList(shuffled.get(2), shuffled.get(3)));
        updates.put("status", "SEMI_FINALS");
        db.collection("tournaments").document(tournamentId).update(updates);
    }

    // ─── Pokretanje igre između para – Spec 10b ──────────────────────────────

    private void checkMyMatch(Map<String, Object> data) {
        String status = (String) data.get("status");
        List<String> myPair = null;
        String myRoomIdKey = null;
        String myWinnerKey = null;
        boolean isFinal = "FINAL".equals(status);

        if ("SEMI_FINALS".equals(status)) {
            List<String> sf1 = (List<String>) data.get("semiFinal1");
            List<String> sf2 = (List<String>) data.get("semiFinal2");
            if (sf1 != null && sf1.contains(userId)) {
                myPair = sf1; myRoomIdKey = "sf1RoomId"; myWinnerKey = "sf1Winner";
            } else if (sf2 != null && sf2.contains(userId)) {
                myPair = sf2; myRoomIdKey = "sf2RoomId"; myWinnerKey = "sf2Winner";
            }
        } else if ("FINAL".equals(status)) {
            List<String> finalists = (List<String>) data.get("finalists");
            if (finalists != null && finalists.contains(userId)) {
                myPair = finalists; myRoomIdKey = "finalRoomId"; myWinnerKey = "finalWinner";
            }
        }

        if (myPair == null) return;

        // Resetuj gameStarted kada prelazimo iz SF u FINAL
        String matchType = isFinal ? "FINAL" : "SEMI";
        if (!matchType.equals(lastMatchType)) {
            lastMatchType = matchType;
            gameStarted = false;
        }

        if (gameStarted) return;
        if (data.get(myWinnerKey) != null) return; // Pobednik već određen

        String existingRoomId = (String) data.get(myRoomIdKey);
        if (existingRoomId != null) {
            // Soba postoji – pridruži se kao player 2
            gameStarted = true;
            Intent intent = new Intent(this, WaitingRoomActivity.class);
            intent.putExtra("autoJoinRoomId", existingRoomId);
            intent.putExtra("tournamentId", tournamentId);
            intent.putExtra("tournamentWinnerKey", myWinnerKey);
            startActivity(intent);
        } else if (myPair.get(0).equals(userId)) {
            // Ja sam prvi u paru – kreiram sobu
            gameStarted = true;
            Intent intent = new Intent(this, WaitingRoomActivity.class);
            intent.putExtra("autoCreate", true);
            intent.putExtra("tournamentId", tournamentId);
            intent.putExtra("tournamentRoomKey", myRoomIdKey);
            intent.putExtra("tournamentWinnerKey", myWinnerKey);
            startActivity(intent);
        }
        // Drugi igrač čeka da prvi kreira sobu (listener ponovo okine kada se soba napravi)
    }

    // ─── Rezultat i nagrade – Spec 10d/10e/10f ───────────────────────────────

    private void checkMyResult(Map<String, Object> data) {
        String status = (String) data.get("status");

        if ("SEMI_FINALS".equals(status)) {
            List<String> sf1 = (List<String>) data.get("semiFinal1");
            List<String> sf2 = (List<String>) data.get("semiFinal2");
            String sf1Winner = (String) data.get("sf1Winner");
            String sf2Winner = (String) data.get("sf2Winner");

            String myWinner = null;
            if (sf1 != null && sf1.contains(userId)) myWinner = sf1Winner;
            else if (sf2 != null && sf2.contains(userId)) myWinner = sf2Winner;

            // Animacija se prikazuje samo jednom, čim je poznato ko je pobedio u mom polufinalu
            if (myWinner != null && !resultShownForMatchType.equals("SEMI")) {
                resultShownForMatchType = "SEMI";
                boolean iWon = myWinner.equals(userId);
                showResultAnimation(iWon, false); // Spec 10f

                if (iWon) {
                    // Spec 10d: pobednik polufinala dobija 2 tokena
                    db.collection("users").document(userId)
                            .update("tokens", FieldValue.increment(2));
                    NotificationHelper.sendRealNotification(this,
                            "Prošao si polufinale!", "+2 tokena. Čeka te finale!",
                            NotificationHelper.CHANNEL_REWARDS);
                }
            }

            // Finale kreira sf1Winner čim su oba polufinala gotova.
            // Ovo je ODVOJENO od animacije — mora raditi i ako je animacija već prikazana ranije.
            if (sf1Winner != null && sf2Winner != null
                    && userId.equals(sf1Winner) && data.get("finalists") == null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("finalists", Arrays.asList(sf1Winner, sf2Winner));
                updates.put("status", "FINAL");
                db.collection("tournaments").document(tournamentId).update(updates);
            }

        } else if (("FINAL".equals(status) || "COMPLETED".equals(status)) && !resultShownForMatchType.equals("FINAL")) {
            List<String> finalists = (List<String>) data.get("finalists");
            String finalWinner = (String) data.get("finalWinner");

            if (finalists != null && finalists.contains(userId) && finalWinner != null) {
                resultShownForMatchType = "FINAL";
                boolean iWon = finalWinner.equals(userId);
                showResultAnimation(iWon, true); // Spec 10f

                if (iWon) {
                    // Spec 10e: pobednik finala +3 tokena + zvezde + 10 bonus zvezda
                    db.collection("users").document(userId)
                            .update("tokens", FieldValue.increment(3));
                    RankingManager.updateStars(userId, 10); // 10 bonus zvezda
                    RankingManager.completeMission(userId, "tournament_win", () ->
                            NotificationHelper.showMissionCompletedDialog(TournamentActivity.this, "Pobedi partiju u turniru"));
                    NotificationHelper.sendRealNotification(this,
                            "Pobedio si turnir!", "+3 tokena i +10 bonus zvezda!",
                            NotificationHelper.CHANNEL_REWARDS);

                    if ("FINAL".equals(status)) {
                        // Zaključi turnir
                        db.collection("tournaments").document(tournamentId)
                                .update("status", "COMPLETED", "winner", finalWinner);
                    }
                }
            }
        }
    }

    // Spec 10f: animacija pobede/poraza
    private void showResultAnimation(boolean iWon, boolean isFinal) {
        if (layoutResult == null || tvResultTitle == null || tvResultDetail == null) return;

        tvResultTitle.setText(iWon ? "POBEDIO SI!" : "IZGUBIO SI");
        if (iWon && isFinal) tvResultDetail.setText("+3 tokena i +10 bonus zvezda!");
        else if (iWon) tvResultDetail.setText("+2 tokena – prošao si u finale!");
        else tvResultDetail.setText("Bolje sreće sledeći put.");

        if (isFinal) {
            layoutResult.setOnClickListener(v -> navigateToHome());
        }

        layoutResult.setVisibility(View.VISIBLE);
        layoutResult.setAlpha(0f);
        layoutResult.setScaleX(0.6f);
        layoutResult.setScaleY(0.6f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(layoutResult, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(layoutResult, "scaleX", 0.6f, 1f),
                ObjectAnimator.ofFloat(layoutResult, "scaleY", 0.6f, 1f)
        );
        set.setDuration(450);
        set.start();

        // Spec 10g: zvučna animacija
        try {
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(getApplicationContext(),
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─── UI bracket ─────────────────────────────────────────────────────────

    private void updateBracketUI(Map<String, Object> data) {
        List<String> players = (List<String>) data.get("playerIds");
        if (players != null) {
            if (players.size() >= 1) updatePlayerView(player1, players.get(0));
            if (players.size() >= 2) updatePlayerView(player2, players.get(1));
            if (players.size() >= 3) updatePlayerView(player3, players.get(2));
            if (players.size() >= 4) updatePlayerView(player4, players.get(3));
        }

        List<String> finalists = (List<String>) data.get("finalists");
        if (finalists != null) {
            if (finalists.size() >= 1) updatePlayerView(finalist1, finalists.get(0));
            if (finalists.size() >= 2) updatePlayerView(finalist2, finalists.get(1));
        }

        String status = (String) data.get("status");
        if ("WAITING".equals(status)) tvStatus.setText("Čekanje igrača...");
        else if ("SEMI_FINALS".equals(status)) tvStatus.setText("Polufinale u toku!");
        else if ("FINAL".equals(status)) tvStatus.setText("Finale!");
        else if ("COMPLETED".equals(status)) {
            tvStatus.setText("Turnir završen!");
            if (!hasNavigatedHome) {
                new android.os.Handler(getMainLooper()).postDelayed(this::navigateToHome, 5000);
            }
        }
    }

    private static final String[] LEAGUE_NAMES = {
        "Miš", "Vuk", "Medved", "Lav", "Orao", "Zmaj"
    };

    // Spec 10f: prikazati avatar, ligu i korisničko ime svakog igrača
    private void updatePlayerView(View view, String pId) {
        if (view == null || pId == null) return;
        db.collection("users").document(pId).get().addOnSuccessListener(doc -> {
            TextView tvName = view.findViewById(R.id.tvUsername);
            TextView tvLeague = view.findViewById(R.id.tvLeague);
            ImageView iv = view.findViewById(R.id.ivAvatar);
            if (tvName != null) tvName.setText(doc.getString("username"));
            if (tvLeague != null) {
                int league = doc.getLong("league") != null ? doc.getLong("league").intValue() : 0;
                int idx = Math.max(0, Math.min(league, LEAGUE_NAMES.length - 1));
                tvLeague.setText(LEAGUE_NAMES[idx]);
            }
            if (iv != null) {
                String avatar = doc.getString("avatarUrl");
                if (avatar != null) {
                    int resId = getResources().getIdentifier(avatar, "drawable", getPackageName());
                    if (resId != 0) iv.setImageResource(resId);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tournamentListener != null) tournamentListener.remove();
    }
}
