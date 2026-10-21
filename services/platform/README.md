# `services/platform`

> Users, authentication, schools, courses, sales, payments and file storage. The only service that issues tokens; every other service verifies them with the keys published at `/.well-known/jwks.json`.

Source root: `src/main/java/vkurse/platform`. Each direct subpackage is one module (a Spring Modulith application module), split into four layers.

| Module | Description |
|---|---|
| [`user/`](src/main/java/vkurse/platform/user/README.md) | The `User` aggregate and the caller's own profile (`/users/me`) |
| [`auth/`](src/main/java/vkurse/platform/auth/README.md) | Sessions, our JWTs, email OTP, Google and Apple sign-in, the security filter chain |
| [`school/`](src/main/java/vkurse/platform/school/README.md) | Schools, their staff with roles, invitations, students, the logo and the file library |
| [`course/`](src/main/java/vkurse/platform/course/README.md) | Courses, sections and lessons with block content, publishing, the public catalog with search |
| [`billing/`](src/main/java/vkurse/platform/billing/README.md) | A school's balance at Vkurse, top-ups, and the daily charge for the storage it uses |
| [`chat/`](src/main/java/vkurse/platform/chat/README.md) | A chat per course for its students and the school's staff: messages, banners, attachments, votes, live delivery |
| [`commerce/`](src/main/java/vkurse/platform/commerce/README.md) | A course's offers, a school's promo codes, orders and checkout |
| [`learning/`](src/main/java/vkurse/platform/learning/README.md) | Students taking courses: enrollments, which lessons are open, progress, homework and its review, quizzes |
| [`payment/`](src/main/java/vkurse/platform/payment/README.md) | A school's merchants (its accounts with payment providers), payments to them, refunds, provider notifications |
| [`storage/`](src/main/java/vkurse/platform/storage/README.md) | Files in Supabase Storage: signed uploads and downloads, per-school paths |

| Layer | Holds | May use |
|---|---|---|
| `domain` | Aggregates, value objects, events, repository interfaces, ports | `libs/core`, other modules' `domain` |
| `application` | Use cases: load, call the aggregate, save. Transactions, jobs, listeners | `domain`, other modules' `application` |
| `infrastructure` | JPA repositories, token signing, mail, Spring Security wiring | everything in its module |
| `web` | Controllers and request/response records | `application`, `domain` (to read) |

Root classes: `PlatformApplication` (entry point), `PlatformProperties` (`vkurse.public-url`, `vkurse.web-url`).

Events between modules are stored in `event_publication` with the change that raised them and handled after it is committed. `EventRedeliveryJob` (root class, every minute) hands an event to its listener again when the listener failed or never ran: first after a minute, then after longer pauses, up to half an hour. Spring Modulith doesn't do this by itself, so a listener may get an event twice and must be safe to run twice.

