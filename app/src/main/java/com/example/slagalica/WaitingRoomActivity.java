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
        currentUserId = mAuth.getCurrentUser() != null
                ? mAuth.getCurrentUser().getUid()
                : UUID.randomUUID().toString();

        initViews();
        fetchUserName();

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

        Task<QuerySnapshot> questionsTask = db.collection("ko_zna_zna_questions").get();
        Task<QuerySnapshot> spojniceTask = db.collection("spojnice").get();

        Tasks.whenAllSuccess(questionsTask, spojniceTask).addOnSuccessListener(results -> {
            QuerySnapshot qSnap = (QuerySnapshot) results.get(0);
            QuerySnapshot sSnap = (QuerySnapshot) results.get(1);

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
            room.put("currentGame", "korakPoKorak");
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

                        if ("korakPoKorak".equals(currentGame)) {
                            intent.setClass(this, KorakPoKorakActivity.class);
                        } else if ("koZnaZna".equals(currentGame)) {
                            intent.setClass(this, KoZnaZnaActivity.class);
                        } else if ("spojnice".equals(currentGame)) {
                            intent.setClass(this, SpojniceActivity.class);
                        } else {
                            // Fallback na prvu igru ako je nesto nepoznato
                            intent.setClass(this, KorakPoKorakActivity.class);
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