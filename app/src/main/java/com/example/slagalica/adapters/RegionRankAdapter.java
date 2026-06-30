package com.example.slagalica.adapters;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.RegionDetailActivity;
import com.example.slagalica.models.RegionRankingEntry;

import java.util.List;

public class RegionRankAdapter extends RecyclerView.Adapter<RegionRankAdapter.RegionViewHolder> {

    private final List<RegionRankingEntry> regions;
    private final String userRegion;

    public RegionRankAdapter(List<RegionRankingEntry> regions, String userRegion) {
        this.regions = regions;
        this.userRegion = userRegion;
    }

    @NonNull
    @Override
    public RegionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_region_ranking, parent, false);
        return new RegionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RegionViewHolder holder, int position) {
        RegionRankingEntry entry = regions.get(position);
        holder.tvRank.setText((position + 1) + ".");
        holder.tvRegionName.setText(entry.getName());
        holder.tvStars.setText(String.valueOf(entry.getStars()));
        holder.ivRegionIcon.setImageResource(entry.getIconRes());

        if (entry.getName().equals(userRegion)) {
            holder.cardRegion.setCardBackgroundColor(Color.parseColor("#FFFDE7")); 
            holder.tvRegionName.setTextColor(Color.parseColor("#823FAB"));
            holder.tvRegionStatus.setVisibility(View.VISIBLE);
            holder.tvRegionStatus.setText("Vaš region");
        } else {
            holder.cardRegion.setCardBackgroundColor(Color.WHITE);
            holder.tvRegionName.setTextColor(Color.parseColor("#321C1C"));
            holder.tvRegionStatus.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), RegionDetailActivity.class);
            intent.putExtra("regionName", entry.getName());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return regions.size();
    }

    static class RegionViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvRegionName, tvStars, tvRegionStatus;
        ImageView ivRegionIcon;
        CardView cardRegion;

        public RegionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvRegionName = itemView.findViewById(R.id.tvRegionName);
            tvStars = itemView.findViewById(R.id.tvStars);
            tvRegionStatus = itemView.findViewById(R.id.tvRegionStatus);
            ivRegionIcon = itemView.findViewById(R.id.ivRegionIcon);
            cardRegion = itemView.findViewById(R.id.cardRegion);
        }
    }
}