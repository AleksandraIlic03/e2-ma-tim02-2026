package com.example.slagalica;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class StatisticsManager {

    private static final String USERS_COLLECTION = "users";
    private static final String STATS_FIELD = "stats";

    public static void updateKZZStats(int correct, int wrong, int points, boolean isNewMatch) {
        String uid = getUid();
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put(STATS_FIELD + ".kzzCorrect", FieldValue.increment(correct));
        updates.put(STATS_FIELD + ".kzzWrong", FieldValue.increment(wrong));
        updates.put(STATS_FIELD + ".kzzTotalPoints", FieldValue.increment(points));
        if (isNewMatch) {
            updates.put(STATS_FIELD + ".kzzGames", FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".totalPoints", FieldValue.increment(points));

        FirebaseFirestore.getInstance().collection(USERS_COLLECTION).document(uid).update(updates);
    }

    public static void updateMBStats(boolean exactHit, int points, boolean isNewMatch) {
        String uid = getUid();
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        if (exactHit) {
            updates.put(STATS_FIELD + ".mbExactHits", FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".mbTotalPoints", FieldValue.increment(points));
        if (isNewMatch) {
            updates.put(STATS_FIELD + ".mbGames", FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".totalPoints", FieldValue.increment(points));

        FirebaseFirestore.getInstance().collection(USERS_COLLECTION).document(uid).update(updates);
    }

    public static void updateKPKStats(int stepIndex, int points, boolean isNewMatch) {
        String uid = getUid();
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        if (stepIndex >= 0 && stepIndex < 7) {
            updates.put(STATS_FIELD + ".kpkStepHits." + stepIndex, FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".kpkTotalPoints", FieldValue.increment(points));
        if (isNewMatch) {
            updates.put(STATS_FIELD + ".kpkGames", FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".totalPoints", FieldValue.increment(points));

        FirebaseFirestore.getInstance().collection(USERS_COLLECTION).document(uid).update(updates);
    }

    public static void updateASStats(boolean solved, int points, boolean isNewMatch) {
        String uid = getUid();
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        if (solved) {
            updates.put(STATS_FIELD + ".asSolved", FieldValue.increment(1));
        } else {
            updates.put(STATS_FIELD + ".asUnsolved", FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".asTotalPoints", FieldValue.increment(points));
        if (isNewMatch) {
            updates.put(STATS_FIELD + ".asGames", FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".totalPoints", FieldValue.increment(points));

        FirebaseFirestore.getInstance().collection(USERS_COLLECTION).document(uid).update(updates);
    }

    public static void updateSKStats(int attemptIndex, int points, boolean isNewMatch) {
        String uid = getUid();
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        if (attemptIndex >= 0 && attemptIndex < 6) {
            updates.put(STATS_FIELD + ".skAttemptHits." + attemptIndex, FieldValue.increment(1));
        } else if (attemptIndex == 6) {
             updates.put(STATS_FIELD + ".skAttemptHits.steal", FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".skTotalPoints", FieldValue.increment(points));
        if (isNewMatch) {
            updates.put(STATS_FIELD + ".skGames", FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".totalPoints", FieldValue.increment(points));

        FirebaseFirestore.getInstance().collection(USERS_COLLECTION).document(uid).update(updates);
    }

    public static void updateSPStats(int correctPairs, int totalPairs, int points, boolean isNewMatch) {
        String uid = getUid();
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put(STATS_FIELD + ".spCorrectPairs", FieldValue.increment(correctPairs));
        updates.put(STATS_FIELD + ".spTotalPairs", FieldValue.increment(totalPairs));
        updates.put(STATS_FIELD + ".spTotalPoints", FieldValue.increment(points));
        if (isNewMatch) {
            updates.put(STATS_FIELD + ".spGames", FieldValue.increment(1));
        }
        updates.put(STATS_FIELD + ".totalPoints", FieldValue.increment(points));

        FirebaseFirestore.getInstance().collection(USERS_COLLECTION).document(uid).update(updates);
    }

    public static void updateMatchResult(String result) {
        String uid = getUid();
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put(STATS_FIELD + ".totalGamesPlayed", FieldValue.increment(1));
        if ("win".equals(result)) {
            updates.put(STATS_FIELD + ".totalWins", FieldValue.increment(1));
        } else if ("loss".equals(result)) {
            updates.put(STATS_FIELD + ".totalLosses", FieldValue.increment(1));
        } else if ("draw".equals(result)) {
            updates.put(STATS_FIELD + ".totalDraws", FieldValue.increment(1));
        }

        FirebaseFirestore.getInstance().collection(USERS_COLLECTION).document(uid).update(updates);
    }

    private static String getUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return null;
    }
}
