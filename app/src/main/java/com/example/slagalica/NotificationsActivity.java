package com.example.slagalica;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class NotificationsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        if (findViewById(R.id.btnBack) != null) {
            findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        }
    }
}
