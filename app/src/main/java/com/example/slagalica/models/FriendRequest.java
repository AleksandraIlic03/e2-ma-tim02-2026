package com.example.slagalica.models;

import com.google.firebase.Timestamp;

public class FriendRequest {
    private String requestId;
    private String fromUserId;
    private String fromUsername;
    private String toUserId;
    private String status; // "pending", "accepted", "rejected"
    private Timestamp timestamp;

    public FriendRequest() {}

    public FriendRequest(String requestId, String fromUserId, String fromUsername, String toUserId, String status, Timestamp timestamp) {
        this.requestId = requestId;
        this.fromUserId = fromUserId;
        this.fromUsername = fromUsername;
        this.toUserId = toUserId;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }

    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }

    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
