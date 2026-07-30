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

It also draws the line the platform's hybrid persistence depends on. PostgreSQL exists
here for exactly two reasons: Keycloak's own schema, and Spring Modulith's event
publication registry. **No business document is ever written to it.** Redis exists for
exactly four: OTP codes, idempotency guards, distributed locks, and caches — every key
with a TTL, and never a fact the platform cannot recompute.

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
is a federation violation wearing a driver. **No service reads another service's
collections**; it asks over the graph or listens for the event.

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

**PostgreSQL is framework infrastructure and nothing else.** The `modulith_events` schema
holds Spring Modulith's `event_publication` table; Keycloak holds its own schema. No JPA
entity exists in any service, and the blocking JDBC pool is sized for the outbox drain,
not for request traffic.

**Redis holds four things, each with a TTL.** OTP codes, idempotency guards, distributed
locks, and read-through caches. It holds **no business state**: a TTL expiry must never
be able to destroy a fact, and Redis cannot participate in a Mongo transaction, so a
write that lands in one and not the other is a write nobody can reconcile.

**Rejected alternatives**

- *A database per service.* Three connection strings, three backup policies and three replica sets, to enforce a boundary a collection prefix and a lint rule already enforce.
- *A standalone `mongod` in development, replica set only in production.* Guarantees that the class of bug transactions exist to prevent is invisible until staging.
- *`long` minor units (ngwee) instead of `BigDecimal`.* Correct, and it makes every commission percentage a rounding argument at the call site instead of once at persistence.
- *Pessimistic locking on ticket tiers.* Serialises the on-sale minute — the one minute that must not serialise.
- *An application-level sequence for ticket numbers.* A second document to contend on, and a single point of contention per event; ticket identity is the `ObjectId` and the human-facing reference is derived from it.
- *Storing the outbox in MongoDB and draining it with Debezium.* Adds Kafka Connect, a Debezium connector and a second delivery pipeline to obtain a guarantee Spring Modulith already provides over a table the platform already runs — see [ET-PLT-003](../003-event-contract/).
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
- [ ] No `@Transactional` method spans MongoDB and PostgreSQL

### ET-PLT-002-R2 · Every collection is a row of the registry, owned by exactly one service

THE SYSTEM SHALL persist business documents only to collections named in §4, and IF a
service reads a collection it does not own, THEN the build SHALL fail.

**Acceptance**
- [ ] Every `@Document` in every service names a row of the §4 collection registry
- [ ] Every collection name is prefixed `catalog_`, `booking_` or `identity_` and the prefix matches the declaring service
- [ ] No service declares a `@Document` or repository for a collection whose registry row names another service
- [ ] No service holds a `MongoTemplate` call naming another service's collection as a string
- [ ] `./scripts/spec-lint.sh --persistence` exits 0
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
- [ ] `./scripts/spec-lint.sh --money` exits 0

### ET-PLT-002-R5 · Time is `Instant`, from the clock, in UTC

THE SYSTEM SHALL persist every timestamp as `Instant` sourced from the application
`Clock`.

**Acceptance**
- [ ] No document field is `LocalDateTime`, `LocalDate`, `java.util.Date` or an epoch `long`
- [ ] Spring Data auditing (`@CreatedDate`, `@LastModifiedDate`) is backed by a `DateTimeProvider` reading the same `Clock` bean, so a frozen test clock freezes audit fields too
- [ ] Action timestamps follow the `{pastTense}At` convention of §4 — `submittedAt`, `approvedAt`, `expiresAt`, `settledAt`
- [ ] A document written under a frozen clock carries exactly the frozen instant in `createdAt`
- [ ] `./scripts/spec-lint.sh --clock` exits 0

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

### ET-PLT-002-R7 · PostgreSQL and Redis stay in their lanes

THE SYSTEM SHALL restrict PostgreSQL to framework infrastructure and Redis to expiring
operational data, and SHALL NOT store business state in either.

**Acceptance**
- [ ] No service declares a JPA `@Entity` or `@Table`
- [ ] The only PostgreSQL schema any service touches is `modulith_events`; Keycloak's schema is Keycloak's alone
- [ ] The JDBC pool is sized for the outbox drain and is configured with a maximum well below the MongoDB connection budget
- [ ] Every Redis key the platform writes is a row of the §4 Redis registry and carries a TTL
- [ ] No fact is readable only from Redis — for each registry row, §4 names where the authority lives
- [ ] Flushing Redis loses no business data; an integration test asserts this by flushing mid-suite

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
| `catalog_reference_data` | no | typed lookup lists | ET-CAT-003 |
| `catalog_approval_timelines` | no | the audit of an event's approval steps | ET-ADM-001 |
| `catalog_approval_escalations` | no | SLA escalation state | ET-ADM-001 |

**booking-service**

