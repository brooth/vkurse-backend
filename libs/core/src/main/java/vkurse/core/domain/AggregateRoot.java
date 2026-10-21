/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
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
        this.id = UuidV7.at(now);
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
}
