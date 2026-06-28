package com.example.slagalica;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class ProfileActivity extends AppCompatActivity {

    private ImageView ivAvatar, ivLeagueIcon, ivQrCode;
    private TextView tvUsername, tvEmail, tvTokens, tvStars, tvRegion, tvLeagueName;
    private CardView btnEditAvatar;
    private View avatarFrame;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userId;

    private final int[] avatarResources = {
            R.drawable.avatar_1,
            R.drawable.avatar_2,
            R.drawable.avatar_3,
            R.drawable.avatar_4,
            R.drawable.avatar_5,
            R.drawable.avatar_6,
            R.drawable.avatar_7,
            R.drawable.avatar_8,
            R.drawable.avatar_9,
            R.drawable.ic_user // default ikonica
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        }

        initViews();
        loadUserData();

        if (btnEditAvatar != null) {
            btnEditAvatar.setOnClickListener(v -> showAvatarSelectionDialog());
        }

        setupButtons();
    }

    private void initViews() {
        ivAvatar = findViewById(R.id.ivAvatar);
        tvUsername = findViewById(R.id.tvUsername);
        tvEmail = findViewById(R.id.tvEmail);
        tvTokens = findViewById(R.id.tvTokens);
        tvStars = findViewById(R.id.tvStars);
        tvRegion = findViewById(R.id.tvRegion);
        tvLeagueName = findViewById(R.id.tvLeagueName);
        ivLeagueIcon = findViewById(R.id.ivLeagueIcon);
        btnEditAvatar = findViewById(R.id.btnEditAvatar);
        avatarFrame = findViewById(R.id.avatarFrame);
        ivQrCode = findViewById(R.id.ivQrCode);
    }

    private void loadUserData() {
        if (userId == null) return;
        
        generateQrCode(userId);

        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        if (tvUsername != null) tvUsername.setText(documentSnapshot.getString("username"));
                        if (tvEmail != null) tvEmail.setText(documentSnapshot.getString("email"));
                        if (tvTokens != null) tvTokens.setText(String.valueOf(documentSnapshot.getLong("tokens")));
                        if (tvStars != null) tvStars.setText(String.valueOf(documentSnapshot.getLong("stars")));
                        
                        Long league = documentSnapshot.getLong("league");
                        updateLeagueInfo(league != null ? league : 0);

                        String region = documentSnapshot.getString("region");
                        if (tvRegion != null) tvRegion.setText(region);
                        if (region != null) checkRegionAwards(region);

                        // Učitavanje sačuvanog avatara
                        String avatarName = documentSnapshot.getString("avatarUrl");
                        if (avatarName != null && !avatarName.isEmpty()) {
                            int resId = getResources().getIdentifier(avatarName, "drawable", getPackageName());
                            if (resId != 0 && ivAvatar != null) {
                                ivAvatar.setImageResource(resId);
                                ivAvatar.setPadding(0, 0, 0, 0); 
                                ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Greška pri učitavanju podataka", Toast.LENGTH_SHORT).show());
    }

    private void generateQrCode(String content) {
        if (ivQrCode == null) return;
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            android.graphics.Bitmap bitmap = barcodeEncoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 400, 400);
            ivQrCode.setImageBitmap(bitmap);
            ivQrCode.setPadding(0, 0, 0, 0);
            ivQrCode.setBackgroundColor(android.graphics.Color.WHITE);
            ivQrCode.setImageTintList(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkRegionAwards(String userRegion) {
        db.collection("system").document("cycleState").get().addOnSuccessListener(doc -> {
            if (doc.exists() && doc.contains("topRegionsLastMonth")) {
                java.util.List<String> topRegions = (java.util.List<String>) doc.get("topRegionsLastMonth");
                if (topRegions != null && avatarFrame != null) {
                    int rank = topRegions.indexOf(userRegion);
                    if (rank == 0) { // Zlato
                        avatarFrame.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFD700")));
                    } else if (rank == 1) { // Srebro
                        avatarFrame.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#C0C0C0")));
                    } else if (rank == 2) { // Bronza
                        avatarFrame.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#CD7F32")));
                    }
                }
            }
        });
    }

    private void updateLeagueInfo(long league) {
        if (tvLeagueName == null || ivLeagueIcon == null) return;

        String leagueName;
        int leagueIcon;

        switch ((int) league) {
            case 1:
                leagueName = "Vuk";
                leagueIcon = R.drawable.ic_league_wolf;
                break;
            case 2:
                leagueName = "Medved";
                leagueIcon = R.drawable.ic_league_bear;
                break;
            case 3:
                leagueName = "Lav";
                leagueIcon = R.drawable.ic_league_lion;
                break;
            case 4:
                leagueName = "Orao";
                leagueIcon = R.drawable.ic_league_eagle;
                break;
            case 5:
                leagueName = "Zmaj";
                leagueIcon = R.drawable.ic_league_dragon;
                break;
            default:
                leagueName = "Miš";
                leagueIcon = R.drawable.ic_league_mouse;
                break;
        }

        tvLeagueName.setText(leagueName);
        ivLeagueIcon.setImageResource(leagueIcon);
    }

    private void showAvatarSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Izaberi novi avatar");

        GridView gridView = new GridView(this);
        gridView.setNumColumns(3);
        gridView.setPadding(30, 30, 30, 30);
        gridView.setVerticalSpacing(20);
        gridView.setHorizontalSpacing(20);

        AvatarAdapter adapter = new AvatarAdapter(this, avatarResources);
        gridView.setAdapter(adapter);

        builder.setView(gridView);
        AlertDialog dialog = builder.create();

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            int selectedResId = avatarResources[position];
            if (ivAvatar != null) {
                ivAvatar.setImageResource(selectedResId);
                ivAvatar.setPadding(0, 0, 0, 0);
                ivAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }

            String resourceName = getResources().getResourceEntryName(selectedResId);
            updateAvatarInDatabase(resourceName);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateAvatarInDatabase(String avatarName) {
        if (userId == null) return;

        db.collection("users").document(userId)
                .update("avatarUrl", avatarName)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Avatar uspešno promenjen!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Greška pri čuvanju u bazu.", Toast.LENGTH_SHORT).show());
    }

    private void setupButtons() {
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                mAuth.signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        MaterialButton btnStatistics = findViewById(R.id.btnStatistics);
        if (btnStatistics != null) {
            btnStatistics.setOnClickListener(v -> {
                Intent intent = new Intent(this, StatisticsActivity.class);
                startActivity(intent);
            });
        }

        MaterialButton btnManagePassword = findViewById(R.id.btnManagePassword);
        if (btnManagePassword != null) {
            btnManagePassword.setOnClickListener(v -> {
                Intent intent = new Intent(this, ResetPasswordActivity.class);
                startActivity(intent);
            });
        }
    }

    private static class AvatarAdapter extends BaseAdapter {
        private final Context context;
        private final int[] avatars;

        public AvatarAdapter(Context context, int[] avatars) {
            this.context = context;
            this.avatars = avatars;
        }

        @Override
        public int getCount() { return avatars.length; }
        @Override
        public Object getItem(int position) { return avatars[position]; }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CardView cardView;
            ImageView imageView;

            if (convertView == null) {
                // Kreiramo kontejner (CardView) koji pravi krug
                cardView = new CardView(context);
                int size = 180; // veličina ikonice u gridu
                cardView.setLayoutParams(new GridView.LayoutParams(size, size));
                cardView.setRadius(size / 2f); // Poluprečnik za krug
                cardView.setCardElevation(0);
                cardView.setCardBackgroundColor(android.graphics.Color.WHITE);
                cardView.setBackgroundResource(R.drawable.avatar_frame); // Ljubicasti okvir

                imageView = new ImageView(context);
                imageView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                cardView.addView(imageView);
            } else {
                cardView = (CardView) convertView;
                imageView = (ImageView) cardView.getChildAt(0);
            }

            imageView.setImageResource(avatars[position]);
            return cardView;
        }
    }
}
