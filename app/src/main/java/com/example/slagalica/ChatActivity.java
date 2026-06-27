package com.example.slagalica;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.adapters.ChatAdapter;
import com.example.slagalica.models.ChatMessage;
import com.example.slagalica.models.Notification;
import com.example.slagalica.models.NotificationRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ChatAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration chatListener;

    private String currentUserId;
    private String currentUserName;
    private String currentRegion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUserId = auth.getCurrentUser() != null
                ? auth.getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "Nisi ulogovan.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadUserAndStartChat();
    }

    private void initViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etMessage  = findViewById(R.id.etMessage);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);

        adapter = new ChatAdapter(messages, currentUserId);
        rvMessages.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());
    }

    private void loadUserAndStartChat() {
        db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    currentUserName = doc.getString("username");
                    if (currentUserName == null) currentUserName = "Igrač";

                    currentRegion = doc.getString("region");
                    if (currentRegion == null || currentRegion.isEmpty()) {
                        Toast.makeText(this, "Nisi u nijednom regionu.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    TextView tvRegion = findViewById(R.id.tvRegionLabel);
                    tvRegion.setText(currentRegion);

                    // Spec 8e: proveri propuštene poruke od poslednjeg puta
                    checkMissedMessages(doc);

                    // Upiši lastSeenChat = sada
                    db.collection("users").document(currentUserId)
                            .update("lastSeenChat", Timestamp.now());

                    attachChatListener();
                });
    }

    /**
     * Spec 8e (bez Cloud Functions):
     * Poruke novije od lastSeenChat → lokalna notifikacija.
     */
    private void checkMissedMessages(DocumentSnapshot userDoc) {
        Timestamp lastSeen = userDoc.getTimestamp("lastSeenChat");
        if (lastSeen == null) return; // prvi put, nema propuštenih

        db.collection("chats")
                .document(currentRegion)
                .collection("messages")
                .whereGreaterThan("timestamp", lastSeen)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) return;

                    int count = 0;
                    String lastName = "";
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ChatMessage msg = doc.toObject(ChatMessage.class);
                        if (msg == null || msg.getSenderId().equals(currentUserId)) continue;
                        count++;
                        lastName = msg.getSenderName();
                    }

                    if (count == 0) return;

                    String title = "Čet — " + currentRegion;
                    String body  = count == 1
                            ? lastName + " ti je poslao poruku"
                            : count + " novih poruka u regionalnom četu";

                    NotificationHelper.sendRealNotification(
                            this, title, body, NotificationHelper.CHANNEL_CHAT);
                });
    }

    private void attachChatListener() {
        // Spec 8b: real-time
        chatListener = db.collection("chats")
                .document(currentRegion)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    messages.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ChatMessage msg = doc.toObject(ChatMessage.class);
                        if (msg != null) messages.add(msg);
                    }
                    adapter.notifyDataSetChanged();

                    if (!messages.isEmpty()) {
                        rvMessages.scrollToPosition(messages.size() - 1);
                    }
                });
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        // Spec 8c: ime pošiljaoca, datum i vreme
        ChatMessage msg = new ChatMessage(
                currentUserId,
                currentUserName,
                text,
                Timestamp.now(),
                currentRegion
        );

        db.collection("chats")
                .document(currentRegion)
                .collection("messages")
                .add(msg)
                .addOnSuccessListener(ref -> {
                    etMessage.setText("");

                    // Osvezi lastSeenChat
                    db.collection("users").document(currentUserId)
                            .update("lastSeenChat", Timestamp.now());

                    // Spec 11b: istorija notifikacija
                    String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(new Date());
                    NotificationRepository.addNotification(new Notification(
                            String.valueOf(System.currentTimeMillis()),
                            "Poruka poslata",
                            "Tvoja poruka u četu — " + currentRegion,
                            time,
                            "chat",
                            true
                    ));
                })
                .addOnFailureListener(ex ->
                        Toast.makeText(this, "Greška pri slanju.", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUserId != null) {
            db.collection("users").document(currentUserId)
                    .update("lastSeenChat", Timestamp.now());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListener != null) chatListener.remove();
    }
}