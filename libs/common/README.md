# `libs/common`

> What every VKurse service shares. A service depends on `project(":libs:common")` and gets the beans through Spring Boot auto-configuration (`CommonAutoConfiguration`); it does not component-scan `ru.vkurse.common`.

| Package | Content |
|---|---|
| `domain` | `AggregateRoot` (id, timestamps, version, raised events) and `DomainEvent` |
| `persistence` | `JpaAggregateRepository` (base of every repository implementation), `LockService` (cluster locks) |
| `error` | `Errors` (the client and server exceptions) and `ErrorHandler` (RFC 9457 problem bodies with a `code`) |
| `validation` | `Validation`: constraint codes for error bodies, `@LanguageCode`, `@TimeZone` |
| `security` | `UserSession`, `@GetSession`, `@AdminOnly`, `JwtResourceServer` (verifies core's tokens) |
| `web` | `Paging`: `$limit`, `$skip`, `$search` of list endpoints |
| `money` | `Money` (`BigDecimal` + currency, scale 2) |

Beans a service gets: `ErrorHandler`, `Clock`, `LockProvider` + `LockService`, `@SchedulerLock` support. It must set `vkurse.node-id` and `vkurse.database.schema` (`CommonProperties`), and its schema needs the `shedlock` table.

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

## Verifying tokens in a service other than core

```java
var decoder = JwtResourceServer.decoder(coreUrl + JwtResourceServer.JWKS_PATH, coreUrl, JwtResourceServer.ACCESS, clock);
http.oauth2ResourceServer(server -> server.jwt(jwt -> jwt.decoder(decoder)
        .jwtAuthenticationConverter(JwtResourceServer.sessionConverter())));
```

The principal is a `UserSession(userId, sessionId, role)` built from the claims, with the authority `ROLE_<role>`. Such a service makes no call to core, so it trusts a token until it expires: a sign-out or a role change reaches it with the next access token (15 minutes at most).

## Guidelines
- Put here only what at least two services need, or what defines a contract between them (token claims, error bodies).
- No business rules of one service.
