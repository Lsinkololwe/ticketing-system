# ET-PLT-002 · Persistence baseline — replica set, the collection registry, money and time

> **Conformance** · PDI Phase 1 replica set · PDI Phase 4 optimistic locking · V3 §7 collections

## 1. Capability

Three services share one MongoDB database and write documents that must never disagree
with each other: a ticket exists because a reservation was paid for, an escrow balance is
what the journal says it is, an organization member's role is what Keycloak's groups
mirror. Getting that right is mostly a question of what can be written atomically, and
MongoDB answers that question differently depending on how it was deployed — which is why
the topology is a specification and not an operations detail.

This spec fixes the storage substrate. It declares the replica set that makes multi-document
transactions possible at all, the single database and the prefix discipline that keeps
ownership legible, the closed registry of every collection and every index the platform
runs, the three types that are not negotiable — `BigDecimal`/`Decimal128` for money,
`Instant` for time, `@Version` for anything carrying a balance or a count — and the one
write shape that stops an event overselling.

It also draws the line the platform's storage depends on. **One store holds business
state, and it is MongoDB.** No service connects to PostgreSQL at all — Keycloak runs its
own schema, on its own instance, and no service reaches it. Redis exists for exactly four
things: OTP codes, idempotency guards, distributed locks, and caches — every key with a
TTL, and never a fact the platform cannot recompute.

It delivers no domain behaviour. Its success criterion is that a multi-document
transaction commits and rolls back correctly, that every collection the platform will
ever write is named here with its indexes, and that a concurrent decrement of one ticket
tier by two hundred callers sells exactly the available quantity and no more.

## 2. Design decisions

**A replica set in every environment, single-node in development.** MongoDB supports
multi-document transactions only on a replica set. Against a standalone `mongod`,
`@Transactional` on a reactive method does not fail — it is silently inert, every write
commits independently, and a reservation whose payment intent failed to persist becomes
inventory that nobody can buy and nobody can release. A one-node replica set costs a
keyfile and an `rs.initiate()`; the alternative costs correctness under exactly the load
the platform is built for.

**One database, `ticketing`; collections prefixed by their owning service.** A
per-service database would make the prefix redundant and the join impossible, but it
would also make an aggregation across booking and catalog a two-connection problem for no
gain — these services are one deployment unit's worth of data. The prefix is what keeps
ownership legible: `booking_tickets` is booking's, and a query for it from catalog-service
is a federation violation wearing a driver. **No service writes another service's
collections**, and none reads them either — it asks over the graph or listens for the event —
with two named exceptions, the platform's shared engines: `catalog_reference_data` and
`catalog_platform_configuration`. Each is one table for the whole platform with one writer
(catalog), and every other service reads it directly, read-only, through a shared-library
reader (`StatusSemanticResolver`, `PlatformConfigurationReader`). *(Amended 2026-09-19 — see
the platform settings note below.)*

**The collection registry in §4 is closed.** Every `@Document` names a row of it. A new
collection is a change to this spec, in the same commit as the code, because a collection
that appears without its indexes is a collection that is discovered by a production
outage. The same is true of the index registry: an index added by hand in a shell is an
index that exists in one environment.

**Money is `BigDecimal` in Java and `Decimal128` in MongoDB.** `double` cannot represent
K0.10, and a platform whose entire purpose is moving other people's money does not get to
be approximately right. Every monetary field carries its currency alongside it — `ZMW`
today (D-14), but stored, because single-currency assumptions are cheap to make and
expensive to remove. Rounding is `HALF_UP` at two decimal places, applied once, at
persistence.

**Time is `Instant`, everywhere, with no exceptions.** `LocalDateTime` has no zone, which
means a settlement window that closes at midnight is decided by whichever host ran the
job; `java.util.Date` is mutable; an epoch `long` is untyped. The value is read from the
`Clock` bean of [ET-PLT-001](../001-runtime-baseline/) and stored as `Instant`;
presentation in a user's zone happens at the edge, in the client.

**Optimistic locking on anything that carries a balance or a count.** `@Version` on
escrow accounts, platform accounts, tier inventory and reservations. At on-sale peak, two
concurrent writes to the same inventory document is the ordinary case, not the edge case,
and last-write-wins on a counter is an oversold event.

**Tier *definition* is catalog's; tier *inventory* is booking's.** This is the one place
the ownership rule would otherwise force a network hop onto the hottest path in the
platform. `catalog_ticket_tiers` holds what the organizer authored — name, price,
capacity, sales window — and changes rarely. `booking_tier_inventory` holds the three
counters that move five thousand times a minute, and booking owns them because booking is
what reserves, sells and releases. One inventory document is created per tier from
`catalog.TicketTierPublished`, and a capacity change arrives as
`catalog.TicketTierCapacityChanged` rather than as a write from another service. The
alternative — calling catalog-service synchronously to hold inventory — puts an HTTP round
trip inside the reservation transaction, which is the one transaction that must be short.

**Inventory moves by one conditional atomic update, never read-modify-write.** A
`findAndModify` whose filter carries `available >= quantity` and whose update decrements
in the same round trip is the only oversell defence that holds without a lock, because
the filter and the update are evaluated together by the server. Reading the tier,
checking in Java, and writing back is correct in a test and wrong at five thousand
requests a minute.

**Ids: MongoDB `ObjectId` as a string, except users.** `User.id` **is** the Keycloak user
ID — the UUID Keycloak minted — and there is no second `keycloakUserId` field. One
identity, one column; the redundant field is where the two eventually disagree. Every
other document takes a generated `ObjectId` rendered as a `String`, and every foreign key
follows `{entity}Id`.

**Nothing is hard-deleted that a person or an auditor may ask about later.** Users,
organizations, events and tickets carry a status that includes their terminal states;
deletion is a status transition plus, where the law requires it, anonymisation
([ET-PLT-008](../008-data-protection/)). Reference data and expired invitations may be
removed, and TTL indexes do it.

**No service touches PostgreSQL at all.** Keycloak runs its own schema on its own
instance, and that is the whole of PostgreSQL's role in this platform. No service declares
a datasource, a JDBC driver, a connection pool or a JPA entity. A blocking pool inside a
strictly reactive stack is a liability that has to earn its place, and once the outbox
lives in MongoDB ([ET-PLT-003](../003-event-contract/)) nothing is left for it to do.

**Redis holds four things, each with a TTL.** OTP codes, idempotency guards, distributed
locks, and read-through caches. It holds **no business state**: a TTL expiry must never
be able to destroy a fact, and Redis cannot participate in a Mongo transaction, so a
write that lands in one and not the other is a write nobody can reconcile.

**The stored type discriminator is an alias, never a class name.** Spring Data MongoDB
writes the fully-qualified class name into a `_class` field on every document by default.
That silently makes the Java package structure part of the persisted schema: renaming a
package, moving a document class, or promoting it to `shared-library` rewrites data that
was never meant to change, and the documents already written no longer deserialise into
the class that now owns them. Every `@Document` therefore carries `@TypeAlias` with a
short, stable name that is chosen once and never changed. The alias is a storage
identifier that happens to start life matching a class, not a pointer to one.

```java
@Document(collection = "booking_escrow_accounts")
@TypeAlias("escrowAccount")          // stable; survives every package move
public class EscrowAccount { … }
```

**Rejected alternatives**

- *A database per service.* Three connection strings, three backup policies and three replica sets, to enforce a boundary a collection prefix and a lint rule already enforce.
- *Removing the type discriminator entirely, with a `DefaultMongoTypeMapper(null)`.* Tempting for single-type collections and genuinely smaller on disk, but it removes the platform's only defence against a collection ever holding a second shape, and it fails silently rather than loudly the day one does. The alias costs a dozen bytes and keeps the option.
- *Leaving `_class` at its default and rewriting it in a migration when a class moves.* A data migration triggered by a refactor is a refactor nobody performs, so the packages ossify instead.
- *A standalone `mongod` in development, replica set only in production.* Guarantees that the class of bug transactions exist to prevent is invisible until staging.
- *`long` minor units (ngwee) instead of `BigDecimal`.* Correct, and it makes every commission percentage a rounding argument at the call site instead of once at persistence.
- *Pessimistic locking on ticket tiers.* Serialises the on-sale minute — the one minute that must not serialise.
- *An application-level sequence for ticket numbers.* A second document to contend on, and a single point of contention per event; ticket identity is the `ObjectId` and the human-facing reference is derived from it.
- *An outbox in PostgreSQL, alongside the reactive MongoDB write.* Two transaction managers that cannot enlist together, so the document and the event row are not atomic — which is the entire guarantee an outbox exists to provide. It also puts a blocking JDBC pool inside a reactive stack. See [ET-PLT-003](../003-event-contract/).
- *Draining the MongoDB outbox with Debezium.* Kafka Connect, a connector and an oplog dependency, where the drain's indexed poll suffices at this volume.
- *Soft-delete flags (`deleted: true`) alongside a status enum.* Two fields answering one question, and every query then needs both predicates or it is wrong.

