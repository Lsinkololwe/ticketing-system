# Event Ticketing · constructs and naming, Spring Boot 3.5.4 + DGS 10.5 + WebFlux + Temporal

Every convention below is normative for this corpus and supersedes anything in
`CLAUDE.md`, `docs/`, or existing code that contradicts it. Where a convention differs
from what the repository does today, the row says so and names the spec that fixes it.

**A spec that proposes a construct not listed here is wrong, not a variation.**

The stack is fixed. This corpus adds no framework, no broker, no database and no
language beyond the rows below; Temporal joined them by decision D-21 as the platform's
workflow engine. What it adds is a decision about how the ones already chosen are used.

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
| Event publication | MongoDB outbox, staged in the business transaction | — | ✓ all three services, [ET-PLT-003](_platform/003-event-contract/) |
| Cross-service bus | Azure Service Bus via Spring Cloud Stream — facts only | Azure 5.19.0 | ✓ |
| Workflow engine | Temporal — Java SDK and Spring Boot starter; every multi-step, cross-service or timed process is a workflow, every recurring job a Schedule | 1.38.0 | ✓ [ET-PLT-015](_platform/015-durable-execution/), D-21 |
| Cache, OTP, rate limits | Redis, reactive | 7.x | ✓ |
| Identity | Keycloak + custom SPI | 26.x | ✓ |
| Gateway | `spring-cloud-starter-gateway-server-webflux` | — | ✓ |
| Router | Apollo Router, **in `docker-resources/apollo-router/ticketing/`** | — | ✓ |

Three services, three subgraphs, one database, one router, one Temporal namespace per
environment. Adding a fourth service is a platform spec, not a feature decision.

Specified by [ET-PLT-001](_platform/001-runtime-baseline/) and [ET-PLT-015](_platform/015-durable-execution/).

---

## 1 · Reactivity

The whole request path is non-blocking, from the router to the driver. One blocking call
on an event-loop thread stalls every concurrent request that thread is carrying, which at
on-sale peak is thousands.

| Rule | Why |
|---|---|
| Every repository, service and resolver method returns `Mono<T>` or `Flux<T>` | The signature is the contract; a method returning `T` has already blocked |
| Never `.block()`, `.blockFirst()`, `.blockLast()`, `.toFuture().get()` | Blocks a Netty worker |
| Never `.subscribe()` to trigger work in a request path | Fire-and-forget: the response is sent before the write happens and the error goes nowhere. Work that must outlive the request is a workflow |
| Compose with `flatMap`, not `map(x -> repo.save(x).block())` | The second is the first mistake spelled differently |
| Genuinely blocking third-party SDKs go on `Schedulers.boundedElastic()`, wrapped once, in `infrastructure/` | Contain the blocking to one adapter class rather than letting it leak into services |
| The Temporal client is reached only through `com.pml.shared.infrastructure.temporal.TemporalGateway` (shared-library); an activity may `.block()` on a reactive chain because it runs on the worker's own thread | The SDK is thread-based; the gateway keeps it off the event loop, and the worker is not the event loop |
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

### The stores, and what each is for

| Store | Holds | Never holds |
|---|---|---|
| **MongoDB** (`ticketing`) | Every business document, in prefixed collections — the system of record | Never framework infrastructure, never workflow history |
| **PostgreSQL** | Keycloak's own schema, reached only by Keycloak; Temporal's history and visibility stores, reached only by the Temporal Service | **Nothing a service connects to** |
| **Temporal** | Each process's history, current step, pending timers and task queues, kept for the namespace's retention after the run ends | **Never the record.** A screen, a report or a reconciliation never reads workflow state |
| **Redis** | OTP codes, idempotency fast paths, rate-limit counters, read-through caches — every key with a TTL | **Never business state, and never a job lock.** A TTL expiry silently destroys it; a workflow id or a Schedule's overlap policy is what stops two pods doing one job |

