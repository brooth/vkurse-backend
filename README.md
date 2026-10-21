# Vkurse backend

Backend for **Vkurse**, a platform for online schools in Russia. Schools build and sell courses, and students buy them and study. It is a set of services that share one library.

**Stack:** Java 25 · Spring Boot 4.1 · Hibernate 7 · PostgreSQL (Supabase) · Flyway · Spring Modulith · ShedLock · Gradle (Kotlin DSL, multi-project)

| Path | What |
|---|---|
| [`services/platform`](services/platform/README.md) | Users, authentication, schools and file storage. Issues the tokens every other service verifies |
| [`services/monitoring`](services/monitoring/README.md) | Telemetry from the services through Kafka, kept for Grafana: slow endpoints, nodes, database, jobs, errors |
| [`libs/core`](libs/core/README.md) | Shared library: errors, validation, aggregate base, token verification, money |

- [AGENTS.md](AGENTS.md): coding rules and conventions
- [Main plan](docs/PLAN.md): decisions, architecture, milestones
- [NestJS migration guide](docs/nest_js_migration.md): porting modules from a NestJS + Firestore codebase

## Running locally

Requirements: Docker. Gradle downloads JDK 25 for compiling, but needs a JDK 17 or newer to start itself (`JAVA_HOME`).

```bash
docker compose -f compose.dev.yaml up -d         # local Postgres, Supabase Storage, Mailpit
cp services/platform/.env.example services/platform/.env # first time only; then fill in VKURSE_AUTH_PRIVATE_KEY
./gradlew :services:platform:bootRun                 # http://localhost:8080
```

| Service | URL |
|---|---|
| platform | http://localhost:8080 (health: `/actuator/health`, keys: `/.well-known/jwks.json`) |
| monitoring | http://localhost:8090 (health: `/actuator/health`) |
| Mailpit (local email) | http://127.0.0.1:54324 |
| Kafka (telemetry, bootstrap server) | localhost:54392 |
| Postgres | `postgresql://postgres:postgres@127.0.0.1:54322/postgres` (tables in schema `platform`; `docker exec -it vkurse-db psql -U postgres`) |
| Supabase Storage | http://127.0.0.1:54321 |

[compose.dev.yaml](compose.dev.yaml) runs them as `vkurse-db`, `vkurse-storage` and `vkurse-mailpit`. The database is the Supabase Postgres image, which Storage needs; Storage runs without the Supabase gateway, so its URL has no `/storage/v1`. `docker compose -f compose.dev.yaml down -v` wipes the database and the files.

Each service owns one Postgres schema, and Flyway applies its migrations on startup.

## Common commands

```bash
./gradlew build                     # compile and package everything
./gradlew spotlessApply             # format code and add copyright headers
./gradlew :services:platform:bootRun    # run one service with the dev profile
```