## 3. Requirements

### ET-PLT-002-R1 · Transactions are possible, and they are used where writes must agree

THE SYSTEM SHALL run MongoDB as a replica set in every environment, and WHEN a business
operation writes more than one document that must agree, THE SYSTEM SHALL perform those
writes in one transaction.

**Acceptance**
- [ ] `MONGODB_URI` names a replica set (`?replicaSet=rs0`) in every profile, single-node in development
- [ ] A `ReactiveMongoTransactionManager` bean is declared over the `ticketing` database
- [ ] An integration test writes two documents in one `@Transactional` method, forces a failure after the first, and observes neither present
- [ ] The same test against a standalone `mongod` fails — the suite proves the topology, not just the annotation
- [ ] A reservation and its payment intent are written in one transaction; a ticket and its escrow credit are written in one transaction
- [ ] No `@Transactional` method spans two datastores — there is only one, so the dual-write window does not exist to be reasoned about

### ET-PLT-002-R2 · Every collection is a row of the registry, owned by exactly one service

THE SYSTEM SHALL persist business documents only to collections named in §4, and IF a
service reads a collection it does not own, THEN the build SHALL fail.

**Acceptance**
- [ ] Every `@Document` in every service names a row of the §4 collection registry
- [ ] Every collection name is prefixed `catalog_`, `booking_` or `identity_` and the prefix matches the declaring service
- [ ] No service declares a `@Document` or repository for a collection whose registry row names another service
- [ ] No service holds a `MongoTemplate` call naming another service's collection as a string
- [ ] Every `@Document` names a row of the ET-PLT-002 §4 registry and carries `@TypeAlias`; no document field is `LocalDateTime` or `java.util.Date`; every balance-bearing document declares `@Version`
- [ ] Adding a collection changes this spec's §4 in the same commit as the `@Document`

### ET-PLT-002-R3 · Every index the platform runs is declared, and none is created by hand

THE SYSTEM SHALL declare every index in §4, create them at startup from that declaration,
and SHALL NOT rely on any index created outside it.

**Acceptance**
- [ ] Every index in the §4 index registry is declared as `@Indexed`/`@CompoundIndex` on the document or in a `MongoIndexInitializer`
- [ ] `auto-index-creation` is explicit in configuration rather than inherited
- [ ] Every uniqueness constraint in §4 is a `unique: true` index, not an application-level check — a check-then-write races
- [ ] Every TTL rule in §4 is an `expireAfterSeconds` index
- [ ] An integration test asserts the live index set of each collection equals its registry rows
- [ ] `explain()` on each of the five hot queries named in §4 reports `IXSCAN`, never `COLLSCAN`

### ET-PLT-002-R4 · Money is exact, carries its currency, and rounds once

THE SYSTEM SHALL represent every monetary value as `BigDecimal` in Java and `Decimal128`
in MongoDB, and SHALL store the currency alongside it.

**Acceptance**
- [ ] No field named for an amount, balance, price, fee, total or commission is `double`, `float`, `Double` or `Float` anywhere in production code
- [ ] Every monetary field persists as `Decimal128` — a `BigDecimal`-to-`Decimal128` converter is registered and a round-trip test proves `K1234.56` survives unchanged
- [ ] Every document carrying a monetary field also carries a `currency` field, defaulting to `ZMW`
- [ ] Rounding is `RoundingMode.HALF_UP` at scale 2, applied at persistence and nowhere else — no intermediate calculation rounds
- [ ] No production field typed `double` or `float` names an amount, balance, price, fee, total or commission; every rounding is `HALF_UP`; no balance is assigned outside the ledger

### ET-PLT-002-R5 · Time is `Instant`, from the clock, in UTC

THE SYSTEM SHALL persist every timestamp as `Instant` sourced from the application
`Clock`.

**Acceptance**
- [ ] No document field is `LocalDateTime`, `LocalDate`, `java.util.Date` or an epoch `long`
- [ ] Spring Data auditing (`@CreatedDate`, `@LastModifiedDate`) is backed by a `DateTimeProvider` reading the same `Clock` bean, so a frozen test clock freezes audit fields too
- [ ] Action timestamps follow the `{pastTense}At` convention of §4 — `submittedAt`, `approvedAt`, `expiresAt`, `settledAt`
- [ ] A document written under a frozen clock carries exactly the frozen instant in `createdAt`
- [ ] A search of production source finds no inline `Instant.now()`, `LocalDateTime.now()`, `LocalDate.now()`, `ZonedDateTime.now()` or `System.currentTimeMillis()`

### ET-PLT-002-R6 · Contended documents are version-locked, and inventory moves atomically

WHILE two writers contend for one document that carries a balance or a count, THE SYSTEM
SHALL detect the conflict rather than lose a write, and inventory SHALL move by one
conditional atomic update.

**Acceptance**
- [ ] `@Version` is present on every document in §4 marked *versioned* — escrow accounts, platform accounts, tier inventory, reservations, payment intents, payout requests, promo codes
- [ ] A concurrent-modification test observes `OptimisticLockingFailureException` rather than a lost update
- [ ] Inventory decrements by a single `findAndModify` against `booking_tier_inventory` whose filter includes `availableQuantity >= :quantity` and whose update is `$inc: { availableQuantity: -quantity, reservedQuantity: +quantity }`
- [ ] No code path reads `availableQuantity` into Java, compares it, and writes it back
- [ ] No service other than booking writes `booking_tier_inventory`, and booking never writes `catalog_ticket_tiers`
- [ ] A load test issuing 200 concurrent reservations against an inventory document with 50 available yields exactly 50 successes and 150 `TierSoldOut` refusals, and `available + reserved + sold` equals capacity throughout
- [ ] Retry on optimistic failure is bounded and explicit, never an unbounded loop

### ET-PLT-002-R7 · MongoDB is the only datastore a service connects to

THE SYSTEM SHALL restrict every service to MongoDB and Redis, and SHALL NOT open a
connection to any relational database.

**Acceptance**
- [ ] No service declares a JPA `@Entity` or `@Table`
- [ ] No service declares a `DataSource`, a JDBC driver dependency, a connection pool or a `PlatformTransactionManager`
- [ ] No service has a `spring.datasource.*` block in any profile; PostgreSQL appears in no service's configuration
- [ ] Keycloak's schema is Keycloak's alone, and no service holds credentials for it
- [ ] Every Redis key the platform writes is a row of the §4 Redis registry and carries a TTL
- [ ] No fact is readable only from Redis — for each registry row, §4 names where the authority lives
- [ ] Flushing Redis loses no business data; an integration test asserts this by flushing mid-suite

### ET-PLT-002-R8 · No stored document names a Java class

THE SYSTEM SHALL write a stable, package-independent type alias as the document type
discriminator, and no persisted document SHALL contain a fully-qualified Java class name.

**Acceptance**
- [ ] Every `@Document` class carries `@TypeAlias("…")` whose value is a short, stable, lower-camel name — never a package, never a class literal
- [ ] No document written by any service contains a `_class` value matching `com.pml.*`
- [ ] A type alias is never changed once written; renaming or moving the Java class leaves the alias untouched
- [ ] Moving a `@Document` class between packages, or promoting it to `shared-library`, changes no stored value and requires no backfill — a test asserts a document written before the move still deserialises after it
- [ ] Two services mapping the same collection agree on the alias, and a test asserts both read a document written by the other
- [ ] Every `@Document` names a row of the ET-PLT-002 §4 registry and carries `@TypeAlias`; no document field is `LocalDateTime` or `java.util.Date`; every balance-bearing document declares `@Version` fails on a `@Document` with no `@TypeAlias`

## 4. Model

### Type rules

| Concern | Java | MongoDB | Notes |
|---|---|---|---|
| Identity | `String` | `ObjectId` rendered as string | except `User.id`, which is the Keycloak UUID |
| Money | `BigDecimal` | `Decimal128` | + a sibling `currency: String`, default `ZMW` |
| Time | `Instant` | `Date` (BSON UTC) | from the `Clock` bean; `{pastTense}At` naming |
| Version | `Long` | `Long` | `@Version`, on versioned documents only |
| Enum | Java enum | `String` | UPPER_SNAKE_CASE |
| Foreign key | `String` | `String` | `{entity}Id`; user references are always `userId` |
| Type discriminator | `@TypeAlias("…")` | `_class` | a short stable alias — **never** a package or class name |

### Collection registry — closed

**catalog-service**

