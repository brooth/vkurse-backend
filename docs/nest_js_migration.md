# Porting NestJS code to VKurse

How to bring a module from a NestJS + Firestore codebase (the source style this project grew out of) into VKurse (Spring Boot + Hibernate + Postgres). Read [AGENTS.md](../AGENTS.md) first; this guide covers only what differs when the *source* is NestJS.

**Port behavior, not code.** Understand what the source module does, then write it the VKurse way. A line-by-line translation of TypeScript gives Java that fights the framework, and carries over the source's bugs.

**The source is anemic; VKurse is not.** A source entity is a bag of fields, and its rules are spread over the service and its hooks, on top of a generic CRUD base (`findOne`, `updateById`, `preHook`). VKurse has no such base. Find the rules, and give them to an aggregate as methods (`session.rotate(…)`, `otp.verify(…)`); the service that remains only loads, calls and saves.

---

## 1. Ground rules

- **No source branding.** Never copy the source project's name, URLs, copyright headers, API keys, secrets or `.env` values. Every file gets the VKurse header.
- **Don't port known bugs** (section 15). If the source has a `TODO` stub (for example a `reserve()` that does nothing), implement it properly or leave it out and say so. Never port a stub silently.
- **Don't port Firestore workarounds.** Search tokens, `_bin` collections, index JSON files, reads-before-writes ordering and `watch` all exist only because of Firestore (section 11).
- **Keep the business rules.** RU region, the balance-first payment rule and the money naming carry over unchanged ([AGENTS.md](../AGENTS.md)).
- **Record every deviation** from the source in the milestone doc (`docs/milestones/NN-*.md`).

## 2. Files and modules

| NestJS source | VKurse |
|---|---|
| `{module}/{module}.entity.ts` | `domain/{Name}.java`: one aggregate per file, no `Entity` suffix (`Course`). Its enums are nested in it |
| Create/Update DTO classes | Request records next to the controller in `web/` (`CreateCourseRequest`), or nested in it when used once |
| `{module}.service.ts` | Rules → methods of the aggregate. What is left → `application/{Module}Service.java` (use cases, transactions) |
| Firestore access inside the service | `domain/{Name}Repository.java` (interface) + `infrastructure/Jpa{Name}Repository.java` |
| `{module}.controller.ts` | `web/{Module}Controller.java` (package-private) |
| `{module}.module.ts` (`imports`/`providers`/`exports`) | `package-info.java` with `@ApplicationModule`; `@NamedInterface` on the layers other modules use. Beans are found by component scan |
| `{module}.types.ts` | Event records and value objects in `domain/`; the module's client errors in `domain/{Module}Errors.java` |
| `{module}.utils.ts` | A value object or an aggregate method, if it holds a rule; otherwise a helper where it is used |
| Client for an outside system (mail, payments) | A port interface in `domain/`, its implementation in `infrastructure/` |
| `README.md` | `README.md` in the module package, updated in the same change |
| `*.spec.ts` | Not now: tests are milestone M13 |
| `database/migrations/{N}. desc.ts` | `src/main/resources/db/migration/V{N}__desc.sql` in the service (SQL, not code) |
| `utils/index.ts` barrel | No barrels: import the type directly (`import ru.vkurse.common.error.Errors.NotFoundException;`) |
| Path aliases (`database/…`, `utils`) | Java packages (`ru.vkurse.common.error`) |
| Manual import-order rule | Spotless orders imports (`./gradlew spotlessApply`) |
| Dynamic modules (`forRoot`, `register`) | A `@Configuration` class with `@Bean` methods |

## 3. Dependency injection

