package com.example.slagalica;

import android.os.Bundle;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.adapters.NotificationAdapter;
import com.example.slagalica.models.Notification;
import com.example.slagalica.models.NotificationRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {
    private NotificationAdapter adapter;
    private List<Notification> displayedNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        if (findViewById(R.id.btnBack) != null) {
            findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        }

        RecyclerView rvNotifications = findViewById(R.id.rvNotifications);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));

        displayedNotifications = new ArrayList<>(NotificationRepository.getAll());
        adapter = new NotificationAdapter(displayedNotifications, notification -> {
            if ("challenge".equals(notification.getType())) {
                Intent intent = new Intent(this, ChallengeResultActivity.class);
                intent.putExtra("challengeId", notification.getId());
                startActivity(intent);
            }
        });
        rvNotifications.setAdapter(adapter);

        setupFilters();

        findViewById(R.id.btnMarkAllRead).setOnClickListener(v -> {
            for (Notification n : NotificationRepository.getAll()) {
                n.setRead(true);
            }
            filterNotifications("Sve");
        });
    }

    private void setupFilters() {
        ChipGroup chipGroup = findViewById(R.id.chipGroupFilters);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                filterNotifications("Sve");
                return;
            }
            int checkedId = checkedIds.get(0);
            Chip chip = findViewById(checkedId);
            filterNotifications(chip.getText().toString());
        });
    }

    private void filterNotifications(String criteria) {
        displayedNotifications.clear();
        List<Notification> all = NotificationRepository.getAll();

        switch (criteria) {
            case "Sve":
                displayedNotifications.addAll(all);
                break;
            case "Nepročitane":
                for (Notification n : all) if (!n.isRead()) displayedNotifications.add(n);
                break;
            case "Pročitane":
                for (Notification n : all) if (n.isRead()) displayedNotifications.add(n);
                break;
            case "Čet":
                for (Notification n : all) if (n.getType().equals("chat")) displayedNotifications.add(n);
                break;
            case "Rangiranje":
                for (Notification n : all) if (n.getType().equals("ranking")) displayedNotifications.add(n);
                break;
            case "Nagrade":
                for (Notification n : all) if (n.getType().equals("rewards")) displayedNotifications.add(n);
                break;
            case "Ostalo":
                for (Notification n : all) if (n.getType().equals("other")) displayedNotifications.add(n);
                break;
        }
        adapter.notifyDataSetChanged();
    }
}