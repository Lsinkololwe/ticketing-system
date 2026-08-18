# Event Ticketing · constructs and naming, Spring Boot 3.5.4 + DGS 10.5 + WebFlux

Every convention below is normative for this corpus and supersedes anything in
`CLAUDE.md`, `docs/`, or existing code that contradicts it. Where a convention differs
from what the repository does today, the row says so and names the spec that fixes it.

**A spec that proposes a construct not listed here is wrong, not a variation.**

The stack is fixed. This corpus adds no framework, no broker, no database and no
language. What it adds is a decision about how the ones already chosen are used.

---

## 0 · The stack, pinned

| Layer | Choice | Version | Repo today |
|---|---|---|---|
| Language | Java, records + sealed types + virtual threads | 21 | ✓ |
| Framework | Spring Boot, fully reactive | 3.5.4 | ✓ |
| Web | Spring WebFlux — no `spring-boot-starter-web` on any classpath | — | ✓ |
| GraphQL | Netflix DGS on Spring GraphQL | **10.5.0** | ✓ — `CLAUDE.md` says 10.0.1 and is stale |
| Federation | Apollo Federation | **v2.9** | ✓ on all three subgraphs |
| Business data | Spring Data MongoDB **Reactive**, replica set | 8.x | ✓ driver, ✓ replica set — `docker-resources` runs `mongod --replSet rs0` with keyfile auth |
| Event publication | MongoDB outbox, staged in the business transaction | — | ✗ ET-PLT-003 introduces it |
| Cross-service bus | Azure Service Bus via Spring Cloud Stream | Azure 5.19.0 | ✓ |
| Cache, OTP, locks | Redis, reactive | 7.x | ✓ |
| Identity | Keycloak + custom SPI | 26.x | ✓ |
| Gateway | `spring-cloud-starter-gateway-server-webflux` | — | ✓ |
| Router | Apollo Router, **in `docker-resources/apollo-router/ticketing/`** | — | ✓ |

Three services, three subgraphs, one database, one router. Adding a fourth service is a
platform spec, not a feature decision.

Specified by [ET-PLT-001](_platform/001-runtime-baseline/).

---

## 1 · Reactivity

The whole request path is non-blocking, from the router to the driver. One blocking call
on an event-loop thread stalls every concurrent request that thread is carrying, which at
on-sale peak is thousands.

| Rule | Why |
|---|---|
| Every repository, service and resolver method returns `Mono<T>` or `Flux<T>` | The signature is the contract; a method returning `T` has already blocked |
| Never `.block()`, `.blockFirst()`, `.blockLast()`, `.toFuture().get()` | Blocks a Netty worker |
| Never `.subscribe()` to trigger work in a request path | Fire-and-forget: the response is sent before the write happens and the error goes nowhere |
| Compose with `flatMap`, not `map(x -> repo.save(x).block())` | The second is the first mistake spelled differently |
| Genuinely blocking third-party SDKs go on `Schedulers.boundedElastic()`, wrapped once, in `infrastructure/` | Contain the blocking to one adapter class rather than letting it leak into services |
| `@Transactional` on a reactive method needs a `ReactiveMongoTransactionManager` **and** a replica set | Against a standalone `mongod` the annotation is silently inert |

```java
// the shape every service method takes
public Mono<Ticket> reserve(ReserveTicketsCommand cmd) {
    return tierRepository.decrementAvailable(cmd.tierId(), cmd.quantity())
        .switchIfEmpty(Mono.error(new TierSoldOut(cmd.tierId())))
        .flatMap(tier -> reservationRepository.save(Reservation.open(cmd, clock.instant())))
        .flatMap(outbox::stage);
}
```

Repo today: **five `.block()` calls in production code** and 150 files still on
`LocalDateTime`. ET-PLT-001 removes both.

Specified by [ET-PLT-001](_platform/001-runtime-baseline/).

---

## 2 · Persistence

### The two stores, and what each is for

| Store | Holds | Never holds |
|---|---|---|
| **MongoDB** (`ticketing`) | Every business document, in prefixed collections | Never framework infrastructure |
| **PostgreSQL** | Keycloak's own schema, reached only by Keycloak | **Nothing a service connects to** |
| **Redis** | OTP codes, idempotency guards, distributed locks, rate-limit counters, read-through caches — every key with a TTL | **Never business state.** A TTL expiry silently destroys it |

