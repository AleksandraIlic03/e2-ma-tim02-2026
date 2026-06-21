package com.example.slagalica.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.Mission;

import java.util.List;

public class MissionAdapter extends RecyclerView.Adapter<MissionAdapter.MissionViewHolder> {

    private final List<Mission> missions;

    public MissionAdapter(List<Mission> missions) {
        this.missions = missions;
    }

    @NonNull
    @Override
    public MissionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mission, parent, false);
        return new MissionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MissionViewHolder holder, int position) {
        Mission mission = missions.get(position);
        holder.tvTitle.setText(mission.getTitle());
        holder.tvReward.setText("Nagrada: " + mission.getRewardStars() + " zvezde");
        holder.cbCompleted.setChecked(mission.isCompleted());
    }

    @Override
    public int getItemCount() {
        return missions.size();
    }

    public static class MissionViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvReward;
        CheckBox cbCompleted;

        public MissionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvMissionTitle);
            tvReward = itemView.findViewById(R.id.tvReward);
            cbCompleted = itemView.findViewById(R.id.cbCompleted);
        }
    }
}
