package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.EditText;
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
 *
 * Pravila bodovanja kopirana iz specifikacije, pojednostavljena za solo mod
 * (nema "brži igrač", nema "ukradi poen" mehanika koje zahtevaju protivnika).
 */
public class ChallengeGameActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String challengeId;
    private String currentUserId;
    private long totalScore = 0;

    // ====== Zajednički UI ======
    private TextView tvGameTitle, tvTimer, tvScore;
    private LinearLayout panelKoZnaZna, panelSpojnice, panelAsocijacije,
            panelSkocko, panelKorakPoKorak, panelMojBroj, panelLoading;

    private CountDownTimer activeTimer;

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
    // 1. KO ZNA ZNA — Spec igra 1: 5 pitanja, 5s svako, +10 tačno / -5 netačno
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
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF823FAB));
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
                // Spec 3g: timeout = 0 bodova za to pitanje
                tvTimer.setText("⏱ 0s");
                kzzIndex++;
                displayKzzQuestion();
            }
        }.start();
    }

    private void answerKzz(int selected, Map<String, Object> q) {
        if (activeTimer != null) activeTimer.cancel();
        Long correctL = (Long) q.get("correctAnswerIndex");
        int correct = correctL != null ? correctL.intValue() : -1;

        if (selected == correct) {
            totalScore += 10;
            updateScoreDisplay();
        } else {
            totalScore = Math.max(0, totalScore - 5);
            updateScoreDisplay();
        }
        kzzIndex++;
        new android.os.Handler(getMainLooper()).postDelayed(this::displayKzzQuestion, 400);
    }

    // =========================================================================================
    // 2. SPOJNICE — Spec igra 2: 5 pojmova, 2 boda po pojmu, max 10 bodova (1 runda solo)
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
            leftBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD5C4E0));

            MaterialButton rightBtn = findViewById(rightIds[i]);
            rightBtn.setText(spojnica.right.get(i));
            rightBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD5C4E0));
            rightBtn.setEnabled(true);
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
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF823FAB));
                btn.setTextColor(0xFFFFFFFF);
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
            rightBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
            rightBtn.setEnabled(false);
            leftBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
            spIndex++;
            if (spIndex >= 5) {
                if (activeTimer != null) activeTimer.cancel();
                new android.os.Handler(getMainLooper()).postDelayed(this::startAsocijacije, 600);
            } else {
                markSpojniceCurrentLeft();
            }
        } else {
            rightBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFC62828));
            new android.os.Handler(getMainLooper()).postDelayed(() ->
                    rightBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD5C4E0)), 400);
        }
    }

    // =========================================================================================
    // 3. ASOCIJACIJE — Spec igra 3: 4 kolone x 4 polja, bodovanje po specifikaciji (1 runda solo)
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
        String[][] kolData = {asocKolA, asocKolB, asocKolV, asocKolG};
        char[] labels = {'A', 'B', 'V', 'G'};

        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                final int c = col, r = row;
                MaterialButton btn = findViewById(btnIds[col][row]);
                btn.setText(labels[col] + "" + (row + 1));
                btn.setEnabled(true);
                btn.setOnClickListener(v -> openAsocField(c, r));
            }
        }

        int[] etIds = {R.id.etAsResA, R.id.etAsResB, R.id.etAsResV, R.id.etAsResG};
        String[] correct = {asocResA, asocResB, asocResV, asocResG};
        for (int col = 0; col < 4; col++) {
            final int c = col;
            EditText et = findViewById(etIds[col]);
            et.setText("");
            et.setEnabled(true);
            et.setOnEditorActionListener((v, actionId, event) -> {
                checkAsocColumn(c, et.getText().toString().trim());
                return true;
            });
        }

        EditText etFinal = findViewById(R.id.etAsResFinal);
        etFinal.setText("");
        etFinal.setEnabled(true);
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
        btn.setEnabled(false);
    }

    private void checkAsocColumn(int col, String input) {
        if (asocSolved[col] || asocFinalSolved) return;
        String[] correct = {asocResA, asocResB, asocResV, asocResG};
        int[] etIds = {R.id.etAsResA, R.id.etAsResB, R.id.etAsResV, R.id.etAsResG};

        if (input.equalsIgnoreCase(correct[col])) {
            int unopened = 0;
            for (int i = 0; i < 4; i++) if (!asocOpened[col][i]) unopened++;
            // Spec 3f: 2 boda + 1 bod za svako neotvoreno polje
            totalScore += 2 + unopened;
            updateScoreDisplay();
            asocSolved[col] = true;
            EditText et = findViewById(etIds[col]);
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
            // Spec 3g: 7 bodova + 6 bodova za svaku neotvorenu kolonu (nerešenu) +
            // dobijeni bodovi za otvorene/delimično otvorene kolone
            int points = 7;
            String[] correct = {asocResA, asocResB, asocResV, asocResG};
            for (int col = 0; col < 4; col++) {
                if (asocSolved[col]) continue; // već nagrađeno ranije
                int opened = 0;
                for (int i = 0; i < 4; i++) if (asocOpened[col][i]) opened++;
                if (opened == 0) {
                    points += 6;
                } else {
                    points += 2 + (4 - opened);
                }
            }
            totalScore += points;
            updateScoreDisplay();
            asocFinalSolved = true;
            if (activeTimer != null) activeTimer.cancel();

            EditText etFinal = findViewById(R.id.etAsResFinal);
            etFinal.setEnabled(false);
            etFinal.setBackgroundResource(R.drawable.input_success_bg);

            new android.os.Handler(getMainLooper()).postDelayed(this::startSkocko, 1500);
        } else {
            EditText etFinal = findViewById(R.id.etAsResFinal);
            etFinal.setText("");
            Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================================
    // 4. SKOČKO — Spec igra 4: pogodi kombinaciju 4 znaka u 6 pokušaja (1 runda solo)
    // =========================================================================================

    private final int[] skTarget = new int[4];
    private final List<int[]> skAttempts = new ArrayList<>();
    private final int[] skCurrentInput = new int[]{-1, -1, -1, -1};
    private int skInputIndex = 0;
    private static final int[] SK_DRAWABLES_FALLBACK = {0, 1, 2, 3, 4, 5};

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

    private final String[] skSymbolNames = {"⭐", "■", "●", "♥", "▲", "✦"};

    private void setupSkockoUI() {
        int[] symbolBtnIds = {R.id.btnSkSym1, R.id.btnSkSym2, R.id.btnSkSym3, R.id.btnSkSym4, R.id.btnSkSym5, R.id.btnSkSym6};
        for (int i = 0; i < 6; i++) {
            MaterialButton btn = findViewById(symbolBtnIds[i]);
            btn.setText(skSymbolNames[i]);
            final int symIdx = i;
            btn.setOnClickListener(v -> addSkSymbol(symIdx));
        }

        findViewById(R.id.btnSkConfirm).setOnClickListener(v -> confirmSkAttempt());
        findViewById(R.id.btnSkDelete).setOnClickListener(v -> deleteSkSymbol());

        updateSkCurrentInputUI();
        updateSkAttemptsUI();

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
        TextView tvInput = findViewById(R.id.tvSkCurrentInput);
        StringBuilder sb = new StringBuilder();
        for (int v : skCurrentInput) sb.append(v == -1 ? "_ " : skSymbolNames[v] + " ");
        tvInput.setText(sb.toString());
    }

    private void confirmSkAttempt() {
        for (int v : skCurrentInput) if (v == -1) {
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

        skInputIndex = 0;
        java.util.Arrays.fill(skCurrentInput, -1);
        updateSkCurrentInputUI();
        updateSkAttemptsUI();

        if (correct == 4) {
            int attemptNum = skAttempts.size();
            int points = attemptNum <= 2 ? 20 : (attemptNum <= 4 ? 15 : 10); // Spec 4c
            totalScore += points;
            updateScoreDisplay();
            if (activeTimer != null) activeTimer.cancel();
            Toast.makeText(this, "Pogodio si! +" + points + " bodova", Toast.LENGTH_SHORT).show();
            new android.os.Handler(getMainLooper()).postDelayed(this::startKorakPoKorak, 1200);
        } else if (skAttempts.size() >= 6) {
            if (activeTimer != null) activeTimer.cancel();
            Toast.makeText(this, "Nisi pogodio kombinaciju.", Toast.LENGTH_SHORT).show();
            new android.os.Handler(getMainLooper()).postDelayed(this::startKorakPoKorak, 1200);
        }
    }

    private void updateSkAttemptsUI() {
        TextView tvAttempts = findViewById(R.id.tvSkAttemptsHistory);
        StringBuilder sb = new StringBuilder();
        for (int[] a : skAttempts) {
            for (int v : a) sb.append(skSymbolNames[v]).append(" ");
            sb.append("\n");
        }
        tvAttempts.setText(sb.toString());
    }

    // =========================================================================================
    // 5. KORAK PO KORAK — Spec igra 5: max 7 koraka, 10s svaki, opadajući bodovi (1 runda solo)
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
        TextView stepView = new TextView(this);
        stepView.setText((kpkSteps.size() - kpkStep) + ". " + kpkSteps.get(kpkStep));
        stepView.setTextSize(15);
        stepView.setPadding(8, 8, 8, 8);
        llSteps.addView(stepView, 0);

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
    // 6. MOJ BROJ — Spec igra 6: pogodi target koristeći 6 brojeva i 4 operacije (1 runda solo)
    // =========================================================================================

    private int mbTarget = -1;
    private final List<Integer> mbNumbers = new ArrayList<>();
    private StringBuilder mbExpression = new StringBuilder();
    private final List<Integer> mbUsedIndices = new ArrayList<>();

    private void startMojBroj() {
        showPanel(panelLoading);
        tvGameTitle.setText("Moj broj");

        Random r = new Random();
        mbTarget = r.nextInt(900) + 100;
        mbNumbers.clear();
        for (int i = 0; i < 4; i++) mbNumbers.add(r.nextInt(9) + 1);
        mbNumbers.add(new int[]{10, 15, 20}[r.nextInt(3)]);
        mbNumbers.add(new int[]{25, 50, 75, 100}[r.nextInt(4)]);

        mbExpression = new StringBuilder();
        mbUsedIndices.clear();

        showPanel(panelMojBroj);
        setupMojBrojUI();
    }

    private void setupMojBrojUI() {
        TextView tvTarget = findViewById(R.id.tvMbTarget);
        tvTarget.setText(String.valueOf(mbTarget));

        LinearLayout llNumbers = findViewById(R.id.llMbNumbers);
        llNumbers.removeAllViews();
        for (int i = 0; i < mbNumbers.size(); i++) {
            final int idx = i;
            MaterialButton btn = new MaterialButton(this);
            btn.setText(String.valueOf(mbNumbers.get(i)));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 120, 1f);
            p.setMargins(3, 3, 3, 3);
            btn.setLayoutParams(p);
            btn.setOnClickListener(v -> {
                if (!mbUsedIndices.contains(idx)) {
                    mbUsedIndices.add(idx);
                    mbExpression.append(mbNumbers.get(idx));
                    updateMbExpression();
                    btn.setEnabled(false);
                }
            });
            llNumbers.addView(btn);
        }

        findViewById(R.id.btnMbPlus).setOnClickListener(v -> mbExpression.append("+"));
        findViewById(R.id.btnMbMinus).setOnClickListener(v -> mbExpression.append("-"));
        findViewById(R.id.btnMbMultiply).setOnClickListener(v -> mbExpression.append("*"));
        findViewById(R.id.btnMbDivide).setOnClickListener(v -> mbExpression.append("/"));
        findViewById(R.id.btnMbClear).setOnClickListener(v -> {
            mbExpression = new StringBuilder();
            mbUsedIndices.clear();
            updateMbExpression();
            setupMojBrojUI();
        });
        findViewById(R.id.btnMbConfirm).setOnClickListener(v -> confirmMbAnswer());
        updateMbExpression();

        if (activeTimer != null) activeTimer.cancel();
        activeTimer = new CountDownTimer(60000, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText(String.valueOf(ms / 1000)); }
            @Override public void onFinish() { confirmMbAnswer(); }
        }.start();
    }

    private void updateMbExpression() {
        TextView tvExpr = findViewById(R.id.tvMbExpression);
        tvExpr.setText(mbExpression.toString());
    }

    private void confirmMbAnswer() {
        if (activeTimer != null) activeTimer.cancel();
        int result = -1;
        try {
            result = evaluateExpression(mbExpression.toString());
        } catch (Exception ignored) {}

        // Spec 6g: 10 bodova ako se pogodi traženi broj; solo mod nema poređenje sa
        // protivnikom, pa ovde dajemo i 5 bodova ako je rezultat dovoljno blizu (≤5 razlike)
        if (result == mbTarget) {
            totalScore += 10;
        } else if (result >= 0 && Math.abs(result - mbTarget) <= 5) {
            totalScore += 5;
        }
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
    }
}