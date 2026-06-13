package com.example.slagalica;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.NotificationHelper;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkockoActivity extends AppCompatActivity {

    private int[] targetCombination = new int[4];
    private int[][] attempts = new int[6][4];
    private int currentAttemptIndex = 0;
    private int currentSymbolIndex = 0;

    private TextView tvTimer, tvPoints, tvPlayer1Name, tvPlayer2Name, tvPlayer1Points, tvPlayer2Points;
    private ImageView ivPlayer1Avatar, ivPlayer2Avatar;
    private LinearLayout[] rows = new LinearLayout[7];
    private LinearLayout layoutStealRow;
    private LinearLayout row7;
    private int[] stealAttempt = new int[]{-1, -1, -1, -1};
    private CountDownTimer countDownTimer;
    private int player1Score = 0, player2Score = 0;
    private int currentRound = 1;
    private boolean isOpponentChance = false;
    private boolean isGameOver = false;
    private long lastRoundStartTime = -1;
    private boolean wasOpponentChance = false;
    private boolean gameStatsRecordedThisMatch = false;
    private int[] previousRoundTarget = null;

    private final int[] symbolDrawables = {
            R.drawable.img,
            R.drawable.ic_kvadrat,
            R.drawable.ic_krug,
            R.drawable.ic_srce,
            R.drawable.ic_trougao,
            R.drawable.ic_zvezda
    };

    private MaterialButton btnConfirm, btnDelete;
    private LinearLayout layoutSolution;
    private ImageView[] ivSolutions = new ImageView[4];

    private FirebaseFirestore db;
    private String roomId;
    private boolean isPlayer1;
    private String currentTurn = "p1";
    private ListenerRegistration gameListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        db = FirebaseFirestore.getInstance();
        roomId = getIntent().getStringExtra("roomId");
        if (roomId == null) {
            Toast.makeText(this, "Greška: Soba nije pronađena!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);

        initViews();
        attachGameListener();
    }

    private void initViews() {
        tvTimer = findViewById(R.id.tvTimer);
        tvPoints = findViewById(R.id.tvPoints);

        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);
        tvPlayer1Points = findViewById(R.id.tvPlayer1Points);
        tvPlayer2Points = findViewById(R.id.tvPlayer2Points);

        ivPlayer1Avatar = findViewById(R.id.ivPlayer1Avatar);
        ivPlayer2Avatar = findViewById(R.id.ivPlayer2Avatar);

        btnConfirm = findViewById(R.id.btnConfirm);
        btnDelete = findViewById(R.id.btnDelete);
        layoutSolution = findViewById(R.id.layoutSolution);
        ivSolutions[0] = findViewById(R.id.ivSol1);
        ivSolutions[1] = findViewById(R.id.ivSol2);
        ivSolutions[2] = findViewById(R.id.ivSol3);
        ivSolutions[3] = findViewById(R.id.ivSol4);

        rows[0] = findViewById(R.id.row1);
        rows[1] = findViewById(R.id.row2);
        rows[2] = findViewById(R.id.row3);
        rows[3] = findViewById(R.id.row4);
        rows[4] = findViewById(R.id.row5);
        rows[5] = findViewById(R.id.row6);
        layoutStealRow = findViewById(R.id.layoutStealRow);
        row7 = findViewById(R.id.row7);
        rows[6] = row7;

        findViewById(R.id.btnSymbol1).setOnClickListener(v -> addSymbol(0));
        findViewById(R.id.btnSymbol2).setOnClickListener(v -> addSymbol(1));
        findViewById(R.id.btnSymbol3).setOnClickListener(v -> addSymbol(2));
        findViewById(R.id.btnSymbol4).setOnClickListener(v -> addSymbol(3));
        findViewById(R.id.btnSymbol5).setOnClickListener(v -> addSymbol(4));
        findViewById(R.id.btnSymbol6).setOnClickListener(v -> addSymbol(5));

        btnDelete.setOnClickListener(v -> removeSymbol());
        btnConfirm.setOnClickListener(v -> {
            if (!isGameOver) confirmAttempt();
        });
    }

    private void attachGameListener() {
        gameListener = db.collection("gameRooms").document(roomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) return;
                    runOnUiThread(() -> {
                        String currentGame = snapshot.getString("currentGame");
                        if ("korakPoKorak".equals(currentGame)) {
                            navigateToKorakPoKorak();
                            return;
                        }

                        Long p1s = snapshot.getLong("player1Score");
                        Long p2s = snapshot.getLong("player2Score");
                        player1Score = p1s != null ? p1s.intValue() : 0;
                        player2Score = p2s != null ? p2s.intValue() : 0;

                        tvPoints.setText("⭐ " + (isPlayer1 ? player1Score : player2Score));
                        if (tvPlayer1Points != null) tvPlayer1Points.setText(String.valueOf(player1Score));
                        if (tvPlayer2Points != null) tvPlayer2Points.setText(String.valueOf(player2Score));

                        setAvatar(ivPlayer1Avatar, snapshot.getString("player1Avatar"));
                        setAvatar(ivPlayer2Avatar, snapshot.getString("player2Avatar"));

                        Long round = snapshot.getLong("skocko_currentRound");
                        int newRound = round != null ? round.intValue() : 1;
                        if (newRound != currentRound) {
                            currentSymbolIndex = 0;
                            finishTimerStarted = false;
                            findViewById(R.id.btnNext).setVisibility(View.GONE);
                            if (newRound == 2) {
                                Toast.makeText(SkockoActivity.this, "POČINJE DRUGA RUNDA!", Toast.LENGTH_SHORT).show();
                                // Save round 1 solution before targetCombination gets overwritten
                                previousRoundTarget = targetCombination.clone();
                                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                                    previousRoundTarget = null;
                                    if (layoutSolution != null && !isGameOver) {
                                        layoutSolution.setVisibility(View.GONE);
                                    }
                                }, 3000);
                            }
                        }
                        currentRound = newRound;

                        currentTurn = snapshot.getString("skocko_turn") != null
                            ? snapshot.getString("skocko_turn") : "p1";

                        Boolean steal = snapshot.getBoolean("skocko_isSteal");
                        isOpponentChance = steal != null && steal;

                        List<Long> target = (List<Long>) snapshot.get("skocko_target" + currentRound);
                        if (target != null && target.size() == 4) {
                            for (int i = 0; i < 4; i++) targetCombination[i] = target.get(i).intValue();
                        }

                        syncAttempts(snapshot);

                        if (snapshot.contains("roundStartTime")) {
                            Long startTime = snapshot.getLong("roundStartTime");
                            if (startTime != null && startTime != lastRoundStartTime) {
                                lastRoundStartTime = startTime;
                                long duration = isOpponentChance ? 10000 : 30000;
                                startTimer(startTime, duration);
                            }
                        }
                    });
                });
    }

    private void setAvatar(ImageView iv, String avatarName) {
        if (iv == null || avatarName == null || avatarName.isEmpty()) return;
        int resId = getResources().getIdentifier(avatarName, "drawable", getPackageName());
        if (resId != 0) {
            iv.setImageResource(resId);
        }
    }

    private void syncAttempts(DocumentSnapshot snap) {
        try {
            List<Map<String, Object>> syncAttempts =
                (List<Map<String, Object>>) snap.get("skocko_attempts");

            isGameOver = false;
            boolean solved = false;
            boolean stealDone = false;
            currentAttemptIndex = 0;

            if (syncAttempts != null) {
                for (Map<String, Object> attempt : syncAttempts) {
                    if (attempt == null) continue;

                    List<Long> symbols = (List<Long>) attempt.get("symbols");
                    if (symbols == null || symbols.size() < 4) continue;

                    Long correctL = (Long) attempt.get("correct");
                    Long wrongL = (Long) attempt.get("wrong");
                    int correct = correctL != null ? correctL.intValue() : 0;
                    int wrong = wrongL != null ? wrongL.intValue() : 0;

                    if (Boolean.TRUE.equals(attempt.get("isSteal"))) {
                        for (int j = 0; j < 4; j++) stealAttempt[j] = symbols.get(j).intValue();
                        updateStealRowUI();
                        updateHints(6, correct, wrong);
                        stealDone = true;
                    } else if (currentAttemptIndex < 6) {
                        for (int j = 0; j < 4; j++) attempts[currentAttemptIndex][j] = symbols.get(j).intValue();
                        updateRowUI(currentAttemptIndex);
                        updateHints(currentAttemptIndex, correct, wrong);
                        currentAttemptIndex++;
                    }

                    if (correct == 4) { solved = true; isGameOver = true; }
                }
            }

            if (syncAttempts == null || syncAttempts.isEmpty()) {
                for (int i = 0; i < 6; i++) {
                    for (int j = 0; j < 4; j++) attempts[i][j] = -1;
                    updateRowUI(i);
                    resetHints(i);
                }
                for (int j = 0; j < 4; j++) stealAttempt[j] = -1;
                updateStealRowUI();
                resetHints(6);
                if (previousRoundTarget != null) {
                    showSolution(previousRoundTarget);
                } else {
                    layoutSolution.setVisibility(View.GONE);
                }
            }

            if (!solved && stealDone) isGameOver = true;

            boolean isMyTurn = currentTurn.equals(isPlayer1 ? "p1" : "p2");
            if (!isMyTurn) currentSymbolIndex = 0;

            updateUIState();

        } catch (Exception ex) {
            android.util.Log.e("Skocko", "syncAttempts error: " + ex.getMessage(), ex);
        }
    }

    private void updateUIState() {
        boolean isMyTurn = currentTurn.equals(isPlayer1 ? "p1" : "p2");
        if (isGameOver || !isMyTurn) {
            btnConfirm.setEnabled(false);
            btnDelete.setEnabled(false);
            disableSymbolButtons();
        } else {
            btnConfirm.setEnabled(true);
            btnDelete.setEnabled(true);
            enableSymbolButtons();
        }

        TextView tvStealLabel = findViewById(R.id.tvStealLabel);
        if (tvStealLabel != null) {
            String stealPlayer = (currentRound == 1) ? "P2" : "P1";
            tvStealLabel.setText("⚡" + stealPlayer);
            tvStealLabel.setTextColor(isOpponentChance ?
                Color.parseColor("#E65100") : Color.parseColor("#B39DDB"));
        }

        if (isOpponentChance) {
            layoutStealRow.setBackgroundColor(Color.parseColor("#FFE0CC"));
            if (!wasOpponentChance) {
                stealAttempt = new int[]{-1, -1, -1, -1};
                updateStealRowUI();
                boolean isMyStealTurn = currentTurn.equals(isPlayer1 ? "p1" : "p2");
                if (isMyStealTurn) {
                    Toast.makeText(this, "Tvoja šansa! Imaš 10 sekundi!", Toast.LENGTH_SHORT).show();
                    NotificationHelper.sendRealNotification(this,
                            "Skočko — tvoja šansa!",
                            "Protivnik nije pogodio kombinaciju. Imaš 10s!",
                            NotificationHelper.CHANNEL_OTHER);
                } else {
                    Toast.makeText(this, "Protivnik pokušava da ukrade!", Toast.LENGTH_SHORT).show();
                }
            }
            wasOpponentChance = true;
        } else {
            layoutStealRow.setBackground(null);
            wasOpponentChance = false;
        }

        if (isGameOver) {
            showTargetCombination();
            showNextGameButton();
        }
    }

    private void startTimer(long startTime, long duration) {
        if (countDownTimer != null) countDownTimer.cancel();
        long remaining = duration - (System.currentTimeMillis() - startTime);
        if (remaining <= 0) {
            tvTimer.setText("⏱ 0s");
            if (!isGameOver) {
                boolean isMyTurn = currentTurn.equals(isPlayer1 ? "p1" : "p2");
                if (!isMyTurn) return;
                StatisticsManager.updateSKStats(-1, 0, !gameStatsRecordedThisMatch);
                gameStatsRecordedThisMatch = true;
                if (!isOpponentChance) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("skocko_turn", currentRound == 1 ? "p2" : "p1");
                    updates.put("skocko_isSteal", true);
                    updates.put("roundStartTime", System.currentTimeMillis());
                    db.collection("gameRooms").document(roomId).update(updates);
                } else {
                    Map<String, Object> roundUpdates = new HashMap<>();
                    roundUpdates.put("skocko_isSteal", false);
                    if (currentRound == 1) {
                        roundUpdates.put("skocko_currentRound", 2);
                        roundUpdates.put("skocko_turn", "p2");
                        roundUpdates.put("skocko_attempts", new ArrayList<>());
                        roundUpdates.put("roundStartTime", System.currentTimeMillis());
                    } else {
                        roundUpdates.put("currentGame", "korakPoKorak");
                        roundUpdates.put("roundStartTime", System.currentTimeMillis());
                    }
                    db.collection("gameRooms").document(roomId).update(roundUpdates);
                }
            }
            return;
        }
        countDownTimer = new CountDownTimer(remaining, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText("⏱ " + (ms / 1000) + "s"); }
            @Override public void onFinish() {
                tvTimer.setText("⏱ 0s");
                if (!isGameOver) {
                    boolean isMyTurn = currentTurn.equals(isPlayer1 ? "p1" : "p2");
                    if (!isMyTurn) return;
                    StatisticsManager.updateSKStats(-1, 0, !gameStatsRecordedThisMatch);
                    gameStatsRecordedThisMatch = true;
                    if (!isOpponentChance) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("skocko_turn", currentRound == 1 ? "p2" : "p1");
                        updates.put("skocko_isSteal", true);
                        updates.put("roundStartTime", System.currentTimeMillis());
                        db.collection("gameRooms").document(roomId).update(updates);
                    } else {
                        Map<String, Object> roundUpdates = new HashMap<>();
                        roundUpdates.put("skocko_isSteal", false);
                        if (currentRound == 1) {
                            roundUpdates.put("skocko_currentRound", 2);
                            roundUpdates.put("skocko_turn", "p2");
                            roundUpdates.put("skocko_attempts", new ArrayList<>());
                            roundUpdates.put("roundStartTime", System.currentTimeMillis());
                        } else {
                            roundUpdates.put("currentGame", "korakPoKorak");
                            roundUpdates.put("roundStartTime", System.currentTimeMillis());
                        }
                        db.collection("gameRooms").document(roomId).update(roundUpdates);
                    }
                }
            }
        }.start();
    }

    private void addSymbol(int symbolIndex) {
        if (currentSymbolIndex >= 4) return;
        if (isOpponentChance) {
            stealAttempt[currentSymbolIndex] = symbolIndex;
            updateStealRowUI();
        } else {
            int rowIdx = currentAttemptIndex < 6 ? currentAttemptIndex : 5;
            attempts[rowIdx][currentSymbolIndex] = symbolIndex;
            updateRowUI(rowIdx);
        }
        currentSymbolIndex++;
    }

    private void removeSymbol() {
        if (currentSymbolIndex > 0) {
            currentSymbolIndex--;
            if (isOpponentChance) {
                stealAttempt[currentSymbolIndex] = -1;
                updateStealRowUI();
            } else {
                int rowIdx = currentAttemptIndex < 6 ? currentAttemptIndex : 5;
                attempts[rowIdx][currentSymbolIndex] = -1;
                updateRowUI(rowIdx);
            }
        }
    }

    private void updateStealRowUI() {
        LinearLayout symbolLayout = (LinearLayout) row7.getChildAt(0);
        for (int i = 0; i < 4; i++) {
            ImageView iv = (ImageView) symbolLayout.getChildAt(i);
            if (stealAttempt[i] != -1) {
                iv.setImageResource(symbolDrawables[stealAttempt[i]]);
                iv.setBackgroundTintList(null);
            } else {
                iv.setImageDrawable(null);
                iv.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#C8BDD9")));
            }
        }
    }

    private void confirmAttempt() {
        if (currentSymbolIndex < 4) {
            Toast.makeText(this, "Popunite sva polja!", Toast.LENGTH_SHORT).show();
            return;
        }

        int[] currentAttemptArr;
        if (isOpponentChance) {
            currentAttemptArr = stealAttempt;
        } else {
            int rowIdx = currentAttemptIndex < 6 ? currentAttemptIndex : 5;
            currentAttemptArr = attempts[rowIdx];
        }

        int correct = 0; int wrong = 0;
        boolean[] targetUsed = new boolean[4];
        boolean[] attemptUsed = new boolean[4];

        for (int i = 0; i < 4; i++) {
            if (currentAttemptArr[i] == targetCombination[i]) {
                correct++; targetUsed[i] = true; attemptUsed[i] = true;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (attemptUsed[i]) continue;
            for (int j = 0; j < 4; j++) {
                if (!targetUsed[j] && currentAttemptArr[i] == targetCombination[j]) {
                    wrong++; targetUsed[j] = true; break;
                }
            }
        }

        List<Integer> symbols = new ArrayList<>();
        for (int s : currentAttemptArr) symbols.add(s);

        Map<String, Object> attemptMap = new HashMap<>();
        attemptMap.put("symbols", symbols);
        attemptMap.put("correct", correct);
        attemptMap.put("wrong", wrong);
        if (isOpponentChance) attemptMap.put("isSteal", true);

        final int finalCorrect = correct;
        final int finalAttemptIdx = currentAttemptIndex;

        db.collection("gameRooms").document(roomId).get().addOnSuccessListener(doc -> {
            List<Map<String, Object>> syncAttempts = (List<Map<String, Object>>) doc.get("skocko_attempts");
            if (syncAttempts == null) syncAttempts = new ArrayList<>();
            syncAttempts.add(attemptMap);

            Map<String, Object> updates = new HashMap<>();
            updates.put("skocko_attempts", syncAttempts);

            if (finalCorrect == 4) {
                int points;
                if (isOpponentChance) points = 10;
                else if (finalAttemptIdx < 2) points = 20;
                else if (finalAttemptIdx < 4) points = 15;
                else points = 10;

                StatisticsManager.updateSKStats(isOpponentChance ? 6 : finalAttemptIdx, points, !gameStatsRecordedThisMatch);
                gameStatsRecordedThisMatch = true;

                String scoreField = isPlayer1 ? "player1Score" : "player2Score";
                updates.put(scoreField, (isPlayer1 ? player1Score : player2Score) + points);
                updates.put("skocko_isSteal", false);

                db.collection("gameRooms").document(roomId).update(updates);
            } else if (syncAttempts.size() >= 6 && !isOpponentChance) {
                StatisticsManager.updateSKStats(-1, 0, !gameStatsRecordedThisMatch);
                gameStatsRecordedThisMatch = true;
                updates.put("skocko_turn", currentRound == 1 ? "p2" : "p1");
                updates.put("skocko_isSteal", true);
                updates.put("roundStartTime", System.currentTimeMillis());
                db.collection("gameRooms").document(roomId).update(updates);
            } else if (isOpponentChance) {
                StatisticsManager.updateSKStats(-1, 0, !gameStatsRecordedThisMatch);
                gameStatsRecordedThisMatch = true;
                db.collection("gameRooms").document(roomId).update(updates);
            } else {
                db.collection("gameRooms").document(roomId).update(updates);
            }
            currentSymbolIndex = 0;
        });
    }

    private void startNextRound() {
        if (countDownTimer != null) countDownTimer.cancel();
        Map<String, Object> updates = new HashMap<>();
        updates.put("skocko_currentRound", 2);
        updates.put("skocko_turn", "p2");
        updates.put("skocko_attempts", new ArrayList<>());
        updates.put("skocko_isSteal", false);
        updates.put("roundStartTime", System.currentTimeMillis());
        db.collection("gameRooms").document(roomId).update(updates);
    }

    private void transitionToNextGame() {
        if (countDownTimer != null) countDownTimer.cancel();
        Map<String, Object> updates = new HashMap<>();
        updates.put("currentGame", "korakPoKorak");
        updates.put("roundStartTime", System.currentTimeMillis());
        db.collection("gameRooms").document(roomId).update(updates);
    }

    private void navigateToKorakPoKorak() {
        if (gameListener != null) gameListener.remove();
        if (countDownTimer != null) countDownTimer.cancel();
        Intent intent = new Intent(this, KorakPoKorakActivity.class);
        intent.putExtra("roomId", roomId);
        intent.putExtra("isPlayer1", isPlayer1);
        startActivity(intent);
        finish();
    }

    private void updateHints(int rowIndex, int correctPlace, int wrongPlace) {
        LinearLayout row = rows[rowIndex];
        GridLayout hintGrid = (GridLayout) row.getChildAt(1);
        for (int i = 0; i < 4; i++) {
            ImageView hint = (ImageView) hintGrid.getChildAt(i);
            if (i < correctPlace) hint.setImageTintList(ColorStateList.valueOf(Color.RED));
            else if (i < correctPlace + wrongPlace) hint.setImageTintList(ColorStateList.valueOf(Color.YELLOW));
            else hint.setImageTintList(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
        }
    }

    private void resetHints(int rowIndex) {
        LinearLayout row = rows[rowIndex];
        GridLayout hintGrid = (GridLayout) row.getChildAt(1);
        for (int i = 0; i < 4; i++) {
            ImageView hint = (ImageView) hintGrid.getChildAt(i);
            hint.setImageTintList(ColorStateList.valueOf(Color.parseColor("#DADCE0")));
        }
    }

    private void updateRowUI(int rowIndex) {
        LinearLayout row = rows[rowIndex];
        LinearLayout symbolLayout = (LinearLayout) row.getChildAt(0);
        int[] currentAttempt = attempts[rowIndex];
        for (int i = 0; i < 4; i++) {
            ImageView iv = (ImageView) symbolLayout.getChildAt(i);
            if (currentAttempt[i] != -1) {
                iv.setImageResource(symbolDrawables[currentAttempt[i]]);
                iv.setBackgroundTintList(null);
            } else {
                iv.setImageDrawable(null);
                iv.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#C8BDD9")));
            }
        }
    }

    private boolean finishTimerStarted = false;

    private void showNextGameButton() {
        if (finishTimerStarted) return;
        finishTimerStarted = true;

        MaterialButton btnNext = findViewById(R.id.btnNext);
        btnNext.setVisibility(View.VISIBLE);
        btnNext.setText(currentRound == 1 ? "SLEDEĆA RUNDA" : "SLEDEĆA IGRA");
        btnNext.setOnClickListener(v -> {
            if (currentRound == 1) startNextRound();
            else transitionToNextGame();
        });

        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText("⏱ " + (ms / 1000 + 1) + "s");
            }

            @Override
            public void onFinish() {
                tvTimer.setText("⏱ 0s");
                if (currentRound == 1) startNextRound();
                else transitionToNextGame();
            }
        }.start();
    }

    private void showTargetCombination() {
        showSolution(targetCombination);
    }

    private void showSolution(int[] combo) {
        layoutSolution.setVisibility(View.VISIBLE);
        for (int i = 0; i < 4; i++) {
            ivSolutions[i].setImageResource(symbolDrawables[combo[i]]);
            ivSolutions[i].setBackgroundTintList(null);
            ivSolutions[i].setPadding(6, 6, 6, 6);
        }
    }

    private void disableSymbolButtons() {
        findViewById(R.id.btnSymbol1).setEnabled(false);
        findViewById(R.id.btnSymbol2).setEnabled(false);
        findViewById(R.id.btnSymbol3).setEnabled(false);
        findViewById(R.id.btnSymbol4).setEnabled(false);
        findViewById(R.id.btnSymbol5).setEnabled(false);
        findViewById(R.id.btnSymbol6).setEnabled(false);
    }

    private void enableSymbolButtons() {
        findViewById(R.id.btnSymbol1).setEnabled(true);
        findViewById(R.id.btnSymbol2).setEnabled(true);
        findViewById(R.id.btnSymbol3).setEnabled(true);
        findViewById(R.id.btnSymbol4).setEnabled(true);
        findViewById(R.id.btnSymbol5).setEnabled(true);
        findViewById(R.id.btnSymbol6).setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameListener != null) gameListener.remove();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
