# Payment module

> Payments to a school. A school connects merchants (its own accounts with payment providers), and a payer's money goes straight to the school: this service only creates the payment in the school's shop and learns its outcome. Vkurse never receives or holds that money.

| Layer | File | Description |
|---|---|---|
| `domain` | `Merchant` | A school's account with a provider: `connect`, `rename`, `changeReceipts`, `replaceCredentials`, `enable`, `disable`, `disconnect` |
| | `MerchantCredentials`, `CredentialCipher` | The credentials (a sealed type, one record per provider), and the port that encrypts them |
| | `ReceiptSettings` | Value object: whether the provider issues 54-FZ receipts for the merchant, and the VAT code |
| | `Payment` | One payment: `start`, `test`, `accept`, `apply`, `checked`, `refund`, `settle`, `drop` |
| | `Refund` | Money of a payment given back |
| | `WebhookEvent` | The inbox: the notifications that settled something |
| | `PaymentGateway` | Port: one provider (`describe`, `create`, `find`, `refund`, `findRefund`, `parse`) |
| | `ProviderPayment`, `ProviderRefund`, `ProviderNotification`, `PaymentOrder`, `RefundOrder`, `MerchantAccount` | What goes to a provider and comes back |
| | `PaymentSucceeded`, `PaymentCanceled`, `PaymentRefunded` | Events, each raised once |
| | `ReturnUrl` | Value object: a page of our web app |
| | `PaymentErrors`, `*Repository` | |
| `application` | `MerchantService` | `connect`, `list`, `findOffered`, `update`, `replaceCredentials`, `enable`, `disable`, `disconnect` |
| | `PaymentService` | `start` (for other modules), `testPayment`, `get`, `list`, `sync`, `refund`, `refunds` |
| | `PaymentWebhookService` | Handles a provider's notification |
| | `PaymentSync` | Asks the provider about a payment or refund and applies the answer |
| | `PaymentSyncJob` | Does that for everything pending, every minute |
| | `PaymentGateways` | The gateway of each provider |
| `infrastructure` | `YookassaPaymentGateway`, `AesCredentialCipher`, `Jpa*Repository` | |
| `web` | `MerchantController`, `PaymentController`, `PaymentWebhookController` and their records | |

Root class: `PaymentProperties` (`vkurse.payment.*`).

## Using it from another module

```java
// Which merchants a payer may choose among:
var offered = merchants.findOffered(schoolId);               // MerchantService

// Start a payment. Whether the payer may buy this is your check, made before:
var payment = payments.start(merchantId, payerId, Money.rub("4900"), "Курс «Python с нуля»",
        "order", orderId, returnUrl);                        // PaymentService
// Send the payer to payment.getConfirmationUrl().

// Learn the outcome: listen for the event and match your own purpose.
@ApplicationModuleListener
void on(PaymentSucceeded event) {
    if (event.purpose().equals("order")) { … event.referenceId() … }
}
```

A payment doesn't know what was bought. Its starter names a `purpose` and its own `referenceId`, and gets both back in `PaymentSucceeded`, `PaymentCanceled` and `PaymentRefunded`.

## Vkurse's own shop

One merchant belongs to no school: Vkurse's own shop, which schools pay Vkurse through (the [billing module](../billing/README.md) tops a balance up with it).
- It is made at startup from `VKURSE_PAYMENT_PLATFORM_SHOP_ID` and `VKURSE_PAYMENT_PLATFORM_SHOP_SECRET_KEY`, and brought in line with them at every start. The credentials are encrypted like any merchant's. The provider is not asked at startup: a wrong key shows at the first payment. A key that starts with `test_` marks a test shop.
- Receipts for it are `VKURSE_PAYMENT_PLATFORM_SHOP_RECEIPTS` and `…_VAT_CODE`.
- Another module starts a payment to it with `PaymentService.startForPlatform(payerId, schoolId, …)`. The payment is the paying school's: its payer, owner and admins read and `sync` it. It is settled like any payment, and raises the same events.
- **A school can't manage this merchant or refund a payment to it** (404, 403), and sees neither among its merchants and payments.
- Its notification URL is `/payments/webhooks/yookassa/{id}`, with the id logged at startup; Vkurse sets it in its own YooKassa cabinet.

