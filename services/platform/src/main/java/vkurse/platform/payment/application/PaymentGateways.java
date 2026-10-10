/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vkurse.platform.payment.domain.PaymentGateway;
import vkurse.platform.payment.domain.PaymentProvider;

/** The gateway of each payment provider. */
@Component
class PaymentGateways {
    private final Map<PaymentProvider, PaymentGateway> gateways = new EnumMap<>(PaymentProvider.class);

    PaymentGateways(List<PaymentGateway> all) {
        all.forEach(gateway -> gateways.put(gateway.provider(), gateway));
        for (var provider : PaymentProvider.values()) {
            if (!gateways.containsKey(provider)) {
                throw new IllegalStateException("No payment gateway for " + provider);
            }
        }
    }

    PaymentGateway of(PaymentProvider provider) {
        return gateways.get(provider);
    }
}
