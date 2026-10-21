# `libs/core`

> What every Vkurse service shares. A service depends on `project(":libs:core")` and gets the beans through Spring Boot auto-configuration (`CoreAutoConfiguration`); it does not component-scan `vkurse.core`.

| Package | Content |
|---|---|
| `domain` | `AggregateRoot` (id, timestamps, version, raised events), `DomainEvent` and `UuidV7` (the time-ordered ids of aggregates and telemetry events) |
| `persistence` | `JpaAggregateRepository` (base of every repository implementation), `LockService` (cluster locks) |
| `error` | `Errors` (the client and server exceptions) and `ErrorHandler` (RFC 9457 problem bodies with a `code`) |
| `validation` | `Validation`: constraint codes for error bodies, `@LanguageCode`, `@TimeZone` |
| `security` | `UserSession`, `@GetSession`, `@AdminOnly`, `JwtResourceServer` (verifies platform's tokens) |
| `web` | `Paging`: `$limit`, `$skip`, `$search` of list endpoints |
| `money` | `Money` (`BigDecimal` + currency, scale 2) |
| `notification` | `NotificationService`: email and Telegram, sent at once or stored and retried |
| `telemetry` | `Telemetry` (what a node tells `monitoring`), `KafkaTelemetry`, the events, `LatencyBuckets` |
| `logging` | `HttpLogFilter` (every request served), `HttpClients` (every outside HTTP call), `JobLog` (every scheduled job), `LogMasker` (secrets out of the log) |

Beans a service gets: `ErrorHandler`, `Clock`, `LockProvider` + `LockService`, `@SchedulerLock` support, `NotificationService`, and the [logging](#logging) beans. It must set `vkurse.node-id` and `vkurse.database.schema` (`CoreProperties`), and its schema needs the `shedlock` table. Notifications need more: see [below](#notifications).

## Aggregates

```java
@Entity
@Table(name = "courses")
@Getter
public class Course extends AggregateRoot {
    private String title;

    protected Course() {}                       // for Hibernate

    private Course(String title, Instant now) {
        super(now);                             // assigns the UUIDv7 id and createdAt
        this.title = title;
    }

    public static Course draft(String title, Instant now) {
        var course = new Course(title, now);
        course.raise(new CourseDrafted(course.getId()));
        return course;
    }
}
```

`JpaAggregateRepository.save(aggregate)` inserts a new aggregate or stamps `updatedAt` on a changed one, flushes (so a constraint violation surfaces inside the call), and publishes the raised events in the same transaction. `findByIdForUpdate` takes a row lock.

## Notifications

```java
// The user is waiting for it: deliver now. Throws DeliveryFailedException; nothing is stored.
notifications.send(Message.of(
        Content.template("otp-code", locale, Map.of("code", code, "minutes", 7)),
        Target.email(address)));

// Everything else: store it. A job delivers it within 10 seconds and retries a failure.
notifications.submit(Message.of(
        Content.template("welcome", user.getLocale(), Map.of("name", user.getName())),
        Target.email(address), Target.telegram(chatId)));   // email, else Telegram
```

| | `send(message)` | `submit(message)` |
|---|---|---|
| Stored | No | A row in `notifications` |
| Failure | `DeliveryFailedException`, for the caller to handle | Retried after 1 min, 5 min, 15 min, 1 h; then `FAILED` and an error in the log |
| Transaction | Call it **outside** one: it talks to the mail server | Joins the caller's: the message is sent only if the changes are committed |
| Guarantee | The caller knows the outcome | At-least-once: a rare repeat is possible |
| Secrets | Fine | **Never**: the parameters are stored as JSON |

A `Message` is a list of attempts, tried in order until one is delivered. An attempt is a `Content` and the `Target`s to try it on, in order. `Message.of(content, targets…)` is one attempt; `.orElse(content, targets…)` adds one with other wording.

- `Content.text(title, body)` is sent as it is.
- `Content.template(name, locale, params)` is rendered by Thymeleaf from the service's resources:

| File | Used for |
|---|---|
| `templates/notification/{name}.html` | The email's HTML. A fragment: core puts it inside its `layout.html` (title, sign-off). Write values with `th:text`, which escapes them |
| `templates/notification/{name}.txt` | The Telegram message and the email's text part. Values are `[(${name})]` |
| `notification.{name}.subject` in `messages.properties` | The email subject, the title in the layout, the first line in Telegram |

Comments in a template are `<!--/* … */-->`, which Thymeleaf leaves out of the email.

What a service needs:
- The `notifications` table in its schema (copy it from platform's `V2__notifications.sql`), and no entity of its own named `Notification`.
- `vkurse.notification.mail-from`, `vkurse.notification.telegram.bot-token` (`NotificationProperties`) and `spring.mail.*`, with SMTP timeouts.
- `messages.properties` in Russian as the base bundle, and `spring.messages.fallback-to-system-locale: false`.
- `@EnableScheduling`, for the delivery job (every 10 seconds) and the cleanup job (daily; rows are kept 30 days).

A Telegram target is a chat id: a user who started our bot, or a group it was added to.

## Logging

A service logs these without writing a line for them:

| What | The line | Level |
|---|---|---|
| A request it serves (`HttpLogFilter`) | `POST /auth/otp 200 41ms - 127.0.0.1 @anonymous request={…} response={…}` | INFO; WARN for a 4xx; ERROR for a 5xx |
| An HTTP call it makes (`HttpClients`) | `yookassa POST https://api.yookassa.ru/v3/payments 200 312ms request={…} response={…}` | INFO; WARN for a 5xx or no answer |
| A notification (`NotificationService`) | `Sent otp-code by EMAIL to a@b.ru via 127.0.0.1:54325, subject "…", message id <…>`; submitted, delivered, failed with `attempt 2/5` and the next attempt's time | INFO; WARN for a failure; ERROR when it gives up |
| A scheduled job that threw (`JobLog`) | `Job StorageChargeJob.chargeToday failed after 812ms` and the stack trace | ERROR |

`/actuator/**` and CORS preflights are not logged. A stream (server-sent events) is logged when it opens, marked `(stream)`.

**What a line belongs to** is in the MDC, printed in brackets in the console and as fields in production's JSON:

| Key | Set | Value |
|---|---|---|
| `requestId` | For a request, and for the event listeners it starts on other threads | The caller's `X-Request-Id` header, else 12 random hex characters. It is sent back in `X-Request-Id` |
| `userId` | Once the caller is authenticated | The caller's id |
| `job` | While a `@Scheduled` method runs | `StorageChargeJob.chargeToday` |

A service's `application.yaml` prints them with `logging.pattern.correlation` (see platform's).

**Secrets** never reach the log: `LogMasker` replaces the value of a field in `vkurse.logging.masked-fields` (`password`, `token`, `accessToken`, `secretKey`, …) with `***`, in JSON bodies, form bodies and query strings, and the Telegram bot token in a URL. `code` is masked only in what is sent (a request body, a query string), because in a response it is an error code. Headers are never logged. A body longer than `vkurse.logging.max-body-length` (2000) characters is cut; a body that isn't text is only named (`<image/png, 48211 bytes>`). When a new request or response field holds a secret under a new name, add the name to `LogProperties`.

With `vkurse.logging.pretty-bodies: true` (platform's `dev` profile) a JSON body is logged indented, on lines of its own. Leave it off in production, where one event is one line.

**An outside HTTP API** is called with a client built by `HttpClients`, never a bare `RestClient.builder()`, so that it has timeouts and is logged:

```java
this.client = httpClients.builder("yookassa", CONNECT_TIMEOUT, READ_TIMEOUT).baseUrl(url).build();
```

Loggers that switch a part on and off (`LOGGING_LEVEL_VKURSE_JOB=debug` as an environment variable; that form works for lowercase names only, so platform has `VKURSE_LOG_SQL` for the last one):

| Logger | What | Default |
|---|---|---|
| `vkurse.http.body` | Request and response bodies in the two HTTP lines | INFO (on); `off` leaves the lines without bodies |
| `vkurse.notification.body` | The text of each sent message, at DEBUG | On in `dev`, off in `prod`: an OTP email holds the code |
| `vkurse.job` | `Job … finished in 12ms` for every run, at DEBUG | Off (INFO). A job says what it did itself |
| `org.hibernate.SQL` | SQL statements, at DEBUG | Off |

## Telemetry

Every service tells the `monitoring` service how it performs, with no code of its own: the filter, the outside-call
interceptor and the job advisor of [Logging](#logging) also call `Telemetry`. Events go to Kafka as JSON, keyed by the
node's id, and `monitoring` reads them ([M13](../../docs/milestones/13-monitoring.md)).

| Event | Topic | What it is |
|---|---|---|
| `HttpWindow` | `telemetry.http` | One minute of one route on this node: counts, errors, total and max time, a histogram (`LatencyBuckets`). Routes are controller templates (`/schools/{schoolId}/courses`); a request no controller answered is `UNMATCHED` |
| `HttpSample` | `telemetry.http` | One request kept whole: a 5xx, one slower than `slow-millis`, or a `sample-rate` share of the rest |
| `NodeSample` | `telemetry.node` | CPU, heap, GC, threads, connection pool, uptime, events lost; every `node-interval` |
| `JobRun`, `OutsideCall` | `telemetry.jobs` | A run of a `@Scheduled` job; a call made through `HttpClients` (host and path, no query, no headers) |

Rules it keeps:
- **It never slows or fails the service.** A request only adds to a counter and, if kept whole, to a bounded queue; a thread of its own sends batches. A full queue or an unreachable Kafka drops events and counts them (`NodeSample.droppedEvents`, and a WARN line every 5 minutes). Kafka is reached lazily, so the service starts without it.
- **No secrets or personal data:** no bodies, addresses, tokens or query strings; the user is an id.
- **The bean is always there.** `vkurse.telemetry.enabled` (default `false`) picks `KafkaTelemetry` or `NoopTelemetry`; callers never read the flag. With it on, `kafka.bootstrap-servers` is required.

| Property (`vkurse.telemetry`) | Default | |
|---|---|---|
| `enabled` | `false` | Send to Kafka |
| `kafka.bootstrap-servers` | none | Required when enabled (`localhost:54392` in `compose.dev.yaml`; MSK in production) |
| `slow-millis` | `500` | A request this slow is kept whole |
| `sample-rate` | `0.01` | The share of other requests kept whole |
| `queue-size` | `10000` | Events waiting to be sent |
| `node-interval` | `15s` | How often the node reports |

The service's name is `spring.application.name`; its version comes from `BuildProperties` (`springBoot { buildInfo() }` in the service's `build.gradle.kts`, else `unknown`).

## Verifying tokens in a service other than platform

```java
var decoder = JwtResourceServer.decoder(platformUrl + JwtResourceServer.JWKS_PATH, platformUrl, JwtResourceServer.ACCESS, clock);
http.oauth2ResourceServer(server -> server.jwt(jwt -> jwt.decoder(decoder)
        .jwtAuthenticationConverter(JwtResourceServer.sessionConverter())));
```

The principal is a `UserSession(userId, sessionId, role)` built from the claims, with the authority `ROLE_<role>`. Such a service makes no call to platform, so it trusts a token until it expires: a sign-out or a role change reaches it with the next access token (15 minutes at most).

## Guidelines
- Put here only what at least two services need, or what defines a contract between them (token claims, error bodies).
- No business rules of one service.
