/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import vkurse.core.logging.HttpClients;
import vkurse.core.money.Money;
import vkurse.platform.payment.PaymentProperties;
import vkurse.platform.payment.domain.MerchantAccount;
import vkurse.platform.payment.domain.MerchantCredentials;
import vkurse.platform.payment.domain.PaymentErrors.LowAmountException;
import vkurse.platform.payment.domain.PaymentErrors.MerchantRejectedException;
import vkurse.platform.payment.domain.PaymentErrors.PaymentFailedException;
import vkurse.platform.payment.domain.PaymentGateway;
import vkurse.platform.payment.domain.PaymentOrder;
import vkurse.platform.payment.domain.PaymentProvider;
import vkurse.platform.payment.domain.ProviderNotification;
import vkurse.platform.payment.domain.ProviderPayment;
import vkurse.platform.payment.domain.ProviderRefund;
import vkurse.platform.payment.domain.RefundOrder;

/**
 * YooKassa, through its REST API. Every call is made as the shop the credentials name (HTTP basic: shop id and secret
 * key). A request that creates something carries an {@code Idempotence-Key} made from our own id, so sending it again
 * gives the same payment or refund, never a second one.
 */
@Component
class YookassaPaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(YookassaPaymentGateway.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    // What YooKassa says when an amount is below the least a payment method takes. It has no code of its own.
    private static final String LOW_AMOUNT = "smaller than the minimum";

    private final RestClient client;
    private final JsonMapper json;

    YookassaPaymentGateway(PaymentProperties properties, HttpClients httpClients, JsonMapper json) {
        var base = properties.yookassa().url().toString();
        this.client = httpClients
                .builder("yookassa", CONNECT_TIMEOUT, READ_TIMEOUT)
                .baseUrl(base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                .build();
        this.json = json;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.YOOKASSA;
    }

    @Override
    public MerchantAccount describe(MerchantCredentials credentials) {
        var shop = shop(credentials);
        try {
            var me = client.get()
                    .uri("/me")
                    .headers(headers -> headers.setBasicAuth(shop.shopId(), shop.secretKey()))
                    .retrieve()
                    .body(Me.class);
            if (me == null || me.accountId() == null) {
                throw new IllegalStateException("YooKassa returned no account");
            }
            return new MerchantAccount(me.accountId(), Boolean.TRUE.equals(me.test()));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new MerchantRejectedException();
            }
            throw e;
        }
    }

    @Override
    public ProviderPayment create(MerchantCredentials credentials, PaymentOrder order) {
        var shop = shop(credentials);
        var receipt = order.receipt() == null
                ? null
                : new Receipt(
                        new Customer(order.receipt().email()),
                        List.of(new ReceiptItem(
                                order.description(),
                                "1.00",
                                Amount.of(order.amount()),
                                order.receipt().vatCode(),
                                "service",
                                "full_payment")));
        var request = new CreatePayment(
                Amount.of(order.amount()),
                true,
                new Confirmation("redirect", order.returnUrl().toString(), null),
                order.description(),
                Map.of("paymentId", order.paymentId().toString()),
                receipt);
        try {
            return payment(client.post()
                    .uri("/payments")
                    .headers(headers -> headers.setBasicAuth(shop.shopId(), shop.secretKey()))
                    .header("Idempotence-Key", order.paymentId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(YookassaPayment.class));
        } catch (RestClientResponseException e) {
            throw refused(e);
        }
    }

    @Override
    public Optional<ProviderPayment> find(MerchantCredentials credentials, String providerPaymentId) {
        var shop = shop(credentials);
        try {
            return Optional.of(payment(client.get()
                    .uri("/payments/{id}", providerPaymentId)
                    .headers(headers -> headers.setBasicAuth(shop.shopId(), shop.secretKey()))
                    .retrieve()
                    .body(YookassaPayment.class)));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public ProviderRefund refund(MerchantCredentials credentials, RefundOrder order) {
        var shop = shop(credentials);
        try {
            return refund(client.post()
                    .uri("/refunds")
                    .headers(headers -> headers.setBasicAuth(shop.shopId(), shop.secretKey()))
                    .header("Idempotence-Key", order.refundId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreateRefund(order.providerPaymentId(), Amount.of(order.amount()), order.reason()))
                    .retrieve()
                    .body(YookassaRefund.class));
        } catch (RestClientResponseException e) {
            throw refused(e);
        }
    }

    @Override
    public Optional<ProviderRefund> findRefund(MerchantCredentials credentials, String providerRefundId) {
        var shop = shop(credentials);
        try {
            return Optional.of(refund(client.get()
                    .uri("/refunds/{id}", providerRefundId)
                    .headers(headers -> headers.setBasicAuth(shop.shopId(), shop.secretKey()))
                    .retrieve()
                    .body(YookassaRefund.class)));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<ProviderNotification> parse(String body) {
        Notification notification;
        try {
            notification = json.readValue(body, Notification.class);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        if (notification == null
                || notification.event() == null
                || notification.object() == null
                || notification.object().id() == null) {
            return Optional.empty();
        }
        var event = notification.event();
        var kind = event.startsWith("payment.")
                ? ProviderNotification.Kind.PAYMENT
                : event.startsWith("refund.") ? ProviderNotification.Kind.REFUND : ProviderNotification.Kind.OTHER;
        return Optional.of(
                new ProviderNotification(event, kind, notification.object().id()));
    }

    private static MerchantCredentials.Yookassa shop(MerchantCredentials credentials) {
        if (credentials instanceof MerchantCredentials.Yookassa shop) {
            return shop;
        }
        throw new IllegalArgumentException("Not YooKassa credentials");
    }

    private static ProviderPayment payment(@Nullable YookassaPayment payment) {
        if (payment == null || payment.id() == null || payment.status() == null || payment.amount() == null) {
            throw new IllegalStateException("YooKassa returned an incomplete payment");
        }
        var state = switch (payment.status()) {
            case "succeeded" -> ProviderPayment.State.SUCCEEDED;
            case "canceled" -> ProviderPayment.State.CANCELED;
            // `pending`, and `waiting_for_capture`, which we never ask for.
            default -> ProviderPayment.State.PENDING;
        };
        var url = payment.confirmation() == null ? null : payment.confirmation().confirmationUrl();
        var reason = payment.cancellationDetails() == null
                ? null
                : payment.cancellationDetails().reason();
        return new ProviderPayment(
                payment.id(), state, payment.amount().money(), url == null ? null : URI.create(url), reason);
    }

    private static ProviderRefund refund(@Nullable YookassaRefund refund) {
        if (refund == null || refund.id() == null || refund.status() == null) {
            throw new IllegalStateException("YooKassa returned an incomplete refund");
        }
        var state = switch (refund.status()) {
            case "succeeded" -> ProviderRefund.State.SUCCEEDED;
            case "canceled" -> ProviderRefund.State.CANCELED;
            default -> ProviderRefund.State.PENDING;
        };
        return new ProviderRefund(refund.id(), state);
    }

    /** Turns YooKassa's refusal into our error. A 5xx is theirs, and stays a server error. */
    private RuntimeException refused(RestClientResponseException e) {
        if (!e.getStatusCode().is4xxClientError()) {
            return e;
        }
        if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            return new MerchantRejectedException();
        }
        ErrorBody error = null;
        try {
            error = e.getResponseBodyAs(ErrorBody.class);
        } catch (RuntimeException unreadable) {
            // Reported as a plain failure below.
        }
        log.warn(
                "YooKassa refused a request with {}, code {}, parameter {}, description {}",
                e.getStatusCode().value(),
                error == null ? null : error.code(),
                error == null ? null : error.parameter(),
                error == null ? null : error.description());
        if (error != null && error.description() != null && error.description().contains(LOW_AMOUNT)) {
            return new LowAmountException();
        }
        return new PaymentFailedException();
    }

    private record Me(
            @JsonProperty("account_id") @Nullable String accountId,
            @Nullable Boolean test) {}

    private record Amount(String value, String currency) {
        static Amount of(Money money) {
            return new Amount(money.value().toPlainString(), money.currency().name());
        }

        Money money() {
            return new Money(new BigDecimal(value), Money.Currency.valueOf(currency));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Confirmation(
            @Nullable String type,
            @JsonProperty("return_url") @Nullable String returnUrl,
            @JsonProperty("confirmation_url") @Nullable String confirmationUrl) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CreatePayment(
            Amount amount,
            boolean capture,
            Confirmation confirmation,
            String description,
            Map<String, String> metadata,
            @Nullable Receipt receipt) {}

    private record Receipt(Customer customer, List<ReceiptItem> items) {}

    private record Customer(String email) {}

    private record ReceiptItem(
            String description,
            String quantity,
            Amount amount,
            @JsonProperty("vat_code") int vatCode,
            @JsonProperty("payment_subject") String paymentSubject,
            @JsonProperty("payment_mode") String paymentMode) {}

    private record YookassaPayment(
            @Nullable String id,
            @Nullable String status,
            @Nullable Amount amount,
            @Nullable Confirmation confirmation,
            @JsonProperty("cancellation_details") @Nullable CancellationDetails cancellationDetails) {}

    private record CancellationDetails(
            @Nullable String party, @Nullable String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CreateRefund(
            @JsonProperty("payment_id") String paymentId,
            Amount amount,
            @Nullable String description) {}

    private record YookassaRefund(
            @Nullable String id, @Nullable String status) {}

    private record Notification(
            @Nullable String event, @Nullable NotifiedObject object) {}

    private record NotifiedObject(@Nullable String id) {}

    private record ErrorBody(
            @Nullable String code,
            @Nullable String description,
            @Nullable String parameter) {}
}
