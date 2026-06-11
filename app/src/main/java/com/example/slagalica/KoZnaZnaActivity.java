package com.example.slagalica;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.models.KoZnaZnaQuestion;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KoZnaZnaActivity extends AppCompatActivity {

    private TextView tvTimer, tvQuestion, tvPlayer1Points, tvPlayer1Name;
    private TextView tvAnswer1, tvAnswer2, tvAnswer3, tvAnswer4;
    private ImageButton btnAnswer1, btnAnswer2, btnAnswer3, btnAnswer4;
    private MaterialButton btnNext;
    private LinearLayout llProgress;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private List<KoZnaZnaQuestion> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int player1Points = 0;
    private int initialPoints = 0;
    private CountDownTimer questionTimer;
    private boolean answered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        initViews();
        loadUserData();

        initialPoints = getIntent().getIntExtra("player1Score", 0);
        player1Points = initialPoints;
        tvPlayer1Points.setText(String.valueOf(player1Points));

//        seedQuestions(); // Da se napuni baza tokom pokretanja
        fetchQuestions();

        btnNext.setOnClickListener(v -> {
            if (answered || questionTimer == null) {
                goToNextQuestion();
            } else {
                Toast.makeText(this, "Morate odgovoriti na pitanje ili sačekati da vreme istekne", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initViews() {
        tvTimer = findViewById(R.id.tvTimer);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvPlayer1Points = findViewById(R.id.tvPlayer1Points);
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);

        tvAnswer1 = findViewById(R.id.tvAnswer1);
        tvAnswer2 = findViewById(R.id.tvAnswer2);
        tvAnswer3 = findViewById(R.id.tvAnswer3);
        tvAnswer4 = findViewById(R.id.tvAnswer4);

        btnAnswer1 = findViewById(R.id.btnAnswer1);
        btnAnswer2 = findViewById(R.id.btnAnswer2);
        btnAnswer3 = findViewById(R.id.btnAnswer3);
        btnAnswer4 = findViewById(R.id.btnAnswer4);

        btnNext = findViewById(R.id.btnNext);
        llProgress = findViewById(R.id.llProgress);

        btnAnswer1.setOnClickListener(v -> checkAnswer(0));
        btnAnswer2.setOnClickListener(v -> checkAnswer(1));
        btnAnswer3.setOnClickListener(v -> checkAnswer(2));
        btnAnswer4.setOnClickListener(v -> checkAnswer(3));

        btnNext.setVisibility(View.GONE);
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

    private void fetchQuestions() {
        db.collection("ko_zna_zna_questions")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    questions.clear(); // Očisti listu pre dodavanja
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        KoZnaZnaQuestion question = document.toObject(KoZnaZnaQuestion.class);
                        questions.add(question);
                    }
                    Collections.shuffle(questions);
                    if (questions.size() > 5) {
                        questions = new ArrayList<>(questions.subList(0, 5));
                    }
                    if (!questions.isEmpty()) {
                        displayQuestion();
                    } else {
                        Toast.makeText(this, "Nema pitanja u bazi", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Greška pri učitavanju pitanja", Toast.LENGTH_SHORT).show();
                });
    }

    private void displayQuestion() {
        answered = false;
        btnNext.setVisibility(View.GONE);
        enableAnswerButtons(true);
        resetButtonColors();

        KoZnaZnaQuestion q = questions.get(currentQuestionIndex);
        tvQuestion.setText(q.getQuestion());
        tvAnswer1.setText(q.getAnswers().get(0));
        tvAnswer2.setText(q.getAnswers().get(1));
        tvAnswer3.setText(q.getAnswers().get(2));
        tvAnswer4.setText(q.getAnswers().get(3));

        updateProgressDots();
        startTimer();
    }

    private void startTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
        }
        questionTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText("⏱ " + (millisUntilFinished / 1000 + 1) + "s");
            }

            @Override
            public void onFinish() {
                tvTimer.setText("⏱ 0s");
                if (!answered) {
                    answered = true;
                    showCorrectAnswer();
                    tvTimer.postDelayed(() -> goToNextQuestion(), 1500);
                }
            }
        }.start();
    }

    private void checkAnswer(int selectedIndex) {
        if (answered) return;
        answered = true;
        questionTimer.cancel();
        enableAnswerButtons(false);

        KoZnaZnaQuestion q = questions.get(currentQuestionIndex);
        if (selectedIndex == q.getCorrectAnswerIndex()) {
            player1Points += 10;
            highlightButton(selectedIndex, true);
        } else {
            player1Points -= 5;
            highlightButton(selectedIndex, false);
            showCorrectAnswer();
        }
        tvPlayer1Points.setText(String.valueOf(player1Points));
        tvTimer.postDelayed(() -> goToNextQuestion(), 1500);
    }

    private void showCorrectAnswer() {
        KoZnaZnaQuestion q = questions.get(currentQuestionIndex);
        highlightButton(q.getCorrectAnswerIndex(), true);
    }

    private void highlightButton(int index, boolean correct) {
        ImageButton btn = null;
        switch (index) {
            case 0: btn = btnAnswer1; break;
            case 1: btn = btnAnswer2; break;
            case 2: btn = btnAnswer3; break;
            case 3: btn = btnAnswer4; break;
        }
        if (btn != null) {
            if (correct) {
                btn.setBackgroundResource(R.drawable.input_success_bg);
            } else {
                btn.setBackgroundResource(R.drawable.input_error_bg);
            }
        }
    }

    private void resetButtonColors() {
        btnAnswer1.setBackgroundResource(R.drawable.input_bg_rounded);
        btnAnswer2.setBackgroundResource(R.drawable.input_bg_rounded);
        btnAnswer3.setBackgroundResource(R.drawable.input_bg_rounded);
        btnAnswer4.setBackgroundResource(R.drawable.input_bg_rounded);
    }

    private void enableAnswerButtons(boolean enable) {
        btnAnswer1.setEnabled(enable);
        btnAnswer2.setEnabled(enable);
        btnAnswer3.setEnabled(enable);
        btnAnswer4.setEnabled(enable);
    }

    private void updateProgressDots() {
        for (int i = 0; i < llProgress.getChildCount(); i++) {
            View dot = llProgress.getChildAt(i);
            if (i == currentQuestionIndex) {
                dot.setBackgroundResource(R.drawable.dot_active);
            } else {
                dot.setBackgroundResource(R.drawable.dot_inactive);
            }
        }
    }

    private void goToNextQuestion() {
        currentQuestionIndex++;
        if (currentQuestionIndex < questions.size()) {
            displayQuestion();
        } else {
            finishGame();
        }
    }

    private void finishGame() {
        Toast.makeText(this, "Igra 'Ko zna zna' završena!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SpojniceActivity.class);
        intent.putExtra("player1Name", tvPlayer1Name.getText().toString());
        intent.putExtra("player1Score", player1Points);
        startActivity(intent);
        finish();
    }

    private void seedQuestions() {
        List<KoZnaZnaQuestion> seed = new ArrayList<>();
        seed.add(new KoZnaZnaQuestion("Kada je počeo Prvi svetski rat?", 
                List.of("1912. godine", "1914. godine", "1916. godine", "1918. godine"), 1));
        seed.add(new KoZnaZnaQuestion("Koji je glavni grad Francuske?", 
                List.of("London", "Berlin", "Pariz", "Madrid"), 2));
        seed.add(new KoZnaZnaQuestion("Koja planeta je najbliža Suncu?", 
                List.of("Venera", "Mars", "Merkur", "Zemlja"), 2));
        seed.add(new KoZnaZnaQuestion("Ko je napisao 'Na Drini ćuprija'?", 
                List.of("Meša Selimović", "Ivo Andrić", "Miloš Crnjanski", "Borisav Stanković"), 1));
        seed.add(new KoZnaZnaQuestion("Koliko kontinenata postoji na Zemlji?", 
                List.of("5", "6", "7", "8"), 2));

        for (KoZnaZnaQuestion q : seed) {
            db.collection("ko_zna_zna_questions").add(q);
        }
    }
}
