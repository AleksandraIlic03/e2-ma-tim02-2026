package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.models.SpojnicaModel;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

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

        db.collection("spojnice").get().addOnSuccessListener(queryDocumentSnapshots -> {
            Map<String, List<SpojnicaModel>> grouped = new HashMap<>();
            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                SpojnicaModel s = doc.toObject(SpojnicaModel.class);
                if (!grouped.containsKey(s.getTitle())) {
                    grouped.put(s.getTitle(), new ArrayList<>());
                }
                grouped.get(s.getTitle()).add(s);
            }

            List<String> validTitles = new ArrayList<>();
            for (String title : grouped.keySet()) {
                if (grouped.get(title).size() >= 2) {
                    validTitles.add(title);
                }
            }

            if (validTitles.isEmpty()) {
                Toast.makeText(this, "Nedovoljno spojnica sa istom temom u bazi", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                btnCreateRoom.setEnabled(true);
                btnJoinRoom.setEnabled(true);
                return;
            }

            Collections.shuffle(validTitles);
            String selectedTitle = validTitles.get(0);
            List<SpojnicaModel> variants = grouped.get(selectedTitle);
            Collections.shuffle(variants);
            SpojnicaModel s1 = variants.get(0);
            SpojnicaModel s2 = variants.get(1);

            roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            
            Map<String, Object> room = new HashMap<>();
            room.put("player1Id", currentUserId);
            room.put("player1Name", currentUserName);
            room.put("player1Score", 0);
            room.put("player2Id", null);
            room.put("player2Name", null);
            room.put("player2Score", 0);
            room.put("status", "waiting");
            room.put("currentRound", 1);
            room.put("turn", "p1");
            room.put("currentLeftIndex", 0);
            room.put("matchedRightIndices", java.util.Arrays.asList(-1, -1, -1, -1, -1));
            room.put("whoMatched", java.util.Arrays.asList("", "", "", "", ""));
            room.put("lastWrongIndex", -1);
            room.put("wrongClickTrigger", 0);
            room.put("roundStartTime", System.currentTimeMillis());
            room.put("spojnica1", s1);
            room.put("spojnica2", s2);

            db.collection("gameRooms").document(roomId).set(room)
                    .addOnSuccessListener(aVoid -> {
                        tvRoomId.setText("Šifra sobe: " + roomId);
                        tvRoomId.setVisibility(View.VISIBLE);
                        tvStatus.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                        listenForOpponent(true);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Greška pri kreiranju sobe", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        btnCreateRoom.setEnabled(true);
                        btnJoinRoom.setEnabled(true);
                    });
        });
    }

    private void joinRoom() {
        String inputId = etRoomId.getText().toString().trim().toUpperCase();
        if (inputId.isEmpty()) {
            etRoomId.setError("Unesite šifru");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnJoinRoom.setEnabled(false);
        btnCreateRoom.setEnabled(false);

        DocumentReference roomRef = db.collection("gameRooms").document(inputId);
        roomRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String status = documentSnapshot.getString("status");
                if ("waiting".equals(status)) {
                    roomId = inputId;
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("player2Id", currentUserId);
                    updates.put("player2Name", currentUserName);
                    updates.put("status", "playing");

                    roomRef.update(updates).addOnSuccessListener(aVoid -> {
                        listenForOpponent(false);
                    });
                } else {
                    Toast.makeText(this, "Soba je popunjena ili igra traje", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    btnJoinRoom.setEnabled(true);
                    btnCreateRoom.setEnabled(true);
                }
            } else {
                Toast.makeText(this, "Soba nije pronađena", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                btnJoinRoom.setEnabled(true);
                btnCreateRoom.setEnabled(true);
            }
        });
    }

    private void listenForOpponent(boolean isPlayer1) {
        roomListener = db.collection("gameRooms").document(roomId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
                        if ("playing".equals(status)) {
                            if (roomListener != null) roomListener.remove();
                            Intent intent = new Intent(this, SpojniceActivity.class);
                            intent.putExtra("roomId", roomId);
                            intent.putExtra("isPlayer1", isPlayer1);
                            startActivity(intent);
                            finish();
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) roomListener.remove();
    }
}
