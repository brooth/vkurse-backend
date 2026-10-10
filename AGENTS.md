# Vkurse backend: rules for AI agents and developers

> **Microservices on Spring Boot 4 + Hibernate + Postgres, with a rich domain model (DDD). Read this file before any task.**

Services live in [services/](services/) and share [libs/core](libs/core/README.md). The plan and milestones are in [docs/PLAN.md](docs/PLAN.md).

## Critical rules

### 1. Copyright header (REQUIRED)
Every source file starts with:
```java
/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
```
`./gradlew spotlessApply` adds it to Java files automatically ([config/license-header.txt](config/license-header.txt)). Add it to SQL migrations by hand.

### 2. Logging
Never use `System.out`/`System.err`. Use SLF4J:
```java
private static final Logger log = LoggerFactory.getLogger(MyService.class);
```
Log errors with a plain message (no trailing colon) and the exception **last**, so the stack trace is kept:
```java
log.error("Failed to renew subscription {}", subscriptionId, e);   // ✅
log.error("Failed to renew subscription: " + e.getMessage());       // ❌ loses the stack trace
```

### 3. Injection
- **Constructor injection** with `final` fields. No `@Autowired` fields in services or controllers.
- The one exception is `JpaAggregateRepository`, which field-injects its `EntityManager`, event publisher and `Clock`, so a repository only calls `super(Course.class)`.

### 4. Field naming
- Plural for repositories and services of one aggregate: `courses`, `orders`, `users`.
- Singular for utilities and ports: `clock`, `locks`, `tokens`, `mailer`.

### 5. No conditional wiring
Every bean always loads; don't use `@ConditionalOnProperty` for features. Required settings go in a `@ConfigurationProperties @Validated` record (see [PlatformProperties](services/platform/src/main/java/vkurse/platform/PlatformProperties.java)), so a missing value stops startup.
Document each property with a `@param` tag in the record's javadoc: the configuration processor turns it into the hint the IDE shows in `application.yaml`.

### 6. RU region
The project launches in Russia: defaults are `ru` and `RUB` only. Never default to `en` or `USD`.

### 7. Money
Money is `vkurse.core.money.Money` (`BigDecimal` + currency, scale 2), never `double`. Fields of type `Money` are named `amount` or `*Amount` (`priceAmount`, `balanceAmount`).

### 8. Balance-only charges (billing)
Vkurse charges a school only from its balance, and only through `BillingAccount`, which writes the ledger row with the change. What the balance can't cover is a debt (a balance below zero): never charge a card ourselves. Money comes in only by a top-up the school makes.

### 9. Types
No raw types or unchecked casts without a one-line comment explaining why. Packages are `@NullMarked` (JSpecify): everything is non-null unless it is annotated `@Nullable`.

### 10. Tests
There are no automated tests until milestone M13. Verify changes by hand (see "Running locally").

