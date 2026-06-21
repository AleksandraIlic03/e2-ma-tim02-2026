package com.example.slagalica;

import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.adapters.RankAdapter;
import com.example.slagalica.models.RankingEntry;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RankingActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private RecyclerView rvRankings;
    private TextView tvCycleDates;
    private RankAdapter adapter;
    private List<RankingEntry> rankingList = new ArrayList<>();
    private FirebaseFirestore db;
    private Handler refreshHandler = new Handler();
    private Runnable refreshRunnable;
    private boolean isWeekly = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rankings);

        db = FirebaseFirestore.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tabLayout = findViewById(R.id.tabLayout);
        rvRankings = findViewById(R.id.rvRankings);
        tvCycleDates = findViewById(R.id.tvCycleDates);

        rvRankings.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RankAdapter(rankingList);
        rvRankings.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isWeekly = tab.getPosition() == 0;
                updateCycleDates();
                loadRankings();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        updateCycleDates();
        loadRankings();
        RankingManager.checkAndResetCycle(this);

        // Refresh every 2 minutes
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadRankings();
                refreshHandler.postDelayed(this, 120000); // 2 minutes
            }
        };
        refreshHandler.postDelayed(refreshRunnable, 120000);
    }

    private void updateCycleDates() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        if (isWeekly) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            String start = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_WEEK, 6);
            String end = sdf.format(cal.getTime());
            tvCycleDates.setText(start + " - " + end);
        } else {
            cal.set(Calendar.DAY_OF_MONTH, 1);
            String start = sdf.format(cal.getTime());
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            String end = sdf.format(cal.getTime());
            tvCycleDates.setText(start + " - " + end);
        }
    }

    private void loadRankings() {
        String field = isWeekly ? "starsWeekly" : "starsMonthly";
        db.collection("users")
                .orderBy(field, Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    rankingList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        // Spec 4a: igrač mora da je odigrao min 1 partiju
                        Long gamesPlayed = doc.getLong("gamesPlayed");
                        if (gamesPlayed == null || gamesPlayed < 1) continue;

                        int league = doc.getLong("league") != null
                                ? doc.getLong("league").intValue() : 0;
                        long stars = doc.getLong(field) != null ? doc.getLong(field) : 0;

                        RankingEntry entry = new RankingEntry(
                                doc.getId(),
                                doc.getString("username"),
                                doc.getString("avatarUrl"),
                                stars,
                                league
                        );
                        rankingList.add(entry);
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(RankingActivity.this,
                        "Greška pri učitavanju rang liste", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
