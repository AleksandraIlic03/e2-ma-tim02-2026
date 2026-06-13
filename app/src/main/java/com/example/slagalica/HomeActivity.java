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

        NotificationHelper.createNotificationChannels(this);

        MaterialButton btnAsocijacije = findViewById(R.id.btnAsocijacije);
        btnAsocijacije.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, WaitingRoomActivity.class);
            startActivity(intent);
        });

        MaterialButton btnSkocko = findViewById(R.id.btnSkocko);
        btnSkocko.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, WaitingRoomActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnNotifications).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnStartMatch).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, WaitingRoomActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnNavProfile).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnMojBroj).setVisibility(android.view.View.GONE);

        findViewById(R.id.btnKoZnaZna).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, WaitingRoomActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnSpojnice).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, WaitingRoomActivity.class);
            startActivity(intent);
        });

//         Intent importIntent = new Intent(this, DataImportActivity.class);
//         startActivity(importIntent);
    }
}