| Collection | Versioned | Holds | Introduced by |
|---|---|---|---|
| `catalog_events` | yes | the event, its state, schedule and organizer | ET-CAT-001 |
| `catalog_ticket_tiers` | yes | tier **definition** — name, price, capacity, sales window, status | ET-CAT-002 |
| `catalog_locations` | no | venues | ET-CAT-003 |
| `catalog_cities` | no | reference | ET-CAT-003 |
| `catalog_provinces` | no | reference | ET-CAT-003 |
| `catalog_categories` | no | event categories | ET-CAT-003 |
| `catalog_reference_data` | no | typed lookup lists and workflow statuses, one row per `(type, code)` | ET-PLT-014 |
| `catalog_platform_configuration` | no | the platform settings and feature flags — one table, read by every service | ET-ADM-002 |
| `catalog_approval_timelines` | no | the audit of an event's approval steps | ET-ADM-001 |
| `catalog_approval_escalations` | no | SLA escalation state | ET-ADM-001 |
| `catalog_statistics_rollups` | no | precomputed catalog statistics — events by city, by category, growth | ET-ADM-004 |
| `catalog_migration_runs` | no | this service's document-version backfills | ET-PLT-010 |
| `catalog_outbox` | no | staged cross-service events, written in the business transaction | ET-PLT-003 |
| `catalog_media` | **yes** | uploaded images: organizations' own and the platform's stock | ET-CAT-004 |

**booking-service**

| Collection | Versioned | Holds | Introduced by |
|---|---|---|---|
| `booking_tier_inventory` | **yes** | the **authoritative counters** — available, reserved, sold | ET-TKT-001 |
| `booking_reservations` | **yes** | the inventory hold and its TTL | ET-TKT-001 |
| `booking_tickets` | **yes** | the issued ticket, its owner, its QR identity | ET-TKT-002 |
| `booking_payment_intents` | **yes** | one intent per purchase attempt, keyed by idempotency key | ET-PAY-001 |
| `booking_payment_attempts` | **yes** | one row per provider call, with the provider reference | ET-PAY-001 |
| `booking_webhook_receipts` | no | every provider callback, for replay defence | ET-PAY-002 |
| `booking_escrow_accounts` | **yes** | one per event (D-05) | ET-FIN-001 |
| `booking_escrow_transactions` | **yes** | the movements into and out of an escrow account | ET-FIN-001 |
| `booking_platform_accounts` | **yes** | singleton revenue and fee accounts | ET-FIN-001 |
| `booking_chart_of_accounts` | **yes** | the account tree | ET-FIN-001 |
| `booking_journal_entries` | **yes** | the double-entry header | ET-FIN-001 |
| `booking_journal_lines` | no | the debit and credit lines | ET-FIN-001 |
| `booking_commission_records` | **yes** | pending and recognised commission | ET-FIN-002 |
| `booking_bank_accounts` | **yes** | organizer payout destinations | ET-FIN-003 |
| `booking_payout_requests` | **yes** | the payout lifecycle | ET-FIN-003 |
| `booking_refund_requests` | **yes** | refund lifecycle and fees | ET-FIN-004 |
| `booking_chargebacks` | **yes** | provider-initiated reversals | ET-FIN-004 |
| `booking_reconciliation_runs` | **yes** | one per reconciliation execution | ET-FIN-005 |
| `booking_reconciliation_items` | no | per-transaction match state | ET-FIN-005 |
| `booking_promo_codes` | **yes** | code, budget, redemption counter | ET-CAT-002 |
| `booking_checkins` | no | one row per admitted ticket, with the gate and the scanner | ET-TKT-003 |
| `booking_checkin_conflicts` | no | duplicate and offline-collision scans, for adjudication | ET-TKT-003 |
| `booking_ticket_transfers` | **yes** | transfer and resale lifecycle between users | ET-TKT-004 |
| `booking_recovery_proposals` | **yes** | proposed dual-control recovery actions on stuck money | ET-ADM-003 |
| `booking_bookings` | **yes** | one durable booking per reservation: number, contact, items and lifecycle (the reservation itself is TTL-removed) | ET-TKT-005 |
| `booking_counters` | no | monotonic counters behind human-facing numbers (booking numbers) | ET-TKT-005 |
| `booking_holder_messages` | no | audit of organizer messages to ticket holders: who, what, how many | ET-TKT-005 |
| `booking_platform_transfers` | no | moves between the platform's own accounts, one per idempotency key | ET-ADM-006 |
| `booking_purchase_escalations` | no | a failed purchase somebody investigated and resolved | **unassigned — see below** |
| `booking_statistics_rollups` | no | precomputed finance and sales statistics | ET-ADM-004 |
| `booking_migration_runs` | no | this service's document-version backfills | ET-PLT-010 |
| `booking_outbox` | no | staged cross-service events, written in the business transaction | ET-PLT-003 |

**identity-service**

| Collection | Versioned | Holds | Introduced by |
|---|---|---|---|
| `identity_users` | no | profile; `_id` is the Keycloak user ID | ET-IDN-002 |
| `identity_organizations` | no | the tenant, its KYB data and its status | ET-ORG-001 |
| `identity_organization_members` | no | membership and organization role | ET-ORG-002 |
| `identity_team_invitations` | no | pending invitations and their tokens | ET-ORG-002 |
| `identity_ownership_transfers` | no | pending ownership transfers | ET-ORG-002 |
| `identity_event_access_grants` | no | event-level role overrides | ET-ORG-003 |
| `identity_permissions` | no | the permission catalogue, bootstrapped from the code registry | ET-PLT-013 |
| `identity_role_permissions` | no | role → permission mapping, **one document per role**; Keycloak owns the roles themselves | ET-PLT-013 |
| `identity_role_permission_changes` | no | append-only log of every mapping change | ET-PLT-013 |
| `identity_verification_documents` | no | KYB document metadata and review state | ET-ORG-001 |
| `identity_notifications` | no | delivered and pending notifications | ET-NTF-001 |
| `identity_notification_preferences` | no | per-user channel preferences | ET-NTF-001 |
| `identity_user_devices` | no | push tokens | ET-NTF-001 |
| `identity_event_reminders` | no | scheduled reminder state | ET-NTF-002 |
| `identity_audit_logs` | no | the immutable audit trail | ET-PLT-009 |
| `identity_payout_config_audit_logs` | no | changes to payout configuration, who made them | **unassigned — see below** |
| `identity_notification_templates` | **yes** | versioned message bodies per channel and locale | ET-NTF-001 |
| `identity_mass_sends` | no | bulk-send batches and their per-recipient outcome | ET-NTF-002 |
| `identity_review_claims` | no | one claim per review subject — organizer, document or event | ET-ADM-001 |
| `identity_temporary_blocks` | no | time-limited subject and IP blocks | ET-PLT-011 |
| `identity_consent_records` | no | consent given and withdrawn, per purpose | ET-PLT-008 |
| `identity_erasure_requests` | no | erasure lifecycle and the 30-day grace period | ET-PLT-008 |
| `identity_data_exports` | no | subject-access export requests and their artefacts | ET-PLT-008 |
| `identity_statistics_rollups` | no | precomputed identity and organization statistics | ET-ADM-004 |
| `identity_migration_runs` | no | this service's document-version backfills | ET-PLT-010 |
| `identity_token_revocations` | no | revoked tokens, sessions and subjects — the system of record | ET-IDN-003 |
| `identity_outbox` | no | staged cross-service events, written in the business transaction | ET-PLT-003 |
| `identity_contacts` | no | verified WhatsApp numbers and emails, each owned by exactly one account | ET-IDN-004 |
| `identity_consents` | no | consent granted per purpose and version, with withdrawal | ET-IDN-004 |
| `identity_account_events` | no | account lifecycle and OTP-lock facts, no personal data | ET-IDN-004 |
| `identity_system_alerts` | no | operational alerts raised by services and the health probe, with their acknowledgement | ET-ADM-005 |
| `identity_announcements` | no | administrator announcements shown to a segment in a time window | ET-ADM-005 |

**78 collections** — catalog 14, booking 32, identity 32. No other collection exists.

**Ten rows marked versioned to match the code, 2026-09-19.** `booking_tickets`,
`booking_journal_entries`, `booking_payment_attempts`, `booking_chargebacks`,
`booking_commission_records`, `booking_chart_of_accounts`, `booking_bank_accounts`,
`booking_refund_requests`, `booking_escrow_transactions` and `booking_reconciliation_runs` carried
`@Version` while this table said unversioned. The table moved, not the code: optimistic locking
only ever turns a lost update into a refused one, and removing it can only make concurrency worse.
On the append-only rows it costs one integer per document; on `booking_tickets`, which is
validated, transferred and refunded concurrently, it is the protection that matters.

### Three rows opened from implementation, 2026-08-19

The registry is closed, so a collection the code writes and the registry does not name is a
contradiction in one direction or the other. Three were found by running the migrations against a
live database. Two more — `escrow_accounts` and `approval_notifications` — were reachable from
nothing but their own repositories and have been **deleted** rather than registered.