There is one datastore and one transaction manager. The event that must accompany a write
is staged into `{service}_outbox` **through the same `ReactiveMongoTemplate` session as the
write itself**, so the two commit together or neither does. That is the whole reason the
outbox is in MongoDB: an outbox in a second store is two transaction managers that cannot
enlist together, which means the document can commit while the event row fails — the exact
dual-write failure an outbox exists to prevent, reintroduced by the thing meant to prevent
it. A `@Scheduled` drain publishes to the bus afterwards, outside any transaction.

### MongoDB rules

| Rule | Why |
|---|---|
| One database, `ticketing`; collections prefixed by owning service | A service reading another service's collection is a federation violation wearing a driver |
| The prefix is the **writing** service — `catalog_`, `booking_`, `identity_`, and nothing else | `admin_` names a persona, not a service. A prefix by audience records no ownership and invites three writers into one collection |
| Every `@Document` carries `@TypeAlias` with a short, stable name | Spring Data writes the FQCN into `_class` by default, which makes the Java package structure part of the persisted schema — move the class and the documents already written no longer deserialise |
| A replica set in **every** environment, single-node in dev | `@Transactional` and `$currentDate`-style atomicity need one; a standalone `mongod` fails open |
| Every collection is declared in the ET-PLT-002 §4 registry with its indexes | An index discovered in production is an outage discovered in production |
| Money is `BigDecimal` in Java and `Decimal128` in Mongo | `double` cannot represent K0.10. This is not a style preference |
| Timestamps are `java.time.Instant`, never `LocalDateTime`, `Date` or epoch `long` | `LocalDateTime` has no zone; a payout window that straddles midnight is decided by whichever host ran the job |
| Optimistic locking (`@Version`) on every document that carries a balance or a count | Two concurrent decrements of the same tier is the default case at on-sale, not the edge case |
| Inventory decrement is one conditional atomic update, never read-modify-write | `findAndModify` with `available >= qty` in the filter is the only oversell defence that holds |

Specified by [ET-PLT-002](_platform/002-persistence-baseline/).

---

## 3 · Events — two tiers, and the line between them

```
                    ┌──────────────────────────────────────────────────┐
   intra-service    │  ApplicationEventPublisher.publishEvent          │
   after commit     │  @TransactionalEventListener(AFTER_COMMIT)       │
   in-process       │  no durability of its own                        │
                    └──────────────────────────────────────────────────┘
                    ┌──────────────────────────────────────────────────┐
   the outbox       │  outbox.stage(envelope)  ← SAME reactive tx      │
   atomic           │  → {service}_outbox in MongoDB                   │
                    └──────────────────────────────────────────────────┘
                    ┌──────────────────────────────────────────────────┐
   cross-service    │  @Scheduled drain → StreamBridge.send(...)       │
   at-least-once    │  → Azure Service Bus topic                       │
   unordered        │  → subscription per consuming service            │
                    └──────────────────────────────────────────────────┘
```

| Concern | Convention | Repo today |
|---|---|---|
| Intra-service event name | Past tense + `Event` suffix — `PaymentCompletedEvent` | ✓ 24 event types |
| Cross-service wire name | `{context}.{PastTense}` — `booking.TicketPurchased` | ✗ the Java FQN is the wire name |
| Envelope | Every cross-service message carries `eventId`, `eventType`, `occurredAt`, `correlationId`, `causationId`, `schemaVersion`, `payload` | ✗ payload only |
| Version | Integer counter, starts at `1` | ✗ absent |
| Handler | `@TransactionalEventListener(AFTER_COMMIT)` — never bare `@EventListener` | ✓ where listeners exist |
| Topics | `catalog-events`, `booking-events`, `identity-events` | ✓ |
| Consumer | Idempotent, keyed on `eventId` in a Redis `SET NX` guard with a 7-day TTL | ✗ |

**Three rules that are not negotiable.**

1. **Never call `StreamBridge.send` inside a MongoDB transaction.** The bus commits and
   the transaction then rolls back, and the platform has told three services about a
   ticket that does not exist. Publish from an `@TransactionalEventListener(AFTER_COMMIT)`, which runs
   after commit by definition, or from the outbox drain.

2. **Never let a delivery failure throw out of a listener.** A provider being down is a
   business outcome; record it as a further event or a retry row. Throwing dead-letters a
   message whose only problem is that somebody else's server is slow.

