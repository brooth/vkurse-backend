# ADR-0002: Token verification via JWT/JWKS with a Redis revocation blocklist

## Status
Accepted

## Context

Platform issues JWT access tokens to authenticate users. Other services (currently analytics, more will follow) need to verify these tokens on every request, without creating a hard dependency on platform being reachable for every single call.

**Motivating use case:** this is an online course platform — students and authors interact directly, and a course curator may need to ban a student for a violation (e.g. abusive behavior, cheating, harassment of an author). A ban must take effect immediately: the banned student's existing session should stop working right away, not continue to function for up to 15 minutes until their token naturally expires. The same mechanism also covers the more general cases of logout and role changes, but instant ban enforcement is the primary driver for needing any revocation mechanism at all, rather than relying purely on short token expiry.

Three options were considered for how tokens are verified and how revocation (logout, role change, blocking a user) is handled:

1. **Pure JWT + JWKS, no blocklist.** Platform signs tokens asymmetrically (RS256/ES256) and publishes a public key via a `/.well-known/jwks.json` endpoint. Any service verifies the signature and the `exp` claim locally, with no network call. Revocation only takes effect when the token naturally expires.
2. **JWT + JWKS with a Redis-based revocation blocklist.** Same local signature/expiry verification as option 1, plus an optional Redis lookup for revoked tokens (or their `jti`), used only where instant revocation matters.
3. **Redis as the primary session store, opaque tokens.** No JWT at all — the token is a random string, and every service looks up session data (and whether it's still valid) directly in Redis on every request. Revocation is a simple `DEL`.

## Decision

Adopt **option 2: JWT + JWKS with a Redis revocation blocklist.**

- Platform signs access tokens with an asymmetric key (RS256/ES256) and publishes the public key via JWKS.
- Access token lifetime is shortened from 1h to ~15 minutes.
- Other services verify the signature and `exp` claim locally — no call to platform on every request.
- A Redis-based blocklist of revoked tokens (or their `jti`) is checked by services/operations where instant revocation matters; this is an additive check, not a replacement for local verification.
- Volatile user fields (phone, name, etc.) are never embedded in the token; services fetch them fresh from platform when a critical operation needs current data.

## Rationale

- **Pure JWT/JWKS (option 1)** is the fastest and most resilient — no network dependency at all for verification, and no outage in Redis or platform can block it. Its weakness is that revocation (logout, role change, ban) only takes effect at token expiry. For a banned student, that means up to 15 minutes of continued access after the ban — not acceptable for this use case.
- **Redis as primary session store (option 3)** makes revocation trivial and instantaneous, but makes every single request — from every service — depend on Redis being reachable. This turns Redis into a single point of failure for all authentication across the whole system, which is a larger risk than the project is willing to accept just to get instant revocation on every path.
- **JWT/JWKS + blocklist (option 2)** keeps the resilience and speed of local verification as the default path, and adds Redis only where instant revocation is actually needed. The blocklist only ever holds currently-revoked tokens (a small, bounded set), not all active sessions, so the Redis dependency is much lighter than in option 3, and a Redis outage degrades only the instant-revocation feature, not authentication as a whole.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Pure JWT/JWKS, no blocklist | No way to instantly revoke a token; a banned student would keep access for up to the remaining token lifetime, which isn't acceptable for a ban |
| Redis as primary session store (opaque tokens) | Makes Redis a single point of failure for all authentication across every service, on every request; too large a resilience trade-off for the gain of instant revocation |

## Consequences

- Services other than platform never call platform to verify a token's signature — only an optional Redis lookup when instant revocation matters.
- Access tokens are short-lived (~15 min), meaning the frontend must refresh more frequently than with the previous 1h lifetime.
- Volatile user data (phone, name, etc.) must never be embedded in tokens; any operation needing current values fetches them fresh from platform rather than trusting token contents.
- Service-to-service calls (e.g. analytics calling platform) forward the user's existing token rather than using separate service credentials, for simplicity at the current project scale.
</content>
