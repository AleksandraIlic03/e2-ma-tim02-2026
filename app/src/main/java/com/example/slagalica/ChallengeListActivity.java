package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spec 9a: Prikaz aktivnih izazova u regionu, sa mogućnošću pridruživanja
 * (maksimalno 3 dodatna igrača po izazovu).
 */
public class ChallengeListActivity extends AppCompatActivity {

    private RecyclerView rvChallenges;
    private MaterialButton btnNewChallenge;
    private TextView tvEmpty;

    private FirebaseFirestore db;
    private String currentUserId;
    private String currentUserName = "Igrač";
    private String currentUserAvatar = "ic_user";
    private String region;
    private ListenerRegistration challengesListener;

    private final List<Map<String, Object>> challenges = new ArrayList<>();
    private final List<String> challengeIds = new ArrayList<>();
    private ChallengeAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_list);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();
        region = getIntent().getStringExtra("regionName");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Izazovi — " + region);
        }

        rvChallenges = findViewById(R.id.rvChallenges);
        btnNewChallenge = findViewById(R.id.btnNewChallenge);
        tvEmpty = findViewById(R.id.tvEmptyChallenges);

        rvChallenges.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChallengeAdapter();
        rvChallenges.setAdapter(adapter);

        btnNewChallenge.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateChallengeActivity.class);
            intent.putExtra("regionName", region);
            startActivity(intent);
        });

        loadUserInfo();
        attachChallengesListener();
    }

    private void loadUserInfo() {
        if (currentUserId == null) return;
        db.collection("users").document(currentUserId).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;
            currentUserName = doc.getString("username") != null ? doc.getString("username") : "Igrač";
            String avatar = doc.getString("avatarUrl");
            if (avatar != null && !avatar.isEmpty()) currentUserAvatar = avatar;
        });
    }

    private void attachChallengesListener() {
        challengesListener = db.collection("challenges")
                .whereEqualTo("region", region)
                .whereEqualTo("status", "open")
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null) return;
                    challenges.clear();
                    challengeIds.clear();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        challenges.add(doc.getData());
                        challengeIds.add(doc.getId());
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(challenges.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void joinChallenge(String challengeId) {
        if (currentUserId == null) {
            Toast.makeText(this, "Moraš biti ulogovan.", Toast.LENGTH_SHORT).show();
            return;
        }
        ChallengeManager.joinChallenge(challengeId, currentUserId, currentUserName, currentUserAvatar,
                new ChallengeManager.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(ChallengeListActivity.this, "Pridružio si se izazovu!", Toast.LENGTH_SHORT).show();
                        openChallengeGame(challengeId);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChallengeListActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openChallengeGame(String challengeId) {
        Intent intent = new Intent(this, ChallengeGameActivity.class);
        intent.putExtra("challengeId", challengeId);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (challengesListener != null) challengesListener.remove();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private class ChallengeAdapter extends RecyclerView.Adapter<ChallengeAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_challenge, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Map<String, Object> challenge = challenges.get(position);
            String challengeId = challengeIds.get(position);

            String creatorName = (String) challenge.get("creatorName");
            long stakeStars = challenge.get("stakeStars") != null ? ((Number) challenge.get("stakeStars")).longValue() : 0;
            long stakeTokens = challenge.get("stakeTokens") != null ? ((Number) challenge.get("stakeTokens")).longValue() : 0;
            Map<String, Object> participants = (Map<String, Object>) challenge.get("participants");
            int participantCount = participants != null ? participants.size() : 1;
            long maxParticipants = challenge.get("maxParticipants") != null
                    ? ((Number) challenge.get("maxParticipants")).longValue() : 4;

            holder.tvCreator.setText(creatorName + " te izaziva!");
            holder.tvStake.setText("Ulog: " + stakeStars + " ⭐  " + stakeTokens + " 🎟️");
            holder.tvParticipants.setText(participantCount + "/" + maxParticipants + " igrača");

            boolean isCreator = currentUserId != null && currentUserId.equals(challenge.get("creatorId"));
            boolean alreadyJoined = participants != null && currentUserId != null && participants.containsKey(currentUserId);
            boolean isFull = participantCount >= maxParticipants;

            if (isCreator) {
                holder.btnJoin.setText("ČEKAŠ IGRAČE");
                holder.btnJoin.setEnabled(false);
            } else if (alreadyJoined) {
                holder.btnJoin.setText("VEĆ SI PRIJAVLJEN");
                holder.btnJoin.setEnabled(false);
            } else if (isFull) {
                holder.btnJoin.setText("POPUNJENO");
                holder.btnJoin.setEnabled(false);
            } else {
                holder.btnJoin.setText("PRIDRUŽI SE");
                holder.btnJoin.setEnabled(true);
                holder.btnJoin.setOnClickListener(v -> joinChallenge(challengeId));
            }
        }

        @Override
        public int getItemCount() {
            return challenges.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCreator, tvStake, tvParticipants;
            MaterialButton btnJoin;

            VH(View itemView) {
                super(itemView);
                tvCreator = itemView.findViewById(R.id.tvChallengeCreator);
                tvStake = itemView.findViewById(R.id.tvChallengeStake);
                tvParticipants = itemView.findViewById(R.id.tvChallengeParticipants);
                btnJoin = itemView.findViewById(R.id.btnJoinChallenge);
            }
        }
    }
}