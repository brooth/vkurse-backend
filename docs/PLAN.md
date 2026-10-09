# VKurse backend: main plan

VKurse is a platform for online schools in Russia. Schools (course authors) build and sell courses, and students buy them and study. The backend is a set of **Spring Boot** services on **Supabase** (Postgres and Storage).

This is the **main plan**. Every milestone below gets its own detailed plan in `docs/milestones/` before it is implemented.

## Decisions
| Topic | Decision |
|---|---|
| Market | Russia only: YooKassa, RUB, `ru` by default, 54-FZ receipts. Russia's 152-FZ data law means prod runs in a RU cloud (Yandex Cloud, Selectel or Timeweb): self-hosted Supabase, or plain Postgres + S3-compatible storage |
| Money flow | Each school connects **its own YooKassa shop** (credentials stored encrypted), and students pay the school directly. The **platform bills schools a SaaS subscription** through its own shop |
| Architecture | **Microservices** with a shared library (`libs/common`). `services/core` owns users and auth and is the only token issuer. Each service owns one Postgres schema and its migrations; no service reads another's tables |
| Model | **Rich domain model (DDD)**: aggregates own their rules and have no setters; modules are split into `domain` / `application` / `infrastructure` / `web`; repositories are explicit interfaces. See `AGENTS.md` |
| Supabase | **Postgres + Storage** only. The services are the only data gateway; PostgREST is not exposed for our schemas |
| Auth | **Our own `auth` module** in `core` (M2), not Supabase Auth: email OTP, Google and Apple login. Core issues RS256 JWTs (15-minute access + rotating refresh), publishes its keys at `/.well-known/jwks.json` and keeps sessions in Postgres. Other services verify tokens locally and trust them until they expire; on core itself a sign-out is instant. Russian logins (VK ID, Yandex ID, Telegram) can be added as more providers later |
| Build | One Gradle multi-project (`backend/`): Kotlin DSL + `libs.versions.toml` + foojay toolchains (JDK 25 is downloaded automatically) |
| Runtime | Java 25 LTS, Spring Boot 4.1, virtual threads on |
| Data access | **Hibernate (JPA)** on the aggregates themselves, through one `JpaAggregateRepository` subclass per aggregate, with no Spring Data repositories. **Flyway** only applies versioned schema scripts (`db/migration/V{N}__desc.sql`). We don't use Hibernate's `ddl-auto`, because it isn't safe in prod |
| API | No wrapper: success returns the resource itself, and errors return the HTTP status plus a flat RFC 9457 body `{ status, title, detail, code, constraints? }`. `code` is kebab-case (`not-found`, `payment-failed/low-amount`). DELETE → `204` |
| Tests / API docs | Tests are deferred to **M13**. No Swagger/OpenAPI for now |
| Deletes | Decided per aggregate and named in the model: a deleted user keeps its row (`deleted_at`), a session is revoked (`revoked_at`), used codes are removed. No blanket `deleted_at`, no bin tables |
| Header | Every source file starts with the header below |

```java
/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
```

## Stack
Java 25 (records, sealed interfaces, pattern-matching `switch`, virtual threads) · Spring Boot 4.1 (Web MVC, Validation, Security + OAuth2 Resource Server + OAuth2 Client, JPA/Hibernate 7, Actuator, Mail) · PostgreSQL via Supabase · Flyway · Spring Modulith (module declarations + reliable event publication, used as an outbox) · ShedLock (cluster-safe `@Scheduled`) · Lombok (entities only) · Jackson `@JsonView` · `RestClient`/`@HttpExchange` + Spring 7 `@Retryable` · Spotless · later: Micrometer/OpenTelemetry/Grafana (M12), Testcontainers/WireMock/ArchUnit (M13).

## Codebase shape

The rules are in [AGENTS.md](../AGENTS.md); this is the outline.

```
backend/
├── libs/common/                 # errors, validation, AggregateRoot, JpaAggregateRepository, token verification, Money
└── services/core/               # ru.vkurse.core
    ├── user/                    # one package per module
    │   ├── domain/              # User, Email, UserRegistered, UserRepository (interface)
    │   ├── application/         # UserService: load → call the aggregate → save
    │   ├── infrastructure/      # JpaUserRepository
    │   └── web/                 # UserController, UserResponse
    └── auth/
```
- **Aggregates** are JPA entities without setters, created by a factory (`User.register(…)`) and changed by methods that keep their rules (`session.rotate(…)`, `otp.verify(…)`). They raise domain events, published when they are saved.
- **Repositories** are interfaces in `domain` with named methods (`findByEmail`), implemented in `infrastructure`.
- **Application services** hold use cases and transactions, and take the caller's `userId` explicitly.
- **Controllers** return response records, never aggregates.
- Aggregates reference each other **by id**, with no lazy JPA relations.
- Tables use plural names (`courses`) in the service's own schema (`core`).

---

## Milestones (one module each, in order)

