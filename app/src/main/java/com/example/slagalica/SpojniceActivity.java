package com.example.slagalica;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.models.SpojnicaModel;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpojniceActivity extends AppCompatActivity {

    private TextView tvPlayer1Name, tvPlayer1Points, tvPlayer2Name, tvPlayer2Points, tvTimer, tvInstruction;
    private ImageView ivPlayer1Avatar, ivPlayer2Avatar;
    private MaterialButton[] leftButtons = new MaterialButton[5];
    private MaterialButton[] rightButtons = new MaterialButton[5];
    private MaterialButton btnNext;

    private FirebaseFirestore db;
    private String roomId;
    private boolean isPlayer1;
    private List<SpojnicaModel> spojnice = new ArrayList<>();
    private SpojnicaModel currentSpojnica;
    private int currentRound = 1;
    private ListenerRegistration gameListener;
    private CountDownTimer timer;

    private String currentTurn = "p1";
    private int currentLeftIndex = 0;
    private List<Long> matchedRightIndices = new ArrayList<>();
    private List<String> whoMatched = new ArrayList<>();
    private boolean isMyTurn = false;
    private long lastWrongTrigger = 0;
    private boolean transitioning = false;
    private boolean statsUpdatedThisRound = false;
    private boolean gameStatsRecordedThisMatch = false;
    private int lastRecordedRound = 0;
    private boolean p1Ready = false, p2Ready = false;

    private final String COLOR_P1 = "#823FAB"; // Purple
    private final String COLOR_P2 = "#2196F3"; // Blue
    private final String COLOR_DEFAULT = "#D5C4E0";
    private final String COLOR_TEXT_DEFAULT = "#2D1B4E";
    private final String COLOR_WRONG = "#C62828";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        db = FirebaseFirestore.getInstance();
        roomId = getIntent().getStringExtra("roomId");
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);

        initViews();
        fetchSpojniceFromRoom();
    }

    private void initViews() {
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer1Points = findViewById(R.id.tvPlayer1Points);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);
        tvPlayer2Points = findViewById(R.id.tvPlayer2Points);
        ivPlayer1Avatar = findViewById(R.id.ivPlayer1Avatar);
        ivPlayer2Avatar = findViewById(R.id.ivPlayer2Avatar);
        tvTimer = findViewById(R.id.tvTimer);
        tvInstruction = findViewById(R.id.tvInstruction);
        btnNext = findViewById(R.id.btnNext);

        leftButtons[0] = findViewById(R.id.btnLeft1);
        leftButtons[1] = findViewById(R.id.btnLeft2);
        leftButtons[2] = findViewById(R.id.btnLeft3);
        leftButtons[3] = findViewById(R.id.btnLeft4);
        leftButtons[4] = findViewById(R.id.btnLeft5);

        rightButtons[0] = findViewById(R.id.btnRight1);
        rightButtons[1] = findViewById(R.id.btnRight2);
        rightButtons[2] = findViewById(R.id.btnRight3);
        rightButtons[3] = findViewById(R.id.btnRight4);
        rightButtons[4] = findViewById(R.id.btnRight5);

        for (int i = 0; i < 5; i++) {
            final int index = i;
            rightButtons[i].setOnClickListener(v -> onRightButtonClick(index));
        }

        btnNext.setVisibility(View.INVISIBLE);
        btnNext.setOnClickListener(v -> handleNextRound());
    }

    private void fetchSpojniceFromRoom() {
        db.collection("gameRooms").document(roomId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        spojnice.add(doc.get("spojnica1", SpojnicaModel.class));
                        spojnice.add(doc.get("spojnica2", SpojnicaModel.class));
                        attachGameListener();
                    }
                });
    }

    private void attachGameListener() {
        gameListener = db.collection("gameRooms").document(roomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) return;

                    String currentGame = snapshot.getString("currentGame");
                    if ("asocijacije".equals(currentGame)) {
                        navigateToAsocijacije();
                        return;
                    }

                    int newRound = snapshot.getLong("currentRound") != null ? snapshot.getLong("currentRound").intValue() : 1;
                    if (newRound != lastRecordedRound) {
                        lastRecordedRound = newRound;
                        statsUpdatedThisRound = false;
                    }
                    currentRound = newRound;
                    currentTurn = snapshot.getString("spojnice_turn") != null ? snapshot.getString("spojnice_turn") : "p1";
                    currentLeftIndex = snapshot.getLong("spojnice_currentLeftIndex") != null ? snapshot.getLong("spojnice_currentLeftIndex").intValue() : 0;
                    matchedRightIndices = (List<Long>) snapshot.get("spojnice_matchedRightIndices");
                    whoMatched = (List<String>) snapshot.get("spojnice_whoMatched");
                    
                    tvPlayer1Points.setText(String.valueOf(snapshot.getLong("player1Score")));
                    tvPlayer2Points.setText(String.valueOf(snapshot.getLong("player2Score")));

                    tvPlayer1Name.setText(snapshot.getString("player1Name"));
                    tvPlayer2Name.setText(snapshot.getString("player2Name"));

                    setAvatar(ivPlayer1Avatar, snapshot.getString("player1Avatar"));
                    setAvatar(ivPlayer2Avatar, snapshot.getString("player2Avatar"));

                    tvPlayer1Name.setTextColor(Color.parseColor(COLOR_P1));
                    tvPlayer2Name.setTextColor(Color.parseColor(COLOR_P2));

                    if (spojnice.size() >= currentRound) {
                        currentSpojnica = spojnice.get(currentRound - 1);
                        tvInstruction.setText(currentSpojnica.getTitle());
                        
                        isMyTurn = currentTurn.equals(isPlayer1 ? "p1" : "p2");

                        // PROVERA READY STANJA ZA SLEDECU RUNDU/IGRU
                        p1Ready = snapshot.getBoolean("spojnice_p1Ready") != null && snapshot.getBoolean("spojnice_p1Ready");
                        p2Ready = snapshot.getBoolean("spojnice_p2Ready") != null && snapshot.getBoolean("spojnice_p2Ready");

                        if (p1Ready && p2Ready) {
                            if (isPlayer1) {
                                Map<String, Object> resetReady = new HashMap<>();
                                resetReady.put("spojnice_p1Ready", false);
                                resetReady.put("spojnice_p2Ready", false);
                                db.collection("gameRooms").document(roomId).update(resetReady);

                                handleNextRound();
                            }
                        }

                        updateUIState();
                    }

                    if (snapshot.contains("roundStartTime")) {
                        syncTimer(snapshot.getLong("roundStartTime"));
                    }

                    if (snapshot.contains("spojnice_wrongClickTrigger")) {
                        long wrongTrigger = snapshot.getLong("spojnice_wrongClickTrigger");
                        if (wrongTrigger > lastWrongTrigger) {
                            lastWrongTrigger = wrongTrigger;
                            int wrongIdx = snapshot.getLong("spojnice_lastWrongIndex").intValue();
                            showWrongClickAnimation(wrongIdx);
                        }
                    }

                    if (currentLeftIndex == 5) {
                        endRoundLocally();
                    }
                });
    }

    private void setAvatar(ImageView iv, String avatarName) {
        if (iv == null || avatarName == null || avatarName.isEmpty()) return;
        int resId = getResources().getIdentifier(avatarName, "drawable", getPackageName());
        if (resId != 0) {
            iv.setImageResource(resId);
        }
    }

    private void syncTimer(long startTime) {
        if (timer != null) timer.cancel();
        long remaining = 60000 - (System.currentTimeMillis() - startTime);
        if (remaining <= 0) {
            tvTimer.setText("⏱ 0s");
            if (currentLeftIndex < 5) endRoundLocally();
            return;
        }

        timer = new CountDownTimer(remaining, 1000) {
            @Override public void onTick(long l) { tvTimer.setText("⏱ " + (l / 1000) + "s"); }
            @Override public void onFinish() { 
                tvTimer.setText("⏱ 0s");
                endRoundLocally();
            }
        }.start();
    }

    private void showWrongClickAnimation(int rightIdx) {
        if (rightIdx < 0 || rightIdx >= 5 || currentLeftIndex >= 5) return;
        
        rightButtons[rightIdx].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_WRONG)));
        rightButtons[rightIdx].setTextColor(Color.WHITE);
        leftButtons[currentLeftIndex].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_WRONG)));
        leftButtons[currentLeftIndex].setTextColor(Color.WHITE);

        rightButtons[rightIdx].postDelayed(this::updateUIState, 500);
    }

    private void updateUIState() {
        if (currentSpojnica == null) return;
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(currentSpojnica.getLeftSide().get(i));
            rightButtons[i].setText(currentSpojnica.getRightSide().get(i));
            
            leftButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_DEFAULT)));
            leftButtons[i].setTextColor(Color.parseColor(COLOR_TEXT_DEFAULT));
            rightButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(COLOR_DEFAULT)));
            rightButtons[i].setTextColor(Color.parseColor(COLOR_TEXT_DEFAULT));
            rightButtons[i].setEnabled(isMyTurn);
        }

        if (matchedRightIndices != null) {
            for (int i = 0; i < 5; i++) {
                int matchedRightIdx = matchedRightIndices.get(i).intValue();
                if (matchedRightIdx != -1) {
                    String matchOwner = whoMatched.get(i);
                    int color = Color.parseColor(matchOwner.equals("p1") ? COLOR_P1 : COLOR_P2);
                    leftButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                    leftButtons[i].setTextColor(Color.WHITE);
                    rightButtons[matchedRightIdx].setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                    rightButtons[matchedRightIdx].setTextColor(Color.WHITE);
                    rightButtons[matchedRightIdx].setEnabled(false);
                }
            }
        }

        if (currentLeftIndex < 5) {
            int turnColor = Color.parseColor(currentTurn.equals("p1") ? COLOR_P1 : COLOR_P2);
            leftButtons[currentLeftIndex].setBackgroundTintList(android.content.res.ColorStateList.valueOf(turnColor));
            leftButtons[currentLeftIndex].setTextColor(Color.WHITE);
        }
    }

    private String getRoundStarter() {
        return currentRound == 1 ? "p1" : "p2";
    }

    private void onRightButtonClick(int rightIndex) {
        if (!isMyTurn || currentLeftIndex >= 5) return;

        int correctIndex = currentSpojnica.getCorrectMapping().get(currentLeftIndex);
        Map<String, Object> updates = new HashMap<>();

        if (rightIndex == correctIndex) {
            matchedRightIndices.set(currentLeftIndex, (long) rightIndex);
            whoMatched.set(currentLeftIndex, isPlayer1 ? "p1" : "p2");
            updates.put("spojnice_matchedRightIndices", matchedRightIndices);
            updates.put("spojnice_whoMatched", whoMatched);
            updates.put("spojnice_currentLeftIndex", currentLeftIndex + 1);
            
            String scoreField = isPlayer1 ? "player1Score" : "player2Score";
            db.collection("gameRooms").document(roomId).get().addOnSuccessListener(snap -> {
                updates.put(scoreField, snap.getLong(scoreField) + 2);
                db.collection("gameRooms").document(roomId).update(updates);
            });
        } else {
            String starter = getRoundStarter();
            if (currentTurn.equals(starter)) {
                updates.put("spojnice_turn", isPlayer1 ? "p2" : "p1");
            } else {
                updates.put("spojnice_currentLeftIndex", currentLeftIndex + 1);
                updates.put("spojnice_turn", starter);
            }
            updates.put("spojnice_lastWrongIndex", rightIndex);
            updates.put("spojnice_wrongClickTrigger", System.currentTimeMillis());
            db.collection("gameRooms").document(roomId).update(updates);
        }
    }

    private void endRoundLocally() {
        if (timer != null) timer.cancel();
        if (!statsUpdatedThisRound && whoMatched != null) {
            statsUpdatedThisRound = true;
            int correctCount = 0;
            String me = isPlayer1 ? "p1" : "p2";
            for (String owner : whoMatched) {
                if (me.equals(owner)) correctCount++;
            }
            StatisticsManager.updateSPStats(correctCount, 5, correctCount * 2, !gameStatsRecordedThisMatch);
            gameStatsRecordedThisMatch = true;
        }
        
        btnNext.setVisibility(View.VISIBLE);
        btnNext.setText(currentRound == 1 ? "SLEDEĆA RUNDA" : "SLEDEĆA IGRA");

        boolean myReady = isPlayer1 ? p1Ready : p2Ready;
        if (myReady) {
            btnNext.setEnabled(false);
            btnNext.setText("ČEKANJE...");
        } else {
            btnNext.setEnabled(true);
        }

        btnNext.setOnClickListener(v -> {
            btnNext.setEnabled(false);
            btnNext.setText("ČEKANJE...");
            String field = isPlayer1 ? "spojnice_p1Ready" : "spojnice_p2Ready";
            db.collection("gameRooms").document(roomId).update(field, true);
        });

        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long l) {
                tvTimer.setText("⏱ " + (l / 1000 + 1) + "s");
            }

            @Override
            public void onFinish() {
                tvTimer.setText("⏱ 0s");
                // Uklonjen automatski prelaz
            }
        }.start();
    }

    private void handleNextRound() {
        if (timer != null) timer.cancel();
        if (currentRound == 1) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("currentRound", 2);
            updates.put("spojnice_turn", "p2"); 
            updates.put("spojnice_currentLeftIndex", 0);
            updates.put("spojnice_matchedRightIndices", java.util.Arrays.asList(-1, -1, -1, -1, -1));
            updates.put("spojnice_whoMatched", java.util.Arrays.asList("", "", "", "", ""));
            updates.put("spojnice_lastWrongIndex", -1);
            updates.put("spojnice_wrongClickTrigger", 0);
            updates.put("roundStartTime", System.currentTimeMillis());
            db.collection("gameRooms").document(roomId).update(updates);
        } else {
            Map<String, Object> upd = new HashMap<>();
            upd.put("currentGame", "asocijacije");
            upd.put("roundStartTime", System.currentTimeMillis());
            db.collection("gameRooms").document(roomId).update(upd);
        }
    }

    private void navigateToAsocijacije() {
        if (transitioning) return;
        transitioning = true;
        if (timer != null) timer.cancel();
        if (gameListener != null) gameListener.remove();
        Intent intent = new Intent(this, AsocijacijeActivity.class);
        intent.putExtra("roomId", roomId);
        intent.putExtra("isPlayer1", isPlayer1);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameListener != null) gameListener.remove();
        if (timer != null) timer.cancel();
    }
}
