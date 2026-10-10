# Billing module

> What a school owes Vkurse. Vkurse charges a school for one thing: the storage it uses. A school tops up a balance through Vkurse's own shop, and each day of storage is taken from it. There are no plans and no subscriptions.

| Layer | File | Description |
|---|---|---|
| `domain` | `BillingAccount` | A school's balance and its state: `open`, `topUp`, `charge`, `takeBack`, `status`, `ensureCanUpload`, `ensureCanRead`, `noticeDue` |
| | `LedgerEntry` | One change of a balance, made only by the account together with the change |
| | `StorageUsage` | What a school stored on a day and what the day cost |
| | `StoragePrice` | The price of one GB a month from a day on; `dailyCharge(bytes, day)` |
| | `TopUpAmount` | Value object: 100 to 100 000 RUB |
| | `BillingErrors`, `*Repository` | |
| `application` | `BillingService` | `overview`, `ledger`, `usage`, `topUp` |
| | `TopUpListener` | Credits a paid top-up; debits a refunded one |
| | `StorageChargeJob`, `StorageCharger` | Charge every school for the day's storage |
| | `BillingStorageGate` | Storage's `SchoolStorageGate`: closes uploads and reading to a school that owes |
| | `BillingNotifier` | The emails to a school's owner |
| `infrastructure` | `Jpa*Repository` | |
| `web` | `BillingController` and its records | |

It depends on [`payment`](../payment/README.md) (Vkurse's own shop, payments and their events), [`storage`](../storage/README.md) (how much a school stores; it implements storage's gate), [`school`](../school/README.md) (roles, the owner) and `user`. None of them depends on it.

## Rules

**The balance**
- A school has one account, opened at its first top-up or first charge. A school without one has a balance of 0.
- **Every change of the balance writes a ledger row in the same transaction, with the account's row locked.** A ledger row is named by its kind and what caused it (unique together), so a payment is credited once, a day is charged once and a refund is taken back once, however often an event or the job comes again. A balance always equals the sum of its ledger rows.
- The balance may go below zero: that is a debt. **Vkurse never charges a card by itself**; a debt is paid by a top-up.

**Top-ups**
- Owner and admin top up, 100 to 100 000 RUB at a time, by a payment to Vkurse's own shop (see the [payment module](../payment/README.md#vkurses-own-shop)). The balance grows when the payment succeeds, a moment after: `PaymentSucceeded` is handled after the payment's transaction.
- The amount credited is what our own payment row says.
- A school can't refund a top-up. When Vkurse refunds one (M11), the balance is debited by `PaymentRefunded`.

**Storage charges**
- Usage is every confirmed, not deleted file of the school: library files, videos, homework files, the logo, covers. 1 GB is 1024³ bytes.
- **Each day (Moscow time) a school is charged once** for what it stores when it is measured: `bytes ÷ GB × price ÷ days in this month`, rounded to kopecks. Usage too small to cost half a kopeck a day costs nothing, and makes no ledger row.
- The job runs every 10 minutes: the first run after midnight does the work, the later ones pick up what failed. A day's usage row is what its charge was computed from, and never changes.
- The price is the latest row of `billing_storage_prices` that has started. **The seeded 5.00 RUB per GB a month is a placeholder.** A new price is a new row; an admin adds it in M11.
- A deleted school is not charged.

**A school that owes**

| State | When | What stops |
|---|---|---|
| `ACTIVE` | The balance is 0 or more | Nothing |
| `DEBT` | The balance is below zero | Every upload for the school, students' homework files too: 402 `billing/payment-required` |
| `SUSPENDED` | In debt for more than 14 days | Also every signed link to the school's private files and videos, for everyone: 402 `billing/suspended` |

- Nothing is ever deleted. Public files (the logo, covers) keep working, and a user's own picture is not the school's.
- While suspended the staff can still list and delete files, see the balance and top up.
- A top-up that brings the balance to 0 or more ends both states at once.
- The owner is emailed once per state: when the balance covers under 7 days of storage (`billing-low-balance`), when it goes below zero (`billing-debt`, with the day of suspension), and at the first charge after suspension (`billing-suspended`).

**Who may do what** (roles are in the [school module](../school/README.md#rules)): owner and admin see the balance, the ledger and the usage, and top up. Authors and curators get 403; a school the caller doesn't work in is 404.

| Error | When |
|---|---|
| 402 `billing/payment-required` | An upload for a school whose balance is below zero |
| 402 `billing/suspended` | A link to a private file of a school in debt for more than 14 days |

## Endpoints

| Endpoint | Body | Who | Returns |
|---|---|---|---|
| `GET /schools/{id}/billing` | | owner, admin | `{ balance, status, debtSince, suspendsAt, storedBytes, price, dailyCharge, daysLeft }` |
| `GET /schools/{id}/billing/ledger` | | owner, admin | `{ id, createdAt, kind, amount, balanceAfter, referenceId }[]`, newest first |
| `GET /schools/{id}/billing/usage` | | owner, admin | `{ day, bytes, price, charge }[]`, newest first |
| `POST /schools/{id}/billing/top-ups` | `{ amount, returnUrl }` | owner, admin | `{ paymentId, amount, confirmationUrl }` |

Lists take `$limit` and `$skip`. Money is `{ value, currency }`.

- `kind`: `TOP_UP`, `STORAGE_CHARGE`, `REFUND`. `amount` is above zero for money in and below for money out.
- `price` is for one GB a month; `dailyCharge` is what a day of the school's storage costs today; `daysLeft` is how long the balance lasts at that cost (absent when storage costs nothing now).
- After the provider sends the payer back, call `POST /payments/{paymentId}/sync` and read the balance again.

## Tables

- `billing_accounts`: `school_id` (unique), `balance_amount_*`, `debt_since`, `low_balance_notified_at`, `debt_notified_at`, `suspension_notified_at`.
- `billing_ledger`: `account_id`, `school_id`, `kind`, `amount_*`, `balance_after_amount_*`, `reference_id`; unique on (`kind`, `reference_id`).
- `billing_storage_usage`: `school_id`, `day`, `bytes`, `price_amount_*`, `charge_amount_*`; unique on (`school_id`, `day`).
- `billing_storage_prices`: `amount_*`, `effective_from` (unique).