| NestJS | VKurse |
|---|---|
| `@Injectable()` service | `@Service` (`@Component` for non-services) |
| `@Inject() private readonly users: UserService;` | Constructor injection: `private final UserService users;` set in the constructor |
| `@Inject('FIRESTORE')` / string tokens | Typed beans; add `@Qualifier` only if two beans share a type |
| `forwardRef(() => X)` / circular imports | Redesign. A cycle is a design smell: move the call behind an event (section 9), or move the logic to the module that owns the data. `@Lazy` on one constructor parameter is a last resort, with a comment |
| `ConfigService.get('X')`, `process.env.X` | A `@ConfigurationProperties @Validated` record per module; never read `System.getenv` in business code |
| `MANDATORY_ENVS` check that exits on startup | `@NotBlank`/`@NotNull` on the properties record |
| `if (env) imports.push(Module)` | Forbidden in both: every bean always loads |

The one exception to constructor injection is `JpaAggregateRepository`, which field-injects its `EntityManager`, event publisher and `Clock`. So a repository only calls `super(Course.class)`.

## 4. Entities and payloads

### 4.1 Base classes
| Source | VKurse |
|---|---|
| `Entity` (`id: string`, `createdAt`, `updatedAt`, `deletedAt`) | `AggregateRoot` (`UUID id` v7, `createdAt`, `updatedAt`, `version`). No `deletedAt`: see "Deleting" in section 5 |
| `UserEntity` base (`createdBy`, `updatedBy`, `deletedBy`) | No base class. An aggregate that belongs to a user has an explicit `userId` (or `ownerId`), set by its factory |
| `User`, `Course` entity classes | `User`, `Course`: aggregates, with no `Entity` suffix and no setters |
| Firestore auto ids (strings) | `AggregateRoot` assigns a UUIDv7 when the aggregate is created. Never assign an id by hand |
| `super('items')` collection name | `@Table(name = "items")` on the aggregate + `super(Item.class)` in the repository |
| `typeOf(doc)` for polymorphic docs | JPA inheritance (`@Inheritance(SINGLE_TABLE)` + discriminator), or a sealed type stored as JSONB for content variants |
| A string with a format (`email`, a URL that must be allowlisted) | A value object record that validates itself (`Email`, `CallbackUrl`), stored with a nested `AttributeConverter` |

### 4.2 Field types
| Source (TS / Firestore) | VKurse (Java / Postgres) |
|---|---|
| `string` | `String` + `VARCHAR`/`TEXT` |
| `number` (count) | `int`/`long` + `INT`/`BIGINT` |
| `number` (money, credits) | `Money` (`BigDecimal`, scale 2), or `BigDecimal` for non-money decimals; **never `double`** |
| `Amount { value, currency }` (in `pricing/`) | `ru.vkurse.pricing.Money` record, `@Embedded` → `<field>_value`, `<field>_currency` columns |
| `Date` | `Instant` + `TIMESTAMPTZ`; `LocalDate` for dates without time |
| `moment()`, `new Date()` | In an application service: `clock.instant()` (the injected `Clock`), never `Instant.now()`. In an aggregate: an `Instant now` parameter |
| IANA timezone string | `ZoneId` (a `String` column, converted in code) |
| `const xs = [...] as const; type X = typeof xs[number]` | A Java `enum` with `@Enumerated(EnumType.STRING)` + `VARCHAR`. Constants are `UPPER_SNAKE` (`past_due` → `PAST_DUE`), and JSON and the database carry the constant name |
| Nested object that is filtered or sorted on | `@Embedded` record, giving prefixed columns (`phone` → `phone_code`, `phone_number`) |
| Nested object that is only stored | JSONB: `@JdbcTypeCode(SqlTypes.JSON)` + a `JSONB` column |
| Array of objects | JSONB if it is always read and written whole; otherwise a child table |
| Reference to another doc (`userId: string`) | `UUID userId` + `REFERENCES` in SQL. No `@ManyToOne`/`@OneToMany` |
| Denormalized snapshot (`user: UserShort` inside a transaction) | Keep it as an embedded snapshot **only** when it is a historical record (transactions, receipts). Otherwise store the id and load the user in the service |
| Optional field (`?`) | A nullable column, and `@Nullable` on the Java field |

