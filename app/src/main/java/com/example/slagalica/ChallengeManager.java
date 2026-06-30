package com.example.slagalica;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Upravlja Firestore logikom za Izazov (Spec 9).
 * Kolekcija: challenges/{challengeId}
 *
 * Polja dokumenta:
 *  - creatorId, creatorName, region
 *  - stakeStars, stakeTokens   (Spec 9b: max 10 zvezda, max 2 tokena)
 *  - status: "open" | "active" | "finished"
 *  - participants: Map<userId, {name, avatar, score, finished}>
 *  - maxParticipants: 4 (kreator + 3 dodatna - Spec 9a)
 *  - createdAt
 *  - results: Map<userId, payout> (popunjava se na kraju)
 */
public class ChallengeManager {

    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();
    public static final int MAX_ADDITIONAL_PLAYERS = 3; // Spec 9a
    public static final int MAX_STAKE_STARS = 10;        // Spec 9b
    public static final int MAX_STAKE_TOKENS = 2;        // Spec 9b

    public interface ChallengeCallback {
        void onSuccess(String challengeId);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    /**
     * Spec 9b: Igrač kreira izazov i licitira ulog (max 10 zvezda, max 2 tokena).
     * Ulog se odmah rezerviše (oduzima) od kreatora.
     */
    public static void createChallenge(String userId, String userName, String avatar,
                                       String region, int stakeStars, int stakeTokens,
                                       ChallengeCallback callback) {
        int stars = Math.min(Math.max(stakeStars, 0), MAX_STAKE_STARS);
        int tokens = Math.min(Math.max(stakeTokens, 0), MAX_STAKE_TOKENS);

        DocumentReference userRef = db.collection("users").document(userId);

        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot userDoc = transaction.get(userRef);
                    long currentStars = userDoc.getLong("stars") != null ? userDoc.getLong("stars") : 0;
                    long currentTokens = userDoc.getLong("tokens") != null ? userDoc.getLong("tokens") : 0;

                    if (currentStars < stars || currentTokens < tokens) {
                        throw new RuntimeException("Nemaš dovoljno zvezda ili tokena za ovaj ulog.");
                    }

                    transaction.update(userRef,
                            "stars", currentStars - stars,
                            "tokens", currentTokens - tokens);

                    String challengeId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    DocumentReference challengeRef = db.collection("challenges").document(challengeId);

                    Map<String, Object> challenge = new HashMap<>();
                    challenge.put("creatorId", userId);
                    challenge.put("creatorName", userName);
                    challenge.put("region", region);
                    challenge.put("stakeStars", stars);
                    challenge.put("stakeTokens", tokens);
                    challenge.put("status", "open");
                    challenge.put("maxParticipants", MAX_ADDITIONAL_PLAYERS + 1);
                    challenge.put("createdAt", System.currentTimeMillis());

                    Map<String, Object> participant = new HashMap<>();
                    participant.put("name", userName);
                    participant.put("avatar", avatar);
                    participant.put("score", 0L);
                    participant.put("finished", false);

                    Map<String, Object> participants = new HashMap<>();
                    participants.put(userId, participant);
                    challenge.put("participants", participants);

                    transaction.set(challengeRef, challenge);
                    return challengeId;
                }).addOnSuccessListener(result -> callback.onSuccess((String) result))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Spec 9c: Igrač koji prihvata izazov ulaže istu količinu zvezda/tokena kao kreator.
     */
    public static void joinChallenge(String challengeId, String userId, String userName,
                                     String avatar, SimpleCallback callback) {
        DocumentReference challengeRef = db.collection("challenges").document(challengeId);
        DocumentReference userRef = db.collection("users").document(userId);

        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot challengeDoc = transaction.get(challengeRef);
                    if (!challengeDoc.exists()) throw new RuntimeException("Izazov ne postoji.");

                    String status = challengeDoc.getString("status");
                    if (!"open".equals(status)) throw new RuntimeException("Izazov više nije otvoren za prijave.");

                    Map<String, Object> participants = (Map<String, Object>) challengeDoc.get("participants");
                    if (participants == null) participants = new HashMap<>();
                    if (participants.containsKey(userId)) throw new RuntimeException("Već si u ovom izazovu.");

                    long maxParticipants = challengeDoc.getLong("maxParticipants") != null
                            ? challengeDoc.getLong("maxParticipants") : 4;
                    if (participants.size() >= maxParticipants) throw new RuntimeException("Izazov je popunjen.");

                    long stakeStars = challengeDoc.getLong("stakeStars") != null ? challengeDoc.getLong("stakeStars") : 0;
                    long stakeTokens = challengeDoc.getLong("stakeTokens") != null ? challengeDoc.getLong("stakeTokens") : 0;

                    com.google.firebase.firestore.DocumentSnapshot userDoc = transaction.get(userRef);
                    long currentStars = userDoc.getLong("stars") != null ? userDoc.getLong("stars") : 0;
                    long currentTokens = userDoc.getLong("tokens") != null ? userDoc.getLong("tokens") : 0;

                    if (currentStars < stakeStars || currentTokens < stakeTokens) {
                        throw new RuntimeException("Nemaš dovoljno zvezda ili tokena da prihvatiš ovaj izazov.");
                    }

                    transaction.update(userRef,
                            "stars", currentStars - stakeStars,
                            "tokens", currentTokens - stakeTokens);

                    Map<String, Object> participant = new HashMap<>();
                    participant.put("name", userName);
                    participant.put("avatar", avatar);
                    participant.put("score", 0L);
                    participant.put("finished", false);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("participants." + userId, participant);

                    // Spec 9a: kad se popuni (kreator + 3), izazov prelazi u "active"
                    if (participants.size() + 1 >= maxParticipants) {
                        updates.put("status", "active");
                    }

                    transaction.update(challengeRef, updates);
                    return null;
                }).addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Igrač ručno započinje izazov (ako ne čeka da se popuni do max broja igrača).
     * Kreator može da pokrene partiju i sa manje od 4 učesnika.
     */
    public static void startChallenge(String challengeId, SimpleCallback callback) {
        db.collection("challenges").document(challengeId)
                .update("status", "active")
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Igrač upisuje svoj konačni rezultat (zbir bodova kroz svih 6 igara) nakon
     * što završi solo partiju u ChallengeGameActivity.
     */
    public static void submitScore(String challengeId, String userId, long score,
                                   SimpleCallback callback) {
        DocumentReference challengeRef = db.collection("challenges").document(challengeId);

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot doc = transaction.get(challengeRef);
            Map<String, Object> participants = (Map<String, Object>) doc.get("participants");
            if (participants == null || !participants.containsKey(userId)) {
                throw new RuntimeException("Nisi učesnik ovog izazova.");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("participants." + userId + ".score", score);
            updates.put("participants." + userId + ".finished", true);
            transaction.update(challengeRef, updates);

            // Proveri da li su svi učesnici završili
            boolean allFinished = true;
            for (String pid : participants.keySet()) {
                Map<String, Object> p = (Map<String, Object>) participants.get(pid);
                boolean finished = pid.equals(userId) || (p != null && Boolean.TRUE.equals(p.get("finished")));
                if (!finished) { allFinished = false; break; }
            }

            if (allFinished) {
                transaction.update(challengeRef, "status", "finished");
            }
            return allFinished;
        }).addOnSuccessListener(allFinished -> {
            if (Boolean.TRUE.equals(allFinished)) {
                distributeRewards(challengeId);
            }
            callback.onSuccess();
        }).addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Spec 9d: Raspodela nagrada kad svi učesnici završe.
     * - 1. mesto: 75% ukupno uloženog
     * - 2. mesto: vraćeno tačno ono što je uložio
     * - ostali: ništa nazad (ulog je izgubljen)
     */
    private static void distributeRewards(String challengeId) {
        DocumentReference challengeRef = db.collection("challenges").document(challengeId);

        challengeRef.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;

            Map<String, Object> participants = (Map<String, Object>) doc.get("participants");
            if (participants == null || participants.isEmpty()) return;

            long stakeStars = doc.getLong("stakeStars") != null ? doc.getLong("stakeStars") : 0;
            long stakeTokens = doc.getLong("stakeTokens") != null ? doc.getLong("stakeTokens") : 0;
            int participantCount = participants.size();

            long totalStars = stakeStars * participantCount;
            long totalTokens = stakeTokens * participantCount;

            // Sortiraj učesnike po bodovima opadajuće
            List<Map.Entry<String, Long>> ranked = new ArrayList<>();
            for (String uid : participants.keySet()) {
                Map<String, Object> p = (Map<String, Object>) participants.get(uid);
                long score = 0;
                if (p != null && p.get("score") != null) {
                    score = ((Number) p.get("score")).longValue();
                }
                ranked.add(new java.util.AbstractMap.SimpleEntry<>(uid, score));
            }
            ranked.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            WriteBatch batch = db.batch();
            Map<String, Object> resultsMap = new HashMap<>();

            for (int i = 0; i < ranked.size(); i++) {
                String uid = ranked.get(i).getKey();
                DocumentReference userRef = db.collection("users").document(uid);

                long starsPayout = 0;
                long tokensPayout = 0;

                if (i == 0) {
                    // Spec 9d: pobednik osvaja 75% ukupno uloženog
                    starsPayout = Math.round(totalStars * 0.75);
                    tokensPayout = Math.round(totalTokens * 0.75);
                } else if (i == 1) {
                    // Spec 9d: drugi po broju poena dobija nazad svoj ulog
                    starsPayout = stakeStars;
                    tokensPayout = stakeTokens;
                }
                // ostali (i >= 2): ništa nazad

                if (starsPayout > 0) {
                    batch.update(userRef, "stars", FieldValue.increment(starsPayout));
                }
                if (tokensPayout > 0) {
                    batch.update(userRef, "tokens", FieldValue.increment(tokensPayout));
                }

                Map<String, Object> resultEntry = new HashMap<>();
                resultEntry.put("rank", i + 1);
                resultEntry.put("starsWon", starsPayout);
                resultEntry.put("tokensWon", tokensPayout);
                resultsMap.put(uid, resultEntry);

                // Notifikacija svakom učesniku
                if (i == 0) {
                    sendChallengeNotification(uid, "Pobedio si u izazovu!",
                            "Osvojio si " + starsPayout + " ⭐ i " + tokensPayout + " 🎟️");
                } else if (i == 1) {
                    sendChallengeNotification(uid, "Izazov završen",
                            "Bio si drugi — ulog ti je vraćen (" + starsPayout + " ⭐, " + tokensPayout + " 🎟️)");
                } else {
                    sendChallengeNotification(uid, "Izazov završen",
                            "Nažalost, nisi se plasirao među prva dva mesta.");
                }
            }

            batch.update(challengeRef, "results", resultsMap);
            batch.update(challengeRef, "status", "finished");
            batch.commit();
        });
    }

    private static void sendChallengeNotification(String userId, String title, String body) {
        // Upisujemo u korisnikov pendingChallengeResult da UI može prikazati dijalog
        // kad sledeći put otvori app/stranicu sa rezultatima (Spec 9e)
        Map<String, Object> pending = new HashMap<>();
        pending.put("title", title);
        pending.put("body", body);
        db.collection("users").document(userId)
                .update("pendingChallengeResult", pending);
    }

    /**
     * Igrač napušta/otkazuje izazov pre nego što počne (status "open").
     * Vraća uloženo.
     */
    public static void leaveChallenge(String challengeId, String userId, SimpleCallback callback) {
        DocumentReference challengeRef = db.collection("challenges").document(challengeId);

        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot doc = transaction.get(challengeRef);
                    if (!doc.exists()) throw new RuntimeException("Izazov ne postoji.");

                    String status = doc.getString("status");
                    if (!"open".equals(status)) throw new RuntimeException("Izazov je već počeo, ne možeš ga napustiti.");

                    long stakeStars = doc.getLong("stakeStars") != null ? doc.getLong("stakeStars") : 0;
                    long stakeTokens = doc.getLong("stakeTokens") != null ? doc.getLong("stakeTokens") : 0;

                    DocumentReference userRef = db.collection("users").document(userId);
                    com.google.firebase.firestore.DocumentSnapshot userDoc = transaction.get(userRef);
                    long currentStars = userDoc.getLong("stars") != null ? userDoc.getLong("stars") : 0;
                    long currentTokens = userDoc.getLong("tokens") != null ? userDoc.getLong("tokens") : 0;

                    transaction.update(userRef,
                            "stars", currentStars + stakeStars,
                            "tokens", currentTokens + stakeTokens);

                    String creatorId = doc.getString("creatorId");
                    if (userId.equals(creatorId)) {
                        // Kreator otkazuje ceo izazov
                        transaction.update(challengeRef, "status", "cancelled");
                    } else {
                        transaction.update(challengeRef, "participants." + userId, FieldValue.delete());
                    }
                    return null;
                }).addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}