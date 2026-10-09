/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Base class for aggregates: an identity, timestamps, a version for optimistic locking and the domain events raised
 * since the last save. Not an entity itself (no table): its fields become columns of every subclass's table.
 *
 * <p>An aggregate has no setters. Its state changes only through methods that keep its rules, and it is created by a
 * static factory ({@code User.register(…)}), which passes the creation time to {@link #AggregateRoot(Instant)}.
 */
@MappedSuperclass
public abstract class AggregateRoot {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private @Nullable Instant updatedAt;

    // A wrapper, so Hibernate tells a new aggregate (null) from a saved one although the id is assigned up front.
    @Version
    private @Nullable Long version;

    @Transient
    private final List<DomainEvent> events = new ArrayList<>();

    /**
     * For Hibernate, which fills the fields right after this when it loads a row. Subclasses declare their own
     * protected no-arg constructor.
     */
    protected AggregateRoot() {}

    /** A new aggregate, with a time-ordered id that exists before the first save. */
    protected AggregateRoot(Instant now) {
        this.id = newId(now);
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Whether the aggregate has never been saved. */
    public boolean isNew() {
        return version == null;
    }

    /** Records an event. The repository publishes it when the aggregate is saved, in the same transaction. */
    protected void raise(DomainEvent event) {
        events.add(event);
    }

    /** Called by the repository: returns the events raised since the last save and forgets them. */
    public List<DomainEvent> pullEvents() {
        var pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    /** Called by the repository when it writes a change to an aggregate that was already saved. */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    @Override
    public final boolean equals(@Nullable Object other) {
        return other instanceof AggregateRoot that && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    /** A UUIDv7 (RFC 9562): 48 bits of milliseconds, then random bits, so ids sort by creation time. */
    private static UUID newId(Instant now) {
        var random = new byte[10];
        RANDOM.nextBytes(random);
        long high = (now.toEpochMilli() << 16) | 0x7000L | ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);
        long low = 0x8000000000000000L | ((random[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            low |= (random[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(high, low);
    }
}
