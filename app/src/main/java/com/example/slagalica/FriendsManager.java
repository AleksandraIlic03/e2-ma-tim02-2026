package com.example.slagalica;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class FriendsManager {
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static String getCurrentUserId() {
        return FirebaseAuth.getInstance().getUid();
    }

    public interface ActionCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public static void sendFriendRequest(String toUserId, String toUsername, String fromUsername, ActionCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null || toUserId == null) {
            if (callback != null) callback.onFailure(new Exception("Korisnik nije prijavljen"));
            return;
        }
        if (currentUserId.equals(toUserId)) return;

        db.collection("friendRequests")
                .whereEqualTo("fromUserId", currentUserId)
                .whereEqualTo("toUserId", toUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Map<String, Object> request = new HashMap<>();
                        request.put("fromUserId", currentUserId);
                        request.put("fromUsername", fromUsername);
                        request.put("toUserId", toUserId);
                        request.put("status", "pending");
                        request.put("timestamp", FieldValue.serverTimestamp());

                        db.collection("friendRequests").add(request)
                                .addOnSuccessListener(documentReference -> {
                                    if (callback != null) callback.onSuccess();
                                })
                                .addOnFailureListener(e -> {
                                    if (callback != null) callback.onFailure(e);
                                });
                    } else {
                        if (callback != null) callback.onSuccess();
                    }
                });
    }

    public static void acceptFriendRequest(String requestId, String fromUserId, ActionCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            if (callback != null) callback.onFailure(new Exception("Korisnik nije prijavljen"));
            return;
        }

        WriteBatch batch = db.batch();
        DocumentReference requestRef = db.collection("friendRequests").document(requestId);
        batch.update(requestRef, "status", "accepted");

        DocumentReference currentUserRef = db.collection("users").document(currentUserId);
        DocumentReference otherUserRef = db.collection("users").document(fromUserId);

        batch.update(currentUserRef, "friends", FieldValue.arrayUnion(fromUserId));
        batch.update(otherUserRef, "friends", FieldValue.arrayUnion(currentUserId));

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    public static void rejectFriendRequest(String requestId, ActionCallback callback) {
        db.collection("friendRequests").document(requestId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    public static void cancelFriendRequest(String toUserId, ActionCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return;

        db.collection("friendRequests")
                .whereEqualTo("fromUserId", currentUserId)
                .whereEqualTo("toUserId", toUserId)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onFailure(e);
                            });
                });
    }

    public static void removeFriend(String friendId, ActionCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return;

        WriteBatch batch = db.batch();
        DocumentReference currentUserRef = db.collection("users").document(currentUserId);
        DocumentReference otherUserRef = db.collection("users").document(friendId);

        batch.update(currentUserRef, "friends", FieldValue.arrayRemove(friendId));
        batch.update(otherUserRef, "friends", FieldValue.arrayRemove(currentUserId));

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    public static void sendGameInvite(String toUserId, String fromUsername, String roomId, ActionCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null || toUserId == null) return;

        Map<String, Object> invite = new HashMap<>();
        invite.put("fromUserId", currentUserId);
        invite.put("fromUsername", fromUsername);
        invite.put("toUserId", toUserId);
        invite.put("roomId", roomId);
        invite.put("status", "pending");
        invite.put("timestamp", FieldValue.serverTimestamp());

        db.collection("gameInvites").add(invite)
                .addOnSuccessListener(documentReference -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    public static void respondToGameInvite(String inviteId, String status, ActionCallback callback) {
        db.collection("gameInvites").document(inviteId)
                .update("status", status)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    public static void cancelGameInvite(String roomId, ActionCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return;

        db.collection("gameInvites")
                .whereEqualTo("fromUserId", currentUserId)
                .whereEqualTo("roomId", roomId)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.update(doc.getReference(), "status", "cancelled");
                    }
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onFailure(e);
                            });
                });
    }
}