**`booking_purchase_escalations` — added, owner unassigned.** Six files write it. It is *not*
`booking_recovery_proposals` under another name, which was the standing guess: that row is a
dual-control proposal (`confirmedById` ≠ `proposedById`, `PT2H` expiry, idempotency key) and this
is a single-person resolution record (`reason`, `amount`, `resolved`, `resolvedBy`). Merging them
would drop the four-eyes requirement ET-ADM-003 exists to impose. Note also that
`booking_recovery_proposals` has a generated constant and **no implementing class** — so the two
subjects are, between them, one specified-but-absent collection and one
implemented-but-unspecified one.

**`identity_payout_config_audit_logs` — added, owner unassigned.** A resolver, a service, an
implementation and a repository write it. Distinct from `identity_audit_logs`, which
ET-PLT-009 defines as the general immutable trail; this is a targeted record of payout
configuration changes. It carries no service prefix on disk and must be renamed to the row above.

**`platform_configuration` → `catalog_platform_configuration` — decided 2026-09-19.** The
platform settings are **one table, like the reference data**: the database is shared, and the
services differ in what they do, not in where the settings live. Catalog already wrote the
document and stays its only writer; the table takes catalog's prefix. Identity's private
`PlatformConfigurationView` and its repository are deleted, and identity reads the payment
defaults through shared-library's `PlatformConfigurationReader`. The two rows this registry
had reserved for ET-ADM-002 in identity — `identity_platform_configuration` and
`identity_feature_flags` — are replaced by the one catalog row: a feature flag is a platform
setting, so it lives in the same table. Catalog's migration step `platform-settings-rename`
moves the data; both `CollectionRegistryLintTest` budgets it occupied drop to zero.


**There is no `admin_` prefix.** `admin` is a persona, not a service. A collection is
prefixed by the service that **writes** it, because that prefix is the only place
write-ownership is recorded; prefixing by audience produces a name that lies the moment a
second surface reads the data, and — worse — invites three services to write one
collection. Administrative data therefore lives with its owning service: blocks and review
claims in `identity_`, recovery proposals in `booking_`, and the platform settings in
`catalog_platform_configuration`. The two genuinely per-service concerns — statistics rollups and migration runs
— are **one collection per service**, never one shared collection with three writers.

### Index registry

Uniqueness rows are constraints, not optimisations: each one is a race the application
cannot win by checking first.

| Collection | Index | Kind | Why |
|---|---|---|---|
| `identity_users` | `{ email: 1 }` | unique, partial `email: {$type: string}` | one account per address |
| `identity_users` | `{ phoneNumber: 1 }` | unique, partial `phoneNumber: {$type: string}` | phone is the login identity |
| `identity_users` | `{ username: 1 }` | unique, partial `username: {$type: string}` | one account per username; Better Auth creates the row before the username exists, so many nulls coexist |
| `identity_users` | `{ keycloakUserId: 1 }` | unique, partial `keycloakUserId: {$type: string}` | ET-IDN-004 — one account per Keycloak user; orphans carry none |
| `identity_users` | `{ status: 1 }` | single | ET-IDN-004 — the repair schedule and admin lists read by account state |
| `identity_users` | `{ mergedInto: 1 }` | partial `mergedInto: {$type: string}` | ET-IDN-004 — find the accounts folded into a survivor |
| `identity_contacts` | `{ type: 1, valueHash: 1 }` | unique, partial `verifiedAt` exists and `releasedAt` null | ET-IDN-004 — one verified owner per contact; built by migration `contacts-unique-index` after the backfill, never at startup |
| `identity_contacts` | `{ accountId: 1 }` | single | ET-IDN-004 — an account's contacts |
| `identity_consents` | `{ accountId: 1, purpose: 1 }` | compound | ET-IDN-004 — current consent per purpose |
| `identity_account_events` | `{ accountId: 1, at: -1 }` | compound | ET-IDN-004 — an account's history, newest first |
| `identity_account_events` | `{ kind: 1, at: -1 }` | compound | ET-IDN-004 — operational reads by kind |
| `identity_organizations` | `{ slug: 1 }` | unique | the slug is a public URL and a Keycloak group name |
| `identity_organizations` | `{ ownerId: 1 }` | single | "my organization" |
| `identity_organizations` | `{ status: 1, submittedAt: 1 }` | compound | the approval queue, oldest first |
| `identity_organization_members` | `{ userId: 1, organizationId: 1 }` | **unique** | one membership per user per org |
| `identity_organization_members` | `{ organizationId: 1, role: 1, status: 1 }` | compound | the team list and the owner-count invariant |
| `identity_organization_members` | `{ organizationId: 1 }` | **partial unique** where `role = OWNER` | ET-ORG-002 R2 — two transfers confirmed at once each write a second owner, and no check-first wins that race |
| `identity_organization_members` | `{ mirrorPending: 1 }` | compound | ET-ORG-002 R8 — the `identity-group-mirror-repair` Schedule's repair runs every minute and this is its only query |
| `identity_team_invitations` | `{ invitationToken: 1 }` | unique | token lookup on the acceptance page |
| `identity_team_invitations` | `{ organizationId: 1, email: 1, status: 1 }` | compound | "is there already a pending invite" |
| `identity_team_invitations` | `{ expiresAt: 1 }` | **TTL, `expireAfterSeconds: 0`** | expired invitations remove themselves |
| `identity_team_invitations` | `{ phoneNumber: 1, status: 1 }` | compound | ET-ORG-002 R9 — pending invitations sent to a WhatsApp number |
| `identity_system_alerts` | `{ status: 1, lastSeenAt: -1 }` | compound | ET-ADM-005 — the alert list, newest activity first |
| `identity_system_alerts` | `{ source: 1, key: 1 }` | unique, partial `status` in `OPEN`, `ACKNOWLEDGED` | ET-ADM-005 — one live alert per condition, so concurrent raisers cannot open two |
| `identity_announcements` | `{ startsAt: 1, endsAt: 1 }` | compound | ET-ADM-005 — announcements live now |
| `identity_users` | `{ createdAt: 1 }` | single | ET-ADM-004 — the user-growth series scans a date range |
| `identity_ownership_transfers` | `{ transferToken: 1 }` | unique | a duplicate token makes one acceptance link resolve to two handshakes |
| `identity_ownership_transfers` | `{ organizationId: 1, status: 1 }` | compound | "is a transfer already in flight for this organization" |
| `identity_event_access_grants` | `{ userId: 1, eventId: 1 }` | **unique** | one grant per user per event |
| `identity_event_access_grants` | `{ eventId: 1, status: 1 }` | compound | who may scan this event |
| `identity_verification_documents` | `{ organizationId: 1, documentType: 1 }` | compound | the review panel |
| `identity_notifications` | `{ userId: 1, status: 1, createdAt: -1 }` | compound | the notification feed |
| `identity_user_devices` | `{ deviceToken: 1 }` | unique | one registration per device |
| `identity_audit_logs` | `{ createdAt: 1 }` | TTL, retention per ET-PLT-009 | bounded growth |
| `catalog_events` | `{ status: 1, eventDateTime: 1 }` | compound | **hot** — public discovery |
| `catalog_events` | `{ organizationId: 1, status: 1 }` | compound | the organizer dashboard |
| `catalog_events` | `{ status: 1, categoryId: 1, eventDateTime: 1 }` | compound | discovery by category |
| `catalog_events` | `{ status: 1, cityId: 1, eventDateTime: 1 }` | compound | discovery by city |
| `catalog_events` | `{ status: 1, categoryId: 1, cityId: 1, eventDateTime: 1 }` | compound | discovery by category and city |
| `catalog_events` | `{ title: "text", description: "text" }` | text, title weighted 5 | search |
| `catalog_ticket_tiers` | `{ eventId: 1, salesStartAt: 1 }` | compound | the on-sale query |
| `catalog_events` | `{ status: 1, publishedAt: -1 }` | compound | ET-CAT-004 — the feed, newest first |
| `catalog_events` | `{ status: 1, lowestTicketPrice: 1, eventDateTime: 1 }` | compound | ET-CAT-004 — the feed, cheapest first |
| `catalog_events` | `{ status: 1, lowestTicketPrice: -1, eventDateTime: 1 }` | compound | ET-CAT-004 — the feed, dearest first |
| `catalog_events` | `{ status: 1, soldTickets: -1, eventDateTime: 1 }` | compound | ET-CAT-004 — the feed, most sold first; trending |
| `catalog_events` | `{ organizationId: 1, createdAt: -1, _id: -1 }` | compound | ET-CAT-004 — an organization's events newest first, paged by cursor |
| `catalog_media` | `{ fileKey: 1 }` | **unique** | ET-CAT-004 — the file a public URL names |
| `catalog_media` | `{ organizationId: 1, createdAt: -1, _id: -1 }` | compound | ET-CAT-004 — an organization's library, paged by cursor |
| `catalog_media` | `{ kind: 1, status: 1, createdAt: -1 }` | compound | ET-CAT-004 — the moderation queue |
| `catalog_media` | `{ kind: 1, purpose: 1, categoryCode: 1, active: 1 }` | compound | ET-CAT-004 — the stock library and category tiles |
| `catalog_locations` | `{ cityId: 1 }` | single | venue lookup |
| `booking_tier_inventory` | `{ tierId: 1 }` | **unique** | **hot** — the reservation decrement targets this and only this |
| `booking_tier_inventory` | `{ eventId: 1 }` | single | remaining capacity across an event |
| `booking_reservations` | `{ expiresAt: 1 }` | **TTL, `expireAfterSeconds: 0`** | a hold nobody paid for releases itself |
| `booking_reservations` | `{ tierId: 1, status: 1 }` | compound | **hot** — outstanding holds per tier |
| `booking_reservations` | `{ userId: 1, status: 1 }` | compound | "my pending purchase" |
| `booking_reservations` | `{ userId: 1, items.ticketTierId: 1 }` | unique, partial `status: HELD` | ET-TKT-001 R5 — one live hold per buyer per tier |
| `booking_reservations` | `{ status: 1, expiresAt: 1 }` | compound | ET-TKT-001 R8 — boot adoption and the recovery queue read `HELD` reservations by expiry |
| `booking_tickets` | `{ eventId: 1, status: 1 }` | compound | **hot** — check-in and sales counts |
| `booking_tickets` | `{ ownerId: 1, status: 1 }` | compound | "my tickets" |
| `booking_tickets` | `{ ticketReference: 1 }` | unique, partial `ticketReference: {$type: string}` | the scanned identity; tickets that carry none coexist |
| `booking_payment_intents` | `{ idempotencyKey: 1 }` | **unique** | the retry guard that Redis alone cannot give |
| `booking_payment_intents` | `{ status: 1, createdAt: 1 }` | compound | ET-ADM-003's stuck-transaction queue; the purchase workflow's escalation marks what it lists |
| `booking_payment_intents` | `{ depositId: 1 }` | unique, partial `depositId: {$type: string}` | ET-PAY-002 R4 — the platform reference a provider callback correlates on |
| `booking_payment_attempts` | `{ providerReference: 1 }` | unique, partial `providerReference: {$type: string}` | webhook correlation |
| `booking_payment_attempts` | `{ paymentIntentId: 1 }` | | ET-PAY-001 §4's `paymentAttempts(intentId)` |
| `booking_webhook_receipts` | `{ providerEventId: 1 }` | **unique** | replay defence |
| `booking_webhook_receipts` | `{ receivedAt: 1 }` | TTL, 90 days | bounded growth |
| `booking_escrow_accounts` | `{ eventId: 1 }` | **unique** | one escrow per event |
| `booking_escrow_transactions` | `{ escrowAccountId: 1, createdAt: -1 }` | compound | the statement |
| `booking_journal_entries` | `{ referenceType: 1, referenceId: 1 }` | compound | trace a movement back to its cause |
| `booking_journal_lines` | `{ journalEntryId: 1 }` | single | entry → lines |
| `booking_journal_lines` | `{ accountCode: 1, postedAt: 1 }` | compound | **hot** — trial balance |
| `booking_commission_records` | `{ eventId: 1, status: 1 }` | compound | recognition at event completion |
| `booking_payout_requests` | `{ organizationId: 1, status: 1 }` | compound | the payout queue |
| `booking_payout_requests` | `{ status: 1, requestedAt: 1 }` | compound | the finance workbench |
| `booking_payout_requests` | `{ idempotencyKey: 1 }` | **unique**, partial `$type: string` | one payout per key; partial because a keyless request stores `null` and sparse would index it |
| `booking_checkins` | `{ scanId: 1 }` | **unique**, partial `$type: string` | offline upload replay guard; online scans store `null`, so sparse would reject every scan after the first |
| `booking_bank_accounts` | `{ organizationId: 1, isDefault: 1 }` | compound | the default destination |
| `booking_promo_codes` | `{ code: 1 }` | unique | redemption lookup |
| `catalog_outbox` | `{ status: 1, stagedAt: 1 }` | compound | **hot** — the drain's claim, every poll |
| `booking_outbox` | `{ status: 1, stagedAt: 1 }` | compound | **hot** — the drain's claim, every poll |
| `identity_outbox` | `{ status: 1, stagedAt: 1 }` | compound | **hot** — the drain's claim, every poll |
| `catalog_approval_escalations` | `{ eventId: 1, level: 1 }` | **unique** | ET-ADM-001 R3 — one escalation per level, at the database rather than a read-then-write |

