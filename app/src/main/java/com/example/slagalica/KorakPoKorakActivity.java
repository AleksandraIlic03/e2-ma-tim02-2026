package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KorakPoKorakActivity extends AppCompatActivity {

    private TextView tvPlayer1Name, tvPlayer1Points, tvPlayer2Name, tvPlayer2Points;
    private TextView tvRound, tvStepTimer, tvCurrentPoints;
    private TextInputEditText etAnswer;
    private LinearLayout layoutWaiting, layoutGame, llSteps;

    private FirebaseFirestore db;
    private String roomId;
    private boolean isPlayer1;
    private ListenerRegistration gameListener;

    private String phase = "";
    private int currentStep = 0;
    private int lastSeenStep = -1;
    private int myPoints = 0;
    private boolean transitioning = false;

    private CountDownTimer stepTimer;

    private final List<String> steps1 = new ArrayList<>();
    private final List<String> steps2 = new ArrayList<>();
    private List<String> currentSteps = new ArrayList<>();
    private String currentAnswer = "";

    private static final int[] POINTS_PER_STEP = {20, 18, 16, 14, 12, 10, 7};
    private static final int BONUS_POINTS = 5;
    private static final int STEP_DURATION_MS = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        db = FirebaseFirestore.getInstance();
        roomId = getIntent().getStringExtra("roomId");
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);

        initViews();
        loadGameData();
        attachGameListener();
    }

    private void initViews() {
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer1Points = findViewById(R.id.tvPlayer1Points);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);
        tvPlayer2Points = findViewById(R.id.tvPlayer2Points);
        tvRound = findViewById(R.id.tvRound);
        tvStepTimer = findViewById(R.id.tvStepTimer);
        tvCurrentPoints = findViewById(R.id.tvCurrentPoints);
        etAnswer = findViewById(R.id.etAnswer);
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutGame = findViewById(R.id.layoutGame);
        llSteps = findViewById(R.id.llSteps);
        findViewById(R.id.btnGuess).setOnClickListener(v -> checkAnswer());
    }

    private void loadGameData() {
        steps1.add("Romul i Rem");
        steps1.add("Sedam brezuljaka");
        steps1.add("Vatikan");
        steps1.add("Koloseum");
        steps1.add("Pasta i pica");
        steps1.add("Tiber");
        steps1.add("Vecni grad");

        steps2.add("Sfinga");
        steps2.add("Pustinja");
        steps2.add("Nil");
        steps2.add("Mumije");
        steps2.add("Faraoni");
        steps2.add("Piramide");
        steps2.add("Kleopatra");
    }

    private void attachGameListener() {
        gameListener = db.collection("gameRooms").document(roomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) return;
                    runOnUiThread(() -> handleGameUpdate(snapshot));
                });
    }

    private void handleGameUpdate(com.google.firebase.firestore.DocumentSnapshot snapshot) {
        if (transitioning) return;

        // Prelaz na sledecu igru
        String currentGame = snapshot.getString("currentGame");
        if ("mojBroj".equals(currentGame)) {
            transitioning = true;
            if (gameListener != null) gameListener.remove();
            if (stepTimer != null) stepTimer.cancel();
            Intent intent = new Intent(this, MojBrojActivity.class);
            intent.putExtra("roomId", roomId);
            intent.putExtra("isPlayer1", isPlayer1);
            startActivity(intent);
            finish();
            return;
        }

        // Header
        tvPlayer1Name.setText(snapshot.getString("player1Name"));
        tvPlayer2Name.setText(snapshot.getString("player2Name"));
        long p1Score = snapshot.getLong("player1Score") != null ? snapshot.getLong("player1Score") : 0;
        long p2Score = snapshot.getLong("player2Score") != null ? snapshot.getLong("player2Score") : 0;
        tvPlayer1Points.setText(String.valueOf(p1Score + (isPlayer1 ? myPoints : 0)));
        tvPlayer2Points.setText(String.valueOf(p2Score + (!isPlayer1 ? myPoints : 0)));

        String newPhase = snapshot.getString("korak_phase");
        if (newPhase == null) newPhase = "p1_playing";

        if (!newPhase.equals(phase)) {
            phase = newPhase;
            handlePhaseChange();
        }

        // Gledalac vidi korake u realnom vremenu i nadoknadjuje propustene
        if (isWatcher()) {
            Long firestoreStep = snapshot.getLong("korak_currentStep");
            if (firestoreStep != null) {
                int stepInt = firestoreStep.intValue();
                if (stepInt >= 0 && stepInt > lastSeenStep) {
                    for (int i = lastSeenStep + 1; i <= stepInt; i++) {
                        if (i < currentSteps.size()) {
                            addStepCard(i, currentSteps.get(i));
                        }
                    }
                    lastSeenStep = stepInt;
                    tvCurrentPoints.setText("Protivnik igra...");
                    tvStepTimer.setText("");
                }
            }
        }
    }

    private boolean isWatcher() {
        switch (phase) {
            case "p1_playing": return !isPlayer1;
            case "p2_bonus":   return isPlayer1;
            case "p2_playing": return isPlayer1;
            case "p1_bonus":   return !isPlayer1;
            default:           return false;
        }
    }

    private boolean isMyActivePhase() {
        switch (phase) {
            case "p1_playing": return isPlayer1;
            case "p2_bonus":   return !isPlayer1;
            case "p2_playing": return !isPlayer1;
            case "p1_bonus":   return isPlayer1;
            default:           return false;
        }
    }

    private void handlePhaseChange() {
        if (stepTimer != null) stepTimer.cancel();
        llSteps.removeAllViews();
        lastSeenStep = -1;
        currentStep = 0;

        switch (phase) {
            case "p1_playing":
                currentSteps = new ArrayList<>(steps1);
                currentAnswer = "Rim";
                tvRound.setText("Runda 1/2");
                showGameScreen();
                if (isPlayer1) {
                    enableInput(true);
                    tvCurrentPoints.setText("Tvoj red");
                    startRound();
                } else {
                    enableInput(false);
                    tvCurrentPoints.setText("Protivnik igra...");
                }
                break;

            case "p2_bonus":
                currentSteps = new ArrayList<>(steps1);
                currentAnswer = "Rim";
                tvRound.setText("Bonus sansa! +5 bodova");
                showAllSteps(steps1);
                if (!isPlayer1) {
                    enableInput(true);
                    startBonusTimer();
                } else {
                    enableInput(false);
                    tvCurrentPoints.setText("Protivnik ima bonus sansu...");
                }
                break;

            case "p2_playing":
                currentSteps = new ArrayList<>(steps2);
                currentAnswer = "Egipat";
                tvRound.setText("Runda 2/2");
                showGameScreen();
                if (!isPlayer1) {
                    enableInput(true);
                    tvCurrentPoints.setText("Tvoj red");
                    startRound();
                } else {
                    enableInput(false);
                    tvCurrentPoints.setText("Protivnik igra...");
                }
                break;

            case "p1_bonus":
                currentSteps = new ArrayList<>(steps2);
                currentAnswer = "Egipat";
                tvRound.setText("Bonus sansa! +5 bodova");
                showAllSteps(steps2);
                if (isPlayer1) {
                    enableInput(true);
                    startBonusTimer();
                } else {
                    enableInput(false);
                    tvCurrentPoints.setText("Protivnik ima bonus sansu...");
                }
                break;

            case "done":
                finishGame();
                break;
        }
    }

    private void startRound() {
        currentStep = 0;
        llSteps.removeAllViews();
        revealNextStep();
    }

    private void revealNextStep() {
        if (currentStep >= currentSteps.size()) {
            onRoundFailed();
            return;
        }

        String stepText = currentSteps.get(currentStep);
        addStepCard(currentStep, stepText);

        Map<String, Object> update = new HashMap<>();
        update.put("korak_currentStep", currentStep);
        update.put("korak_currentStepText", stepText);
        db.collection("gameRooms").document(roomId).update(update);

        int points = POINTS_PER_STEP[Math.min(currentStep, POINTS_PER_STEP.length - 1)];
        tvCurrentPoints.setText("Pogodi za " + points + " bodova");

        if (stepTimer != null) stepTimer.cancel();
        stepTimer = new CountDownTimer(STEP_DURATION_MS, 1000) {
            @Override
            public void onTick(long ms) {
                tvStepTimer.setText("⏱️ " + (ms / 1000) + "s");
            }
            @Override
            public void onFinish() {
                currentStep++;
                revealNextStep();
            }
        }.start();
    }

    private void startBonusTimer() {
        tvCurrentPoints.setText("Pogodi za " + BONUS_POINTS + " bodova");
        if (stepTimer != null) stepTimer.cancel();
        stepTimer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long ms) {
                tvStepTimer.setText("⏱️ " + (ms / 1000) + "s");
            }
            @Override
            public void onFinish() {
                onBonusFailed();
            }
        }.start();
    }

    private void showAllSteps(List<String> stepsToShow) {
        llSteps.removeAllViews();
        for (int i = 0; i < stepsToShow.size(); i++) {
            addStepCard(i, stepsToShow.get(i));
        }
        showGameScreen();
    }

    private void addStepCard(int stepIndex, String text) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_step_card, llSteps, false);
        TextView tvStepNumber = card.findViewById(R.id.tvStepNumber);
        TextView tvStepText = card.findViewById(R.id.tvStepText);
        int totalSteps = currentSteps.isEmpty() ? 7 : currentSteps.size();
        tvStepNumber.setText(String.valueOf(totalSteps - stepIndex));
        tvStepText.setText(text);
        int[] colors = {0xFF823FAB, 0xFF9B59B6, 0xFFA873C7, 0xFFB990D4, 0xFFCCADE0, 0xFFD5C4E0, 0xFFE0D5EE};
        ((CardView) card).setCardBackgroundColor(colors[Math.min(stepIndex, colors.length - 1)]);
        llSteps.addView(card, 0);
    }

    private void checkAnswer() {
        if (!isMyActivePhase()) return;
        if (stepTimer != null) stepTimer.cancel();

        String userAnswer = etAnswer.getText().toString().trim();
        etAnswer.setText("");

        if (userAnswer.equalsIgnoreCase(currentAnswer)) {
            boolean isBonus = phase.equals("p2_bonus") || phase.equals("p1_bonus");
            int points = isBonus ? BONUS_POINTS : POINTS_PER_STEP[Math.min(currentStep, POINTS_PER_STEP.length - 1)];
            myPoints += points;
            Toast.makeText(this, "Tacno! +" + points + " bodova", Toast.LENGTH_SHORT).show();
            onRoundGuessed();
        } else {
            Toast.makeText(this, "Netacno!", Toast.LENGTH_SHORT).show();
            if (phase.equals("p2_bonus") || phase.equals("p1_bonus")) {
                onBonusFailed();
            } else {
                currentStep++;
                revealNextStep();
            }
        }
    }

    private void onRoundGuessed() {
        if (stepTimer != null) stepTimer.cancel();
        saveMyScore();
        switch (phase) {
            case "p1_playing": setPhase("p2_playing"); break;
            case "p2_bonus":   setPhase("p2_playing"); break;
            case "p2_playing": setPhase("done"); break;
            case "p1_bonus":   setPhase("done"); break;
        }
    }

    private void onRoundFailed() {
        if (stepTimer != null) stepTimer.cancel();
        saveMyScore();
        switch (phase) {
            case "p1_playing": setPhase("p2_bonus"); break;
            case "p2_playing": setPhase("p1_bonus"); break;
        }
    }

    private void onBonusFailed() {
        if (stepTimer != null) stepTimer.cancel();
        saveMyScore();
        switch (phase) {
            case "p2_bonus": setPhase("p2_playing"); break;
            case "p1_bonus": setPhase("done"); break;
        }
    }

    private void setPhase(String newPhase) {
        db.collection("gameRooms").document(roomId).update("korak_phase", newPhase);
    }

    private void saveMyScore() {
        if (myPoints == 0) return;
        int pts = myPoints;
        myPoints = 0;
        String field = isPlayer1 ? "player1Score" : "player2Score";
        db.collection("gameRooms").document(roomId).get()
                .addOnSuccessListener(snapshot -> {
                    long current = snapshot.getLong(field) != null ? snapshot.getLong(field) : 0;
                    db.collection("gameRooms").document(roomId).update(field, current + pts);
                });
    }

    private void enableInput(boolean enabled) {
        if (etAnswer != null) { etAnswer.setEnabled(enabled); etAnswer.setAlpha(enabled ? 1f : 0.4f); }
        View btn = findViewById(R.id.btnGuess);
        if (btn != null) { btn.setEnabled(enabled); btn.setAlpha(enabled ? 1f : 0.4f); }
    }

    private void showGameScreen() {
        layoutGame.setVisibility(View.VISIBLE);
        layoutWaiting.setVisibility(View.GONE);
    }

    private void finishGame() {
        if (stepTimer != null) stepTimer.cancel();

        if (isPlayer1) {
            if (gameListener != null) gameListener.remove();
            Map<String, Object> updates = new HashMap<>();
            updates.put("currentGame", "mojBroj");
            db.collection("gameRooms").document(roomId).update(updates)
                    .addOnSuccessListener(unused -> {
                        Intent intent = new Intent(this, MojBrojActivity.class);
                        intent.putExtra("roomId", roomId);
                        intent.putExtra("isPlayer1", true);
                        startActivity(intent);
                        finish();
                    });
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stepTimer != null) stepTimer.cancel();
        if (gameListener != null) gameListener.remove();
    }
}