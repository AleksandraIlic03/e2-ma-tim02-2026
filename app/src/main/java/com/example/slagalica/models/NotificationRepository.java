package com.example.slagalica.models;

import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {
    private static List<Notification> notifications = new ArrayList<>();

    static {
        notifications.add(new Notification("1", "Nedeljna nagrada", "Osvojili ste 3 tokena za 2. mesto na rang listi!", "pre 1h", "rewards", false));
        notifications.add(new Notification("2", "Promocija u Ligu", "Čestitamo! Prešli ste u 'Prvu ligu'!", "pre 2h", "ranking", false));
        notifications.add(new Notification("3", "Dnevne misije", "Sve misije ispunjene! Dobili ste 2 tokena.", "juče", "rewards", true));
    }

    public static List<Notification> getAll() {
        return notifications;
    }

    public static void addNotification(Notification n) {
        notifications.add(0, n);
    }
}
