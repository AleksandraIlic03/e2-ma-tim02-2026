package com.example.slagalica;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.tabs.TabLayout;

public class StatisticsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        NestedScrollView scrollOverview = findViewById(R.id.scrollOverview);
        NestedScrollView scrollGames = findViewById(R.id.scrollGames);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    scrollOverview.setVisibility(View.VISIBLE);
                    scrollGames.setVisibility(View.GONE);
                } else {
                    scrollOverview.setVisibility(View.GONE);
                    scrollGames.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
}
