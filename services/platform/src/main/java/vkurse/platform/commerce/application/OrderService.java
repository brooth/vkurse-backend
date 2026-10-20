/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.application;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.web.Paging;
import vkurse.platform.commerce.domain.CommerceErrors.OfferUnavailableException;
import vkurse.platform.commerce.domain.CommerceErrors.PromoCodeInvalidException;
import vkurse.platform.commerce.domain.Offer;
import vkurse.platform.commerce.domain.OfferRepository;
import vkurse.platform.commerce.domain.Order;
import vkurse.platform.commerce.domain.OrderRepository;
import vkurse.platform.commerce.domain.PromoCode;
import vkurse.platform.commerce.domain.PromoCodeRepository;
import vkurse.platform.commerce.domain.PromoCodeText;
import vkurse.platform.commerce.domain.Quote;
import vkurse.platform.course.application.CatalogService;
import vkurse.platform.course.domain.CourseCard;
import vkurse.platform.payment.application.MerchantService;
import vkurse.platform.payment.application.PaymentService;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.Payment;
import vkurse.platform.payment.domain.PaymentErrors.MerchantUnavailableException;
import vkurse.platform.school.application.SchoolMemberService;
import vkurse.platform.user.domain.User;

/**
 * Orders: a buyer orders an offer and pays the school for it. The order is made paid by {@link OrderPaymentListener},
 * when the payment succeeds; nothing here trusts the buyer's word that they paid.
 */
