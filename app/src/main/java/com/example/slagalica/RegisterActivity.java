package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etUsername, etPassword, etConfirmPassword;
    private AutoCompleteTextView dropdownRegion;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Random random = new Random();

    private static class BoundingBox {
        double minLat, maxLat, minLng, maxLng;
        BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
            this.minLat = minLat; this.maxLat = maxLat; this.minLng = minLng; this.maxLng = maxLng;
        }
    }

    private static final Map<String, BoundingBox> REGION_BOUNDS = new HashMap<>();
    static {
        REGION_BOUNDS.put("Beogradski region", new BoundingBox(44.6, 44.9, 20.2, 20.6));
        REGION_BOUNDS.put("Vojvodina", new BoundingBox(44.8, 46.2, 18.8, 21.5));
        REGION_BOUNDS.put("Šumadijski okrug", new BoundingBox(43.9, 44.4, 20.6, 21.2));
        REGION_BOUNDS.put("Podunavski okrug", new BoundingBox(44.3, 44.7, 20.8, 21.2));
        REGION_BOUNDS.put("Braničevski okrug", new BoundingBox(44.2, 44.8, 21.1, 21.9));
        REGION_BOUNDS.put("Pomoravski okrug", new BoundingBox(43.8, 44.3, 21.1, 21.7));
        REGION_BOUNDS.put("Borski okrug", new BoundingBox(44.0, 44.7, 21.8, 22.6));
        REGION_BOUNDS.put("Zaječarski okrug", new BoundingBox(43.5, 44.2, 21.8, 22.5));
        REGION_BOUNDS.put("Nišavski okrug", new BoundingBox(43.1, 43.6, 21.5, 22.1));
        REGION_BOUNDS.put("Toplički okrug", new BoundingBox(42.9, 43.4, 21.1, 21.6));
        REGION_BOUNDS.put("Pirotski okrug", new BoundingBox(42.8, 43.4, 22.3, 23.0));
        REGION_BOUNDS.put("Jablanički okrug", new BoundingBox(42.7, 43.1, 21.5, 22.4));
        REGION_BOUNDS.put("Pčinjski okrug", new BoundingBox(42.2, 42.7, 21.8, 22.6));
        REGION_BOUNDS.put("Rasinski okrug", new BoundingBox(43.2, 43.7, 20.9, 21.5));
        REGION_BOUNDS.put("Raški okrug", new BoundingBox(42.9, 43.6, 20.3, 21.0));
        REGION_BOUNDS.put("Moravički okrug", new BoundingBox(43.5, 44.1, 20.0, 20.6));
        REGION_BOUNDS.put("Zlatiborski okrug", new BoundingBox(43.2, 44.1, 19.3, 20.2));
        REGION_BOUNDS.put("Kolubarski okrug", new BoundingBox(44.1, 44.6, 19.7, 20.4));
        REGION_BOUNDS.put("Mačvanski okrug", new BoundingBox(44.2, 44.9, 19.1, 19.8));
    }

    private static final List<String> REGIONS = RegionUtils.ALL_REGIONS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        dropdownRegion = findViewById(R.id.dropdownRegion);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, REGIONS);
        dropdownRegion.setAdapter(adapter);
        dropdownRegion.setOnClickListener(v -> dropdownRegion.showDropDown());
        dropdownRegion.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dropdownRegion.showDropDown();
        });

        findViewById(R.id.tvLogin).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));

        findViewById(R.id.btnRegister).setOnClickListener(v -> register());
    }

    private void register() {
        String email = etEmail.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String region = dropdownRegion.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (email.isEmpty() || username.isEmpty() || region.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Popunite sva polja.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!REGIONS.contains(region)) {
            Toast.makeText(this, "Odaberite validan region.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Lozinke se ne poklapaju.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Lozinka mora imati najmanje 6 karaktera.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        Toast.makeText(this, "Korisničko ime je zauzeto.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createAuthUser(email, username, region, password);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void createAuthUser(String email, String username, String region, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();

                    authResult.getUser().sendEmailVerification()
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(this,
                                            "Registracija uspješna! Provjerite email za verifikaciju.",
                                            Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Nije moguće poslati verifikacioni mejl.",
                                            Toast.LENGTH_SHORT).show());

                    Map<String, Object> user = new HashMap<>();
                    user.put("email", email);
                    user.put("username", username);
                    user.put("region", region);
                    user.put("tokens", 100);
                    user.put("stars", 0);
                    user.put("starsWeekly", 0);
                    user.put("starsMonthly", 0);
                    user.put("league", 0);
                    user.put("avatarUrl", "");
                    user.put("friends", Arrays.asList());

                    BoundingBox bounds = REGION_BOUNDS.get(region);
                    if (bounds != null) {
                        double lat = bounds.minLat + (bounds.maxLat - bounds.minLat) * random.nextDouble();
                        double lng = bounds.minLng + (bounds.maxLng - bounds.minLng) * random.nextDouble();
                        user.put("lat", lat);
                        user.put("lng", lng);
                    }

                    db.collection("users").document(uid).set(user)
                            .addOnSuccessListener(unused -> {
                                startActivity(new Intent(this, LoginActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Greška pri čuvanju podataka: "
                                            + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Greška: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }
}