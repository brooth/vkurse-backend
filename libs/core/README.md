# `libs/core`

> What every Vkurse service shares. A service depends on `project(":libs:core")` and gets the beans through Spring Boot auto-configuration (`CoreAutoConfiguration`); it does not component-scan `vkurse.core`.

| Package | Content |
|---|---|
| `domain` | `AggregateRoot` (id, timestamps, version, raised events) and `DomainEvent` |
| `persistence` | `JpaAggregateRepository` (base of every repository implementation), `LockService` (cluster locks) |
| `error` | `Errors` (the client and server exceptions) and `ErrorHandler` (RFC 9457 problem bodies with a `code`) |
| `validation` | `Validation`: constraint codes for error bodies, `@LanguageCode`, `@TimeZone` |
| `security` | `UserSession`, `@GetSession`, `@AdminOnly`, `JwtResourceServer` (verifies platform's tokens) |
| `web` | `Paging`: `$limit`, `$skip`, `$search` of list endpoints |
| `money` | `Money` (`BigDecimal` + currency, scale 2) |
| `notification` | `NotificationService`: email and Telegram, sent at once or stored and retried |

Beans a service gets: `ErrorHandler`, `Clock`, `LockProvider` + `LockService`, `@SchedulerLock` support, `NotificationService`. It must set `vkurse.node-id` and `vkurse.database.schema` (`CoreProperties`), and its schema needs the `shedlock` table. Notifications need more: see [below](#notifications).

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
