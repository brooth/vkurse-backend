# ADR-0003: Hexagonal architecture (ports and adapters) for domain-bearing service modules

## Status
Accepted

## Context

Platform's service modules (`auth`, `course`, `enrollment`, etc.) contain real business logic and invariants — rules that must always hold (e.g. "a course can't be published with zero lessons"), aggregates with protected internal state, domain operations like `course.publish()` or `course.addLesson()`.

Three architectural styles were considered for structuring these modules:

1. **Flat/layered structure.** Simple packages by technical type (`controller`, `service`, `repository`, `entity`). No enforced boundary between business logic and framework/persistence code.
2. **Clean Architecture.** Concentric layers (entities, use cases, interface adapters, frameworks/drivers) with dependencies pointing inward.
3. **Hexagonal architecture (ports and adapters).** Domain and application logic live in the center, with zero framework dependencies where possible; repository interfaces ("ports") are defined in the domain, and their implementations ("adapters" — JPA, external APIs, etc.) live in an outer infrastructure layer.

## Decision

Adopt **hexagonal architecture (ports and adapters) with a rich domain model** for platform's domain-bearing service modules.

- Each module is split into `domain`, `application`, and `infrastructure` layers.
- Business logic and invariants live inside domain entities/aggregate roots (rich domain model), not in anemic services that just move data around.
- Repository interfaces are defined in the domain layer; their implementations (JPA, etc.) are adapters in the infrastructure layer.
- This is applied to modules with real domain logic (platform's modules). It is explicitly **not** applied to the technical `core` library (errors, validation, money, JWT verification) or to analytics, which stays simple CRUD — see the "scope" note below.

## Rationale

- A flat/layered structure doesn't prevent business logic from leaking into controllers or getting tangled with JPA/Hibernate annotations and framework concerns. Nothing stops an invariant from silently being bypassed by code that goes straight to a repository or entity.
- Clean Architecture's concentric-layer model solves a similar problem to hexagonal, but hexagonal's ports-and-adapters framing was judged a better fit here: it maps directly onto the project's existing repository-interface pattern (a port is just the domain-defined repository interface; an adapter is the JPA implementation), so adopting it is an extension of decisions already made in the repository-pattern refactor, not a parallel competing structure.
- The real benefit of hexagonal over a disciplined flat structure is that the domain/infrastructure boundary is enforced by the compiler (the domain package cannot import JPA/Spring types), not just by convention or developer discipline. This matters most in larger teams or when module boundaries need to survive contributors who don't know the informal rules — but it's also valuable here as a forcing function for this project specifically, so invariants get written where they belong rather than scattered into services.
- This decision was reached with real skepticism going in — a working interface-based repository swap (from the repository-pattern refactor) already gives most of the testability and swappability a strict hexagonal split would provide, without the full domain/infrastructure separation ceremony. The project explicitly chose to try hexagonal in practice on the real codebase and see what friction it causes, rather than adopt it purely on faith.

### Scope note

Hexagonal is applied only where there is domain logic with invariants to protect:

- **Platform's modules** (`auth`, `course`, `enrollment`, etc.) — full hexagonal + rich domain model.
- **Analytics** — kept as simple CRUD; no significant invariants to protect, so the extra layering isn't worth the ceremony.
- **The `core` library** (errors, validation, money, JWT verification) — stays a plain technical utility library. It has no aggregate root and no domain layer in the DDD sense, so there's nothing for a hexagonal split to protect; forcing the same layering onto it would be ritual rather than useful structure.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Flat/layered structure (controller/service/repository packages) | No enforced boundary between business logic and framework/persistence concerns; invariants can be bypassed without the compiler noticing |
| Clean Architecture (concentric layers) | Solves a similar problem, but hexagonal's ports-and-adapters framing maps more directly onto the project's existing repository-interface pattern, so it was the more natural extension of decisions already made |
| Full hexagonal applied uniformly to every module, including `core` and `analytics` | `core` has no domain layer to protect (pure technical utilities); `analytics` has no significant invariants (simple CRUD) — the layering would add ceremony without benefit in either case |

## Consequences

- Domain packages in platform's modules should not import Spring/JPA types directly; persistence concerns are pushed into infrastructure-layer adapters implementing domain-defined repository interfaces.
- Business rules and invariants belong inside aggregate roots/entities (rich domain model), not in anemic service classes.
- This adds some structural overhead per module (more packages, explicit port interfaces) that is deliberately not applied to `core` or `analytics`, where it wouldn't pay for itself.
- Because this was adopted experimentally rather than dogmatically, friction points discovered while building the real project may lead to a narrower or adjusted application of hexagonal later (e.g. relaxing strict domain/infrastructure separation on simpler aggregates) — this ADR should be revisited if that happens.
</content>
