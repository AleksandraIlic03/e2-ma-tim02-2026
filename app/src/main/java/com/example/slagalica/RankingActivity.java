package com.example.slagalica;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.adapters.RankAdapter;
import com.example.slagalica.adapters.RegionRankAdapter;
import com.example.slagalica.models.RankingEntry;
import com.example.slagalica.models.RegionRankingEntry;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
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
    private TextView tvCycleDates, tvEmptyRanking;
    private RankAdapter userAdapter;
    private RegionRankAdapter regionAdapter;
    private List<RankingEntry> rankingList = new ArrayList<>();
    private List<RegionRankingEntry> regionRankingList = new ArrayList<>();
    private FirebaseFirestore db;
    private Handler refreshHandler = new Handler();
    private Runnable refreshRunnable;
    private int selectedTab = 0; // 0: Weekly, 1: Monthly, 2: Regional
    private String userRegion = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rankings);

        db = FirebaseFirestore.getInstance();
        loadUserRegion();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tabLayout = findViewById(R.id.tabLayout);
        rvRankings = findViewById(R.id.rvRankings);
        tvCycleDates = findViewById(R.id.tvCycleDates);
        tvEmptyRanking = findViewById(R.id.tvEmptyRanking);

        rvRankings.setLayoutManager(new LinearLayoutManager(this));
        userAdapter = new RankAdapter(rankingList);
        regionAdapter = new RegionRankAdapter(regionRankingList, userRegion);
        
        rvRankings.setAdapter(userAdapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
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

        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadRankings();
                refreshHandler.postDelayed(this, 120000);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, 120000);
    }

    private void loadUserRegion() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                userRegion = doc.getString("region");
                if (userRegion == null) userRegion = "";
                // Re-create adapter to pass the region
                regionAdapter = new RegionRankAdapter(regionRankingList, userRegion);
                if (selectedTab == 2) rvRankings.setAdapter(regionAdapter);
            });
        }
    }

    private void updateCycleDates() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        if (selectedTab == 0) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            String start = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_WEEK, 6);
            String end = sdf.format(cal.getTime());
            tvCycleDates.setText(start + " - " + end);
            tvCycleDates.setVisibility(View.VISIBLE);
        } else if (selectedTab == 1) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
            String start = sdf.format(cal.getTime());
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            String end = sdf.format(cal.getTime());
            tvCycleDates.setText(start + " - " + end);
            tvCycleDates.setVisibility(View.VISIBLE);
        } else {
            // Regionalna je mesečna po specifikaciji
            cal.set(Calendar.DAY_OF_MONTH, 1);
            String start = sdf.format(cal.getTime());
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            String end = sdf.format(cal.getTime());
            tvCycleDates.setText("Regionalni učinak: " + start + " - " + end);
            tvCycleDates.setVisibility(View.VISIBLE);
        }
    }

    private void loadRankings() {
        if (selectedTab == 2) {
            loadRegionalRankings();
            return;
        }

        rvRankings.setAdapter(userAdapter);
        String field = (selectedTab == 0) ? "starsWeekly" : "starsMonthly";
        db.collection("users")
                .whereGreaterThan(field, 0L)
                .orderBy(field, Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    rankingList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
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
                    userAdapter.notifyDataSetChanged();
                    tvEmptyRanking.setVisibility(rankingList.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> Toast.makeText(RankingActivity.this,
                        "Greška pri učitavanju rang liste", Toast.LENGTH_SHORT).show());
    }

    private void loadRegionalRankings() {
        rvRankings.setAdapter(regionAdapter);
        db.collection("regions")
                .orderBy("starsMonthly", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    regionRankingList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        long totalStars = doc.getLong("starsMonthly") != null ? doc.getLong("starsMonthly") : 0L;
                        regionRankingList.add(new RegionRankingEntry(
                                doc.getId(),
                                totalStars,
                                RegionUtils.getIconRes(doc.getId())
                        ));
                    }
                    regionAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Greška pri učitavanju ranga regiona", Toast.LENGTH_SHORT).show());
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
