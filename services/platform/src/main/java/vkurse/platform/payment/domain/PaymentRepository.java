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

public interface PaymentRepository {

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdForUpdate(UUID id);

    /** The payment of a merchant that the provider knows under that id. */
    Optional<Payment> findByProviderId(UUID merchantId, String providerPaymentId);

    /** A school's payments, newest first. */
    /** The payments to a school's own merchants, newest first. Its payments to Vkurse are not among them. */
    List<Payment> findBySchool(UUID schoolId, int limit, int offset);

    /** The ids of pending payments it is time to ask the provider about, the longest overdue first. */
    List<UUID> findDueForCheck(Instant now, int limit);

    Payment save(Payment payment);

    void remove(Payment payment);
}
