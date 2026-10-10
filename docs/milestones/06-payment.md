# M6: Payment

> A school's merchants, payments to them, refunds and provider notifications, in the `payment` module of `services/platform`, with YooKassa as the first provider. Ported from two NestJS sources: a YooKassa service with one shop, and a merchant model with several providers. How it works is in the [module README](../../services/platform/src/main/java/vkurse/platform/payment/README.md).

## Scope

| Part | State |
|---|---|
| `Merchant`: a school's account with a provider; connect, change, disable, disconnect; encrypted credentials | Done |
| `PaymentGateway` port and the YooKassa adapter | Done. Verified against a local stand-in for the API; the real test shop is still to be checked (see below) |
| `Payment`: started for another module, settled only by the provider's answer | Done |
| Notifications (webhooks) with an inbox; a job that asks the provider about pending payments | Done |
| Refunds, whole and partial | Done |
| `PaymentSucceeded`, `PaymentCanceled`, `PaymentRefunded` | Done. Nothing listens until M7 |
| 54-FZ receipts, a setting of each merchant | Done |
| A test payment for the owner | Done |
| Checkout, orders, offers, promo codes | Not done: M7 |
| T-Bank, Robokassa; YooKassa partner OAuth | Not done: each is one more adapter, or one more way to connect |
| Saved cards, autopay, two-step payments | Not done |
| Vkurse's own shop, the school balance, storage charges | Not done: M10 |
| Rate limiting and an IP allowlist on the webhook | Not done: M14 |

## Decisions

- **A student pays the school directly.** Each school connects its own merchants; Vkurse never receives or holds a student's money, so there are no payouts and no sales balance.
- **Vkurse has no plans or subscriptions.** It charges a school only for storage, in M10. PLAN.md was changed for this in the same change. AGENTS.md rule 8 (balance-first) still stands, and is reworded for storage charges when M10 is planned.
- **A school may have several merchants**, and the payer chooses among the active ones.
- **YooKassa is connected with a shop id and a secret key**, not partner OAuth. So we can't register webhooks ourselves: the school sets our URL in its cabinet.
- **A payment's outcome never depends on a notification.** A job asks the provider about every pending payment, and the payer's app asks on the way back.
- **A notification's body is never believed**; it only tells us which payment to ask about. The URL has the merchant's id and no secret, because a forged call can do no more than make us ask.
- **"Exactly once" is the aggregate's rule**, kept under a row lock: a settled payment ignores every later answer. The inbox records the notification that settled it, in the same transaction.
- **A notification that changes nothing is not recorded.** Recording first would let a forged or early notification mark the real one as already seen.
- **Our payment's id is the idempotence key**, so the payment is saved before the provider is called, and removed if the provider refuses.
- **Credentials are one encrypted column** (AES-256-GCM, a key from `VKURSE_PAYMENT_ENCRYPTION_KEY`), a sealed type with a record per provider.
- **Only the owner manages merchants and refunds.** Admins see them.
- **`Payment` is generic**: `purpose` and `referenceId` belong to whoever started it.
- **A disconnected merchant keeps its row but loses its credentials**, so its old payments can't be refunded here any more.

## Deviations from the source

| Source | Vkurse |
|---|---|
| One shop for the whole backend, its keys in the environment | A `Merchant` per school, with its own credentials, passed to the gateway on each call |
| Without the keys the feature switched itself off at startup | No conditional wiring: a school without a merchant simply has none |
| The payer's money came to the platform and was credited to the payer's balance | The money goes to the school's own shop; nothing is credited anywhere |
| `Idempotence-Key` was a new random UUID on every call | Our payment's id, and our refund's id |
| The webhook acted on every delivery | A settled payment ignores every later answer; the settling notification is in the inbox |
| The amount to credit and the user were read from the payment's metadata at the provider | Our own `Payment` row says who paid whom and how much; the provider's amount is only compared with it |
| A canceled payment was only logged | `PaymentCanceled` |
| No refunds | Refunds, whole and partial |
| A receipt on every payment, with a fixed VAT code | A setting of each merchant |
| A saved payment method made the next payment an autopay | Not ported |
| Subscription payments were routed by a `paymentType` in the metadata | `purpose` and `referenceId` on our row; the starter listens for the event |
| The merchant model also had offline methods (at the counter, on arrival) | Not ported: a course is paid online |
| YooKassa merchants were onboarded with partner OAuth, which also registered the webhooks | Shop id and secret key; the school sets the notification URL itself |
| A merchant's secrets were plain fields, hidden from responses by serialization groups | One encrypted column; no response record has a field for them |
| Money as a floating-point number of rubles | `Money` |
| Errors as HTTP 200 with an error body | Real statuses; the codes `payment-failed` and `payment-failed/low-amount` are kept |

## Verified by hand

Against a local stand-in for the YooKassa API (a small script that answers `/me`, `/payments` and `/refunds` the way the API documents). **The real YooKassa test shop is not checked yet.**

- **Done when:** a test payment is created → paid at the provider → a notification arrives → the payment is `SUCCEEDED`, with one inbox row. The same notification three more times, another event for the same payment, and the payer's `sync` leave the row untouched (same version and `updated_at`) and the inbox at one row.
- A forged "succeeded" notification for a payment that is really pending changes nothing and leaves no inbox row; the real one after it settles the payment. A notification with an unknown payment id, an unreadable body, or sent to an unknown merchant gets 200 and changes nothing.
- No notification at all: the payer's `sync` settles a paid payment; the job settles a paid one and cancels one the provider canceled (with the provider's reason).
- The request to the provider carries our payment id as the idempotence key and, with receipts on, the receipt line with the payer's email and the VAT code.
- Merchants: wrong keys → 400 `payment/merchant-rejected`; the stored credentials are ciphertext, and neither the database nor any response holds the key; receipts without a VAT code → 400; a disabled merchant → 409 for new payments while refunds still work; replacing credentials; the sixth merchant → 429; disconnecting wipes the credentials, hides the merchant, and refunds through it → 409.
- Refunds: a part, then the rest; more than is left, or one more after that → 409 `payment/not-refundable`; a canceled payment → 409. The provider's idempotence keys are our refund ids.
- Roles: an admin sees merchants, payments and refunds but changes nothing (403); an author sees none of it (403); a non-member → 404; no token → 401.
- The return URL: a stranger's site and a look-alike host are refused.
- Provider unreachable: starting a payment fails and leaves no row; a refund fails and frees its amount; connecting fails.
- Startup: V6 applies.
- Not verified: the real YooKassa API (its exact bodies, a real card payment, what a shop without receipts does with a receipt, its real notification); a payer who is not on the school's staff reading their own payment (nothing starts such a payment until M7); a payment that stays pending for hours or days; two notifications for one payment at the very same moment; an event reaching a listener (there is none yet).
