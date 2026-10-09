/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.application;

import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vkurse.core.auth.domain.OtpChallengeRepository;
import ru.vkurse.core.auth.domain.PendingOAuthRequestRepository;
import ru.vkurse.core.auth.domain.SessionRepository;

/** Removes expired sessions, OTP challenges and unfinished OAuth sign-ins, hourly. */
@Component
class AuthCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(AuthCleanupJob.class);

    private final SessionRepository sessions;
    private final OtpChallengeRepository challenges;
    private final PendingOAuthRequestRepository oauthRequests;
    private final Clock clock;

    AuthCleanupJob(
            SessionRepository sessions,
            OtpChallengeRepository challenges,
            PendingOAuthRequestRepository oauthRequests,
            Clock clock) {
        this.sessions = sessions;
        this.challenges = challenges;
        this.oauthRequests = oauthRequests;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "auth-cleanup")
    @Transactional
    public void deleteExpired() {
        var now = clock.instant();
        var deleted = sessions.deleteExpired(now) + challenges.deleteExpired(now) + oauthRequests.deleteExpired(now);
        log.info("Auth cleanup deleted {} expired rows", deleted);
    }
}