### 4.3 Validation (class-validator → Jakarta Validation)
Format validation lives on the **request records** in `web/`, and gives the client the `constraints` map. The aggregate guards its own rules as well (a method that would break one throws), so they hold for callers that are not controllers.

| class-validator | Jakarta / Java |
|---|---|
| `@IsString()`, `@IsNumber()`, `@IsBoolean()` | The Java type itself; a wrong JSON type is rejected with code `invalidType` |
| `@IsOptional()` | `@Nullable` on the record component |
| `@IsDefined()` / required | `@NotNull` → code `isDefined` |
| `@IsNotEmpty()` | `@NotBlank` (strings) / `@NotEmpty` (collections) → `isNotEmpty` |
| `@IsEmail()` | `@Email` |
| `@IsIn(values)` | A Java enum; an unknown value → `isIn {values}` |
| `@MinLength` / `@MaxLength` | `@Size(min, max)` → `minLength` / `maxLength {count}` |
| `@Min` / `@Max` | `@Min` / `@Max` → `minValue` / `maxValue {value}` |
| `@Matches(regex)` | `@Pattern(regexp)` → `invalidFormat {regexp}` |
| `@ValidateNested()` + `@Type(() => X)` | `@Valid` + a nested record type |
| `@IsDate()` + `@Type(() => Date)` | An `Instant`/`LocalDate` component (Jackson parses ISO-8601) |
| `@ValidateIf(...)` | A class-level constraint in `Validation.java`, or a check in the service that throws `ValidationException` |
| Custom `IsLanguageCode` | `@LanguageCode` (exists) |
| Custom `IsBiggerThan`, `IsAfterThan`, `IsExtPhone` | Add them to `Validation.java` when a module first needs them (a class-level constraint for cross-field checks) |
| `ValidationPipe({ groups: ['admin'] })` | A separate request record for the admin controller, instead of validation groups |

The error vocabulary (`isDefined`, `isNotEmpty`, `minLength`, `isIn`, …) deliberately matches the source's, so existing frontends keep working. The body shape is different, though: see section 7.

### 4.4 PickType / PartialType
Java can't derive types from other types, and a request is not a patch of fields anyway. Write a record per use case:
- `CreateXxx extends PickType(Xxx, [...])` → a request record with only the fields a client may set, passed to the aggregate's factory (`Course.draft(title, now)`), which sets the server-side defaults (`status`, owner).
- `UpdateXxx extends PartialType(CreateXxx)` → prefer a method per intent (`course.rename(title)`, `course.publish(now)`). A real "edit the form" update is one method taking the editable fields, where **`null` means "leave unchanged"** (`user.changeProfile(name, locale, timezone)`).
- Admin-only settable fields → a separate request record and a separate aggregate method, used only by the admin controller.

### 4.5 Serialization (class-transformer → response records)
An aggregate is never serialized. A controller maps it to a response record (`CourseResponse.of(course)`), which lists exactly the fields that audience sees.

| class-transformer | VKurse |
|---|---|
| `@Expose()` on every field | A component of the response record |
| `excludeExtraneousValues: true` | The default: unknown JSON properties are ignored, and request records only declare the allowed fields |
| `@Expose({ groups: ['admin'] })`, `['owner']` | A separate response record per audience (`CourseAdminResponse`) |
| A field without `@Expose` (e.g. `password`), `@Exclude()` | Simply not in the response record |
| `@Transform` masking (e.g. a PIN shown as `****` to non-owners) | Done in the response record's factory |
| `plainToInstance` / `instanceToPlain` | Not needed: Jackson and Hibernate convert |
| Unset fields left out (`exposeUnsetFields: false`) | `spring.jackson.default-property-inclusion: non_null` (already on) |

## 5. Services and data access

There is no generic CRUD service and no query object. Each source call becomes either a named repository method or an aggregate method.

