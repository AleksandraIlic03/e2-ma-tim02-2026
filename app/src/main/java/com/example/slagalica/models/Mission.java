package com.example.slagalica.models;

public class Mission {
    private String id;
    private String title;
    private boolean completed;
    private int rewardStars;

    public Mission() {}

    public Mission(String id, String title, int rewardStars) {
        this.id = id;
        this.title = title;
        this.rewardStars = rewardStars;
        this.completed = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public int getRewardStars() { return rewardStars; }
    public void setRewardStars(int rewardStars) { this.rewardStars = rewardStars; }
}
