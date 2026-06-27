package com.example.slagalica.models;

import com.google.firebase.Timestamp;

public class ChatMessage {
    private String senderId;
    private String senderName;
    private String text;
    private Timestamp timestamp;
    private String region;

    public ChatMessage() {}

    public ChatMessage(String senderId, String senderName, String text, Timestamp timestamp, String region) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = timestamp;
        this.region = region;
    }

    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getText() { return text; }
    public Timestamp getTimestamp() { return timestamp; }
    public String getRegion() { return region; }
}