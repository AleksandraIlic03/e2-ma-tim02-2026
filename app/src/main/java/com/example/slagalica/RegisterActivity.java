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

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etUsername, etPassword, etConfirmPassword;
    private AutoCompleteTextView dropdownRegion;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private static final List<String> REGIONS = Arrays.asList(
            "Beogradski region",
            "Vojvodina",
            "Šumadijski okrug",
            "Podunavski okrug",
            "Braničevski okrug",
            "Pomoravski okrug",
            "Borski okrug",
            "Zaječarski okrug",
            "Nišavski okrug",
            "Toplički okrug",
            "Pirotski okrug",
            "Jablanički okrug",
            "Pčinjski okrug",
            "Rasinski okrug",
            "Raški okrug",
            "Moravički okrug",
            "Zlatiborski okrug",
            "Kolubarski okrug",
            "Mačvanski okrug"
    );

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