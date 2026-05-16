package com.example.slagalica;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.adapters.NotificationAdapter;
import com.example.slagalica.models.Notification;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NotificationsActivity extends AppCompatActivity {
    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private List<Notification> allNotifications;
    private List<Notification> filteredNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        if (findViewById(R.id.btnBack) != null) {
            findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        }

        rvNotifications = findViewById(R.id.rvNotifications);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));

        loadMockNotifications();
        filteredNotifications = new ArrayList<>(allNotifications);
        adapter = new NotificationAdapter(filteredNotifications);
        rvNotifications.setAdapter(adapter);

        setupFilters();

        findViewById(R.id.btnMarkAllRead).setOnClickListener(v -> {
            for (Notification n : allNotifications) {
                n.setRead(true);
            }
            adapter.notifyDataSetChanged();
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
        filteredNotifications.clear();
        if (criteria.equals("Sve")) {
            filteredNotifications.addAll(allNotifications);
        } else if (criteria.equals("Nepročitane")) {
            for (Notification n : allNotifications) {
                if (!n.isRead()) filteredNotifications.add(n);
            }
        } else if (criteria.equals("Čet")) {
            for (Notification n : allNotifications) {
                if (n.getType().equals("chat")) filteredNotifications.add(n);
            }
        } else if (criteria.equals("Rangiranje")) {
            for (Notification n : allNotifications) {
                if (n.getType().equals("ranking")) filteredNotifications.add(n);
            }
        } else if (criteria.equals("Nagrade")) {
            for (Notification n : allNotifications) {
                if (n.getType().equals("rewards")) filteredNotifications.add(n);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadMockNotifications() {
        allNotifications = new ArrayList<>();
        allNotifications.add(new Notification("1", "Nova nagrada!", "Dobili ste 5 tokena za plasman!", "pre 2 min", "rewards", false));
        allNotifications.add(new Notification("2", "Nova poruka u četu", "Marko: Hej, jesi li za partiju?", "pre 15 min", "chat", false));
        allNotifications.add(new Notification("3", "Ažurirana rang lista", "Skočili ste na 3. mesto u ligi!", "pre 1 sat", "ranking", true));
        allNotifications.add(new Notification("4", "Poziv za prijatelja", "Korisnik 'SlagalicaMajstor' vas je dodao.", "juče", "friend", true));
    }
}
