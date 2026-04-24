package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        MaterialButton btnStart = findViewById(R.id.btnStartMatch);
        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, KorakPoKorakActivity.class);
            startActivity(intent);
        });
    }
}