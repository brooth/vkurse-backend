/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.application;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import vkurse.platform.commerce.domain.Order;
import vkurse.platform.commerce.domain.OrderRepository;
import vkurse.platform.payment.domain.PaymentRefunded;
import vkurse.platform.payment.domain.PaymentSucceeded;

/**
 * Makes an order paid when its payment succeeds, and refunded when the whole payment goes back. These run after the
 * payment's own transaction is committed, each in a transaction of its own; if one fails, the event is kept and
 * delivered again.
 */
@Component
class OrderPaymentListener {
    private static final Logger log = LoggerFactory.getLogger(OrderPaymentListener.class);

    private final OrderRepository orders;
    private final OrderFulfilment fulfilment;
    private final Clock clock;

    OrderPaymentListener(OrderRepository orders, OrderFulfilment fulfilment, Clock clock) {
        this.orders = orders;
        this.fulfilment = fulfilment;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void on(PaymentSucceeded event) {
        if (!Order.PAYMENT_PURPOSE.equals(event.purpose())) {
            return;
        }
        var order = orders.findByIdForUpdate(event.referenceId()).orElse(null);
        if (order == null) {
            log.error("Payment {} succeeded for order {}, which doesn't exist", event.paymentId(), event.referenceId());
            return;
        }
        if (orders.hasPaid(order.getBuyerId(), order.getCourseId(), order.getId())) {
            // The money was taken twice for one course. Nothing here can undo that: the staff must refund it.
            log.error(
                    "Payment {} succeeded for order {}, but buyer {} has course {} already: refund it",
                    event.paymentId(),
                    order.getId(),
                    order.getBuyerId(),
                    order.getCourseId());
            return;
        }
        if (order.markPaid(clock.instant())) {
            orders.save(order);
            fulfilment.fulfil(order);
        }
    }

    @ApplicationModuleListener
    public void on(PaymentRefunded event) {
        if (!Order.PAYMENT_PURPOSE.equals(event.purpose()) || !event.fully()) {
            return;
        }
        orders.findByIdForUpdate(event.referenceId()).ifPresent(order -> {
            if (order.markRefunded()) {
                orders.save(order);
            }
        });
    }
}
