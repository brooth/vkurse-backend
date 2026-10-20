# Commerce module

> Selling courses: a course's offers (what it costs), a school's promo codes, and orders. A buyer orders an offer and pays the school through one of its merchants; when the payment succeeds, the order is paid and the buyer has the course.

| Layer | File | Description |
|---|---|---|
| `domain` | `Offer` | One way to buy a course: `create`, `changeDetails`, `changeAccess`, `activate`, `deactivate`, `archive`, `isOnSale`, `ensureSellable` |
| | `PromoCode` | A school's code: `create`, `change`, `changeLimits`, `activate`, `deactivate`, `archive`, `quote`, `use`, `giveBack` |
| | `PromoCodeText`, `Discount`, `OfferScope` | Value objects: the code's text; a percent or a fixed amount off; the offers a code is for |
| | `Quote` | What an offer costs a buyer: price, discount, total; `needsPayment` |
| | `Order` | `place`, `attach`, `markPaid`, `cancel`, `expire`, `markRefunded` |
| | `OrderPaid`, `OrderRefunded` | Events, each raised once per order |
| | `CommerceErrors`, `*Repository` | |
| `application` | `OfferService` | Staff: `create`, `list`, `update`, `activate`, `deactivate`, `archive`. Public: `findOnSale` (a course's), `getOnSale` (one, with its course) |
| | `PromoCodeService` | Staff: `create`, `list`, `update`, `activate`, `deactivate`, `archive` |
| | `OrderService` | Buyer: `quote`, `place`, `get`, `listMine`, `pay`, `cancel`. Staff: `list`, `get`. For the responses: `merchantsFor`, `courseOf`, `coursesOf` |
| | `OrderPaymentListener` | Makes an order paid when its payment succeeds, refunded when it is given back whole |
| | `OrderFulfilment` | What follows a paid order: the buyer becomes a student, and gets an email |
| | `OrderExpiryJob` | Expires unpaid orders, every 10 minutes |
| | `SalesAccess` | Who may manage a school's sales |
| `infrastructure` | `Jpa*Repository` | |
| `web` | `OfferController`, `PromoCodeController`, `OrderController` and their records | |

It depends on [`course`](../course/README.md) (what is sold), [`payment`](../payment/README.md) (how it is paid), [`school`](../school/README.md) (roles, students) and `user`.

## Using it from another module

```java
@ApplicationModuleListener
void on(OrderPaid event) {
    // event.buyerId() has event.courseId(), for event.accessDays() days (null = no end).
}

@ApplicationModuleListener
void on(OrderRefunded event) {
    // The buyer no longer has the course by this order.
}
```

The [learning module](../learning/README.md) listens for both: it gives the course and takes it away.

## Rules

**Offers**
- A course has up to 20 offers (its tariffs). An offer has a name, a price in rubles and optionally for how many days the buyer has the course (no value = no end). A price of zero makes it free.
- A new offer is not on sale: it is activated when it is ready. **An offer can be bought when it is active, not archived, and its course is published**; otherwise 409 `commerce/offer-unavailable`.
- Archiving takes an offer off sale for good and hides it from the lists. Its row stays, because orders point to it.
- Anyone may read an offer on sale by its id, with its course and school (`GET /catalog/offers/{id}`): the page where it is bought needs nothing else. An offer that can't be bought is 404 there.

**Promo codes**
- A school has up to 200 codes. A code is 3 to 32 latin letters, digits, hyphens and underscores, kept in capitals, unique among the school's codes that are not archived (409 `commerce/promo-code-taken`).
- A code takes a percent (1 to 100) or a fixed amount off, never more than the price. It may end at a time, work a limited number of times, and be for chosen offers only.
- **A code that can't be used is always 400 `commerce/promo-code-invalid`**, whether it is unknown, another school's, switched off, expired, used up or for other offers. The reason is not said, so codes can't be probed.
- **A use is counted when an order is placed with the code**, under a lock on the code, and given back if that order is canceled or expires unpaid. A use of a paid order stays counted, also after a refund.
- One code per order.

**Orders**
- A buyer must be signed in. Anyone may buy; buying makes them a student of the school.
- **An order keeps what was bought as it was when ordered**: the offer's name, the course's title, the access length, the price, the discount and the total. Later changes of the offer or the code don't touch it.
- **A buyer has a course once**: ordering a course they have by a paid order is 409 `commerce/already-bought`. A paid order whose access days have run out no longer counts, so the course can be bought again. Ordering a course again while an earlier order waits for payment cancels the earlier one.
- **Only paid orders count for that**, not who has the course now (the [learning module](../learning/README.md) knows that, and depends on this one). A course given by the staff can still be ordered, and paying makes its access longer; a course the staff took away while the paid order's days still run can't be ordered again. The web app reads `GET /learning/courses` to say "you have this course", and handles the 409 as well.
- An order's `schoolSlug` and `courseSlug` are the address of the course's page as it is now, not as it was when ordered; absent while the course is not in the catalog.
- An order with nothing to pay (a free offer, or a code that covers the price) is paid at once, with no payment.
- An order waits for payment for 24 hours, then expires.

**Paying**
- The buyer chooses one of the school's active merchants (`merchants` in the order, and in the quote before it) and gets the provider's page. While that payment is pending, paying again returns the same one, so one order doesn't get two payments. After a payment was canceled, a new one is started. If the earlier payment turns out to be paid, it is returned with `paymentStatus: SUCCEEDED` and no `confirmationUrl`: there is nothing to send the buyer to.
- **Only the payment's success makes an order paid.** The payment module raises `PaymentSucceeded` when the provider confirms the money; `OrderPaymentListener` handles it after that transaction is committed, in its own. If handling fails, the event is kept and delivered again a minute or two later (`EventRedeliveryJob`). So an order turns `PAID` a moment after its payment does: the web app calls `POST /payments/{paymentId}/sync` when the buyer returns, then reads the order.
- **Paid money wins**: an order that had expired or was canceled still becomes paid when its payment succeeds.
- If a payment succeeds for a course the buyer already has by another order, the order is left as it is and an error is logged: the staff must refund that payment.
- Becoming paid makes the buyer a student of the school and sends them the `order-paid` email with a link to the order's page (`{webUrl}/orders/{id}`), in the same transaction, and raises `OrderPaid`.
- A refund of the whole payment (made by the school's owner, see the [payment module](../payment/README.md)) makes the order `REFUNDED` and raises `OrderRefunded`. A partial refund changes nothing in the order.

**Who may do what**

| | OWNER | ADMIN | the buyer | anyone |
|---|---|---|---|---|
| Manage offers and promo codes | ✓ | ✓ | | |
| See the school's orders | ✓ | ✓ | | |
| See the offers on sale of a published course | | | | ✓ |
| Order, pay and cancel; see their own orders | | | ✓ | |

Authors and curators get 403. A school the caller doesn't work in, and another buyer's order, are 404.

| Error | When |
|---|---|
| 409 `commerce/offer-unavailable` | The offer is not on sale, or its course is not published |
| 400 `commerce/promo-code-invalid` | The code can't be used for this offer |
| 409 `commerce/promo-code-taken` | The school has this code already |
| 409 `commerce/already-bought` | The buyer has this course by a paid order whose access still lasts |
| 409 `commerce/order-not-payable` | The order is not waiting for payment (paid, canceled, expired) |
| 429 `commerce/too-many` | A 21st offer of a course, or a 201st promo code of a school |

## Endpoints

| Endpoint | Body | Who | Returns |
|---|---|---|---|
| `POST /courses/{courseId}/offers` | `{ name, description?, price, accessDays? }` | owner, admin | `OfferResponse` |
| `GET /courses/{courseId}/offers` | | owner, admin | `OfferResponse[]`, on sale or not |
| `PATCH /offers/{id}` | `{ name?, description?, price?, access?: { days } }` (`null` = unchanged; `access.days: null` = no end) | owner, admin | `OfferResponse` |
| `POST /offers/{id}/activate`, `/deactivate` | | owner, admin | `OfferResponse` |
| `DELETE /offers/{id}` | | owner, admin | `204` |
| `GET /catalog/courses/{courseId}/offers` | | public | `OfferResponse[]`: on sale only |
| `GET /catalog/offers/{id}` | | public | `{ offer: OfferResponse, course: CatalogCourseResponse }`; 404 if it can't be bought |
| `POST /schools/{id}/promo-codes` | `{ code, discount: { kind, percent? \| amount? }, validUntil?, maxUses?, offerIds? }` | owner, admin | `PromoCodeResponse` |
| `GET /schools/{id}/promo-codes` | | owner, admin | `PromoCodeResponse[]` |
| `PATCH /promo-codes/{id}` | `{ discount?, offerIds?, limits?: { validUntil, maxUses } }` | owner, admin | `PromoCodeResponse` |
| `POST /promo-codes/{id}/activate`, `/deactivate` | | owner, admin | `PromoCodeResponse` |
| `DELETE /promo-codes/{id}` | | owner, admin | `204` |
| `POST /orders/quote` | `{ offerId, promoCode? }` | any user | `{ price, discount, total, merchants[ { id, provider, name } ] }`; no merchants when the total is zero, or the school can't take payments now |
| `POST /orders` | `{ offerId, promoCode? }` | any user | `OrderResponse` |
| `GET /orders` | | any user | `OrderResponse[]`: their own |
| `GET /orders/{id}` | | the buyer, owner, admin | `OrderResponse` |
| `POST /orders/{id}/pay` | `{ merchantId, returnUrl }` | the buyer | `{ order, paymentId, paymentStatus, confirmationUrl? }` |
| `POST /orders/{id}/cancel` | | the buyer | `OrderResponse` |
| `GET /schools/{id}/orders` | | owner, admin | `OrderResponse[]`, newest first |

- `OfferResponse`: `id`, `courseId`, `name`, `description`, `price { value, currency }`, `accessDays`, `active`, `position`.
- `CatalogCourseResponse` is the [course module](../course/README.md)'s: `id`, `title`, `slug`, `summary`, `cover`, `publishedAt`, `lessons`, `school { id, name, slug, logo }`.
- `PromoCodeResponse`: `id`, `createdAt`, `code`, `discount { kind, percent, amount }`, `validUntil`, `maxUses`, `usedCount`, `offerIds`, `active`.
- `OrderResponse`: `id`, `createdAt`, `updatedAt`, `schoolId`, `buyerId`, `offerId`, `courseId`, `offerName`, `courseTitle`, `schoolSlug`, `courseSlug`, `accessDays`, `price`, `discount`, `total`, `promoCode`, `status` (`PENDING`, `PAID`, `CANCELED`, `EXPIRED`, `REFUNDED`), `paymentId`, `paidAt`, `expiresAt`, `merchants[ { id, provider, name } ]` (while pending).

## Tables

- `offers`: `school_id`, `course_id`, `name`, `description`, `price_amount_*`, `access_days`, `active`, `position`, `archived_at`.
- `promo_codes`: `school_id`, `code`, `discount_kind`, `discount_percent`, `discount_amount`, `valid_until`, `max_uses`, `used_count`, `scope` (JSONB), `active`, `archived_at`.
- `orders`: `school_id`, `buyer_id`, `offer_id`, `course_id`, `offer_name`, `course_title`, `access_days`, `price_amount_*`, `discount_amount_*`, `total_amount_*`, `promo_code_id`, `promo_code`, `status`, `payment_id`, `paid_at`, `expires_at`.