There is one datastore for business data and one transaction manager. The event that must
accompany a write is staged into `{service}_outbox` **through the same `ReactiveMongoTemplate`
session as the write itself**, so the two commit together or neither does. That is the whole
reason the outbox is in MongoDB: an outbox in a second store is two transaction managers that
cannot enlist together, which means the document can commit while the event row fails — the
exact dual-write failure an outbox exists to prevent, reintroduced by the thing meant to prevent
it. The outbox drain — the one scheduled task a service runs — publishes to the bus afterwards,
outside any transaction.

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
| Optimistic locking (`@Version`) or compare-and-set on every document that carries a balance, a count or a process status | Two concurrent decrements of the same tier is the default case at on-sale, and an activity retried after a crash is the default case for a workflow |
| Inventory decrement is one conditional atomic update, never read-modify-write | `findAndModify` with `available >= qty` in the filter is the only oversell defence that holds |

Specified by [ET-PLT-002](_platform/002-persistence-baseline/).

---

## 3 · Events — facts between services, and the line before them

```
                    ┌──────────────────────────────────────────────────┐
   a service's      │  the workflow's next activity                    │
   own next step    │  or the rest of one transaction                  │
   durable          │  no in-memory event, no listener chain           │
                    └──────────────────────────────────────────────────┘
                    ┌──────────────────────────────────────────────────┐
   the outbox       │  outbox.stage(envelope)  ← SAME reactive tx      │
   atomic           │  → {service}_outbox in MongoDB                   │
                    └──────────────────────────────────────────────────┘
                    ┌──────────────────────────────────────────────────┐
   cross-service    │  outbox drain → EventBridge → Service Bus        │
   at-least-once    │  → topic → subscription per consuming service    │
   unordered        │  → consumer: one transaction, or one signal      │
                    └──────────────────────────────────────────────────┘
```

| Concern | Convention | Repo today |
|---|---|---|
| A step within a service | A workflow activity, or part of one transaction — never `ApplicationEventPublisher` or an event listener | ✓ no in-memory event types remain |
| Cross-service wire name | `{context}.{PastTense}` — `booking.TicketPurchased` | ✓ `EventType`, parsed from ET-PLT-003 §4 |
| Envelope | Every cross-service message carries `eventId`, `eventType`, `occurredAt`, `correlationId`, `causationId`, `schemaVersion`, `payload` | ✓ `EventEnvelope` |
| Version | Integer counter, starts at `1` | ✓ |
| Handler | A consumer guards on `eventId`, then runs one transaction or starts or signals a workflow carrying the `eventId` ([ET-PLT-015](_platform/015-durable-execution/) R6) — never a listener chain | ✓ `ConsumerDispatch` |
| Publisher | Only the outbox drain, through `EventBridge` | ✓ `EventPublicationLintTest` at zero in every service |
| Topics | `catalog-events`, `booking-events`, `identity-events` | ✓ |
| Consumer | Idempotent, keyed on `eventId`: a Redis `SET NX` fast path with a 7-day TTL and a durable marker | ✓ `ConsumerGuard` |

**Three rules that are not negotiable.**

1. **Never call `StreamBridge.send` outside the drain.** Inside a MongoDB transaction, the bus
   commits and the transaction then rolls back, and the platform has told three services about a
   ticket that does not exist. Inside an activity, a retry sends twice and a crash between the
   write and the send loses it. Stage the envelope in the outbox inside the transaction; only the
   drain publishes, outside any transaction.

2. **Never let a delivery failure throw out of a consumer.** A provider being down is a
   business outcome; record it as state, or hand it to the workflow that retries it. Throwing
   dead-letters a message whose only problem is that somebody else's server is slow.

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
| A mutation that moves a process — approve, retry, cancel, confirm — reaches its workflow as an update or signal through a `*Process` facade; no mutation writes a process status directly | A status written beside the workflow is a status the workflow does not know about, and the next timer undoes it |
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
| A refusal from a workflow's update validator or activity leads its message with the `ErrorCode` name (`com.pml.shared.workflow.Refusals`), and the facade translates it back | An update validator's exception type does not survive to the client; the message does |
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
| Group membership `/organizations/{slug}/{role}` | Keycloak, mirrored from Mongo by workflow activities and the repair Schedule |
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
| The authorization check runs before the workflow is reached; a workflow's validator re-checks the business rule (dual control, ownership) | The facade is the security boundary; the workflow is the process boundary, and both must refuse |
| A JWT is validated by JWKS at each service, never trusted because the gateway forwarded it | The gateway is not a security boundary a subgraph can assume |
| Workflow ids, inputs, signals and results carry identifiers, never personal data | Temporal stores history as plain text outside ET-PLT-008's reach; activities load details by id |

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
| A provider's answer is applied only after verification against the provider, whether it arrived by callback, by a workflow's poll, or by recovery | Three paths that each decide an outcome are three places to disagree |
| Money leaves only after the ledger records it leaving, and a verified failure reverses it exactly | A transfer the ledger has not recorded is money the platform cannot account for |
| Rounding is `HALF_UP` at 2 decimal places, applied once, at the point of persistence | Rounding twice is how a ledger drifts by a ngwee a day |

