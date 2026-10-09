/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.common.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import ru.vkurse.common.domain.AggregateRoot;

/**
 * Database access for one aggregate type: the only code that uses the {@link EntityManager}. A module declares a
 * repository interface in its {@code domain} package and implements it in {@code infrastructure} by extending this
 * class. Every query beyond the ones here is a named method on that interface ({@code findByEmail}), written in JPQL.
 *
 * <pre>{@code
 * @Repository
 * class JpaUserRepository extends JpaAggregateRepository<User> implements UserRepository {
 *     JpaUserRepository() { super(User.class); }
 * }
 * }</pre>
 *
 * A repository has no transaction of its own: it joins the one its application service started.
 */
public abstract class JpaAggregateRepository<A extends AggregateRoot> {
    private static final HibernateJpaDialect DIALECT = new HibernateJpaDialect();

    protected final Class<A> type;

    // Base-class infrastructure is field-injected so repositories don't pass it through super(...).
    @PersistenceContext
    protected EntityManager em;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private Clock clock;

    protected JpaAggregateRepository(Class<A> type) {
        this.type = type;
    }

    public Optional<A> findById(UUID id) {
        return Optional.ofNullable(em.find(type, id));
    }

    /** Loads the aggregate and locks its row until the transaction ends, so concurrent callers wait for each other. */
    public Optional<A> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(em.find(type, id, LockModeType.PESSIMISTIC_WRITE));
    }

    /**
     * Writes a new or changed aggregate and publishes the events it raised. Call it after every change: the write is
     * flushed here, so a constraint violation surfaces inside the call as a Spring {@link DataAccessException}.
     */
    public A save(A aggregate) {
        if (aggregate.isNew()) {
            em.persist(aggregate);
        } else {
            aggregate.touch(clock.instant());
        }
        flush();
        aggregate.pullEvents().forEach(events::publishEvent);
        return aggregate;
    }

    /** Removes the row, and publishes the events the aggregate raised before it was removed. */
    public void remove(A aggregate) {
        em.remove(aggregate);
        flush();
        aggregate.pullEvents().forEach(events::publishEvent);
    }

    /** Flushes pending writes and translates persistence errors into Spring {@link DataAccessException}s. */
    protected void flush() {
        try {
            em.flush();
        } catch (RuntimeException e) {
            DataAccessException translated = DIALECT.translateExceptionIfPossible(e);
            throw translated != null ? translated : e;
        }
    }
}
