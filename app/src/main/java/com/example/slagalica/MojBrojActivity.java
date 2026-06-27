package com.example.slagalica;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MojBrojActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvPlayer1Name, tvPlayer1Points, tvPlayer2Name, tvPlayer2Points;
    private ImageView ivPlayer1Avatar, ivPlayer2Avatar;
    private TextView tvRound, tvTimer, tvTargetNumber, tvExpression, tvResult;
    private LinearLayout llNumberButtons;
    private MaterialButton btnStop;

    private FirebaseFirestore db;
    private String roomId;
    private boolean isPlayer1;
    private ListenerRegistration gameListener;

    private SensorManager sensorManager;
    private float acceleration;
    private float currentAcceleration;
    private float lastAcceleration;
    private static final int SHAKE_THRESHOLD = 12;

    private int targetNumber = -1;
    private boolean numbersLoaded = false;
    private boolean generateCalled = false;
    private final List<Integer> numbers = new ArrayList<>();
    private StringBuilder expression = new StringBuilder();
    private final List<Integer> usedIndices = new ArrayList<>();
    private boolean transitioning = false;
    private boolean isFinalizingRound = false;
    private boolean myTurnFinished = false;
    private boolean gameStatsRecordedThisMatch = false;

    private String phase = "";
    private CountDownTimer roundTimer;
    private CountDownTimer revealTimer;
    private boolean statsUpdatedThisRound = false;
    private boolean p1Ready = false, p2Ready = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        db = FirebaseFirestore.getInstance();
        roomId = getIntent().getStringExtra("roomId");
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);

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
        attachGameListener();
    }

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Napuštanje igre")
                .setMessage("Ako napustite igru sada, izgubićete 10 zvezdi. Da li ste sigurni?")
                .setPositiveButton("Da", (dialog, which) -> {
                    String uid = FirebaseAuth.getInstance().getUid();
                    if (uid != null) RankingManager.updateStars(uid, -10);
                    db.collection("gameRooms").document(roomId).update("playerLeft", isPlayer1 ? "p1" : "p2");
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                })
                .setNegativeButton("Ne", null)
                .show();
    }

    private void initViews() {
        tvPlayer1Name = findViewById(R.id.tvPlayer1Name);
        tvPlayer1Points = findViewById(R.id.tvPlayer1Points);
        tvPlayer2Name = findViewById(R.id.tvPlayer2Name);
        tvPlayer2Points = findViewById(R.id.tvPlayer2Points);
        ivPlayer1Avatar = findViewById(R.id.ivPlayer1Avatar);
        ivPlayer2Avatar = findViewById(R.id.ivPlayer2Avatar);
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

        btnStop = findViewById(R.id.btnStop);
        if (btnStop != null) {
            btnStop.setVisibility(View.GONE);
            btnStop.setOnClickListener(v -> stopRolling());
        }

        enableInput(false);
    }

    private void stopRolling() {
        if (revealTimer != null) revealTimer.cancel();
        if (btnStop != null) btnStop.setVisibility(View.GONE);
        
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

    private void startRollingAnimation() {
        if (btnStop != null) btnStop.setVisibility(View.VISIBLE);
        Random r = new Random();
        revealTimer = new CountDownTimer(30000, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTargetNumber.setText(String.valueOf(r.nextInt(900) + 100));
                for (int i = 0; i < llNumberButtons.getChildCount(); i++) {
                    View v = llNumberButtons.getChildAt(i);
                    if (v instanceof MaterialButton) {
                        ((MaterialButton) v).setText(String.valueOf(r.nextInt(9) + 1));
                    }
                }
            }
            @Override
            public void onFinish() {
                stopRolling();
            }
        }.start();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        lastAcceleration = currentAcceleration;
        currentAcceleration = (float) Math.sqrt(x * x + y * y + z * z);
        float delta = currentAcceleration - lastAcceleration;
        acceleration = acceleration * 0.9f + delta;
        if (acceleration > SHAKE_THRESHOLD && btnStop != null && btnStop.getVisibility() == View.VISIBLE) {
            stopRolling();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
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

        tvPlayer1Name.setText(snapshot.getString("player1Name"));
        tvPlayer2Name.setText(snapshot.getString("player2Name"));
        tvPlayer1Points.setText(String.valueOf(snapshot.getLong("player1Score") != null ? snapshot.getLong("player1Score") : 0));
        tvPlayer2Points.setText(String.valueOf(snapshot.getLong("player2Score") != null ? snapshot.getLong("player2Score") : 0));

        setAvatar(ivPlayer1Avatar, snapshot.getString("player1Avatar"));
        setAvatar(ivPlayer2Avatar, snapshot.getString("player2Avatar"));

        // Spec 3f: protivnik napustio - preskoči čekanje i završi igru
        String playerLeft = snapshot.getString("playerLeft");
        if (playerLeft != null && !playerLeft.isEmpty()) {
            String opponent = isPlayer1 ? "p2" : "p1";
            if (playerLeft.equals(opponent)) {
                // Preostali igrač završava rundu/igru
                if (phase.equals("p1_playing") || phase.equals("p2_playing")) {
                    // Ako protivnik nije završio, postavi mu -1
                    String oppResultField = isPlayer1 ? "mojbroj_p2Result" : "mojbroj_p1Result";
                    String oppFinishedField = isPlayer1 ? "mojbroj_p2Finished" : "mojbroj_p1Finished";
                    if (snapshot.get(oppResultField) == null || (Long)snapshot.get(oppResultField) == -1) {
                         db.collection("gameRooms").document(roomId).update(oppResultField, -1, oppFinishedField, true);
                    }
                } else if (phase.equals("done")) {
                    finishGame();
                }
            }
        }

        String snapPhase = snapshot.getString("mojbroj_phase");
        if (snapPhase == null) snapPhase = "p1_playing";

        if (phase.equals("done")) {
            transitioning = true;
            if (gameListener != null) gameListener.remove();
            if (roundTimer != null) roundTimer.cancel();
            Intent intent = new Intent(this, KoZnaZnaActivity.class);
            intent.putExtra("roomId", roomId);
            intent.putExtra("isPlayer1", isPlayer1);
            startActivity(intent);
            finish();
            return;
        }

        boolean opponentLeft = playerLeft != null && !playerLeft.isEmpty() && !playerLeft.equals(isPlayer1 ? "p1" : "p2");

        if (!snapPhase.equals(phase)) {
            phase = snapPhase;
            resetRoundUI();
            if (phase.equals("done")) {
                finishGame();
                return;
            }
            // Generisanje brojeva: Radi Player 1, ili preostali igrač ako je protivnik otišao
            if (!generateCalled && (isPlayer1 || opponentLeft)) {
                generateCalled = true;
                startRollingAnimation();
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
                || (opponentLeft && (isPlayer1 ? Boolean.TRUE.equals(snapshot.getBoolean("mojbroj_p1Finished")) : Boolean.TRUE.equals(snapshot.getBoolean("mojbroj_p2Finished"))))) {
            
            // Ako je protivnik otišao, simuliraj da je i on završio (ako već nije)
            String oppFinishedField = isPlayer1 ? "mojbroj_p2Finished" : "mojbroj_p1Finished";
            if (opponentLeft && !Boolean.TRUE.equals(snapshot.getBoolean(oppFinishedField))) {
                 db.collection("gameRooms").document(roomId).update(oppFinishedField, true, (isPlayer1 ? "mojbroj_p2Result" : "mojbroj_p1Result"), -1);
                 return;
            }

            if (Boolean.TRUE.equals(snapshot.getBoolean("mojbroj_p1Finished")) && Boolean.TRUE.equals(snapshot.getBoolean("mojbroj_p2Finished"))) {
                showComparison(snapshot);
                updateLocalStats(snapshot);

                if (isPlayer1 || opponentLeft) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::calculateAndMove, 3000);
                }
            }
        }
    }

    private boolean finishTimerStarted = false;
    private void showNextGameButton() {}

    private void resetRoundUI() {
        if (roundTimer != null) roundTimer.cancel();
        isFinalizingRound = false;
        myTurnFinished = false;
        statsUpdatedThisRound = false;
        targetNumber = -1;
        numbersLoaded = false;
        generateCalled = false;
        finishTimerStarted = false;
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
        // Obsolete, moved to stopRolling()
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
        
        // Spec 3f/g: Automatski prelaz na Home nakon završetka druge runde (faza done)
        if (phase.equals("done")) {
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (!isFinishing()) {
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                }
            }, 5000);
        }
    }

    private void updateLocalStats(com.google.firebase.firestore.DocumentSnapshot snap) {
        if (statsUpdatedThisRound) return;
        statsUpdatedThisRound = true;

        Long r1Long = snap.getLong("mojbroj_p1Result");
        Long r2Long = snap.getLong("mojbroj_p2Result");
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

        if (isPlayer1) {
            StatisticsManager.updateMBStats(r1 == targetNumber, p1Add, !gameStatsRecordedThisMatch);
        } else {
            StatisticsManager.updateMBStats(r2 == targetNumber, p2Add, !gameStatsRecordedThisMatch);
        }
        gameStatsRecordedThisMatch = true;
    }

    private void calculateAndMove() {
        if (roundTimer != null) roundTimer.cancel();
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
                        u.put("roundStartTime", System.currentTimeMillis());
                    } else {
                        u.put("mojbroj_phase", "done");
                        u.put("currentGame", "koZnaZna");
                        u.put("questionStartTime", System.currentTimeMillis());
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

    private void setAvatar(ImageView iv, String avatarName) {
        if (iv == null || avatarName == null || avatarName.isEmpty()) return;
        int resId = getResources().getIdentifier(avatarName, "drawable", getPackageName());
        if (resId != 0) {
            iv.setImageResource(resId);
        }
    }

    private boolean isRoundHost() {
        return (phase.equals("p1_playing") && isPlayer1) || (phase.equals("p2_playing") && !isPlayer1);
    }

    private void finishGame() {
        if (transitioning) return;
        transitioning = true;
        if (roundTimer != null) roundTimer.cancel();
        if (gameListener != null) gameListener.remove();
        db.collection("gameRooms").document(roomId).get()
                .addOnSuccessListener(snap -> {
                    long p1Score = snap.getLong("player1Score") != null ? snap.getLong("player1Score") : 0;
                    long p2Score = snap.getLong("player2Score") != null ? snap.getLong("player2Score") : 0;
                    String p1Name = snap.getString("player1Name") != null ? snap.getString("player1Name") : "Igrač 1";
                    String p2Name = snap.getString("player2Name") != null ? snap.getString("player2Name") : "Igrač 2";
                    String p1Id = snap.getString("player1Id");
                    String p2Id = snap.getString("player2Id");
                    String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    Boolean isFriendlyVal = snap.getBoolean("isFriendly");
                    boolean isFriendly = isFriendlyVal != null && isFriendlyVal;

                    long myScore = isPlayer1 ? p1Score : p2Score;
                    int starChange=0;
                    if (!isFriendly) {
                        // Spec 3d: računamo promenu zvezda
                        String result;


                        if (p1Score == p2Score) {
                            result = "draw";
                            starChange = (int) (myScore / 40); // bez ±10 za nerešeno
                        } else if ((isPlayer1 && p1Score > p2Score) || (!isPlayer1 && p2Score > p1Score)) {
                            result = "win";
                            starChange = 10 + (int) (myScore / 40);
                        } else {
                            result = "loss";
                            starChange = -10 + (int) (myScore / 40);
                        }

                        StatisticsManager.updateMatchResult(result);

                        // Napravi finalnu kopiju da bi mogla da se koristi unutar lambde (addOnSuccessListener)
                        final int finalStarChange = starChange;

                        // Spec 3d-iii: 50 zvezda → 1 token; čitamo trenutne zvezde pre upisa
                        db.collection("users").document(currentUserId).get()
                                .addOnSuccessListener(userDoc -> {
                                    long current = userDoc.getLong("stars") != null ? userDoc.getLong("stars") : 0;
                                    long updated = Math.max(0, current + finalStarChange);
                                    long tokensEarned = (updated / 50) - (current / 50);

                                    Map<String, Object> userUpd = new HashMap<>();
                                    userUpd.put("stars", updated);
                                    if (tokensEarned > 0)
                                        userUpd.put("tokens", com.google.firebase.firestore.FieldValue.increment(tokensEarned));
                                    db.collection("users").document(currentUserId).update(userUpd);
                                });

                        RankingManager.updateStars(currentUserId, starChange);

                        if ("win".equals(result)) {
                            RankingManager.completeMission(currentUserId, "win_game");
                        }

                        if (!gameStatsRecordedThisMatch) {
                            gameStatsRecordedThisMatch = true;
                            db.collection("users").document(currentUserId)
                                    .update("gamesPlayed", com.google.firebase.firestore.FieldValue.increment(1));
                        }
                    } else {
                        // Spec 3e: prijateljska partija — samo misija, bez zvezda/statistike
                        RankingManager.completeMission(currentUserId, "play_friendly");
                    }

                    // Turnir: ako je ovo turnirska partija, primeni posebna pravila nagrađivanja
                    String tId = snap.getString("tournamentId");
                    String tWinnerKey = snap.getString("tournamentWinnerKey");
                    if (tId != null && !tId.isEmpty() && tWinnerKey != null && !tWinnerKey.isEmpty()) {
                        String winnerId = p1Score >= p2Score ? p1Id : p2Id;
                        boolean iWon = winnerId != null && winnerId.equals(currentUserId);
                        boolean isSemiFinal = tWinnerKey.equals("sf1Winner") || tWinnerKey.equals("sf2Winner");

                        // Spec 10d: SF gubitnik ne dobija NI zvezde ni kaznu
                        // Spec 10e: Final gubitnik dobija zvezde po regularnim pravilima (starChange već izračunat)
                        if (!iWon && isSemiFinal) {
                            // Poništi promenu zvezda za gubitnika polufinala
                            // (updateStars je već pozvan gore - poništavamo ga negativnim)
                            RankingManager.updateStars(currentUserId, -starChange);
                        }

                        db.collection("tournaments").document(tId)
                                .update(tWinnerKey, winnerId)
                                .addOnCompleteListener(task -> {
                                    Intent intent = new Intent(this, TournamentActivity.class);
                                    intent.putExtra("tournamentId", tId);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    startActivity(intent);
                                    finish();
                                });
                        return;
                    }

                    String winner = p1Score > p2Score ? p1Name : (p2Score > p1Score ? p2Name : "Nerešeno");
                    new AlertDialog.Builder(this)
                            .setTitle("Kraj igre!")
                            .setMessage("Pobednik: " + winner + "\n" + p1Name + ": " + p1Score + "\n" + p2Name + ": " + p2Score)
                            .setCancelable(false)
                            .setPositiveButton("U redu", (d, w) -> {
                                startActivity(new Intent(this, HomeActivity.class));
                                finish();
                            }).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roundTimer != null) roundTimer.cancel();
        if (gameListener != null) gameListener.remove();
        if (!isFinishing()) {
            writePlayerLeft();
            // Spec 3f: Napuštanjem igre igrač gubi partiju i ne dobija zvezde (-10 zvezdi)
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid != null) {
                // Proveri da li je friendly (treba nam snap, ali ovde smo u onDestroy)
                // Za sad samo penalizuj, asinhrono je ionako.
                RankingManager.updateStars(uid, -10);
            }
        }
    }

    private void writePlayerLeft() {
        if (roomId == null) return;
        db.collection("gameRooms").document(roomId).update("playerLeft", isPlayer1 ? "p1" : "p2");
    }
}