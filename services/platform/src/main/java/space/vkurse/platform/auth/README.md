# Auth module

> Our own authentication: JWTs, sessions stored in Postgres, email OTP, Google and Apple sign-in, and the security filter chain. Supabase Auth is not used.

| Layer | File | Description |
|---|---|---|
| `domain` | `Session` | A signed-in device: `start`, `rotate` (refresh), `issueExchangeCode` / `redeemExchangeCode`, `revoke`, `isLive` |
| | `OtpChallenge` | An emailed code: `issue`, `verify` (expiry, attempts, constant-time compare) |
| | `LinkedAccount` | A Google or Apple account of a user, keyed by the provider's `sub` |
| | `PendingOAuthRequest`, `CallbackUrl` | A sign-in that is at the provider; the allowlisted frontend URL it returns to |
| | `SignInProvider`, `AuthErrors` | `OTP` / `GOOGLE` / `APPLE`; the module's client errors |
| | `…Repository`, `TokenIssuer`, `SecretHasher` | Repository interfaces and ports |
| `application` | `SignInService` | Sign-in, OAuth exchange codes, refresh-token rotation |
| | `OtpSignInService` | Send a code, sign in with it |
| | `OAuthSignInService` | Find or sign up the user behind a provider account |
| | `OAuthRequestService` | Keeps pending OAuth sign-ins |
| | `SessionService` | The caller's devices: list, revoke one, revoke others |
| | `UserDeletedListener`, `AuthCleanupJob` | Ends a deleted user's access; hourly removal of expired rows |
| `infrastructure` | `Jpa…Repository` | Database access |
| | `RsaJwtTokens`, `HmacSecretHasher` | The ports' implementations |
| | `security/` | `SecurityConfig`, `SessionAuthenticationConverter`, `OAuthConfig`, `OAuthRequestStore`, `OAuthLoginHandler`, `AppleClientSecret`, `AuthFailure` |
| `web` | `AuthController`, `OtpController`, `SessionController`, `JwksController` | Controllers with their request/response records |

`AuthProperties` (`vkurse.auth.*`) is at the module root.

## Protecting routes

Every route needs an access token, except the public ones in `SecurityConfig`: `/auth/**`, `/.well-known/jwks.json`, `/actuator/health`, `/error`.

```java
@GetMapping("/me")
UserResponse me(@GetSession UserSession session) { … }   // userId, sessionId, role

@AdminOnly
@PatchMapping("/admin/users/{id}")
UserResponse update(…) { … }                             // 403 forbidden for non-admins
```

## Tokens

RS256 JWTs signed with `VKURSE_AUTH_PRIVATE_KEY`, header `kid` = `VKURSE_AUTH_KEY_ID`, `iss` = `VKURSE_PUBLIC_URL`. The `aud` claim says what the token is for, so one kind can't be used as another. The public key is served at `GET /.well-known/jwks.json`.

| Token | Claims | Lifetime |
|---|---|---|
| Access | `aud=access`, `sub` (user id), `sid` (session id), `role` | `vkurse.auth.access-ttl` (15 min) |
| Refresh | `aud=refresh`, `sub`, `sid`, `gen` (refresh generation) | `vkurse.auth.refresh-ttl` (180 d), sliding |

**Who checks what.** Other services verify the signature and claims only (`JwtResourceServer` in `libs/core`), so they trust a token until it expires. Platform also loads the session on every request (one primary-key read) and rejects a revoked or expired one at once. The role in a token is the role at the time it was issued: a role change shows at the next refresh.

**Refresh rotation.** `POST /auth/refresh` loads the session with a row lock and calls `session.rotate(gen, now, ttl)`. The current generation moves the session to the next one and extends it. Any other generation means the token was already used: the session is revoked (someone else has a copy) and the call gets `relogin-required`. **Clients must not refresh in parallel** (two tabs with the same token): the second call counts as a replay.

## Endpoints

