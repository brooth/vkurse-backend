/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.billing.application.BillingService;
import vkurse.platform.billing.domain.BillingAccount;
import vkurse.platform.billing.domain.LedgerEntry;
import vkurse.platform.billing.domain.StorageUsage;
import vkurse.platform.payment.domain.ReturnUrl;
import vkurse.platform.payment.web.MoneyResponse;

/** A school's balance at Vkurse. For the school's owner and admins. */
@RestController
@RequestMapping("/schools/{schoolId}/billing")
class BillingController {
    private final BillingService billing;

    BillingController(BillingService billing) {
        this.billing = billing;
    }

    /** The balance and its state, what the school stores now and what a day of that costs. */
    @GetMapping
    OverviewResponse overview(@GetSession UserSession session, @PathVariable UUID schoolId) {
        return OverviewResponse.of(billing.overview(session.userId(), schoolId));
    }

    /** The changes of the balance, newest first. */
    @GetMapping("/ledger")
    List<LedgerEntryResponse> ledger(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return billing.ledger(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(LedgerEntryResponse::of)
                .toList();
    }

    /** What the school stored and paid for each day, newest first. */
    @GetMapping("/usage")
    List<UsageResponse> usage(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return billing.usage(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(UsageResponse::of)
                .toList();
    }

    /**
     * Starts a payment that tops the balance up, and returns the provider's page to send the payer to. When the
     * provider sends them back, call {@code POST /payments/{paymentId}/sync} and read the balance again: it grows a
     * moment after the payment succeeds.
     */
    @PostMapping("/top-ups")
    TopUpResponse topUp(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody TopUpRequest data) {
        var payment = billing.topUp(session.userId(), schoolId, data.amount(), data.returnUrl());
        return new TopUpResponse(payment.getId(), MoneyResponse.of(payment.getAmount()), payment.getConfirmationUrl());
    }

    record TopUpRequest(
            @NotNull BigDecimal amount,
            @NotBlank @Size(max = ReturnUrl.MAX_LENGTH) String returnUrl) {}

    /** {@code confirmationUrl} is absent if the payment needs no more action from the payer. */
    record TopUpResponse(
            UUID paymentId, MoneyResponse amount, @Nullable String confirmationUrl) {}

    /**
     * {@code status}: {@code ACTIVE}; {@code DEBT} (the balance is below zero: no uploads); {@code SUSPENDED} (in
     * debt past the grace period: the school's files don't open). {@code price} is for one GB a month.
     * {@code daysLeft} is how long the balance lasts at today's cost; absent when storage costs nothing now.
     */
    record OverviewResponse(
            MoneyResponse balance,
            BillingAccount.Status status,
            @Nullable Instant debtSince,
            @Nullable Instant suspendsAt,
            long storedBytes,
            @Nullable MoneyResponse price,
            MoneyResponse dailyCharge,
            @Nullable Long daysLeft) {
        static OverviewResponse of(BillingService.Overview overview) {
            var account = overview.account();
            return new OverviewResponse(
                    MoneyResponse.of(account.getBalanceAmount()),
                    overview.status(),
                    account.getDebtSince(),
                    account.suspendsAt(),
                    overview.storedBytes(),
                    overview.price() == null ? null : MoneyResponse.of(overview.price()),
                    MoneyResponse.of(overview.dailyCharge()),
                    account.daysLeft(overview.dailyCharge()));
        }
    }

    /** {@code amount} is above zero for money in and below for money out. */
    record LedgerEntryResponse(
            UUID id,
            Instant createdAt,
            LedgerEntry.Kind kind,
            MoneyResponse amount,
            MoneyResponse balanceAfter,
            UUID referenceId) {
        static LedgerEntryResponse of(LedgerEntry entry) {
            return new LedgerEntryResponse(
                    entry.getId(),
                    entry.getCreatedAt(),
                    entry.getKind(),
                    MoneyResponse.of(entry.getAmount()),
                    MoneyResponse.of(entry.getBalanceAfterAmount()),
                    entry.getReferenceId());
        }
    }

    record UsageResponse(LocalDate day, long bytes, MoneyResponse price, MoneyResponse charge) {
        static UsageResponse of(StorageUsage usage) {
            return new UsageResponse(
                    usage.getDay(),
                    usage.getBytes(),
                    MoneyResponse.of(usage.getPriceAmount()),
                    MoneyResponse.of(usage.getChargeAmount()));
        }
    }
}
