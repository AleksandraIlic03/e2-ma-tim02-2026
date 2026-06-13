package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
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
    private String currentUserAvatar = "ic_user";
    private String roomId;
    private ListenerRegistration roomListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_room);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser() != null
                ? mAuth.getCurrentUser().getUid()
                : UUID.randomUUID().toString();

        initViews();
        fetchUserData();

        btnCreateRoom.setOnClickListener(v -> createRoom());
        btnJoinRoom.setOnClickListener(v -> joinRoom());
    }

    private void initViews() {
        tvRoomId = findViewById(R.id.tvRoomId);
        tvStatus = findViewById(R.id.tvStatus);
        etRoomId = findViewById(R.id.etRoomId);
        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        progressBar = findViewById(R.id.progressBar);
    }

    private void fetchUserData() {
        if (mAuth.getCurrentUser() != null) {
            db.collection("users").document(currentUserId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            currentUserName = documentSnapshot.getString("username");
                            String avatar = documentSnapshot.getString("avatarUrl");
                            if (avatar != null && !avatar.isEmpty()) {
                                currentUserAvatar = avatar;
                            }
                        }
                    });
        }
    }

    private void createRoom() {
        progressBar.setVisibility(View.VISIBLE);
        btnCreateRoom.setEnabled(false);
        btnJoinRoom.setEnabled(false);

        Task<QuerySnapshot> questionsTask = db.collection("ko_zna_zna_questions").get();
        Task<QuerySnapshot> spojniceTask = db.collection("spojnice").get();
        Task<QuerySnapshot> asociacijeTask = db.collection("asocijacije").get();

        Tasks.whenAllSuccess(questionsTask, spojniceTask, asociacijeTask).addOnSuccessListener(results -> {
            QuerySnapshot qSnap = (QuerySnapshot) results.get(0);
            QuerySnapshot sSnap = (QuerySnapshot) results.get(1);
            QuerySnapshot aSnap = (QuerySnapshot) results.get(2);

            List<KoZnaZnaQuestion> allQuestions = new ArrayList<>();
            for (QueryDocumentSnapshot doc : qSnap)
                allQuestions.add(doc.toObject(KoZnaZnaQuestion.class));

            if (allQuestions.size() < 5) {
                Toast.makeText(this, "Nedovoljno pitanja 'Ko zna zna'",
                        Toast.LENGTH_SHORT).show();
                resetUI();
                return;
            }
            Collections.shuffle(allQuestions);
            List<KoZnaZnaQuestion> selectedQuestions = allQuestions.subList(0, 5);

            Map<String, List<SpojnicaModel>> grouped = new HashMap<>();
            for (QueryDocumentSnapshot doc : sSnap) {
                SpojnicaModel s = doc.toObject(SpojnicaModel.class);
                if (!grouped.containsKey(s.getTitle()))
                    grouped.put(s.getTitle(), new ArrayList<>());
                grouped.get(s.getTitle()).add(s);
            }

            List<String> validTitles = new ArrayList<>();
            for (String title : grouped.keySet()) {
                if (grouped.get(title).size() >= 2) validTitles.add(title);
            }

            if (validTitles.isEmpty()) {
                Toast.makeText(this, "Nedovoljno spojnica sa istom temom",
                        Toast.LENGTH_SHORT).show();
                resetUI();
                return;
            }

            Collections.shuffle(validTitles);
            List<SpojnicaModel> variants = grouped.get(validTitles.get(0));
            Collections.shuffle(variants);

            List<Map<String, Object>> allAsocData;
            if (aSnap.size() < 2) {
                allAsocData = getDefaultAsocijacije();
                for (Map<String, Object> a : allAsocData) {
                    db.collection("asocijacije").add(a);
                }
            } else {
                allAsocData = new ArrayList<>();
                for (DocumentSnapshot doc : aSnap.getDocuments()) {
                    allAsocData.add(doc.getData());
                }
            }
            Collections.shuffle(allAsocData);

            roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            Map<String, Object> room = new HashMap<>();

            // Osnovni podaci
            room.put("player1Id", currentUserId);
            room.put("player1Name", currentUserName);
            room.put("player1Avatar", currentUserAvatar);
            room.put("player2Id", null);
            room.put("player2Name", null);
            room.put("player2Avatar", null);
            room.put("player1Score", 0);
            room.put("player2Score", 0);
            room.put("status", "waiting");

            // Ko zna zna
            room.put("currentGame", "koZnaZna");
            room.put("koZnaZnaQuestions", selectedQuestions);
            room.put("currentQuestionIndex", 0);
            room.put("questionStartTime", 0L);
            room.put("answers_q0", new HashMap<String, Object>());
            room.put("answers_q1", new HashMap<String, Object>());
            room.put("answers_q2", new HashMap<String, Object>());
            room.put("answers_q3", new HashMap<String, Object>());
            room.put("answers_q4", new HashMap<String, Object>());

            // Korak po korak
            room.put("korak_phase", "p1_playing");
            room.put("korak_currentStep", -1);
            room.put("korak_currentStepText", "");

            // Spojnice
            room.put("spojnica1", variants.get(0));
            room.put("spojnica2", variants.get(1));
            room.put("spojnice_turn", "p1");
            room.put("spojnice_currentLeftIndex", 0);
            room.put("spojnice_matchedRightIndices",
                    java.util.Arrays.asList(-1, -1, -1, -1, -1));
            room.put("spojnice_whoMatched",
                    java.util.Arrays.asList("", "", "", "", ""));

            // Asocijacije
            room.put("asocijacija1", allAsocData.get(0));
            room.put("asocijacija2", allAsocData.get(1));
            room.put("asoc_currentRound", 1L);
            room.put("asoc_turn", "p1");
            room.put("asoc_solvedA", false);
            room.put("asoc_solvedB", false);
            room.put("asoc_solvedV", false);
            room.put("asoc_solvedG", false);
            room.put("asoc_solvedFinal", false);
            Map<String, Object> asocOpened = new HashMap<>();
            for (int i = 0; i < 4; i++)
                asocOpened.put(String.valueOf(i), Arrays.asList(false, false, false, false));
            room.put("asoc_opened", asocOpened);

            java.util.Random rand = new java.util.Random();
            List<Integer> skockoTarget1 = new ArrayList<>();
            List<Integer> skockoTarget2 = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                skockoTarget1.add(rand.nextInt(6));
                skockoTarget2.add(rand.nextInt(6));
            }
            room.put("skocko_target1", skockoTarget1);
            room.put("skocko_target2", skockoTarget2);
            room.put("skocko_currentRound", 1);
            room.put("skocko_turn", "p1");
            room.put("skocko_attempts", new ArrayList<>());
            room.put("skocko_isSteal", false);

            db.collection("gameRooms").document(roomId).set(room)
                    .addOnSuccessListener(aVoid -> {
                        tvRoomId.setText("Sifra sobe: " + roomId);
                        tvRoomId.setVisibility(View.VISIBLE);
                        tvStatus.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                        listenForOpponent(true);
                    });
        });
    }

    private List<Map<String, Object>> getDefaultAsocijacije() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> a1 = new HashMap<>();
        a1.put("kolonaA", Arrays.asList("PAPIR", "OLOVKA", "SKOLA", "DJAK"));
        a1.put("resenjeA", "KNJIGA");
        a1.put("kolonaB", Arrays.asList("GLUMAC", "SCENA", "DRAMA", "MASKA"));
        a1.put("resenjeB", "POZORISTE");
        a1.put("kolonaV", Arrays.asList("KLAVIR", "NOTA", "PESMA", "PEVAC"));
        a1.put("resenjeV", "MUZIKA");
        a1.put("kolonaG", Arrays.asList("SLIKA", "MUZEJ", "BOJA", "CETKICA"));
        a1.put("resenjeG", "UMETNOST");
        a1.put("konacnoResenje", "KULTURA");
        list.add(a1);

        Map<String, Object> a2 = new HashMap<>();
        a2.put("kolonaA", Arrays.asList("KRALJ", "KRALJICA", "DVOR", "KRUNA"));
        a2.put("resenjeA", "MONARHIJA");
        a2.put("kolonaB", Arrays.asList("TOP", "LOVAC", "PESAK", "SKAKAC"));
        a2.put("resenjeB", "SAH");
        a2.put("kolonaV", Arrays.asList("GLAVA", "TELO", "RUKE", "NOGE"));
        a2.put("resenjeV", "COVEK");
        a2.put("kolonaG", Arrays.asList("ZIMA", "SNEG", "LED", "MRAZ"));
        a2.put("resenjeG", "HLADNOCA");
        a2.put("konacnoResenje", "DRZAVA");
        list.add(a2);

        Map<String, Object> a3 = new HashMap<>();
        a3.put("kolonaA", Arrays.asList("GOLMAN", "MREZA", "OFSAJD", "PENAL"));
        a3.put("resenjeA", "FUDBAL");
        a3.put("kolonaB", Arrays.asList("KOS", "LOPTA", "PARKET", "TRENER"));
        a3.put("resenjeB", "KOSARKA");
        a3.put("kolonaV", Arrays.asList("REKET", "SET", "LOB", "SERVIS"));
        a3.put("resenjeV", "TENIS");
        a3.put("kolonaG", Arrays.asList("BAZEN", "STAZA", "PLIVAC", "KAPA"));
        a3.put("resenjeG", "PLIVANJE");
        a3.put("konacnoResenje", "SPORT");
        list.add(a3);

        Map<String, Object> a4 = new HashMap<>();
        a4.put("kolonaA", Arrays.asList("BUKVA", "HRAST", "BOR", "JELA"));
        a4.put("resenjeA", "DRVO");
        a4.put("kolonaB", Arrays.asList("ORAO", "LASTAVICA", "GOLUB", "VRABAC"));
        a4.put("resenjeB", "PTICA");
        a4.put("kolonaV", Arrays.asList("VUK", "VEVERICA", "MEDVED", "JELEN"));
        a4.put("resenjeV", "ZIVOTINJA");
        a4.put("kolonaG", Arrays.asList("DUNAV", "SAVA", "NERETVA", "TISA"));
        a4.put("resenjeG", "REKA");
        a4.put("konacnoResenje", "PRIRODA");
        list.add(a4);

        return list;
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
                updates.put("player2Avatar", currentUserAvatar);
                updates.put("status", "playing");
                roomRef.update(updates)
                        .addOnSuccessListener(aVoid -> listenForOpponent(false));
            } else {
                Toast.makeText(this, "Soba nije dostupna", Toast.LENGTH_SHORT).show();
                resetUI();
            }
        });
    }

    private void listenForOpponent(boolean isPlayer1) {
        roomListener = db.collection("gameRooms").document(roomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()
                            && "playing".equals(snapshot.getString("status"))) {
                        if (roomListener != null) roomListener.remove();

                        String currentGame = snapshot.getString("currentGame");
                        Intent intent = new Intent();

                        if ("koZnaZna".equals(currentGame)) {
                            intent.setClass(this, KoZnaZnaActivity.class);
                        } else if ("spojnice".equals(currentGame)) {
                            intent.setClass(this, SpojniceActivity.class);
                        } else if ("asocijacije".equals(currentGame)) {
                            intent.setClass(this, AsocijacijeActivity.class);
                        } else if ("skocko".equals(currentGame)) {
                            intent.setClass(this, SkockoActivity.class);
                        } else if ("korakPoKorak".equals(currentGame)) {
                            intent.setClass(this, KorakPoKorakActivity.class);
                        } else if ("mojBroj".equals(currentGame)) {
                            intent.setClass(this, MojBrojActivity.class);
                        } else {
                            intent.setClass(this, KoZnaZnaActivity.class);
                        }

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