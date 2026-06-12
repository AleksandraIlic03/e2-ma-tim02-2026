package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MojBrojActivity extends AppCompatActivity {

    private TextView tvPlayer1Name, tvPlayer1Points, tvPlayer2Name, tvPlayer2Points;
    private TextView tvRound, tvTimer, tvTargetNumber, tvExpression, tvResult;
    private LinearLayout llNumberButtons;

    private FirebaseFirestore db;
    private String roomId;
    private boolean isPlayer1;
    private ListenerRegistration gameListener;

    private int targetNumber = -1;
    private boolean numbersLoaded = false;
    private boolean generateCalled = false;
    private final List<Integer> numbers = new ArrayList<>();
    private StringBuilder expression = new StringBuilder();
    private final List<Integer> usedIndices = new ArrayList<>();
    private boolean transitioning = false;
    private boolean isFinalizingRound = false;
    private boolean myTurnFinished = false;

    private String phase = "";
    private CountDownTimer roundTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        db = FirebaseFirestore.getInstance();
        roomId = getIntent().getStringExtra("roomId");
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);

        initViews();
        attachGameListener();
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

        findViewById(R.id.btnPlus).setOnClickListener(v -> appendOperator("+"));
        findViewById(R.id.btnMinus).setOnClickListener(v -> appendOperator("-"));
        findViewById(R.id.btnMultiply).setOnClickListener(v -> appendOperator("*"));
        findViewById(R.id.btnDivide).setOnClickListener(v -> appendOperator("/"));
        findViewById(R.id.btnOpenParen).setOnClickListener(v -> appendOperator("("));
        findViewById(R.id.btnCloseParen).setOnClickListener(v -> appendOperator(")"));
        findViewById(R.id.btnBackspace).setOnClickListener(v -> backspace());
        findViewById(R.id.btnClear).setOnClickListener(v -> clearExpression());
        findViewById(R.id.btnConfirm).setOnClickListener(v -> confirmAnswer());

        View btnStop = findViewById(R.id.btnStop);
        if (btnStop != null) btnStop.setVisibility(View.GONE);

        enableInput(false);
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

        String currentGame = snapshot.getString("currentGame");
        if ("koZnaZna".equals(currentGame)) {
            transitioning = true;
            if (roundTimer != null) roundTimer.cancel();
            if (gameListener != null) gameListener.remove();
            Intent intent = new Intent(this, KoZnaZnaActivity.class);
            intent.putExtra("roomId", roomId);
            intent.putExtra("isPlayer1", isPlayer1);
            startActivity(intent);
            finish();
            return;
        }

        tvPlayer1Name.setText(snapshot.getString("player1Name"));
        tvPlayer2Name.setText(snapshot.getString("player2Name"));
        tvPlayer1Points.setText(String.valueOf(snapshot.getLong("player1Score") != null ? snapshot.getLong("player1Score") : 0));
        tvPlayer2Points.setText(String.valueOf(snapshot.getLong("player2Score") != null ? snapshot.getLong("player2Score") : 0));

        String snapPhase = snapshot.getString("mojbroj_phase");
        if (snapPhase == null) snapPhase = "p1_playing";

        if (!snapPhase.equals(phase)) {
            phase = snapPhase;
            resetRoundUI();
            if (phase.equals("done")) {
                finishGame();
                return;
            }
            // Prva runda — P1 generiše brojeve
            if (phase.equals("p1_playing") && isPlayer1 && !generateCalled) {
                generateCalled = true;
                generateAndSave();
            }
        }

        if (!numbersLoaded) {
            String prefix = phase.equals("p1_playing") ? "r1" : "r2";
            Long target = snapshot.getLong(prefix + "Target");
            List<Long> nums = (List<Long>) snapshot.get(prefix + "Numbers");

            if (target != null && target > 0 && nums != null && !nums.isEmpty()) {
                numbersLoaded = true;
                targetNumber = target.intValue();
                numbers.clear();
                for (Long n : nums) numbers.add(n.intValue());
                startRoundLocally();
            }
        }

        if (Boolean.TRUE.equals(snapshot.getBoolean("mojbroj_p1Finished"))
                && Boolean.TRUE.equals(snapshot.getBoolean("mojbroj_p2Finished"))) {
            showComparison(snapshot);
            // Samo jedan igrač (P1 ili Host faze) pokreće prelaz nakon pauze
            if (isRoundHost() && !isFinalizingRound) {
                isFinalizingRound = true;
                new android.os.Handler().postDelayed(this::calculateAndMove, 3500);
            }
        }
    }

    private void resetRoundUI() {
        if (roundTimer != null) roundTimer.cancel();
        isFinalizingRound = false;
        myTurnFinished = false;
        targetNumber = -1;
        numbersLoaded = false;
        generateCalled = false;
        numbers.clear();
        expression = new StringBuilder();
        usedIndices.clear();
        tvExpression.setText("");
        tvResult.setText("= ?");
        tvTargetNumber.setText("?");
        tvTimer.setText("60");
        tvRound.setText(phase.equals("p1_playing") ? "Runda 1/2" : "Runda 2/2");
        llNumberButtons.removeAllViews();
        setupPlaceholderButtons();
        enableInput(false);
    }

    private void setupPlaceholderButtons() {
        llNumberButtons.removeAllViews();
        for (int i = 0; i < 6; i++) {
            MaterialButton btn = new MaterialButton(this);
            btn.setText("?");
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9B59B6));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 120, 1f);
            p.setMargins(3, 3, 3, 3);
            btn.setLayoutParams(p);
            btn.setCornerRadius(16);
            llNumberButtons.addView(btn);
        }
    }

    private void generateAndSave() {
        Random r = new Random();
        int t = r.nextInt(900) + 100;
        List<Integer> ns = new ArrayList<>();
        for (int i = 0; i < 4; i++) ns.add(r.nextInt(9) + 1);
        ns.add(new int[]{10, 15, 20}[r.nextInt(3)]);
        ns.add(new int[]{25, 50, 75, 100}[r.nextInt(4)]);

        String pref = phase.equals("p1_playing") ? "r1" : "r2";
        Map<String, Object> u = new HashMap<>();
        u.put(pref + "Target", t);
        u.put(pref + "Numbers", ns);
        u.put("mojbroj_p1Finished", false);
        u.put("mojbroj_p2Finished", false);
        u.put("mojbroj_p1Result", -1);
        u.put("mojbroj_p2Result", -1);
        db.collection("gameRooms").document(roomId).update(u);
    }

    private void startRoundLocally() {
        tvTargetNumber.setText(String.valueOf(targetNumber));
        llNumberButtons.removeAllViews();
        for (int i = 0; i < numbers.size(); i++) {
            final int idx = i;
            MaterialButton btn = new MaterialButton(this);
            btn.setText(String.valueOf(numbers.get(i)));
            int num = numbers.get(i);
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    num >= 25 ? 0xFFE94560 : (num >= 10 ? 0xFFFF912B : 0xFF9B59B6)));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 120, 1f);
            p.setMargins(3, 3, 3, 3);
            btn.setLayoutParams(p);
            btn.setCornerRadius(16);
            btn.setOnClickListener(v -> {
                if (!myTurnFinished && !usedIndices.contains(idx)) {
                    usedIndices.add(idx);
                    expression.append(numbers.get(idx));
                    updateExpression();
                    btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD5C4E0));
                }
            });
            llNumberButtons.addView(btn);
        }
        enableInput(true);
        if (roundTimer != null) roundTimer.cancel();
        roundTimer = new CountDownTimer(60000, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText(String.valueOf(ms / 1000)); }
            @Override public void onFinish() { if (!myTurnFinished) confirmAnswer(); }
        }.start();
    }

    private void confirmAnswer() {
        if (myTurnFinished) return;
        myTurnFinished = true;
        enableInput(false);
        if (roundTimer != null) roundTimer.cancel();
        int res = -1;
        try {
            res = evaluate(expression.toString());
            tvResult.setText("= " + res);
        } catch (Exception e) {
            tvResult.setText("= ?");
        }
        Map<String, Object> u = new HashMap<>();
        u.put(isPlayer1 ? "mojbroj_p1Result" : "mojbroj_p2Result", res);
        u.put(isPlayer1 ? "mojbroj_p1Finished" : "mojbroj_p2Finished", true);
        db.collection("gameRooms").document(roomId).update(u);
        tvResult.setText("Sačekajte protivnika...");
    }

    private void showComparison(com.google.firebase.firestore.DocumentSnapshot snap) {
        Long r1 = snap.getLong("mojbroj_p1Result");
        Long r2 = snap.getLong("mojbroj_p2Result");
        if (r1 == null || r2 == null) return;
        int v1 = r1.intValue(), v2 = r2.intValue();
        int d1 = v1 >= 0 ? Math.abs(v1 - targetNumber) : 999;
        int d2 = v2 >= 0 ? Math.abs(v2 - targetNumber) : 999;
        tvResult.setText("P1: " + (v1 < 0 ? "/" : v1) + " (" + d1 + ") | P2: "
                + (v2 < 0 ? "/" : v2) + " (" + d2 + ")");
    }

    private void calculateAndMove() {
        db.collection("gameRooms").document(roomId).get()
                .addOnSuccessListener(freshSnap -> {
                    Long r1Long = freshSnap.getLong("mojbroj_p1Result");
                    Long r2Long = freshSnap.getLong("mojbroj_p2Result");
                    if (r1Long == null || r2Long == null) return;

                    int r1 = r1Long.intValue(), r2 = r2Long.intValue();
                    int p1Add = 0, p2Add = 0;

                    if (r1 == targetNumber) p1Add = 10;
                    if (r2 == targetNumber) p2Add = 10;

                    if (p1Add == 0 && p2Add == 0) {
                        int d1 = r1 >= 0 ? Math.abs(r1 - targetNumber) : 1000;
                        int d2 = r2 >= 0 ? Math.abs(r2 - targetNumber) : 1000;
                        if (d1 < d2) p1Add = 5;
                        else if (d2 < d1) p2Add = 5;
                        else if (d1 != 1000) {
                            if (phase.equals("p1_playing")) p1Add = 5; else p2Add = 5;
                        }
                    }

                    long currentP1 = freshSnap.getLong("player1Score") != null ? freshSnap.getLong("player1Score") : 0;
                    long currentP2 = freshSnap.getLong("player2Score") != null ? freshSnap.getLong("player2Score") : 0;

                    Map<String, Object> u = new HashMap<>();
                    u.put("player1Score", currentP1 + p1Add);
                    u.put("player2Score", currentP2 + p2Add);
                    
                    // Reset flags
                    u.put("mojbroj_p1Finished", false);
                    u.put("mojbroj_p2Finished", false);
                    u.put("mojbroj_p1Result", -1);
                    u.put("mojbroj_p2Result", -1);

                    if (phase.equals("p1_playing")) {
                        // Ako idemo u drugu rundu, odmah generiši brojeve
                        Random r = new Random();
                        int t = r.nextInt(900) + 100;
                        List<Integer> ns = new ArrayList<>();
                        for (int i = 0; i < 4; i++) ns.add(r.nextInt(9) + 1);
                        ns.add(new int[]{10, 15, 20}[r.nextInt(3)]);
                        ns.add(new int[]{25, 50, 75, 100}[r.nextInt(4)]);
                        
                        u.put("r2Target", t);
                        u.put("r2Numbers", ns);
                        u.put("mojbroj_phase", "p2_playing");
                    } else {
                        u.put("mojbroj_phase", "done");
                    }
                    
                    db.collection("gameRooms").document(roomId).update(u);
                });
    }

    private int evaluate(String e) {
        String s = e.trim();
        if (s.isEmpty()) return -1;
        if (s.startsWith("(") && s.endsWith(")")) s = s.substring(1, s.length() - 1);
        int d = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') d++;
            else if (c == '(') d--;
            else if (d == 0 && (c == '+' || c == '-') && i > 0)
                return evaluate(s.substring(0, i)) + (c == '+' ? 1 : -1) * evaluate(s.substring(i + 1));
        }
        d = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') d++;
            else if (c == '(') d--;
            else if (d == 0 && (c == '*' || c == '/') && i > 0) {
                int l = evaluate(s.substring(0, i)), r = evaluate(s.substring(i + 1));
                if (c == '/') return r == 0 ? 0 : l / r;
                return l * r;
            }
        }
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return -1; }
    }

    private void appendOperator(String o) {
        if (!myTurnFinished && targetNumber > 0) {
            expression.append(o);
            updateExpression();
        }
    }

    private void backspace() {
        if (!myTurnFinished && expression.length() > 0) {
            String s = expression.toString();
            for (int i = usedIndices.size() - 1; i >= 0; i--) {
                String n = String.valueOf(numbers.get(usedIndices.get(i)));
                if (s.endsWith(n)) {
                    int idx = usedIndices.remove(i);
                    expression.delete(s.length() - n.length(), s.length());
                    resetBtnCol((MaterialButton) llNumberButtons.getChildAt(idx), numbers.get(idx));
                    updateExpression();
                    return;
                }
            }
            expression.deleteCharAt(s.length() - 1);
            updateExpression();
        }
    }

    private void clearExpression() {
        if (!myTurnFinished) {
            expression = new StringBuilder();
            usedIndices.clear();
            for (int i = 0; i < llNumberButtons.getChildCount(); i++)
                resetBtnCol((MaterialButton) llNumberButtons.getChildAt(i), numbers.get(i));
            updateExpression();
        }
    }

    private void resetBtnCol(MaterialButton b, int n) {
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                n >= 25 ? 0xFFE94560 : (n >= 10 ? 0xFFFF912B : 0xFF9B59B6)));
    }

    private void updateExpression() {
        tvExpression.setText(expression.toString());
    }

    private void enableInput(boolean e) {
        int[] ids = {R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide,
                R.id.btnOpenParen, R.id.btnCloseParen, R.id.btnBackspace,
                R.id.btnClear, R.id.btnConfirm};
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) {
                v.setEnabled(e);
                v.setAlpha(e ? 1f : 0.4f);
            }
        }
    }

    private boolean isRoundHost() {
        return (phase.equals("p1_playing") && isPlayer1) || (phase.equals("p2_playing") && !isPlayer1);
    }

    private void finishGame() {
        if (!transitioning) {
            transitioning = true;
            if (isPlayer1) {
                if (gameListener != null) gameListener.remove();
                db.collection("gameRooms").document(roomId)
                        .update("currentGame", "koZnaZna")
                        .addOnSuccessListener(unused -> {
                            Intent intent = new Intent(this, KoZnaZnaActivity.class);
                            intent.putExtra("roomId", roomId);
                            intent.putExtra("isPlayer1", isPlayer1);
                            startActivity(intent);
                            finish();
                        });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roundTimer != null) roundTimer.cancel();
        if (gameListener != null) gameListener.remove();
    }
}
