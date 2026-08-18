# Event Ticketing · specification corpus and build order

A greenfield specification of the platform on **Java 21 / Spring Boot 3.5.4 WebFlux,
Netflix DGS 10.5 over Apollo Federation 2.9, reactive MongoDB,
Azure Service Bus and Keycloak 26**, using the constructs in [CONVENTIONS.md](CONVENTIONS.md).

Written 2026-07-30 against `docs/USER_STORIES.md` v3.0 (roles and hierarchy),
`docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` (the financial model),
`docs/PAYMENT_DATA_INTEGRITY_IMPLEMENTATION_PLAN.md` (transactional integrity), and the
architecture documents in `docs/architecture/`.

---

## Ground rules for this corpus

**Greenfield.** Every spec describes the target system. None references what exists
today. Reconciliation against the working tree is a separate pass — see
[README §Reconciliation](README.md#reconciliation).

**The stack is settled and is not a spec's decision to make.** Three services, three
subgraphs, one MongoDB, one router, one Keycloak realm. A spec that wants a fourth
service, a second broker or a per-service database is proposing a platform change and
belongs in `_platform/`, with the argument written down.

**Every construct is from CONVENTIONS.md.** Fully reactive signatures, `Instant` from an
injected `Clock`, `BigDecimal` money in a double-entry ledger, two-tier eventing with the
bus never inside a transaction, federation ownership with `@tag` contracts, one
permission resolver, typed errors with registry codes. Anything else is a defect, not a
variation.

---

## Decisions taken while writing this corpus

| # | Question | Decision |
|---|---|---|
| **D-01** | MongoDB topology | **Replica set in every environment**, single-node in development. Reservations, escrow movements and journal pairs are multi-document writes; against a standalone `mongod` `@Transactional` is silently inert and the platform oversells under load |
| **D-02** | One datastore per service | **MongoDB only.** No service connects to PostgreSQL: Keycloak runs its own schema and nothing else relational exists. A blocking JDBC pool inside a strictly reactive stack has to earn its place, and once the outbox lives in MongoDB there is nothing left for it to do |
| **D-03** | Eventing tiers | **In-process events inside a service, Azure Service Bus between services, and a MongoDB outbox between the two.** The outbox row is staged in the same reactive transaction as the document, so the write and the intent to publish cannot disagree; a scheduled drain reaches the bus afterwards. `StreamBridge` is never called inside a transaction |
| **D-04** | Commission model | **Two-stage: pending at purchase, recognised at event completion.** Money owed on a ticket for an event that is later cancelled was never revenue, and a platform that books it at purchase reports a profit it must then reverse |
| **D-05** | Escrow granularity | **One escrow account per event**, not per organizer. Cancelling one event must not reach into another event's settled funds; and a per-event balance is what makes a refund obligation computable |
| **D-06** | Payment provider | **PawaPay only at launch, behind a `PaymentProviderPort`.** MTN, Airtel and Zamtel reach the platform through one aggregator; the port exists so the second aggregator is an adapter, not a rewrite |
| **D-07** | Idempotency | **Every money-moving mutation takes a client-supplied `idempotencyKey`**, guarded in Redis with a 24-hour TTL and by a unique index on the persisted attempt. Mobile networks retry; a double charge is not recoverable by apologising |
| **D-08** | Inventory | **One conditional atomic update per reservation**, `findAndModify` filtering on `available >= quantity`, plus optimistic `@Version`. Read-modify-write oversells at on-sale and only at on-sale, which is when it matters |
| **D-09** | Reservation model | **Tickets are reserved before they are paid for**, with a 10-minute TTL swept by a scheduled job. Mobile-money confirmation takes tens of seconds and sometimes minutes; holding inventory during it is the difference between a sale and a race |
| **D-10** | Permission resolution | **One resolver, one order**: platform role → event grant → organization role → custom → denied → deny. Explicit deny beats inherited allow. Written once, in identity-service, consumed by the other two over the internal API |
| **D-11** | File uploads | **REST with presigned URLs, never GraphQL multipart.** Streams to storage, no CSRF surface, native progress, and the server never buffers the file |
| **D-12** | Dashboard freshness | **Smart polling, not GraphQL subscriptions.** Apollo Router's managed federation does not carry subscriptions on the path this platform uses; polling with a visibility-aware interval is honest about that rather than half-building a transport |
| **D-13** | Statistics | **Server-side MongoDB aggregation with `$match` first**, one pipeline per stat type, never client-side counting over a fetched page |
| **D-14** | Currency | **ZMW only at launch**, but stored on every monetary field. Single-currency assumptions are cheap to make and expensive to remove |
| **D-15** | Notification channels | **WhatsApp, SMS, push and email.** WhatsApp is primary in-market and carries the OTP that is the login mechanism; SMS is its fallback; email is for receipts and the invitation flow, which needs a durable addressable identity |
| **D-17** | Permission vocabulary | **`module:action` — flat, exactly one colon, no dots — a closed catalogue declared in code, with `manage` implying CRUD and any write implying read, and no third rule.** A finer subject is its own module (`ticket_tiers`, `ticket_qr`, `bank_accounts`), never a dotted path, so `split(':')` is the whole parser. Keycloak owns roles; MongoDB owns the role→permission mapping as **one document per role** — no junction collection, no view, no `$lookup` on the authorization path. A platform administrator edits the mapping; nobody invents a permission, because a key nothing enforces is protection that does not exist |
| **D-18** | Reference data | **One polymorphic collection, compiled types and runtime rows.** Workflow statuses are reflected out of the code's own enums rather than seeded, so the value list cannot drift; each carries a coarse `WorkflowSemantic`, and code branches on the semantic, never on the code string, so a new status needs no deployment |
| **D-16** | Target scale | **200,000 tickets/month, with an on-sale peak of 5,000 reservations/minute against a single event.** Every capacity requirement in this corpus is sized against that second figure, because the first never breaks anything |

---

## Build order

Eight waves. Each wave is buildable once the previous is `implemented`; within a wave,
specs are largely independent and can fan out.

### Wave 0 · Platform foundation

The runtime, the contracts every later spec depends on, and the test harness. Nothing
below is optional and nothing after it is safe to sequence first.

| ID | Title | Conformance |
|---|---|---|
| [ET-PLT-012](_platform/012-build-topology/) | Build topology — the parent POM, the BOM set, the reactor, the enforcer | PDI §1 |
| [ET-PLT-001](_platform/001-runtime-baseline/) | Runtime baseline — reactive contract, `Clock`, module boundaries, service topology | PDI §1 |
| [ET-PLT-002](_platform/002-persistence-baseline/) | Persistence baseline — replica set, collection and index registry, money and time types | PDI §1, §4 |
| [ET-PLT-003](_platform/003-event-contract/) | Event contract — two tiers, envelope, outbox, topics, idempotent consumers, DLQ | PDI §3, §8 |
| [ET-PLT-004](_platform/004-federation-contract/) | Federation contract — ownership, keys, stubs, `@tag` contracts, composition gate | — |
| [ET-PLT-005](_platform/005-error-contract/) | Error contract — the closed code registry, typed handlers, retryability | — |
| [ET-PLT-006](_platform/006-test-harness/) | Five-layer test harness — Testcontainers, WireMock, frozen `Clock` | PDI §9 |

### Wave 1 · Identity and access

| ID | Title |
|---|---|
| [ET-PLT-007](_platform/007-security-and-authorization/) | Keycloak realm, roles, `@auth`, internal scopes, idempotency keys, tenant scoping |
| [ET-PLT-013](_platform/013-permission-engine/) | The permission engine — flat `module:action` catalogue, role→permission mapping, evaluation |
| [ET-IDN-001](identity/001-phone-otp-identity/) | Phone-OTP passwordless identity — the Keycloak SPI and the OTP lifecycle |
| [ET-IDN-002](identity/002-keycloak-user-sync/) | Keycloak ↔ MongoDB user synchronisation, drift detection and recovery |
| [ET-IDN-003](identity/003-token-revocation/) | Token revocation — `jti`/`sid`/`sub`, the fail-closed check, platform-wide propagation |
| [ET-ORG-001](organization/001-organizer-onboarding/) | Organizer application — **nine states**, documents, staged access, approval |
| [ET-ORG-002](organization/002-teams-and-invitations/) | Organization members, invitations, ownership transfer |
| [ET-ORG-003](organization/003-permission-resolution/) | The three-tier permission resolver and event access grants |

### Wave 2 · The catalogue

| ID | Title |
|---|---|
| [ET-CAT-001](catalog/001-event-lifecycle/) | Event lifecycle — the state machine, approval, publish, reschedule, cancel |
| [ET-CAT-002](catalog/002-ticket-tiers-and-inventory/) | Ticket tiers, capacity, sales windows, the authoritative inventory count |
| [ET-PLT-014](_platform/014-reference-data-engine/) | The reference data engine — enum-derived, administrator-owned lookups and statuses |
| [ET-CAT-003](catalog/003-locations-and-reference-data/) | Provinces, cities, venues, categories, discovery and search |

### Wave 3 · The purchase loop

| ID | Title |
|---|---|
| [ET-TKT-001](ticketing/001-reservation-and-hold/) | Reservation, atomic hold, TTL expiry, the purchase saga's first half |
| [ET-PAY-001](payment/001-payment-intents-and-providers/) | Payment intents, the provider port, the PawaPay adapter, idempotency |
| [ET-PAY-002](payment/002-webhooks-and-settlement/) | Webhook signature verification, replay defence, provider reconciliation |
| [ET-TKT-002](ticketing/002-ticket-issuance-and-qr/) | Ticket issuance, QR signing, delivery and re-issue |
| [ET-FIN-001](finance/001-escrow-and-ledger/) | Per-event escrow accounts, chart of accounts, double-entry journal |

### Wave 4 · Money out

| ID | Title |
|---|---|
| [ET-FIN-002](finance/002-commission/) | Two-stage commission, rate resolution, recognition at event completion |
| [ET-FIN-003](finance/003-payouts-and-settlement/) | Payout eligibility, request lifecycle, bank accounts, settlement saga |
| [ET-FIN-004](finance/004-refunds-and-chargebacks/) | Refund policy and fees, event-cancellation refunds, chargeback handling |
| [ET-FIN-005](finance/005-reconciliation/) | Provider reconciliation, ledger-to-balance proof, financial close |

### Wave 5 · At the venue, and after

| ID | Title |
|---|---|
| [ET-TKT-003](ticketing/003-validation-and-checkin/) | QR validation, offline scanning, duplicate-scan defence, check-in reporting |
| [ET-TKT-004](ticketing/004-transfer-and-resale/) | Ticket transfer between users, and controlled resale |
| [ET-NTF-001](notification/001-notification-transport/) | Channels, templates, devices, preferences, delivery outcomes |
| [ET-NTF-002](notification/002-lifecycle-triggers/) | Which fact produces which message, to whom, on which channel |

### Wave 6 · Operations

| ID | Title |
|---|---|
| [ET-ADM-001](admin/001-approvals-workbench/) | Organizer, event and document approval queues, SLA and escalation |
| [ET-ADM-002](admin/002-platform-configuration/) | Market configuration, commission defaults, feature flags, versioned config |
| [ET-ADM-003](admin/003-transaction-recovery/) | Stuck transactions, payout and escrow lifecycle operations, bulk retry |
| [ET-ADM-004](admin/004-analytics-and-statistics/) | Dashboard aggregations across all three services, and the polling contract |
| [ET-ADM-005](admin/005-observability-and-health/) | Metrics, tracing, correlation IDs, SLOs, system health and alerting |

### Wave 7 · Scale and compliance

| ID | Title |
|---|---|
| [ET-PLT-008](_platform/008-data-protection/) | PII inventory, GDPR erasure, the 30-day grace period, anonymised retention |
| [ET-PLT-009](_platform/009-audit-trail/) | The immutable audit log, what must be recorded, and who may read it |
| [ET-PLT-010](_platform/010-schema-evolution/) | Event and GraphQL schema versioning, upcasting, deprecation windows |
| [ET-PLT-011](_platform/011-rate-limiting-and-abuse/) | Rate limits, on-sale queueing, bot defence, OTP abuse control |

**41 specs, all authored and `approved`.** Every one carries the six sections, 7–8 EARS requirements with
acceptance boxes, a §4 model that names every collection, index, event, operation and
code, and a §5 task list with `depends` and `parallel-safe` on each task.

The dependency graph is a DAG — 180 edges, no cycles, and no spec is blocked by one in a
later wave, so the wave order below is executable as written.

---

## Conformance coverage

Two documents in `docs/` are treated as external authorities this corpus must satisfy in
full. Each stop is mapped to the spec that covers it.

### `ARCHITECTURE_REDESIGN_V3_COMPLETE.md` — the financial model

| § | Topic | Spec |
|---|---|---|
| 2 | Commission structure and profit model | ET-FIN-002 |
| 3 | Account types and fund flow | ET-FIN-001 |
| 4 | Complete user journeys | ET-TKT-001, ET-ORG-001 |
| 5 | Event lifecycle state machine | ET-CAT-001 |
| 7 | MongoDB collections per service | ET-PLT-002 §4 registry |
| 8 | Payment integration and consistency | ET-PAY-001, ET-PAY-002 |
| 9 | Rescheduling and cancellation | ET-CAT-001, ET-FIN-004 |
| 10 | Payout and settlement rules | ET-FIN-003 |
| 11 | Transaction tracking and terminology | ET-FIN-001, ET-FIN-005 |
| 12 | Settlement process | ET-FIN-003, ET-FIN-005 |
| 13 | Escrow vs non-escrow account classification | ET-FIN-001 |
| 14 | Refund processing fees | ET-FIN-004 |

### `PAYMENT_DATA_INTEGRITY_IMPLEMENTATION_PLAN.md` — transactional integrity

| Phase | Topic | Spec |
|---|---|---|
| 1 | MongoDB replica set and transactions | ET-PLT-002 |
| 2 | Transaction architecture, reservation model | ET-TKT-001 |
| 3 | Atomic outbox pattern | ET-PLT-003 |
| 4 | Optimistic locking and concurrency | ET-PLT-002, ET-CAT-002 |
| 5 | Payment idempotency | ET-PAY-001, ET-PLT-007 |
| 6 | Webhook hardening | ET-PAY-002 |
| 7 | Saga state machine | ET-TKT-001, ET-FIN-003 |
| 8 | Dead-letter queue and recovery | ET-PLT-003, ET-ADM-003 |
| 9 | Monitoring and alerting | ET-ADM-005 |

### `USER_STORIES.md` v3.0 — roles and hierarchy

| Part | Topic | Spec |
|---|---|---|
| I §1–3 | Platform, organization and event role hierarchies | ET-ORG-003 |
| I §4 | Organizer onboarding stages | ET-ORG-001 |
| I §5 | Team management and invitations | ET-ORG-002 |
| I §6 | Keycloak integration architecture | ET-IDN-002 |
| II §7–12 | CRUD operations and business logic | ET-ORG-001, ET-ORG-002 |
| III §13–19 | End-user stories by role | mapped per spec in each §1 |
| IV §20 | Saga orchestration | ET-ORG-001, ET-TKT-001, ET-FIN-003 |
| IV §21 | State machines | ET-ORG-001, ET-ORG-002, ET-CAT-001 |
| IV §22 | Permission resolution algorithm | ET-ORG-003 |
| V §23–25 | Collections, documents, indexes | ET-PLT-002 §4 |

---

## Cross-cutting properties

Properties no single spec owns, asserted across several. Each is listed here so it cannot
fall between specs.

| Property | Asserted by |
|---|---|
| No blocking call ever runs on an event-loop thread | ET-PLT-001, and lint |
| Every timestamp comes from the injected `Clock` | ET-PLT-001, and lint |
| No business document lives in PostgreSQL or Redis | ET-PLT-002, and lint |
| An event can never be sold beyond its capacity | ET-CAT-002, ET-TKT-001 |
| No message is published to the bus inside a transaction | ET-PLT-003, and lint |
| Every cross-service consumer is idempotent on `eventId` | ET-PLT-003 |
| No balance is written except as a double-entry pair | ET-FIN-001, and lint |
| The ledger and every cached balance reconcile | ET-FIN-005 |
| A refused operation persists nothing | ET-PLT-006 — required on every refusal test |
| Every money-moving mutation is idempotent under retry | ET-PLT-007, ET-PAY-001 |
| Permission is resolved in exactly one implementation | ET-ORG-003, and lint |
| No tenant's data is reachable through another tenant's query | ET-PLT-007, ET-ORG-003 |
| Every admin-only schema field is `@tag`ged | ET-PLT-004, and composition |
| Every domain refusal has a registry code and a typed handler | ET-PLT-005, and lint |
| No personal data leaves the platform in an event payload | ET-PLT-008 |
