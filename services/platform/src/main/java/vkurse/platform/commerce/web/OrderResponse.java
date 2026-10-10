/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.commerce.domain.Order;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.PaymentProvider;

/**
 * An order. While it is {@code PENDING}, {@code merchants} are the ways to pay it, to choose one for
 * {@code POST /orders/{id}/pay}; {@code paymentId} is the payment last started for it.
 */
public record OrderResponse(
        UUID id,
        Instant createdAt,
        @Nullable Instant updatedAt,
        UUID schoolId,
        UUID buyerId,
        UUID offerId,
        UUID courseId,
        String offerName,
        String courseTitle,
        @Nullable Integer accessDays,
        PriceResponse price,
        PriceResponse discount,
        PriceResponse total,
        @Nullable String promoCode,
        Order.Status status,
        @Nullable UUID paymentId,
        @Nullable Instant paidAt,
        Instant expiresAt,
        List<MerchantOption> merchants) {

    public static OrderResponse of(Order order, List<Merchant> merchants) {
        return new OrderResponse(
                order.getId(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getSchoolId(),
                order.getBuyerId(),
                order.getOfferId(),
                order.getCourseId(),
                order.getOfferName(),
                order.getCourseTitle(),
                order.getAccessDays(),
                PriceResponse.of(order.getPriceAmount()),
                PriceResponse.of(order.getDiscountAmount()),
                PriceResponse.of(order.getTotalAmount()),
                order.getPromoCode(),
                order.getStatus(),
                order.getPaymentId(),
                order.getPaidAt(),
                order.getExpiresAt(),
                merchants.stream()
                        .map(merchant ->
                                new MerchantOption(merchant.getId(), merchant.getProvider(), merchant.getName()))
                        .toList());
    }

    /** One way to pay: a merchant of the school, as a payer sees it. */
    public record MerchantOption(UUID id, PaymentProvider provider, String name) {}
}
