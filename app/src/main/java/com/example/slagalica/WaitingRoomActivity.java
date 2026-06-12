package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.models.KoZnaZnaQuestion;
import com.example.slagalica.models.SpojnicaModel;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WaitingRoomActivity extends AppCompatActivity {

    private TextView tvRoomId, tvStatus;
    private EditText etRoomId;
    private MaterialButton btnCreateRoom, btnJoinRoom;
    private ProgressBar progressBar;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUserId;
    private String currentUserName = "Gost";
    private String roomId;
    private ListenerRegistration roomListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_room);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : UUID.randomUUID().toString();

        initViews();
        fetchUserName();

        btnCreateRoom.setOnClickListener(v -> createRoom());
        btnJoinRoom.setOnClickListener(v -> joinRoom());
        seedData();
    }

    private void initViews() {
        tvRoomId = findViewById(R.id.tvRoomId);
        tvStatus = findViewById(R.id.tvStatus);
        etRoomId = findViewById(R.id.etRoomId);
        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        progressBar = findViewById(R.id.progressBar);
    }

    private void seedData() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Seed Ko Zna Zna
        db.collection("ko_zna_zna_questions").get().addOnSuccessListener(snap -> {
            if (snap.isEmpty()) {
                Map<String, Object> q1 = new HashMap<>();
                q1.put("question", "Koji je glavni grad Francuske?");
                q1.put("answers", java.util.Arrays.asList("London", "Berlin", "Pariz", "Rim"));
                q1.put("correctAnswerIndex", 2);
                db.collection("ko_zna_zna_questions").add(q1);

                Map<String, Object> q2 = new HashMap<>();
                q2.put("question", "Koliko planeta ima Sunčev sistem?");
                q2.put("answers", java.util.Arrays.asList("7", "8", "9", "10"));
                q2.put("correctAnswerIndex", 1);
                db.collection("ko_zna_zna_questions").add(q2);

                Map<String, Object> q3 = new HashMap<>();
                q3.put("question", "Ko je napisao 'Na Drini ćuprija'?");
                q3.put("answers", java.util.Arrays.asList("Ivo Andrić", "Meša Selimović", "Miloš Crnjanski", "Bora Stanković"));
                q3.put("correctAnswerIndex", 0);
                db.collection("ko_zna_zna_questions").add(q3);
            }
        });

        // 2. Seed Spojnice
        db.collection("spojnice").get().addOnSuccessListener(snap -> {
            if (snap.isEmpty()) {
                Map<String, Object> s1 = new HashMap<>();
                s1.put("naslov", "Spoji države i glavne gradove");
                s1.put("levaKolona", java.util.Arrays.asList("Srbija", "Hrvatska", "Grčka", "Italija", "Španija"));
                s1.put("desnaKolona", java.util.Arrays.asList("Rim", "Madrid", "Beograd", "Zagreb", "Atina"));
                s1.put("tacniIndeksi", java.util.Arrays.asList(2, 3, 4, 0, 1));
                db.collection("spojnice").add(s1);
            }
        });

        // 3. Seed Asocijacije
        db.collection("asocijacije").get().addOnSuccessListener(snap -> {
            if (snap.size() >= 2) return;

            Map<String, Object> a1 = new HashMap<>();
            a1.put("kolonaA", java.util.Arrays.asList("PAPIR", "OLOVKA", "SKOLA", "DJAK"));
            a1.put("resenjeA", "KNJIGA");
            a1.put("kolonaB", java.util.Arrays.asList("GLUMAC", "SCENA", "DRAMA", "MASKA"));
            a1.put("resenjeB", "POZORISTE");
            a1.put("kolonaV", java.util.Arrays.asList("KLAVIR", "NOTA", "PESMA", "PEVAC"));
            a1.put("resenjeV", "MUZIKA");
            a1.put("kolonaG", java.util.Arrays.asList("SLIKA", "MUZEJ", "BOJA", "CETKICA"));
            a1.put("resenjeG", "UMETNOST");
            a1.put("konacnoResenje", "KULTURA");
            db.collection("asocijacije").add(a1);

            Map<String, Object> a2 = new HashMap<>();
            a2.put("kolonaA", java.util.Arrays.asList("KRALJ", "KRALJICA", "DVOR", "KRUNA"));
            a2.put("resenjeA", "MONARHIJA");
            a2.put("kolonaB", java.util.Arrays.asList("TOP", "LOVAC", "PESAK", "KRALJ"));
            a2.put("resenjeB", "SAH");
            a2.put("kolonaV", java.util.Arrays.asList("GLAVA", "TELO", "RUKE", "NOGE"));
            a2.put("resenjeV", "COVEK");
            a2.put("kolonaG", java.util.Arrays.asList("ZIMA", "SNEG", "LED", "MRAZ"));
            a2.put("resenjeG", "HLADNOCA");
            a2.put("konacnoResenje", "DRZAVA");
            db.collection("asocijacije").add(a2);
        });
    }

    private void fetchUserName() {
        if (mAuth.getCurrentUser() != null) {
            db.collection("users").document(currentUserId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            currentUserName = documentSnapshot.getString("username");
                        }
                    });
        }
    }

    private void createRoom() {
        progressBar.setVisibility(View.VISIBLE);
        btnCreateRoom.setEnabled(false);
        btnJoinRoom.setEnabled(false);

        Task<QuerySnapshot> qTask = db.collection("ko_zna_zna_questions").get();
        Task<QuerySnapshot> sTask = db.collection("spojnice").get();
        Task<QuerySnapshot> aTask = db.collection("asocijacije").get();

        Tasks.whenAllComplete(qTask, sTask, aTask).addOnSuccessListener(tasks -> {
            try {
                // 1. Pitanja
                List<KoZnaZnaQuestion> selectedQuestions = new ArrayList<>();
                if (qTask.isSuccessful() && qTask.getResult() != null) {
                    for (QueryDocumentSnapshot doc : qTask.getResult()) {
                        KoZnaZnaQuestion q = doc.toObject(KoZnaZnaQuestion.class);
                        if (q != null) selectedQuestions.add(q);
                    }
                    Collections.shuffle(selectedQuestions);
                    if (selectedQuestions.size() > 5) selectedQuestions = selectedQuestions.subList(0, 5);
                }

                // 2. Spojnice
                List<SpojnicaModel> variants = new ArrayList<>();
                if (sTask.isSuccessful() && sTask.getResult() != null) {
                    Map<String, List<SpojnicaModel>> grouped = new HashMap<>();
                    for (QueryDocumentSnapshot doc : sTask.getResult()) {
                        SpojnicaModel s = doc.toObject(SpojnicaModel.class);
                        if (s != null && s.getTitle() != null) {
                            if (!grouped.containsKey(s.getTitle())) grouped.put(s.getTitle(), new ArrayList<>());
                            grouped.get(s.getTitle()).add(s);
                        }
                    }
                    for (String title : grouped.keySet()) {
                        if (grouped.get(title).size() >= 2) {
                            variants = grouped.get(title);
                            Collections.shuffle(variants);
                            break;
                        }
                    }
                }

                // 3. Asocijacije
                List<Map<String, Object>> selectedAsocijacije = new ArrayList<>();
                if (aTask.isSuccessful() && aTask.getResult() != null) {
                    for (QueryDocumentSnapshot doc : aTask.getResult()) {
                        selectedAsocijacije.add(doc.getData());
                    }
                    Collections.shuffle(selectedAsocijacije);
                    if (selectedAsocijacije.size() > 2) selectedAsocijacije = selectedAsocijacije.subList(0, 2);
                }

                // Provera dovoljno podataka
                if (selectedQuestions.size() < 5) {
                    Toast.makeText(this, "Nedovoljno pitanja u bazi: " + selectedQuestions.size() + "/5", Toast.LENGTH_LONG).show();
                    resetUI();
                    return;
                }
                if (variants.size() < 2) {
                    Toast.makeText(this, "Nedovoljno spojnica sa istom temom u bazi.", Toast.LENGTH_LONG).show();
                    resetUI();
                    return;
                }
                if (selectedAsocijacije.size() < 2) {
                    Toast.makeText(this, "Nedovoljno asocijacija u bazi: " + selectedAsocijacije.size() + "/2", Toast.LENGTH_LONG).show();
                    resetUI();
                    return;
                }

                // 4. Skočko kombinacije
                java.util.Random rand = new java.util.Random();
                List<Integer> skocko1 = new ArrayList<>();
                List<Integer> skocko2 = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    skocko1.add(rand.nextInt(6));
                    skocko2.add(rand.nextInt(6));
                }

                // 5. Kreiranje sobe sa SVIM potrebnim poljima
                roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                Map<String, Object> room = new HashMap<>();

                // Osnovni podaci
                room.put("player1Id", currentUserId);
                room.put("player1Name", currentUserName);
                room.put("player2Id", null);
                room.put("player2Name", null);
                room.put("player1Score", 0);
                room.put("player2Score", 0);
                room.put("status", "waiting");

                // Ko zna zna
                room.put("koZnaZnaQuestions", selectedQuestions);
                room.put("currentQuestionIndex", 0);
                room.put("questionStartTime", 0);
                room.put("answers_q0", new HashMap<String, Object>());
                room.put("answers_q1", new HashMap<String, Object>());
                room.put("answers_q2", new HashMap<String, Object>());
                room.put("answers_q3", new HashMap<String, Object>());
                room.put("answers_q4", new HashMap<String, Object>());

                // Spojnice
                room.put("spojnica1", variants.get(0));
                room.put("spojnica2", variants.get(1));
                room.put("spojnice_turn", "p1");
                room.put("spojnice_currentLeftIndex", 0);
                room.put("spojnice_matchedRightIndices", java.util.Arrays.asList(-1, -1, -1, -1, -1));
                room.put("spojnice_whoMatched", java.util.Arrays.asList("", "", "", "", ""));
                room.put("spojnice_lastWrongIndex", -1);
                room.put("spojnice_wrongClickTrigger", 0L);

                // Asocijacije - SVA POTREBNA POLJA
                room.put("asocijacija1", selectedAsocijacije.get(0));
                room.put("asocijacija2", selectedAsocijacije.get(1));
                room.put("asoc_currentRound", 1);
                room.put("asoc_turn", "p1");
                room.put("asoc_solvedA", false);
                room.put("asoc_solvedB", false);
                room.put("asoc_solvedV", false);
                room.put("asoc_solvedG", false);
                room.put("asoc_solvedFinal", false);

                Map<String, Object> asocOpened = new HashMap<>();
                asocOpened.put("0", java.util.Arrays.asList(false, false, false, false));
                asocOpened.put("1", java.util.Arrays.asList(false, false, false, false));
                asocOpened.put("2", java.util.Arrays.asList(false, false, false, false));
                asocOpened.put("3", java.util.Arrays.asList(false, false, false, false));
                room.put("asoc_opened", asocOpened);

                // Skočko
                room.put("skocko_target1", skocko1);
                room.put("skocko_target2", skocko2);
                room.put("skocko_turn", "p1");
                room.put("skocko_currentRound", 1);
                room.put("skocko_attempts", new ArrayList<>());
                room.put("skocko_isSteal", false);

                db.collection("gameRooms").document(roomId).set(room)
                        .addOnSuccessListener(aVoid -> {
                            tvRoomId.setText("Šifra sobe: " + roomId);
                            tvRoomId.setVisibility(View.VISIBLE);
                            tvStatus.setVisibility(View.VISIBLE);
                            progressBar.setVisibility(View.GONE);
                            listenForOpponent(true);
                        })
                        .addOnFailureListener(ex -> {
                            Log.e("WaitingRoom", "Greška pri upisu sobe", ex);
                            Toast.makeText(this, "Greška: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                            resetUI();
                        });

            } catch (Exception ex) {
                Log.e("WaitingRoom", "Crash u createRoom", ex);
                Toast.makeText(this, "Greška: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                resetUI();
            }
        });
    }

    private void resetUI() {
        progressBar.setVisibility(View.GONE);
        btnCreateRoom.setEnabled(true);
        btnJoinRoom.setEnabled(true);
    }

    private void joinRoom() {
        String inputId = etRoomId.getText().toString().trim().toUpperCase();
        if (inputId.isEmpty()) return;

        progressBar.setVisibility(View.VISIBLE);
        btnJoinRoom.setEnabled(false);
        btnCreateRoom.setEnabled(false);

        DocumentReference roomRef = db.collection("gameRooms").document(inputId);
        roomRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists() && "waiting".equals(snapshot.getString("status"))) {
                roomId = inputId;
                Map<String, Object> updates = new HashMap<>();
                updates.put("player2Id", currentUserId);
                updates.put("player2Name", currentUserName);
                updates.put("status", "playing");
                updates.put("roundStartTime", System.currentTimeMillis());
                updates.put("questionStartTime", System.currentTimeMillis());
                roomRef.update(updates).addOnSuccessListener(aVoid -> listenForOpponent(false));
            } else {
                Toast.makeText(this, "Soba nije dostupna", Toast.LENGTH_SHORT).show();
                resetUI();
            }
        });
    }

    private void listenForOpponent(boolean isPlayer1) {
        roomListener = db.collection("gameRooms").document(roomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists() && "playing".equals(snapshot.getString("status"))) {
                        if (roomListener != null) roomListener.remove();
                        Intent intent = new Intent(this, SkockoActivity.class);
                        intent.putExtra("roomId", roomId);
                        intent.putExtra("isPlayer1", isPlayer1);
                        startActivity(intent);
                        finish();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) roomListener.remove();
    }
}