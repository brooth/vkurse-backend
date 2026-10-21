# M13: Monitoring

> A `monitoring` service that collects telemetry from every service through Kafka, keeps it in its own Postgres schema and shows it to admins in Grafana: how the platform and its database perform. It replaces the Grafana LGTM plan of the first M13 (Grafana stays, but reads our own tables) and the idea of an analytics service. What we learn from it drives the optimising work: caching, indexes, N+1 fixes.

## Scope

| Part | State |
|---|---|
| Telemetry contract in `libs/core`: events, the `Telemetry` port, a Kafka producer that never blocks or fails a request, a no-op one when `vkurse.telemetry.enabled` is false | Done |
| Per-minute, per-route aggregates of HTTP requests, plus raw samples of errors, slow requests and 1% of the rest | Done |
| Per-request DB counters (queries, DB time) and normalised slow statements | Not done |
| Node metrics every 15 s: CPU, heap, GC, threads, Hikari pool, uptime | Done |
| Jobs and outside HTTP calls | Done |
| Notification attempts, grouped errors | Not done |
| `services/monitoring`: Kafka ingest (idempotent), daily partitions, TTL by dropping partitions, 1-minute and 1-hour rollups | Not done |
| `pg_stat_statements`, locks, sizes and cache hit ratio of the platform database, polled each minute | Not done |
| Grafana (a container next to the service): the PostgreSQL data source, dashboards and alert rules kept in `services/monitoring/grafana/`, sign-in with our JWT, admins only | Not done |
| Kafka and Grafana in `compose.dev.yaml` | Kafka: done |
| A REST API of reports for the website | Not done: later, over the same views, if Grafana is not enough |

## Decisions

- **Typed events, not log text.** The places that log today also emit an event; logs stay for people.
- **Pre-aggregate on the node.** A window per minute and route template (never the raw URL), with a fixed log-scale histogram, so percentiles merge across nodes. Raw samples only for errors, slow requests (`slowMs`, 500) and a 1% sample.
- **Telemetry never hurts the app.** A bounded queue, a batch sender that drops when full and counts the drops. A broker that is down, at start or later, never fails a start or a request.
- **A flag, not conditional wiring.** `vkurse.telemetry.enabled` (default false, so a developer without Kafka needs nothing). The `Telemetry` bean is always there: a `@Bean` method returns `KafkaTelemetry` or `NoopTelemetry`, and callers never read the flag. `bootstrap-servers` is required only in the sense of `@Validated` when the flag is on.
- **Kafka**: a single KRaft node in `compose.dev.yaml`, **Amazon MSK in production**. Three partitions, 24 h retention, keyed by node id: `telemetry.http`, `telemetry.node`, `telemetry.jobs`, `telemetry.errors`.
- **A separate database for telemetry in production.** In development it is one more schema (`monitoring`) in the one Postgres, as for every service. In production it is a small RDS instance of its own: the write load, rollups and Grafana queries must not slow the platform we measure, and the numbers of `pg_stat_statements` stay clean. The service reaches the platform database only for `pg_stat_*`, through a second, read-only data source (`pg_monitor` role); no tables of platform are read. Grafana reads only the `monitoring` database, with a read-only role.
- **Partitions and rollups.** Raw tables are partitioned by day; the TTL drops a partition. Rollups (1 minute, 1 hour) are kept longer. Defaults: raw samples 7 d, 1-minute rollups 30 d, 1-hour rollups 1 y, raw node metrics 3 d. No sharding; if ingest passes about 5 to 10 thousand rows a second, move the tables to TimescaleDB or ClickHouse.
- **Idempotent ingest.** Events carry a UUIDv7 id (`ON CONFLICT DO NOTHING` on `(id, ts)`); windows are replaced by `(node, route, bucket)`, never added.
- **Measurements are not aggregates.** No `AggregateRoot`, no events; written with JDBC batches in `infrastructure`.
- **Every event carries `service`, `nodeId` and `version`**, so releases can be compared.
- **No bodies, IPs, tokens or SQL parameters.** `userId` only. Free text goes through `LogMasker`.
- **Reports are SQL views** in the `monitoring` schema; a Grafana panel is a query on a view. Alerts are Grafana alert rules sent to Telegram and email.
- **Per-request counters:** a counter bound to the request thread, started by `HttpLogFilter`, raised by a Hibernate `StatementInspector` and timed by a thin `DataSource` wrapper; read at the end of the request.
- **Cloud:** AWS (RDS, MSK, S3). Moving the rest of the platform off Supabase is a separate task, not part of this milestone.

## Reports (Grafana dashboards)

All filter by service, node and version; the resolution (raw, 1 minute, 1 hour) follows the range.

1. Overview: requests per second, error rate, p95, active nodes, pool saturation, top problems.
2. Slowest endpoints, by p95 and by total time (count × average).
3. Endpoint detail: percentiles over time, status mix, queries per request, DB share, slow samples with their SQL.
4. N+1 and chatty endpoints.
5. Cache candidates: high-volume reads, high DB time, few writes, with a score.
6. Nodes: CPU, memory, GC, threads, pool, uptime, restarts, silent nodes.
7. Database: costliest statements, lock waits, idle in transaction, cache hit ratio, table growth, connections.
8. Jobs: duration trend, failures, last success.
9. Outside APIs: latency and errors per client.
10. Notifications: sent, failed, retried per channel.
11. Errors: grouped by fingerprint, first and last seen, trend, sample stack.
12. Release compare: version A against B.
13. Telemetry health: drops, consumer lag, ingest rate, table sizes.

## Steps

1. Docs (this file, `PLAN.md`, READMEs).
2. Kafka in `compose.dev.yaml` (done; `pg_stat_statements` is already loaded in the dev image).
3. Contract and hooks in `libs/core`; platform emits (done; JMX and the Hikari bean are read directly, so no Micrometer is needed).
4. `services/monitoring` skeleton, `V1__init.sql` (done: it starts, migrates, verifies tokens).
5. Ingest, partitions, TTL, rollups.
6. DB signals.
7. Views, Grafana in compose, provisioning, JWT sign-in, alerts.

## Verified by hand

To fill in when done. The plan: drive mixed traffic (fast, slow, 4xx, 5xx) and see events in Kafka and rows in `monitoring.*`; stop Kafka and see platform start and stay responsive while drops are counted; with the flag off see nothing sent; replay a batch and see no duplicates; expire a partition; open each dashboard as admin and see a non-admin refused; find a planted N+1 and a planted slow endpoint at the top; run two nodes and see merged percentiles.