3. **Write the event before the command.** If you cannot name the past-tense fact —
   `TicketReserved`, `EscrowCredited`, `PayoutSettled` — the operation is not a business
   decision, it is a CRUD update wearing a costume. `UpdateTicket` is a smell.

Specified by [ET-PLT-003](_platform/003-event-contract/).

---

## 4 · The federated graph

Three subgraphs, composed by Apollo Router. Ownership is total: exactly one service
declares each type's canonical fields, and every other service references it as a stub.

| Rule | Why |
|---|---|
| `type X @key(fields: "id")` in the owning service only | Two owners is two sources of truth |
| `type X @key(fields: "id", resolvable: false) { id: ID! }` for a stub | The stub says "I know this exists, I cannot fetch it" |
| `extend type X @key(fields: "id")` to contribute fields | Contribution, not ownership |
| **Never redeclare `id` inside an `extend` block** | `tried to redefine field 'id'` — the single most common composition failure in this repo's history |
| `@external` on a field you read but do not own; `@requires` when you compute from it | Otherwise the router cannot plan the fetch |
| `@DgsEntityFetcher(name = "X")` in the owning service, resolving by key | The router's only way back to the entity |
| Every admin-only field carries `@tag(name: "admin")` | The public contract variant is derived from tags; an untagged admin field leaks to the customer graph |
| Shared scalars — `BigDecimal`, `DateTime`, `JSON`, `Long`, `PhoneNumber` — declared identically in all three | A scalar that differs by subgraph fails composition |
| `@auth(requires: Role)` from `shared-library/graphql/auth.graphqls` is the only authorization directive | One directive, one implementation, one place to audit |
| Composition runs in CI and **fails the build** | `compose-supergraph.sh` warning and exiting 0 is how a broken subgraph vanishes silently |

**The schema workflow has one direction and no exceptions:**

```
backend schema.graphqls  →  rover supergraph compose  →  GraphOS publish  →  npm run codegen
```

A hand-written TypeScript type for a GraphQL shape is a defect. If the type is missing,
it is missing from the schema.

Specified by [ET-PLT-004](_platform/004-federation-contract/).

---

## 5 · Errors

A GraphQL error that reaches a mobile client as `INTERNAL` is an error the client cannot
act on. Every domain refusal resolves to a registry row with a stable code.

```java
@DgsComponent
public class BookingErrorHandler {

    @DgsExceptionHandler(TierSoldOut.class)
    public GraphQLError soldOut(TierSoldOut ex) {
        return TypedGraphQLError.newBuilder()
            .errorType(ErrorType.FAILED_PRECONDITION)
            .message(ex.getMessage())
            .extensions(Map.of(
                "errorCode",  "TIER_SOLD_OUT",     // the registry row
                "retryable",  false,               // the client's actual question
                "tierId",     ex.tierId()))
            .build();
    }
}
```

| Rule | Why |
|---|---|
| Refusals are named as facts — `TierSoldOut`, `PayoutBelowMinimum`, `InvitationExpired` | Not `TicketNotFoundException`. The name is the business outcome |
| Every refusal extends the abstract `DomainRefusal` in `shared-library` | One base type, one handler, one audit |
| `extensions.errorCode` comes from the closed ET-PLT-005 §4 registry; one code per refusal | Two codes for one condition means clients branch on both, and later on neither |
| `extensions.retryable` is always present | *Try again* and *this is gone* are different screens |
| A payment or payout refusal never carries the provider's raw message | It leaks account state to whoever asked |

Repo today: **zero `@DgsExceptionHandler` methods**, despite `CLAUDE.md` documenting the
pattern. Every domain exception currently surfaces as a generic 500.

Specified by [ET-PLT-005](_platform/005-error-contract/).

---

## 6 · Security and authorization

Keycloak is the source of truth for **who you are** and **what platform role you hold**.
MongoDB is the source of truth for **which organization you belong to and in what
capacity**. Neither is authoritative for the other's half, and no code asks the wrong one.

| Concern | Home |
|---|---|
| Credentials, MFA, account enabled/locked | Keycloak |
| Realm roles: `SUPER_ADMIN`, `ADMIN`, `FINANCE`, `ORGANIZER`, `CUSTOMER` | Keycloak |
| Group membership `/organizations/{slug}/{role}` | Keycloak, mirrored from Mongo |
| Organization membership and its role | MongoDB `organization_members` |
| Event-level access grants | MongoDB `event_access_grants` |
| Custom and denied permissions | MongoDB, on the membership or the grant |

