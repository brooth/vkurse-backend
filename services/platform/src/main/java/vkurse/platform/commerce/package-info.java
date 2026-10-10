/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

/**
 * Selling courses: a course's offers (what it costs), a school's promo codes, and orders. A buyer orders an offer and
 * pays the school through one of its merchants; when the payment succeeds the order is paid, and {@code OrderPaid}
 * tells other modules that the buyer has the course.
 */
@NullMarked
@ApplicationModule(displayName = "Commerce")
package vkurse.platform.commerce;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
