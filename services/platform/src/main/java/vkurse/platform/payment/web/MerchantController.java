/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.core.money.Money;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.platform.PlatformProperties;
import vkurse.platform.payment.application.MerchantService;
import vkurse.platform.payment.application.PaymentService;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.MerchantCredentials;
import vkurse.platform.payment.domain.PaymentProvider;
import vkurse.platform.payment.domain.ReceiptSettings;
import vkurse.platform.payment.domain.ReturnUrl;

/** A school's merchants: its accounts with payment providers. The owner manages them; admins see them. */
@RestController
class MerchantController {
    private final MerchantService merchants;
    private final PaymentService payments;
    private final PlatformProperties properties;

    MerchantController(MerchantService merchants, PaymentService payments, PlatformProperties properties) {
        this.merchants = merchants;
        this.payments = payments;
        this.properties = properties;
    }

    /** Connects an account. The provider is asked to confirm the credentials; they are then kept encrypted. */
    @PostMapping("/schools/{schoolId}/merchants")
    MerchantResponse connect(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody ConnectRequest data) {
        var receipts = data.receipts() == null
                ? ReceiptSettings.OFF
                : ReceiptSettings.of(data.receipts().enabled(), data.receipts().vatCode());
        return response(merchants.connect(
                session.userId(), schoolId, data.name(), credentials(data.provider(), data.yookassa()), receipts));
    }

    @GetMapping("/schools/{schoolId}/merchants")
    List<MerchantResponse> list(@GetSession UserSession session, @PathVariable UUID schoolId) {
        return merchants.list(session.userId(), schoolId).stream()
                .map(this::response)
                .toList();
    }

    @PatchMapping("/merchants/{id}")
    MerchantResponse update(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody UpdateRequest data) {
        var receipts = data.receipts() == null
                ? null
                : ReceiptSettings.of(data.receipts().enabled(), data.receipts().vatCode());
        return response(merchants.update(session.userId(), id, data.name(), receipts));
    }

    /** Replaces the credentials, for the same provider. The provider is asked to confirm the new ones. */
    @PostMapping("/merchants/{id}/credentials")
    MerchantResponse replaceCredentials(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody CredentialsRequest data) {
        return response(
                merchants.replaceCredentials(session.userId(), id, credentials(data.provider(), data.yookassa())));
    }

    @PostMapping("/merchants/{id}/enable")
    MerchantResponse enable(@GetSession UserSession session, @PathVariable UUID id) {
        return response(merchants.enable(session.userId(), id));
    }

    /** Stops offering the merchant to payers. Its payments can still be refunded. */
    @PostMapping("/merchants/{id}/disable")
    MerchantResponse disable(@GetSession UserSession session, @PathVariable UUID id) {
        return response(merchants.disable(session.userId(), id));
    }

    /** Disconnects the merchant for good: its credentials are forgotten, and its payments can't be refunded here. */
    @DeleteMapping("/merchants/{id}")
    ResponseEntity<Void> disconnect(@GetSession UserSession session, @PathVariable UUID id) {
        merchants.disconnect(session.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Starts a small payment the owner pays themselves, to check that the merchant works end to end. */
    @PostMapping("/merchants/{id}/test-payment")
    PaymentResponse testPayment(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody TestPaymentRequest data) {
        return PaymentResponse.of(
                payments.testPayment(session.userId(), id, Money.rub(data.amount()), data.returnUrl()));
    }

    private MerchantResponse response(Merchant merchant) {
        return MerchantResponse.of(merchant, properties.publicUrl());
    }

    private static MerchantCredentials credentials(PaymentProvider provider, @Nullable YookassaRequest yookassa) {
        return switch (provider) {
            case YOOKASSA -> {
                if (yookassa == null) {
                    throw new BadRequestException("A YooKassa merchant needs `yookassa` credentials");
                }
                yield new MerchantCredentials.Yookassa(
                        yookassa.shopId().trim(), yookassa.secretKey().trim());
            }
        };
    }

    /** The credentials go under the key named after the provider: {@code yookassa} for {@code YOOKASSA}. */
    record ConnectRequest(
            @NotNull PaymentProvider provider,
            @NotBlank @Size(max = Merchant.MAX_NAME_LENGTH) String name,
            @Valid @Nullable YookassaRequest yookassa,
            @Valid @Nullable ReceiptsRequest receipts) {}

    record CredentialsRequest(
            @NotNull PaymentProvider provider,
            @Valid @Nullable YookassaRequest yookassa) {}

    record YookassaRequest(
            @NotBlank @Size(max = 32) String shopId,
            @NotBlank @Size(max = 200) String secretKey) {}

    /** {@code vatCode} is required when {@code enabled}. */
    record ReceiptsRequest(
            @NotNull Boolean enabled, @Nullable Integer vatCode) {}

    /** {@code null} leaves a field unchanged. */
    record UpdateRequest(
            @Size(min = 1, max = Merchant.MAX_NAME_LENGTH) @Nullable
            String name,

            @Valid @Nullable ReceiptsRequest receipts) {}

    record TestPaymentRequest(
            @NotNull @DecimalMin("1") @Digits(integer = 10, fraction = 2)
            BigDecimal amount,

            @NotBlank @Size(max = ReturnUrl.MAX_LENGTH) String returnUrl) {}
}