## Rules

**Merchants**
- A merchant is a school's own account with a provider. The money of a payment goes to that account; Vkurse is not a party to it.
- A school may connect up to 5 merchants (429 `payment/too-many-merchants`), and a payer chooses among the active ones.
- **Credentials are confirmed with the provider before they are kept** (400 `payment/merchant-rejected` if it refuses), encrypted with AES-256-GCM under `VKURSE_PAYMENT_ENCRYPTION_KEY`, and never returned by any endpoint or written to a log.
- A disabled merchant is not offered to payers (409 `payment/merchant-unavailable`), but its payments are still followed and can be refunded.
- **Disconnecting is final**: the credentials are wiped and the merchant disappears from the lists. Its row stays, because payments point to it, but those payments can no longer be checked or refunded here.
- Receipts are a setting of each merchant. When on, a payment carries one receipt line (the payment's description, its whole amount, the VAT code, the payer's email) and the provider issues the receipt. A school turns it on when its shop has receipts set up.

**Payments**
- A payment is saved as `PENDING` before the provider is asked to create it, and **its id is the provider's idempotence key**: asking twice creates one payment. If the provider refuses or can't be reached, the payment is removed and the request fails.
- **Only the provider's own answer settles a payment.** We ask it, with the merchant's credentials, when a notification arrives, when the payer comes back (`POST /payments/{id}/sync`), and from a job for everything pending. The paid amount must equal ours.
- `PENDING` becomes `SUCCEEDED` or `CANCELED` once, under a row lock, and the event is raised once. Any later answer about a settled payment changes nothing.
- The job asks about a pending payment after as long as it has existed (at least a minute, at most 30), and stops after 7 days. A payment the provider never took is canceled after an hour.
- The return URL must be a page of our web app (`VKURSE_WEB_URL`).
- A test payment is at most 100 RUB. The owner pays it themselves to check a merchant; it is an ordinary payment with the purpose `merchant-test`.

**Notifications**
- Each merchant has its own URL, shown as `notificationUrl`. It holds no secret.
- **A notification's body is never believed.** It only names a payment or a refund; we then ask the provider. A notification about something that isn't ours, one we can't read, and a forged one all get 200 and change nothing.
- The notification that settled a payment or a refund is recorded in the inbox, in the same transaction. One that changed nothing is not recorded, so a forged or early notification can't stand in the way of the real one.
- A provider gets a 5xx only when handling failed, so that it sends the notification again.

**Refunds**
- Only a paid payment is refunded, in whole or in part, and all refunds together never exceed it: 409 `payment/not-refundable`. The amount is set aside under a row lock before the provider is asked, and freed if the provider refuses.
- A refund's id is the provider's idempotence key.