> **Partial, not sparse, on the three optional-field uniqueness rows.** Sparse excludes a document
> in which the field is **absent**; it still indexes one that stores an explicit `null`, which is
> what mapping an object with a null field produces. Under `unique` the second such document then
> collides with the first on the key `null` — so one user without a phone number means no other
> user may be created without one. This is not theoretical: building
> `identity_users.idx_phoneNumber` against the live database fails with
> `E11000 dup key: { phoneNumber: null }`. A `$type` test excludes both the absent field and the
> stored null in one condition.


The **hot** rows are the queries R3's `explain()` box names.

The three outbox rows were added when ET-PLT-003 BE-1 landed. The drain claims with
`status = PENDING` sorted by `stagedAt`, which without this index is a collection scan plus an
in-memory sort — and MongoDB aborts an in-memory sort above 32MB. An outbox that has fallen
behind is exactly when it is largest, so the failure arrives at the moment the drain is most
needed.

#### Lookups and constraints moved from model annotations (2026-09-18)

These indexes were created at startup from `@Indexed`, `@CompoundIndex` and `@TextIndexed` on the
model classes, and from two start-up helpers (booking's analytics indexes, catalog's reference-data
seeder). They now live in each service's `*IndexInitializer` under the names the annotations gave
them, so a database that already has them reports each as present. `auto-index-creation` is off.
Two annotation indexes were not carried over because a registry TTL index on the same field
replaces them: plain `{ expiresAt: 1 }` on `identity_team_invitations` and on `booking_reservations`.
Five analytics indexes on `financial_transactions` were dropped with them: no service writes that
collection, so there is nothing to index (FINDINGS F-037).
`IndexCensusTest` in each service proves the registry produces every other index that existed before.

