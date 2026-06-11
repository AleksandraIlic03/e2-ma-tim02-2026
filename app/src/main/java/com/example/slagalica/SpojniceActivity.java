package com.example.slagalica;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.models.SpojnicaModel;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpojniceActivity extends AppCompatActivity {

    private TextView tvPlayer1Name, tvPlayer1Points, tvTimer, tvInstruction;
    private MaterialButton[] leftButtons = new MaterialButton[5];
    private MaterialButton[] rightButtons = new MaterialButton[5];
    private MaterialButton btnNext;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private List<SpojnicaModel> spojnice = new ArrayList<>();
    private SpojnicaModel currentSpojnica;
    private int currentRound = 1;
    private int player1Score = 0;
    private int initialScore = 0;
    private String player1Name;

    private int currentLeftIndex = 0;
    private boolean[] matchedRight = new boolean[5];
    private CountDownTimer timer;
    private boolean isRoundActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        player1Name = getIntent().getStringExtra("player1Name");
        initialScore = getIntent().getIntExtra("player1Score", 0);
        player1Score = initialScore;

        initViews();
        loadUserData();
        seedSpojnice(); // Odkomentarisano za prvo pokretanje da se napuni baza
        fetchSpojnice();

        btnNext.setOnClickListener(v -> {
            if (currentRound == 1) {
                currentRound = 2;
                startRound();
            } else {
                finishGame();
            }
        });
    }

    private void initViews() {
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer1Points = findViewById(R.id.tvPlayer1Points);
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
        tvPlayer1Points.setText(String.valueOf(player1Score));
    }

    private void loadUserData() {
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            tvPlayer1Name.setText(documentSnapshot.getString("username"));
                        }
                    });
        }
    }

    private void fetchSpojnice() {
        db.collection("spojnice")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<SpojnicaModel> allSpojnice = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        allSpojnice.add(document.toObject(SpojnicaModel.class));
                    }
                    
                    if (!allSpojnice.isEmpty()) {
                        // Grupišemo po naslovu (temi)
                        Map<String, List<SpojnicaModel>> grouped = new HashMap<>();
                        for (SpojnicaModel s : allSpojnice) {
                            if (!grouped.containsKey(s.getTitle())) {
                                grouped.put(s.getTitle(), new ArrayList<>());
                            }
                            grouped.get(s.getTitle()).add(s);
                        }
                        
                        // Tražimo teme koje imaju bar 2 seta podataka
                        List<String> validTitles = new ArrayList<>();
                        for (String title : grouped.keySet()) {
                            if (grouped.get(title).size() >= 2) {
                                validTitles.add(title);
                            }
                        }
                        
                        spojnice.clear();
                        if (!validTitles.isEmpty()) {
                            Collections.shuffle(validTitles);
                            String selectedTitle = validTitles.get(0);
                            List<SpojnicaModel> variants = grouped.get(selectedTitle);
                            Collections.shuffle(variants);
                            spojnice.add(variants.get(0));
                            spojnice.add(variants.get(1));
                        } else {
                            // Fallback ako nijedna tema nema 2 seta, uzmi bilo koja dva
                            Collections.shuffle(allSpojnice);
                            spojnice.add(allSpojnice.get(0));
                            if (allSpojnice.size() > 1) {
                                spojnice.add(allSpojnice.get(1));
                            }
                        }
                        
                        startRound();
                    } else {
                        Toast.makeText(this, "Nema spojnica u bazi", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startRound() {
        if (spojnice.isEmpty()) return;
        
        isRoundActive = true;
        currentLeftIndex = 0;
        matchedRight = new boolean[5];
        btnNext.setVisibility(View.INVISIBLE);
        
        currentSpojnica = spojnice.get((currentRound - 1) % spojnice.size());
        tvInstruction.setText(currentSpojnica.getTitle());

        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(currentSpojnica.getLeftSide().get(i));
            leftButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#D5C4E0")));
            leftButtons[i].setTextColor(Color.parseColor("#2D1B4E"));
            
            rightButtons[i].setText(currentSpojnica.getRightSide().get(i));
            rightButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#D5C4E0")));
            rightButtons[i].setTextColor(Color.parseColor("#2D1B4E"));
            rightButtons[i].setEnabled(true);
        }

        highlightCurrentLeft();
        startTimer();
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText("⏱ " + (millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                tvTimer.setText("⏱ 0s");
                endRound();
            }
        }.start();
    }

    private void highlightCurrentLeft() {
        for (int i = 0; i < 5; i++) {
            if (i == currentLeftIndex) {
                leftButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#823FAB"))); // Purple for active
                leftButtons[i].setTextColor(Color.WHITE);
            }
        }
    }

    private void onRightButtonClick(int rightIndex) {
        if (!isRoundActive || matchedRight[rightIndex]) return;

        int correctRightIndexForLeft = currentSpojnica.getCorrectMapping().get(currentLeftIndex);

        if (rightIndex == correctRightIndexForLeft) {
            // Correct match - Purple
            player1Score += 2;
            tvPlayer1Points.setText(String.valueOf(player1Score));
            rightButtons[rightIndex].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#823FAB")));
            rightButtons[rightIndex].setTextColor(Color.WHITE);
            leftButtons[currentLeftIndex].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#823FAB")));
            leftButtons[currentLeftIndex].setTextColor(Color.WHITE);
            matchedRight[rightIndex] = true;
            rightButtons[rightIndex].setEnabled(false);
        } else {
            // Wrong match - Brief Muted Red then back to Light Purple
            rightButtons[rightIndex].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")));
            rightButtons[rightIndex].setTextColor(Color.WHITE);
            leftButtons[currentLeftIndex].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")));
            leftButtons[currentLeftIndex].setTextColor(Color.WHITE);
            
            final int finalRightIdx = rightIndex;
            final int finalLeftIdx = currentLeftIndex;
            rightButtons[rightIndex].postDelayed(() -> {
                if (isRoundActive && !matchedRight[finalRightIdx]) {
                    rightButtons[finalRightIdx].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#D5C4E0")));
                    rightButtons[finalRightIdx].setTextColor(Color.parseColor("#2D1B4E"));
                }
                // Also reset left button if it was wrong
                leftButtons[finalLeftIdx].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#D5C4E0")));
                leftButtons[finalLeftIdx].setTextColor(Color.parseColor("#2D1B4E"));
            }, 500);
        }

        currentLeftIndex++;
        if (currentLeftIndex >= 5) {
            endRound();
        } else {
            highlightCurrentLeft();
        }
    }

    private void endRound() {
        isRoundActive = false;
        if (timer != null) timer.cancel();
        
        // Show all correct matches that were not found in Yellow (#FFB300)
        for (int i = 0; i < 5; i++) {
            int correctIdx = currentSpojnica.getCorrectMapping().get(i);
            if (!matchedRight[correctIdx]) {
                rightButtons[correctIdx].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFB300")));
                rightButtons[correctIdx].setTextColor(Color.WHITE);
                leftButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFB300")));
                leftButtons[i].setTextColor(Color.WHITE);
            }
        }

        btnNext.setVisibility(View.VISIBLE);
        if (currentRound == 1) {
            btnNext.setText("SLEDEĆA RUNDA");
        } else {
            btnNext.setText("ZAVRŠI IGRU");
        }
    }

    private void finishGame() {
        Intent intent = new Intent(this, HomeActivity.class); // Ili na sledeću igru
        intent.putExtra("player1Name", player1Name);
        intent.putExtra("player1Score", player1Score);
        startActivity(intent);
        finish();
    }

    private void seedSpojnice() {
        List<SpojnicaModel> seed = new ArrayList<>();
        // Tema 1 - Pisci - Set 1
        seed.add(new SpojnicaModel("Povežite pisce sa njihovim delima",
                List.of("Ivo Andrić", "Meša Selimović", "Miloš Crnjanski", "Bora Stanković", "Danilo Kiš"),
                List.of("Seobe", "Na Drini ćuprija", "Derviš i smrt", "Grobnica za Borisa Davidoviča", "Nečista krv"),
                List.of(1, 2, 0, 4, 3)));
        
        // Tema 1 - Pisci - Set 2
        seed.add(new SpojnicaModel("Povežite pisce sa njihovim delima",
                List.of("Jovan Dučić", "Aleksa Šantić", "Desanka Maksimović", "Laza Kostić", "Milan Rakić"),
                List.of("Santa Maria della Salute", "Blago cara Radovana", "Iskrena pesma", "Ostajte ovdje", "Tražim pomilovanje"),
                List.of(1, 3, 4, 0, 2)));

        // Tema 2 - Gradovi - Set 1
        seed.add(new SpojnicaModel("Povežite države sa glavnim gradovima",
                List.of("Srbija", "Francuska", "Italija", "Nemačka", "Španija"),
                List.of("Berlin", "Rim", "Madrid", "Beograd", "Pariz"),
                List.of(3, 4, 1, 0, 2)));
        
        // Tema 2 - Gradovi - Set 2
        seed.add(new SpojnicaModel("Povežite države sa glavnim gradovima",
                List.of("Grčka", "Rusija", "Mađarska", "Austrija", "Hrvatska"),
                List.of("Beč", "Budimpešta", "Atina", "Zagreb", "Moskva"),
                List.of(2, 4, 1, 0, 3)));

        for (SpojnicaModel s : seed) {
            db.collection("spojnice").add(s);
        }
    }
}
