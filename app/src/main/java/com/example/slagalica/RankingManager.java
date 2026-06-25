package com.example.slagalica;

import android.content.Context;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

public class RankingManager {

    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static void updateStars(String userId, int starChange) {
        if (userId == null) return;

        DocumentReference userRef = db.collection("users").document(userId);

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(userRef);
            
            long currentStars = snapshot.getLong("stars") != null ? snapshot.getLong("stars") : 0;
            long currentWeekly = snapshot.getLong("starsWeekly") != null ? snapshot.getLong("starsWeekly") : 0;
            long currentMonthly = snapshot.getLong("starsMonthly") != null ? snapshot.getLong("starsMonthly") : 0;

            // 1. Osiguravamo da zvezde ne odu ispod nule (Spec 3.d.iv)
            long newStars = Math.max(0, currentStars + starChange);
            long newWeekly = Math.max(0, currentWeekly + starChange);
            long newMonthly = Math.max(0, currentMonthly + starChange);

            // 2. Automatsko računanje LIGE na osnovu UKUPNIH zvezda (Spec 6.c)
            int newLeague = 0;
            if (newStars >= 1600) newLeague = 5;
            else if (newStars >= 800) newLeague = 4;
            else if (newStars >= 400) newLeague = 3;
            else if (newStars >= 200) newLeague = 2;
            else if (newStars >= 100) newLeague = 1;

            transaction.update(userRef, 
                "stars", newStars,
                "starsWeekly", newWeekly,
                "starsMonthly", newMonthly,
                "league", newLeague
            );

            return null;
        });
    }

    // Spec 4c/4g: proveri da li je novi nedeljni/mesečni ciklus, nagradi top 10, resetuj
    public static void checkAndResetCycle(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year = cal.get(java.util.Calendar.YEAR);
        int week = cal.get(java.util.Calendar.WEEK_OF_YEAR);
        int month = cal.get(java.util.Calendar.MONTH);
        String weekKey = year + "-W" + week;
        String monthKey = year + "-M" + month;

        DocumentReference cycleRef = db.collection("system").document("cycleState");

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot doc = transaction.get(cycleRef);
            String lastWeek = doc.exists() ? doc.getString("lastWeeklyReset") : null;
            String lastMonth = doc.exists() ? doc.getString("lastMonthlyReset") : null;

            java.util.Map<String, Object> toSet = new java.util.HashMap<>();
            boolean weeklyReset = !weekKey.equals(lastWeek);
            boolean monthlyReset = !monthKey.equals(lastMonth);
            if (weeklyReset) toSet.put("lastWeeklyReset", weekKey);
            if (monthlyReset) toSet.put("lastMonthlyReset", monthKey);
            if (!toSet.isEmpty()) transaction.set(cycleRef, toSet, SetOptions.merge());

            java.util.Map<String, Boolean> result = new java.util.HashMap<>();
            result.put("weekly", weeklyReset);
            result.put("monthly", monthlyReset);
            return result;
        }).addOnSuccessListener(result -> {
            if (Boolean.TRUE.equals(result.get("weekly")))
                rewardAndResetPeriod(context, "starsWeekly", "Nedeljni");
            if (Boolean.TRUE.equals(result.get("monthly")))
                rewardAndResetPeriod(context, "starsMonthly", "Mesečni");
        });
    }

    private static void rewardAndResetPeriod(Context context, String starsField, String periodName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Spec 4c: nedeljno [5,3,2,1,1,1,1,1,1,1], mesečno [10,6,4,2,2,2,2,2,2,2]
        boolean isWeekly = starsField.equals("starsWeekly");
        int[] tokenRewards = isWeekly
                ? new int[]{5, 3, 2, 1, 1, 1, 1, 1, 1, 1}
                : new int[]{10, 6, 4, 2, 2, 2, 2, 2, 2, 2};

        db.collection("users")
            .orderBy(starsField, Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener(snap -> {
                WriteBatch batch = db.batch();
                int rank = 0;
                for (QueryDocumentSnapshot doc : snap) {
                    long stars = doc.getLong(starsField) != null ? doc.getLong(starsField) : 0;
                    if (stars > 0 && rank < tokenRewards.length) {
                        int earned = tokenRewards[rank];
                        batch.update(doc.getReference(), "tokens", FieldValue.increment(earned));
                        // Spec 4g: sačuvaj nagradu da HomeActivity može da je prikaže
                        java.util.Map<String, Object> pending = new java.util.HashMap<>();
                        pending.put("tokens", (long) earned);
                        pending.put("rank", (long) (rank + 1));
                        pending.put("period", periodName);
                        batch.update(doc.getReference(), "pendingReward", pending);
                        rank++;
                    }
                    batch.update(doc.getReference(), starsField, 0L);
                }
                batch.commit().addOnSuccessListener(v -> {
                    if (context != null) {
                        NotificationHelper.sendRealNotification(
                            context,
                            periodName + " ciklus je završen!",
                            "Rang lista je resetovana. Nagrade su podeljene top 10 igračima.",
                            NotificationHelper.CHANNEL_RANKING
                        );
                    }
                });
            });
    }

    public static void completeMission(String userId, String missionKey) {
        if (userId == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        DocumentReference missionRef = db.collection("users").document(userId).collection("dailyMissions").document(today);

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(missionRef);
            if (snapshot.exists()) {
                Boolean isDone = snapshot.getBoolean(missionKey);
                if (isDone != null && !isDone) {
                    transaction.update(missionRef, missionKey, true);
                    updateStars(userId, 3); // Spec 12b: +3 zvezde po misiji
                }
            } else {
                // Dokument ne postoji (igrač nije otvorio DailyMissions) – kreiraj ga
                java.util.Map<String, Object> init = new java.util.HashMap<>();
                init.put("win_game", false);
                init.put("send_chat", false);
                init.put("friend_game", false);
                init.put("tournament_win", false);
                init.put("bonus_claimed", false);
                init.put(missionKey, true);
                transaction.set(missionRef, init);
                updateStars(userId, 3); // Spec 12b: +3 zvezde po misiji
            }
            return null;
        });
    }
}
