# VKurse backend

Backend for **VKurse**, a platform for online schools in Russia. Schools build and sell courses, and students buy them and study. It is a set of services that share one library.

**Stack:** Java 25 · Spring Boot 4.1 · Hibernate 7 · PostgreSQL (Supabase) · Flyway · Spring Modulith · ShedLock · Gradle (Kotlin DSL, multi-project)

| Path | What |
|---|---|
| [`services/core`](services/core/README.md) | Users and authentication. Issues the tokens every other service verifies |
| [`libs/common`](libs/common/README.md) | Shared library: errors, validation, aggregate base, token verification, money |

- [AGENTS.md](AGENTS.md): coding rules and conventions
- [Main plan](docs/PLAN.md): decisions, architecture, milestones
- [NestJS migration guide](docs/nest_js_migration.md): porting modules from a NestJS + Firestore codebase

## Running locally

Requirements: Docker and the [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started). Gradle downloads JDK 25 for compiling, but needs a JDK 17 or newer to start itself (`JAVA_HOME`).

```bash
supabase start                                   # local Postgres, Studio, Mailpit (config in ../api/supabase)
cp services/core/.env.example services/core/.env # first time only; then fill in VKURSE_AUTH_PRIVATE_KEY
./gradlew :services:core:bootRun                 # http://localhost:8080
```

| Service | URL |
|---|---|
| core | http://localhost:8080 (health: `/actuator/health`, keys: `/.well-known/jwks.json`) |
| Supabase Studio | http://127.0.0.1:54323 (tables in schema `core`) |
| Mailpit (local email) | http://127.0.0.1:54324 |
| Postgres | `postgresql://postgres:postgres@127.0.0.1:54322/postgres` |

Each service owns one Postgres schema, and Flyway applies its migrations on startup.

## Common commands

```bash
./gradlew build                     # compile and package everything
./gradlew spotlessApply             # format code and add copyright headers
./gradlew :services:core:bootRun    # run one service with the dev profile
```