| Collection | Versioned | Holds | Introduced by |
|---|---|---|---|
| `booking_tier_inventory` | **yes** | the **authoritative counters** — available, reserved, sold | ET-TKT-001 |
| `booking_reservations` | **yes** | the inventory hold and its TTL | ET-TKT-001 |
| `booking_tickets` | no | the issued ticket, its owner, its QR identity | ET-TKT-002 |
| `booking_payment_intents` | **yes** | one intent per purchase attempt, keyed by idempotency key | ET-PAY-001 |
| `booking_payment_attempts` | no | one row per provider call, with the provider reference | ET-PAY-001 |
| `booking_webhook_receipts` | no | every provider callback, for replay defence | ET-PAY-002 |
| `booking_escrow_accounts` | **yes** | one per event (D-05) | ET-FIN-001 |
| `booking_escrow_transactions` | no | the movements into and out of an escrow account | ET-FIN-001 |
| `booking_platform_accounts` | **yes** | singleton revenue and fee accounts | ET-FIN-001 |
| `booking_chart_of_accounts` | no | the account tree | ET-FIN-001 |
| `booking_journal_entries` | no | the double-entry header | ET-FIN-001 |
| `booking_journal_lines` | no | the debit and credit lines | ET-FIN-001 |
| `booking_commission_records` | no | pending and recognised commission | ET-FIN-002 |
| `booking_bank_accounts` | no | organizer payout destinations | ET-FIN-003 |
| `booking_payout_requests` | **yes** | the payout lifecycle | ET-FIN-003 |
| `booking_refund_requests` | no | refund lifecycle and fees | ET-FIN-004 |
| `booking_chargebacks` | no | provider-initiated reversals | ET-FIN-004 |
| `booking_reconciliation_runs` | no | one per reconciliation execution | ET-FIN-005 |
| `booking_reconciliation_items` | no | per-transaction match state | ET-FIN-005 |
| `booking_promo_codes` | **yes** | code, budget, redemption counter | ET-CAT-002 |

**identity-service**

| Collection | Versioned | Holds | Introduced by |
|---|---|---|---|
| `identity_users` | no | profile; `_id` is the Keycloak user ID | ET-IDN-002 |
| `identity_organizations` | no | the tenant, its KYB data and its status | ET-ORG-001 |
| `identity_organization_members` | no | membership and organization role | ET-ORG-002 |
| `identity_team_invitations` | no | pending invitations and their tokens | ET-ORG-002 |
| `identity_ownership_transfers` | no | pending ownership transfers | ET-ORG-002 |
| `identity_event_access_grants` | no | event-level role overrides | ET-ORG-003 |
| `identity_permissions` | no | the permission catalogue | ET-ORG-003 |
| `identity_role_permissions` | no | role → permission mapping | ET-ORG-003 |
| `identity_verification_documents` | no | KYB document metadata and review state | ET-ORG-001 |
| `identity_notifications` | no | delivered and pending notifications | ET-NTF-001 |
| `identity_notification_preferences` | no | per-user channel preferences | ET-NTF-001 |
| `identity_user_devices` | no | push tokens | ET-NTF-001 |
| `identity_event_reminders` | no | scheduled reminder state | ET-NTF-002 |
| `identity_audit_logs` | no | the immutable audit trail | ET-PLT-009 |

**38 collections.** No other collection exists.

### Index registry

Uniqueness rows are constraints, not optimisations: each one is a race the application
cannot win by checking first.

