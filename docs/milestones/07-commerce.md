# M7: Commerce

> A course's offers, a school's promo codes, orders and checkout, in the `commerce` module of `services/platform`. Designed fresh: there is no NestJS source for it. How it works is in the [module README](../../services/platform/src/main/java/vkurse/platform/commerce/README.md).

## Scope

| Part | State |
|---|---|
| `Offer`: several per course, a price, optional access length; on sale or not; archive | Done |
| `PromoCode`: percent or fixed amount, end time, number of uses, chosen offers | Done |
| `Order`: place, pay through a merchant, cancel, expire; paid by the payment's success; refunded by a whole refund | Done |
| Free orders (a free offer, or a code that covers the price) | Done |
| `OrderPaid`, `OrderRefunded` | Done. Nothing listens until M8 |
| The buyer becomes a student of the school, and gets an email | Done |
| Public list of a course's offers on sale | Done |
| Access to lessons, "my courses", taking access away after a refund | Not done: M8 |
| Bundles of courses, installments, gifts, invoices for companies, a crossed-out old price | Not done |
| Sales reports | Not done: M11 |
| Notifying the staff of a sale | Not done |
| Refunding automatically a payment for a course the buyer already has | Not done: it is logged as an error for the staff |

## Decisions

- **A module of `services/platform`**, depending on `course`, `payment`, `school` and `user`. None of them depends on it.
- **An offer sells one course**, and a course has several offers.
- **An order freezes what was bought**, so a later change of a price or a code never changes what a buyer owes or paid.
- **A buyer has a course once.** A new order for a course replaces the buyer's unpaid one.
- **Only `PaymentSucceeded` makes an order paid.** It is handled after the payment's transaction is committed, through the event publication registry, so a crash between the two can't lose it. The order therefore turns `PAID` a moment after the payment.
- **Paid money wins**: an expired or canceled order becomes paid when its payment succeeds. The expiry job doesn't ask whether a payment is still pending, because the outcome is right either way.
- **While an order's payment is pending, paying again returns the same payment**; the provider is asked first, so a payment that was just paid isn't started again. Two `pay` requests at the very same moment can still start two payments.
- **A promo code's use is counted when the order is placed** and given back if it is never paid. Counting at payment would let more orders than the limit be placed at once.
- **Every reason a code can't be used is one error**, so codes can't be probed.
- **A whole refund makes the order `REFUNDED`**; a partial one changes nothing.
- **Owner and admin manage sales.** Authors don't set prices.
- **Becoming a student is done here, in the paid order's transaction**, by calling `school`. `school` can't listen for `OrderPaid`: that would be a module cycle.

## Verified by hand

Against the environment from `compose.dev.yaml`. The rules were checked with a local stand-in for the YooKassa API, which lets a payment be marked paid or canceled; the real YooKassa test shop was used for what is marked "real".

- **Offers:** a new offer is not on sale; offers of a draft course are not on sale and can't be ordered (409 `commerce/offer-unavailable`); once activated and the course published, anyone sees them. A deactivated or archived offer, and any offer of an unpublished course, can't be ordered. An archived offer leaves the lists, and an order placed with it stays readable. The access length is set and removed.
- **Promo codes:** percent and fixed amounts quote correctly, a fixed amount larger than the price brings the total to zero, and a code limited to one offer works for that offer. An expired code, one for another offer, a switched-off one, another school's, an unknown one and a malformed one all give 400 `commerce/promo-code-invalid`. The same text twice → 409; after archiving, the text is free again.
- **Order and pay:** a buyer orders with a one-use code → the use is counted, and a second buyer can't use it. The offer's price and name are then changed → the order keeps its own. Paying returns the provider's page; paying again returns the same payment (one payment row). The payment succeeds → after `sync` the order is `PAID`, the buyer is in the school's students, the email is in Mailpit, and the `PaymentSucceeded` publication is completed once. A second `sync` changes nothing.
- **Rules:** ordering the same course again → 409 `commerce/already-bought`; paying or canceling a paid order → 409 `commerce/order-not-payable`; another school's merchant → 409 `payment/merchant-unavailable`; someone else's order → 404.
- **Free:** a free offer, and a 100 % code, give an order that is `PAID` at once with no payment; the buyer is a student.
- **Replace and cancel:** ordering a course again cancels the earlier unpaid order and gives its code's use back; a canceled order can't be paid or canceled again. After a payment was canceled, paying starts a new one.
- **Refund:** a partial refund leaves the order `PAID`; refunding the rest makes it `REFUNDED`.
- **Expiry:** the job expired an order whose time was moved into the past and gave its code's use back; an expired order can't be paid; when its earlier payment then succeeded, the order became `PAID`.
- **Roles:** an author can't manage offers or codes or see orders (403); an admin can; a buyer sees only their own orders; a non-member and another buyer get 404; no token → 401.
- **Real YooKassa test shop:** see the last section of [06-payment.md](06-payment.md).
- Not verified: `OrderPaid` reaching a listener (there is none until M8); a payment that succeeds for a course the buyer already has; two `pay` requests at the same moment; an event that fails and is delivered again; the limits of 20 offers and 200 codes.
