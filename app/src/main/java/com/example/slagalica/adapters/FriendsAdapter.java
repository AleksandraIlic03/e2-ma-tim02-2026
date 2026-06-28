package com.example.slagalica.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.RankingEntry;

import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.FriendViewHolder> {

    private final List<RankingEntry> friends;
    private final OnFriendClickListener listener;

    public interface OnFriendClickListener {
        void onPlayClick(RankingEntry friend);
    }

    public FriendsAdapter(List<RankingEntry> friends, OnFriendClickListener listener) {
        this.friends = friends;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }

    private static final String[] LEAGUE_NAMES = {"Miš", "Vuk", "Medved", "Lav", "Orao", "Zmaj"};

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        RankingEntry friend = friends.get(position);
        holder.tvUsername.setText(friend.getUsername() != null ? friend.getUsername() : "Korisnik");
        holder.tvStars.setText(friend.getStars() + " ⭐");
        
        int leagueIdx = Math.max(0, Math.min(friend.getLeague(), LEAGUE_NAMES.length - 1));
        holder.tvLeague.setText(LEAGUE_NAMES[leagueIdx]);
        
        if (friend.getRank() > 0) {
            holder.tvMonthlyRank.setText("Mesečni rang: #" + friend.getRank());
        } else {
            holder.tvMonthlyRank.setText("Nije rangiran");
        }

        if (friend.getAvatarUrl() != null && !friend.getAvatarUrl().isEmpty()) {
            int resId = holder.itemView.getContext().getResources().getIdentifier(
                    friend.getAvatarUrl(), "drawable", holder.itemView.getContext().getPackageName());
            if (resId != 0) {
                holder.ivAvatar.setImageResource(resId);
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_user);
            }
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_user);
        }

        if (holder.vOnlineIndicator != null) {
            holder.vOnlineIndicator.setVisibility(friend.isOnline() ? View.VISIBLE : View.GONE);
            if (friend.isOnline()) {
                if (friend.isInGame()) {
                    holder.vOnlineIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.YELLOW));
                    holder.tvStatusText.setText("U igri");
                    holder.tvStatusText.setTextColor(android.graphics.Color.parseColor("#FBC02D"));
                    holder.btnPlay.setVisibility(View.GONE);
                } else {
                    holder.vOnlineIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN));
                    holder.tvStatusText.setText("Aktivan");
                    holder.tvStatusText.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                    holder.btnPlay.setVisibility(View.VISIBLE);
                    holder.btnPlay.setEnabled(true);
                }
            } else {
                holder.tvStatusText.setText("Neaktivan");
                holder.tvStatusText.setTextColor(android.graphics.Color.parseColor("#877777"));
                holder.btnPlay.setVisibility(View.GONE);
            }
        }

        holder.btnPlay.setOnClickListener(v -> listener.onPlayClick(friend));
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    public static class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername, tvLeague, tvStars, tvMonthlyRank, tvStatusText;
        ImageView ivAvatar;
        View vOnlineIndicator;
        Button btnPlay;

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvLeague = itemView.findViewById(R.id.tvLeague);
            tvStars = itemView.findViewById(R.id.tvStars);
            tvMonthlyRank = itemView.findViewById(R.id.tvMonthlyRank);
            tvStatusText = itemView.findViewById(R.id.tvStatusText);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            vOnlineIndicator = itemView.findViewById(R.id.vOnlineIndicator);
            btnPlay = itemView.findViewById(R.id.btnPlay);
        }
    }
}
