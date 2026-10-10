/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.web;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vkurse.platform.payment.application.PaymentWebhookService;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.PaymentProvider;

/**
 * Public: where payment providers notify us. Each merchant has its own URL. The body is not believed (see
 * {@link PaymentWebhookService}); a provider gets 200 for whatever it sends, unless handling failed and it should
 * send the notification again.
 */
@RestController
@RequestMapping("/payments/webhooks")
class PaymentWebhookController {
    private static final String YOOKASSA = "/yookassa/";

    private final PaymentWebhookService webhooks;

    PaymentWebhookController(PaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping(YOOKASSA + "{merchantId}")
    ResponseEntity<Void> yookassa(@PathVariable UUID merchantId, @RequestBody String body) {
        webhooks.handle(PaymentProvider.YOOKASSA, merchantId, body);
        return ResponseEntity.ok().build();
    }

    /** The URL a school sets in its shop with the provider. */
    static URI notificationUrl(URI publicUrl, Merchant merchant) {
        var base = publicUrl.toString();
        var path = switch (merchant.getProvider()) {
            case YOOKASSA -> YOOKASSA;
        };
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/payments/webhooks"
                + path + merchant.getId());
    }
}
