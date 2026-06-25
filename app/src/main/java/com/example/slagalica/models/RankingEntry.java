package com.example.slagalica.models;

public class RankingEntry {
    private String userId;
    private String username;
    private String avatarUrl;
    private long stars;
    private int rank;

    public RankingEntry() {}

    private int league;

    public RankingEntry(String userId, String username, String avatarUrl, long stars) {
        this.userId = userId;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.stars = stars;
    }

    public RankingEntry(String userId, String username, String avatarUrl, long stars, int league) {
        this.userId = userId;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.stars = stars;
        this.league = league;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public long getStars() { return stars; }
    public void setStars(long stars) { this.stars = stars; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public int getLeague() { return league; }
    public void setLeague(int league) { this.league = league; }
}
