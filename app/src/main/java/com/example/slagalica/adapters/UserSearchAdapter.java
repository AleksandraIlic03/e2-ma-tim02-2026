package com.example.slagalica.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.RankingEntry;

import java.util.List;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.SearchViewHolder> {

    private final List<RankingEntry> users;
    private final List<String> pendingSentRequests;
    private final OnUserSearchClickListener listener;

    public interface OnUserSearchClickListener {
        void onAddFriend(RankingEntry user);
        void onCancelRequest(RankingEntry user);
    }

    public UserSearchAdapter(List<RankingEntry> users, List<String> pendingSentRequests, OnUserSearchClickListener listener) {
        this.users = users;
        this.pendingSentRequests = pendingSentRequests;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_search, parent, false);
        return new SearchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchViewHolder holder, int position) {
        RankingEntry user = users.get(position);
        holder.tvUsername.setText(user.getUsername());
        
        if (pendingSentRequests.contains(user.getUserId())) {
            holder.btnAddFriend.setText("NA ČEKANJU");
            holder.btnAddFriend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY));
            holder.btnAddFriend.setEnabled(true);
            holder.btnAddFriend.setOnClickListener(v -> listener.onCancelRequest(user));
        } else {
            holder.btnAddFriend.setText("DODAJ");
            holder.btnAddFriend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#823FAB")));
            holder.btnAddFriend.setEnabled(true);
            holder.btnAddFriend.setOnClickListener(v -> listener.onAddFriend(user));
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public static class SearchViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername;
        Button btnAddFriend;

        public SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            btnAddFriend = itemView.findViewById(R.id.btnAddFriend);
        }
    }
}
