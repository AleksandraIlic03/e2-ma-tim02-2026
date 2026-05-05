package com.example.slagalica;

import android.os.Bundle;
import android.content.Intent;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
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
    }
}
