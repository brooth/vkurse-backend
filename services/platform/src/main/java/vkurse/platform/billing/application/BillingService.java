/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.money.Money;
import vkurse.core.web.Paging;
import vkurse.platform.billing.domain.BillingAccount;
import vkurse.platform.billing.domain.BillingAccountRepository;
import vkurse.platform.billing.domain.LedgerEntry;
import vkurse.platform.billing.domain.LedgerEntryRepository;
import vkurse.platform.billing.domain.StoragePriceRepository;
import vkurse.platform.billing.domain.StorageUsage;
import vkurse.platform.billing.domain.StorageUsageRepository;
import vkurse.platform.billing.domain.TopUpAmount;
import vkurse.platform.payment.application.PaymentService;
import vkurse.platform.payment.domain.Payment;
import vkurse.platform.school.application.SchoolMemberService;
import vkurse.platform.storage.application.StoredFileService;
import vkurse.platform.user.domain.User;

/** A school's balance at Vkurse, for the staff who see its payments: what it has, what it stores, topping up. */
@Service
public class BillingService {
    /** What a top-up's payment names as its purpose. */
    static final String TOP_UP_PURPOSE = "billing-top-up";

    private static final Locale RUSSIAN = Locale.forLanguageTag(User.DEFAULT_LOCALE);

    private final BillingAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final StorageUsageRepository usages;
    private final StoragePriceRepository prices;
    private final SchoolMemberService staff;
    private final StoredFileService files;
    private final PaymentService payments;
    private final MessageSource messages;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public BillingService(
            BillingAccountRepository accounts,
            LedgerEntryRepository ledger,
            StorageUsageRepository usages,
            StoragePriceRepository prices,
            SchoolMemberService staff,
            StoredFileService files,
            PaymentService payments,
            MessageSource messages,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.usages = usages;
        this.prices = prices;
        this.staff = staff;
        this.files = files;
        this.payments = payments;
        this.messages = messages;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** The school's balance and state, what it stores now, and what a day of that costs. */
    public Overview overview(UUID userId, UUID schoolId) {
        return Objects.requireNonNull(transaction.execute(status -> {
            staff.require(schoolId, userId).ensureCanSeePayments();
            var now = clock.instant();
            // A school that never topped up and was never charged has no account yet: it is shown an empty one.
            var account = accounts.findBySchool(schoolId).orElseGet(() -> BillingAccount.open(schoolId, now));
            var bytes = files.usageOf(schoolId);
            var day = LocalDate.now(clock.withZone(BillingNotifier.MOSCOW));
            var price = prices.findInForce(day).orElse(null);
            var daily = price == null ? Money.ZERO_RUB : price.dailyCharge(bytes, day);
            return new Overview(account, account.status(now), bytes, price == null ? null : price.getAmount(), daily);
        }));
    }

    /** The changes of the school's balance, newest first. */
    public List<LedgerEntry> ledger(UUID userId, UUID schoolId, Paging paging) {
        return Objects.requireNonNull(transaction.execute(status -> {
            staff.require(schoolId, userId).ensureCanSeePayments();
            return ledger.findBySchool(schoolId, paging.limit(), paging.offset());
        }));
    }

    /** What the school stored and paid for each day, newest first. */
    public List<StorageUsage> usage(UUID userId, UUID schoolId, Paging paging) {
        return Objects.requireNonNull(transaction.execute(status -> {
            staff.require(schoolId, userId).ensureCanSeePayments();
            return usages.findBySchool(schoolId, paging.limit(), paging.offset());
        }));
    }

    /**
     * Starts a payment to Vkurse's own shop that tops the school's balance up. The balance grows when the payment
     * succeeds, a moment after the provider says so. The provider is called outside a transaction.
     *
     * @return the payment, with the provider's page to send the payer to
     */
    public Payment topUp(UUID userId, UUID schoolId, BigDecimal value, String returnUrl) {
        var amount = TopUpAmount.rub(value);
        var accountId = Objects.requireNonNull(transaction.execute(status -> {
            staff.require(schoolId, userId).ensureCanSeePayments();
            return accounts.findBySchoolForUpdate(schoolId)
                    .orElseGet(() -> accounts.save(BillingAccount.open(schoolId, clock.instant())))
                    .getId();
        }));
        var description = messages.getMessage("billing.top-up.description", null, RUSSIAN);
        return payments.startForPlatform(
                userId, schoolId, amount.amount(), description, TOP_UP_PURPOSE, accountId, returnUrl);
    }

    /**
     * @param status the account's state now
     * @param storedBytes what the school stores now
     * @param price the price of one GB for a month today; {@code null} if none is in force
     * @param dailyCharge what a day of the school's storage costs today
     */
    public record Overview(
            BillingAccount account,
            BillingAccount.Status status,
            long storedBytes,
            @Nullable Money price,
            Money dailyCharge) {}
}