**Who may do what** (roles are in the [school module](../school/README.md#rules))

| | OWNER | ADMIN | the payer |
|---|---|---|---|
| Connect, change and disconnect merchants; test payments | ✓ | | |
| Refund a payment | ✓ | | |
| See merchants, the school's payments and refunds | ✓ | ✓ | |
| See one payment, and ask the provider about it | ✓ | ✓ | their own |

Authors and curators get 403. A school the caller doesn't work in is 404.

| Error | When |
|---|---|
| 400 `payment/merchant-rejected` | The provider doesn't accept the credentials |
| 400 `payment-failed` | The provider refused to create a payment or a refund |
| 400 `payment-failed/low-amount` | The amount is below the provider's minimum |
| 409 `payment/merchant-unavailable` | The merchant is disabled or disconnected |
| 409 `payment/not-refundable` | The payment isn't paid, or less is left of it than asked back |
| 429 `payment/too-many-merchants` | A sixth merchant |

## YooKassa

- A school connects with its **shop id and secret key** (YooKassa cabinet → Integration → API keys). We call the API as that shop, with HTTP basic authentication.
- **The school must set our notification URL in its cabinet** (Integration → HTTP notifications), with the events `payment.succeeded`, `payment.canceled` and `refund.succeeded`. YooKassa's API for registering webhooks works only for partner applications with OAuth, which we don't use. A school that skips this still gets its payments settled, by the job, a minute or more later.
- Payments are created with `capture: true` and a redirect confirmation, so a payment is either paid or not: nothing is held.
- YooKassa has no error code for an amount below the minimum; it is recognised by the text of the description. Its test shop takes any amount.
- A shop without receipts set up (`fiscalization_enabled: false`) accepts a payment that carries a receipt and ignores the receipt.
- A new provider is a new `PaymentProvider` constant, a `MerchantCredentials` record, a `PaymentGateway` implementation and a webhook route.

## Endpoints

| Endpoint | Body | Who | Returns |
|---|---|---|---|
| `POST /schools/{id}/merchants` | `{ provider, name, yookassa: { shopId, secretKey }, receipts?: { enabled, vatCode? } }` | owner | `MerchantResponse` |
| `GET /schools/{id}/merchants` | | owner, admin | `MerchantResponse[]`: the connected ones |
| `PATCH /merchants/{id}` | `{ name?, receipts? }` | owner | `MerchantResponse` |
| `POST /merchants/{id}/credentials` | `{ provider, yookassa: { shopId, secretKey } }` | owner | `MerchantResponse` |
| `POST /merchants/{id}/enable`, `/disable` | | owner | `MerchantResponse` |
| `DELETE /merchants/{id}` | | owner | `204` |
| `POST /merchants/{id}/test-payment` | `{ amount, returnUrl }` | owner | `PaymentResponse` |
| `GET /schools/{id}/payments` | | owner, admin | `PaymentResponse[]`, newest first |
| `GET /payments/{id}` | | the payer, owner, admin | `PaymentResponse` |
| `POST /payments/{id}/sync` | | the payer, owner, admin | `PaymentResponse` |
| `POST /payments/{id}/refunds` | `{ amount?, reason? }` (no `amount` = all that is left) | owner | `RefundResponse` |
| `GET /payments/{id}/refunds` | | owner, admin | `RefundResponse[]` |
| `POST /payments/webhooks/yookassa/{merchantId}` | the provider's notification | public | `200` |

- `MerchantResponse`: `id`, `createdAt`, `updatedAt`, `schoolId`, `provider`, `name`, `accountId`, `testMode`, `receipts { enabled, vatCode }`, `status` (`ACTIVE`, `DISABLED`), `notificationUrl`.
- `PaymentResponse`: `id`, `createdAt`, `updatedAt`, `merchantId`, `schoolId`, `payerId`, `amount { value, currency }`, `description`, `purpose`, `referenceId`, `status` (`PENDING`, `SUCCEEDED`, `CANCELED`), `confirmationUrl` (while pending), `paidAt`, `canceledAt`, `cancellationReason`, `refundedAmount`.
- `RefundResponse`: `id`, `createdAt`, `paymentId`, `amount`, `reason`, `requestedBy`, `status` (`PENDING`, `SUCCEEDED`, `CANCELED`).

There is no endpoint that starts an ordinary payment: the [commerce module](../commerce/README.md) starts one for an order (`POST /orders/{id}/pay`), with the purpose `order`.

## Tables

- `payment_merchants`: `school_id`, `provider`, `name`, `account_id`, `test_mode`, `credentials` (ciphertext; null once disconnected), `receipts_enabled`, `receipts_vat_code`, `status`.
- `payments`: `merchant_id`, `school_id`, `payer_id`, `amount_*`, `description`, `purpose`, `reference_id`, `status`, `provider_payment_id`, `confirmation_url`, `return_url`, `paid_at`, `canceled_at`, `cancellation_reason`, `refunded_amount_*`, `next_check_at`.
- `payment_refunds`: `payment_id`, `merchant_id`, `amount_*`, `reason`, `requested_by`, `status`, `provider_refund_id`.
- `payment_webhook_events`: `provider`, `merchant_id`, `event`, `object_id`, unique together.