| Collection | Index | Kind | Why |
|---|---|---|---|
| `identity_audit_logs` | `{ action: 1, timestamp: -1 }` | compound | lookup by `action`, `timestamp` |
| `identity_audit_logs` | `{ action: 1 }` | single | lookup by `action` |
| `identity_audit_logs` | `{ performedBy: 1 }` | single | lookup by `performedBy` |
| `identity_audit_logs` | `{ status: 1, timestamp: -1 }` | compound | lookup by `status`, `timestamp` |
| `identity_audit_logs` | `{ status: 1 }` | single | lookup by `status` |
| `identity_audit_logs` | `{ timestamp: 1 }` | single | lookup by `timestamp` |
| `identity_audit_logs` | `{ userId: 1, action: 1, timestamp: -1 }` | compound | lookup by `userId`, `action`, `timestamp` |
| `identity_audit_logs` | `{ userId: 1 }` | single | lookup by `userId` |
| `identity_event_access_grants` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `identity_event_access_grants` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `identity_event_access_grants` | `{ userId: 1 }` | single | lookup by `userId` |
| `identity_event_reminders` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `identity_event_reminders` | `{ userId: 1 }` | single | lookup by `userId` |
| `identity_notification_preferences` | `{ userId: 1 }` | unique | one row per `userId` |
| `identity_notifications` | `{ userId: 1 }` | single | lookup by `userId` |
| `identity_organization_members` | `{ organizationId: 1, role: 1 }` | compound | lookup by `organizationId`, `role` |
| `identity_organization_members` | `{ organizationId: 1, status: 1 }` | compound | lookup by `organizationId`, `status` |
| `identity_organization_members` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `identity_organization_members` | `{ userId: 1 }` | single | lookup by `userId` |
| `identity_organizations` | `{ businessEmail: 1 }` | single | lookup by `businessEmail` |
| `identity_organizations` | `{ status: 1 }` | single | lookup by `status` |
| `identity_organizations` | `{ statusSemantic: 1 }` | single | lookup by `statusSemantic` |
| `identity_ownership_transfers` | `{ expiresAt: 1 }` | single | lookup by `expiresAt` |
| `identity_ownership_transfers` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `identity_payout_config_audit_logs` | `{ action: 1 }` | single | lookup by `action` |
| `identity_payout_config_audit_logs` | `{ organizationId: 1, timestamp: -1 }` | compound | lookup by `organizationId`, `timestamp` |
| `identity_payout_config_audit_logs` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `identity_payout_config_audit_logs` | `{ timestamp: 1 }` | single | lookup by `timestamp` |
| `identity_payout_config_audit_logs` | `{ userId: 1, timestamp: -1 }` | compound | lookup by `userId`, `timestamp` |
| `identity_payout_config_audit_logs` | `{ userId: 1 }` | single | lookup by `userId` |
| `identity_team_invitations` | `{ email: 1, organizationId: 1 }` | compound | lookup by `email`, `organizationId` |
| `identity_team_invitations` | `{ email: 1 }` | single | lookup by `email` |
| `identity_team_invitations` | `{ organizationId: 1, status: 1 }` | compound | lookup by `organizationId`, `status` |
| `identity_team_invitations` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `identity_token_revocations` | `{ expiresAt: 1 }` | TTL, `expireAfterSeconds: 0` | expires at `expiresAt` |
| `identity_user_devices` | `{ deviceToken: 1 }` | single | lookup by `deviceToken` |
| `identity_user_devices` | `{ userId: 1 }` | single | lookup by `userId` |
| `identity_verification_documents` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `catalog_approval_escalations` | `{ escalatedTo: 1 }` | single | lookup by `escalatedTo` |
| `catalog_approval_escalations` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `catalog_approval_escalations` | `{ nextReminderAt: 1 }` | single | lookup by `nextReminderAt` |
| `catalog_approval_escalations` | `{ status: 1, escalatedTo: 1 }` | compound | lookup by `status`, `escalatedTo` |
| `catalog_approval_escalations` | `{ status: 1 }` | single | lookup by `status` |
| `catalog_approval_timelines` | `{ assignedReviewerId: 1 }` | single | lookup by `assignedReviewerId` |
| `catalog_approval_timelines` | `{ currentStatus: 1, slaDeadline: 1 }` | compound | lookup by `currentStatus`, `slaDeadline` |
| `catalog_approval_timelines` | `{ currentStatus: 1 }` | single | lookup by `currentStatus` |
| `catalog_approval_timelines` | `{ eventId: 1 }` | unique | one row per `eventId` |
| `catalog_approval_timelines` | `{ hasActiveEscalation: 1 }` | single | lookup by `hasActiveEscalation` |
| `catalog_approval_timelines` | `{ isOverdue: 1 }` | single | lookup by `isOverdue` |
| `catalog_approval_timelines` | `{ organizerId: 1, currentStatus: 1 }` | compound | lookup by `organizerId`, `currentStatus` |
| `catalog_approval_timelines` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `catalog_approval_timelines` | `{ slaDeadline: 1 }` | single | lookup by `slaDeadline` |
| `catalog_approval_timelines` | `{ submittedAt: 1 }` | single | lookup by `submittedAt` |
| `catalog_categories` | `{ code: 1 }` | unique | one row per `code` |
| `catalog_categories` | `{ name: 1 }` | unique | one row per `name` |
| `catalog_cities` | `{ country: 1 }` | single | lookup by `country` |
| `catalog_cities` | `{ name: 1 }` | single | lookup by `name` |
| `catalog_cities` | `{ provinceId: 1 }` | single | lookup by `provinceId` |
| `catalog_events` | `{ assignedReviewerId: 1 }` | single | lookup by `assignedReviewerId` |
| `catalog_events` | `{ categoryId: 1 }` | single | lookup by `categoryId` |
| `catalog_events` | `{ cityName: 1 }` | single | lookup by `cityName` |
| `catalog_events` | `{ createdBy: 1 }` | single | lookup by `createdBy` |
| `catalog_events` | `{ deletedBy: 1 }` | single | lookup by `deletedBy` |
| `catalog_events` | `{ eventDateTime: 1 }` | single | lookup by `eventDateTime` |
| `catalog_events` | `{ isDeleted: 1 }` | single | lookup by `isDeleted` |
| `catalog_events` | `{ isFreeEvent: 1 }` | single | lookup by `isFreeEvent` |
| `catalog_events` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `catalog_events` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `catalog_events` | `{ status: 1 }` | single | lookup by `status` |
| `catalog_events` | `{ updatedBy: 1 }` | single | lookup by `updatedBy` |
| `catalog_provinces` | `{ country: 1 }` | single | lookup by `country` |
| `catalog_provinces` | `{ name: 1 }` | unique | one row per `name` |
| `catalog_reference_data` | `{ type: 1, code: 1 }` | unique compound | one row per `type`, `code` |
| `catalog_reference_data` | `{ type: 1, isActive: 1, displayOrder: 1 }` | compound | lookup by `type`, `isActive`, `displayOrder` |
| `catalog_reference_data` | `{ type: 1, parentCode: 1 }` | compound | lookup by `type`, `parentCode` |
| `catalog_ticket_tiers` | `{ eventId: 1, code: 1 }` | unique compound | one row per `eventId`, `code` |
| `catalog_ticket_tiers` | `{ eventId: 1, isActive: 1, sortOrder: 1 }` | compound | lookup by `eventId`, `isActive`, `sortOrder` |
| `catalog_ticket_tiers` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `catalog_ticket_tiers` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_bank_accounts` | `{ accountNumber: 1 }` | single | lookup by `accountNumber` |
| `booking_bank_accounts` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_bank_accounts` | `{ organizerId: 1, isDefault: 1 }` | compound | lookup by `organizerId`, `isDefault` |
| `booking_bank_accounts` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_chargebacks` | `{ chargebackId: 1 }` | unique | one row per `chargebackId` |
| `booking_chargebacks` | `{ customerId: 1 }` | single | lookup by `customerId` |
| `booking_chargebacks` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_chargebacks` | `{ journalEntryId: 1 }` | single | lookup by `journalEntryId` |
| `booking_chargebacks` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_chargebacks` | `{ organizerId: 1, status: 1 }` | compound | lookup by `organizerId`, `status` |
| `booking_chargebacks` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_chargebacks` | `{ originalTransactionId: 1 }` | single | lookup by `originalTransactionId` |
| `booking_chargebacks` | `{ reason: 1 }` | single | lookup by `reason` |
| `booking_chargebacks` | `{ receivedAt: 1 }` | single | lookup by `receivedAt` |
| `booking_chargebacks` | `{ recoveryStatus: 1 }` | single | lookup by `recoveryStatus` |
| `booking_chargebacks` | `{ status: 1, receivedAt: -1 }` | compound | lookup by `status`, `receivedAt` |
| `booking_chargebacks` | `{ status: 1 }` | single | lookup by `status` |
| `booking_chargebacks` | `{ ticketId: 1 }` | single | lookup by `ticketId` |
| `booking_chart_of_accounts` | `{ accountCode: 1 }` | unique | one row per `accountCode` |
| `booking_chart_of_accounts` | `{ accountType: 1, isActive: 1 }` | compound | lookup by `accountType`, `isActive` |
| `booking_chart_of_accounts` | `{ accountType: 1 }` | single | lookup by `accountType` |
| `booking_chart_of_accounts` | `{ isActive: 1 }` | single | lookup by `isActive` |
| `booking_chart_of_accounts` | `{ parentAccountCode: 1 }` | single | lookup by `parentAccountCode` |
| `booking_chart_of_accounts` | `{ subType: 1, isActive: 1 }` | compound | lookup by `subType`, `isActive` |
| `booking_checkin_conflicts` | `{ eventId: 1, detectedAt: -1 }` | compound | lookup by `eventId`, `detectedAt` |
| `booking_checkin_conflicts` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_checkin_conflicts` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_checkins` | `{ eventId: 1, recordedAt: -1 }` | compound | lookup by `eventId`, `recordedAt` |
| `booking_checkins` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_checkins` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_checkins` | `{ ticketId: 1 }` | unique | one row per `ticketId` |
| `booking_commission_records` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_commission_records` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_commission_records` | `{ organizerId: 1, status: 1 }` | compound | lookup by `organizerId`, `status` |
| `booking_commission_records` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_commission_records` | `{ status: 1 }` | single | lookup by `status` |
| `booking_commission_records` | `{ ticketId: 1 }` | unique | one row per `ticketId` |
| `booking_escrow_accounts` | `{ accountNumber: 1 }` | unique | one row per `accountNumber` |
| `booking_escrow_accounts` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_escrow_accounts` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_escrow_accounts` | `{ status: 1, organizerId: 1 }` | compound | lookup by `status`, `organizerId` |
| `booking_escrow_accounts` | `{ status: 1 }` | single | lookup by `status` |
| `booking_escrow_accounts` | `{ statusSemantic: 1 }` | single | lookup by `statusSemantic` |
| `booking_escrow_transactions` | `{ category: 1 }` | single | lookup by `category` |
| `booking_escrow_transactions` | `{ chargebackId: 1 }` | single | lookup by `chargebackId` |
| `booking_escrow_transactions` | `{ escrowAccountId: 1, category: 1 }` | compound | lookup by `escrowAccountId`, `category` |
| `booking_escrow_transactions` | `{ escrowAccountId: 1, timestamp: -1 }` | compound | lookup by `escrowAccountId`, `timestamp` |
| `booking_escrow_transactions` | `{ escrowAccountId: 1, type: 1 }` | compound | lookup by `escrowAccountId`, `type` |
| `booking_escrow_transactions` | `{ escrowAccountId: 1 }` | single | lookup by `escrowAccountId` |
| `booking_escrow_transactions` | `{ journalEntryId: 1 }` | single | lookup by `journalEntryId` |
| `booking_escrow_transactions` | `{ paymentIntentId: 1 }` | single | lookup by `paymentIntentId` |
| `booking_escrow_transactions` | `{ payoutRequestId: 1 }` | single | lookup by `payoutRequestId` |
| `booking_escrow_transactions` | `{ refundRequestId: 1 }` | single | lookup by `refundRequestId` |
| `booking_escrow_transactions` | `{ ticketId: 1 }` | single | lookup by `ticketId` |
| `booking_escrow_transactions` | `{ timestamp: 1 }` | single | lookup by `timestamp` |
| `booking_escrow_transactions` | `{ type: 1 }` | single | lookup by `type` |
| `booking_journal_entries` | `{ correlationId: 1 }` | single | lookup by `correlationId` |
| `booking_journal_entries` | `{ entryDate: 1, status: 1 }` | compound | lookup by `entryDate`, `status` |
| `booking_journal_entries` | `{ entryDate: 1 }` | single | lookup by `entryDate` |
| `booking_journal_entries` | `{ entryNumber: 1 }` | unique | one row per `entryNumber` |
| `booking_journal_entries` | `{ lines.accountCode: 1, status: 1 }` | compound | lookup by `lines.accountCode`, `status` |
| `booking_journal_entries` | `{ reversalOfEntryId: 1 }` | single | lookup by `reversalOfEntryId` |
| `booking_journal_entries` | `{ reversedByEntryId: 1 }` | single | lookup by `reversedByEntryId` |
| `booking_journal_entries` | `{ status: 1 }` | single | lookup by `status` |
| `booking_journal_entries` | `{ type: 1, status: 1 }` | compound | lookup by `type`, `status` |
| `booking_journal_entries` | `{ type: 1 }` | single | lookup by `type` |
| `booking_payment_attempts` | `{ attemptNumber: 1 }` | unique | one row per `attemptNumber` |
| `booking_payment_attempts` | `{ buyerId: 1, createdAt: -1 }` | compound | lookup by `buyerId`, `createdAt` |
| `booking_payment_attempts` | `{ buyerId: 1 }` | single | lookup by `buyerId` |
| `booking_payment_attempts` | `{ correlationId: 1 }` | single | lookup by `correlationId` |
| `booking_payment_attempts` | `{ depositId: 1 }` | unique | one row per `depositId` |
| `booking_payment_attempts` | `{ eventId: 1, status: 1 }` | compound | lookup by `eventId`, `status` |
| `booking_payment_attempts` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_payment_attempts` | `{ expiresAt: 1 }` | single | lookup by `expiresAt` |
| `booking_payment_attempts` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_payment_attempts` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_payment_attempts` | `{ providerTransactionId: 1 }` | single | lookup by `providerTransactionId` |
| `booking_payment_attempts` | `{ reservationId: 1, status: 1 }` | compound | lookup by `reservationId`, `status` |
| `booking_payment_attempts` | `{ reservationId: 1 }` | single | lookup by `reservationId` |
| `booking_payment_attempts` | `{ status: 1, createdAt: 1 }` | compound | lookup by `status`, `createdAt` |
| `booking_payment_attempts` | `{ status: 1, expiresAt: 1 }` | compound | lookup by `status`, `expiresAt` |
| `booking_payment_attempts` | `{ status: 1 }` | single | lookup by `status` |
| `booking_payment_intents` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_payment_intents` | `{ providerTransactionId: 1 }` | single | lookup by `providerTransactionId` |
| `booking_payment_intents` | `{ reservationId: 1 }` | unique | one row per `reservationId` |
| `booking_payment_intents` | `{ status: 1 }` | single | lookup by `status` |
| `booking_payment_intents` | `{ transactionRef: 1 }` | unique | one row per `transactionRef` |
| `booking_payment_intents` | `{ userId: 1 }` | single | lookup by `userId` |
| `booking_payout_requests` | `{ escrowAccountId: 1 }` | single | lookup by `escrowAccountId` |
| `booking_payout_requests` | `{ eventId: 1, status: 1 }` | compound | lookup by `eventId`, `status` |
| `booking_payout_requests` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_payout_requests` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_payout_requests` | `{ organizerId: 1, status: 1 }` | compound | lookup by `organizerId`, `status` |
| `booking_payout_requests` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_payout_requests` | `{ paymentReference: 1 }` | single | lookup by `paymentReference` |
| `booking_payout_requests` | `{ requestId: 1 }` | unique | one row per `requestId` |
| `booking_payout_requests` | `{ status: 1, organizerId: 1 }` | compound | lookup by `status`, `organizerId` |
| `booking_payout_requests` | `{ status: 1 }` | single | lookup by `status` |
| `booking_payout_requests` | `{ statusSemantic: 1 }` | single | lookup by `statusSemantic` |
| `booking_platform_accounts` | `{ accountType: 1 }` | unique | one row per `accountType` |
| `booking_promo_codes` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_promo_codes` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_promo_codes` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_purchase_escalations` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_purchase_escalations` | `{ reason: 1 }` | single | lookup by `reason` |
| `booking_purchase_escalations` | `{ reservationId: 1 }` | unique | one row per `reservationId` |
| `booking_purchase_escalations` | `{ resolved: 1 }` | single | lookup by `resolved` |
| `booking_reconciliation_runs` | `{ reconciliationDate: 1 }` | single | lookup by `reconciliationDate` |
| `booking_reconciliation_runs` | `{ runNumber: 1 }` | unique | one row per `runNumber` |
| `booking_reconciliation_runs` | `{ status: 1, reconciliationDate: -1 }` | compound | lookup by `status`, `reconciliationDate` |
| `booking_reconciliation_runs` | `{ status: 1 }` | single | lookup by `status` |
| `booking_reconciliation_runs` | `{ type: 1, reconciliationDate: -1 }` | compound | lookup by `type`, `reconciliationDate` |
| `booking_reconciliation_runs` | `{ type: 1 }` | single | lookup by `type` |
| `booking_refund_requests` | `{ buyerId: 1 }` | single | lookup by `buyerId` |
| `booking_refund_requests` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_refund_requests` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_refund_requests` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_refund_requests` | `{ pawaPayRefundId: 1 }` | single | lookup by `pawaPayRefundId` |
| `booking_refund_requests` | `{ requestId: 1 }` | unique | one row per `requestId` |
| `booking_refund_requests` | `{ ticketId: 1 }` | single | lookup by `ticketId` |
| `booking_refund_requests` | `{ ticketNumber: 1 }` | single | lookup by `ticketNumber` |
| `booking_reservations` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_reservations` | `{ idempotencyKey: 1 }` | single | lookup by `idempotencyKey` |
| `booking_reservations` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_reservations` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_reservations` | `{ paymentIntentId: 1 }` | single | lookup by `paymentIntentId` |
| `booking_reservations` | `{ status: 1 }` | single | lookup by `status` |
| `booking_reservations` | `{ userId: 1 }` | single | lookup by `userId` |
| `booking_tickets` | `{ buyerId: 1 }` | single | lookup by `buyerId` |
| `booking_tickets` | `{ eventId: 1 }` | single | lookup by `eventId` |
| `booking_tickets` | `{ organizationId: 1 }` | single | lookup by `organizationId` |
| `booking_tickets` | `{ organizerId: 1 }` | single | lookup by `organizerId` |
| `booking_tickets` | `{ reservationId: 1 }` | single | lookup by `reservationId` |
| `booking_tickets` | `{ status: 1 }` | single | lookup by `status` |
| `booking_tickets` | `{ statusSemantic: 1 }` | single | lookup by `statusSemantic` |
| `booking_tickets` | `{ ticketNumber: 1 }` | unique | one row per `ticketNumber` |
| `booking_tickets` | `{ ticketTierId: 1 }` | single | lookup by `ticketTierId` |
| `booking_tickets` | `{ bookingId: 1 }` | single | ET-TKT-002 — the tickets of one booking |
| `booking_ticket_transfers` | `{ ticketId: 1, status: 1 }` | compound | ET-TKT-004 — the open offer of a ticket |
| `booking_ticket_transfers` | `{ fromUserId: 1, createdAt: -1 }` | compound | ET-TKT-004 — a sender's offers, newest first |
| `booking_ticket_transfers` | `{ toUserId: 1, createdAt: -1 }` | compound | ET-TKT-004 — a recipient's offers, newest first |
| `booking_ticket_transfers` | `{ eventId: 1, status: 1 }` | compound | ET-TKT-004 — the organizer's view of an event's transfers |
| `booking_ticket_transfers` | `{ ticketId: 1 }` | unique, partial `status: PENDING` | ET-TKT-004 — at most one open offer per ticket |
| `booking_bookings` | `{ reservationId: 1 }` | unique | ET-TKT-005 — one booking per reservation |
| `booking_bookings` | `{ bookingNumber: 1 }` | unique | ET-TKT-005 — the human booking number |
| `booking_bookings` | `{ organizationId: 1, createdAt: -1 }` | compound | ET-TKT-005 — the organizer's booking list |
| `booking_bookings` | `{ buyerId: 1, createdAt: -1 }` | compound | ET-TKT-005 — the buyer's booking list |
| `booking_bookings` | `{ eventId: 1, status: 1 }` | compound | ET-TKT-005 — bookings of an event by status |
| `booking_platform_transfers` | `{ idempotencyKey: 1 }` | unique | ET-ADM-006 — one platform transfer per idempotency key |
| `booking_platform_transfers` | `{ createdAt: -1 }` | single | ET-ADM-006 — transfers, newest first |
| `booking_recovery_proposals` | `{ status: 1, expiresAt: 1 }` | compound | ET-ADM-003 — proposals that can still be confirmed, soonest expiry first |
| `booking_recovery_proposals` | `{ proposedById: 1, proposedAt: -1 }` | compound | ET-ADM-003 — a maker's own proposals |
| `booking_holder_messages` | `{ eventId: 1, createdAt: -1 }` | compound | ET-TKT-005 — an event's holder-message history |

