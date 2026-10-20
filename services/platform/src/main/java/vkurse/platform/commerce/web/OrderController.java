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
import vkurse.platform.commerce.domain.Order;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.Payment;
import vkurse.platform.payment.domain.ReturnUrl;

/** Orders: a buyer's own, and a school's for the staff who manage its sales. */
@RestController
class OrderController {
    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * What an offer costs, with a promo code if one is given, and the merchants it can be paid with. Nothing is
     * ordered.
     */
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
        return response(placed.order(), placed.order().isPending() ? placed.merchants() : List.of());
    }

    /** The caller's own orders, newest first. */
    @GetMapping("/orders")
    List<OrderResponse> listMine(@GetSession UserSession session, @RequestParam Map<String, String> params) {
        return responses(orders.listMine(session.userId(), Paging.parse(params)));
    }

    /** The school's orders, newest first. */
    @GetMapping("/schools/{schoolId}/orders")
    List<OrderResponse> list(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return responses(orders.list(session.userId(), schoolId, Paging.parse(params)));
    }

    @GetMapping("/orders/{id}")
    OrderResponse get(@GetSession UserSession session, @PathVariable UUID id) {
        var order = orders.get(session.userId(), id);
        return response(order, orders.merchantsFor(order));
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
                response(checkout.order(), List.of()),
                checkout.payment().getId(),
                checkout.payment().getStatus(),
                checkout.payment().getConfirmationUrl());
    }

    /** Gives up an order that isn't paid. */
    @PostMapping("/orders/{id}/cancel")
    OrderResponse cancel(@GetSession UserSession session, @PathVariable UUID id) {
        return response(orders.cancel(session.userId(), id), List.of());
    }

    private OrderResponse response(Order order, List<Merchant> merchants) {
        return OrderResponse.of(order, merchants, orders.courseOf(order).orElse(null));
    }

    private List<OrderResponse> responses(List<Order> found) {
        var courses = orders.coursesOf(found);
        return found.stream()
                .map(order -> OrderResponse.of(order, List.of(), courses.get(order.getCourseId())))
                .toList();
    }

    record OrderRequest(
            @NotNull UUID offerId, @Size(max = 64) @Nullable String promoCode) {}

    record PayRequest(
            @NotNull UUID merchantId,
            @NotBlank @Size(max = ReturnUrl.MAX_LENGTH) String returnUrl) {}

    /**
     * {@code merchants} are the ways to pay the total, to choose one for {@code POST /orders/{id}/pay} once the order
     * is placed; empty when nothing is left to pay, and when the school can't take payments now.
     */
    record QuoteResponse(
            PriceResponse price,
            PriceResponse discount,
            PriceResponse total,
            List<OrderResponse.MerchantOption> merchants) {
        static QuoteResponse of(OrderService.Quoted quoted) {
            var quote = quoted.quote();
            return new QuoteResponse(
                    PriceResponse.of(quote.price()),
                    PriceResponse.of(quote.discount()),
                    PriceResponse.of(quote.total()),
                    quoted.merchants().stream()
                            .map(OrderResponse.MerchantOption::of)
                            .toList());
        }
    }

    /**
     * {@code confirmationUrl} is absent if the payment needs no more action from the buyer: {@code paymentStatus} is
     * then {@code SUCCEEDED} for an order whose earlier payment turned out to be paid.
     */
    record CheckoutResponse(
            OrderResponse order,
            UUID paymentId,
            Payment.Status paymentStatus,
            @Nullable String confirmationUrl) {}
}
