/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.payment.domain.CredentialCipher;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.MerchantCredentials;
import vkurse.platform.payment.domain.MerchantRepository;
import vkurse.platform.payment.domain.ReceiptSettings;
import vkurse.platform.school.application.SchoolMemberService;
import vkurse.platform.school.domain.SchoolMember;

/**
 * A school's merchants: its accounts with payment providers. The owner connects and changes them; credentials are
 * confirmed with the provider before they are kept, and are kept encrypted.
 */
@Service
public class MerchantService {
    private final MerchantRepository merchants;
    private final SchoolMemberService staff;
    private final PaymentGateways gateways;
    private final CredentialCipher cipher;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public MerchantService(
            MerchantRepository merchants,
            SchoolMemberService staff,
            PaymentGateways gateways,
            CredentialCipher cipher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.merchants = merchants;
        this.staff = staff;
        this.gateways = gateways;
        this.cipher = cipher;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Connects an account of the school with a provider. The provider is asked to confirm the credentials first,
     * outside a transaction.
     */
    public Merchant connect(
            UUID userId, UUID schoolId, String name, MerchantCredentials credentials, ReceiptSettings receipts) {
        staff.require(schoolId, userId).ensureCanManagePayments();
        var account = gateways.of(credentials.provider()).describe(credentials);
        return Objects.requireNonNull(transaction.execute(status -> {
            staff.require(schoolId, userId).ensureCanManagePayments();
            return merchants.save(Merchant.connect(
                    schoolId,
                    name,
                    credentials,
                    account,
                    receipts,
                    merchants.countConnectedBySchool(schoolId),
                    cipher,
                    clock.instant()));
        }));
    }

    /** The school's connected merchants, for its owner and admins. */
    @Transactional(readOnly = true)
    public List<Merchant> list(UUID userId, UUID schoolId) {
        staff.require(schoolId, userId).ensureCanSeePayments();
        return merchants.findConnectedBySchool(schoolId);
    }

    /**
     * The merchants a payer may choose among to pay the school. For other modules: anyone who may pay may see them.
     */
    @Transactional(readOnly = true)
    public List<Merchant> findOffered(UUID schoolId) {
        return merchants.findActiveBySchool(schoolId);
    }

    /** Changes the name and the receipts setting. A {@code null} argument leaves that as it is. */
    @Transactional
    public Merchant update(UUID userId, UUID merchantId, @Nullable String name, @Nullable ReceiptSettings receipts) {
        var merchant = manageable(userId, merchantId);
        if (name != null) {
            merchant.rename(name);
        }
        if (receipts != null) {
            merchant.changeReceipts(receipts);
        }
        return merchants.save(merchant);
    }

    /** Replaces the credentials, once the provider has confirmed the new ones. */
    public Merchant replaceCredentials(UUID userId, UUID merchantId, MerchantCredentials credentials) {
        transaction.executeWithoutResult(status -> manageable(userId, merchantId));
        var account = gateways.of(credentials.provider()).describe(credentials);
        return Objects.requireNonNull(transaction.execute(status -> {
            var merchant = manageable(userId, merchantId);
            merchant.replaceCredentials(credentials, account, cipher);
            return merchants.save(merchant);
        }));
    }

    @Transactional
    public Merchant enable(UUID userId, UUID merchantId) {
        var merchant = manageable(userId, merchantId);
        merchant.enable();
        return merchants.save(merchant);
    }

    /** Stops offering the merchant to payers. */
    @Transactional
    public Merchant disable(UUID userId, UUID merchantId) {
        var merchant = manageable(userId, merchantId);
        merchant.disable();
        return merchants.save(merchant);
    }

    /** Disconnects the merchant for good; its credentials are forgotten. */
    @Transactional
    public void disconnect(UUID userId, UUID merchantId) {
        var merchant = manageable(userId, merchantId);
        merchant.disconnect();
        merchants.save(merchant);
    }

    /** A connected merchant of a school the caller may manage payments in. Call it in a transaction. */
    Merchant manageable(UUID userId, UUID merchantId) {
        var merchant =
                merchants.findById(merchantId).filter(Merchant::isConnected).orElseThrow(MerchantService::notFound);
        member(userId, merchant.getSchoolId()).ensureCanManagePayments();
        return merchant;
    }

    /** The caller as a member of that school; a school they are not in is "merchant not found". */
    private SchoolMember member(UUID userId, UUID schoolId) {
        try {
            return staff.require(schoolId, userId);
        } catch (NotFoundException e) {
            throw notFound();
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Merchant not found");
    }
}