| Endpoint | Auth | Body | Returns |
|---|---|---|---|
| `POST /auth/otp` | public | `{ email }` | `{ token, expiresAt }` |
| `POST /auth/otp/login` | public | `{ token, code }` | identity |
| `GET /auth/oauth/{google\|apple}?callback=…` | public (browser) | | 302 to the provider |
| `GET\|POST /auth/oauth/{provider}/callback` | public (provider) | | 302 to `callback?code=…` or `callback?error=…` |
| `POST /auth/exchange` | public | `{ code }` | identity |
| `POST /auth/refresh` | public | `{ refreshToken }` | identity |
| `GET /.well-known/jwks.json` | public | | JWK set |
| `GET /sessions` | token | `$limit`, `$skip` | live sessions, newest first |
| `DELETE /sessions/{id}` | token | | `204` (sign one device out) |
| `DELETE /sessions` | token | | `204` (sign out every other device) |

Identity: `{ accessToken, refreshToken, sessionId, user }`.

## Email OTP

A 6-digit code lives 7 minutes and allows 5 wrong attempts; a new code for the same email can be requested once per 30 seconds (the constants are in `OtpChallenge`). Only `HMAC(secret, email:code)` is stored, and the client gets the challenge id as `token`.

The email is the `otp-code` template, sent with `NotificationService.send` from `libs/core`: at once, and never stored, because a stored message would keep the code. It is sent after the challenge is committed, so no database connection is held during SMTP. If sending fails, the challenge is removed and the request fails. A wrong code is committed as a used attempt even though the call fails. A correct code removes the challenge.

## Google and Apple

`oauth2Login` (Spring Security OAuth2 Client) under `/auth/oauth`; the registrations are in `application.yaml`.

1. `GET /auth/oauth/{provider}?callback=<frontend URL>`. `CallbackUrl` accepts only an http(s) URL on an origin in `VKURSE_WEB_ORIGINS`. The request is stored in `auth_oauth_requests` (10 minutes), not in the HTTP session, so any node can finish it.
2. The provider redirects back. `OAuthLoginHandler` reads the id token and `OAuthSignInService` finds the user: by linked account (`provider` + `sub`), else by email, but only an email the provider marks verified. A new link is stored.
3. A session is started with a one-time exchange code (60 seconds), and the browser is redirected to `callback?code=…`. Tokens never appear in a URL.
4. The frontend calls `POST /auth/exchange`.

PKCE is on for Google and removed for Apple, which gets `response_mode=form_post`. Apple's client secret is a JWT signed with the `.p8` key (`AppleClientSecret`). Provider tokens are not kept.

## Errors

| Status | `code` | When |
|---|---|---|
| 401 | `invalid-session` | The access token has expired: call `/auth/refresh` |
| 401 | `relogin-required` | The token is invalid, or its session has ended: sign in again |
| 401 | `unauthorized` | No token |
| 400 | `otp-expired` | Unknown or expired code |
| 400 | `otp-incorrect` (+ `attemptsLeft`) | Wrong code |
| 429 | `otp-attempts-exceeded` | No attempts left |
| 429 | `otp-too-frequent` | A code was sent less than 30 seconds ago |
| 400 | `exchange-code-invalid` | Wrong, used or expired exchange code |
| redirect | `error=oauth-email-required` | The provider gave no verified email |
| redirect | `error=oauth-failed` | The provider sign-in failed or was cancelled |

## Tables

| Table | Rows end by |
|---|---|
| `auth_sessions` | `revoked_at` on sign-out, replay or account deletion; removed by the cleanup once `expires_at` has passed |
| `auth_linked_accounts` | Removed with the user's account |
| `auth_otp_codes` | Removed on success; expired ones by the cleanup |
| `auth_oauth_requests` | Removed when the provider redirects back; expired ones by the cleanup |

## Configuration

`vkurse.auth.*` (`AuthProperties`): `private-key`, `key-id`, `secret`, `access-ttl`, `refresh-ttl`, `web-origins`, `apple.{team-id,key-id,private-key}`. See the [service README](../../../../../../../README.md#environment) for the environment variables.
