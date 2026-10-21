# `services/monitoring`

> Collects telemetry from the services through Kafka, keeps it in its own Postgres schema and shows it to admins in Grafana: slow endpoints, node CPU and memory, database statements, jobs, outside APIs and errors. The plan is [M13](../../docs/milestones/13-monitoring.md); what the services send is in [libs/core](../../libs/core/README.md#telemetry).

Source root: `src/main/java/vkurse/monitoring`. It does not issue tokens: it verifies platform's access tokens with the keys at `/.well-known/jwks.json` (`SecurityConfig`), and everything but `/actuator/health` needs one.

State: the skeleton (it starts, applies its migrations, delivers notifications and verifies tokens). Ingest, retention, the reports' views and Grafana are the next steps of M13.

Root classes: `MonitoringApplication` (entry point), `MonitoringProperties` (`vkurse.platform-url`), `SecurityConfig`.

Notifications come from [`libs/core`](../../libs/core/README.md#notifications): this service has the `notifications` table, `messages.properties` and no templates yet.

Resources: `application.yaml` (+ `-dev`, `-prod`), `db/migration/V{N}__{description}.sql` (schema `monitoring`), `messages.properties` (Russian, the base bundle).

## Environment

| Variable | Required | Description |
|---|---|---|
| `VKURSE_NODE_ID` | yes | Instance name used in logs and alerts |
| `VKURSE_PLATFORM_URL` | yes | Platform's public base URL (its `VKURSE_PUBLIC_URL`): the issuer of the access tokens and where its keys are |
| `VKURSE_MAIL_FROM` | yes | Sender address of our emails |
| `TELEGRAM_BOT_TOKEN` | yes | Telegram bot that sends our notifications. A placeholder works locally |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | no (local defaults) | JDBC connection. In production its own database, not platform's |
| `SPRING_PROFILES_ACTIVE` | no (`dev`) | `prod` in production (JSON logs) |
| `PORT` | no (`8090`) | HTTP port |
| `VKURSE_LOG_SQL` | no (`info`) | `debug` logs every SQL statement |
| `VKURSE_TELEMETRY_ENABLED`, `KAFKA_BOOTSTRAP_SERVERS` | no (`false`, `localhost:54392`) | This service's own numbers, like any service's; see [Telemetry](../../libs/core/README.md#telemetry) |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | no (local Mailpit) | SMTP |

## Running locally

```bash
docker compose -f compose.dev.yaml up -d
cp services/monitoring/.env.example services/monitoring/.env   # first time only
./gradlew :services:monitoring:bootRun                          # http://localhost:8090
curl localhost:8090/actuator/health
```
