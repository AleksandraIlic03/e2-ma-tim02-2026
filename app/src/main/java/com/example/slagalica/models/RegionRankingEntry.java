package com.example.slagalica.models;

public class RegionRankingEntry {
    private String name;
    private long stars;
    private int iconRes;

    public RegionRankingEntry(String name, long stars, int iconRes) {
        this.name = name;
        this.stars = stars;
        this.iconRes = iconRes;
    }

    public String getName() { return name; }
    public long getStars() { return stars; }
    public int getIconRes() { return iconRes; }
}