Notifications come from [`libs/core`](../../libs/core/README.md#notifications). This service adds the `notifications` table, the templates in `templates/notification/` (`otp-code`, `welcome`, `school-invitation`, `order-paid`, `course-granted`, `homework-accepted`, `homework-returned`, `billing-low-balance`, `billing-debt`, `billing-suspended`) and their subjects in `messages.properties`.

Resources: `application.yaml` (+ `-dev`, `-prod`), `db/migration/V{N}__{description}.sql` (schema `platform`), `templates/notification/`, `messages.properties` (Russian, the base bundle).

## Environment

| Variable | Required | Description |
|---|---|---|
| `VKURSE_NODE_ID` | yes | Instance name used in logs and alerts |
| `VKURSE_PUBLIC_URL` | yes | Public base URL of this service: token issuer, OAuth redirect URIs |
| `VKURSE_WEB_URL` | yes | Base URL of the web app: links in our emails (a school invitation) lead there |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | no (local defaults) | JDBC connection. It must be a direct connection to Postgres, not a pooler in transaction mode: the chat module listens for `NOTIFY` on it |
| `SPRING_PROFILES_ACTIVE` | no (`dev`) | `prod` in production (JSON logs) |
| `PORT` | no (`8080`) | HTTP port |
| `VKURSE_LOG_SQL` | no (`info`) | `debug` logs every SQL statement. More switches are in [Logs](#logs) |
| `VKURSE_TELEMETRY_ENABLED`, `KAFKA_BOOTSTRAP_SERVERS` | no (`false`, `localhost:54392`) | Send request, job and node numbers to Kafka for the `monitoring` service; see [Telemetry](../../libs/core/README.md#telemetry). Off needs no Kafka |
| `VKURSE_STORAGE_URL` | yes | Base URL of the Supabase Storage API as this service reaches it. Locally `http://127.0.0.1:54321`; behind the Supabase gateway it ends with `/storage/v1` |
| `VKURSE_STORAGE_PUBLIC_URL` | yes | The same API as a browser reaches it: upload, download and public links start with it |
| `VKURSE_STORAGE_SERVICE_KEY` | yes | Supabase service role key. `.env.example` has the published local demo key |
| `VKURSE_PAYMENT_ENCRYPTION_KEY` | yes | Encrypts schools' payment credentials in the database: 32 random bytes in Base64 (`openssl rand -base64 32`). Losing or changing it loses every connected merchant |
| `VKURSE_PAYMENT_PLATFORM_SHOP_ID`, `VKURSE_PAYMENT_PLATFORM_SHOP_SECRET_KEY` | yes | Vkurse's own YooKassa shop, which schools top up their balance through. A key that starts with `test_` is a test shop's |
| `VKURSE_PAYMENT_PLATFORM_SHOP_RECEIPTS`, `VKURSE_PAYMENT_PLATFORM_SHOP_VAT_CODE` | no | Whether YooKassa issues a 54-FZ receipt for each payment to that shop (default `false`), and its VAT code (default `1`) |
| `VKURSE_AUTH_PRIVATE_KEY` | yes | RSA key that signs our tokens: PKCS#8 PEM, 2048 bits or more, newlines as `\n` |
| `VKURSE_AUTH_KEY_ID` | yes | `kid` of that key |
| `VKURSE_AUTH_SECRET` | yes | HMAC key for OTP and exchange code hashes, at least 32 characters |
| `VKURSE_WEB_ORIGINS` | yes | Comma-separated frontend origins: allowed OAuth callbacks and CORS |
| `VKURSE_MAIL_FROM` | yes | Sender address of our emails |
| `TELEGRAM_BOT_TOKEN` | yes (a placeholder works locally) | Bot API token of the bot that sends our Telegram notifications |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | no (local Mailpit `127.0.0.1:54325`) | SMTP server |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | yes | Google OAuth client |
| `APPLE_CLIENT_ID` / `APPLE_TEAM_ID` / `APPLE_KEY_ID` / `APPLE_PRIVATE_KEY` | yes | Sign in with Apple: Services ID, team, key id, `.p8` PEM content |

Storage must be running when the service starts: it creates its buckets then (see the [storage module](src/main/java/vkurse/platform/storage/README.md#supabase-storage)).

Generate a local signing key: `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | awk '{printf "%s\\n", $0}'`.

## Logs

What is logged, and how a line is tied to its request or job, is described in [`libs/core`](../../libs/core/README.md#logging). In `dev` the console has `vkurse` at DEBUG, no SQL, and short colored lines (`18:22:55.405  INFO [HttpLogFilter] [<request id>] POST /auth/otp 200 …`, the pattern is in `application-dev.yaml`); in `prod` it is JSON with `requestId`, `userId` and `job` as fields.

| To see | Set |
|---|---|
| SQL | `VKURSE_LOG_SQL=debug` |
| Every run of every job | `LOGGING_LEVEL_VKURSE_JOB=debug` |
| HTTP lines without bodies | `LOGGING_LEVEL_VKURSE_HTTP_BODY=off` |
| The text of sent messages in `prod` (it holds sign-in codes) | `LOGGING_LEVEL_VKURSE_NOTIFICATION_BODY=debug` |

Locally an email goes to Mailpit, not to a real inbox: the `Sent … via 127.0.0.1:54325` line says so, and the email is at `http://127.0.0.1:54324`.

## Guidelines
- Keep this file and the module READMEs up to date in every change.
- New modules follow the layout in [AGENTS.md](../../AGENTS.md#module-structure).
