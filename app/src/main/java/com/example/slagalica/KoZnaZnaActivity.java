package com.example.slagalica;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoZnaZnaActivity extends AppCompatActivity {

    private TextView tvTimer, tvQuestion, tvPlayer1Points, tvPlayer1Name, tvPlayer2Points, tvPlayer2Name;
    private ImageView ivPlayer1Avatar, ivPlayer2Avatar;
    private TextView tvAnswer1, tvAnswer2, tvAnswer3, tvAnswer4;
    private ImageButton btnAnswer1, btnAnswer2, btnAnswer3, btnAnswer4;
    private LinearLayout llProgress;

    private FirebaseFirestore db;
    private String roomId;
    private boolean isPlayer1;
    private List<Object> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int lastProcessedQuestionIndex = -1;
    private CountDownTimer questionTimer;
    private boolean answered = false;
    private boolean nextGameButtonShown = false;
    private ListenerRegistration gameListener;
    private boolean p1Ready = false, p2Ready = false;
    private long lastSyncedStartTime = -1;

    private final String COLOR_P1 = "#823FAB";
    private final String COLOR_P2 = "#2196F3";
    private final String COLOR_WRONG = "#C62828";

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Napuštanje igre")
                .setMessage("Ako napustite igru sada, izgubićete 10 zvezdi. Da li ste sigurni?")
                .setPositiveButton("Da", (dialog, which) -> {
                    String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
                    if (uid != null) RankingManager.updateStars(uid, -10);
                    db.collection("gameRooms").document(roomId).update("playerLeft", isPlayer1 ? "p1" : "p2");
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                })
                .setNegativeButton("Ne", null)
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        db = FirebaseFirestore.getInstance();
        roomId = getIntent().getStringExtra("roomId");
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmation();
            }
        });

        initViews();
        attachGameListener();
    }

    private void initViews() {
        tvTimer = findViewById(R.id.tvTimer);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvPlayer1Points = findViewById(R.id.tvPlayer1Points);
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer2Points = findViewById(R.id.tvPlayer2Points);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);

        ivPlayer1Avatar = findViewById(R.id.ivPlayer1Avatar);
        ivPlayer2Avatar = findViewById(R.id.ivPlayer2Avatar);

        tvAnswer1 = findViewById(R.id.tvAnswer1);
        tvAnswer2 = findViewById(R.id.tvAnswer2);
        tvAnswer3 = findViewById(R.id.tvAnswer3);
        tvAnswer4 = findViewById(R.id.tvAnswer4);

        btnAnswer1 = findViewById(R.id.btnAnswer1);
        btnAnswer2 = findViewById(R.id.btnAnswer2);
        btnAnswer3 = findViewById(R.id.btnAnswer3);
        btnAnswer4 = findViewById(R.id.btnAnswer4);

        llProgress = findViewById(R.id.llProgress);

        btnAnswer1.setOnClickListener(v -> checkAnswer(0));
        btnAnswer2.setOnClickListener(v -> checkAnswer(1));
        btnAnswer3.setOnClickListener(v -> checkAnswer(2));
        btnAnswer4.setOnClickListener(v -> checkAnswer(3));

        findViewById(R.id.btnNext).setVisibility(View.GONE);
        
        tvPlayer1Name.setTextColor(Color.parseColor(COLOR_P1));
        tvPlayer2Name.setTextColor(Color.parseColor(COLOR_P2));
    }

    private void attachGameListener() {
        gameListener = db.collection("gameRooms").document(roomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) return;

                    String currentGame = snapshot.getString("currentGame");
                    if ("spojnice".equals(currentGame)) {
                        navigateToSpojnice();
                        return;
                    }

                    tvPlayer1Name.setText(snapshot.getString("player1Name"));
                    tvPlayer2Name.setText(snapshot.getString("player2Name"));

                    String avatar1 = snapshot.getString("player1Avatar");
                    String avatar2 = snapshot.getString("player2Avatar");
                    setAvatar(ivPlayer1Avatar, avatar1);
                    setAvatar(ivPlayer2Avatar, avatar2);

                    tvPlayer1Points.setText(String.valueOf(snapshot.getLong("player1Score")));
                    tvPlayer2Points.setText(String.valueOf(snapshot.getLong("player2Score")));

                    // Spec 3f: protivnik napustio - automatski odgovori za njega
                    String playerLeft = snapshot.getString("playerLeft");
                    if (playerLeft != null && !playerLeft.isEmpty()) {
                        String opponent = isPlayer1 ? "p2" : "p1";
                        if (playerLeft.equals(opponent)) {
                            autoAnswerForOpponent();
                        }
                    }

                    Long idxLong = snapshot.getLong("currentQuestionIndex");
                    int newIdx = (idxLong != null) ? idxLong.intValue() : 0;
                    
                    if (questions.isEmpty()) {
                        questions = (List<Object>) snapshot.get("koZnaZnaQuestions");
                        currentQuestionIndex = newIdx;
                        if (questions != null && !questions.isEmpty()) displayQuestion();
                    } else if (newIdx != currentQuestionIndex) {
                        currentQuestionIndex = newIdx;
                        displayQuestion();
                    }

                    Long startLong = snapshot.getLong("questionStartTime");
                    if (startLong != null && startLong > 0 && startLong != lastSyncedStartTime) {
                        lastSyncedStartTime = startLong;
                        syncTimer(startLong);
                    } else if (isPlayer1 && (startLong == null || startLong == 0)) {
                        db.collection("gameRooms").document(roomId).update("questionStartTime", System.currentTimeMillis());
                    }
                    
                    checkBothAnswered(snapshot);

                    // PROVERA READY STANJA ZA SLEDECU IGRU
                    p1Ready = snapshot.getBoolean("kzz_p1Ready") != null && snapshot.getBoolean("kzz_p1Ready");
                    p2Ready = snapshot.getBoolean("kzz_p2Ready") != null && snapshot.getBoolean("kzz_p2Ready");

                    if (p1Ready && p2Ready) {
                        if (isPlayer1) {
                            Map<String, Object> resetReady = new HashMap<>();
                            resetReady.put("kzz_p1Ready", false);
                            resetReady.put("kzz_p2Ready", false);
                            resetReady.put("currentGame", "spojnice");
                            db.collection("gameRooms").document(roomId).update(resetReady);
                        }
                    }

                    if (newIdx >= 4 && !nextGameButtonShown) {
                        Map<String, Object> answers = (Map<String, Object>) snapshot.get("answers_q4");
                        if (answers != null && answers.size() == 2) {
                            nextGameButtonShown = true;
                            showNextGameButton();
                        }
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

    private void displayQuestion() {
        answered = false;
        enableAnswerButtons(true);
        resetButtonColors();

        Map<String, Object> qMap = (Map<String, Object>) questions.get(currentQuestionIndex);
        tvQuestion.setText((String) qMap.get("question"));
        List<String> answers = (List<String>) qMap.get("answers");
        if (answers != null && answers.size() >= 4) {
            tvAnswer1.setText(answers.get(0));
            tvAnswer2.setText(answers.get(1));
            tvAnswer3.setText(answers.get(2));
            tvAnswer4.setText(answers.get(3));
        }
        updateProgressDots();
    }

    private void syncTimer(long startTime) {
        if (questionTimer != null) questionTimer.cancel();
        long remaining = 5000 - (System.currentTimeMillis() - startTime);
        if (remaining <= 0) {
            tvTimer.setText("⏱ 0s");
            if (!answered) {
                answered = true;
                enableAnswerButtons(false);
                submitAnswer(-1); // Timeout
            }
            return;
        }

        questionTimer = new CountDownTimer(remaining, 1000) {
            @Override public void onTick(long l) { tvTimer.setText("⏱ " + (l / 1000 + 1) + "s"); }
            @Override public void onFinish() { 
                tvTimer.setText("⏱ 0s");
                if (!answered) {
                    answered = true;
                    enableAnswerButtons(false);
                    submitAnswer(-1);
                }
                
                // Automatski preskoči na sledeće pitanje ako protivnik ne odgovara (solo mod)
                db.collection("gameRooms").document(roomId).get().addOnSuccessListener(snapshot -> {
                    String playerLeft = snapshot.getString("playerLeft");
                    boolean opponentLeft = playerLeft != null && !playerLeft.isEmpty() && !playerLeft.equals(isPlayer1 ? "p1" : "p2");
                    if (opponentLeft) {
                        autoAnswerForOpponent();
                    }
                });
            }
        }.start();
    }

    private void checkAnswer(int selectedIndex) {
        if (answered) return;
        answered = true;
        enableAnswerButtons(false);
        submitAnswer(selectedIndex);
    }

    private void submitAnswer(int selectedIndex) {
        Map<String, Object> qMap = (Map<String, Object>) questions.get(currentQuestionIndex);
        Long correctIdxLong = (Long) qMap.get("correctAnswerIndex");
        int correctIdx = (correctIdxLong != null) ? correctIdxLong.intValue() : -1;
        
        long timeTaken = System.currentTimeMillis();

        Map<String, Object> myAnswer = new HashMap<>();
        myAnswer.put("index", selectedIndex);
        myAnswer.put("time", timeTaken);
        myAnswer.put("correct", selectedIndex == correctIdx);

        db.collection("gameRooms").document(roomId).update("answers_q" + currentQuestionIndex + "." + (isPlayer1 ? "p1" : "p2"), myAnswer);
        
        if (selectedIndex != -1) {
            if (selectedIndex == correctIdx) highlightButton(selectedIndex, true);
            else {
                highlightButton(selectedIndex, false);
                highlightButton(correctIdx, true);
            }
        } else {
            highlightButton(correctIdx, true);
        }
    }

    private void checkBothAnswered(DocumentSnapshot snap) {
        if (lastProcessedQuestionIndex == currentQuestionIndex) return;

        Map<String, Object> answers = (Map<String, Object>) snap.get("answers_q" + currentQuestionIndex);
        if (answers != null && answers.size() == 2) {
            lastProcessedQuestionIndex = currentQuestionIndex;
            showBothSelections(answers, snap);
        }
    }

    private void showBothSelections(Map<String, Object> answers, DocumentSnapshot snap) {
        Map<String, Object> p1 = (Map<String, Object>) answers.get("p1");
        Map<String, Object> p2 = (Map<String, Object>) answers.get("p2");

        int p1Idx = p1.get("index") != null ? ((Long) p1.get("index")).intValue() : -1;
        int p2Idx = p2.get("index") != null ? ((Long) p2.get("index")).intValue() : -1;
        long p1Time = p1.get("time") != null ? (Long) p1.get("time") : Long.MAX_VALUE;
        long p2Time = p2.get("time") != null ? (Long) p2.get("time") : Long.MAX_VALUE;
        boolean p1Corr = p1.get("correct") != null && (boolean) p1.get("correct");
        boolean p2Corr = p2.get("correct") != null && (boolean) p2.get("correct");

        Map<String, Object> qMap = (Map<String, Object>) questions.get(currentQuestionIndex);
        int correctIdx = ((Long) qMap.get("correctAnswerIndex")).intValue();

        resetButtonColors();
        if (p1Idx != -1 && p1Idx == p2Idx) {
            if (p1Time < p2Time) highlightButtonWithBothColors(p1Idx, COLOR_P1, COLOR_P2);
            else highlightButtonWithBothColors(p1Idx, COLOR_P2, COLOR_P1);
        } else {
            if (p1Idx != -1) highlightButtonWithColor(p1Idx, COLOR_P1);
            if (p2Idx != -1) highlightButtonWithColor(p2Idx, COLOR_P2);
        }

        tvTimer.postDelayed(() -> {
            resetButtonColors();

            // Odredi boju tačnog odgovora
            if (p1Corr && p2Corr) {
                highlightButtonWithColor(correctIdx, p1Time < p2Time ? COLOR_P1 : COLOR_P2);
            } else if (p1Corr) {
                highlightButtonWithColor(correctIdx, COLOR_P1);
            } else if (p2Corr) {
                highlightButtonWithColor(correctIdx, COLOR_P2);
            } else {
                highlightButtonWithColor(correctIdx, "#FFEB3B");
            }

            // Update local statistics
            int myCorrect = (isPlayer1 ? p1Corr : p2Corr) ? 1 : 0;
            int myIdx = isPlayer1 ? p1Idx : p2Idx;
            int myWrong = (!(isPlayer1 ? p1Corr : p2Corr) && myIdx != -1) ? 1 : 0;
            int myPointsAdd;
            if (p1Corr && p2Corr) {
                if (isPlayer1) myPointsAdd = (p1Time < p2Time) ? 10 : 0;
                else myPointsAdd = (p2Time < p1Time) ? 10 : 0;
            } else {
                if (isPlayer1) myPointsAdd = p1Corr ? 10 : (p1Idx != -1 ? -5 : 0);
                else myPointsAdd = p2Corr ? 10 : (p2Idx != -1 ? -5 : 0);
            }
            StatisticsManager.updateKZZStats(myCorrect, myWrong, myPointsAdd, currentQuestionIndex == 0);

            if (isPlayer1) {
                updateFirestoreScoresAndNextQuestion(snap, p1Corr, p2Corr, p1Time, p2Time, p1Idx, p2Idx);
            }

            if (currentQuestionIndex >= 4) {
                if (isPlayer1) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::triggerNextGame, 3000);
                }
            }
        }, 1000);
    }

    private void updateFirestoreScoresAndNextQuestion(DocumentSnapshot snap, boolean p1Corr, boolean p2Corr,
                                                       long p1Time, long p2Time, int p1Idx, int p2Idx) {
        long p1ScoreAdd = 0, p2ScoreAdd = 0;
        if (p1Corr && p2Corr) {
            if (p1Time < p2Time) p1ScoreAdd = 10; else p2ScoreAdd = 10;
        } else {
            if (p1Corr) p1ScoreAdd = 10; else if (p1Idx != -1) p1ScoreAdd = -5;
            if (p2Corr) p2ScoreAdd = 10; else if (p2Idx != -1) p2ScoreAdd = -5;
        }

        Map<String, Object> updates = new HashMap<>();
        Long s1 = snap.getLong("player1Score");
        Long s2 = snap.getLong("player2Score");
        updates.put("player1Score", (s1 != null ? s1 : 0) + p1ScoreAdd);
        updates.put("player2Score", (s2 != null ? s2 : 0) + p2ScoreAdd);
        db.collection("gameRooms").document(roomId).update(updates);

        if (currentQuestionIndex < 4) {
            tvTimer.postDelayed(() -> {
                Map<String, Object> nextUpdates = new HashMap<>();
                nextUpdates.put("currentQuestionIndex", currentQuestionIndex + 1);
                nextUpdates.put("questionStartTime", System.currentTimeMillis());
                db.collection("gameRooms").document(roomId).update(nextUpdates);
            }, 2000);
        }
    }

    private void showNextGameButton() {}

    private void triggerNextGame() {
        if (questionTimer != null) questionTimer.cancel();
        Map<String, Object> updates = new HashMap<>();
        updates.put("currentGame", "spojnice");
        updates.put("currentRound", 1);
        updates.put("spojnice_turn", "p1");
        updates.put("spojnice_currentLeftIndex", 0);
        updates.put("spojnice_matchedRightIndices", java.util.Arrays.asList(-1, -1, -1, -1, -1));
        updates.put("spojnice_whoMatched", java.util.Arrays.asList("", "", "", "", ""));
        updates.put("roundStartTime", System.currentTimeMillis());
        
        db.collection("gameRooms").document(roomId).update(updates);
    }

    private ImageButton getButtonByIndex(int index) {
        if (index == 0) return btnAnswer1;
        if (index == 1) return btnAnswer2;
        if (index == 2) return btnAnswer3;
        if (index == 3) return btnAnswer4;
        return null;
    }

    private void highlightButtonWithBothColors(int index, String colorFirst, String colorSecond) {
        ImageButton btn = getButtonByIndex(index);
        if (btn == null) return;
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor(colorFirst), Color.parseColor(colorFirst), 
                          Color.parseColor(colorSecond), Color.parseColor(colorSecond)}
        );
        gd.setCornerRadius(30); 
        btn.setBackground(gd);
    }

    private void highlightButtonWithColor(int index, String colorHex) {
        ImageButton btn = getButtonByIndex(index);
        if (btn != null) {
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(colorHex)));
        }
    }

    private void highlightButton(int index, boolean correct) {
        if (index < 0) return;
        ImageButton btn = index == 0 ? btnAnswer1 : (index == 1 ? btnAnswer2 : (index == 2 ? btnAnswer3 : btnAnswer4));
        btn.setBackgroundResource(correct ? R.drawable.input_success_bg : R.drawable.input_error_bg);
    }

    private void resetButtonColors() {
        btnAnswer1.setBackgroundResource(R.drawable.input_bg_rounded);
        btnAnswer2.setBackgroundResource(R.drawable.input_bg_rounded);
        btnAnswer3.setBackgroundResource(R.drawable.input_bg_rounded);
        btnAnswer4.setBackgroundResource(R.drawable.input_bg_rounded);
        btnAnswer1.setBackgroundTintList(null);
        btnAnswer2.setBackgroundTintList(null);
        btnAnswer3.setBackgroundTintList(null);
        btnAnswer4.setBackgroundTintList(null);
    }

    private void enableAnswerButtons(boolean enable) {
        btnAnswer1.setEnabled(enable); btnAnswer2.setEnabled(enable);
        btnAnswer3.setEnabled(enable); btnAnswer4.setEnabled(enable);
    }

    private void updateProgressDots() {
        if (llProgress == null) return;
        for (int i = 0; i < llProgress.getChildCount(); i++) {
            llProgress.getChildAt(i).setBackgroundResource(i == currentQuestionIndex ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    private void navigateToSpojnice() {
        if (gameListener != null) gameListener.remove();
        Intent intent = new Intent(this, SpojniceActivity.class);
        intent.putExtra("roomId", roomId);
        intent.putExtra("isPlayer1", isPlayer1);
        startActivity(intent);
        finish();
    }

    private void autoAnswerForOpponent() {
        Map<String, Object> myAnswer = new HashMap<>();
        myAnswer.put("index", -1);
        myAnswer.put("time", System.currentTimeMillis());
        myAnswer.put("correct", false);
        db.collection("gameRooms").document(roomId).update("answers_q" + currentQuestionIndex + "." + (isPlayer1 ? "p2" : "p1"), myAnswer);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameListener != null) gameListener.remove();
        if (questionTimer != null) questionTimer.cancel();
        if (!isFinishing()) {
            db.collection("gameRooms").document(roomId).update("playerLeft", isPlayer1 ? "p1" : "p2");
        }
    }
}