`docs/ARCHITECTURE_REDESIGN_V3_COMPLETE.md` is authoritative on the financial model;
where it and the code disagree, the document is right and the code is the bug.

Specified by [ET-FIN-001](finance/001-escrow-and-ledger/) and
[ET-FIN-002](finance/002-commission/).

---

## 8 · Time

| Rule | Why |
|---|---|
| One application-wide `Clock` bean, `Africa/Lusaka`, injected everywhere outside workflow code | Freeze time once in a test instead of injecting a clock into two hundred classes |
| Inside a workflow, time comes from `Workflow.currentTimeMillis()` and waits from `Workflow.sleep` / `Workflow.await` | The workflow is replayed; a clock read during replay reads a different time and takes a different branch |
| **No `Instant.now()`, `LocalDateTime.now()` or `System.currentTimeMillis()` inline** | 202 inline calls today make every deadline untestable |
| Timestamps are `Instant`; a wall-clock date the user sees is formatted at the edge | Storage is UTC; presentation is local; the two are different concerns |
| Every deadline — reservation TTL, invitation expiry, settlement window, OTP validity — has a boundary test; a workflow deadline is tested under time skipping | What happens at exactly seven days is where invitation bugs live |
| Recurring jobs are Temporal Schedules in UTC with overlap policy `SKIP` | A Schedule belongs to the Temporal Service, so ten pods still mean one run |

Specified by [ET-PLT-001](_platform/001-runtime-baseline/) and [ET-PLT-015](_platform/015-durable-execution/).

---

## 9 · Workflows and long-running processes

A multi-step process that crosses a service, a provider or a moment in time is a **Temporal
workflow** ([ET-PLT-015](_platform/015-durable-execution/), decision D-21). An implicit saga — state
spread across listeners, sweeps and flags — is worse than none, because nothing can resume it.

| Rule | Why |
|---|---|
| Workflows decide; activities do. Workflow code touches no database, broker, provider, clock or random source | A crashed workflow resumes by replaying its code against recorded history; anything read outside `Workflow` replays differently |
| Process state lives in the workflow; the business document carries a `status` projection written only by activities | GraphQL, reconciliation and audit read MongoDB; nobody asks Temporal what a reservation is |
| Every activity is idempotent and advances a status by compare-and-set, staging its outbox rows in the same transaction | Activities run at least once: a worker can finish the write and die before reporting |
| Compensation is an explicit activity, registered before the step it undoes | "What compensated last month" is answerable from the journal and the history, not a log search |
| Timeouts are workflow timers; recurring jobs are Schedules with overlap `SKIP` — never `@Scheduled` sweeps under a Redis lock | A timer fires whether or not the pod that set it is still alive, and a Schedule runs once however many pods there are |
| A workflow id is the business id, built by `WorkflowIds`; each queue is a `TaskQueues` constant and a §4 registry row | A repeated start reaches the same execution, a callback finds it without a lookup table, and the Web UI is a recovery console keyed by what operators already know |
| Resolvers, controllers, consumers and runners reach a workflow only through a `*Process` facade over `TemporalGateway` | One place per workflow decides the id, the conflict policy and how a refusal comes back |
| Workflow code never reads its own workflow id or run id | Run metadata is not reproduced by replay; carry the business id in the start |
| Every workflow type has a layer-3 test that replays its recorded history | An incompatible change fails the build instead of the in-flight processes |
| An execution that would grow past a bounded history continues as new, carrying only its position | A history is capped at 51,200 events or 50 MB |
| An existing record whose process predates its workflow is adopted by a boot runner, not left for a sweep | Adoption gives the record a timer and an owner once, then nothing polls |

