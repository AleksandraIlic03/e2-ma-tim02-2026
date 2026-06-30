package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Spec 9b: Igrač koji postavlja izazov licitira broj zvezda (max 10) i tokena (max 2).
 */
public class CreateChallengeActivity extends AppCompatActivity {

    private Slider sliderStars, sliderTokens;
    private TextView tvStarsValue, tvTokensValue, tvUserStars, tvUserTokens;
    private MaterialButton btnCreate;

    private FirebaseFirestore db;
    private String currentUserId;
    private String currentUserName = "Igrač";
    private String currentUserAvatar = "ic_user";
    private String region;

    private long userStars = 0;
    private long userTokens = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_challenge);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();
        region = getIntent().getStringExtra("regionName");

        if (currentUserId == null) {
            Toast.makeText(this, "Moraš biti ulogovan da bi pokrenuo izazov.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Novi izazov");
        }

        initViews();
        loadUserData();
    }

    private void initViews() {
        sliderStars = findViewById(R.id.sliderStars);
        sliderTokens = findViewById(R.id.sliderTokens);
        tvStarsValue = findViewById(R.id.tvStarsValue);
        tvTokensValue = findViewById(R.id.tvTokensValue);
        tvUserStars = findViewById(R.id.tvUserStars);
        tvUserTokens = findViewById(R.id.tvUserTokens);
        btnCreate = findViewById(R.id.btnCreateChallenge);

        sliderStars.setValueFrom(0);
        sliderStars.setValueTo(ChallengeManager.MAX_STAKE_STARS);
        sliderStars.setStepSize(1);
        sliderStars.addOnChangeListener((slider, value, fromUser) ->
                tvStarsValue.setText(String.valueOf((int) value)));

        sliderTokens.setValueFrom(0);
        sliderTokens.setValueTo(ChallengeManager.MAX_STAKE_TOKENS);
        sliderTokens.setStepSize(1);
        sliderTokens.addOnChangeListener((slider, value, fromUser) ->
                tvTokensValue.setText(String.valueOf((int) value)));

        btnCreate.setOnClickListener(v -> createChallenge());
    }

    private void loadUserData() {
        db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    currentUserName = doc.getString("username") != null ? doc.getString("username") : "Igrač";
                    String avatar = doc.getString("avatarUrl");
                    if (avatar != null && !avatar.isEmpty()) currentUserAvatar = avatar;

                    userStars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                    userTokens = doc.getLong("tokens") != null ? doc.getLong("tokens") : 0;

                    tvUserStars.setText("Imaš: " + userStars + " ⭐");
                    tvUserTokens.setText("Imaš: " + userTokens + " 🎟️");

                    // Ograniči slidere na ono što igrač realno ima
                    float maxStars = Math.max(0, Math.min(ChallengeManager.MAX_STAKE_STARS, (int) userStars));
                    float maxTokens = Math.max(0, Math.min(ChallengeManager.MAX_STAKE_TOKENS, (int) userTokens));

                    sliderStars.setValueTo(maxStars > 0 ? maxStars : 1.0f);
                    sliderStars.setEnabled(maxStars > 0);
                    
                    sliderTokens.setValueTo(maxTokens > 0 ? maxTokens : 1.0f);
                    sliderTokens.setEnabled(maxTokens > 0);
                });
    }

    private void createChallenge() {
        int stakeStars = (int) sliderStars.getValue();
        int stakeTokens = (int) sliderTokens.getValue();

        if (stakeStars > userStars || stakeTokens > userTokens) {
            Toast.makeText(this, "Nemaš dovoljno zvezda ili tokena.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (stakeStars == 0 && stakeTokens == 0) {
            Toast.makeText(this, "Moraš uložiti bar nešto da pokreneš izazov.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCreate.setEnabled(false);

        ChallengeManager.createChallenge(currentUserId, currentUserName, currentUserAvatar,
                region, stakeStars, stakeTokens, new ChallengeManager.ChallengeCallback() {
                    @Override
                    public void onSuccess(String challengeId) {
                        Toast.makeText(CreateChallengeActivity.this,
                                "Izazov je kreiran! Čekaš protivnike.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(CreateChallengeActivity.this, ChallengeListActivity.class);
                        intent.putExtra("regionName", region);
                        intent.putExtra("openChallengeId", challengeId);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        btnCreate.setEnabled(true);
                        Toast.makeText(CreateChallengeActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}