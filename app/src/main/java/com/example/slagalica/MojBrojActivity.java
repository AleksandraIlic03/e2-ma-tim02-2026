package com.example.slagalica;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MojBrojActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvPlayer1Name, tvPlayer1Points, tvPlayer2Name, tvPlayer2Points;
    private TextView tvRound, tvTimer, tvTargetNumber, tvExpression, tvResult;
    private LinearLayout llNumberButtons, layoutWaiting, layoutGame;

    private String player1Name, player2Name;
    private int player1Score, player2Score;
    private int myPoints = 0;
    private int currentRound = 1;

    private int targetNumber;
    private List<Integer> numbers = new ArrayList<>();
    private StringBuilder expression = new StringBuilder();
    private List<Integer> usedIndices = new ArrayList<>();

    private CountDownTimer roundTimer;
    private CountDownTimer stopTimer;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;

    private boolean numbersRevealed = false;
    private int round1Result = -1;

    private static final int ROUND_DURATION_MS = 60000;
    private static final float SHAKE_THRESHOLD = 12f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        initViews();
        getIntentData();
        updateHeader();
        setupShakeSensor();
        startRound();
    }

    private void initViews() {
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer1Points = findViewById(R.id.tvPlayer1Points);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);
        tvPlayer2Points = findViewById(R.id.tvPlayer2Points);
        tvRound = findViewById(R.id.tvRound);
        tvTimer = findViewById(R.id.tvTimer);
        tvTargetNumber = findViewById(R.id.tvTargetNumber);
        tvExpression = findViewById(R.id.tvExpression);
        tvResult = findViewById(R.id.tvResult);
        llNumberButtons = findViewById(R.id.llNumberButtons);
        layoutWaiting = findViewById(R.id.layoutWaiting);
        layoutGame = findViewById(R.id.layoutGame);

        findViewById(R.id.btnPlus).setOnClickListener(v -> appendOperator("+"));
        findViewById(R.id.btnMinus).setOnClickListener(v -> appendOperator("-"));
        findViewById(R.id.btnMultiply).setOnClickListener(v -> appendOperator("*"));
        findViewById(R.id.btnDivide).setOnClickListener(v -> appendOperator("/"));
        findViewById(R.id.btnOpenParen).setOnClickListener(v -> appendOperator("("));
        findViewById(R.id.btnCloseParen).setOnClickListener(v -> appendOperator(")"));
        findViewById(R.id.btnBackspace).setOnClickListener(v -> backspace());
        findViewById(R.id.btnClear).setOnClickListener(v -> clearExpression());
        findViewById(R.id.btnStop).setOnClickListener(v -> revealNumbers());
        findViewById(R.id.btnConfirm).setOnClickListener(v -> confirmAnswer());
    }

    private void getIntentData() {
        Intent intent = getIntent();
        player1Name = intent.getStringExtra("player1Name") != null
                ? intent.getStringExtra("player1Name") : "Igrac 1";
        player2Name = intent.getStringExtra("player2Name") != null
                ? intent.getStringExtra("player2Name") : "Igrac 2";
        player1Score = intent.getIntExtra("player1Score", 0);
        player2Score = intent.getIntExtra("player2Score", 0);
    }

    private void updateHeader() {
        tvPlayer1Name.setText(player1Name);
        tvPlayer2Name.setText(player2Name);
        tvPlayer1Points.setText(String.valueOf(player1Score + myPoints));
        tvPlayer2Points.setText(String.valueOf(player2Score));
        tvRound.setText("Runda " + currentRound + "/2");
    }

    private void startRound() {
        expression = new StringBuilder();
        usedIndices.clear();
        numbers.clear();
        numbersRevealed = false;
        tvExpression.setText("");
        tvResult.setText("= ?");
        tvTargetNumber.setText("?");
        tvTimer.setText("5");
        updateHeader();
        generateNumbers();
        startStopTimer();
    }

    private void generateNumbers() {
        Random random = new Random();
        targetNumber = random.nextInt(900) + 100;

        numbers.clear();
        for (int i = 0; i < 4; i++) {
            numbers.add(random.nextInt(9) + 1);
        }
        int[] medium = {10, 15, 20};
        numbers.add(medium[random.nextInt(medium.length)]);
        int[] large = {25, 50, 75, 100};
        numbers.add(large[random.nextInt(large.length)]);

        llNumberButtons.removeAllViews();
        for (int i = 0; i < numbers.size(); i++) {
            final int index = i;
            MaterialButton btn = new MaterialButton(this);
            btn.setText("?");
            btn.setTextSize(11f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, 120, 1f);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF9B59B6));

            params.setMargins(3, 3, 3, 3);
            btn.setLayoutParams(params);
            btn.setCornerRadius(16);
            btn.setOnClickListener(v -> {
                if (numbersRevealed && !usedIndices.contains(index)) {
                    usedIndices.add(index);
                    expression.append(numbers.get(index));
                    updateExpression();
                    btn.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFFD5C4E0));
                }
            });
            llNumberButtons.addView(btn);
        }
    }

    private void startStopTimer() {
        if (stopTimer != null) stopTimer.cancel();
        stopTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(String.valueOf(millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                if (!numbersRevealed) revealNumbers();
            }
        }.start();
    }

    private void revealNumbers() {
        if (numbersRevealed) return;
        numbersRevealed = true;
        if (stopTimer != null) stopTimer.cancel();

        // Otkrij target broj
        tvTargetNumber.setText(String.valueOf(targetNumber));

        // Otkrij brojeve na dugmadima
        for (int i = 0; i < llNumberButtons.getChildCount(); i++) {
            MaterialButton btn = (MaterialButton) llNumberButtons.getChildAt(i);
            btn.setText(String.valueOf(numbers.get(i)));
            if (numbers.get(i) >= 25) {
                btn.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFE94560));
            } else if (numbers.get(i) >= 10) {
                btn.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFFF912B));
            }
        }

        startRoundTimer();
    }

    private void startRoundTimer() {
        if (roundTimer != null) roundTimer.cancel();
        roundTimer = new CountDownTimer(ROUND_DURATION_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(String.valueOf(millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                confirmAnswer();
            }
        }.start();
    }

    private void appendOperator(String op) {
        if (!numbersRevealed) return;
        expression.append(op);
        updateExpression();
    }

    private void backspace() {
        if (expression.length() > 0) {
            String expr = expression.toString();
            for (int i = usedIndices.size() - 1; i >= 0; i--) {
                int idx = usedIndices.get(i);
                String num = String.valueOf(numbers.get(idx));
                if (expr.endsWith(num)) {
                    usedIndices.remove(i);
                    expression.delete(expression.length() - num.length(),
                            expression.length());
                    MaterialButton btn =
                            (MaterialButton) llNumberButtons.getChildAt(idx);
                    if (numbers.get(idx) >= 25) {
                        btn.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFFE94560));
                    } else if (numbers.get(idx) >= 10) {
                        btn.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFFFF912B));
                    } else {
                        btn.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFF9B59B6));
                    }
                    updateExpression();
                    return;
                }
            }
            expression.deleteCharAt(expression.length() - 1);
            updateExpression();
        }
    }

    private void clearExpression() {
        expression = new StringBuilder();
        usedIndices.clear();
        for (int i = 0; i < llNumberButtons.getChildCount(); i++) {
            MaterialButton btn = (MaterialButton) llNumberButtons.getChildAt(i);
            if (numbers.get(i) >= 25) {
                btn.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFE94560));
            } else if (numbers.get(i) >= 10) {
                btn.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFFF912B));
            } else {
                btn.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF9B59B6));
            }
        }
        updateExpression();
    }

    private void updateExpression() {
        String expr = expression.toString();
        tvExpression.setText(expr);
        tvResult.setText("= ?");
    }

    private int evaluate(String expr) {
        expr = expr.trim();
        if (expr.startsWith("(") && expr.endsWith(")")) {
            expr = expr.substring(1, expr.length() - 1);
        }

        int depth = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth == 0 && (c == '+' || c == '-') && i > 0) {
                return evaluate(expr.substring(0, i)) +
                        (c == '+' ? 1 : -1) * evaluate(expr.substring(i + 1));
            }
        }

        depth = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth == 0 && (c == '*' || c == '/') && i > 0) {
                int left = evaluate(expr.substring(0, i));
                int right = evaluate(expr.substring(i + 1));
                if (c == '/') {
                    if (right == 0) throw new ArithmeticException("Deljenje nulom");
                    return left / right;
                }
                return left * right;
            }
        }

        return Integer.parseInt(expr.trim());
    }

    private void confirmAnswer() {
        if (roundTimer != null) roundTimer.cancel();

        int result = -1;
        String expr = expression.toString();
        if (!expr.isEmpty()) {
            try {
                result = evaluate(expr);
                tvResult.setText("= " + result);
            } catch (Exception e) {
                tvResult.setText("= ?");
                result = -1;
            }
        }

        final int finalResult = result;
        new android.os.Handler().postDelayed(() -> {
            if (currentRound == 1) {
                round1Result = finalResult;
                if (finalResult == targetNumber) {
                    myPoints += 10;
                    Toast.makeText(this, "Tacno! +10 bodova", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Runda 1 zavrsena. Rezultat: " +
                                    (finalResult == -1 ? "nije unet" : String.valueOf(finalResult)),
                            Toast.LENGTH_SHORT).show();
                }
                currentRound = 2;
                showWaitingScreen();
                new android.os.Handler().postDelayed(() -> {
                    showGameScreen();
                    startRound();
                }, 3000);
            } else {
                if (finalResult == targetNumber) {
                    myPoints += 10;
                    Toast.makeText(this, "Tacno! +10 bodova", Toast.LENGTH_SHORT).show();
                } else if (finalResult != -1 && round1Result != -1) {
                    int diff1 = Math.abs(round1Result - targetNumber);
                    int diff2 = Math.abs(finalResult - targetNumber);
                    if (diff2 < diff1) {
                        myPoints += 5;
                        Toast.makeText(this, "Blize! +5 bodova", Toast.LENGTH_SHORT).show();
                    } else if (diff1 < diff2) {
                        Toast.makeText(this, "Runda 1 bila blize.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Jednako daleko.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
                updateHeader();
                finishGame();
            }
        }, 1500);
    }

    private void showGameScreen() {
        layoutGame.setVisibility(android.view.View.VISIBLE);
        layoutWaiting.setVisibility(android.view.View.GONE);
    }

    private void showWaitingScreen() {
        layoutGame.setVisibility(android.view.View.GONE);
        layoutWaiting.setVisibility(android.view.View.VISIBLE);
    }

    private void setupShakeSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double acceleration = Math.sqrt(x * x + y * y + z * z) -
                SensorManager.GRAVITY_EARTH;
        if (acceleration > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > 1000) {
                lastShakeTime = now;
                revealNumbers();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void finishGame() {
        Intent intent = new Intent(this, KoZnaZnaActivity.class);
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
        if (roundTimer != null) roundTimer.cancel();
        if (stopTimer != null) stopTimer.cancel();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }
}