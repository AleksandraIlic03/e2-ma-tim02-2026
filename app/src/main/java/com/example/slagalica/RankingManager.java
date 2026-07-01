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

    public static int calculateLeague(long stars) {
        if (stars >= 1600) return 5;
        if (stars >= 800) return 4;
        if (stars >= 400) return 3;
        if (stars >= 200) return 2;
        if (stars >= 100) return 1;
        return 0;
    }

    public static String calculateLeagueName(int league) {
        String[] names = {"Miš", "Vuk", "Medved", "Lav", "Orao", "Zmaj"};
        if (league >= 0 && league < names.length) return names[league];
        return "Nepoznato";
    }

    public static String getLeagueEmoji(long league) {
        switch ((int) league) {
            case 1: return "🐺";
            case 2: return "🐻";
            case 3: return "🦁";
            case 4: return "🦅";
            case 5: return "🐉";
            default: return "🐭";
        }
    }

    public static void updateStars(String userId, int starChange) {
        if (userId == null) return;

        DocumentReference userRef = db.collection("users").document(userId);

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(userRef);
            
            long currentStars = snapshot.getLong("stars") != null ? snapshot.getLong("stars") : 0;
            long currentWeekly = snapshot.getLong("starsWeekly") != null ? snapshot.getLong("starsWeekly") : 0;
            long currentMonthly = snapshot.getLong("starsMonthly") != null ? snapshot.getLong("starsMonthly") : 0;
            long currentLeague = snapshot.getLong("league") != null ? snapshot.getLong("league") : 0L;

            // 1. Osiguravamo da zvezde ne odu ispod nule (Spec 3.d.iv)
            long newStars = Math.max(0, currentStars + starChange);
            long newWeekly = Math.max(0, currentWeekly + starChange);
            long newMonthly = Math.max(0, currentMonthly + starChange);

            // 2. Automatsko računanje LIGE na osnovu ukupnih zvezda
            // Formula: L1=100, L2=200, L3=400, L4=800, L5=1600
            int newLeague = calculateLeague(newStars);

            transaction.update(userRef, 
                "stars", newStars,
                "starsWeekly", newWeekly,
                "starsMonthly", newMonthly,
                "league", (long) newLeague
            );

            // Obaveštavanje o promeni lige
            if (newLeague != (int) currentLeague) {
                String title = newLeague > (int) currentLeague ? "Napredovali ste u novu ligu!" : "Obaveštenje: Ispali ste u nižu ligu.";
                String[] leagueNames = {"Miš", "Vuk", "Medved", "Lav", "Orao", "Zmaj"};
                String body = "Sada ste u ligi: " + leagueNames[newLeague];
                
                java.util.Map<String, Object> pending = new java.util.HashMap<>();
                pending.put("oldLeague", currentLeague);
                pending.put("newLeague", (long) newLeague);
                pending.put("title", title);
                pending.put("body", body);
                
                transaction.update(userRef, "pendingLeagueChange", pending);
            }

            // Ažuriraj zvezde regiona (mesečna lista)
            String region = snapshot.getString("region");
            if (region != null && !region.isEmpty() && starChange > 0) {
                DocumentReference regionRef = db.collection("regions").document(region);
                java.util.Map<String, Object> regUpdate = new java.util.HashMap<>();
                regUpdate.put("starsMonthly", FieldValue.increment(starChange));
                transaction.set(regionRef, regUpdate, SetOptions.merge());
            }

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

        // Pronađi top 50 da znamo ko dobija nagradu, a ko gubi zvezde (ako je mesečni reset)
        db.collection("users")
            .orderBy(starsField, Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snap -> {
                WriteBatch batch = db.batch();
                int rank = 0;
                
                // Set to track who was in top 50 (or whatever threshold) to avoid 30% reduction
                java.util.Set<String> rankedUserIds = new java.util.HashSet<>();

                for (QueryDocumentSnapshot doc : snap) {
                    long starsVal = doc.getLong(starsField) != null ? doc.getLong(starsField) : 0;
                    
                    // Spec 8.e: Samo Top 3 igrača zadržavaju sve zvezde, ostali gube 30% (ako je mesečni reset)
                    if (rank < 3 && starsVal > 0) {
                        rankedUserIds.add(doc.getId());
                    }

                    if (starsVal > 0 && rank < tokenRewards.length) {
                        int earned = tokenRewards[rank];
                        batch.update(doc.getReference(), "tokens", FieldValue.increment(earned));
                        
                        java.util.Map<String, Object> pending = new java.util.HashMap<>();
                        pending.put("tokens", (long) earned);
                        pending.put("rank", (long) (rank + 1));
                        pending.put("period", periodName);
                        batch.update(doc.getReference(), "pendingReward", pending);
                    }
                    batch.update(doc.getReference(), starsField, 0L);
                    rank++;
                }
                
                // Spec 8.e: Ukoliko se igrač ne plasira na mesečnoj rang listi, gubi 30% zvezda
                if (!isWeekly) {
                    applyMonthlyStarReduction(rankedUserIds);
                }

                batch.commit().addOnSuccessListener(v -> {
                    if (isWeekly) {
                        if (context != null) {
                            NotificationHelper.sendRealNotification(
                                    context,
                                    periodName + " ciklus je završen!",
                                    "Rang lista je resetovana. Nagrade su podeljene top 10 igračima.",
                                    NotificationHelper.CHANNEL_RANKING
                            );
                        }
                    } else {
                        handleRegionalReset(context);
                    }
                });
            });
    }

    private static void applyMonthlyStarReduction(java.util.Set<String> rankedUserIds) {
        // We need to fetch all users who are NOT in rankedUserIds
        db.collection("users").get().addOnSuccessListener(allUsers -> {
            WriteBatch reductionBatch = db.batch();
            int count = 0;
            for (QueryDocumentSnapshot userDoc : allUsers) {
                if (!rankedUserIds.contains(userDoc.getId())) {
                    Long starsLong = userDoc.getLong("stars");
                    long currentStars = starsLong != null ? starsLong : 0L;
                    if (currentStars > 0) {
                        long newStars = (long) (currentStars * 0.7); // lose 30%, keep 70%
                        reductionBatch.update(userDoc.getReference(), "stars", newStars);
                        
                        // Recalculate league after reduction
                        int newLeague = calculateLeague(newStars);
                        reductionBatch.update(userDoc.getReference(), "league", (long) newLeague);

                        // Spec 8.f: Obavesti korisnika o padu u ligu (ako se desilo)
                        int oldL = userDoc.getLong("league") != null ? userDoc.getLong("league").intValue() : 0;
                        if (newLeague != oldL) {
                            java.util.Map<String, Object> pending = new java.util.HashMap<>();
                            pending.put("oldLeague", (long) oldL);
                            pending.put("newLeague", (long) newLeague);
                            pending.put("title", "Obaveštenje o ligi");
                            pending.put("body", "Sada ste u ligi: " + calculateLeagueName(newLeague));
                            reductionBatch.update(userDoc.getReference(), "pendingLeagueChange", pending);
                        }
                        count++;
                    }
                }
                if (count >= 450) { // Stay within 500 batch limit
                    reductionBatch.commit();
                    reductionBatch = db.batch();
                    count = 0;
                }
            }
            reductionBatch.commit();
        });
    }

    private static void handleRegionalReset(Context context) {
        db.collection("regions")
                .orderBy("starsMonthly", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    WriteBatch batch = db.batch();
                    java.util.List<String> topRegions = new java.util.ArrayList<>();
                    int rank = 1;
                    for (QueryDocumentSnapshot doc : snap) {
                        if (rank <= 3) {
                            topRegions.add(doc.getId());
                            String field = (rank == 1) ? "firstPlaces" : (rank == 2) ? "secondPlaces" : "thirdPlaces";
                            batch.update(doc.getReference(), field, FieldValue.increment(1));
                        }
                        batch.update(doc.getReference(), "starsMonthly", 0L);
                        rank++;
                    }

                    batch.update(db.collection("system").document("cycleState"),
                            "topRegionsLastMonth", topRegions);

                    batch.commit().addOnSuccessListener(v -> {
                        if (context != null) {
                            NotificationHelper.sendRealNotification(
                                    context,
                                    "Mesečni ciklus je završen!",
                                    "Regionalna rang lista je resetovana. Proverite uspeh vašeg regiona!",
                                    NotificationHelper.CHANNEL_RANKING
                            );
                        }
                    });
                });
    }

    // Spec 3a + 6b: svaki dan +5 tokena + broj liga bonus tokena
    public static void grantDailyTokensIfNeeded(String userId) {
        if (userId == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        DocumentReference userRef = db.collection("users").document(userId);

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(userRef);
            String lastDate = snapshot.getString("lastDailyTokenDate");
            if (today.equals(lastDate)) return null;

            long league = snapshot.getLong("league") != null ? snapshot.getLong("league") : 0L;
            long tokensToGrant = 5 + league; // Spec 3a: 5 baznih + Spec 6b: 1 po ligi

            transaction.update(userRef,
                    "tokens", FieldValue.increment(tokensToGrant),
                    "lastDailyTokenDate", today);
            return tokensToGrant;
        });
    }

    public static void completeMission(String userId, String missionKey) {
        completeMission(userId, missionKey, null);
    }

    public static void completeMission(String userId, String missionKey, Runnable onNewlyCompleted) {
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
                    updateStars(userId, 3);
                    return Boolean.TRUE;
                }
                return Boolean.FALSE;
            } else {
                java.util.Map<String, Object> init = new java.util.HashMap<>();
                init.put("win_game", false);
                init.put("send_chat", false);
                init.put("friend_game", false);
                init.put("tournament_win", false);
                init.put("bonus_claimed", false);
                init.put(missionKey, true);
                transaction.set(missionRef, init);
                updateStars(userId, 3);
                return Boolean.TRUE;
            }
        }).addOnSuccessListener(result -> {
            if (Boolean.TRUE.equals(result) && onNewlyCompleted != null) {
                onNewlyCompleted.run();
            }
        });
    }
}
