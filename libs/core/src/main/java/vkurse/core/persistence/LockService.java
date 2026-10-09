/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.persistence;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cluster-wide locks for critical sections outside scheduled jobs. Scheduled jobs use {@code @SchedulerLock} instead.
 *
 * <pre>{@code
 * locks.withLock("school-renewal:" + schoolId, Duration.ofMinutes(1), () -> renew(schoolId))
 *     .orElseThrow(() -> new ConflictException("Renewal is already running"));
 * }</pre>
 */
@Service
public class LockService {
    private static final Logger log = LoggerFactory.getLogger(LockService.class);

    private final LockingTaskExecutor executor;
    private final Clock clock;

    public LockService(LockProvider lockProvider, Clock clock) {
        this.executor = new DefaultLockingTaskExecutor(lockProvider);
        this.clock = clock;
    }

    /**
     * Runs {@code task} while holding the lock {@code name}. Returns empty, without running the task, if another node
     * holds the lock. The lock is released when the task ends, or after {@code lockAtMostFor} if the node dies.
     */
    public <T> Optional<T> withLock(String name, Duration lockAtMostFor, Supplier<T> task) {
        var config = new LockConfiguration(clock.instant(), name, lockAtMostFor, Duration.ZERO);
        try {
            var result = executor.executeWithLock(task::get, config);
            if (!result.wasExecuted()) {
                log.debug("Lock {} is held by another node, skipping", name);
                return Optional.empty();
            }
            return Optional.ofNullable(result.getResult());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            // Supplier cannot throw checked exceptions; ShedLock's signature just declares Throwable.
            throw new IllegalStateException("Task under lock " + name + " failed", e);
        }
    }
}
