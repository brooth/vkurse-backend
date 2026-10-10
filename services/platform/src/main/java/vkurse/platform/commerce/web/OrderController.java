/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.commerce.application.OrderService;
import vkurse.platform.commerce.domain.Quote;
import vkurse.platform.payment.domain.ReturnUrl;

/** Orders: a buyer's own, and a school's for the staff who manage its sales. */
@RestController
class OrderController {
    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** What an offer costs, with a promo code if one is given. Nothing is ordered. */
    @PostMapping("/orders/quote")
    QuoteResponse quote(@GetSession UserSession session, @Valid @RequestBody OrderRequest data) {
        return QuoteResponse.of(orders.quote(data.offerId(), data.promoCode()));
    }

    /**
     * Orders an offer. The caller's earlier unpaid order for the same course is canceled. An order with nothing to
     * pay comes back {@code PAID}.
     */
    @PostMapping("/orders")
    OrderResponse place(@GetSession UserSession session, @Valid @RequestBody OrderRequest data) {
        var placed = orders.place(session.userId(), data.offerId(), data.promoCode());
        return OrderResponse.of(placed.order(), placed.order().isPending() ? placed.merchants() : List.of());
    }

    /** The caller's own orders, newest first. */
    @GetMapping("/orders")
    List<OrderResponse> listMine(@GetSession UserSession session, @RequestParam Map<String, String> params) {
        return orders.listMine(session.userId(), Paging.parse(params)).stream()
                .map(order -> OrderResponse.of(order, List.of()))
                .toList();
    }

    /** The school's orders, newest first. */
    @GetMapping("/schools/{schoolId}/orders")
    List<OrderResponse> list(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return orders.list(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(order -> OrderResponse.of(order, List.of()))
                .toList();
    }

    @GetMapping("/orders/{id}")
    OrderResponse get(@GetSession UserSession session, @PathVariable UUID id) {
        var order = orders.get(session.userId(), id);
        return OrderResponse.of(order, orders.merchantsFor(order));
    }

    /**
     * Starts the payment of the order with one of its {@code merchants}, and returns the provider's page to send the
     * buyer to. When the provider sends them back, call {@code POST /payments/{paymentId}/sync} and read the order
     * again: it turns {@code PAID} a moment after the payment succeeds.
     */
    @PostMapping("/orders/{id}/pay")
    CheckoutResponse pay(@GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody PayRequest data) {
        var checkout = orders.pay(session.userId(), id, data.merchantId(), data.returnUrl());
        return new CheckoutResponse(
                OrderResponse.of(checkout.order(), List.of()),
                checkout.payment().getId(),
                checkout.payment().getConfirmationUrl());
    }

    /** Gives up an order that isn't paid. */
    @PostMapping("/orders/{id}/cancel")
    OrderResponse cancel(@GetSession UserSession session, @PathVariable UUID id) {
        return OrderResponse.of(orders.cancel(session.userId(), id), List.of());
    }

    record OrderRequest(
            @NotNull UUID offerId, @Size(max = 64) @Nullable String promoCode) {}

    record PayRequest(
            @NotNull UUID merchantId,
            @NotBlank @Size(max = ReturnUrl.MAX_LENGTH) String returnUrl) {}

    record QuoteResponse(PriceResponse price, PriceResponse discount, PriceResponse total) {
        static QuoteResponse of(Quote quote) {
            return new QuoteResponse(
                    PriceResponse.of(quote.price()),
                    PriceResponse.of(quote.discount()),
                    PriceResponse.of(quote.total()));
        }
    }

    /** {@code confirmationUrl} is absent if the payment needs no more action from the buyer. */
    record CheckoutResponse(
            OrderResponse order, UUID paymentId, @Nullable String confirmationUrl) {}
}