**Permission resolution has one implementation and one order** (ET-ORG-003):

```
platform role  →  event access grant  →  organization role  →  custom  →  denied  →  DENY
```

An explicit deny beats an inherited allow at every level. The default is deny. A
resolution written a second time anywhere in the tree is a defect, because the second
copy is where the two will disagree.

| Rule | Why |
|---|---|
| `@auth(requires: …)` on the schema field, `@PreAuthorize` on the REST method | Two entry points, one policy source |
| `/api/internal/**` demands `SCOPE_internal-read` / `SCOPE_internal-write` / `ROLE_INTERNAL_SERVICE` | These endpoints bypass user authorization entirely |
| Tenant scoping is a repository-level filter, never a resolver-level `if` | A missing `if` returns another organization's data with a 200 |
| Every money-moving mutation takes a client-supplied `idempotencyKey` | Mobile networks retry. Twice-charged is unrecoverable trust |
| A JWT is validated by JWKS at each service, never trusted because the gateway forwarded it | The gateway is not a security boundary a subgraph can assume |

Specified by [ET-PLT-007](_platform/007-security-and-authorization/).

---

## 7 · Money

| Rule | Why |
|---|---|
| `BigDecimal` in Java, `Decimal128` in Mongo, `BigDecimal` scalar on the wire | Anything else loses cents at scale |
| Currency is `ZMW` and is stored on every monetary field's document | A platform that assumes one currency cannot add a second without a migration |
| Every balance movement is a **double-entry journal pair**; no balance is ever set directly | A balance you can only compute is a balance you can audit |
| A balance column is a cached projection of the ledger, reconcilable to it on demand | If they disagree, the ledger is right |
| Commission is earned in two stages — pending at purchase, recognised at event completion | Money owed on a ticket for an event that gets cancelled was never revenue |
| Escrow is per event, not per organizer | Cancelling one event must not be able to claw back another's settled funds |
| Rounding is `HALF_UP` at 2 decimal places, applied once, at the point of persistence | Rounding twice is how a ledger drifts by a ngwee a day |

`docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` is authoritative on the financial model;
where it and the code disagree, the document is right and the code is the bug.

Specified by [ET-FIN-001](finance/001-escrow-and-ledger/) and
[ET-FIN-002](finance/002-commission/).

---

## 8 · Time

| Rule | Why |
|---|---|
| One application-wide `Clock` bean, `Africa/Lusaka`, injected everywhere | Freeze time once in a test instead of injecting a clock into two hundred classes |
| **No `Instant.now()`, `LocalDateTime.now()` or `System.currentTimeMillis()` inline** | 202 inline calls today make every deadline untestable |
| Timestamps are `Instant`; a wall-clock date the user sees is formatted at the edge | Storage is UTC; presentation is local; the two are different concerns |
| Every deadline — reservation TTL, invitation expiry, settlement window, OTP validity — has a boundary test | What happens at exactly seven days is where invitation bugs live |

Specified by [ET-PLT-001](_platform/001-runtime-baseline/).

---

## 9 · Sagas and long-running processes

Unlike some frameworks, nothing here removes sagas — but an implicit saga is worse than
none. A multi-step process that crosses a service or a provider is a **persisted state
machine**:

| Rule | Why |
|---|---|
| A saga has a document in its own collection with an explicit `status` enum | A process whose state lives only in the sequence of listeners cannot be resumed |
| Its collection and indexes ship in the **same change** as the orchestrator | An orchestrator whose collection has no index is a full scan per step |
| Every step is idempotent and re-entrant | At-least-once delivery means every step runs twice eventually |
| Compensation is an explicit step with its own event, not a `catch` block | You will need to query "what compensated last month" |
| Timeouts are swept by a `@Scheduled` method holding a Redis lock, in a class with no listeners | Keeps the fire-and-forget a sweep legitimately does away from a listener that must never do one |

The three sagas this platform runs: **ticket purchase** (ET-TKT-001 → ET-PAY-002),
**organizer approval** (ET-ORG-001) and **payout settlement** (ET-FIN-003).

---

