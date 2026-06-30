package com.example.slagalica.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.RankingEntry;

import java.util.List;

public class RankAdapter extends RecyclerView.Adapter<RankAdapter.RankViewHolder> {

    private final List<RankingEntry> rankingEntries;

    public RankAdapter(List<RankingEntry> rankingEntries) {
        this.rankingEntries = rankingEntries;
    }

    @NonNull
    @Override
    public RankViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ranking, parent, false);
        return new RankViewHolder(view);
    }

    private static final String[] LEAGUE_NAMES = {
        "Miš", "Vuk", "Medved", "Lav", "Orao", "Zmaj"
    };

    @Override
    public void onBindViewHolder(@NonNull RankViewHolder holder, int position) {
        RankingEntry entry = rankingEntries.get(position);
        holder.tvRank.setText((position + 1) + ".");
        holder.tvUsername.setText(entry.getUsername());

        long starsToShow = Math.max(0, entry.getStars());
        holder.tvStars.setText(String.valueOf(starsToShow));

        int leagueIdx = Math.max(0, Math.min(entry.getLeague(), LEAGUE_NAMES.length - 1));
        holder.tvLeague.setText(LEAGUE_NAMES[leagueIdx]);

        if (entry.getAvatarUrl() != null && !entry.getAvatarUrl().isEmpty()) {
            int resId = holder.itemView.getContext().getResources().getIdentifier(
                    entry.getAvatarUrl(), "drawable", holder.itemView.getContext().getPackageName());
            if (resId != 0) {
                holder.ivAvatar.setImageResource(resId);
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_user);
            }
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_user);
        }
    }

    @Override
    public int getItemCount() {
        return rankingEntries.size();
    }

    public static class RankViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvUsername, tvStars, tvLeague;
        ImageView ivAvatar;

        public RankViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvStars = itemView.findViewById(R.id.tvStars);
            tvLeague = itemView.findViewById(R.id.tvLeague);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
        }
    }
}
