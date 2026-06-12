package com.example.slagalica;

import android.content.Context;
import android.content.Intent;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AsocijacijeActivity extends AppCompatActivity {

    private AsocijacijaModel currentAsocijacija;
    private int player1Score = 0, player2Score = 0;
    private String player1Name, player2Name;
    private TextView tvTimer, tvPoints, tvPlayer1Name, tvPlayer2Name, tvPlayer1Points, tvPlayer2Points;
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
    
    private FirebaseFirestore db;
    private String roomId;
    private boolean isPlayer1;
    private String currentTurn = "p1";
    private ListenerRegistration gameListener;
    private boolean hasOpenedThisTurn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

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
        btnNextAction = findViewById(R.id.btnNextGame);
        
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);
        tvPlayer1Points = findViewById(R.id.tvPlayer1Points);
        tvPlayer2Points = findViewById(R.id.tvPlayer2Points);

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

    private void attachGameListener() {
        gameListener = db.collection("gameRooms").document(roomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) return;

                    String status = snapshot.getString("status");
                    android.util.Log.d("Asocijacije", "Room status: " + status);
                    if ("koznazna".equals(status)) {
                        navigateToKoZnaZna();
                        return;
                    }

                    Long p1s = snapshot.getLong("player1Score");
                    Long p2s = snapshot.getLong("player2Score");
                    player1Score = (p1s != null) ? p1s.intValue() : 0;
                    player2Score = (p2s != null) ? p2s.intValue() : 0;
                    
                    player1Name = snapshot.getString("player1Name") != null ? snapshot.getString("player1Name") : "Igrač 1";
                    player2Name = snapshot.getString("player2Name") != null ? snapshot.getString("player2Name") : "Igrač 2";
                    
                    if (tvPlayer1Name != null) tvPlayer1Name.setText(player1Name);
                    if (tvPlayer2Name != null) tvPlayer2Name.setText(player2Name);
                    if (tvPlayer1Points != null) tvPlayer1Points.setText(String.valueOf(player1Score));
                    if (tvPlayer2Points != null) tvPlayer2Points.setText(String.valueOf(player2Score));
                    tvPoints.setText("⭐ " + (isPlayer1 ? player1Score : player2Score));

                    currentRound = snapshot.getLong("asoc_currentRound") != null ? snapshot.getLong("asoc_currentRound").intValue() : 1;
                    currentTurn = snapshot.getString("asoc_turn") != null ? snapshot.getString("asoc_turn") : "p1";
                    
                    Map<String, Object> asocData = (Map<String, Object>) snapshot.get("asocijacija" + currentRound);
                    if (asocData != null) {
                        try {
                            String[] kolA = extractList(asocData.get("kolonaA"));
                            String[] kolB = extractList(asocData.get("kolonaB"));
                            String[] kolV = extractList(asocData.get("kolonaV"));
                            String[] kolG = extractList(asocData.get("kolonaG"));
                            
                            currentAsocijacija = new AsocijacijaModel(
                                kolA, (String) asocData.get("resenjeA"),
                                kolB, (String) asocData.get("resenjeB"),
                                kolV, (String) asocData.get("resenjeV"),
                                kolG, (String) asocData.get("resenjeG"),
                                (String) asocData.get("konacnoResenje")
                            );
                        } catch (Exception ex) {
                            android.util.Log.e("Asocijacije", "Greška pri parsiranju modela: " + ex.getMessage());
                        }
                    }

                    syncState(snapshot);
                    
                    if (snapshot.contains("roundStartTime")) {
                        startLocalTimer(snapshot.getLong("roundStartTime"));
                    }
                });
    }

    private void syncState(DocumentSnapshot snap) {
        isA_Solved = snap.getBoolean("asoc_solvedA") != null && snap.getBoolean("asoc_solvedA");
        isB_Solved = snap.getBoolean("asoc_solvedB") != null && snap.getBoolean("asoc_solvedB");
        isV_Solved = snap.getBoolean("asoc_solvedV") != null && snap.getBoolean("asoc_solvedV");
        isG_Solved = snap.getBoolean("asoc_solvedG") != null && snap.getBoolean("asoc_solvedG");
        isFinalSolved = snap.getBoolean("asoc_solvedFinal") != null && snap.getBoolean("asoc_solvedFinal");

        try {
            Map<String, List<Boolean>> opened = (Map<String, List<Boolean>>) snap.get("asoc_opened");
            if (opened != null) {
                for (int i = 0; i < 4; i++) {
                    List<Boolean> row = opened.get(String.valueOf(i));
                    if (row != null && row.size() >= 4) {
                        for (int j = 0; j < 4; j++) {
                            openedFields[i][j] = row.get(j);
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("Asocijacije", "Error syncing opened fields", e);
        }

        updateUI();
    }

    private void updateUI() {
        if (currentAsocijacija == null || etKonacnoResenje == null) {
            android.util.Log.w("Asocijacije", "UI Update skipped: Missing data or views");
            return;
        }
        boolean isMyTurn = currentTurn.equals(isPlayer1 ? "p1" : "p2");
        char[] cols = {'A', 'B', 'V', 'G'};
        for (char col : cols) {
            int colIdx = colToIdx(col);
            MaterialButton[] btns = getBtns(col);
            String[] data = getColData(col);
            boolean solved = isColSolved(col);
            
            if (btns == null || data == null) continue;

            for (int i = 0; i < 4; i++) {
                if (btns[i] == null) continue;
                if (openedFields[colIdx][i] || solved || isFinalSolved) {
                    btns[i].setText(data[i] != null ? data[i] : "");
                    btns[i].setEnabled(false);
                } else {
                    btns[i].setText(col + "" + (i + 1));
                    btns[i].setEnabled(isMyTurn && !hasOpenedThisTurn);
                }
            }
            
            EditText et = getET(col);
            if (et == null) continue;
            
            if (solved || isFinalSolved) {
                String res = getCorrectRes(col);
                et.setText(res != null ? res.toUpperCase() : "");
                et.setEnabled(false);
                et.setBackgroundResource(R.drawable.input_success_bg);
            } else {
                et.setEnabled(isMyTurn);
                if (!et.hasFocus()) et.setText("");
            }
        }

        if (isFinalSolved) {
            String fr = currentAsocijacija.getKonacnoResenje();
            etKonacnoResenje.setText(fr != null ? fr.toUpperCase() : "");
            etKonacnoResenje.setEnabled(false);
            etKonacnoResenje.setBackgroundResource(R.drawable.final_success_bg);
            if (btnNextAction != null) {
                btnNextAction.setVisibility(View.VISIBLE);
                btnNextAction.setText(currentRound == 1 ? "SLEDEĆA RUNDA" : "SLEDEĆA IGRA");
            }
        } else {
            etKonacnoResenje.setEnabled(isMyTurn);
            if (!etKonacnoResenje.hasFocus()) etKonacnoResenje.setText("");
            
            if (btnNextAction != null) {
                if (isMyTurn) {
                    btnNextAction.setVisibility(View.VISIBLE);
                    btnNextAction.setText("DALJE");
                } else {
                    btnNextAction.setVisibility(View.GONE);
                }
            }
        }
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
            if (isFinalSolved) {
                if (currentRound == 1) {
                    startNextRound();
                } else {
                    transitionToKoZnaZna();
                }
            } else {
                // Pass turn
                db.collection("gameRooms").document(roomId).update("asoc_turn", isPlayer1 ? "p2" : "p1");
                hasOpenedThisTurn = false;
            }
        });
    }

    private void startNextRound() {
        if (!isPlayer1) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("asoc_currentRound", 2);
        updates.put("asoc_turn", "p2");
        updates.put("asoc_solvedA", false);
        updates.put("asoc_solvedB", false);
        updates.put("asoc_solvedV", false);
        updates.put("asoc_solvedG", false);
        updates.put("asoc_solvedFinal", false);

        Map<String, Object> asocOpened = new HashMap<>();
        asocOpened.put("0", java.util.Arrays.asList(false, false, false, false));
        asocOpened.put("1", java.util.Arrays.asList(false, false, false, false));
        asocOpened.put("2", java.util.Arrays.asList(false, false, false, false));
        asocOpened.put("3", java.util.Arrays.asList(false, false, false, false));
        updates.put("asoc_opened", asocOpened);

        updates.put("roundStartTime", System.currentTimeMillis());
        db.collection("gameRooms").document(roomId).update(updates);
    }

    private void transitionToKoZnaZna() {
        if (!isPlayer1) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "koznazna");
        updates.put("roundStartTime", System.currentTimeMillis());
        updates.put("questionStartTime", System.currentTimeMillis());
        db.collection("gameRooms").document(roomId).update(updates);
    }

    private void navigateToKoZnaZna() {
        if (gameListener != null) gameListener.remove();
        Intent intent = new Intent(this, KoZnaZnaActivity.class);
        intent.putExtra("roomId", roomId);
        intent.putExtra("isPlayer1", isPlayer1);
        startActivity(intent);
        finish();
    }

    private void openField(char col, int idx) {
        if (!currentTurn.equals(isPlayer1 ? "p1" : "p2")) return;
        if (hasOpenedThisTurn) return;
        
        int colIdx = colToIdx(col);
        if (openedFields[colIdx][idx]) return;

        openedFields[colIdx][idx] = true;
        hasOpenedThisTurn = true;
        
        Map<String, Object> updates = new HashMap<>();
        Map<String, Object> asocOpened = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            asocOpened.put(String.valueOf(i), java.util.Arrays.asList(openedFields[i][0], openedFields[i][1], openedFields[i][2], openedFields[i][3]));
        }
        updates.put("asoc_opened", asocOpened);
        db.collection("gameRooms").document(roomId).update(updates);
        
        Toast.makeText(this, "Možete pogađati ili kliknuti 'DALJE'", Toast.LENGTH_SHORT).show();
    }

    private void checkColumn(char col) {
        String input = getET(col).getText().toString().trim();
        if (input.equalsIgnoreCase(getCorrectRes(col))) {
            int unopened = 0;
            int colIdx = colToIdx(col);
            for (int i = 0; i < 4; i++) if (!openedFields[colIdx][i]) unopened++;
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("asoc_solved" + col, true);
            String scoreField = isPlayer1 ? "player1Score" : "player2Score";
            updates.put(scoreField, (isPlayer1 ? player1Score : player2Score) + 2 + unopened);
            db.collection("gameRooms").document(roomId).update(updates);
            hasOpenedThisTurn = false; // Can keep opening or guessing
        } else {
            handleWrongInput(getET(col));
            db.collection("gameRooms").document(roomId).update("asoc_turn", isPlayer1 ? "p2" : "p1");
            hasOpenedThisTurn = false;
        }
    }

    private void checkFinal() {
        String input = etKonacnoResenje.getText().toString().trim();
        if (input.equalsIgnoreCase(currentAsocijacija.getKonacnoResenje())) {
            int points = 7;
            points += getUnsolvedColPoints('A');
            points += getUnsolvedColPoints('B');
            points += getUnsolvedColPoints('V');
            points += getUnsolvedColPoints('G');
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("asoc_solvedFinal", true);
            updates.put("asoc_solvedA", true);
            updates.put("asoc_solvedB", true);
            updates.put("asoc_solvedV", true);
            updates.put("asoc_solvedG", true);

            String scoreField = isPlayer1 ? "player1Score" : "player2Score";
            updates.put(scoreField, (isPlayer1 ? player1Score : player2Score) + points);
            db.collection("gameRooms").document(roomId).update(updates);
            hasOpenedThisTurn = false;
        } else {
            handleWrongFinalInput();
            db.collection("gameRooms").document(roomId).update("asoc_turn", isPlayer1 ? "p2" : "p1");
            hasOpenedThisTurn = false;
        }
    }

    private int getUnsolvedColPoints(char col) {
        if (isColSolved(col)) return 0;
        int opened = 0;
        int colIdx = colToIdx(col);
        for (int i = 0; i < 4; i++) if (openedFields[colIdx][i]) opened++;
        return 2 + (4 - opened);
    }

    private boolean handleInput(int id, KeyEvent e, char col) {
        if (isConfirmAction(id, e)) { checkColumn(col); return true; }
        return false;
    }

    private boolean isConfirmAction(int id, KeyEvent e) {
        return id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_GO ||
               (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN);
    }

    private void handleWrongInput(EditText et) {
        et.setBackgroundResource(R.drawable.input_error_bg);
        et.setTextColor(Color.parseColor("#C62828"));
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        et.startAnimation(shake);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            et.setText("");
            et.setBackgroundResource(R.drawable.input_bg_rounded);
            et.setTextColor(Color.parseColor("#6A1B9A"));
        }, 1000);
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

    private void startLocalTimer(long startTime) {
        if (countDownTimer != null) countDownTimer.cancel();
        long remaining = 120000 - (System.currentTimeMillis() - startTime);
        if (remaining <= 0) { 
            tvTimer.setText("🕒 00:00"); 
            if (isPlayer1) {
                if (currentRound == 1) {
                    startNextRound();
                } else {
                    transitionToKoZnaZna();
                }
            }
            return; 
        }
        countDownTimer = new CountDownTimer(remaining, 1000) {
            public void onTick(long ms) {
                tvTimer.setText(String.format(Locale.getDefault(), "🕒 %02d:%02d", (ms/1000)/60, (ms/1000)%60));
            }
            public void onFinish() {
                tvTimer.setText("🕒 00:00");
                if (isPlayer1) {
                    if (currentRound == 1) {
                        startNextRound();
                    } else {
                        transitionToKoZnaZna();
                    }
                }
            }
        }.start();
    }

    private int colToIdx(char c) {
        if(c=='A') return 0; if(c=='B') return 1; if(c=='V') return 2; return 3;
    }

    private String[] extractList(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            String[] result = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                result[i] = item != null ? item.toString() : "";
            }
            return result;
        }
        return new String[]{"", "", "", ""};
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameListener != null) gameListener.remove();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
