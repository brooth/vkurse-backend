/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

/**
 * Payments to a school. A school connects merchants (its own accounts with payment providers), and a payer's money
 * goes straight to the school: this service only creates the payment in the school's shop and learns its outcome.
 * Other modules start a payment with {@code PaymentService} and listen for {@code PaymentSucceeded}.
 */
@NullMarked
@ApplicationModule(displayName = "Payment")
package vkurse.platform.payment;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
