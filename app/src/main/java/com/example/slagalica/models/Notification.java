package com.example.slagalica.models;

public class Notification {
    private String id;
    private String title;
    private String message;
    private String timestamp;
    private String type;
    private boolean isRead;

    public Notification(String id, String title, String message, String timestamp, String type, boolean isRead) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.isRead = isRead;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}
