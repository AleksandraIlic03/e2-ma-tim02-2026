package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmailUsername, etPassword;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmailUsername = findViewById(R.id.etEmailUsername);
        etPassword = findViewById(R.id.etPassword);

        findViewById(R.id.tvRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> login());
    }

    private void login() {
        String emailOrUsername = etEmailUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (emailOrUsername.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Popunite sva polja.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ako je unesen email, direktno loguj
        if (emailOrUsername.contains("@")) {
            signInWithEmail(emailOrUsername, password);
        } else {
            // Ako je korisničko ime, pronađi email u Firestoru
            db.collection("users")
                    .whereEqualTo("username", emailOrUsername)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (querySnapshot.isEmpty()) {
                            Toast.makeText(this, "Korisnik nije pronađen.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String email = querySnapshot.getDocuments().get(0).getString("email");
                        signInWithEmail(email, password);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void signInWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();

                   // if (!user.isEmailVerified()) {
                     //   Toast.makeText(this,
                        //        "Email nije verifikovan. Provjerite inbox.",
                          //      Toast.LENGTH_LONG).show();
                        //mAuth.signOut();
                        //return;
                    //}

                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Pogrešan email/lozinka.", Toast.LENGTH_SHORT).show());
    }
}