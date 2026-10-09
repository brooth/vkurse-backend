/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.storage.application;

import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import space.vkurse.core.domain.AggregateRoot;
import space.vkurse.platform.storage.domain.FileStorage;
import space.vkurse.platform.storage.domain.StoredFileRepository;

/**
 * Removes deleted files and uploads that expired unconfirmed, every 10 minutes: first their objects in storage, then
 * their rows. If storage fails, the rows stay and the next run tries again.
 */
@Component
class StorageCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupJob.class);

    private static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES = 20;

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final Clock clock;
    private final TransactionTemplate transaction;

    StorageCleanupJob(
            StoredFileRepository files,
            FileStorage storage,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.files = files;
        this.storage = storage;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "0 */10 * * * *")
    @SchedulerLock(name = "storage-cleanup")
    public void removeDeleted() {
        var removed = 0;
        for (var batch = 0; batch < MAX_BATCHES; batch++) {
            var removable = files.findRemovable(clock.instant(), BATCH_SIZE);
            if (removable.isEmpty()) {
                break;
            }
            // Not in a transaction: this is a call to the storage server.
            storage.delete(removable);
            var ids = removable.stream().map(AggregateRoot::getId).toList();
            transaction.executeWithoutResult(status -> files.removeAll(ids));
            removed += ids.size();
        }
        if (removed > 0) {
            log.info("Storage cleanup removed {} files", removed);
        }
    }
}
