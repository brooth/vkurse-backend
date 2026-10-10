/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.application;

import java.time.Clock;
import java.util.Objects;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.platform.commerce.domain.OrderRepository;

/**
 * Expires orders that waited for payment too long, every 10 minutes, and gives their promo code uses back. A payment
 * that succeeds after that still makes its order paid.
 */
@Component
class OrderExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private static final int BATCH_SIZE = 200;

    private final OrderRepository orders;
    private final OrderService orderService;
    private final Clock clock;
    private final TransactionTemplate transaction;

    OrderExpiryJob(
            OrderRepository orders,
            OrderService orderService,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.orderService = orderService;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "0 5/10 * * * *")
    @SchedulerLock(name = "order-expiry")
    public void expireOverdue() {
        var now = clock.instant();
        var overdue = Objects.requireNonNull(transaction.execute(status -> orders.findOverdue(now, BATCH_SIZE)));
        var expired = 0;
        for (var orderId : overdue) {
            expired += Objects.requireNonNull(transaction.execute(status -> {
                var order = orders.findByIdForUpdate(orderId).orElseThrow();
                if (!order.expire(clock.instant())) {
                    return 0;
                }
                orders.save(order);
                orderService.giveBack(order);
                return 1;
            }));
        }
        if (expired > 0) {
            log.info("Expired {} unpaid orders", expired);
        }
    }
}
