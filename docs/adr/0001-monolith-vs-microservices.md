# ADR-0001: Modular monolith (platform) + separate microservice (analytics)

## Status
Accepted

## Context

The "Vkurse" system consists of two functional areas:

- **Platform** — operational (domain) logic: users, authentication, courses, lessons, enrollments, payments. This is the write side of the system in CQRS terms.
- **Analytics** — reports and dashboards for course authors (enrollment stats, attendance, group engagement). This is the read side, built from events emitted by platform.

Two decisions needed to be made:
1. Whether to split these two areas across separate physical databases.
2. Whether to split them into separate deployment units (microservices), or keep them as modules of one process.

## Decision

- **Platform** is implemented as a **modular monolith**: one Spring Boot process with internal modules (`auth`, `course`, `enrollment`, etc.), each with its own DDD + hexagonal layering (domain/application/infrastructure). Module boundaries are enforced via Spring Modulith.
- **Analytics** is extracted into a **separate physical microservice**, with its own database, built as a CQRS read model.
- Communication between platform and analytics is **asynchronous, via events (Kafka)**, not direct calls or cross-database joins.

## Rationale

### Why separate databases is not the same decision as separate microservices

These two decisions are justified independently of each other:

- **Separate databases** is justified by different load profiles:
  - Platform is **OLTP**: short transactions, frequent small writes. Extra indexes slow down writes, so they are kept to a minimum.
  - Analytics is **OLAP**: heavy, frequent reads (building arbitrary reports), rare batched writes (via events). Here it's fine — even desirable — to add many indexes and denormalize data for specific reports, since write speed isn't critical.
  - Keeping both workloads in one database forces a single compromise on indexing strategy that is optimal for neither side. Separate databases remove that dilemma.
  - **Technically**, this could be done without a separate service at all — one process, two different DB connections. This alone doesn't require microservices.

- **A separate microservice** (rather than just a module with a different database inside the same process) is justified by a different set of reasons:
  - **Independent scaling** — heavy reporting queries shouldn't require scaling platform as a whole.
  - **Fault isolation** — a bug or outage in analytics shouldn't take down platform.
  - **Independent deployment** — analytics can be developed and released independently of platform's domain logic.
  - Communicating via Kafka events is itself a microservices-style practice, not a modular-monolith one: asynchronous, tolerates lag, and the read model catches up independently (eventual consistency).

### Why platform is a monolith, not a set of microservices

- At the current scale, the domain modules (`auth`, `course`, `enrollment`) have no diverging load and no need for independent deployment from each other.
- Splitting them up would add operational complexity (network calls instead of direct calls, distributed transactions instead of local ones) without a matching benefit at this stage.
- Module boundaries are already enforced by DDD/hexagonal rules, so if a real trigger appears later (see below), extracting a module into its own service should require minimal domain changes — mostly swapping a direct call for a network call or event.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Single database for platform and analytics | OLTP/OLAP load profiles conflict on one schema; can't optimize for both sides at once |
| Analytics as a module inside the platform process, with its own DB | Technically possible, but doesn't give the independent scaling and fault isolation that analytics specifically needs (heavy reporting queries) |
| Platform split into separate microservices (auth-service, course-service, enrollment-service...) | Excessive operational complexity at the current project scale; no diverging load or deployment requirements that would justify it |

## Consequences

- Platform and analytics are physically independent: separate repos, separate databases, separate deployments.
- Analytics always works with data on a delay (eventual consistency) — accepted deliberately, since reports don't inherently require instant consistency.
- Enforcing module boundaries inside platform (DDD + hexagonal + Spring Modulith) is a deliberate investment for the future: if any platform module (e.g. `course`) eventually needs independent scaling, it can be extracted into its own service at relatively low cost, because the boundary is already drawn in the code.

## Revisit triggers

Revisit this decision (extract a platform module into its own service) if any of the following occurs:
- Diverging load between platform modules (e.g. `course` needs to scale more than `auth`).
- A need for independent deployment of one module (frequent releases of one part without risking the others).
- The team grows enough that different modules are developed by different people/teams in parallel, and a monolithic deployment becomes a point of conflict.
</content>