@Service
public class OrderService {
    private final OrderRepository orders;
    private final OfferRepository offers;
    private final PromoCodeRepository promoCodes;
    private final CatalogService catalog;
    private final MerchantService merchants;
    private final PaymentService payments;
    private final SchoolMemberService staff;
    private final OrderFulfilment fulfilment;
    private final MessageSource messages;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OrderService(
            OrderRepository orders,
            OfferRepository offers,
            PromoCodeRepository promoCodes,
            CatalogService catalog,
            MerchantService merchants,
            PaymentService payments,
            SchoolMemberService staff,
            OrderFulfilment fulfilment,
            MessageSource messages,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.offers = offers;
        this.promoCodes = promoCodes;
        this.catalog = catalog;
        this.merchants = merchants;
        this.payments = payments;
        this.staff = staff;
        this.fulfilment = fulfilment;
        this.messages = messages;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * What an offer costs with a promo code, before ordering, and the merchants it could be paid with. Nothing is
     * stored and no use is counted.
     */
    public Quoted quote(UUID offerId, @Nullable String promoCode) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var offer = sellable(offerId).offer();
            var promo = promoCode == null ? null : promo(offer, promoCode, false);
            var quote = promo == null ? Quote.full(offer) : promo.quote(offer, clock.instant());
            return new Quoted(
                    quote, quote.needsPayment() ? merchants.findOffered(offer.getSchoolId()) : List.<Merchant>of());
        }));
    }

    /**
     * Places the caller's order for an offer. Their earlier unpaid orders for the same course are canceled. An order
     * with nothing to pay is paid at once.
     */
    public Placed place(UUID userId, UUID offerId, @Nullable String promoCode) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var now = clock.instant();
            var sold = sellable(offerId);
            var offer = sold.offer();
            for (var earlier : orders.findPending(userId, offer.getCourseId())) {
                earlier.cancel();
                orders.save(earlier);
                giveBack(earlier);
            }
            // Locked, so the uses of a code are counted one order at a time.
            var promo = promoCode == null ? null : promo(offer, promoCode, true);
            var quote = promo == null ? Quote.full(offer) : promo.quote(offer, now);
            var order = Order.place(
                    userId,
                    offer,
                    sold.course().course().getTitle(),
                    quote,
                    promo,
                    orders.findPaid(userId, offer.getCourseId()),
                    now);
            if (promo != null) {
                promo.use();
                promoCodes.save(promo);
            }
            orders.save(order);
            if (order.isPaid()) {
                fulfilment.fulfil(order);
            }
            return new Placed(order, merchants.findOffered(order.getSchoolId()));
        }));
    }

    /** An order, for its buyer and for the staff who manage the sales of its school. */
    public Order get(UUID userId, UUID orderId) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var order = orders.findById(orderId).orElseThrow(OrderService::notFound);
            if (!order.isOf(userId)) {
                try {
                    staff.require(order.getSchoolId(), userId).ensureCanManageSales();
                } catch (NotFoundException e) {
                    throw notFound();
                }
            }
            return order;
        }));
    }

    /** The caller's own orders, newest first. */
    public List<Order> listMine(UUID userId, Paging paging) {
        return Objects.requireNonNull(
                transaction.execute(status -> orders.findByBuyer(userId, paging.limit(), paging.offset())));
    }

    /** A school's orders, newest first, for the staff who manage its sales. */
    public List<Order> list(UUID userId, UUID schoolId, Paging paging) {
        return Objects.requireNonNull(transaction.execute(status -> {
            staff.require(schoolId, userId).ensureCanManageSales();
            return orders.findBySchool(schoolId, paging.limit(), paging.offset());
        }));
    }

    /** The order's course as the catalog shows it now; empty while it is not published. */
    public Optional<CourseCard> courseOf(Order order) {
        return catalog.publishedCourse(order.getCourseId());
    }

    /** The courses of those orders that are in the catalog now, by course id. */
    public Map<UUID, CourseCard> coursesOf(List<Order> found) {
        return catalog.publishedCourses(found.stream().map(Order::getCourseId).toList());
    }

    /** The merchants the buyer may pay an order with. */
    public List<Merchant> merchantsFor(Order order) {
        return order.isPending() ? merchants.findOffered(order.getSchoolId()) : List.of();
    }

    /**
     * Starts the payment of the caller's order with one of the school's merchants, and returns it with the provider's
     * page to send the buyer to. While a payment of the order with that merchant is still pending, the same one is
     * returned, so paying twice doesn't create two payments.
     */
    public Checkout pay(UUID userId, UUID orderId, UUID merchantId, String returnUrl) {
        var order = Objects.requireNonNull(transaction.execute(status -> {
            var mine = mine(userId, orderId);
            mine.ensurePayable(clock.instant());
            if (merchants.findOffered(mine.getSchoolId()).stream()
                    .noneMatch(merchant -> merchant.getId().equals(merchantId))) {
                throw new MerchantUnavailableException();
            }
            return mine;
        }));
        var earlier = order.getPaymentId();
        if (earlier != null) {
            // Asks the provider, so a payment that was just paid is not started again.
            var payment = payments.sync(userId, earlier);
            if (payment.isPending() && payment.getMerchantId().equals(merchantId)) {
                return new Checkout(order, payment);
            }
            if (payment.getStatus() == Payment.Status.SUCCEEDED) {
                return new Checkout(order, payment);
            }
        }
        var description = messages.getMessage(
                "commerce.payment.description",
                new Object[] {order.getCourseTitle()},
                Locale.forLanguageTag(User.DEFAULT_LOCALE));
        var payment = payments.start(
                merchantId,
                userId,
                order.getTotalAmount(),
                description.length() > Payment.MAX_DESCRIPTION_LENGTH
                        ? description.substring(0, Payment.MAX_DESCRIPTION_LENGTH)
                        : description,
                Order.PAYMENT_PURPOSE,
                orderId,
                returnUrl);
        var attached = Objects.requireNonNull(transaction.execute(status -> {
            var mine = orders.findByIdForUpdate(orderId).orElseThrow(OrderService::notFound);
            mine.attach(payment.getId(), clock.instant());
            return orders.save(mine);
        }));
        return new Checkout(attached, payment);
    }

    /** The buyer gives up an order they haven't paid. */
    public Order cancel(UUID userId, UUID orderId) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var order = orders.findByIdForUpdate(orderId)
                    .filter(found -> found.isOf(userId))
                    .orElseThrow(OrderService::notFound);
            order.cancel();
            giveBack(order);
            return orders.save(order);
        }));
    }

    /** Gives back the promo code use of an order that will not be paid. Call it in the order's transaction. */
    void giveBack(Order order) {
        var promoCodeId = order.getPromoCodeId();
        if (promoCodeId != null) {
            promoCodes.findByIdForUpdate(promoCodeId).ifPresent(promo -> {
                promo.giveBack();
                promoCodes.save(promo);
            });
        }
    }

    private Order mine(UUID userId, UUID orderId) {
        return orders.findById(orderId).filter(order -> order.isOf(userId)).orElseThrow(OrderService::notFound);
    }

    /** The offer with its course, if it can be bought now. */
    private Sold sellable(UUID offerId) {
        var offer = offers.findById(offerId).orElseThrow(OfferUnavailableException::new);
        var course = catalog.publishedCourse(offer.getCourseId());
        offer.ensureSellable(course.isPresent());
        return new Sold(offer, course.orElseThrow());
    }

    /** The school's promo code with that text; an unknown one is as invalid as any other. */
    private PromoCode promo(Offer offer, String text, boolean lock) {
        PromoCodeText code;
        try {
            code = new PromoCodeText(text);
        } catch (RuntimeException malformed) {
            throw new PromoCodeInvalidException();
        }
        var found =
                promoCodes.findBySchoolAndCode(offer.getSchoolId(), code).orElseThrow(PromoCodeInvalidException::new);
        return lock ? promoCodes.findByIdForUpdate(found.getId()).orElseThrow(PromoCodeInvalidException::new) : found;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Order not found");
    }

    /** What an offer costs, with the merchants the buyer could pay it with; none when nothing is left to pay. */
    public record Quoted(Quote quote, List<Merchant> merchants) {}

    /** An order that was just placed, with the merchants the buyer may pay it with. */
    public record Placed(Order order, List<Merchant> merchants) {}

    /** An order with the payment started for it. */
    public record Checkout(Order order, Payment payment) {}

    private record Sold(Offer offer, CourseCard course) {}
}
