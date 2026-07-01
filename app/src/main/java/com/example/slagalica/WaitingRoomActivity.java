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

        // Ako je automatski ulazak (turnir / matchmaking / poziv prijatelja), sakrij setup UI odmah
        if (getIntent().hasExtra("autoJoinRoomId")
                || getIntent().getBooleanExtra("isFriendly", false)
                || getIntent().getBooleanExtra("autoCreate", false)
                || getIntent().getBooleanExtra("autoMatch", false)) {
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

        // Igra kod prijatelja: napravi friendly sobu i posalji poziv
        if (getIntent().getBooleanExtra("isFriendly", false)) {
            createRoom(true, false);
            return;
        }

        // Turnir: tokeni su vec oduzeti, preskoci proveru
        if (getIntent().getBooleanExtra("autoCreate", false)) {
            doCreateRoom(false, false);
            return;
        }

        // Nasumicno uparivanje (Matchmaking)
        if (getIntent().getBooleanExtra("autoMatch", false)) {
            startMatchmaking();
        }
    }

    private void startMatchmaking() {
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("Traženje protivnika...");

        // Atomicno preuzmi prvu slobodnu sobu ili kreiraj novu.
        // Transaction sprecava race condition gde dva igraca uzmu istu sobu istovremeno.
        db.collection("gameRooms")
                .whereEqualTo("status", "waiting")
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    DocumentSnapshot target = null;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        if (!Boolean.TRUE.equals(doc.getBoolean("isPublic"))) continue;
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
                        DocumentSnapshot fresh = transaction.get(
                                db.collection("gameRooms").document(targetRoomId));
                        String status = fresh.getString("status");
                        String p1Id = fresh.getString("player1Id");
                        if (!"waiting".equals(status) || currentUserId.equals(p1Id)) {
                            return null;
                        }
                        transaction.update(
                                db.collection("gameRooms").document(targetRoomId),
                                "player2Id", currentUserId,
                                "player2Name", currentUserName,
                                "player2Avatar", currentUserAvatar,
                                "status", "playing"
                        );
                        return targetRoomId;
                    }).addOnSuccessListener(result -> {
                        if (result == null) {
                            createRoom(false, true);
                            return;
                        }
                        // Oduzmi token p2-u (isInGame postavlja SlagalicaApp centralno)
                        db.collection("users").document(currentUserId)
                                .update("tokens", FieldValue.increment(-1));
                        roomId = targetRoomId;
                        listenForOpponent(false);
                    }).addOnFailureListener(e -> createRoom(false, true));
                })
                .addOnFailureListener(e -> createRoom(false, true));
    }

    private void createRoom(boolean isFriendly, boolean isPublic) {
        if (!isFriendly && mAuth.getCurrentUser() != null) {
            db.collection("users").document(currentUserId).get()
                    .addOnSuccessListener(userDoc -> {
                        long tokens = userDoc.getLong("tokens") != null ? userDoc.getLong("tokens") : 0;
                        if (tokens <= 0) {
                            Toast.makeText(this, "Nemaš tokena za igranje!", Toast.LENGTH_SHORT).show();
                            resetUI();
                            return;
                        }
                        db.collection("users").document(currentUserId)
                                .update("tokens", FieldValue.increment(-1))
                                .addOnSuccessListener(v -> doCreateRoom(isFriendly, isPublic));
                    });
            return;
        }
        doCreateRoom(isFriendly, isPublic);
    }

    private void doCreateRoom(boolean isFriendly, boolean isPublic) {
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
                Toast.makeText(this, "Nedovoljno podataka u bazi.", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Nedovoljno spojnica.", Toast.LENGTH_SHORT).show();
                resetUI();
                return;
            }

            Collections.shuffle(validTitles);
            List<SpojnicaModel> variants = grouped.get(validTitles.get(0));
            Collections.shuffle(variants);

            List<Map<String, Object>> allAsocData = new ArrayList<>();
            for (DocumentSnapshot doc : aSnap.getDocuments()) {
                allAsocData.add(doc.getData());
            }
            if (allAsocData.size() < 2) allAsocData = getDefaultAsocijacije();
            Collections.shuffle(allAsocData);

            roomId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            Map<String, Object> room = new HashMap<>();
            room.put("player1Id", currentUserId);
            room.put("player1Name", currentUserName);
            room.put("player1Avatar", currentUserAvatar);
            room.put("player2Id", null);
            room.put("player1Score", 0);
            room.put("player2Score", 0);
            room.put("playerLeft", "");
            room.put("status", "waiting");
            room.put("isFriendly", isFriendly);
            room.put("isPublic", isPublic);

            room.put("currentGame", "koZnaZna");
            room.put("koZnaZnaQuestions", selectedQuestions);
            room.put("currentQuestionIndex", 0);
            room.put("questionStartTime", 0L);
            room.put("answers_q0", new HashMap<String, Object>());
            room.put("answers_q1", new HashMap<String, Object>());
            room.put("answers_q2", new HashMap<String, Object>());
            room.put("answers_q3", new HashMap<String, Object>());
            room.put("answers_q4", new HashMap<String, Object>());

            room.put("korak_phase", "p1_playing");
            room.put("korak_currentStep", -1);

            room.put("spojnica1", variants.get(0));
            room.put("spojnica2", variants.get(1));
            room.put("spojnice_turn", "p1");
            room.put("spojnice_currentLeftIndex", 0);
            room.put("spojnice_matchedRightIndices", Arrays.asList(-1, -1, -1, -1, -1));
            room.put("spojnice_whoMatched", Arrays.asList("", "", "", "", ""));

            room.put("asocijacija1", allAsocData.get(0));
            room.put("asocijacija2", allAsocData.get(1));
            room.put("asoc_currentRound", 1L);
            room.put("asoc_turn", "p1");
            Map<String, Object> asocOpened = new HashMap<>();
            for (int i = 0; i < 4; i++)
                asocOpened.put(String.valueOf(i), Arrays.asList(false, false, false, false));
            room.put("asoc_opened", asocOpened);

            java.util.Random rand = new java.util.Random();
            room.put("skocko_target1", Arrays.asList(rand.nextInt(6), rand.nextInt(6), rand.nextInt(6), rand.nextInt(6)));
            room.put("skocko_target2", Arrays.asList(rand.nextInt(6), rand.nextInt(6), rand.nextInt(6), rand.nextInt(6)));
            room.put("skocko_currentRound", 1);
            room.put("skocko_turn", "p1");
            room.put("skocko_attempts", new ArrayList<>());

            // Turnir info (null ako nije turnir)
            room.put("tournamentId", getIntent().getStringExtra("tournamentId"));
            room.put("tournamentWinnerKey", getIntent().getStringExtra("tournamentWinnerKey"));

            db.collection("gameRooms").document(roomId).set(room)
                    .addOnSuccessListener(aVoid -> {
                        // Ako je turnir soba, upisi roomId u turnir dokument
                        String tId = getIntent().getStringExtra("tournamentId");
                        String tKey = getIntent().getStringExtra("tournamentRoomKey");
                        if (tId != null && tKey != null) {
                            db.collection("tournaments").document(tId).update(tKey, roomId);
                        }

                        boolean isAuto = getIntent().getBooleanExtra("autoCreate", false);
                        tvRoomId.setText("Šifra sobe: " + roomId);
                        tvRoomId.setVisibility(isAuto ? View.GONE : View.VISIBLE);
                        tvStatus.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.VISIBLE);

                        String opponentId = getIntent().getStringExtra("opponentId");
                        if (isFriendly && opponentId != null) {
                            FriendsManager.sendGameInvite(opponentId, currentUserName, roomId, new FriendsManager.ActionCallback() {
                                @Override
                                public void onSuccess() {
                                    tvStatus.setText("Poziv poslat prijatelju...");
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    Toast.makeText(WaitingRoomActivity.this, "Greška pri slanju poziva", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else if (!isAuto) {
                            tvStatus.setText("Čekanje protivnika...");
                        }

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
        return list;
    }

    private void resetUI() {
        progressBar.setVisibility(View.GONE);
        btnCreateRoom.setEnabled(true);
        btnJoinRoom.setEnabled(true);
        btnCreateRoom.setVisibility(View.VISIBLE);
        btnJoinRoom.setVisibility(View.VISIBLE);
        if (etRoomId != null) etRoomId.setVisibility(View.VISIBLE);
    }

    private void joinRoom() {
        String inputId = etRoomId.getText().toString().trim().toUpperCase();
        if (inputId.isEmpty()) return;

        DocumentReference roomRef = db.collection("gameRooms").document(inputId);
        roomRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists() && "waiting".equals(snapshot.getString("status"))) {
                String p1Id = snapshot.getString("player1Id");
                if (p1Id != null && p1Id.equals(currentUserId)) {
                    Toast.makeText(this, "Ne možete igrati sami protiv sebe!", Toast.LENGTH_SHORT).show();
                    resetUI();
                    return;
                }

                roomId = inputId;
                boolean isFriendly = Boolean.TRUE.equals(snapshot.getBoolean("isFriendly"));
                boolean isTournamentRoom = snapshot.getString("tournamentId") != null;

                db.collection("users").document(currentUserId).get().addOnSuccessListener(userDoc -> {
                    long tokens = userDoc.getLong("tokens") != null ? userDoc.getLong("tokens") : 0;
                    if (!isFriendly && !isTournamentRoom && tokens < 1) {
                        Toast.makeText(this, "Nemaš tokena!", Toast.LENGTH_SHORT).show();
                        resetUI();
                        return;
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("player2Id", currentUserId);
                    updates.put("player2Name", currentUserName);
                    updates.put("player2Avatar", currentUserAvatar);
                    updates.put("status", "playing");
                    roomRef.update(updates).addOnSuccessListener(v -> {
                        // isInGame postavlja SlagalicaApp centralno
                        if (!isFriendly && !isTournamentRoom) {
                            db.collection("users").document(currentUserId)
                                    .update("tokens", FieldValue.increment(-1));
                        }
                        listenForOpponent(false);
                    });
                });
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

                        String firstGame = snapshot.getString("currentGame");
                        if (firstGame == null) firstGame = "koZnaZna";

                        Intent intent = new Intent();
                        if (firstGame.equals("koZnaZna")) {
                            intent.setClass(this, KoZnaZnaActivity.class);
                        } else {
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
    public void onBackPressed() {
        // Ako jos cekamo protivnika, obrisi sobu pre izlaska
        if (roomId != null) {
            db.collection("gameRooms").document(roomId).delete();
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) roomListener.remove();
    }
}