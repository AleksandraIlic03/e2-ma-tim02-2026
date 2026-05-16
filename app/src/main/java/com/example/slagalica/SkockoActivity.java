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

import java.util.Random;

public class SkockoActivity extends AppCompatActivity {

    private int[] targetCombination = new int[4];
    private int[][] attempts = new int[6][4];
    private int currentAttemptIndex = 0;
    private int currentSymbolIndex = 0;

    private TextView tvTimer, tvPoints;
    private LinearLayout[] rows = new LinearLayout[6];
    private CountDownTimer countDownTimer;
    private int totalPoints = 0;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        initViews();
        startNewRound();
    }

    private void initViews() {
        tvTimer = findViewById(R.id.tvTimer);
        tvPoints = findViewById(R.id.tvPoints);
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
                    currentRound = 2;
                    startNewRound();
                } else {
                    finish();
                }
            } else {
                confirmAttempt();
            }
        });
    }

    private void startNewRound() {
        isGameOver = false;
        isOpponentChance = false;
        currentAttemptIndex = 0;
        currentSymbolIndex = 0;
        layoutSolution.setVisibility(View.GONE);
        btnConfirm.setText("POTVRDI");
        btnConfirm.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#823FAB")));
        btnDelete.setVisibility(View.VISIBLE);
        
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 4; j++) attempts[i][j] = -1;
            updateRowUI(i);
            resetHints(i);
        }
        
        generateCombination();
        startTimer(30000);
    }

    private void generateCombination() {
        Random random = new Random();
        for (int i = 0; i < 4; i++) {
            targetCombination[i] = random.nextInt(6);
        }
    }

    private void startTimer(long millis) {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText("⏱ " + (ms / 1000) + "s");
            }

            @Override
            public void onFinish() {
                tvTimer.setText("⏱ 0s");
                if (!isOpponentChance && !isGameOver) {
                    startOpponentChance();
                } else if (!isGameOver) {
                    endRound(false);
                }
            }
        }.start();
    }

    private void startOpponentChance() {
        isOpponentChance = true;
        currentAttemptIndex = 5;
        currentSymbolIndex = 0;
        Toast.makeText(this, "Šansa za protivnika! (10s)", Toast.LENGTH_SHORT).show();
        startTimer(10000);
    }

    private void addSymbol(int symbolIndex) {
        if (isGameOver || currentAttemptIndex >= 6 || currentSymbolIndex >= 4) return;
        attempts[currentAttemptIndex][currentSymbolIndex] = symbolIndex;
        updateRowUI(currentAttemptIndex);
        currentSymbolIndex++;
    }

    private void removeSymbol() {
        if (isGameOver) return;
        if (currentSymbolIndex > 0) {
            currentSymbolIndex--;
            attempts[currentAttemptIndex][currentSymbolIndex] = -1;
            updateRowUI(currentAttemptIndex);
        }
    }

    private void confirmAttempt() {
        if (isGameOver) return;
        if (currentSymbolIndex < 4) {
            Toast.makeText(this, "Popunite sva polja!", Toast.LENGTH_SHORT).show();
            return;
        }
        checkAttempt();
    }

    private void checkAttempt() {
        int[] currentAttempt = attempts[currentAttemptIndex];
        int correctPlace = 0;
        int wrongPlace = 0;
        boolean[] targetUsed = new boolean[4];
        boolean[] attemptUsed = new boolean[4];

        for (int i = 0; i < 4; i++) {
            if (currentAttempt[i] == targetCombination[i]) {
                correctPlace++;
                targetUsed[i] = true;
                attemptUsed[i] = true;
            }
        }

        for (int i = 0; i < 4; i++) {
            if (attemptUsed[i]) continue;
            for (int j = 0; j < 4; j++) {
                if (!targetUsed[j] && currentAttempt[i] == targetCombination[j]) {
                    wrongPlace++;
                    targetUsed[j] = true;
                    break;
                }
            }
        }

        updateHints(currentAttemptIndex, correctPlace, wrongPlace);

        if (correctPlace == 4) {
            if (isOpponentChance) totalPoints += 10;
            else calculatePoints();
            tvPoints.setText("⭐ " + totalPoints);
            endRound(true);
        } else {
            if (isOpponentChance) {
                endRound(false);
            } else {
                currentAttemptIndex++;
                currentSymbolIndex = 0;
                if (currentAttemptIndex >= 6) {
                    startOpponentChance();
                }
            }
        }
    }

    private void calculatePoints() {
        if (currentAttemptIndex < 2) totalPoints += 20;
        else if (currentAttemptIndex < 4) totalPoints += 15;
        else totalPoints += 10;
    }

    private void updateHints(int rowIndex, int correctPlace, int wrongPlace) {
        LinearLayout row = rows[rowIndex];
        GridLayout hintGrid = (GridLayout) row.getChildAt(1);
        for (int i = 0; i < 4; i++) {
            ImageView hint = (ImageView) hintGrid.getChildAt(i);
            if (i < correctPlace) hint.setImageTintList(ColorStateList.valueOf(Color.RED));
            else if (i < correctPlace + wrongPlace) hint.setImageTintList(ColorStateList.valueOf(Color.YELLOW));
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

    private void endRound(boolean win) {
        if (countDownTimer != null) countDownTimer.cancel();
        isGameOver = true;
        showTargetCombination();
        
        Intent intent = new Intent(GameEventReceiver.ACTION_GAME_FINISHED);
        intent.putExtra("title", "Skočko - Rezultat");
        intent.putExtra("message", "Završena runda " + currentRound + ". Osvojeno: " + totalPoints + " poena.");
        intent.putExtra("type", "rewards");
        sendBroadcast(intent);

        if (currentRound == 1) {
            btnConfirm.setText("SLEDEĆA RUNDA");
        } else {
            btnConfirm.setText("ZAVRŠI IGRU");
        }
        btnConfirm.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        btnDelete.setVisibility(View.GONE);
    }

    private void showTargetCombination() {
        layoutSolution.setVisibility(View.VISIBLE);
        for (int i = 0; i < 4; i++) {
            ivSolutions[i].setImageResource(symbolDrawables[targetCombination[i]]);
        }
    }
}