### 5.1 Method mapping
| Source | VKurse |
|---|---|
| `findById_({ id })` / `findOne_({ query })` | `repository.findById(id)` / a named finder (`findByEmail(email)`) → `Optional<A>` (never `null`) |
| `findMany({ session, query })` | A named finder with the scope in its arguments: `findLiveByUser(userId, now, limit, offset)` |
| `insertOne({ session, data })` | `repository.save(Xxx.factory(…))` |
| `updateById({ session, id, data })` | Load, call a method on the aggregate, `repository.save(aggregate)` |
| `updateMany_` | Load the aggregates and call the method on each, when there are few; a bulk JPQL statement in the repository only for maintenance |
| `replaceOne_` / `findOneAndReplace_` | Removed |
| `deleteOne_` / `deleteById_` (soft delete to the bin) | What the domain means by it: `user.delete(now)`, `session.revoke(now)`, or `repository.remove(aggregate)` |
| `dropOne_` / `dropMany_` | `repository.remove(aggregate)`; a named bulk method for cleanup (`deleteExpired(now)`) |
| `watch_` | Removed: use events (section 9), or SSE when a client needs push |
| `findOneOrCreate` | Find, else create in its own transaction, backed by a **unique constraint**: on `DataIntegrityViolationException`, find again (two requests can race). See `UserService.findOrRegister` |
| `callInTransaction(async (tx) => …)` | A `@Transactional` application-service method. Every repository call inside joins it; there is no `tx` to pass (forgetting it was a source bug) |
| `LockService.acquireLock` / `releaseLock` | `locks.withLock(name, ttl, () -> …)`, or `@SchedulerLock` on scheduled jobs |
| `FieldValue.increment(n)` | Load with `findByIdForUpdate` (row lock) and call the aggregate; never read-modify-write without the lock |
| `FieldValue.arrayUnion` / `arrayRemove` | A child table, or a JSONB value changed by an aggregate method |
| The `_` suffix (system call, no session) | Gone. A use case that acts for a user takes `userId`; one that acts for the system doesn't have that parameter |

### 5.2 Query mapping
Queries are JPQL in the repository implementation, one method per question the domain asks.

| Source `EntityQuery` | VKurse |
|---|---|
| `{ match: { status: 'active' } }` | `WHERE c.status = :status` behind `findActive…` |
| `'=='`, `'!='`, `'<'`, `'in'`, `'not-in'` | JPQL operators |
| `'array-contains'` | A child table (then `IN`/join), or native SQL on JSONB |
| `sort`, `limit` / `offset` | `ORDER BY` in the query; `setMaxResults` / `setFirstResult` from method arguments |
| `search` + `_searchTokens` | `LOWER(c.title) LIKE :pattern` in a named method; no tokens are stored |
| Dot paths `'user.id'` | JPQL paths into `@Embedded` fields; JSONB paths need native SQL |
| `encodeEntityQuery(queryString)` | `Paging.parse(params)` (`$limit`, `$skip`, `$search` only), passed to the application service |

A value object with a converter can be a query parameter as it is (`.setParameter("email", email)`).

### 5.3 Hooks
The source's hooks are where its rules hide. None of them survives as a hook.

| Source | VKurse |
|---|---|
| `@PreHook('insert')` setting defaults, owner, timestamps | The aggregate's factory (defaults, owner); `AggregateRoot` and `JpaAggregateRepository.save` (timestamps) |
| `@PreHook('update')` checking or adjusting the change | The aggregate method that makes the change |
| `@PreHook('find')` adding `createdBy = session.user` | The application service passes the caller's `userId` to a finder, or loads by id and checks `aggregate.belongsTo(userId)`. A foreign row is a `NotFoundException`, like a missing one |
| `@PostHook('insert')` / `postHook` emitting events | `raise(new XxxHappened(getId()))` in the aggregate; published on `save` |
| Post-hooks that trim the returned object for the caller | A response record (section 4.5) |
| Extended contexts (`BusinessFindContext` with a required `business`) | Explicit arguments (`schoolId`) to the use case and the finder |
| `postHook` on insert calling another module | An event the other module listens to (section 9). This avoids circular dependencies and survives crashes |

