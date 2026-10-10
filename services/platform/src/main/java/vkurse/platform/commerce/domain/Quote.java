/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import vkurse.core.money.Money;

/**
 * What an offer costs a buyer: its price, what a promo code takes off, and what is left to pay.
 *
 * @param price the offer's price
 * @param discount what the promo code takes off; zero without one
 * @param total what the buyer pays
 */
public record Quote(Money price, Money discount, Money total) {

    /** The offer's full price, with no promo code. */
    public static Quote full(Offer offer) {
        var price = offer.getPriceAmount();
        return new Quote(price, price.minus(price), price);
    }

    static Quote discounted(Offer offer, Discount discount) {
        var price = offer.getPriceAmount();
        var taken = discount.of(price);
        return new Quote(price, taken, price.minus(taken));
    }
}
