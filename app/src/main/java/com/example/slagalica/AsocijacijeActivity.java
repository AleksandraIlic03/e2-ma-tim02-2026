package com.example.slagalica;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.models.AsocijacijaModel;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class AsocijacijeActivity extends AppCompatActivity {

    private AsocijacijaModel currentAsocijacija;
    private int totalPoints = 0;
    private int roundPoints = 0;
    private TextView tvTimer, tvPoints;
    private CountDownTimer countDownTimer;
    private MaterialButton btnNextAction;

    private final MaterialButton[] buttonsA = new MaterialButton[4];
    private final MaterialButton[] buttonsB = new MaterialButton[4];
    private final MaterialButton[] buttonsV = new MaterialButton[4];
    private final MaterialButton[] buttonsG = new MaterialButton[4];

    private EditText etResenjeA, etResenjeB, etResenjeV, etResenjeG, etKonacnoResenje;

    private boolean isA_Solved = false, isB_Solved = false, isV_Solved = false, isG_Solved = false;
    private boolean isFinalSolved = false;
    private int currentRound = 1;

    private final boolean[][] openedFields = new boolean[4][4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        initViews();
        loadRoundData(currentRound);
        startTimer();
    }

    private void initViews() {
        tvTimer = findViewById(R.id.tvTimer);
        tvPoints = findViewById(R.id.tvPoints);
        btnNextAction = findViewById(R.id.btnNextGame);

        buttonsA[0] = findViewById(R.id.btnA1); buttonsA[1] = findViewById(R.id.btnA2);
        buttonsA[2] = findViewById(R.id.btnA3); buttonsA[3] = findViewById(R.id.btnA4);
        buttonsB[0] = findViewById(R.id.btnB1); buttonsB[1] = findViewById(R.id.btnB2);
        buttonsB[2] = findViewById(R.id.btnB3); buttonsB[3] = findViewById(R.id.btnB4);
        buttonsV[0] = findViewById(R.id.btnV1); buttonsV[1] = findViewById(R.id.btnV2);
        buttonsV[2] = findViewById(R.id.btnV3); buttonsV[3] = findViewById(R.id.btnV4);
        buttonsG[0] = findViewById(R.id.btnG1); buttonsG[1] = findViewById(R.id.btnG2);
        buttonsG[2] = findViewById(R.id.btnG3); buttonsG[3] = findViewById(R.id.btnG4);

        etResenjeA = findViewById(R.id.etResenjeA);
        etResenjeB = findViewById(R.id.etResenjeB);
        etResenjeV = findViewById(R.id.etResenjeV);
        etResenjeG = findViewById(R.id.etResenjeG);
        etKonacnoResenje = findViewById(R.id.etKonacnoResenje);

        setupListeners();
    }

    private void setupListeners() {
        for (int i = 0; i < 4; i++) {
            final int index = i;
            buttonsA[i].setOnClickListener(v -> openField('A', index));
            buttonsB[i].setOnClickListener(v -> openField('B', index));
            buttonsV[i].setOnClickListener(v -> openField('V', index));
            buttonsG[i].setOnClickListener(v -> openField('G', index));
        }

        etResenjeA.setOnEditorActionListener((v, id, e) -> handleInput(id, e, 'A'));
        etResenjeB.setOnEditorActionListener((v, id, e) -> handleInput(id, e, 'B'));
        etResenjeV.setOnEditorActionListener((v, id, e) -> handleInput(id, e, 'V'));
        etResenjeG.setOnEditorActionListener((v, id, e) -> handleInput(id, e, 'G'));
        etKonacnoResenje.setOnEditorActionListener((v, id, e) -> {
            if (isConfirmAction(id, e)) { checkFinal(); return true; }
            return false;
        });

        btnNextAction.setOnClickListener(v -> {
            if (currentRound == 1) {
                currentRound = 2;
                btnNextAction.setVisibility(View.GONE);
                loadRoundData(currentRound);
                startTimer();
            } else {
                finish();
            }
        });
    }

    private boolean handleInput(int id, KeyEvent e, char col) {
        if (isConfirmAction(id, e)) { checkColumn(col); return true; }
        return false;
    }

    private boolean isConfirmAction(int id, KeyEvent e) {
        return id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_GO ||
               (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN);
    }

    private void openField(char col, int idx) {
        if (isFinalSolved) return;
        int colIdx = colToIdx(col);
        if (openedFields[colIdx][idx] || isColSolved(col)) return;

        openedFields[colIdx][idx] = true;
        MaterialButton btn = getBtns(col)[idx];
        btn.setText(getColData(col)[idx]);
        getET(col).requestFocus();
    }

    private void checkColumn(char col) {
        if (isColSolved(col)) return;
        EditText et = getET(col);
        String input = et.getText().toString().trim();
        if (input.equalsIgnoreCase(getCorrectRes(col))) {
            hideKeyboard();
            et.setBackgroundResource(R.drawable.input_success_bg);
            et.setTextColor(Color.parseColor("#2E7D32"));
            solveColumn(col, true);
        } else if (!input.isEmpty()) {
            handleWrongInput(et);
        }
    }

    private void handleWrongInput(EditText et) {
        et.setBackgroundResource(R.drawable.input_error_bg);
        et.setTextColor(Color.parseColor("#C62828"));
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        et.startAnimation(shake);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            et.setText("");
            et.setBackgroundResource(R.drawable.input_bg_rounded);
            et.setTextColor(Color.parseColor("#6A1B9A")); // Match layout
        }, 1000);
    }

    private void solveColumn(char col, boolean isManual) {
        if (isColSolved(col)) return;
        setColSolved(col, true);

        int unopenedInCol = 0;
        MaterialButton[] btns = getBtns(col);
        String[] data = getColData(col);
        int colIdx = colToIdx(col);

        for (int i = 0; i < 4; i++) {
            if (!openedFields[colIdx][i]) {
                unopenedInCol++;
                openedFields[colIdx][i] = true;
                btns[i].setText(data[i]);
            }
        }

        EditText et = getET(col);
        et.setText(getCorrectRes(col).toUpperCase());
        et.setEnabled(false);
        et.setBackgroundResource(R.drawable.input_success_bg);

        if (isManual) {
            roundPoints += (2 + unopenedInCol);
            updateScoreUI();
        }
    }

    private void checkFinal() {
        if (isFinalSolved) return;
        String input = etKonacnoResenje.getText().toString().trim();
        if (input.equalsIgnoreCase(currentAsocijacija.getKonacnoResenje())) {
            hideKeyboard();
            etKonacnoResenje.setBackgroundResource(R.drawable.final_success_bg);
            calculateFinalPoints();
            finishRound();
        } else if (!input.isEmpty()) {
            handleWrongFinalInput();
        }
    }

    private void handleWrongFinalInput() {
        etKonacnoResenje.setBackgroundResource(R.drawable.input_error_bg);
        etKonacnoResenje.setTextColor(Color.parseColor("#C62828"));
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        etKonacnoResenje.startAnimation(shake);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            etKonacnoResenje.setText("");
            etKonacnoResenje.setBackgroundResource(R.drawable.final_resenje_bg);
            etKonacnoResenje.setTextColor(Color.WHITE);
        }, 1000);
    }

    private void calculateFinalPoints() {
        int finalRoundPoints = 7;
        finalRoundPoints += getPointsForColumnAfterFinal('A');
        finalRoundPoints += getPointsForColumnAfterFinal('B');
        finalRoundPoints += getPointsForColumnAfterFinal('V');
        finalRoundPoints += getPointsForColumnAfterFinal('G');
        roundPoints += finalRoundPoints;
        updateScoreUI();
    }

    private int getPointsForColumnAfterFinal(char col) {
        if (isColSolved(col)) return 0;
        int colIdx = colToIdx(col);
        int openedCount = 0;
        for (int i = 0; i < 4; i++) {
            if (openedFields[colIdx][i]) openedCount++;
        }
        return 2 + (4 - openedCount);
    }

    private void finishRound() {
        if (countDownTimer != null) countDownTimer.cancel();
        isFinalSolved = true;
        etKonacnoResenje.setText(currentAsocijacija.getKonacnoResenje().toUpperCase());
        etKonacnoResenje.setEnabled(false);
        etKonacnoResenje.setBackgroundResource(R.drawable.final_success_bg);

        String msg = "Osvojili ste " + totalPoints + " zvezdica u igri Asocijacije!";
        NotificationHelper.sendRealNotification(this, "Kraj Runde " + currentRound, msg, NotificationHelper.CHANNEL_REWARDS);
        
        com.example.slagalica.models.NotificationRepository.addNotification(
            new com.example.slagalica.models.Notification(
                String.valueOf(System.currentTimeMillis()), 
                "Asocijacije - Rezultat", 
                msg, 
                "sada", 
                "rewards", 
                false
            )
        );

        solveColumn('A', false); solveColumn('B', false);
        solveColumn('V', false); solveColumn('G', false);

        btnNextAction.setVisibility(View.VISIBLE);
        if (currentRound == 1) {
            btnNextAction.setText("SLEDEĆA RUNDA");
            tvTimer.setText("🕒 00:00");
        } else {
            btnNextAction.setText("ZAVRŠI IGRU");
            tvTimer.setText("🕒 00:00");
        }
    }

    private void updateScoreUI() {
        totalPoints = roundPoints;
        tvPoints.setText(String.format(Locale.getDefault(), "⭐ %d", totalPoints));
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void loadRoundData(int r) {
        isA_Solved = isB_Solved = isV_Solved = isG_Solved = isFinalSolved = false;
        for(int i=0; i<4; i++) for(int j=0; j<4; j++) openedFields[i][j] = false;
        resetUI();

        if (r == 1) {
            currentAsocijacija = new AsocijacijaModel(
                new String[]{"PAPIR", "OLOVKA", "ŠKOLA", "ĐAK"}, "KNJIGA",
                new String[]{"GLUMAC", "SCENA", "DRAMA", "MASKA"}, "POZORIŠTE",
                new String[]{"KLAVIR", "NOTA", "PESMA", "PEVAČ"}, "MUZIKA",
                new String[]{"SLIKA", "MUZEJ", "BOJA", "ČETKICA"}, "UMETNOST", "KULTURA"
            );
        } else {
            currentAsocijacija = new AsocijacijaModel(
                new String[]{"KRALJ", "KRALJICA", "DVOR", "KRUNA"}, "MONARHIJA",
                new String[]{"TOP", "LOVAC", "PEŠAK", "KRALJ"}, "ŠAH",
                new String[]{"GLAVA", "TELO", "RUKE", "NOGE"}, "ČOVEK",
                new String[]{"ZIMA", "SNEG", "LED", "MRAZ"}, "HLADNOĆA", "DRŽAVA"
            );
        }
    }

    private void resetUI() {
        etKonacnoResenje.setEnabled(true); etKonacnoResenje.setText("");
        etKonacnoResenje.setBackgroundResource(R.drawable.final_resenje_bg);
        etKonacnoResenje.setTextColor(Color.WHITE);

        EditText[] ets = {etResenjeA, etResenjeB, etResenjeV, etResenjeG};
        for(EditText e : ets) { 
            e.setEnabled(true); e.setText(""); 
            e.setBackgroundResource(R.drawable.input_bg_rounded);
            e.setTextColor(Color.parseColor("#6A1B9A"));
        }
        
        char[] cols = {'A', 'B', 'V', 'G'};
        for(char c : cols) {
            MaterialButton[] btns = getBtns(c);
            for(int i=0; i<4; i++) btns[i].setText(c + "" + (i+1));
        }
    }

    private void startTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(120000, 1000) {
            public void onTick(long ms) {
                tvTimer.setText(String.format(Locale.getDefault(), "🕒 %02d:%02d", (ms/1000)/60, (ms/1000)%60));
            }
            public void onFinish() { finishRound(); }
        }.start();
    }

    private int colToIdx(char c) {
        if(c=='A') return 0; if(c=='B') return 1; if(c=='V') return 2; return 3;
    }
    private EditText getET(char c) {
        if(c=='A') return etResenjeA; if(c=='B') return etResenjeB;
        if(c=='V') return etResenjeV; return etResenjeG;
    }
    private String getCorrectRes(char c) {
        if(c=='A') return currentAsocijacija.getResenjeA(); if(c=='B') return currentAsocijacija.getResenjeB();
        if(c=='V') return currentAsocijacija.getResenjeV(); return currentAsocijacija.getResenjeG();
    }
    private MaterialButton[] getBtns(char c) {
        if(c=='A') return buttonsA; if(c=='B') return buttonsB;
        if(c=='V') return buttonsV; return buttonsG;
    }
    private String[] getColData(char c) {
        if(c=='A') return currentAsocijacija.getKolonaA(); if(c=='B') return currentAsocijacija.getKolonaB();
        if(c=='V') return currentAsocijacija.getKolonaV(); return currentAsocijacija.getKolonaG();
    }
    private boolean isColSolved(char c) {
        if(c=='A') return isA_Solved; if(c=='B') return isB_Solved;
        if(c=='V') return isV_Solved; return isG_Solved;
    }
    private void setColSolved(char c, boolean v) {
        if(c=='A') isA_Solved = v; else if(c=='B') isB_Solved = v;
        else if(c=='V') isV_Solved = v; else isG_Solved = v;
    }
}
