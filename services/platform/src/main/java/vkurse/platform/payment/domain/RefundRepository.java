/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {

    Optional<Refund> findById(UUID id);

    /** The refund of a merchant's payment that the provider knows under that id. */
    Optional<Refund> findByProviderId(UUID merchantId, String providerRefundId);

    /** A payment's refunds, oldest first. */
    List<Refund> findByPayment(UUID paymentId);

    /** The ids of refunds the provider took and hasn't settled, asked before {@code before}. */
    List<UUID> findPending(Instant before, int limit);

    Refund save(Refund refund);
}
