/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.platform.billing.domain.StoragePriceRepository;
import vkurse.platform.billing.domain.StorageUsageRepository;
import vkurse.platform.storage.application.StoredFileService;

/**
 * Charges every school for the day's storage. It runs every 10 minutes and charges a school once per day (Moscow
 * time): the first run after midnight does the work, and the later ones pick up what failed or was missed.
 */
@Component
class StorageChargeJob {
    private static final Logger log = LoggerFactory.getLogger(StorageChargeJob.class);

    private final StoredFileService files;
    private final StoragePriceRepository prices;
    private final StorageUsageRepository usages;
    private final StorageCharger charger;
    private final Clock clock;
    private final TransactionTemplate transaction;

    StorageChargeJob(
            StoredFileService files,
            StoragePriceRepository prices,
            StorageUsageRepository usages,
            StorageCharger charger,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.files = files;
        this.prices = prices;
        this.usages = usages;
        this.charger = charger;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "0 2/10 * * * *")
    @SchedulerLock(name = "storage-charge")
    public void chargeToday() {
        var day = LocalDate.now(clock.withZone(BillingNotifier.MOSCOW));
        var price = Objects.requireNonNull(transaction.execute(status -> prices.findInForce(day)))
                .orElse(null);
        if (price == null) {
            log.error("No storage price is in force on {}, nobody is charged", day);
            return;
        }
        var measured = Objects.requireNonNull(transaction.execute(status -> usages.findMeasuredSchools(day)));
        var charged = 0;
        for (var stored : files.usageBySchool().entrySet()) {
            var schoolId = stored.getKey();
            if (measured.contains(schoolId)) {
                continue;
            }
            try {
                var done = transaction.execute(status -> charger.charge(schoolId, stored.getValue(), day, price));
                if (Boolean.TRUE.equals(done)) {
                    charged++;
                }
            } catch (RuntimeException e) {
                // The next run tries this school again.
                log.error("Failed to charge school {} for storage on {}", schoolId, day, e);
            }
        }
        if (charged > 0) {
            log.info("Charged {} schools for storage on {}", charged, day);
        }
    }
}
