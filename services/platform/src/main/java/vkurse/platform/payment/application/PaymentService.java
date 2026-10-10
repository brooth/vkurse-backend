/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.application;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.error.Errors.ForbiddenException;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.money.Money;
import vkurse.core.web.Paging;
import vkurse.platform.PlatformProperties;
import vkurse.platform.payment.domain.CredentialCipher;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.MerchantCredentials;
import vkurse.platform.payment.domain.MerchantRepository;
import vkurse.platform.payment.domain.Payment;
import vkurse.platform.payment.domain.PaymentErrors.MerchantUnavailableException;
import vkurse.platform.payment.domain.PaymentOrder;
import vkurse.platform.payment.domain.PaymentRepository;
import vkurse.platform.payment.domain.Refund;
import vkurse.platform.payment.domain.RefundOrder;
import vkurse.platform.payment.domain.RefundRepository;
import vkurse.platform.payment.domain.ReturnUrl;
import vkurse.platform.school.application.SchoolMemberService;
import vkurse.platform.school.domain.SchoolMember;
import vkurse.platform.user.application.UserService;
import vkurse.platform.user.domain.User;

/**
 * Payments to a school's merchants. Another module starts one with {@link #start} and listens for
 * {@code PaymentSucceeded} with its own {@code purpose}; this service never decides what a payment bought.
 *
 * <p>The provider is always called outside a database transaction. A payment is saved before the provider is asked
 * to create it, so the provider's idempotence key is our payment's id.
 */
