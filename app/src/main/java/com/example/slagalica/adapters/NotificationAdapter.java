package com.example.slagalica.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.models.Notification;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<Notification> notificationList;

    public NotificationAdapter(List<Notification> notificationList) {
        this.notificationList = notificationList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notification = notificationList.get(position);
        holder.tvTitle.setText(notification.getTitle());
        holder.tvMessage.setText(notification.getMessage());
        holder.tvTime.setText(notification.getTimestamp());
        holder.ivUnreadDot.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

        switch (notification.getType()) {
            case "rewards":
                holder.tvIcon.setText("🎁");
                holder.viewIconBg.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFF9C4")));
                break;
            case "chat":
                holder.tvIcon.setText("💬");
                holder.viewIconBg.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E1F5FE")));
                break;
            case "ranking":
                holder.tvIcon.setText("📊");
                holder.viewIconBg.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F3E5F5")));
                break;
            case "friend":
                holder.tvIcon.setText("🤝");
                holder.viewIconBg.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
                break;
            default:
                holder.tvIcon.setText("🔔");
                holder.viewIconBg.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EEEEEE")));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvTime, tvIcon;
        View viewIconBg, ivUnreadDot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNotifTitle);
            tvMessage = itemView.findViewById(R.id.tvNotifMessage);
            tvTime = itemView.findViewById(R.id.tvNotifTime);
            tvIcon = itemView.findViewById(R.id.tvNotifIcon);
            viewIconBg = itemView.findViewById(R.id.viewIconBg);
            ivUnreadDot = itemView.findViewById(R.id.ivUnreadDot);
        }
    }
}
