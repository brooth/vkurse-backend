# `services/platform`

> Users and authentication. The only service that issues tokens; every other service verifies them with the keys published at `/.well-known/jwks.json`.

Source root: `src/main/java/space/vkurse/platform`. Each direct subpackage is one module (a Spring Modulith application module), split into four layers.

| Module | Description |
|---|---|
| [`user/`](src/main/java/space/vkurse/platform/user/README.md) | The `User` aggregate and the caller's own profile (`/users/me`) |
| [`auth/`](src/main/java/space/vkurse/platform/auth/README.md) | Sessions, our JWTs, email OTP, Google and Apple sign-in, the security filter chain |

| Layer | Holds | May use |
|---|---|---|
| `domain` | Aggregates, value objects, events, repository interfaces, ports | `libs/core`, other modules' `domain` |
| `application` | Use cases: load, call the aggregate, save. Transactions, jobs, listeners | `domain`, other modules' `application` |
| `infrastructure` | JPA repositories, token signing, mail, Spring Security wiring | everything in its module |
| `web` | Controllers and request/response records | `application`, `domain` (to read) |

Root classes: `PlatformApplication` (entry point), `PlatformProperties` (`vkurse.public-url`, `vkurse.mail-from`).

Resources: `application.yaml` (+ `-dev`, `-prod`), `db/migration/V{N}__{description}.sql` (schema `platform`).

## Environment

| Variable | Required | Description |
|---|---|---|
| `VKURSE_NODE_ID` | yes | Instance name used in logs and alerts |
| `VKURSE_PUBLIC_URL` | yes | Public base URL of this service: token issuer, OAuth redirect URIs |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | no (local defaults) | JDBC connection |
| `SPRING_PROFILES_ACTIVE` | no (`dev`) | `prod` in production (JSON logs) |
| `PORT` | no (`8080`) | HTTP port |
| `VKURSE_AUTH_PRIVATE_KEY` | yes | RSA key that signs our tokens: PKCS#8 PEM, 2048 bits or more, newlines as `\n` |
| `VKURSE_AUTH_KEY_ID` | yes | `kid` of that key |
| `VKURSE_AUTH_SECRET` | yes | HMAC key for OTP and exchange code hashes, at least 32 characters |
| `VKURSE_WEB_ORIGINS` | yes | Comma-separated frontend origins: allowed OAuth callbacks and CORS |
| `VKURSE_MAIL_FROM` | yes | Sender address of our emails (OTP codes) |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | no (local Mailpit `127.0.0.1:54325`) | SMTP server |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | yes | Google OAuth client |
| `APPLE_CLIENT_ID` / `APPLE_TEAM_ID` / `APPLE_KEY_ID` / `APPLE_PRIVATE_KEY` | yes | Sign in with Apple: Services ID, team, key id, `.p8` PEM content |

Generate a local signing key: `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | awk '{printf "%s\\n", $0}'`.

## Guidelines
- Keep this file and the module READMEs up to date in every change.
- New modules follow the layout in [AGENTS.md](../../AGENTS.md#module-structure).