### 11. Notifications
Send email and Telegram only through `NotificationService` from `libs/core` ([how](libs/core/README.md#notifications)); never use `JavaMailSender` directly.
- `send(message)` when the user is waiting for it (a sign-in code): it delivers now and throws if it can't. Call it outside a database transaction.
- `submit(message)` for everything else: it is stored with your changes and delivered by a job, with retries.
- **Never `submit` a secret** (a code, a token): a submitted message is stored as it is.
- Texts are Russian templates in the service's `templates/notification/`, with the subject in `messages.properties`. Write values with `th:text`, never `th:utext`.

### 12. Docs
Keep the service README and the module READMEs up to date in every change.

Also keep [docs/nest_js_migration.md](docs/nest_js_migration.md) current. When your work reveals a new difference between NestJS and Vkurse (a mapping, pitfall or source bug, or a convention that changed), add it to the guide **in the same change**.

---

## Services

- A service owns its data: one Postgres schema, its own Flyway migrations. No service reads another service's tables.
- `platform` is the only service that issues tokens. Every other service verifies access tokens with `JwtResourceServer` from `libs/core` (platform's keys at `/.well-known/jwks.json`) and makes no call to platform per request.
- A token is trusted until it expires (15 minutes). Don't build anything that needs a sign-out or a role change to reach another service faster than that.
- `libs/core` holds only what at least two services need, or what is a contract between services. No business rules. A package there is flat (no layers), and public only where services use it.

## Module structure

A service is split into modules, one package each under the service's root (`vkurse.platform.user`). A module has four layers:

```
vkurse/platform/course/
├── domain/
│   ├── Course.java             # aggregate (@Entity): state + the rules that change it
│   ├── CourseTitle.java        # value object
│   ├── CoursePublished.java    # domain event (record)
│   └── CourseRepository.java   # interface: what the domain needs from storage
├── application/
│   └── CourseService.java      # use cases: load → call the aggregate → save. Transactions
├── infrastructure/
│   └── JpaCourseRepository.java  # extends JpaAggregateRepository, implements CourseRepository (package-private)
├── web/
│   ├── CourseController.java   # REST (package-private)
│   └── CourseResponse.java     # request/response records
├── package-info.java           # @NullMarked + @ApplicationModule
└── README.md
```

| Layer | May use |
|---|---|
| `domain` | `libs/core`, other modules' `domain`. No Spring beans, no injection, no `EntityManager`, no HTTP types |
| `application` | its `domain`, other modules' `application` |
| `infrastructure` | everything in its module |
| `web` | `application`, and `domain` types to read from |

`domain`, `application` and `web` of a module that others use are `@NamedInterface`s; `infrastructure` is never used from outside.

### Where a rule goes
The model is rich: **a rule lives in `domain`, in the type that owns the data it decides on.** An application service has no rules of its own: it loads, asks the domain, and saves.

| The rule is about | It goes in |
|---|---|
| One aggregate's state: expiry, limits, transitions, defaults | A method or the factory of that aggregate: `session.rotate(…)`, `User.register(…)` |
| A value on its own: its format, when it may be used | A value object: `Email`, `CallbackUrl`, `ProviderAccount.verifiedEmail()` |
| Other rows of the same kind: a resend limit, a quota | The service loads them with a named repository method, and the aggregate decides: `latest.ensureResendAllowed(now)` |
| Two aggregates, or an aggregate and a value | The service loads both and passes one to the other's method or factory: `LinkedAccount.link(userId, account, now)` |
| Something a port computes in memory: a hash | The aggregate method takes the port as a parameter: `challenge.verify(code, hasher, now)`. Never a port that does I/O |

What an application service keeps:
- the transaction, loading and saving, and the order of the steps;
- the ownership check (`belongsTo(userId)`), and "not found";
- calls to ports that do I/O (mail, tokens, payments), and what to do when they fail;
- technical handling: a retry after a unique-index race, a log line;
- turning an aggregate's answer into an exception, when the state must be saved before throwing (`Session.Rotation`).

Move the code into `domain` when you see any of these in a service or a controller:
- It reads a getter, compares it, and then throws or calls a method. Call one method that does both.
- It does arithmetic with a domain constant (`now.minus(OtpChallenge.RESEND_AFTER)`).
- It builds something the aggregate stores: a hash format, a default name, a trimmed value.
- A javadoc says "the caller checks …". Make the method check, or return only what may be used.
- A record that describes a domain thing is declared in `application` or `web`.

### Aggregates
- An aggregate extends `AggregateRoot` and is a JPA `@Entity` with a plural table name: `@Table(name = "courses")`. Class names have **no** `Entity` suffix: `Course`, `User`, `Session`.
- **No setters.** Lombok `@Getter` only (Lombok is for aggregates only). State changes through methods named for what happens: `course.publish(now)`, `session.revoke(now)`, `user.changeProfile(…)`.
- **Created by a static factory** named for the event (`User.register(…)`, `Session.start(…)`), which calls a private constructor that calls `super(now)`. Add a `protected` no-arg constructor for Hibernate.
- **Rules live in the aggregate**, not in a service: expiry, attempt limits, state transitions, what may change (see [Where a rule goes](#where-a-rule-goes)).
- **Time is a parameter** (`Instant now`): an aggregate never calls a clock. The application service passes `clock.instant()`.
- A rule the client broke throws a `ClientException` from `libs/core` (`BadRequestException`, or the module's own in `{Module}Errors`). A programming mistake throws `IllegalArgumentException`/`IllegalStateException`.
- An aggregate raises events with `raise(new CoursePublished(getId()))`. They are published when it is saved, in the same transaction.
- Aggregates reference each other **by id** (a `UUID` column). No `@ManyToOne`/`@OneToMany` between aggregates.
- Ids are `UUID` (v7), assigned when the aggregate is created. Never assign one by hand.

```java
@Entity
@Table(name = "courses")
@Getter
public class Course extends AggregateRoot {
    private String title;

    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;

    private @Nullable Instant publishedAt;

    protected Course() {}

    private Course(String title, Instant now) {
        super(now);
        this.title = title;
    }

    public static Course draft(String title, Instant now) {
        return new Course(title, now);
    }

    public void publish(Instant now) {
        if (status != Status.DRAFT) {
            throw new ConflictException("Only a draft can be published");
        }
        status = Status.PUBLISHED;
        publishedAt = now;
        raise(new CoursePublished(getId()));
    }

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
}
```

### Value objects
A value with rules of its own is a record that can't exist in an invalid state: `Email`, `CallbackUrl`, `Money`. It validates in its constructor or factory. Stored with a nested `AttributeConverter` (one column) or as an `@Embeddable` record (several columns, prefixed: `price_value`, `price_currency`).

### Repositories
- The **interface** is in `domain` and speaks the domain's language: `findByEmail(email)`, `findLiveByUser(userId, now)`. No generic query objects, no field-name strings.
- The **implementation** is in `infrastructure`, extends `JpaAggregateRepository<A>` (from `libs/core`) and is package-private. It is the only code that uses the `EntityManager`; queries are JPQL. Don't write Spring Data repositories (`JpaRepository`).
- **Call `save(aggregate)` after every change.** It inserts or stamps `updatedAt`, flushes, and publishes the aggregate's events.
- When two requests may change the same aggregate and the outcome matters (refresh tokens, attempt counters, money), load it with `findByIdForUpdate` and let the aggregate decide. Don't hide a rule in a conditional `UPDATE`.
- Bulk statements are for cleanup of rows nobody holds (`deleteExpired(now)`).

### Application services
- A use case is a short method: load, call the aggregate, save. `@Transactional` goes here. It makes no business decision ([Where a rule goes](#where-a-rule-goes)).
- **Ownership is explicit.** A call on behalf of a user takes the caller's `userId` and reaches only their data: `sessions.revoke(session.userId(), id)`. A missing or foreign row is the same `NotFoundException`.
- Outside systems (mail, tokens, payment providers) are **ports**: an interface in `domain`, an implementation in `infrastructure`. Don't call a slow outside system inside a database transaction.
- Scheduled jobs and event listeners are application-layer classes.

### Deleting
Decide per aggregate what "deleted" means, and name it: `User.delete` keeps the row with `deletedAt`; `Session.revoke` sets `revokedAt`; a used OTP challenge is removed. There is no blanket `deleted_at`. A table with a soft state uses partial unique indexes (`WHERE deleted_at IS NULL`).

### Events
- Inside a service, cross-module side effects are events, not calls back into the other module. `@ApplicationModuleListener` for work that may run after commit (stored in `event_publication`, re-delivered on failure); a plain `@EventListener` only when it must be in the same transaction.
- Events are records implementing `DomainEvent`, in the publishing module's `domain`, carrying ids, not aggregates.

### Controllers
- There is no global path prefix. Paths are plural nouns (`/schools/{schoolId}/courses`).
- Every `@RequestBody` has `@Valid`.
- **Authenticated by default.** Take the caller as `@GetSession UserSession session` and pass `session.userId()` to the application service. Public routes are listed only in the service's `SecurityConfig`. Admin-only methods get `@AdminOnly`.
- **Never return an aggregate.** Map it to a response record (`CourseResponse.of(course)`). Request records carry Jakarta validation; in a partial update, a `null` component means "leave unchanged".
- Use `@PatchMapping` (never `@PutMapping`) for partial updates, and return the updated resource.
- GET/POST/PATCH return the resource itself, with no `{ value }` wrapper. DELETE returns `204 No Content`.
- List endpoints accept `@RequestParam Map<String, String> params` + `Paging.parse(params)`. Filters and sort come from the server.

```java
@RestController
@RequestMapping("/courses")
class CourseController {
    private final CourseService courses;

    CourseController(CourseService courses) {
        this.courses = courses;
    }

    @PostMapping("/{id}/publish")
    CourseResponse publish(@GetSession UserSession session, @PathVariable UUID id) {
        return CourseResponse.of(courses.publish(session.userId(), id));
    }
}
```

### Errors
Throw the exceptions from `vkurse.core.error.Errors`. `ErrorHandler` turns them into RFC 9457 bodies with a `code`:

| Class | Status | `code` |
|---|---|---|
| `BadRequestException` | 400 | `bad-request` |
| `ValidationException` | 400 | `validation-error` (+ `constraints`) |
| `UnauthorizedException` | 401 | `unauthorized` (`auth` also sends `invalid-session`: refresh the token, and `relogin-required`: log in again) |
| `ForbiddenException` | 403 | `forbidden` |
| `NotFoundException` | 404 | `not-found` |
| `ConflictException` | 409 | `conflict` |
| `ClientException(status, code, message)` | any | custom, e.g. `payment-failed/low-amount` |
| `ServerException` / any other exception | 500 | `server-error` (details only in the log) |

A module's own errors are subclasses in `domain/{Module}Errors.java` (`AuthErrors.OtpExpiredException`).

### Migrations
- Flyway owns the schema. Files are `src/main/resources/db/migration/V{N}__{description}.sql` in the service. Seed data goes in migrations too.
- Never edit a migration that has already been applied. Add a new one.
- Every table goes in the service's schema (Flyway's default schema), so names are unqualified.
- Hibernate only validates (`ddl-auto: validate`), so each new aggregate needs its migration in the same change.
- Every aggregate table has `id UUID`, `created_at`, `updated_at`, `version BIGINT NOT NULL`.

---

## NestJS migration rules

Some Vkurse modules are ported from a NestJS + Firestore codebase. **Before porting any NestJS code, read [docs/nest_js_migration.md](docs/nest_js_migration.md) in full.**

The traps that matter most:
- **Port behavior, not code.** The source is anemic CRUD with hooks; find the rules in its services and hooks and give them to an aggregate. Never copy the source project's name, URLs, headers or secrets.
- **Check ownership on updates and deletes, not only finds.** The source scoped only finds.
- **Money is `Money`/`BigDecimal`, and money writes are atomic** (one transaction, row lock). Webhooks are idempotent (an inbox table with a unique key).
- **Spring cron has 6 fields** (seconds first): Nest's `0 * * * *` becomes `0 0 * * * *`.
- **Never put a secret where the client can read it.** A JWT is signed, not encrypted. Never put tokens in redirect URLs, and only redirect to allowlisted origins.

---

## Running locally
```bash
docker compose -f compose.dev.yaml up -d          # Postgres :54322, Supabase Storage :54321, Mailpit :54324
cp services/platform/.env.example services/platform/.env  # first time only; fill in VKURSE_AUTH_PRIVATE_KEY
./gradlew :services:platform:bootRun                  # http://localhost:8080
./gradlew spotlessApply                           # format + headers (run before finishing a task)
```
Gradle needs a JDK 17+ to start (`JAVA_HOME`); it downloads JDK 25 for the build itself.

Checks: `curl localhost:8080/actuator/health`, data with `docker exec -it vkurse-db psql -U postgres` (schema `platform`), emails in Mailpit (`http://127.0.0.1:54324`), files with the storage API (see the [storage module](services/platform/src/main/java/vkurse/platform/storage/README.md)). The environment variables are listed in each service's README.