### Redis key registry

Every key has a TTL. For every row, the authority is elsewhere.

| Key | Type | TTL | Purpose | Authority |
|---|---|---|---|---|
| `otp:{phone}` | STRING | 300 s | the six-digit code | none — it is ephemeral by design |
| `otp:cooldown:{phone}` | STRING | 60 s | resend throttle | none |
| `idem:{key}` | STRING | 24 h | in-flight idempotency guard | `booking_payment_intents.idempotencyKey` |
| `evt:seen:{consumer}:{eventId}` | STRING | 7 d | consumer deduplication | the consumer's own write |
| `ratelimit:{scope}:{subject}` | STRING | window | gateway rate limiting | none |
| `cache:event:{eventId}` | STRING | 300 s | discovery read-through | `catalog_events` |
| `cache:tier:{tierId}` | STRING | 30 s | tier availability display | `booking_tier_inventory` |

*Amended 2026-09-13 under [D-21](../../ROADMAP.md):* no sweep lock key remains — timers and recurring jobs are Temporal workflows and Schedules ([ET-PLT-015](../015-durable-execution/)), and a Schedule's overlap policy SKIP is the mutex.

`cache:tier:{tierId}` is a **display** value with a deliberately short TTL. No reservation
decision reads it; the decision is the conditional update against the document (R6).

