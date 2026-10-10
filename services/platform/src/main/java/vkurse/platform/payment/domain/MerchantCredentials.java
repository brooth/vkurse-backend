/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What lets us act in a school's account with a provider. A secret: it is kept only encrypted (see
 * {@link CredentialCipher}), opened only to call the provider, and never returned or logged.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "provider")
@JsonSubTypes({@JsonSubTypes.Type(value = MerchantCredentials.Yookassa.class, name = "YOOKASSA")})
public sealed interface MerchantCredentials {

    PaymentProvider provider();

    /** A YooKassa shop's id and secret key. */
    record Yookassa(String shopId, String secretKey) implements MerchantCredentials {
        @Override
        public PaymentProvider provider() {
            return PaymentProvider.YOOKASSA;
        }

        /** Never prints the key. */
        @Override
        public String toString() {
            return "Yookassa[shopId=" + shopId + "]";
        }
    }
}
