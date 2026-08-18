# Event Ticketing specifications

A complete specification of the event ticketing platform, **written greenfield**: every
spec describes a capability to be built from zero on the stack pinned in
[CONVENTIONS.md](CONVENTIONS.md) — Java 21, Spring Boot 3.5.4 WebFlux, Netflix DGS 10.5
over Apollo Federation 2.9, reactive MongoDB, Azure Service Bus and Keycloak 26.

Specs describe the target system, not the current one. They make no reference to what
exists today, carry no migration tasks, and cite no line numbers in the working tree. A
reconciliation pass against the existing code comes after the corpus is agreed — see
[Reconciliation](#reconciliation).

---

## The unit

One capability per directory, two files:

```
specs/<area>/<NNN>-<slug>/
├── spec.yaml    machine-readable header — the contract with any orchestrator
└── spec.md      capability, decisions, requirements, model, tasks
```

`spec.md` has six fixed sections, in this order, in every spec.

| § | Section | Contains |
|---|---|---|
| 1 | **Capability** | What this delivers, in domain terms, and why it exists |
| 2 | **Design decisions** | The calls made and why — and the alternatives that were rejected |
| 3 | **Requirements** | EARS statements with mechanically checkable acceptance criteria |
| 4 | **Model** | The actual contract: documents, indexes, events, topics, schema, codes |
| 5 | **Tasks** | The build order — this is the fan-out list a workflow iterates |
| 6 | **Out of scope** | Named, with the spec that covers it if there is one |

§4 is the section that makes a spec buildable. It names every collection, every index,
every event type and its wire name, every GraphQL operation, every Redis key with its
TTL, and every error code. **An implementing agent should not have to invent a name.**

### Why two files, not four

The Kiro-style split (`requirements.md` / `design.md` / `tasks.md`) serves a human review
gate between stages. Here the reader is usually an agent with one shot at loading
context, and three files drift against each other the moment anything changes. One
document with fixed headings is cheaper to read, cheaper to keep true, and just as
parseable. `spec.yaml` stays separate because shell and JS parse it in one line.

---

## IDs

`ET-<AREA>-<NNN>`; requirements append `-R<n>`, e.g. `ET-FIN-002-R3`.

| Area | Domain | Owning service | Subgraph |
|---|---|---|---|
| `PLT` | Platform, cross-cutting | all | all |
| `IDN` | Identity, authentication, OTP, Keycloak | identity-service, keycloak-extensions | identity |
| `ORG` | Organizations, teams, permissions | identity-service | identity |
| `CAT` | Events, categories, locations, tiers | catalog-service | catalog |
| `TKT` | Tickets, reservations, validation, transfer | booking-service | booking |
| `PAY` | Payment capture, providers, webhooks | booking-service | booking |
| `FIN` | Escrow, ledger, commission, payouts, refunds | booking-service | booking |
| `NTF` | Notifications and messaging | identity-service | identity |
| `ADM` | Platform administration and operations | all | all |

Numbers are allocated sequentially per area and never reused. A withdrawn spec keeps its
number.

---

## Status lifecycle

```
draft ──▶ approved ──▶ in-progress ──▶ implemented ──▶ verified
                 └──▶ deferred          └──▶ withdrawn
```

**approved** is the gate before building. **verified** means every acceptance checkbox is
ticked *and* backed by a test tagged with the spec ID.

A spec claiming `verified` while acceptance boxes remain unchecked is the failure mode this
lifecycle exists to prevent. Nothing enforces it mechanically — the boxes are checked by
the person who read the spec and ran its tests, and `verified` is their statement that both
happened. That makes honesty about an unchecked box the whole safeguard.

---

## Traceability

The spec ID appears in two places, which makes coverage a grep rather than a judgement
call.

1. **The spec** — `specs/finance/002-commission/`
2. **The test** — `@Tag("ET-FIN-002")` on the class; each method's `@DisplayName` names the
   requirement it proves, e.g. `ET-FIN-002-R3`

```bash
grep -rn "ET-FIN-002" backend --include='*.java'    # what mentions it
mvn -q -f backend/booking-service test -Dgroups=ET-FIN-002 -DfailIfNoTests=true
```

`-DfailIfNoTests=true` is not optional. Without it a tag that matches nothing exits 0, and
the spec verifies green having executed no tests at all — which is worse than not running
them, because it looks like proof.

---

## Authoring rules

1. **Write greenfield.** Describe the target. No "currently", no "today the code…", no
   file:line citations into the working tree, no deletion tasks. The one exception is
   [CONVENTIONS.md](CONVENTIONS.md), whose *Repo today* columns exist precisely so the
   specs never need them.
2. **Requirements in EARS.** `WHEN` / `WHILE` / `WHERE` / `IF … THEN` + `THE SYSTEM SHALL`.
3. **Every requirement carries acceptance checkboxes** that are mechanically checkable.
   "The payout is correct" is not; "`PayoutRequest.netAmount` equals
   `grossAmount − commission − processingFee`, asserted at K0.01" is.
4. **§4 names everything.** Collection names, index definitions, event wire names, topic
   names, GraphQL operations, Redis keys with TTLs, error codes. No invention downstream.
5. **4–8 requirements per spec, and roughly 40 acceptance boxes.** More than eight
   requirements means it is two specs. Past about fifty boxes, check what the extra ones
   are asserting: a spec drifts long when it re-specifies something the framework,
   Keycloak, or another spec already decides. Boxes that restate a vendor default are
   boxes that go stale silently.
6. **Every construct comes from [CONVENTIONS.md](CONVENTIONS.md).** A spec proposing a
   blocking call, a second permission resolver or a per-service database is wrong, not a
   variation.
7. **Record rejected alternatives.** One line each. This is what stops the next agent
   re-litigating a settled decision.
8. **Money statements cite `docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md`.** It is
   authoritative on the financial model; a spec that contradicts it is the spec that is
   wrong.

## Precedence

1. `specs/` — for anything `approved` or later
2. [CONVENTIONS.md](CONVENTIONS.md) — on any construct, naming or stack question
3. `docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` — on any money question
4. `docs/PAYMENT_DATA_INTEGRITY_IMPLEMENTATION_PLAN.md` — on transactional integrity
5. `docs/USER_STORIES.md` — on roles, permissions and organizational hierarchy
6. `CLAUDE.md`, then the rest of `docs/`

---

## How a workflow consumes this

`spec.yaml` is the contract between the spec layer and the orchestrator:

- `status` gates whether an agent may build it
- §5 Tasks is the fan-out list
- `depends` and `parallel-safe` on each task decide `pipeline()` vs barrier vs
  `isolation: 'worktree'`
- `verify:` is executed verbatim by the verification stage
- `blocked_by` is a hard edge between specs — a workflow must not start a spec whose
  blockers are not `implemented`

Workflows run only on explicit opt-in — say "ultracode", ask for one in your own words,
or name it.

---

## Reconciliation

The corpus is written as though nothing exists. Substantial parts of the platform are in
fact already built — three services, ~830 production classes, ~8,000 lines of subgraph
schema — and some of it already matches these specs.

Reconciliation is a **separate, later pass**, run once the corpus is `approved`. It
audits each spec against the tree and classifies every requirement as
`already-satisfied`, `partially-satisfied`, `contradicted` or `absent`. Only then does
implementation planning happen, against real deltas rather than assumptions.

Doing it this way round is deliberate. A spec written by reading existing code inherits
that code's decisions, including its mistakes — the target has to be described
independently before the gap to it can be measured honestly. The existing gap documents
(`BACKEND_GAP_ANALYSIS_REPORT.md`, `STUB_TYPES_ANALYSIS.md`) are inputs to that pass, not
to the specs.

---

## Infrastructure lives elsewhere

Docker Compose, the Apollo Router configuration, the supergraph, Keycloak realm exports,
MongoDB and PostgreSQL initialisation are all in the sibling repository:

```
../docker-resources/
├── apollo-router/ticketing/     router-local.yaml, router-graphos.yaml,
│                                router-production.yaml, supergraph.yaml,
│                                supergraph-static.yaml, compose-supergraph.sh
├── keycloak/  mongodb/  postgres/  prometheus/  grafana/  servicebus/
└── docker-compose.yml
```

A spec that proposes creating any of these inside `ticketing-system/` is wrong. What
stays in this repository is `backend/*/src/main/resources/graphql/schema.graphqls`,
and `specs/`.

---

## Index

[ROADMAP.md](ROADMAP.md) — the full corpus, build order, and the decisions taken while
writing it.
