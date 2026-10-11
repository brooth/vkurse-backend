# M10: Billing

> A school's balance at Vkurse, top-ups through Vkurse's own shop, and a daily charge for the storage a school uses, in the `billing` module of `services/platform`. The balance and ledger follow the rules of a NestJS credit account; the rest is designed fresh. How it works is in the [module README](../../services/platform/src/main/java/vkurse/platform/billing/README.md).

## Scope

| Part | State |
|---|---|
| `BillingAccount` with a ledger; every change atomic and made once | Done |
| Vkurse's own shop: a merchant of no school, set from the service's settings | Done |
| Top-ups by owner and admin; credited by `PaymentSucceeded` | Done |
| Storage usage per school and day; the daily charge at a price per GB a month | Done |
| Debt: uploads blocked at once, private links after 14 days; emails to the owner | Done |
| Debit of a refunded top-up (`PaymentRefunded`) | Done, not exercised: nothing can refund a top-up until M12 |
| Changing the price, adjusting a balance, refunding a top-up | Not done: M12 |
| Saved cards and automatic top-ups | Not done |
| Invoices and bank transfers for companies | Not done |
| A free quota, a cap on a school's storage | Not done: every byte is paid |
| Charging a deleted school for the files it leaves | Not done: they stay, unbilled, as since M4 |
| Deleting the files of a school that never pays | Not done |

## Decisions

- **A new module**, depending on `payment`, `storage`, `school` and `user`. `storage` stays a leaf: it asks a port of its own (`SchoolStorageGate`) and `billing` implements it.
- **Storage is charged daily**, for what a school stores when the day is measured, at a price per GB a month. Deleting video lowers the bill from the next day.
- **No free quota.** A new school starts at 0 and may upload; the first daily charge puts it in debt, and uploads stop until it tops up. Blocking at exactly 0 would forbid even a logo before paying. Usage under half a kopeck a day rounds to nothing.
- **A debt blocks uploads at once and reading after 14 days**, for everyone, staff included. Nothing is deleted, public files keep working, and the staff can still delete files and top up.
- **Vkurse never charges a card itself**: there are no saved cards. AGENTS.md rule 8 was reworded for it.
- **Vkurse's own shop is the merchant of no school**, in the `payment` module, made at startup from two required settings and encrypted like any merchant's credentials. A top-up is an ordinary payment of the paying school, so notifications, the sync job and the events needed no change.
- **A school can't refund a payment to Vkurse**, and doesn't see the shop among its merchants or its top-ups among its sales.
- **The ledger is the idempotence**: a row is unique by kind and cause, checked under the account's lock.
- **The price is a table row**, so that a day keeps the price it was charged at. The seeded 5.00 RUB is a placeholder.
- **The job runs every 10 minutes and charges once a day**, so a failed school or a stopped service is caught up without anyone's help.
- **The owner is emailed once per state**; the marks are cleared when the state ends.

## Deviations from the source

| Source | Vkurse |
|---|---|
| An account per user, for buying credits to spend on requests | An account per school, for storage |
| Spending was refused when the balance was too low | The balance goes below zero; the debt closes uploads, then reading |
| Lifetime totals (spent, purchased) kept on the account | Not kept: the ledger has them |
| A saved payment method on the account, charged automatically | Not ported |
| `reserve` and `releaseReservation` stubs that did nothing | Left out |
| The balance update and its transaction record were not atomic, and the balance was read and written back | One transaction, the account's row locked, the change made by the aggregate |
| A top-up was credited on every delivery of the webhook | Once: the ledger row is unique by payment |
| One shop from the environment, switched off when its keys were missing | Required settings; a merchant row like any other |

## Verified by hand

Against the environment from `compose.dev.yaml`, with the local stand-in for the YooKassa API; the real YooKassa test shop where marked.

- **Done when:** a school with 100 GB stored tops up 500 RUB → pays → `sync` → the balance is 500.00 with a `TOP_UP` row → the job charges the day → a usage row (100 GB at 5.00), a `STORAGE_CHARGE` of 16.13 (a 31-day month) and a balance of 483.87.
- **Once only:** a second `sync` and a repeated notification leave the balance and the ledger; the job's next run charges nobody again. Every untouched balance equals the sum of its ledger rows.
- **Charges:** a 3 KB file gives a usage row with 0.00 and no ledger row; a deleted school with 100 GB is not charged; a school with no account gets one.
- **Debt:** a school with files and no money goes to -16.13 → library and logo uploads are 402 `billing/payment-required`, a download link still works, the owner's own picture upload works, the owner gets the email with the day of suspension. A school with money gets no email and uploads.
- **Low balance:** 100 RUB with 16.13 a day leaves 5 days → the email.
- **Suspended:** with the debt moved back 15 days, a download link is 402 `billing/suspended`; listing files, deleting one and the overview work; the next charge sends the suspension email, and the debt email is not sent again. A top-up above the debt makes the school `ACTIVE`, uploads work, the marks are cleared.
- **The platform shop:** made at startup with encrypted credentials; not in the school's merchants, its top-ups not in the school's payments; the owner can't rename, disconnect, test or refund through it (404, 403); the payer reads their top-up, a stranger doesn't.
- **Roles and amounts:** author and curator → 403; a non-member → 404; no token → 401; 99 and 100 001 RUB → 400; a foreign return URL → 400.
- **Real YooKassa test shop:** a top-up is created, with YooKassa's payment page; while unpaid it is `PENDING` and the balance doesn't move.
- Startup: V9 applies, and the platform merchant row is made.
- Not verified: a start without the shop's settings (it should fail); a top-up paid at the real shop; the debit of a refunded top-up; a 30-day month and a price change; two top-up events or a top-up and a charge at the same moment; midnight in Moscow; receipts on the platform shop; M8's lesson and homework links in a suspended school (they pass the same gate as the library's).
