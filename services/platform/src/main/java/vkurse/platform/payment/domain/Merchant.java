/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.platform.payment.domain.PaymentErrors.MerchantUnavailableException;
import vkurse.platform.payment.domain.PaymentErrors.TooManyMerchantsException;

/**
 * A school's account with a payment provider: where the money of a payment to that school goes. A school may have
 * several, and a payer chooses among the active ones.
 */
@Entity
@Table(name = "payment_merchants")
@Getter
public class Merchant extends AggregateRoot {
    public static final int MAX_NAME_LENGTH = 60;

    /** How many merchants a school may have connected. */
    public static final int MAX_PER_SCHOOL = 5;

    /** The school the merchant belongs to; {@code null} for Vkurse's own shop. */
    private @Nullable UUID schoolId;

    @Enumerated(EnumType.STRING)
    private PaymentProvider provider;

    /** What a payer sees when choosing how to pay. */
    private String name;

    /** The account's id with the provider (a shop id). Not a secret. */
    private String accountId;

    /** Whether payments to this account are tests, with no real money. */
    private boolean testMode;

    /** The credentials, encrypted; {@code null} once the merchant is disconnected. */
    @Getter(AccessLevel.NONE)
    private @Nullable String credentials;

    @Embedded
    private ReceiptSettings receipts;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    protected Merchant() {}

    private Merchant(
            @Nullable UUID schoolId,
            PaymentProvider provider,
            String name,
            MerchantAccount account,
            String sealedCredentials,
            ReceiptSettings receipts,
            Instant now) {
        super(now);
        this.schoolId = schoolId;
        this.provider = provider;
        this.name = name;
        this.accountId = account.accountId();
        this.testMode = account.testMode();
        this.credentials = sealedCredentials;
        this.receipts = receipts;
    }

    /**
     * Connects an account the provider has confirmed.
     *
     * @param account what the provider said about the account the credentials open
     * @param existing how many merchants the school has connected
     */
    public static Merchant connect(
            UUID schoolId,
            String name,
            MerchantCredentials credentials,
            MerchantAccount account,
            ReceiptSettings receipts,
            long existing,
            CredentialCipher cipher,
            Instant now) {
        if (existing >= MAX_PER_SCHOOL) {
            throw new TooManyMerchantsException();
        }
        return new Merchant(
                schoolId, credentials.provider(), checkedName(name), account, cipher.seal(credentials), receipts, now);
    }

    /**
     * Vkurse's own shop: the one merchant that belongs to no school. Schools pay Vkurse through it. Its credentials
     * come from the service's settings, so the provider isn't asked to confirm them.
     */
    public static Merchant platform(
            String name,
            MerchantCredentials credentials,
            MerchantAccount account,
            ReceiptSettings receipts,
            CredentialCipher cipher,
            Instant now) {
        return new Merchant(
                null, credentials.provider(), checkedName(name), account, cipher.seal(credentials), receipts, now);
    }

    /** Brings Vkurse's own shop in line with the service's settings. */
    public void configure(
            MerchantCredentials credentials,
            MerchantAccount account,
            ReceiptSettings receipts,
            CredentialCipher cipher) {
        if (!isPlatform()) {
            throw new IllegalStateException("Only Vkurse's own shop is set from the settings");
        }
        this.provider = credentials.provider();
        this.credentials = cipher.seal(credentials);
        this.accountId = account.accountId();
        this.testMode = account.testMode();
        this.receipts = receipts;
        this.status = Status.ACTIVE;
    }

    /** Whether this is Vkurse's own shop, not a school's. */
    public boolean isPlatform() {
        return schoolId == null;
    }

    public boolean belongsTo(UUID schoolId) {
        return schoolId.equals(this.schoolId);
    }

    public void rename(String name) {
        this.name = checkedName(name);
    }

    public void changeReceipts(ReceiptSettings receipts) {
        this.receipts = receipts;
    }

    /** Replaces the credentials with others the provider has confirmed, for the same provider. */
    public void replaceCredentials(MerchantCredentials credentials, MerchantAccount account, CredentialCipher cipher) {
        ensureConnected();
        if (credentials.provider() != provider) {
            throw new BadRequestException("These credentials are of another provider");
        }
        this.credentials = cipher.seal(credentials);
        this.accountId = account.accountId();
        this.testMode = account.testMode();
    }

    /** Stops offering the merchant to payers. Payments already started go on, and refunds work. */
    public void disable() {
        ensureConnected();
        status = Status.DISABLED;
    }

    public void enable() {
        ensureConnected();
        status = Status.ACTIVE;
    }

    /**
     * Disconnects the account for good: the credentials are forgotten. The row stays, because payments point to it,
     * but nothing can be asked of the provider about them any more.
     */
    public void disconnect() {
        status = Status.DISCONNECTED;
        credentials = null;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isConnected() {
        return status != Status.DISCONNECTED;
    }

    /** Checks that a new payment may be made to this merchant. */
    public void ensureTakesPayments() {
        if (!isActive()) {
            throw new MerchantUnavailableException();
        }
    }

    /** The credentials, to call the provider with. Only a connected merchant has them. */
    public MerchantCredentials credentials(CredentialCipher cipher) {
        if (credentials == null) {
            throw new MerchantUnavailableException();
        }
        return cipher.open(credentials);
    }

    private void ensureConnected() {
        if (!isConnected()) {
            throw new MerchantUnavailableException();
        }
    }

    private static String checkedName(String name) {
        var trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("The name must have 1 to " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    public enum Status {
        /** Offered to payers. */
        ACTIVE,
        /** Not offered, but connected: its payments can be checked and refunded. */
        DISABLED,
        /** Disconnected for good; the credentials are gone. */
        DISCONNECTED
    }
}