@Service
public class PaymentService {
    private static final Locale RUSSIAN = Locale.forLanguageTag(User.DEFAULT_LOCALE);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final MerchantRepository merchants;
    private final MerchantService merchantAccess;
    private final SchoolMemberService staff;
    private final UserService users;
    private final PaymentGateways gateways;
    private final PaymentSync sync;
    private final CredentialCipher cipher;
    private final PlatformProperties properties;
    private final MessageSource messages;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PaymentService(
            PaymentRepository payments,
            RefundRepository refunds,
            MerchantRepository merchants,
            MerchantService merchantAccess,
            SchoolMemberService staff,
            UserService users,
            PaymentGateways gateways,
            PaymentSync sync,
            CredentialCipher cipher,
            PlatformProperties properties,
            MessageSource messages,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.payments = payments;
        this.refunds = refunds;
        this.merchants = merchants;
        this.merchantAccess = merchantAccess;
        this.staff = staff;
        this.users = users;
        this.gateways = gateways;
        this.sync = sync;
        this.cipher = cipher;
        this.properties = properties;
        this.messages = messages;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Starts a payment of {@code payerId} to a merchant, for another module. The result has the provider's page to
     * send the payer to ({@code confirmationUrl}). Whether the payer may buy what this pays for is the caller's check.
     *
     * @param purpose the caller's name for what is paid ({@code order}); it comes back in the events
     * @param referenceId the caller's id of what is paid
     * @param returnUrl a page of our web app the provider sends the payer back to
     */
    public Payment start(
            UUID merchantId,
            UUID payerId,
            Money amount,
            String description,
            String purpose,
            UUID referenceId,
            String returnUrl) {
        var back = ReturnUrl.of(returnUrl, properties.webUrl());
        return begin(
                payerId,
                merchantId,
                merchant -> Payment.start(
                        merchant, payerId, amount, description, purpose, referenceId, back, clock.instant()));
    }

    /** Starts a small payment the school's owner makes to check a merchant end to end. */
    /**
     * Starts a payment of a school to Vkurse's own shop, for another module. Whether the payer may pay for that school
     * is the caller's check, made before. The payment is the school's: its payer, owner and admins read and sync it.
     *
     * @param purpose what the starter calls this kind of payment; it comes back in the payment's events
     * @param referenceId the starter's own id of what is paid for
     */
    public Payment startForPlatform(
            UUID payerId,
            UUID schoolId,
            Money amount,
            String description,
            String purpose,
            UUID referenceId,
            String returnUrl) {
        var back = ReturnUrl.of(returnUrl, properties.webUrl());
        var platformId = Objects.requireNonNull(transaction.execute(status -> merchants
                .findPlatform()
                .orElseThrow(MerchantUnavailableException::new)
                .getId()));
        return begin(
                payerId,
                platformId,
                platform -> Payment.toPlatform(
                        platform, schoolId, payerId, amount, description, purpose, referenceId, back, clock.instant()));
    }

    /** A payment by id, for the module that started it. It checks nothing about a caller. */
    public Optional<Payment> find(UUID paymentId) {
        return Objects.requireNonNull(transaction.execute(status -> payments.findById(paymentId)));
    }

    public Payment testPayment(UUID userId, UUID merchantId, Money amount, String returnUrl) {
        transaction.executeWithoutResult(status -> merchantAccess.manageable(userId, merchantId));
        var back = ReturnUrl.of(returnUrl, properties.webUrl());
        var description = messages.getMessage("payment.test.description", null, RUSSIAN);
        return begin(
                userId,
                merchantId,
                merchant -> Payment.test(merchant, userId, amount, description, back, clock.instant()));
    }

    /** A payment, for its payer and for the owner and admins of the school it was paid to. */
    public Payment get(UUID userId, UUID paymentId) {
        return Objects.requireNonNull(transaction.execute(status -> visible(userId, paymentId)));
    }

    /** The school's payments, newest first, for its owner and admins. */
    public List<Payment> list(UUID userId, UUID schoolId, Paging paging) {
        return Objects.requireNonNull(transaction.execute(status -> {
            staff.require(schoolId, userId).ensureCanSeePayments();
            return payments.findBySchool(schoolId, paging.limit(), paging.offset());
        }));
    }

    /**
     * Asks the provider about the payment now and returns it as it then is. The payer calls it on coming back from
     * the provider's page, so they don't wait for a notification.
     */
    public Payment sync(UUID userId, UUID paymentId) {
        get(userId, paymentId);
        sync.refreshPayment(paymentId, null);
        return get(userId, paymentId);
    }

    /**
     * Gives money of a paid payment back to the payer, through the provider.
     *
     * @param amount what to give back; {@code null} for all that is left of the payment
     */
    public Refund refund(UUID userId, UUID paymentId, @Nullable Money amount, @Nullable String reason) {
        var asked = Objects.requireNonNull(transaction.execute(status -> {
            var payment = payments.findByIdForUpdate(paymentId).orElseThrow(PaymentService::notFound);
            member(userId, payment.getSchoolId()).ensureCanManagePayments();
            var merchant = merchants.findById(payment.getMerchantId()).orElseThrow(MerchantUnavailableException::new);
            if (merchant.isPlatform()) {
                // A school's money at Vkurse is given back by Vkurse, not by the school.
                throw new ForbiddenException("A payment to Vkurse can't be refunded by the school");
            }
            var credentials = merchant.credentials(cipher);
            var refund = refunds.save(payment.refund(amount, reason, userId, clock.instant()));
            payments.save(payment);
            return new Asked(
                    refund.getId(),
                    refund.order(Objects.requireNonNull(payment.getProviderPaymentId())),
                    merchant,
                    credentials);
        }));
        try {
            var state = gateways.of(asked.merchant().getProvider()).refund(asked.credentials(), asked.order());
            return Objects.requireNonNull(transaction.execute(status -> {
                var payment = payments.findByIdForUpdate(paymentId).orElseThrow();
                var refund = refunds.findById(asked.refundId()).orElseThrow();
                payment.settle(refund, state);
                payments.save(payment);
                return refunds.save(refund);
            }));
        } catch (RuntimeException e) {
            // The provider didn't take the refund: free its amount, so it can be asked for again.
            transaction.executeWithoutResult(status -> {
                var payment = payments.findByIdForUpdate(paymentId).orElseThrow();
                var refund = refunds.findById(asked.refundId()).orElseThrow();
                payment.drop(refund);
                payments.save(payment);
                refunds.save(refund);
            });
            throw e;
        }
    }

    /** A payment's refunds, for the owner and admins of the school. */
    public List<Refund> refunds(UUID userId, UUID paymentId) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var payment = payments.findById(paymentId).orElseThrow(PaymentService::notFound);
            member(userId, payment.getSchoolId()).ensureCanSeePayments();
            return refunds.findByPayment(paymentId);
        }));
    }

    /**
     * Saves a new payment, asks the provider to create it, and records the provider's answer. If the provider
     * refuses or can't be reached, the payment is removed and the error goes to the caller.
     */
    private Payment begin(UUID payerId, UUID merchantId, Function<Merchant, Payment> starting) {
        var begun = Objects.requireNonNull(transaction.execute(status -> {
            var merchant = merchants.findById(merchantId).orElseThrow(MerchantUnavailableException::new);
            var payment = payments.save(starting.apply(merchant));
            var receipt = merchant.getReceipts().enabled()
                    ? new PaymentOrder.Receipt(
                            users.getMe(payerId).getEmail().value(),
                            Objects.requireNonNull(merchant.getReceipts().vatCode()))
                    : null;
            return new Begun(payment.getId(), payment.order(receipt), merchant, merchant.credentials(cipher));
        }));
        try {
            var created = gateways.of(begun.merchant().getProvider()).create(begun.credentials(), begun.order());
            return Objects.requireNonNull(transaction.execute(status -> {
                var payment = payments.findByIdForUpdate(begun.paymentId()).orElseThrow();
                payment.accept(created, clock.instant());
                return payments.save(payment);
            }));
        } catch (RuntimeException e) {
            transaction.executeWithoutResult(
                    status -> payments.findById(begun.paymentId()).ifPresent(payments::remove));
            throw e;
        }
    }

    /** The payment, if the caller paid it or may see the payments of its school. Call it in a transaction. */
    private Payment visible(UUID userId, UUID paymentId) {
        var payment = payments.findById(paymentId).orElseThrow(PaymentService::notFound);
        if (!payment.isPaidBy(userId)) {
            member(userId, payment.getSchoolId()).ensureCanSeePayments();
        }
        return payment;
    }

    /** The caller as a member of that school; a school they are not in is "payment not found". */
    private SchoolMember member(UUID userId, UUID schoolId) {
        try {
            return staff.require(schoolId, userId);
        } catch (NotFoundException e) {
            throw notFound();
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Payment not found");
    }

    private record Begun(UUID paymentId, PaymentOrder order, Merchant merchant, MerchantCredentials credentials) {}

    private record Asked(UUID refundId, RefundOrder order, Merchant merchant, MerchantCredentials credentials) {}
}
