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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
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

        // Ako je automatski (turnir ili matchmaking), sakrij UI odmah
        if (getIntent().hasExtra("autoJoinRoomId") || 
            getIntent().getBooleanExtra("autoCreate", false) || 
            getIntent().getBooleanExtra("autoMatch", false)) {
            hideSetupUI("Priprema meča...");
        }

        fetchUserData();

        btnCreateRoom.setOnClickListener(v -> createRoom(false, false));
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

    private void hideSetupUI(String message) {
        if (btnCreateRoom != null) btnCreateRoom.setVisibility(View.GONE);
        if (btnJoinRoom != null) btnJoinRoom.setVisibility(View.GONE);
        if (etRoomId != null) etRoomId.setVisibility(View.GONE);
        if (tvRoomId != null) tvRoomId.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(message);
        progressBar.setVisibility(View.VISIBLE);
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
                        handleAutoIntent();
                    });
        } else {
            handleAutoIntent();
        }
    }

    private void handleAutoIntent() {
        String autoJoinId = getIntent().getStringExtra("autoJoinRoomId");
        if (autoJoinId != null) {
            etRoomId.setText(autoJoinId);
            joinRoom();
            return;
        }
        if (getIntent().getBooleanExtra("autoCreate", false)) {
            doCreateRoom(false, false); // Turnir već oduzeo tokene — preskoči proveru
            return;
        }
        if (getIntent().getBooleanExtra("autoMatch", false)) {
            startMatchmaking();
        }
    }

    private void startMatchmaking() {
        db.collection("gameRooms")
                .whereEqualTo("status", "waiting")
                .whereEqualTo("isPublic", true)
                .limit(5)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    DocumentSnapshot target = null;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String p1Id = doc.getString("player1Id");
                        if (p1Id != null && !p1Id.equals(currentUserId)) {
                            target = doc;
                            break;
                        }
                    }

                    if (target == null) {
                        createRoom(false, true);
                        return;
                    }

                    final String targetRoomId = target.getId();
                    db.runTransaction(transaction -> {
                        DocumentSnapshot fresh = transaction.get(db.collection("gameRooms").document(targetRoomId));
                        if (!"waiting".equals(fresh.getString("status"))) return null;
                        transaction.update(db.collection("gameRooms").document(targetRoomId),
                                "player2Id", currentUserId,
                                "player2Name", currentUserName,
                                "player2Avatar", currentUserAvatar,
                                "status", "playing"
                        );
                        return targetRoomId;
                    }).addOnSuccessListener(result -> {
                        if (result == null) { createRoom(false, true); return; }
                        db.collection("users").document(currentUserId).update("tokens", FieldValue.increment(-1));
                        roomId = targetRoomId;
                        listenForOpponent(false);
                    }).addOnFailureListener(e -> createRoom(false, true));
                });
    }

    private void createRoom(boolean isFriendly, boolean isPublic) {
        if (!isFriendly && mAuth.getCurrentUser() != null) {
            db.collection("users").document(currentUserId).get().addOnSuccessListener(userDoc -> {
                if ((userDoc.getLong("tokens") != null ? userDoc.getLong("tokens") : 0) <= 0) {
                    Toast.makeText(this, "Nemaš tokena!", Toast.LENGTH_SHORT).show();
                    resetUI();
                } else {
                    db.collection("users").document(currentUserId).update("tokens", FieldValue.increment(-1))
                            .addOnSuccessListener(v -> doCreateRoom(isFriendly, isPublic));
                }
            });
        } else {
            doCreateRoom(isFriendly, isPublic);
        }
    }

    @SuppressWarnings("unchecked")
    private void doCreateRoom(boolean isFriendly, boolean isPublic) {
        Task<QuerySnapshot> questionsTask = db.collection("ko_zna_zna_questions").get();
        Task<QuerySnapshot> spojniceTask = db.collection("spojnice").get();
        Task<QuerySnapshot> asociacijeTask = db.collection("asocijacije").get();

        Tasks.whenAllSuccess(questionsTask, spojniceTask, asociacijeTask).addOnSuccessListener(results -> {
            List<KoZnaZnaQuestion> qList = new ArrayList<>();
            for (QueryDocumentSnapshot d : (QuerySnapshot)results.get(0)) qList.add(d.toObject(KoZnaZnaQuestion.class));
            Collections.shuffle(qList);

            List<Map<String, Object>> spList = new ArrayList<>();
            for (QueryDocumentSnapshot d : (QuerySnapshot)results.get(1)) spList.add(d.getData());
            Collections.shuffle(spList);

            List<Map<String, Object>> asocList = new ArrayList<>();
            for (QueryDocumentSnapshot d : (QuerySnapshot)results.get(2)) asocList.add(d.getData());
            Collections.shuffle(asocList);

            roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            Map<String, Object> room = new HashMap<>();
            room.put("player1Id", currentUserId);
            room.put("player1Name", currentUserName);
            room.put("player1Avatar", currentUserAvatar);
            room.put("status", "waiting");
            room.put("isFriendly", isFriendly);
            room.put("isPublic", isPublic);
            room.put("currentGame", "koZnaZna");
            room.put("koZnaZnaQuestions", qList.subList(0, Math.min(5, qList.size())));

            // Spojnice podaci za oba kruga (čita SpojniceActivity)
            if (spList.size() >= 2) {
                room.put("spojnica1", spList.get(0));
                room.put("spojnica2", spList.get(1));
            }
            // Asocijacije podaci za oba kruga (čita AsocijacijeActivity)
            if (asocList.size() >= 2) {
                room.put("asocijacija1", asocList.get(0));
                room.put("asocijacija2", asocList.get(1));
            }

            // Turnir info
            room.put("tournamentId", getIntent().getStringExtra("tournamentId"));
            room.put("tournamentWinnerKey", getIntent().getStringExtra("tournamentWinnerKey"));

            db.collection("gameRooms").document(roomId).set(room).addOnSuccessListener(aVoid -> {
                String tId = getIntent().getStringExtra("tournamentId");
                String tKey = getIntent().getStringExtra("tournamentRoomKey");
                if (tId != null && tKey != null) {
                    db.collection("tournaments").document(tId).update(tKey, roomId);
                }
                tvRoomId.setText("Šifra sobe: " + roomId);
                if (getIntent().getBooleanExtra("autoCreate", false)) tvRoomId.setVisibility(View.GONE);
                listenForOpponent(true);
            });
        });
    }

    private void joinRoom() {
        String inputId = etRoomId.getText().toString().trim().toUpperCase();
        if (inputId.isEmpty()) return;

        db.collection("gameRooms").document(inputId).get().addOnSuccessListener(snap -> {
            if (snap.exists() && "waiting".equals(snap.getString("status"))) {
                roomId = inputId;
                db.collection("gameRooms").document(roomId).update(
                        "player2Id", currentUserId,
                        "player2Name", currentUserName,
                        "player2Avatar", currentUserAvatar,
                        "status", "playing"
                ).addOnSuccessListener(v -> {
                    boolean isTournamentRoom = snap.getString("tournamentId") != null;
                    if (!Boolean.TRUE.equals(snap.getBoolean("isFriendly")) && !isTournamentRoom) {
                        db.collection("users").document(currentUserId).update("tokens", FieldValue.increment(-1));
                    }
                    listenForOpponent(false);
                });
            } else {
                Toast.makeText(this, "Soba nije dostupna", Toast.LENGTH_SHORT).show();
                resetUI();
            }
        });
    }

    private void listenForOpponent(boolean isP1) {
        roomListener = db.collection("gameRooms").document(roomId).addSnapshotListener((snap, e) -> {
            if (snap != null && snap.exists() && "playing".equals(snap.getString("status"))) {
                if (roomListener != null) roomListener.remove();
                Intent i = new Intent(this, KoZnaZnaActivity.class);
                i.putExtra("roomId", roomId);
                i.putExtra("isPlayer1", isP1);
                startActivity(i);
                finish();
            }
        });
    }

    private void resetUI() {
        progressBar.setVisibility(View.GONE);
        btnCreateRoom.setEnabled(true);
        btnJoinRoom.setEnabled(true);
        btnCreateRoom.setVisibility(View.VISIBLE);
        btnJoinRoom.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) roomListener.remove();
    }
}