| Collection | Index | Kind | Why |
|---|---|---|---|
| `identity_users` | `{ email: 1 }` | unique, sparse | one account per address |
| `identity_users` | `{ phoneNumber: 1 }` | unique, sparse | phone is the login identity |
| `identity_users` | `{ userType: 1, accountStatus: 1 }` | compound | the admin user list |
| `identity_organizations` | `{ slug: 1 }` | unique | the slug is a public URL and a Keycloak group name |
| `identity_organizations` | `{ ownerId: 1 }` | single | "my organization" |
| `identity_organizations` | `{ status: 1, submittedAt: 1 }` | compound | the approval queue, oldest first |
| `identity_organization_members` | `{ userId: 1, organizationId: 1 }` | **unique** | one membership per user per org |
| `identity_organization_members` | `{ organizationId: 1, role: 1, status: 1 }` | compound | the team list and the owner-count invariant |
| `identity_team_invitations` | `{ invitationToken: 1 }` | unique | token lookup on the acceptance page |
| `identity_team_invitations` | `{ organizationId: 1, email: 1, status: 1 }` | compound | "is there already a pending invite" |
| `identity_team_invitations` | `{ expiresAt: 1 }` | **TTL, `expireAfterSeconds: 0`** | expired invitations remove themselves |
| `identity_event_access_grants` | `{ userId: 1, eventId: 1 }` | **unique** | one grant per user per event |
| `identity_event_access_grants` | `{ eventId: 1, status: 1 }` | compound | who may scan this event |
| `identity_verification_documents` | `{ organizationId: 1, documentType: 1 }` | compound | the review panel |
| `identity_notifications` | `{ userId: 1, status: 1, createdAt: -1 }` | compound | the notification feed |
| `identity_user_devices` | `{ deviceToken: 1 }` | unique | one registration per device |
| `identity_audit_logs` | `{ createdAt: 1 }` | TTL, retention per ET-PLT-009 | bounded growth |
| `catalog_events` | `{ status: 1, startsAt: 1 }` | compound | **hot** — public discovery |
| `catalog_events` | `{ organizationId: 1, status: 1 }` | compound | the organizer dashboard |
| `catalog_events` | `{ categoryId: 1, cityId: 1, startsAt: 1 }` | compound | filtered discovery |
| `catalog_events` | `{ title: "text", description: "text" }` | text | search |
| `catalog_ticket_tiers` | `{ eventId: 1, salesStartAt: 1 }` | compound | the on-sale query |
| `catalog_locations` | `{ cityId: 1 }` | single | venue lookup |
| `booking_tier_inventory` | `{ tierId: 1 }` | **unique** | **hot** — the reservation decrement targets this and only this |
| `booking_tier_inventory` | `{ eventId: 1 }` | single | remaining capacity across an event |
| `booking_reservations` | `{ expiresAt: 1 }` | **TTL, `expireAfterSeconds: 0`** | a hold nobody paid for releases itself |
| `booking_reservations` | `{ tierId: 1, status: 1 }` | compound | **hot** — outstanding holds per tier |
| `booking_reservations` | `{ userId: 1, status: 1 }` | compound | "my pending purchase" |
| `booking_tickets` | `{ eventId: 1, status: 1 }` | compound | **hot** — check-in and sales counts |
| `booking_tickets` | `{ ownerId: 1, status: 1 }` | compound | "my tickets" |
| `booking_tickets` | `{ ticketReference: 1 }` | unique | the scanned identity |
| `booking_payment_intents` | `{ idempotencyKey: 1 }` | **unique** | the retry guard that Redis alone cannot give |
| `booking_payment_intents` | `{ status: 1, createdAt: 1 }` | compound | the stuck-transaction sweep |
| `booking_payment_attempts` | `{ providerReference: 1 }` | unique, sparse | webhook correlation |
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
| `booking_bank_accounts` | `{ organizationId: 1, isDefault: 1 }` | compound | the default destination |
| `booking_promo_codes` | `{ code: 1 }` | unique | redemption lookup |

The five **hot** rows are the queries R3's `explain()` box names.

### Redis key registry

Every key has a TTL. For every row, the authority is elsewhere.

| Key | Type | TTL | Purpose | Authority |
|---|---|---|---|---|
| `otp:{phone}` | STRING | 300 s | the six-digit code | none — it is ephemeral by design |
| `otp:cooldown:{phone}` | STRING | 60 s | resend throttle | none |
| `idem:{key}` | STRING | 24 h | in-flight idempotency guard | `booking_payment_intents.idempotencyKey` |
| `evt:seen:{consumer}:{eventId}` | STRING | 7 d | consumer deduplication | the consumer's own write |
| `lock:sweep:{name}` | STRING | 30 s | scheduled-sweep mutex | none |
| `ratelimit:{scope}:{subject}` | STRING | window | gateway rate limiting | none |
| `cache:event:{eventId}` | STRING | 300 s | discovery read-through | `catalog_events` |
| `cache:tier:{tierId}` | STRING | 30 s | tier availability display | `booking_tier_inventory` |

`cache:tier:{tierId}` is a **display** value with a deliberately short TTL. No reservation
decision reads it; the decision is the conditional update against the document (R6).

### PostgreSQL

| Schema | Owner | Contents |
|---|---|---|
| `modulith_events` | the three services | `event_publication` — Spring Modulith's registry |
| Keycloak's schema | Keycloak | its own; no service connects to it |

No business document, ever. No JPA entity in any service.

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
  - files: every `@Document`, `scripts/spec-lint.sh`
  - verify: `./scripts/spec-lint.sh --persistence`
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
  - verify: `./scripts/spec-lint.sh --money`
  - parallel-safe: no — booking only, but it is most of booking
  - depends: T2

- [ ] **T6 · Time: `Instant` everywhere; `DateTimeProvider` on the platform `Clock`**
  - requirements: R5
  - files: `backend/*/src/main/java/com/pml/*/config/`
  - verify: `./scripts/spec-lint.sh --clock`; a frozen-clock audit-field test
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

- [ ] **T9 · Confine PostgreSQL and Redis; size the JDBC pool; the flush test**
  - requirements: R7
  - files: `backend/*/src/main/resources/application.yml`
  - verify: a mid-suite `FLUSHALL` loses no business data
  - parallel-safe: yes
  - depends: T2

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