⚠️ **Security difference.** In the source, user scoping was added only to `find`/`watch` hooks, so updates and deletes by id were **not** scoped. In VKurse every use case for a user takes the `userId` and checks ownership before it calls the aggregate: there is no unscoped "update by id" to reach for.

### 5.4 Deleting
The source soft-deleted everything into `_bin` collections. In VKurse each aggregate says what ending means: a deleted `User` keeps its row with `deletedAt` (and its repository never returns it); a `Session` is revoked (`revokedAt`); a used `OtpChallenge` is removed. Pick per aggregate, name the method for it, and use a partial unique index (`WHERE deleted_at IS NULL`) when a soft-deleted row must not block a new one.

## 6. Controllers

| NestJS | Spring |
|---|---|
| `@Controller('items')` | `@RestController @RequestMapping("/items")`, class package-private |
| `@UseGuards(BearerGuard)` | Nothing: every route is authenticated by default. Public routes are listed in the service's `SecurityConfig` |
| `@GetSession() session: UserSession` | `@GetSession UserSession session` (a `@AuthenticationPrincipal` meta-annotation in `libs/common`, the same in every service) |
| `session.user.role`, `session.user.name` | `UserSession` carries `userId()`, `sessionId()` and `role()`, read from the access token. Load the user with `UserService` when you need more (in `core`) |
| `@UseGuards(BearerGuard, AdminGuard)` | `@AdminOnly` (`@PreAuthorize("hasRole('ADMIN')")`) |
| `@UsePipes(new ValidationPipe())` + `@Body() data` | `@Valid @RequestBody CreateXxxRequest data` |
| `@Param('id') id: string` | `@PathVariable UUID id` (an invalid UUID becomes a 400 automatically) |
| `@Query() query: string` + `encodeQuery` | `@RequestParam Map<String, String> params` + `Paging.parse(params)` |
| `@Req() req` for a raw webhook body | `@RequestBody String` or a typed record; always verify the payload by re-fetching it from the provider |
| `@Get` / `@Post` / `@Patch` / `@Delete` | `@GetMapping` / `@PostMapping` / `@PatchMapping` / `@DeleteMapping`. There is no `@PutMapping` for partial updates |
| `@HttpCode(200)` | The default for GET/POST/PATCH; `ResponseEntity` for anything else |
| `@UseGuards(ThrottlerGuard)` | Nothing yet: rate limiting arrives in M14 (Bucket4j) |
| WebSocket gateways (socket.io) | Not ported yet: Spring WebSocket/STOMP or SSE when a module needs it |

### Responses
| Source | VKurse |
|---|---|
| `return { value }` | `return XxxResponse.of(aggregate);` |
| `return { value: items }` | `return items.stream().map(XxxResponse::of).toList();` |
| `return { status: 'ok' }` on delete | `ResponseEntity.noContent().build()` (204) |
| `{ value, identity }` on login | The identity itself (`accessToken`, `refreshToken`, `sessionId`, `user`) |
| Errors sent as **HTTP 200** with `{ error: { code, message } }` | A real HTTP status + a problem body; `code` is a top-level field |

## 7. Errors

| Source | VKurse |
|---|---|
| `ClientError(code, message, records)` | `ClientException(status, code, message, records)` |
| `NotFoundError`, `ForbiddenError`, `ConflictError`, `BadRequestError`, `UnauthorizedError` | `NotFoundException`, `ForbiddenException`, `ConflictException`, `BadRequestException`, `UnauthorizedException` |
| `ValidationError(constraints)` | `ValidationException(constraints)` |
| `ServerError(message, records)` | `ServerException(message, records)`: logged, never shown |
| Custom subclasses (`CurrencyRequiredError`) | `new ClientException(BAD_REQUEST, "currency-required", "…")`, or a subclass if it is thrown in many places |
| `HttpExceptionsFilter` | `ErrorHandler` (`@RestControllerAdvice`) |
| `@HandleFirestoreError` | `JpaAggregateRepository.flush()` translates database errors: a unique violation → 409 `conflict`, a stale `version` → 409 `conflict/stale` |
| `*Error` class names | `*Exception`: in Java, `Error` means a fatal JVM failure |

