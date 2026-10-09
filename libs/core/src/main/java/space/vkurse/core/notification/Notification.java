/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import space.vkurse.core.domain.AggregateRoot;

/**
 * A submitted message and what became of it. It waits as {@code PENDING} until a delivery attempt succeeds
 * ({@code SENT}) or the last allowed one fails ({@code FAILED}). The row is kept for {@link #RETENTION} as a record.
 */
@Entity
@Table(name = "notifications")
public class Notification extends AggregateRoot {
    /** How long to wait after the first, second, … failed delivery. One more failure after the last wait gives up. */
    public static final List<Duration> RETRY_AFTER =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1));

    public static final int MAX_ATTEMPTS = RETRY_AFTER.size() + 1;

    /** How long a sent or failed notification is kept. */
    public static final Duration RETENTION = Duration.ofDays(30);

    public static final int MAX_ERROR_LENGTH = 1000;

    @JdbcTypeCode(SqlTypes.JSON)
    private Message message;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    /** Deliveries tried so far. */
    private int attempts;

    /** When to try next; absent once the notification is sent or failed. */
    private @Nullable Instant nextAttemptAt;

    private @Nullable Instant sentAt;

    /** The channel that delivered it. */
    @Enumerated(EnumType.STRING)
    private @Nullable Channel channel;

    /** Why the last delivery failed. */
    private @Nullable String lastError;

    protected Notification() {}

    private Notification(Message message, Instant now) {
        super(now);
        this.message = message;
        this.nextAttemptAt = now;
    }

    /** Accepts a message for delivery. It is due at once. */
    public static Notification submit(Message message, Instant now) {
        return new Notification(message, now);
    }

    public boolean isDue(Instant now) {
        return status == Status.PENDING && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
    }

    /** Records a successful delivery. Does nothing if the notification is no longer waiting. */
    public void delivered(Delivery delivery, Instant now) {
        if (status != Status.PENDING) {
            return;
        }
        attempts++;
        status = Status.SENT;
        channel = delivery.channel();
        sentAt = now;
        nextAttemptAt = null;
    }

    /**
     * Records a failed delivery: the next one is scheduled after the wait in {@link #RETRY_AFTER}, and the
     * {@link #MAX_ATTEMPTS}th failure gives up. Does nothing if the notification is no longer waiting.
     */
    public void failed(@Nullable String error, Instant now) {
        if (status != Status.PENDING) {
            return;
        }
        attempts++;
        lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        if (attempts >= MAX_ATTEMPTS) {
            status = Status.FAILED;
            nextAttemptAt = null;
        } else {
            nextAttemptAt = now.plus(RETRY_AFTER.get(attempts - 1));
        }
    }

    public Message getMessage() {
        return message;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public @Nullable Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public @Nullable Instant getSentAt() {
        return sentAt;
    }

    public @Nullable Channel getChannel() {
        return channel;
    }

    public @Nullable String getLastError() {
        return lastError;
    }

    public enum Status {
        /** Waiting for its next delivery attempt. */
        PENDING,
        SENT,
        /** Given up after {@link #MAX_ATTEMPTS} failed deliveries. */
        FAILED
    }
}
