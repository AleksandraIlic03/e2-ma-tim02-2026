package com.example.slagalica;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Spec 9d: Samostalna (solo) partija za Izazov.
 * Igrač prolazi kroz svih 6 igara jednom (bez protivnika uživo), bodovi se
 * sabiraju i na kraju upisuju u ChallengeManager.submitScore.
 * Redosled: Ko zna zna -> Spojnice -> Asocijacije -> Skočko -> Korak po korak -> Moj broj.
 */
public class ChallengeGameActivity extends AppCompatActivity implements SensorEventListener {

    private FirebaseFirestore db;
    private String challengeId;
    private String currentUserId;
    private long totalScore = 0;

    // ====== Zajednički UI ======
    private TextView tvGameTitle, tvTimer, tvScore;
    private LinearLayout panelKoZnaZna, panelSpojnice, panelAsocijacije,
            panelSkocko, panelKorakPoKorak, panelMojBroj, panelLoading;

    private CountDownTimer activeTimer;
    private CountDownTimer revealTimer;

    // Shake sensor fields
    private SensorManager sensorManager;
    private float acceleration;
    private float currentAcceleration;
    private float lastAcceleration;
    private static final int SHAKE_THRESHOLD = 12;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_game);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();
        challengeId = getIntent().getStringExtra("challengeId");

        if (currentUserId == null || challengeId == null) {
            Toast.makeText(this, "Greška pri pokretanju izazova.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acceleration = 0f;
        currentAcceleration = SensorManager.GRAVITY_EARTH;
        lastAcceleration = SensorManager.GRAVITY_EARTH;

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmation();
            }
        });

        initViews();
        startKoZnaZna();
    }

    private void initViews() {
        tvGameTitle = findViewById(R.id.tvChallengeGameTitle);
        tvTimer = findViewById(R.id.tvChallengeTimer);
        tvScore = findViewById(R.id.tvChallengeScore);

        panelKoZnaZna = findViewById(R.id.panelKoZnaZna);
        panelSpojnice = findViewById(R.id.panelSpojnice);
        panelAsocijacije = findViewById(R.id.panelAsocijacije);
        panelSkocko = findViewById(R.id.panelSkocko);
        panelKorakPoKorak = findViewById(R.id.panelKorakPoKorak);
        panelMojBroj = findViewById(R.id.panelMojBroj);
        panelLoading = findViewById(R.id.panelLoading);
    }

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Napuštanje izazova")
                .setMessage("Ako sada izađeš, tvoj rezultat u izazovu ostaje na trenutnom broju bodova. Da li si siguran?")
                .setPositiveButton("Da", (d, w) -> {
                    if (activeTimer != null) activeTimer.cancel();
                    if (revealTimer != null) revealTimer.cancel();
                    submitFinalScore();
                })
                .setNegativeButton("Ne", null)
                .show();
    }

    private void showPanel(View visible) {
        panelKoZnaZna.setVisibility(View.GONE);
        panelSpojnice.setVisibility(View.GONE);
        panelAsocijacije.setVisibility(View.GONE);
        panelSkocko.setVisibility(View.GONE);
        panelKorakPoKorak.setVisibility(View.GONE);
        panelMojBroj.setVisibility(View.GONE);
        panelLoading.setVisibility(View.GONE);
        visible.setVisibility(View.VISIBLE);
    }

    private void updateScoreDisplay() {
        tvScore.setText("⭐ " + totalScore);
    }

    // =========================================================================================
    // 1. KO ZNA ZNA — 5 pitanja, 5s svako, +10 tačno / -5 netačno
    // =========================================================================================

    private List<Map<String, Object>> kzzQuestions = new ArrayList<>();
    private int kzzIndex = 0;

    private void startKoZnaZna() {
        showPanel(panelLoading);
        tvGameTitle.setText("Ko zna zna");

        db.collection("ko_zna_zna_questions").get().addOnSuccessListener(snap -> {
            kzzQuestions.clear();
            for (QueryDocumentSnapshot doc : snap) kzzQuestions.add(doc.getData());
            Collections.shuffle(kzzQuestions);
            if (kzzQuestions.size() > 5) kzzQuestions = kzzQuestions.subList(0, 5);
            kzzIndex = 0;
            showPanel(panelKoZnaZna);
            displayKzzQuestion();
        }).addOnFailureListener(e -> startSpojnice());
    }

    private void displayKzzQuestion() {
        if (kzzIndex >= kzzQuestions.size() || kzzQuestions.isEmpty()) {
            startSpojnice();
            return;
        }
        Map<String, Object> q = kzzQuestions.get(kzzIndex);

        TextView tvQ = findViewById(R.id.tvKzzQuestion);
        tvQ.setText((String) q.get("question"));

        List<String> answers = (List<String>) q.get("answers");
        int[] btnIds = {R.id.btnKzzAnswer1, R.id.btnKzzAnswer2, R.id.btnKzzAnswer3, R.id.btnKzzAnswer4};
        for (int i = 0; i < 4; i++) {
            MaterialButton btn = findViewById(btnIds[i]);
            btn.setEnabled(true);
            btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#6A1B9A")));
            if (answers != null && i < answers.size()) {
                final int idx = i;
                btn.setText(answers.get(i));
                btn.setOnClickListener(v -> answerKzz(idx, q));
            }
        }

        if (activeTimer != null) activeTimer.cancel();
        activeTimer = new CountDownTimer(5000, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText("⏱ " + (ms / 1000 + 1) + "s"); }
            @Override public void onFinish() {
                tvTimer.setText("⏱ 0s");
                // Spec 3g: timeout = 0 bodova; pokaži tačan odgovor pre prelaska
                Long correctL = (Long) q.get("correctAnswerIndex");
                int correct = correctL != null ? correctL.intValue() : -1;
                int[] btnIdsTimeout = {R.id.btnKzzAnswer1, R.id.btnKzzAnswer2, R.id.btnKzzAnswer3, R.id.btnKzzAnswer4};
                for (int id : btnIdsTimeout) findViewById(id).setEnabled(false);
                if (correct >= 0 && correct < 4) {
                    MaterialButton correctBtn = findViewById(btnIdsTimeout[correct]);
                    correctBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
                }
                kzzIndex++;
                new android.os.Handler(getMainLooper()).postDelayed(ChallengeGameActivity.this::displayKzzQuestion, 1100);
            }
        }.start();
    }

    private void answerKzz(int selected, Map<String, Object> q) {
        if (activeTimer != null) activeTimer.cancel();
        Long correctL = (Long) q.get("correctAnswerIndex");
        int correct = correctL != null ? correctL.intValue() : -1;

        int[] btnIds = {R.id.btnKzzAnswer1, R.id.btnKzzAnswer2, R.id.btnKzzAnswer3, R.id.btnKzzAnswer4};

        // Onemogući sve odgovore da igrač ne može da klikne ponovo dok traje feedback
        for (int id : btnIds) {
            findViewById(id).setEnabled(false);
        }

        // Obeleži tačan odgovor zelenom bojom uvek, a ako je igrač pogrešio,
        // njegov izbor oboji crveno
        MaterialButton correctBtn = correct >= 0 && correct < 4 ? findViewById(btnIds[correct]) : null;
        if (correctBtn != null) {
            correctBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        }

        boolean isCorrect = selected == correct;
        if (!isCorrect && selected >= 0 && selected < 4) {
            MaterialButton selectedBtn = findViewById(btnIds[selected]);
            selectedBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D81B60")));
        }

        if (isCorrect) {
            totalScore += 10;
            updateScoreDisplay();
        } else {
            totalScore = Math.max(0, totalScore - 5);
            updateScoreDisplay();
        }
        kzzIndex++;
        // Pauza malo duža da se feedback boja stigne videti pre prelaska
        new android.os.Handler(getMainLooper()).postDelayed(this::displayKzzQuestion, 1100);
    }

    // =========================================================================================
    // 2. SPOJNICE — 5 pojmova, 2 boda po pojmu, max 10 bodova
    // =========================================================================================

    private SpojnicaModelLite spojnica;
    private int spIndex = 0;

    private static class SpojnicaModelLite {
        String title;
        List<String> left, right;
        List<Integer> mapping;
    }

    private void startSpojnice() {
        showPanel(panelLoading);
        tvGameTitle.setText("Spojnice");

        db.collection("spojnice").get().addOnSuccessListener(snap -> {
            List<Map<String, Object>> all = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snap) all.add(doc.getData());
            if (all.isEmpty()) { startAsocijacije(); return; }
            Collections.shuffle(all);
            Map<String, Object> data = all.get(0);

            spojnica = new SpojnicaModelLite();
            spojnica.title = (String) data.get("title");
            spojnica.left = (List<String>) data.get("leftSide");
            spojnica.right = new ArrayList<>((List<String>) data.get("rightSide"));
            List<Long> rawMapping = (List<Long>) data.get("correctMapping");
            spojnica.mapping = new ArrayList<>();
            if (rawMapping != null) for (Long l : rawMapping) spojnica.mapping.add(l.intValue());

            spIndex = 0;
            showPanel(panelSpojnice);
            setupSpojniceUI();
        }).addOnFailureListener(e -> startAsocijacije());
    }

    private void setupSpojniceUI() {
        TextView tvTitle = findViewById(R.id.tvSpojniceTitle);
        tvTitle.setText(spojnica.title);

        int[] leftIds = {R.id.btnSpLeft1, R.id.btnSpLeft2, R.id.btnSpLeft3, R.id.btnSpLeft4, R.id.btnSpLeft5};
        int[] rightIds = {R.id.btnSpRight1, R.id.btnSpRight2, R.id.btnSpRight3, R.id.btnSpRight4, R.id.btnSpRight5};

        for (int i = 0; i < 5; i++) {
            MaterialButton leftBtn = findViewById(leftIds[i]);
            leftBtn.setText(spojnica.left.get(i));
            leftBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D81B60"))); // P1 boja

            MaterialButton rightBtn = findViewById(rightIds[i]);
            rightBtn.setText(spojnica.right.get(i));
            rightBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            rightBtn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#6A1B9A")));
            rightBtn.setStrokeWidth(2);
            rightBtn.setTextColor(Color.parseColor("#321C1C"));
            rightBtn.setEnabled(true);
            rightBtn.setAlpha(1.0f);
            final int rightIndex = i;
            rightBtn.setOnClickListener(v -> onSpojniceRightClick(rightIndex));
        }

        markSpojniceCurrentLeft();

        if (activeTimer != null) activeTimer.cancel();
        activeTimer = new CountDownTimer(60000, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText("⏱ " + (ms / 1000) + "s"); }
            @Override public void onFinish() {
                tvTimer.setText("⏱ 0s");
                startAsocijacije();
            }
        }.start();
    }

    private void markSpojniceCurrentLeft() {
        int[] leftIds = {R.id.btnSpLeft1, R.id.btnSpLeft2, R.id.btnSpLeft3, R.id.btnSpLeft4, R.id.btnSpLeft5};
        for (int i = 0; i < 5; i++) {
            MaterialButton btn = findViewById(leftIds[i]);
            if (i == spIndex) {
                btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#6A1B9A")));
                btn.setTextColor(Color.WHITE);
            }
        }
    }

    private void onSpojniceRightClick(int rightIndex) {
        if (spIndex >= 5) return;
        int correctIndex = spojnica.mapping.get(spIndex);
        int[] rightIds = {R.id.btnSpRight1, R.id.btnSpRight2, R.id.btnSpRight3, R.id.btnSpRight4, R.id.btnSpRight5};
        int[] leftIds = {R.id.btnSpLeft1, R.id.btnSpLeft2, R.id.btnSpLeft3, R.id.btnSpLeft4, R.id.btnSpLeft5};

        MaterialButton rightBtn = findViewById(rightIds[rightIndex]);
        MaterialButton leftBtn = findViewById(leftIds[spIndex]);

        if (rightIndex == correctIndex) {
            totalScore += 2;
            updateScoreDisplay();
            rightBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8BC34A")));
            rightBtn.setEnabled(false);
            leftBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8BC34A")));
            spIndex++;
            if (spIndex >= 5) {
                if (activeTimer != null) activeTimer.cancel();
                new android.os.Handler(getMainLooper()).postDelayed(this::startAsocijacije, 600);
            } else {
                markSpojniceCurrentLeft();
            }
        } else {
            rightBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D81B60")));
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                rightBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                rightBtn.setTextColor(Color.parseColor("#321C1C"));
            }, 400);
        }
    }

    // =========================================================================================
    // 3. ASOCIJACIJE — 4 kolone x 4 polja
    // =========================================================================================

    private String[] asocKolA, asocKolB, asocKolV, asocKolG;
    private String asocResA, asocResB, asocResV, asocResG, asocFinal;
    private boolean[] asocSolved = new boolean[4]; // A,B,V,G
    private boolean[][] asocOpened = new boolean[4][4];
    private boolean asocFinalSolved = false;

    private void startAsocijacije() {
        showPanel(panelLoading);
        tvGameTitle.setText("Asocijacije");

        db.collection("asocijacije").get().addOnSuccessListener(snap -> {
            List<Map<String, Object>> all = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snap) all.add(doc.getData());
            if (all.size() < 1) { startSkocko(); return; }
            Collections.shuffle(all);
            Map<String, Object> data = all.get(0);

            asocKolA = extractStrArray(data.get("kolonaA"));
            asocKolB = extractStrArray(data.get("kolonaB"));
            asocKolV = extractStrArray(data.get("kolonaV"));
            asocKolG = extractStrArray(data.get("kolonaG"));
            asocResA = (String) data.get("resenjeA");
            asocResB = (String) data.get("resenjeB");
            asocResV = (String) data.get("resenjeV");
            asocResG = (String) data.get("resenjeG");
            asocFinal = (String) data.get("konacnoResenje");

            asocSolved = new boolean[4];
            asocOpened = new boolean[4][4];
            asocFinalSolved = false;

            showPanel(panelAsocijacije);
            setupAsocijacijeUI();
        }).addOnFailureListener(e -> startSkocko());
    }

    private String[] extractStrArray(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            String[] result = new String[list.size()];
            for (int i = 0; i < list.size(); i++) result[i] = list.get(i) != null ? list.get(i).toString() : "";
            return result;
        }
        return new String[]{"", "", "", ""};
    }

    private void setupAsocijacijeUI() {
        int[][] btnIds = {
                {R.id.btnAsA1, R.id.btnAsA2, R.id.btnAsA3, R.id.btnAsA4},
                {R.id.btnAsB1, R.id.btnAsB2, R.id.btnAsB3, R.id.btnAsB4},
                {R.id.btnAsV1, R.id.btnAsV2, R.id.btnAsV3, R.id.btnAsV4},
                {R.id.btnAsG1, R.id.btnAsG2, R.id.btnAsG3, R.id.btnAsG4}
        };
        char[] labels = {'A', 'B', 'V', 'G'};

        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                final int col = c, row = r;
                MaterialButton btn = findViewById(btnIds[c][r]);
                btn.setText(labels[c] + "" + (r + 1));
                btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#6A1B9A")));
                btn.setTextColor(Color.WHITE);
                btn.setEnabled(true);
                btn.setOnClickListener(v -> openAsocField(col, row));
            }
        }

        int[] etIds = {R.id.etAsResA, R.id.etAsResB, R.id.etAsResV, R.id.etAsResG};
        for (int col = 0; col < 4; col++) {
            final int c = col;
            EditText et = findViewById(etIds[col]);
            et.setText("");
            et.setEnabled(true);
            et.setBackgroundResource(R.drawable.input_bg_rounded);
            et.setOnEditorActionListener((v, actionId, event) -> {
                checkAsocColumn(c, et.getText().toString().trim());
                return true;
            });
        }

        EditText etFinal = findViewById(R.id.etAsResFinal);
        etFinal.setText("");
        etFinal.setEnabled(true);
        etFinal.setBackgroundResource(R.drawable.final_resenje_bg);
        etFinal.setOnEditorActionListener((v, actionId, event) -> {
            checkAsocFinal(etFinal.getText().toString().trim());
            return true;
        });

        if (activeTimer != null) activeTimer.cancel();
        activeTimer = new CountDownTimer(120000, 1000) {
            @Override public void onTick(long ms) {
                tvTimer.setText(String.format(java.util.Locale.getDefault(), "🕒 %02d:%02d", (ms / 1000) / 60, (ms / 1000) % 60));
            }
            @Override public void onFinish() {
                tvTimer.setText("🕒 00:00");
                startSkocko();
            }
        }.start();
    }

    private void openAsocField(int col, int row) {
        if (asocFinalSolved || asocOpened[col][row]) return;
        asocOpened[col][row] = true;
        int[][] btnIds = {
                {R.id.btnAsA1, R.id.btnAsA2, R.id.btnAsA3, R.id.btnAsA4},
                {R.id.btnAsB1, R.id.btnAsB2, R.id.btnAsB3, R.id.btnAsB4},
                {R.id.btnAsV1, R.id.btnAsV2, R.id.btnAsV3, R.id.btnAsV4},
                {R.id.btnAsG1, R.id.btnAsG2, R.id.btnAsG3, R.id.btnAsG4}
        };
        String[][] kolData = {asocKolA, asocKolB, asocKolV, asocKolG};
        MaterialButton btn = findViewById(btnIds[col][row]);
        btn.setText(kolData[col][row]);
        btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0D5EE")));
        btn.setTextColor(Color.parseColor("#877777"));
        btn.setEnabled(false);
    }

    private void checkAsocColumn(int col, String input) {
        if (asocSolved[col] || asocFinalSolved) return;
        String[] correct = {asocResA, asocResB, asocResV, asocResG};
        int[] etIds = {R.id.etAsResA, R.id.etAsResB, R.id.etAsResV, R.id.etAsResG};

        if (input.equalsIgnoreCase(correct[col])) {
            int unopened = 0;
            for (int i = 0; i < 4; i++) {
                if (!asocOpened[col][i]) {
                    unopened++;
                    openAsocField(col, i);
                }
            }
            totalScore += 2 + unopened;
            updateScoreDisplay();
            asocSolved[col] = true;
            EditText et = findViewById(etIds[col]);
            et.setText(correct[col].toUpperCase());
            et.setEnabled(false);
            et.setBackgroundResource(R.drawable.input_success_bg);
        } else {
            EditText et = findViewById(etIds[col]);
            et.setText("");
            Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkAsocFinal(String input) {
        if (asocFinalSolved) return;
        if (input.equalsIgnoreCase(asocFinal)) {
            int points = 7;
            String[] correct = {asocResA, asocResB, asocResV, asocResG};
            int[] etIds = {R.id.etAsResA, R.id.etAsResB, R.id.etAsResV, R.id.etAsResG};

            for (int col = 0; col < 4; col++) {
                if (!asocSolved[col]) {
                    int opened = 0;
                    for (int i = 0; i < 4; i++) {
                        if (asocOpened[col][i]) opened++;
                        else openAsocField(col, i);
                    }
                    points += (opened == 0) ? 6 : (2 + (4 - opened));
                    asocSolved[col] = true;
                    EditText etCol = findViewById(etIds[col]);
                    etCol.setText(correct[col].toUpperCase());
                    etCol.setEnabled(false);
                    etCol.setBackgroundResource(R.drawable.input_success_bg);
                }
            }

            totalScore += points;
            updateScoreDisplay();
            asocFinalSolved = true;
            if (activeTimer != null) activeTimer.cancel();

            EditText etFinal = findViewById(R.id.etAsResFinal);
            etFinal.setText(asocFinal.toUpperCase());
            etFinal.setEnabled(false);
            etFinal.setBackgroundResource(R.drawable.input_success_bg);

            new android.os.Handler(getMainLooper()).postDelayed(this::startSkocko, 3000);
        } else {
            EditText etFinal = findViewById(R.id.etAsResFinal);
            etFinal.setText("");
            Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================================
    // 4. SKOČKO — pogodi kombinaciju 4 znakova u 6 pokušaja
    // =========================================================================================

    private final int[] skTarget = new int[4];
    private final List<int[]> skAttempts = new ArrayList<>();
    private final int[] skCurrentInput = new int[]{-1, -1, -1, -1};
    private int skInputIndex = 0;

    private final int[] symbolDrawables = {
            R.drawable.img,
            R.drawable.ic_kvadrat,
            R.drawable.ic_krug,
            R.drawable.ic_srce,
            R.drawable.ic_trougao,
            R.drawable.ic_zvezda
    };

    private void startSkocko() {
        showPanel(panelLoading);
        tvGameTitle.setText("Skočko");

        Random r = new Random();
        for (int i = 0; i < 4; i++) skTarget[i] = r.nextInt(6);
        skAttempts.clear();
        skInputIndex = 0;
        java.util.Arrays.fill(skCurrentInput, -1);

        showPanel(panelSkocko);
        setupSkockoUI();
    }

    private void setupSkockoUI() {
        int[] symbolBtnIds = {R.id.btnSkSym1, R.id.btnSkSym2, R.id.btnSkSym3, R.id.btnSkSym4, R.id.btnSkSym5, R.id.btnSkSym6};
        for (int i = 0; i < 6; i++) {
            MaterialButton btn = findViewById(symbolBtnIds[i]);
            btn.setIcon(androidx.core.content.res.ResourcesCompat.getDrawable(getResources(), symbolDrawables[i], null));
            btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            btn.setPadding(0,0,0,0);
            btn.setIconTint(null);
            final int symIdx = i;
            btn.setOnClickListener(v -> addSkSymbol(symIdx));
        }

        findViewById(R.id.btnSkConfirm).setOnClickListener(v -> confirmSkAttempt());
        findViewById(R.id.btnSkDelete).setOnClickListener(v -> deleteSkSymbol());

        // Reset all rows
        int[] rowIds = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6};
        for(int id : rowIds) {
            View row = findViewById(id);
            LinearLayout container = row.findViewById(R.id.containerSymbols);
            GridLayout hints = row.findViewById(R.id.gridHints);
            for(int i=0; i<4; i++) {
                ((ImageView)container.getChildAt(i)).setImageDrawable(null);
                ((ImageView)container.getChildAt(i)).setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EDE0F5")));
                ((ImageView)hints.getChildAt(i)).setImageTintList(ColorStateList.valueOf(Color.parseColor("#D5C4E0")));
            }
        }

        updateSkCurrentInputUI();

        if (activeTimer != null) activeTimer.cancel();
        activeTimer = new CountDownTimer(30000, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText("⏱ " + (ms / 1000) + "s"); }
            @Override public void onFinish() {
                tvTimer.setText("⏱ 0s");
                startKorakPoKorak();
            }
        }.start();
    }

    private void addSkSymbol(int symbol) {
        if (skInputIndex >= 4) return;
        skCurrentInput[skInputIndex] = symbol;
        skInputIndex++;
        updateSkCurrentInputUI();
    }

    private void deleteSkSymbol() {
        if (skInputIndex <= 0) return;
        skInputIndex--;
        skCurrentInput[skInputIndex] = -1;
        updateSkCurrentInputUI();
    }

    private void updateSkCurrentInputUI() {
        int rowIdx = Math.min(skAttempts.size(), 5);
        int[] rowIds = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6};
        View row = findViewById(rowIds[rowIdx]);
        LinearLayout symbolLayout = row.findViewById(R.id.containerSymbols);

        for (int i = 0; i < 4; i++) {
            ImageView iv = (ImageView) symbolLayout.getChildAt(i);
            if (skCurrentInput[i] != -1) {
                iv.setImageResource(symbolDrawables[skCurrentInput[i]]);
                iv.setBackgroundTintList(null);
                iv.setPadding(8, 8, 8, 8);
            } else {
                iv.setImageDrawable(null);
                iv.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EDE0F5")));
            }
        }

        TextView tvInput = findViewById(R.id.tvSkCurrentInput);
        tvInput.setText("Pokušaj " + (skAttempts.size() + 1));
    }

    private void confirmSkAttempt() {
        if (skInputIndex < 4) {
            Toast.makeText(this, "Popuni sva polja!", Toast.LENGTH_SHORT).show();
            return;
        }

        int[] attempt = skCurrentInput.clone();
        skAttempts.add(attempt);

        int correct = 0, wrong = 0;
        boolean[] targetUsed = new boolean[4], attemptUsed = new boolean[4];
        for (int i = 0; i < 4; i++) {
            if (attempt[i] == skTarget[i]) { correct++; targetUsed[i] = true; attemptUsed[i] = true; }
        }
        for (int i = 0; i < 4; i++) {
            if (attemptUsed[i]) continue;
            for (int j = 0; j < 4; j++) {
                if (!targetUsed[j] && attempt[i] == skTarget[j]) { wrong++; targetUsed[j] = true; break; }
            }
        }

        updateHints(skAttempts.size() - 1, correct, wrong);

        skInputIndex = 0;
        java.util.Arrays.fill(skCurrentInput, -1);

        if (correct == 4) {
            int attemptNum = skAttempts.size();
            int points = attemptNum <= 2 ? 20 : (attemptNum <= 4 ? 15 : 10);
            totalScore += points;
            updateScoreDisplay();
            if (activeTimer != null) activeTimer.cancel();
            Toast.makeText(this, "Pogodio si! +" + points + " bodova", Toast.LENGTH_SHORT).show();
            new android.os.Handler(getMainLooper()).postDelayed(this::startKorakPoKorak, 2000);
        } else if (skAttempts.size() >= 6) {
            if (activeTimer != null) activeTimer.cancel();
            Toast.makeText(this, "Nisi pogodio kombinaciju.", Toast.LENGTH_SHORT).show();
            new android.os.Handler(getMainLooper()).postDelayed(this::startKorakPoKorak, 2000);
        } else {
            updateSkCurrentInputUI();
        }
    }

    private void updateHints(int rowIndex, int correctPlace, int wrongPlace) {
        int[] rowIds = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6};
        View row = findViewById(rowIds[rowIndex]);
        GridLayout hintGrid = row.findViewById(R.id.gridHints);

        for (int i = 0; i < 4; i++) {
            ImageView hint = (ImageView) hintGrid.getChildAt(i);
            if (i < correctPlace) {
                hint.setImageTintList(ColorStateList.valueOf(Color.parseColor("#D81B60")));
            } else if (i < correctPlace + wrongPlace) {
                hint.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FF7043")));
            } else {
                hint.setImageTintList(ColorStateList.valueOf(Color.parseColor("#D5C4E0")));
            }
        }
    }

    // =========================================================================================
    // 5. KORAK PO KORAK — max 7 koraka, 10s svaki
    // =========================================================================================

    private final List<String> kpkSteps = new ArrayList<>();
    private String kpkAnswer = "";
    private int kpkStep = 0;
    private static final int[] KPK_POINTS = {20, 18, 16, 14, 12, 10, 7};

    private void startKorakPoKorak() {
        showPanel(panelLoading);
        tvGameTitle.setText("Korak po korak");

        kpkSteps.clear();
        kpkSteps.add("Romul i Rem");
        kpkSteps.add("Sedam brežuljaka");
        kpkSteps.add("Vatikan");
        kpkSteps.add("Koloseum");
        kpkSteps.add("Pasta i pica");
        kpkSteps.add("Tiber");
        kpkSteps.add("Večni grad");
        kpkAnswer = "Rim";
        kpkStep = 0;

        showPanel(panelKorakPoKorak);
        LinearLayout llSteps = findViewById(R.id.llChallengeSteps);
        llSteps.removeAllViews();

        EditText etAnswer = findViewById(R.id.etKpkAnswer);
        etAnswer.setText("");
        findViewById(R.id.btnKpkGuess).setOnClickListener(v -> checkKpkAnswer());

        revealKpkStep();
    }

    private void revealKpkStep() {
        if (kpkStep >= kpkSteps.size()) {
            startMojBroj();
            return;
        }
        LinearLayout llSteps = findViewById(R.id.llChallengeSteps);

        View card = android.view.LayoutInflater.from(this).inflate(R.layout.item_step_card, llSteps, false);
        TextView tvNum = card.findViewById(R.id.tvStepNumber);
        TextView tvText = card.findViewById(R.id.tvStepText);

        tvNum.setText(String.valueOf(7 - kpkStep));
        tvText.setText(kpkSteps.get(kpkStep));

        int[] colors = {0xFF823FAB, 0xFF9B59B6, 0xFFA873C7, 0xFFB990D4, 0xFFCCADE0, 0xFFD5C4E0, 0xFFE0D5EE};
        int bgColor = colors[Math.min(kpkStep, colors.length - 1)];
        ((androidx.cardview.widget.CardView) card).setCardBackgroundColor(bgColor);

        // Poslednje 3 boje su svetle — koristi taman tekst da ostane čitljiv
        boolean isLightBg = kpkStep >= 4;
        int textColor = isLightBg ? Color.parseColor("#321C1C") : Color.WHITE;
        tvText.setTextColor(textColor);
        tvNum.setTextColor(isLightBg ? Color.parseColor("#823FAB") : Color.parseColor("#823FAB"));

        llSteps.addView(card, 0);

        int points = KPK_POINTS[Math.min(kpkStep, KPK_POINTS.length - 1)];
        TextView tvPts = findViewById(R.id.tvKpkPointsHint);
        tvPts.setText("Pogodi za " + points + " bodova");

        if (activeTimer != null) activeTimer.cancel();
        activeTimer = new CountDownTimer(10000, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText("⏱️ " + (ms / 1000) + "s"); }
            @Override public void onFinish() {
                kpkStep++;
                revealKpkStep();
            }
        }.start();
    }

    private void checkKpkAnswer() {
        EditText etAnswer = findViewById(R.id.etKpkAnswer);
        String userAnswer = etAnswer.getText().toString().trim();
        etAnswer.setText("");

        if (userAnswer.equalsIgnoreCase(kpkAnswer)) {
            if (activeTimer != null) activeTimer.cancel();
            int points = KPK_POINTS[Math.min(kpkStep, KPK_POINTS.length - 1)];
            totalScore += points;
            updateScoreDisplay();
            Toast.makeText(this, "Tačno! +" + points + " bodova", Toast.LENGTH_SHORT).show();
            new android.os.Handler(getMainLooper()).postDelayed(this::startMojBroj, 800);
        } else {
            Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
            if (activeTimer != null) activeTimer.cancel();
            kpkStep++;
            revealKpkStep();
        }
    }

    // =========================================================================================
    // 6. MOJ BROJ — pogodi target koristeći 6 brojeva i 4 operacije
    // =========================================================================================

    private int mbTarget = -1;
    private final List<Integer> mbNumbers = new ArrayList<>();
    private StringBuilder mbExpression = new StringBuilder();
    private final List<Integer> mbUsedIndices = new ArrayList<>();
    private boolean mbStarted = false;

    private void startMojBroj() {
        showPanel(panelLoading);
        tvGameTitle.setText("Moj broj");

        mbNumbers.clear();
        mbExpression = new StringBuilder();
        mbUsedIndices.clear();
        mbStarted = false;

        showPanel(panelMojBroj);
        startRollingAnimation();
    }

    private void startRollingAnimation() {
        MaterialButton btnStop = new MaterialButton(this);
        btnStop.setText("STOP");
        btnStop.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#6A1B9A")));
        btnStop.setOnClickListener(v -> stopRolling());

        LinearLayout ll = (LinearLayout) panelMojBroj.getChildAt(0); // CardView logic
        // Find or add STOP button to layout
        findViewById(R.id.btnMbConfirm).setVisibility(View.GONE);
        MaterialButton btnClear = findViewById(R.id.btnMbClear);
        btnClear.setText("STOP");
        btnClear.setOnClickListener(v -> stopRolling());

        revealTimer = new CountDownTimer(30000, 100) {
            Random r = new Random();
            @Override
            public void onTick(long ms) {
                TextView tvMbT = findViewById(R.id.tvMbTarget);
                tvMbT.setText(String.valueOf(r.nextInt(900) + 100));
            }
            @Override
            public void onFinish() { stopRolling(); }
        }.start();
    }

    private void stopRolling() {
        if (revealTimer != null) revealTimer.cancel();
        mbStarted = true;

        Random r = new Random();
        mbTarget = r.nextInt(900) + 100;
        TextView tvMbTarget = findViewById(R.id.tvMbTarget);
        if (tvMbTarget != null) tvMbTarget.setText(String.valueOf(mbTarget));

        mbNumbers.clear();
        for (int i = 0; i < 4; i++) mbNumbers.add(r.nextInt(9) + 1);
        mbNumbers.add(new int[]{10, 15, 20}[r.nextInt(3)]);
        mbNumbers.add(new int[]{25, 50, 75, 100}[r.nextInt(4)]);

        findViewById(R.id.btnMbConfirm).setVisibility(View.VISIBLE);
        MaterialButton btnClear = findViewById(R.id.btnMbClear);
        if (btnClear != null) {
            btnClear.setText("OČISTI");
            btnClear.setOnClickListener(v -> {
                mbExpression = new StringBuilder();
                mbUsedIndices.clear();
                updateMbExpression();
                setupMojBrojUI();
            });
        }

        setupMojBrojUI();

        if (activeTimer != null) activeTimer.cancel();
        activeTimer = new CountDownTimer(60000, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText(String.valueOf(ms / 1000)); }
            @Override public void onFinish() { confirmMbAnswer(); }
        }.start();
    }

    private void setupMojBrojUI() {
        LinearLayout llNumbers = findViewById(R.id.llMbNumbers);
        llNumbers.removeAllViews();
        for (int i = 0; i < mbNumbers.size(); i++) {
            final int idx = i;
            MaterialButton btn = new MaterialButton(this);
            int val = mbNumbers.get(idx);
            btn.setText(String.valueOf(val));
            btn.setTextColor(Color.WHITE);
            try { btn.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.fredoka_bold)); } catch (Exception ignored) {}

            int color = (val >= 25) ? Color.parseColor("#FF7043") :
                    (val >= 10) ? Color.parseColor("#6A1B9A") :
                            Color.parseColor("#D81B60");

            btn.setBackgroundTintList(ColorStateList.valueOf(color));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 120, 1f);
            p.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(p);
            btn.setCornerRadius(16);
            btn.setOnClickListener(v -> {
                if (!mbUsedIndices.contains(idx)) {
                    mbUsedIndices.add(idx);
                    mbExpression.append(mbNumbers.get(idx));
                    updateMbExpression();
                    btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D5C4E0")));
                    btn.setTextColor(Color.parseColor("#877777"));
                }
            });
            llNumbers.addView(btn);
        }

        findViewById(R.id.btnMbPlus).setOnClickListener(v -> mbExpression.append("+"));
        findViewById(R.id.btnMbMinus).setOnClickListener(v -> mbExpression.append("-"));
        findViewById(R.id.btnMbMultiply).setOnClickListener(v -> mbExpression.append("*"));
        findViewById(R.id.btnMbDivide).setOnClickListener(v -> mbExpression.append("/"));
        findViewById(R.id.btnMbConfirm).setOnClickListener(v -> confirmMbAnswer());
        updateMbExpression();
    }

    private void updateMbExpression() {
        TextView tvExpr = findViewById(R.id.tvMbExpression);
        tvExpr.setText(mbExpression.toString());
    }

    private void confirmMbAnswer() {
        if (activeTimer != null) activeTimer.cancel();
        int result = -1;
        try { result = evaluateExpression(mbExpression.toString()); } catch (Exception ignored) {}

        if (result == mbTarget) totalScore += 10;
        else if (result >= 0 && Math.abs(result - mbTarget) <= 5) totalScore += 5;
        updateScoreDisplay();

        finishChallengeGame();
    }

    private int evaluateExpression(String e) {
        String s = e.trim();
        if (s.isEmpty()) return -1;
        if (s.startsWith("(") && s.endsWith(")")) s = s.substring(1, s.length() - 1);
        int d = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') d++;
            else if (c == '(') d--;
            else if (d == 0 && (c == '+' || c == '-') && i > 0)
                return evaluateExpression(s.substring(0, i)) + (c == '+' ? 1 : -1) * evaluateExpression(s.substring(i + 1));
        }
        d = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') d++;
            else if (c == '(') d--;
            else if (d == 0 && (c == '*' || c == '/') && i > 0) {
                int l = evaluateExpression(s.substring(0, i)), r = evaluateExpression(s.substring(i + 1));
                if (c == '/') return r == 0 ? 0 : l / r;
                return l * r;
            }
        }
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return -1; }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0], y = event.values[1], z = event.values[2];
        lastAcceleration = currentAcceleration;
        currentAcceleration = (float) Math.sqrt(x * x + y * y + z * z);
        float delta = currentAcceleration - lastAcceleration;
        acceleration = acceleration * 0.9f + delta;
        if (acceleration > SHAKE_THRESHOLD && panelMojBroj.getVisibility() == View.VISIBLE && !mbStarted) {
            stopRolling();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }
    @Override protected void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    // =========================================================================================
    // ZAVRŠETAK IZAZOVA
    // =========================================================================================

    private void finishChallengeGame() {
        showPanel(panelLoading);
        tvGameTitle.setText("Izazov završen!");
        submitFinalScore();
    }

    private void submitFinalScore() {
        ChallengeManager.submitScore(challengeId, currentUserId, totalScore,
                new ChallengeManager.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Intent intent = new Intent(ChallengeGameActivity.this, ChallengeResultActivity.class);
                        intent.putExtra("challengeId", challengeId);
                        intent.putExtra("myScore", totalScore);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    }
                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChallengeGameActivity.this, message, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeTimer != null) activeTimer.cancel();
        if (revealTimer != null) revealTimer.cancel();
    }
}