### PostgreSQL

| Schema | Owner | Contents |
|---|---|---|
| Keycloak's schema | Keycloak | its own; **no service connects to it** |
| `temporal`, `temporal_visibility` | the self-hosted Temporal service, staging and production | workflow history and visibility ([ET-PLT-015](../015-durable-execution/), ROADMAP D-28); the development server keeps its own SQLite file; **no service connects to them** |

That is the entire relational footprint. No service declares a datasource, and there is no
relational schema of its own — the outbox is `{service}_outbox` in MongoDB
([ET-PLT-003](../003-event-contract/)), staged in the same transaction as the write it
describes, which is the only way the two are ever actually atomic.

### The atomic inventory write

```java
// the only shape an inventory decrement may take — filter and update evaluated
// together, by the server, in one round trip. booking_tier_inventory, never
// catalog_ticket_tiers: the definition is catalog's, the counters are booking's.
Query hold = Query.query(Criteria.where("tierId").is(tierId)
        .and("status").is(InventoryStatus.ON_SALE)
        .and("availableQuantity").gte(quantity));

Update take = new Update()
        .inc("availableQuantity", -quantity)
        .inc("reservedQuantity",  quantity);

return mongo.findAndModify(hold, take,
                FindAndModifyOptions.options().returnNew(true), TierInventory.class)
            .switchIfEmpty(Mono.error(new TierSoldOut(tierId)));
```

An empty result means the filter did not match: sold out, not on sale, or gone. It is a
refusal, not an error, and it appends nothing.

## 5. Tasks

- [ ] **T1 · Replica set in every environment; keyfile and `rs.initiate()` in dev**
  - requirements: R1
  - files: `../docker-resources/mongodb/`, `../docker-resources/docker-compose.yml`
  - verify: `rs.status()` reports a PRIMARY; a two-document rollback test passes
  - parallel-safe: no — shared compose file, coordinate
  - depends: —

- [ ] **T2 · `ReactiveMongoTransactionManager`; `Decimal128` and `Instant` converters**
  - requirements: R1, R4, R5
  - files: `backend/*/src/main/java/com/pml/*/config/MongoConfig.java`
  - verify: round-trip tests for `K1234.56` and a frozen `Instant`
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T3 · Rename every collection to its registry name; add the lint check**
  - requirements: R2
  - files: every `@Document` class in all three services
  - verify: every `@Document` names a row of the ET-PLT-002 §4 registry and carries `@TypeAlias`; no document field is `LocalDateTime` or `java.util.Date`; every balance-bearing document declares `@Version`
  - parallel-safe: yes — one service per agent
  - depends: T2

- [ ] **T4 · Declare every index; add the live-index assertion test**
  - requirements: R3
  - files: `backend/*/src/main/java/com/pml/*/config/MongoIndexInitializer.java`
  - verify: index-set equality test; `explain()` reports `IXSCAN` on the five hot queries
  - parallel-safe: yes — one service per agent
  - depends: T3

- [ ] **T5 · Money: purge floating point, add `currency`, pin `HALF_UP` at scale 2**
  - requirements: R4
  - files: `backend/booking-service/src/main/java/com/pml/booking/`
  - verify: no production field typed `double` or `float` names an amount, balance, price, fee, total or commission; every rounding is `HALF_UP`; no balance is assigned outside the ledger
  - parallel-safe: no — booking only, but it is most of booking
  - depends: T2

- [ ] **T6 · Time: `Instant` everywhere; `DateTimeProvider` on the platform `Clock`**
  - requirements: R5
  - files: `backend/*/src/main/java/com/pml/*/config/`
  - verify: a search of production source finds no inline `Instant.now()`, `LocalDateTime.now()`, `LocalDate.now()`, `ZonedDateTime.now()` or `System.currentTimeMillis()`; a frozen-clock audit-field test
  - parallel-safe: yes — one service per agent
  - depends: T2

- [ ] **T7 · `@Version` on every versioned row; bounded retry**
  - requirements: R6
  - files: the documents marked *versioned* in §4
  - verify: a concurrent-modification test observes `OptimisticLockingFailureException`
  - parallel-safe: yes
  - depends: T3

- [ ] **T8 · The conditional atomic inventory decrement, and the 200-caller load test**
  - requirements: R6
  - files: `backend/booking-service/.../TierInventoryRepositoryImpl.java`
  - verify: 200 concurrent reservations against 50 available yield exactly 50 successes, against a real replica set under contention
  - parallel-safe: no — the single most important write in the platform
  - depends: T7

- [ ] **T9 · Remove every relational dependency; confine Redis; the flush test**
  - requirements: R7
  - files: `backend/*/pom.xml`, `backend/*/src/main/resources/application.yml`
  - verify: no service resolves a JDBC driver or declares a datasource; a mid-suite `FLUSHALL` loses no business data
  - parallel-safe: yes — one service per agent
  - depends: T2

- [ ] **T10 · `@TypeAlias` on every document; assert no `com.pml.*` reaches storage**
  - requirements: R8
  - files: every `@Document` class in all three services
  - verify: a document written before its class is moved between packages still deserialises after the move; no stored `_class` matches `com.pml.*`
  - parallel-safe: yes — one service per agent
  - depends: T3

## 6. Out of scope

| Capability | Spec |
|---|---|
| The `Clock` bean itself, module boundaries, dependency baseline | [ET-PLT-001](../001-runtime-baseline/) |
| The event publication registry and how the outbox drains | [ET-PLT-003](../003-event-contract/) |
| Which fields each document actually carries | the spec that introduces the collection |
| Idempotency-key semantics and the guard's lifecycle | [ET-PLT-007](../007-security-and-authorization/), [ET-PAY-001](../../payment/001-payment-intents-and-providers/) |
| The chart of accounts and what a journal pair means | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| PII classification, erasure and anonymisation | [ET-PLT-008](../008-data-protection/) |
| Backup, restore and disaster recovery | operations, not a spec in this corpus |

Deliberately never in scope: **a database per service** (a boundary a prefix and a lint
rule already enforce), and **CDC-based outbox drainage** (a second delivery pipeline for a
guarantee the platform already has — see ET-PLT-003).
