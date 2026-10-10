/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

/** Port: encrypts a merchant's credentials for storage, and opens them again. Works in memory. */
public interface CredentialCipher {

    String seal(MerchantCredentials credentials);

    MerchantCredentials open(String sealed);
}
