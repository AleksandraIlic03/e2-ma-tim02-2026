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

import java.util.ArrayList;
import java.util.List;

public class KorakPoKorakActivity extends AppCompatActivity {

    private TextView tvPlayer1Name, tvPlayer1Points, tvPlayer2Name, tvPlayer2Points;
    private TextView tvRound, tvStepTimer, tvCurrentPoints;
    private TextInputEditText etAnswer;
    private LinearLayout layoutWaiting, layoutGame, llSteps;

    private String player1Name, player2Name;
    private int player1Score, player2Score;
    private int myPoints = 0;
    private int currentRound = 1;
    private int currentStep = 0;
    private boolean guessedCorrectly = false;

    private CountDownTimer stepTimer;

    private String answer = "";
    private String answer2 = "";
    private List<String> steps = new ArrayList<>();
    private List<String> steps2 = new ArrayList<>();

    private static final int[] POINTS_PER_STEP = {20, 18, 16, 14, 12, 10, 8};
    private static final int STEP_DURATION_MS = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        initViews();
        getIntentData();
        loadGameData();
        updateHeader();
        showGameScreen();
        startRound();
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

    private void getIntentData() {
        Intent intent = getIntent();
        player1Name = intent.getStringExtra("player1Name") != null
                ? intent.getStringExtra("player1Name") : "Igrač 1";
        player2Name = intent.getStringExtra("player2Name") != null
                ? intent.getStringExtra("player2Name") : "Igrač 2";
        player1Score = intent.getIntExtra("player1Score", 0);
        player2Score = intent.getIntExtra("player2Score", 0);
    }

    private void loadGameData() {
        // Pojam 1 - runda 1
        steps.add("Romul i Rem");
        steps.add("Sedam brežuljaka");
        steps.add("Vatikan");
        steps.add("Koloseum");
        steps.add("Pasta i pica");
        steps.add("Tiber");
        steps.add("Večni grad");
        answer = "Rim";

        // Pojam 2 - runda 2
        steps2.add("Sfinga");
        steps2.add("Pustinja");
        steps2.add("Nil");
        steps2.add("Mumije");
        steps2.add("Faraoni");
        steps2.add("Piramide");
        steps2.add("Kleopatra");
        answer2 = "Egipat";
    }

    private void updateHeader() {
        tvPlayer1Name.setText(player1Name);
        tvPlayer2Name.setText(player2Name);
        tvPlayer1Points.setText(String.valueOf(player1Score + myPoints));
        tvPlayer2Points.setText(String.valueOf(player2Score));
        tvRound.setText("Runda " + currentRound + "/2");
    }

    private void showGameScreen() {
        layoutGame.setVisibility(View.VISIBLE);
        layoutWaiting.setVisibility(View.GONE);
    }

    private void showWaitingScreen() {
        layoutGame.setVisibility(View.GONE);
        layoutWaiting.setVisibility(View.VISIBLE);
    }

    private void startRound() {
        currentStep = 0;
        guessedCorrectly = false;
        llSteps.removeAllViews();
        updateHeader();
        revealNextStep();
    }

    private void revealNextStep() {
        if (currentStep >= steps.size()) {
            endRound(false);
            return;
        }

        addStepCard(currentStep);
        int points = POINTS_PER_STEP[Math.min(currentStep, POINTS_PER_STEP.length - 1)];
        tvCurrentPoints.setText("Pogodi za " + points + " bodova");

        if (stepTimer != null) stepTimer.cancel();
        stepTimer = new CountDownTimer(STEP_DURATION_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvStepTimer.setText("⏱️ " + (millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                currentStep++;
                revealNextStep();
            }
        }.start();
    }

    private void addStepCard(int stepIndex) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_step_card, llSteps, false);
        TextView tvStepNumber = card.findViewById(R.id.tvStepNumber);
        TextView tvStepText = card.findViewById(R.id.tvStepText);

        tvStepNumber.setText(String.valueOf(steps.size() - stepIndex));
        tvStepText.setText(steps.get(stepIndex));

        int[] colors = {0xFF823FAB, 0xFF9B59B6, 0xFFA873C7, 0xFFB990D4, 0xFFCCADE0, 0xFFD5C4E0, 0xFFE0D5EE};
        int color = colors[Math.min(stepIndex, colors.length - 1)];
        ((CardView) card).setCardBackgroundColor(color);

        llSteps.addView(card, 0);
    }

    private void checkAnswer() {
        if (stepTimer != null) stepTimer.cancel();

        String userAnswer = etAnswer.getText().toString().trim();
        etAnswer.setText("");

        if (userAnswer.equalsIgnoreCase(answer)) {
            int points = POINTS_PER_STEP[Math.min(currentStep, POINTS_PER_STEP.length - 1)];
            myPoints += points;
            guessedCorrectly = true;
            Toast.makeText(this, "Tačno! +" + points + " bodova", Toast.LENGTH_SHORT).show();
            updateHeader();
            endRound(true);
        } else {
            Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
            currentStep++;
            revealNextStep();
        }
    }

    private void endRound(boolean guessed) {
        if (stepTimer != null) stepTimer.cancel();

        if (currentRound == 1) {
            currentRound = 2;
            steps.clear();
            steps.addAll(steps2);
            answer = answer2;
            showWaitingScreen();
            new android.os.Handler().postDelayed(() -> {
                showGameScreen();
                startRound();
            }, 3000);
        } else {
            finishGame();
        }
    }

    private void finishGame() {
        Intent intent = new Intent(this, MojBrojActivity.class);
        intent.putExtra("player1Name", player1Name);
        intent.putExtra("player2Name", player2Name);
        intent.putExtra("player1Score", player1Score + myPoints);
        intent.putExtra("player2Score", player2Score);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stepTimer != null) stepTimer.cancel();
    }
}