/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.util.Optional;
import vkurse.platform.payment.domain.PaymentErrors.MerchantRejectedException;
import vkurse.platform.payment.domain.PaymentErrors.PaymentFailedException;

/**
 * Port: one payment provider. Every call but {@link #parse} goes to the provider over the network, in the account
 * the credentials open: use them outside a database transaction.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /**
     * Checks the credentials with the provider and says which account they open.
     *
     * @throws MerchantRejectedException the provider doesn't accept them
     */
    MerchantAccount describe(MerchantCredentials credentials);

    /**
     * Creates a payment the payer then confirms on the provider's page.
     *
     * @throws PaymentFailedException the provider refused
     */
    ProviderPayment create(MerchantCredentials credentials, PaymentOrder order);

    /** The payment's state now; empty if the account has no such payment. */
    Optional<ProviderPayment> find(MerchantCredentials credentials, String providerPaymentId);

    /**
     * Gives money of a paid payment back to the payer.
     *
     * @throws PaymentFailedException the provider refused
     */
    ProviderRefund refund(MerchantCredentials credentials, RefundOrder order);

    /** The refund's state now; empty if the account has no such refund. */
    Optional<ProviderRefund> findRefund(MerchantCredentials credentials, String providerRefundId);

    /** Reads a notification the provider sent us; empty if it can't be read. In memory. */
    Optional<ProviderNotification> parse(String body);
}
