/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.money.Money;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.payment.application.PaymentService;
import vkurse.platform.payment.domain.Payment;

/** Payments: for the payer, and for the owner and admins of the school that was paid. */
@RestController
class PaymentController {
    private final PaymentService payments;

    PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** The school's payments, newest first. */
    @GetMapping("/schools/{schoolId}/payments")
    List<PaymentResponse> list(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return payments.list(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(PaymentResponse::of)
                .toList();
    }

    @GetMapping("/payments/{id}")
    PaymentResponse get(@GetSession UserSession session, @PathVariable UUID id) {
        return PaymentResponse.of(payments.get(session.userId(), id));
    }

    /**
     * Asks the provider about the payment now. The payer's app calls it when the provider sends them back, so the
     * result shows at once.
     */
    @PostMapping("/payments/{id}/sync")
    PaymentResponse sync(@GetSession UserSession session, @PathVariable UUID id) {
        return PaymentResponse.of(payments.sync(session.userId(), id));
    }

    /** Gives money of a paid payment back. Without {@code amount}, all that is left of it. Only the owner may. */
    @PostMapping("/payments/{id}/refunds")
    RefundResponse refund(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody RefundRequest data) {
        var amount = data.amount() == null ? null : Money.rub(data.amount());
        return RefundResponse.of(payments.refund(session.userId(), id, amount, data.reason()));
    }

    @GetMapping("/payments/{id}/refunds")
    List<RefundResponse> refunds(@GetSession UserSession session, @PathVariable UUID id) {
        return payments.refunds(session.userId(), id).stream()
                .map(RefundResponse::of)
                .toList();
    }

    record RefundRequest(
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) @Nullable
            BigDecimal amount,

            @Size(max = Payment.MAX_REASON_LENGTH) @Nullable String reason) {}
}
