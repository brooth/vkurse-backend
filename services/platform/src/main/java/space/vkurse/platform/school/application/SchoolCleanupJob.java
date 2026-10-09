/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.application;

import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import space.vkurse.platform.school.domain.SchoolInvitationRepository;

/** Removes the invitations nobody answered in time, hourly. */
@Component
class SchoolCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(SchoolCleanupJob.class);

    private final SchoolInvitationRepository invitations;
    private final Clock clock;

    SchoolCleanupJob(SchoolInvitationRepository invitations, Clock clock) {
        this.invitations = invitations;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 * * * *")
    @SchedulerLock(name = "school-cleanup")
    @Transactional
    public void deleteExpired() {
        var deleted = invitations.deleteExpired(clock.instant());
        log.info("School cleanup deleted {} expired invitations", deleted);
    }
}
