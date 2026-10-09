/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.notification;

import java.time.Instant;
import java.util.List;
import vkurse.core.persistence.JpaAggregateRepository;

class NotificationRepository extends JpaAggregateRepository<Notification> {

    NotificationRepository() {
        super(Notification.class);
    }

    /** Notifications whose next delivery attempt is due, the longest-waiting first. */
    List<Notification> findDue(Instant now, int limit) {
        return em.createQuery(
                        "SELECT n FROM Notification n WHERE n.status = :pending AND n.nextAttemptAt <= :now"
                                + " ORDER BY n.nextAttemptAt",
                        Notification.class)
                .setParameter("pending", Notification.Status.PENDING)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    /** Removes sent and failed notifications created before {@code before}. */
    int deleteFinishedBefore(Instant before) {
        return em.createQuery("DELETE FROM Notification n WHERE n.status <> :pending AND n.createdAt < :before")
                .setParameter("pending", Notification.Status.PENDING)
                .setParameter("before", before)
                .executeUpdate();
    }
}
