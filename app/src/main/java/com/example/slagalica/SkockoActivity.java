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
    private LinearLayout[] rows = new LinearLayout[7]; // Povećano na 7
    private LinearLayout layoutStealRow;
    private LinearLayout row7;
    private int[] stealAttempt = new int[]{-1, -1, -1, -1};
    private CountDownTimer countDownTimer;
    private int player1Score = 0, player2Score = 0;
    private int currentRound = 1;
    private boolean isOpponentChance = false;
    private boolean isGameOver = false;

    private final int[] symbolDrawables = {
            R.drawable.img, // Skocko
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
            android.widget.Toast.makeText(this, "Greška: Soba nije pronađena!", android.widget.Toast.LENGTH_SHORT).show();
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
        rows[6] = row7; // 7. red u niz

        findViewById(R.id.btnSymbol1).setOnClickListener(v -> addSymbol(0));
        findViewById(R.id.btnSymbol2).setOnClickListener(v -> addSymbol(1));
        findViewById(R.id.btnSymbol3).setOnClickListener(v -> addSymbol(2));
        findViewById(R.id.btnSymbol4).setOnClickListener(v -> addSymbol(3));
        findViewById(R.id.btnSymbol5).setOnClickListener(v -> addSymbol(4));
        findViewById(R.id.btnSymbol6).setOnClickListener(v -> addSymbol(5));

        btnDelete.setOnClickListener(v -> removeSymbol());
        btnConfirm.setOnClickListener(v -> {
            if (isGameOver) {
                if (currentRound == 1) {
                    if (isPlayer1) startNextRound();
                } else {
                    if (isPlayer1) transitionToNextGame();
                }
            } else {
                confirmAttempt();
            }
        });
    }

    private void attachGameListener() {
        gameListener = db.collection("gameRooms").document(roomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) return;

                    String status = snapshot.getString("status");
                    if ("asocijacije".equals(status)) {
                        navigateToAsocijacije();
                        return;
                    }

                    // NULL SAFE
                    Long p1s = snapshot.getLong("player1Score");
                    Long p2s = snapshot.getLong("player2Score");
                    player1Score = p1s != null ? p1s.intValue() : 0;
                    player2Score = p2s != null ? p2s.intValue() : 0;

                    tvPoints.setText("⭐ " + (isPlayer1 ? player1Score : player2Score));
                    if (tvPlayer1Points != null) tvPlayer1Points.setText(String.valueOf(player1Score));
                    if (tvPlayer2Points != null) tvPlayer2Points.setText(String.valueOf(player2Score));

                    // NULL SAFE
                    Long round = snapshot.getLong("skocko_currentRound");
                    int newRound = round != null ? round.intValue() : 1;
                    if (newRound == 2 && currentRound == 1) {
                        Toast.makeText(this, "POČINJE DRUGA RUNDA!", Toast.LENGTH_SHORT).show();
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
                        if (startTime != null) {
                            long duration = isOpponentChance ? 10000 : 30000;
                            startTimer(startTime, duration);
                        }
                    }
                });
    }

    private void syncAttempts(DocumentSnapshot snap) {
        try {
            List<Map<String, Object>> syncAttempts = 
                (List<Map<String, Object>>) snap.get("skocko_attempts");
            
            // Inicijalno čišćenje samo onoga što nije u bazi
            isGameOver = false;
            boolean solved = false;
            currentAttemptIndex = 0;

            if (syncAttempts != null) {
                currentAttemptIndex = syncAttempts.size();
                for (int i = 0; i < syncAttempts.size(); i++) {
                    Map<String, Object> attempt = syncAttempts.get(i);
                    if (attempt == null) continue;

                    List<Long> symbols = (List<Long>) attempt.get("symbols");
                    if (symbols == null || symbols.size() < 4) continue;

                    Long correctL = (Long) attempt.get("correct");
                    Long wrongL = (Long) attempt.get("wrong");
                    int correct = correctL != null ? correctL.intValue() : 0;
                    int wrong = wrongL != null ? wrongL.intValue() : 0;

                    if (i < 6) {
                        for (int j = 0; j < 4; j++) attempts[i][j] = symbols.get(j).intValue();
                        updateRowUI(i);
                        updateHints(i, correct, wrong);
                    } else if (i == 6) {
                        for (int j = 0; j < 4; j++) stealAttempt[j] = symbols.get(j).intValue();
                        updateStealRowUI();
                        updateHints(6, correct, wrong);
                    }

                    if (correct == 4) { solved = true; isGameOver = true; }
                }
            }

            // Ako nema pokušaja (nova runda), očisti sve
            if (syncAttempts == null || syncAttempts.isEmpty()) {
                for (int i = 0; i < 6; i++) {
                    for (int j = 0; j < 4; j++) attempts[i][j] = -1;
                    updateRowUI(i);
                    resetHints(i);
                }
                for (int j = 0; j < 4; j++) stealAttempt[j] = -1;
                updateStealRowUI();
                resetHints(6);
                layoutSolution.setVisibility(View.GONE);
            }

            if (!solved) {
                if (currentAttemptIndex >= 7) isGameOver = true;
            }

            // Ne diramo currentSymbolIndex ako je naš potez u toku
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

        // Steal red je UVEK vidljiv, samo menjamo izgled
        TextView tvStealLabel = findViewById(R.id.tvStealLabel);
        if (tvStealLabel != null) {
            String stealPlayer = (currentRound == 1) ? "P2" : "P1";
            tvStealLabel.setText("⚡" + stealPlayer);
            // Narandžast kada aktivan, siv inače
            tvStealLabel.setTextColor(isOpponentChance ? 
                Color.parseColor("#E65100") : Color.parseColor("#B39DDB"));
        }

        if (isOpponentChance) {
            // Highlight steal reda kada je aktivan
            layoutStealRow.setBackgroundColor(Color.parseColor("#FFE0CC"));
            stealAttempt = new int[]{-1, -1, -1, -1};
            updateStealRowUI();
            Toast.makeText(this, "ŠANSA ZA PROTIVNIKA!", Toast.LENGTH_SHORT).show();
        } else {
            layoutStealRow.setBackground(null);
        }

        if (isGameOver) {
            showTargetCombination();
            btnConfirm.setEnabled(isPlayer1);
            btnConfirm.setVisibility(View.VISIBLE);
            btnConfirm.setText(currentRound == 1 ? "SLEDEĆA RUNDA" : "ZAVRŠI IGRU");
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
                if (!isOpponentChance) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("skocko_turn", currentRound == 1 ? "p2" : "p1");
                    updates.put("skocko_isSteal", true);
                    // NE BRIŠEMO LISTU - ostaje da protivnik vidi
                    updates.put("roundStartTime", System.currentTimeMillis());
                    db.collection("gameRooms").document(roomId).update(updates);
                } else {
                    if (isPlayer1) {
                        Map<String, Object> roundUpdates = new HashMap<>();
                        roundUpdates.put("skocko_attempts", new ArrayList<>());
                        roundUpdates.put("skocko_isSteal", false);
                        if (currentRound == 1) {
                            roundUpdates.put("skocko_currentRound", 2);
                            roundUpdates.put("skocko_turn", "p2");
                            roundUpdates.put("roundStartTime", System.currentTimeMillis());
                        } else {
                            roundUpdates.put("status", "asocijacije");
                            roundUpdates.put("roundStartTime", System.currentTimeMillis());
                        }
                        db.collection("gameRooms").document(roomId).update(roundUpdates);
                    }
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
                    if (!isOpponentChance) {
                        // Time's up for the current player, give opponent a chance
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("skocko_turn", currentRound == 1 ? "p2" : "p1");
                        updates.put("skocko_isSteal", true);
                        // Ne brišemo pokušaje
                        updates.put("roundStartTime", System.currentTimeMillis());
                        db.collection("gameRooms").document(roomId).update(updates);
                    } else {
                        // Time's up for the opponent steal, move to next round or game
                        if (isPlayer1) {
                            Map<String, Object> roundUpdates = new HashMap<>();
                            roundUpdates.put("skocko_attempts", new ArrayList<>());
                            roundUpdates.put("skocko_isSteal", false);
                            if (currentRound == 1) {
                                roundUpdates.put("skocko_currentRound", 2);
                                roundUpdates.put("skocko_turn", "p2");
                                roundUpdates.put("roundStartTime", System.currentTimeMillis());
                            } else {
                                roundUpdates.put("status", "asocijacije");
                                roundUpdates.put("roundStartTime", System.currentTimeMillis());
                            }
                            db.collection("gameRooms").document(roomId).update(roundUpdates);
                        }
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
                
                String scoreField = isPlayer1 ? "player1Score" : "player2Score";
                updates.put(scoreField, (isPlayer1 ? player1Score : player2Score) + points);
                updates.put("skocko_isSteal", false);
            } else if (syncAttempts.size() >= 6 && !isOpponentChance) {
                updates.put("skocko_turn", currentRound == 1 ? "p2" : "p1");
                updates.put("skocko_isSteal", true);
                //updates.put("skocko_attempts", new ArrayList<>()); // OBRISANO - ostavljamo listu da bi protivnik video!
                updates.put("roundStartTime", System.currentTimeMillis());
            } else if (isOpponentChance) {
                updates.put("skocko_isSteal", false);
                updates.put("skocko_attempts", new ArrayList<>());
                // Tek sad kad je steal gotov možemo da mislimo o sledećem koraku
                if (currentRound == 1) {
                    updates.put("skocko_currentRound", 2);
                    updates.put("skocko_turn", "p2");
                    updates.put("roundStartTime", System.currentTimeMillis());
                } else {
                    updates.put("status", "asocijacije");
                    updates.put("roundStartTime", System.currentTimeMillis());
                }
            }

            db.collection("gameRooms").document(roomId).update(updates);
            currentSymbolIndex = 0;
        });
    }

    private void startNextRound() {
        if (!isPlayer1) return;
        layoutSolution.setVisibility(View.GONE); // Sakrij rešenje iz prethodne runde
        Map<String, Object> updates = new HashMap<>();
        updates.put("skocko_currentRound", 2);
        updates.put("skocko_turn", "p2");
        updates.put("skocko_attempts", new ArrayList<>());
        updates.put("skocko_isSteal", false);
        updates.put("roundStartTime", System.currentTimeMillis());
        db.collection("gameRooms").document(roomId).update(updates);
    }

    private void transitionToNextGame() {
        if (!isPlayer1) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "asocijacije");
        updates.put("roundStartTime", System.currentTimeMillis());
        db.collection("gameRooms").document(roomId).update(updates);
    }

    private void navigateToAsocijacije() {
        if (gameListener != null) gameListener.remove();
        Intent intent = new Intent(this, AsocijacijeActivity.class);
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

    private void showTargetCombination() {
        layoutSolution.setVisibility(View.VISIBLE);
        for (int i = 0; i < 4; i++) {
            ivSolutions[i].setImageResource(symbolDrawables[targetCombination[i]]);
            ivSolutions[i].setBackgroundTintList(null); // Čistimo pozadinu kod rešenja
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