The processes this platform runs are the ET-PLT-015 §4 workflow registry.

---

## 10 · Testing — five layers

| Layer | Catches | Needs | Speed | How many |
|---|---|---|---|---|
| 1 · Decision | Wrong business rules, bad state transitions | nothing | ~1 ms | hundreds |
| 2 · Projection / repository | Aggregation pipelines and queries drifting from the documents; activities against real stores; a workflow against the real Temporal server | Testcontainers Mongo, `TemporalDevServer` | ~50 ms | dozens |
| 3 · Workflow | Broken long-running processes, bad compensation, timers, replay | `TestWorkflowEnvironment` with time skipping + stubbed activities + `WorkflowReplayer` | ~50 ms | one per workflow |
| 4 · Contract | Subgraph composition, schema breaking changes | rover | ~1 s | one per subgraph |
| 5 · Integration | Wiring, real concurrency, real money paths | Testcontainers + WireMock | seconds | a handful |

Nearly all tests are layer 1, because nearly all the risk is. If you are asserting on
commission arithmetic inside a Testcontainers test, move it down.

**Always assert that a refused operation persisted nothing.** A test that only checks the
exception still passes if the service wrote the reservation and *then* threw — and an
oversold event is not recoverable by deleting a row.

Repo today: every suite is rewritten against the spec it proves — shared-library 371,
booking 209, catalog 145 and identity 254 tests on 2026-09-13, every workflow replayed.

Specified by [ET-PLT-006](_platform/006-test-harness/).

---

## 11 · Quick reference — forbidden constructs

Every row below is a review rule. Several are enforced by lints; the rest hold only as long as
the person writing the code knows them — which is why the *why* column in each section is not
decoration.

| Forbidden | Use instead |
|---|---|
| `.block()`, `.blockFirst()`, `.toFuture().get()` in production outside an `@ActivityImpl` | Return `Mono`/`Flux` and compose |
| `.subscribe()` to trigger a write or a long job | Return the chain; for work that outlives the request, start a workflow |
| `spring-boot-starter-web` on any service classpath | `spring-boot-starter-webflux` |
| `spring-cloud-starter-gateway` | `spring-cloud-starter-gateway-server-webflux` |
| `spring.cloud.gateway.*` YAML for the WebFlux gateway | `spring.cloud.gateway.server.webflux.*` |
| an outbox in PostgreSQL, or any second store | `{service}_outbox` in MongoDB, staged in the same reactive transaction |
| a `DataSource`, JDBC driver or JPA entity in a service | nothing — no service connects to a relational database |
| `StreamBridge.send` anywhere but `EventBridge` | stage the envelope in the outbox; the drain publishes |
| `ApplicationEventPublisher`, `@EventListener` or `@TransactionalEventListener` between components | the workflow's next activity, or the outbox for another service |
| a `@Scheduled` method for a business timeout, retry or recurring job | a workflow timer, or a Temporal Schedule with overlap `SKIP` |
| a Redis lock to stop two pods running one job | a deterministic workflow id, or a Schedule's overlap policy |
| a mutation, controller or consumer that sets a process status | an update or signal on the workflow, through its `*Process` facade |
| a `WorkflowClient` outside `infrastructure/temporal` and the workflow packages | `TemporalGateway` |
| `Instant.now()`, `Clock`, `UUID.randomUUID()`, a repository or I/O inside workflow code | `Workflow.currentTimeMillis()`, `Workflow.randomUUID()`, an activity |
| `Workflow.getInfo().getWorkflowId()` in workflow logic | the business id, carried in the start |
| a phone number, email or name in a workflow input, signal or result | the id; the activity loads the rest |
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
| a money-moving mutation with no `idempotencyKey` | take one; it names the workflow execution or the persisted record |
| a saga whose state is only the listener sequence, a flag, or a sweep | a Temporal workflow, with the document's `status` as its projection |
| a tenant filter applied in a resolver `if` | a repository-level `organizationId` predicate |
| a GraphQL subscription for dashboard refresh | smart polling (D-12) |
| GraphQL multipart file upload | REST presigned URL (D-11) |
