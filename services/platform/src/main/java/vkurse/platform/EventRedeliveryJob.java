/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers again the events whose listener failed, or never ran because the node stopped. An event is stored with the
 * change that raised it and marked completed when its listener returns; one that isn't completed after a minute is
 * handed to its listener again, then after longer and longer pauses, up to half an hour. Spring Modulith does none of
 * this by itself. So a listener may get an event twice, and must leave things as they are the second time.
 */
@Component
class EventRedeliveryJob {
    private static final Logger log = LoggerFactory.getLogger(EventRedeliveryJob.class);

    /** A listener that still runs is left alone for this long. */
    private static final Duration MIN_AGE = Duration.ofMinutes(1);

    private static final Duration MAX_PAUSE = Duration.ofMinutes(30);

    /** How many events one run delivers again. */
    private static final int BATCH_SIZE = 100;

    private final IncompleteEventPublications publications;
    private final Clock clock;

    EventRedeliveryJob(IncompleteEventPublications publications, Clock clock) {
        this.publications = publications;
        this.clock = clock;
    }

    @Scheduled(cron = "45 * * * * *")
    @SchedulerLock(name = "event-redelivery")
    public void redeliver() {
        var now = clock.instant();
        var redelivered = new AtomicInteger();
        publications.resubmitIncompletePublications(ResubmissionOptions.defaults()
                .withMinAge(MIN_AGE)
                .withBatchSize(BATCH_SIZE)
                .withFilter(publication -> {
                    if (!isDue(publication, now)) {
                        return false;
                    }
                    redelivered.incrementAndGet();
                    log.warn(
                            "Delivering event {} ({}) again, attempt {}",
                            publication.getIdentifier(),
                            publication.getEvent().getClass().getSimpleName(),
                            publication.getCompletionAttempts() + 1);
                    return true;
                }));
        if (redelivered.get() > 0) {
            log.info("Delivered {} events again", redelivered.get());
        }
    }

    /** Each attempt waits a minute longer than the one before it. */
    private static boolean isDue(EventPublication publication, Instant now) {
        var last = publication.getLastResubmissionDate();
        if (last == null) {
            return true;
        }
        var pause = MIN_AGE.multipliedBy(Math.max(1, publication.getCompletionAttempts()));
        return !now.isBefore(last.plus(pause.compareTo(MAX_PAUSE) > 0 ? MAX_PAUSE : pause));
    }
}