The error body changed shape:
```json
// source (HTTP 200)
{ "error": { "code": "not-found", "message": "Course not found" } }
// VKurse (HTTP 404, application/problem+json)
{ "status": 404, "title": "Not Found", "detail": "Course not found", "instance": "/courses/…", "code": "not-found" }
```
Validation errors keep the same `constraints` map, now as a top-level field next to `code: "validation-error"`.

## 8. Logging

| Source | VKurse |
|---|---|
| `protected readonly logger = new Logger(X.name)` | `private static final Logger log = LoggerFactory.getLogger(X.class);` |
| `logger.error('msg', error.stack ?? error.message ?? error.toString())` | `log.error("msg", e)`: the exception last, and SLF4J prints the stack |
| `logger.log(...)` | `log.info(...)` |
| `` `User ${id} created` `` | `log.info("User {} created", id)` (placeholders, not concatenation) |
| `console.*` | Never |
| Winston file and Telegram transports | M12 (a Logback appender); prod logs are JSON on stdout |

## 9. Async work, events and scheduling

| Source | VKurse |
|---|---|
| `async` / `await`, `Promise<T>` | Plain blocking code returning `T`; virtual threads handle concurrency |
| `Promise.all([...])` of I/O calls | Sequential calls, unless the latency really matters; then `Executors.newVirtualThreadPerTaskExecutor()` |
| Fire-and-forget `void this.insights.notify(...)` | Publish an event; a listener in the other module handles it |
| `eventEmitter.emit('x.y', payload)` + `@OnEvent('x.y')` | `ApplicationEventPublisher.publishEvent(new XyEvent(...))` + `@ApplicationModuleListener void on(XyEvent e)` |
| `@Cron('0 * * * *')` (5 fields) | `@Scheduled(cron = "0 0 * * * *")` (**6 fields, seconds first**; a 5-field expression fails at startup) + `@SchedulerLock(name = "…")` |
| `setTimeout` retry loops | Spring's `@Retryable` on the outgoing call |

Events are records implementing `DomainEvent`, in the publishing module's `domain`, raised by the aggregate and published when it is saved. With Spring Modulith they are stored in `event_publication` in the same transaction as the write, delivered **after commit**, and re-delivered if a listener fails. A crash can't lose them, which fire-and-forget emits could.

## 10. Configuration, HTTP clients, i18n, templates

| Source | VKurse |
|---|---|
| `.env` + `.env.${NODE_ENV}` | `.env` (local only, imported by `application.yaml`) + profiles `application-{dev,prod}.yaml` |
| `NODE_ENV` | `SPRING_PROFILES_ACTIVE` (default `dev`) |
| `if (isDevEnv) …` | Profile-specific config/beans, not `if` checks in business code |
| A new env var | Map it in `application.yaml` with an explicit placeholder (`secret-key: ${YOOKASSA_SECRET_KEY}`) into a properties record. Relaxed binding won't map `VKURSE_NODE_ID` to `node-id` on its own |
| `axios.create({ baseURL, auth })` | A port interface in `domain/`, implemented in `infrastructure/` with a `RestClient` bean or an `@HttpExchange` interface, with timeouts set |
| axios error handling (`error.response.data`) | Catch `RestClientResponseException` and map it to a `ClientException` code |
| `Idempotence-Key: randomUUID()` | A key derived from **our** record id (e.g. the payment id), so a retry is the same request |
| `nestjs-i18n` JSON (`t('a.b', lang, { args })`) | Spring `MessageSource`, `messages_ru.properties` (M9); nested keys → dotted keys; default locale `ru` |
| Handlebars templates (`{{name}}`) | Thymeleaf (`th:text="${name}"`) (M9) |

## 11. Firestore → Postgres

