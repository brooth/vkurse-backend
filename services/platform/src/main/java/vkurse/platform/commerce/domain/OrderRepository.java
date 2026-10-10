/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Optional<Order> findById(UUID id);

    Optional<Order> findByIdForUpdate(UUID id);

    /** A buyer's orders, newest first. */
    List<Order> findByBuyer(UUID buyerId, int limit, int offset);

    /** A school's orders, newest first. */
    List<Order> findBySchool(UUID schoolId, int limit, int offset);

    /** Whether the buyer has a paid order for the course, other than {@code exceptOrderId}. */
    boolean hasPaid(UUID buyerId, UUID courseId, UUID exceptOrderId);

    /** Whether the buyer has a paid order for the course. */
    boolean hasPaid(UUID buyerId, UUID courseId);

    /** The buyer's orders for the course that still wait for payment. */
    List<Order> findPending(UUID buyerId, UUID courseId);

    /** The ids of orders that wait for payment past their time, the oldest first. */
    List<UUID> findOverdue(Instant now, int limit);

    Order save(Order order);
}