## 10 · Testing — five layers

| Layer | Catches | Needs | Speed | How many |
|---|---|---|---|---|
| 1 · Decision | Wrong business rules, bad state transitions | nothing | ~1 ms | hundreds |
| 2 · Projection / repository | Aggregation pipelines and queries drifting from the documents | Testcontainers Mongo | ~50 ms | dozens |
| 3 · Saga | Broken long-running processes, bad compensation | fixture + stubs | ~50 ms | one per saga |
| 4 · Contract | Subgraph composition, schema breaking changes | rover | ~1 s | one per subgraph |
| 5 · Integration | Wiring, real concurrency, real money paths | Testcontainers + WireMock | seconds | a handful |

Nearly all tests are layer 1, because nearly all the risk is. If you are asserting on
commission arithmetic inside a Testcontainers test, move it down.

**Always assert that a refused operation persisted nothing.** A test that only checks the
exception still passes if the service wrote the reservation and *then* threw — and an
oversold event is not recoverable by deleting a row.

Repo today: **no test files at all.** The suite was removed deliberately so that every
test is rewritten against the spec it proves, rather than inherited from code that predates
the corpus. ET-PLT-006 is therefore not a clean-up — it is the whole of the platform's test
evidence, and until it lands no spec can honestly reach `verified`.

Specified by [ET-PLT-006](_platform/006-test-harness/).

---

## 11 · Quick reference — forbidden constructs

Every row below is a review rule. None of them is enforced by tooling, so each one holds
only as long as the person writing the code knows it — which is why the *why* column is
not decoration.

| Forbidden | Use instead |
|---|---|
| `.block()`, `.blockFirst()`, `.toFuture().get()` in production | Return `Mono`/`Flux` and compose |
| `.subscribe()` to trigger a write | Return the chain; let the caller subscribe |
| `spring-boot-starter-web` on any service classpath | `spring-boot-starter-webflux` |
| `spring-cloud-starter-gateway` | `spring-cloud-starter-gateway-server-webflux` |
| `spring.cloud.gateway.*` YAML for the WebFlux gateway | `spring.cloud.gateway.server.webflux.*` |
| an outbox in PostgreSQL, or any second store | `{service}_outbox` in MongoDB, staged in the same reactive transaction |
| a `DataSource`, JDBC driver or JPA entity in a service | nothing — no service connects to a relational database |
| `StreamBridge.send` inside a `@Transactional` method | Publish from an `@TransactionalEventListener(AFTER_COMMIT)` |
| bare `@EventListener` for a module boundary | `@TransactionalEventListener(AFTER_COMMIT)` |
| `double` or `float` for money | `BigDecimal` / `Decimal128` |
| `LocalDateTime`, `java.util.Date`, epoch `long` timestamps | `Instant` |
| `Instant.now()` / `LocalDateTime.now()` / `System.currentTimeMillis()` inline | The injected `Clock` bean |
| `id: ID! @external` inside an `extend type` block | The stub type already declares `id` |
| a `@key` on a type owned by another subgraph | `resolvable: false` stub, or `extend type` |
| an admin field with no `@tag(name: "admin")` | Tag it, or it composes into the public contract |
| a hand-written TypeScript type for a GraphQL shape | `npm run codegen` |
| an Apollo Router config inside `ticketing-system/` | `docker-resources/apollo-router/ticketing/` |
| `User.keycloakUserId` | `User.id` **is** the Keycloak user ID |
| a per-service MongoDB database | one `ticketing` database, prefixed collections |
| an `admin_`-prefixed collection | the prefix of the service that writes it |
| a collection with more than one writing service | one collection per writer, composed over the graph |
| a `@Document` with no `@TypeAlias` | a stable alias, so `_class` never stores a Java class name |
| read-modify-write on ticket inventory | one conditional atomic `findAndModify` |
| a balance assignment outside the ledger | a double-entry journal pair |
| a domain exception with no `@DgsExceptionHandler` | a registry row + a typed handler |
| a money-moving mutation with no `idempotencyKey` | take one, guard on it in Redis |
| a saga whose state is only the listener sequence | a document with an explicit `status` |
| a tenant filter applied in a resolver `if` | a repository-level `organizationId` predicate |
| a GraphQL subscription for dashboard refresh | smart polling (D-12) |
| GraphQL multipart file upload | REST presigned URL (D-11) |