| Firestore habit | Postgres / VKurse |
|---|---|
| Schemaless documents | Every field is a column, added in a migration. A new optional field is a nullable column; a new required field needs a `DEFAULT` or a backfill |
| Collections | Plural `snake_case` tables in the service's own schema (`core`) |
| `_locks` | `shedlock` |
| `_db_migrations` | `flyway_schema_history` |
| `_{collection}_bin` | None: see section 5.4 |
| `_auth_sessions` | `auth_sessions` (`revoked_at` on logout, removed once expired; no `invalid` flag) |
| `_auth_tokens`, `_api_keys` | Not ported |
| `.firebase/firestore.indexes.json` | `CREATE INDEX` in the migration for every column you filter or sort on |
| Reads before writes in transactions | No such rule; lock rows with `repository.findByIdForUpdate(id)` when two requests may change the same aggregate |
| `convertTimestamps` | Not needed: Hibernate maps `TIMESTAMPTZ` ↔ `Instant` |
| TS seed migrations (`ref.set({...})`) | `INSERT` statements in a Flyway migration (add `ON CONFLICT DO NOTHING` if it could meet existing rows) |
| Emulator (`FIRESTORE_EMULATOR_HOST`) | Local Supabase (`supabase start`) |

## 12. Auth

The source's `auth` module is ported as the `auth` module of `services/core` (see its README). Only email OTP, Google and Apple are ported.