M1 and M2 are done and live in `services/core` (M1's foundation is now `libs/common`). Which service each later module lands in is decided in its milestone plan. The table keeps the original scope; where it names the old generic services (`UserOwnedEntityService`, `SchoolOwnedEntityService`), read "the aggregate and its application service check ownership".

| # | Module | Scope | Done when |
|---|---|---|---|
| M1 | **Database** (+ bootstrap) | Project skeleton, config, `utils` (errors, error handler, validation, query parsing), `database` (`BaseEntityService`, locks), `pricing` (`Money`), `V1__init.sql` | App starts on local Supabase, V1 is applied, health is UP, error bodies are correct |
| M2 | **Auth & User** | Own JWTs (access + rotating refresh), sessions in Postgres, email OTP, Google and Apple (`oauth2Login`, one-time exchange code), `@GetSession`, `@AdminOnly`, `/sessions`, `UserEntity` + `UserOwnedEntityService`, `GET/PATCH/DELETE /users/me` | OTP code arrives in Mailpit → token → `/users/me`; Google/Apple login; refresh replay kills the session |
| M3 | **School** | `SchoolEntity`, `SchoolMemberEntity` (OWNER/ADMIN/AUTHOR/CURATOR), invitations, `SchoolStudentEntity`, `SchoolOwnedEntityService`, role checks | Owner creates a school and invites an author, and the author sees it |
| M4 | **Storage** | `FileStorage` port, Supabase Storage adapter (signed upload/download URLs), `StoredFile`, per-school paths | Upload and download through signed URLs |
| M5 | **Course** | Course → Section → Lesson, sealed lesson content (JSONB), ordering, publishing, drip rules, stop lessons, catalog + search | Author publishes a course, and it appears in the catalog |
| M6 | **Payment** | `PaymentGateway` port, YooKassa adapter, encrypted per-school credentials, `Payment`, idempotent webhook inbox, refunds, `PaymentSucceeded`/`PaymentCanceled` events | Test-shop webhook is processed exactly once, even when replayed |
| M7 | **Commerce** | `Offer`, `Order` lifecycle, promo codes, checkout, `OrderPaid` event | Student pays with a test card, and the order becomes PAID |
| M8 | **Learning** | `Enrollment`, lesson unlock (drip/stop lessons), progress, homework + curator review, quizzes | Paid student progresses, and accepted homework unlocks the next lesson |
| M9 | **Notification & i18n** | Channels with fallback (email via Thymeleaf ru templates, Telegram), `MessageSource`, the OTP email moved from `auth` to a template, push tokens on sessions, event triggers | Emails show up in Mailpit |
| M10 | **Billing** | Plans with limits, school subscriptions, school balance + ledger, balance-first rule, autopay, renewal job | Renewal charges a school through autopay in the test shop |
| M11 | **Admin** | `/admin/**` for users, schools, plans, subscriptions, payments and balance adjustments; SQL-view reports | Admin can manage everything above |
| M12 | **Monitoring** | Actuator, Micrometer, OpenTelemetry → Grafana LGTM, JSON logs, Telegram error appender, admin insights | Traces and metrics visible in Grafana |
| M13 | **Tests & CI** | Testcontainers, WireMock, Modulith scenario tests, ArchUnit rules, GitHub Actions, image build | CI is green |
| M14 | **Hardening & deploy** | Bucket4j rate limiting, caching, self-hosted Supabase + API on a RU cloud VM, TLS, backups | Production is live |

**Optional later:** Kafka (Modulith `@Externalized`), Kubernetes/Helm, Spring AI with YandexGPT/GigaChat for homework pre-review, a Telegram bot for students.

## Money & payment principles
- **Balance changes are atomic.** The row lock (or atomic `UPDATE … SET balance = balance + ?`) and the ledger row go in one transaction.
- **Webhooks are idempotent.** An inbox table with a unique key ensures a retried notification is processed once.
- **YooKassa `Idempotence-Key` is deterministic**, derived from our own payment id, so a retried request never creates a second payment.
- **A webhook is never trusted on its own:** the payment is re-fetched from YooKassa before acting on it.
- Money is `BigDecimal` (`NUMERIC(19,2)`), never floating point.
- Roles come from the database, and there are no secrets in code.
- Cross-module events go through the Modulith event publication registry (an outbox), so a crash can't lose them.
- Subscriptions: autopay with a saved payment method, a 54-FZ receipt on every payment, and 3 failed renewals → expired.

## Risks & prerequisites
- Self-hosted Supabase means running upgrades and backups ourselves. The fallback is Yandex Managed PostgreSQL + Yandex Object Storage: auth is ours, so nothing else depends on Supabase.
- Owning auth means owning its security: token signing secret, refresh rotation, OTP brute-force limits. IP rate limiting arrives only in M14.
- Apple Sign In only redirects to registered HTTPS URLs, so testing it locally needs a tunnel (`cloudflared`).
- Nested entity classes: M1 confirms early that Hibernate entity naming and the Criteria setup work with them.
- Local setup needs the Supabase CLI (`brew install supabase/tap/supabase`) and a recent Docker.

## Process
1. Each milestone gets a detailed plan: `docs/milestones/NN-<module>.md`.
2. The plan is reviewed, then implemented.
3. The milestone's "done when" is checked by hand: `supabase start` → `./gradlew :services:core:bootRun` → `curl` with a token from `POST /auth/otp/login`, data in Supabase Studio (`:54323`), emails in Mailpit (`:54324`).
