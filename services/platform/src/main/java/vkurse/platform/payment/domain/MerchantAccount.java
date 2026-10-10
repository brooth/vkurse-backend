/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

/**
 * What a provider says about the account some credentials open.
 *
 * @param accountId the account's id with the provider (a shop id)
 * @param testMode whether payments to it are tests, with no real money
 */
public record MerchantAccount(String accountId, boolean testMode) {}
