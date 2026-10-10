/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.application;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.platform.payment.PaymentProperties;
import vkurse.platform.payment.domain.CredentialCipher;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.MerchantAccount;
import vkurse.platform.payment.domain.MerchantCredentials;
import vkurse.platform.payment.domain.MerchantRepository;
import vkurse.platform.payment.domain.ReceiptSettings;

/**
 * Vkurse's own shop: at startup, makes the merchant of no school from the service's settings, or brings it in line
 * with them. The provider is not asked: a wrong key shows when the first payment is started.
 */
@Component
class PlatformShop implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlatformShop.class);

    private static final String NAME = "VKurse";
    private static final String TEST_KEY_PREFIX = "test_";

    private final MerchantRepository merchants;
    private final CredentialCipher cipher;
    private final PaymentProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    PlatformShop(
            MerchantRepository merchants,
            CredentialCipher cipher,
            PaymentProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.merchants = merchants;
        this.cipher = cipher;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        var shop = properties.platformShop();
        var credentials = new MerchantCredentials.Yookassa(shop.shopId(), shop.secretKey());
        var account = new MerchantAccount(shop.shopId(), shop.secretKey().startsWith(TEST_KEY_PREFIX));
        var receipts = ReceiptSettings.of(shop.receiptsEnabled(), shop.receiptsVatCode());
        var merchant = transaction.execute(status -> {
            var existing = merchants.findPlatform().orElse(null);
            if (existing == null) {
                return merchants.save(Merchant.platform(NAME, credentials, account, receipts, cipher, clock.instant()));
            }
            existing.configure(credentials, account, receipts, cipher);
            return merchants.save(existing);
        });
        if (merchant != null) {
            log.info(
                    "Vkurse's own shop is merchant {} (account {}, test mode {})",
                    merchant.getId(),
                    merchant.getAccountId(),
                    merchant.isTestMode());
        }
    }
}