| Source | VKurse |
|---|---|
| `BearerGuard` (verify JWT → load session → `request.session`) | Spring Security resource server. `JwtResourceServer` (in `libs/common`) checks the JWT and builds the `UserSession` from its claims; in core, `SessionAuthenticationConverter` also checks that the session is live |
| `JwtService.sign/verify` with `JWT_SECRET` fallbacks | `RsaJwtTokens`: RS256 with the required `VKURSE_AUTH_PRIVATE_KEY`, public key at `/.well-known/jwks.json`; the `aud` claim separates `access` and `refresh` tokens |
| One shared secret for every backend | Only core holds the private key. Other services fetch the public key and verify locally |
| `InvalidSessionError`, `ReloginRequiredError` | The same codes, `invalid-session` and `relogin-required`, now with HTTP 401 |
| `auth.buildSession(user, apiKey)` | `SignInService.signIn(user, provider, userAgent)` → identity; for OAuth, `startExchange(...)` returns a one-time code instead |
| `auth.refreshSession(sessionId)` (old refresh token stays valid) | `POST /auth/refresh` → `session.rotate(generation, now, ttl)` under a row lock: the refresh token is single-use, and a replay revokes the session |
| `auth.invalidateSessions(query)` | `SessionService.revoke`, `revokeOthers`, `revokeAll` |
| Passport strategies + `AuthGuard('google')` | `oauth2Login` (Spring Security OAuth2 Client), registrations in `application.yaml`, `OAuthLoginHandler` → `OAuthSignInService` |
| OAuth `state` = a signed JWT with `{ apiKey, callback }` | Spring's `state` + PKCE + nonce; the pending request and the validated `CallbackUrl` are a `PendingOAuthRequest` in `auth_oauth_requests` |
| `passport-apple` with `privateKeyLocation` | `AppleClientSecret` builds the ES256 client-secret JWT from `APPLE_PRIVATE_KEY` |
| `OtpService.sendCode` → JWT `{ email, code }` | An `OtpChallenge` with `HMAC(secret, email:code)`; the client gets its id |
| `passport-google-oauth20` without PKCE | Spring Security 7 adds PKCE to every client by default (removed for Apple, which doesn't document it) |
| Passport keeps nothing; Spring's default `OAuth2AuthorizedClientRepository` keeps provider tokens in memory | A no-op repository: we never call provider APIs after sign-in |
| `users.findOneOrCreate({ match: { email } })` in each strategy | `UserService.findOrRegister` (unique index + retry) and `LinkedAccount` keyed by `(provider, sub)` |
| API key on every login (`apiKey` query/header) | None. Sessions store `provider` and `user_agent` |
| Session cookie (`setSessionCookie`) | None: bearer tokens only |
| `@Throttle(...)` on auth routes | Per-email OTP cooldown and per-code attempt limit (rules of `OtpChallenge`); IP rate limiting in M14 |

## 13. Tests

The source has `*.spec.ts` with hand-written Firestore mocks. Don't port them. Tests arrive in M13, with Testcontainers (a real Postgres) instead of mocks.

## 14. Step-by-step: porting one module

1. **Read** the source module: its `README.md`, entities, service, hooks, controller, seed migrations, and every place other modules call it.
2. **List the rules**: every `if` in the service and hooks that decides whether or how state changes. Group them by the entity they protect: those are your aggregates and their methods.
3. **Design the tables:** columns and types (section 4.2), nullability, foreign keys, unique constraints, indexes. Write `V{N}__{module}.sql` in the service.
4. **Write `domain/`:** the aggregates (factory, methods, events), value objects, the repository interfaces with the finders the rules need, ports for outside systems, `{Module}Errors`.
5. **Write `infrastructure/`:** `Jpa{Name}Repository` (extends `JpaAggregateRepository`, package-private) with the JPQL; the ports' implementations.
6. **Write `application/`:** one method per use case: load, call the aggregate, save. Pass `userId` and check ownership. Replace cross-module calls that would form a cycle with events.
7. **Write `web/`:** the controller and its request/response records (section 6); real statuses, no wrappers.
8. **Configuration:** add a properties record for any env vars the module needs, with explicit placeholders in `application.yaml` and entries in `.env.example`.
9. **Docs:** add the module `README.md` and update the service README.
10. **Verify:** `./gradlew spotlessApply build`, `./gradlew :services:core:bootRun` (Hibernate validates the aggregates against the tables), then `curl` the endpoints and check the data in Supabase Studio.

## 15. Source bugs you must not port

| Source behavior | Do instead |
|---|---|
| `callInTransaction(async () => …)` ignored its `tx`, so a balance update and its transaction record weren't atomic | One `@Transactional` method; the balance change and the ledger row are written together |
| The balance was read, changed in memory and written back (race condition) | Load with a row lock (`repository.findByIdForUpdate(id)`), change through the aggregate, save |
| A payment webhook credited the balance on **every** delivery, and providers retry | An inbox table with a unique key `(provider, payment_id, event)`; process each event once |
| The YooKassa `Idempotence-Key` was a new random UUID on every call | Derive the key from our payment id |
| Client errors returned HTTP 200 | Real HTTP statuses |
| Money as floating-point `number` | `Money` / `BigDecimal` |
| A hard-coded admin API key in code | Roles come from the database; secrets come from config |
| Updates and deletes weren't scoped to the owner | Every use case for a user takes `userId` and checks ownership (section 5.3) |
| Events emitted after the write, fire-and-forget (lost on a crash) | Modulith events, stored in the same transaction |
| `TODO` stubs such as `reserve()` / `releaseReservation()` that do nothing | Implement them, or leave them out explicitly |
| The OTP code was inside a signed (readable) JWT returned to the client | Store the code server-side (hashed); give the client an opaque id |
| 4-digit OTP, no attempt limit; OTP login via `GET` with the code in the query | 6 digits, 5 attempts per code, `POST` with a JSON body |
| OAuth `callback` accepted from the query without a check (open redirect) | Only origins in `VKURSE_WEB_ORIGINS` |
| OAuth tokens passed back in the redirect URL | A one-time exchange code, traded for tokens with `POST /auth/exchange` |
| OAuth users linked by email, even an unverified one | Link by provider `sub`; link by email only when the provider marks it verified |
| `/sessions` endpoints not scoped to the caller | `SessionService` takes the caller's `userId` in every method |
| JWT secret with a hard-coded default, shared by everything that verifies tokens | A required private key only core holds; startup fails without it |
