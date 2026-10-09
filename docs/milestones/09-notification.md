# M9: Notification

> Email and Telegram notifications for every service, in `libs/core` (`vkurse.core.notification`). Ported from the NestJS `notification` module. Usage is in [libs/core/README.md](../../libs/core/README.md#notifications).

## Scope

| Part | State |
|---|---|
| `NotificationService.send` (deliver now) and `submit` (store, deliver by a job with retries) | Done |
| Email (SMTP, text + HTML) and Telegram (Bot API, text) with fallback between them | Done |
| Thymeleaf templates with a shared layout, subjects from `MessageSource`, Russian base bundle | Done |
| The OTP email of `auth` moved to a template | Done |
| A welcome email on `UserRegistered`, the first `submit` caller | Done |
| Push notifications and push tokens on sessions | Not done: no mobile client and no provider chosen yet |
| A user's Telegram chat id | Not done: targets are raw chat ids until users can link Telegram |
| Notifications for schools, payments, learning; the ops chat | With their own milestones |

## Decisions

- **In `libs/core`, not a module of `platform`**, because every service notifies. Core holds the mechanism. A service owns its templates, its subjects, its `notifications` table, and the decision of what to send.
- **Two entry points.** `send` stores nothing: the caller learns the outcome and handles a failure. `submit` stores the message in the caller's transaction; a job delivers it.
- **Delivery of a submitted message is at-least-once.** The job sends outside a transaction and saves the outcome after, so a node that dies in between sends again.
- **Retries** are rules of the `Notification` aggregate: after a failure it waits 1 min, 5 min, 15 min, 1 h; the fifth failure is final (`FAILED`). Sent and failed rows are removed after 30 days.
- **A secret is never submitted.** A stored message keeps its parameters as JSON, so the OTP code goes through `send`.
- **`Notification` is registered with Hibernate explicitly** (a `BeanPostProcessor` on `PersistenceManagedTypes`). Adding core's package to the auto-configuration packages doesn't work: Spring Data's auto-configuration reads that list before core can add to it.

## Deviations from the source

| Source | Vkurse |
|---|---|
| A module of the one backend | A package of `libs/core`; each service keeps its own table and templates |
| Stateless only; a failed send is lost | `send` (the same) + `submit` (stored, retried) |
| `send` returns a failed result | `send` throws `DeliveryFailedException` |
| "Shots" with channel payloads | `Message` of attempts, each a `Content` and its `Target`s |
| `plain`, `markdown`, `html`, `markdown-template`, `html-template` | `Content.Text` and `Content.Template`. Markdown and raw HTML had no callers |
| Handlebars with `noEscape: true`: user text went into email HTML unescaped | Thymeleaf; `th:text` escapes |
| A template is a file path under an env-set directory; the check against leaving it is a prefix match | A name (`[a-z0-9-]`) of a classpath resource |
| The email's HTML document was also sent to Telegram with `parse_mode: HTML`, which Telegram rejects, so that fallback never worked | Every template has a text form, and Telegram gets it |
| HTML emails had no text part | Text + HTML |
| Subject and title passed by the caller, picked with `lang === 'ru' ? … : …` | `notification.{name}.subject` in the message bundle |
| A channel without configuration logs a warning at startup and fails at send time | Required settings; a placeholder token works locally |
| No timeouts on SMTP or the Telegram call | Both have timeouts |
| A push stub that logs and reports success | Not ported |
| `support-response` template; the insights message to an ops chat | Not ported: there is no support module, and the ops chat is M12 |

## Verified by hand

- `POST /auth/otp` → the templated email (text and HTML) in Mailpit; `POST /auth/otp/login` works.
- A first sign-in → a `notifications` row, `PENDING` then `SENT` within 10 seconds; the welcome email in Mailpit.
- Mail server unreachable → `POST /auth/otp` is a 500 and leaves no row in `auth_otp_codes`.
- Mail server unreachable, a stored message with an email and a Telegram target → both are tried, the row stays `PENDING` with `attempts = 1`, `last_error` and a retry a minute later; with the mail server back it becomes `SENT`.
- Not verified: a Telegram message that arrives. It needs a real bot token and chat; with the placeholder token the call reaches Telegram and is rejected.
