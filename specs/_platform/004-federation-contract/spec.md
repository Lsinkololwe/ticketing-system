# ET-PLT-004 · Federation contract — ownership, keys, contracts, composition

## 1. Capability

Every client of this platform — the customer web app, the organizer dashboard, the
platform admin console and the mobile app — talks to one endpoint and sees one schema.
Behind it, three services each declare a subgraph, and Apollo Router composes them into a
supergraph and plans each query across them. That composition is the platform's public
contract, and the single most valuable property it can have is that it either composes
cleanly or fails the build. A supergraph that composes with a silently missing subgraph is
a production outage that looks like a successful deploy.

This spec fixes the graph. It declares which service owns which type — total ownership,
one canonical declaration each — and how a service contributes fields to a type it does
not own without claiming it. It declares the tag vocabulary that derives the customer-facing
contract from the admin-facing one, so an admin field cannot leak into a public schema by
omission. It settles the two conventions that a federated graph gets wrong most often:
how lists are paged, and how a mutation reports a refusal. And it makes composition a
build gate rather than a script somebody remembers to run.

It delivers no domain behaviour. Its success criterion is that `rover supergraph compose`
exits non-zero on any breaking change, that the admin contract and the public contract are
both derivable from tags alone, and that no TypeScript type describing a GraphQL shape is
written by hand anywhere in the frontend.

## 2. Design decisions

**Ownership is total, and there is exactly one canonical declaration per type.** The
service that owns a type declares it with `@key(fields: "id")` and resolves it with a
`@DgsEntityFetcher`. Every other service that needs to reference it declares a stub —
`@key(fields: "id", resolvable: false)` with `id` and nothing else — which says *I know
this exists and I cannot fetch it*. A service that needs to add fields uses
`extend type`, which is contribution, not ownership.

**Never redeclare `id` inside an `extend` block.** The stub already declares it, and
adding `id: ID! @external` produces `tried to redefine field 'id'` — a composition failure
whose message points at the schema rather than at the block that caused it. It is called
out here, in a spec, because it is the failure this platform's graph hits most.

**The federation version is one number, in all three subgraphs.** `v2.9`. A subgraph
linking a different version fails composition with a message about directives rather than
about versions, which is a slow way to find it. The same is true of the shared scalars:
`BigDecimal`, `DateTime`, `JSON`, `Long` and `PhoneNumber` are declared identically
everywhere, and `@auth`/`Role` come from one file in `shared-library` that all three put
on the classpath.

**Contracts are derived from tags, and the default is exclusion.** `@tag(name: "admin")`
on every field, type and operation that only the platform console may see;
`@tag(name: "internal")` on anything only another service may call. The public contract
variant excludes both. An untagged field composes into the public schema, so **omission is
the dangerous direction** — a new admin query with no tag is visible to every mobile
client the moment it publishes. The composition gate therefore checks the public contract
too, not just the supergraph.

**Mutations return the thing, and refusals are GraphQL errors.** No
`MutationResponse { success, message }` wrapper. Two error channels means a client must
check both, then checks one, then checks neither — and a `success: false` with HTTP 200
and no GraphQL error is invisible to every piece of generic error handling in the
frontend. A mutation returns the entity or a small purpose-built result type; a refusal
raises the typed error of [ET-PLT-005](../005-error-contract/) carrying
`extensions.errorCode` and `extensions.retryable`. One channel, one place to handle it.

**Two paging shapes, each with a stated reason.** Relay cursor connections
(`XConnection`/`XEdge`/`PageInfo`) for anything a person scrolls — event discovery, my
tickets, a notification feed — because a cursor is stable while the underlying list is
being written to, and an offset is not: page 2 of a list that gained a row shows you row
20 twice. Offset pages (`XPage` with `totalPages`) for admin tables, because an operator
genuinely needs *page 7 of 41* and an admin table is not being written to underneath them
at on-sale rates. Every list field's choice is named in §4; nothing gets to invent a third.

**Entity fetchers resolve by key and nothing else.** A `@DgsEntityFetcher` receives the
key representation and returns the entity. It performs no authorization decision, because
the router calls it on behalf of a query whose authorization was already decided at the
field the client actually asked for — putting a second check here produces confusing
partial results rather than a clean denial.

**The schema workflow has one direction.** Backend `schema.graphqls` → `rover supergraph
compose` → GraphOS publish → `npm run codegen`. A hand-written TypeScript type for a
GraphQL shape is a defect, not a shortcut: it is a copy that drifts, and the drift is only
discovered by a runtime `undefined`. If a type is missing from codegen, it is missing from
the schema.

**Composition failure fails the build, in both directions.** `rover subgraph check` on
every pull request that touches SDL; `rover subgraph publish` on merge; and
`compose-supergraph.sh --static` in CI so composition is provable without running three
services. The script must exit non-zero on failure — a script that warns and exits 0 is
how a broken subgraph vanishes from the supergraph while every build stays green.

**The router configuration lives in `docker-resources`, not here.** Three router profiles
already exist there — local, GraphOS and production. This repository owns subgraph SDL and
nothing else about the graph's runtime.

**Rejected alternatives**

- *`MutationResponse { success, message, errors }` on every mutation.* The dominant convention in the wild and the reason so much frontend error handling has two branches that disagree.
- *A single paging convention everywhere.* Cursors make *page 7 of 41* impossible; offsets make an infinite scroll over a live list duplicate rows. Two shapes, each justified per field, is the honest answer.
- *`@key(fields: "id")` on a stub.* Makes the router believe it can fetch, and it then plans a query the service cannot answer.
- *Schema-first types hand-mirrored in TypeScript for speed.* Every one of them is a copy with no owner.
- *Authorization inside entity fetchers.* Produces `null` entities inside otherwise successful responses, which reads as a bug rather than as a denial.
- *Composing the supergraph only from running services.* Cannot run in CI without standing up three services and a database, so in practice it does not run in CI.

## 3. Requirements

### ET-PLT-004-R1 · Each type has one owner and one canonical declaration

THE SYSTEM SHALL declare each entity type with `@key` in exactly one subgraph, and IF
another subgraph references it, THEN THE SYSTEM SHALL declare a non-resolvable stub or
extend it.

**Acceptance**
- [ ] Every type in the §4 ownership registry is declared with `@key(fields: "id")` in its owning subgraph and nowhere else
- [ ] Every referenced-but-not-owned type is declared `@key(fields: "id", resolvable: false)` carrying `id` and no other field
- [ ] Every contributing subgraph uses `extend type X @key(fields: "id")` and declares no `id` inside the extend block
- [ ] Every owned type has exactly one `@DgsEntityFetcher(name = "X")` in its owning service, resolving by key
- [ ] No entity fetcher performs an authorization check or returns null for an authorization reason
- [ ] No `id` is redeclared inside an `extend type` block, all subgraphs link one federation version, the shared scalars agree, and the supergraph composes

### ET-PLT-004-R2 · Subgraphs agree on federation version, scalars and directives

THE SYSTEM SHALL link one federation version and declare one identical set of shared
scalars and directives across all three subgraphs.

**Acceptance**
- [ ] All three subgraphs `@link` `https://specs.apollo.dev/federation/v2.9` and no other version
- [ ] `BigDecimal`, `DateTime`, `JSON`, `Long` and `PhoneNumber` are declared in all three, with identical definitions
- [ ] `@auth` and `Role` come from `shared-library/src/main/resources/graphql/auth.graphqls`, on every service's classpath, and are declared in no service's own SDL
- [ ] Every scalar has a registered DGS coercion and a round-trip test — `BigDecimal` in particular serialises as a decimal string, never a float
- [ ] All three services resolve identical DGS and `graphql-java` versions

### ET-PLT-004-R3 · The public contract is derived from tags, and omission excludes nothing

THE SYSTEM SHALL tag every admin-only and internal-only element, and the public contract
variant SHALL exclude both.

**Acceptance**
- [ ] Every query, mutation, type and field intended for the platform console carries `@tag(name: "admin")`
- [ ] Every element intended only for service-to-service use carries `@tag(name: "internal")`
- [ ] The `public` contract variant is configured to exclude `admin` and `internal`, and composes
- [ ] A test asserts the public contract contains no field whose name matches the admin vocabulary of §4 — `platformRevenueAccount`, `stuckTransactions`, `systemHealth`, `suspend*`, `resolve*Issue`
- [ ] A new untagged admin operation fails that test rather than shipping to mobile clients
- [ ] Both contract variants are published, and the public variant is what the customer and mobile apps generate from

### ET-PLT-004-R4 · Mutations return the entity; refusals are typed errors

THE SYSTEM SHALL return the affected entity or a purpose-built result from every mutation,
and IF the operation is refused, THEN THE SYSTEM SHALL raise a GraphQL error carrying the
registry code.

**Acceptance**
- [ ] No mutation returns a type carrying a `success: Boolean` field
- [ ] No mutation returns a type carrying a `message: String` field intended to convey failure
- [ ] Every refusal reaches the client as a GraphQL error with `extensions.errorCode` and `extensions.retryable` (ET-PLT-005)
- [ ] A mutation that partially succeeds is a design error, not a payload shape — each mutation either applies or refuses
- [ ] Result types that are not entities are named `{Verb}{Noun}Result` and carry only what the caller cannot already read

### ET-PLT-004-R5 · Every list field uses the paging shape §4 names for it

THE SYSTEM SHALL page person-facing lists with Relay cursor connections and admin tables
with offset pages, per the §4 registry.

**Acceptance**
- [ ] `PageInfo`, `{X}Connection` and `{X}Edge` are declared once, identically, in all three subgraphs
- [ ] `{X}Page` with `content`, `totalElements`, `totalPages`, `page` and `size` is declared once, identically
- [ ] Every list-returning field is a row of the §4 paging registry and uses the shape that row names
- [ ] No list field returns a bare `[X!]!` unless §4 marks it *bounded* with the bound stated
- [ ] Cursor connections expose `totalCount` only where the count is cheap; where it is not, the row says so and the field is absent
- [ ] Every paged query has a maximum page size enforced server-side, and a request above it is refused rather than silently clamped

### ET-PLT-004-R6 · Composition is a build gate

THE SYSTEM SHALL fail the build when the supergraph does not compose or a change breaks an
existing consumer.

**Acceptance**
- [ ] `compose-supergraph.sh --static` composes from on-disk SDL with no running service, and exits non-zero on failure
- [ ] CI runs `rover subgraph check` on every pull request touching a `schema.graphqls`, against the published graph
- [ ] CI runs `rover subgraph publish` on merge to the default branch, for each of the three subgraphs
- [ ] A deliberately broken subgraph — a redeclared `id`, a mismatched scalar, a `@key` on a type another subgraph owns — fails CI in a test that asserts it fails
- [ ] No Apollo Router configuration file exists anywhere under `ticketing-system/`

### ET-PLT-004-R7 · Frontend types come from codegen, in one direction

THE SYSTEM SHALL generate every frontend GraphQL type from the composed schema, and no
GraphQL shape SHALL be declared by hand.

**Acceptance**
- [ ] `npm run codegen` regenerates all GraphQL TypeScript types from the published contract
- [ ] No hand-written TypeScript interface or type alias describes a GraphQL type, input or enum
- [ ] The customer and mobile apps generate from the `public` contract; the admin app generates from the full schema
- [ ] CI fails if generated types are stale — codegen is run and the working tree must be clean afterwards
- [ ] A missing type is added to the backend schema first; the frontend never patches around it

## 4. Model

### Type ownership registry

| Type | Owner | Stubbed by | Extended by |
|---|---|---|---|
| `User` | identity | catalog, booking | booking (`purchasedTickets`, `totalSpent`) |
| `Organization` | identity | catalog, booking | catalog (`events`), booking (`bankAccounts`, `payoutRequests`, `availableBalance`) |
| `OrganizationMember` | identity | — | — |
| `TeamInvitation` | identity | — | — |
| `OwnershipTransfer` | identity | — | — |
| `EventAccessGrant` | identity | — | — |
| `VerificationDocument` | identity | — | — |
| `Notification` | identity | — | — |
| `NotificationPreferences` | identity | — | — |
| `UserDevice` | identity | — | — |
| `Event` | catalog | identity, booking | identity (`accessGrants`), booking (`tickets`, `ticketsSold`, `grossRevenue`, `escrowAccount`) |
| `TicketTier` | catalog | booking | booking (`availableQuantity`, `soldQuantity`) |
| `Location` | catalog | — | — |
| `City` | catalog | — | — |
| `Province` | catalog | — | — |
| `Category` | catalog | — | — |
| `Ticket` | booking | — | — |
| `Reservation` | booking | — | — |
| `PaymentIntent` | booking | — | — |
| `EscrowAccount` | booking | — | — |
| `JournalEntry` | booking | — | — |
| `CommissionRecord` | booking | — | — |
| `PayoutRequest` | booking | — | — |
| `BankAccount` | booking | — | — |
| `RefundRequest` | booking | — | — |
| `Chargeback` | booking | — | — |
| `PromoCode` | booking | — | — |
| `PlatformAccount` | booking | — | — |

`TicketTier.availableQuantity` is contributed by **booking**, not catalog, because
`booking_tier_inventory` is where the counters live ([ET-PLT-002](../002-persistence-baseline/) §2).
Catalog owns the tier's definition; booking contributes its state.

### The three declaration shapes

```graphql
# owner — catalog-service
type Event @key(fields: "id") {
    id: ID!
    title: String!
    startsAt: DateTime!
    organization: Organization!
}

# stub — booking-service: I reference it, I cannot fetch it
type Event @key(fields: "id", resolvable: false) {
    id: ID!
}

# contribution — booking-service. NOTE: no `id` here. Never.
extend type Event @key(fields: "id") {
    tickets(page: Int, size: Int): TicketPage!  @tag(name: "admin")
    ticketsSold: Int!
    grossRevenue: BigDecimal!                   @tag(name: "admin")
    escrowAccount: EscrowAccount                @tag(name: "admin")
}
```

### Shared SDL

| Element | Declared in | Notes |
|---|---|---|
| `@auth(requires: Role)`, `enum Role` | `shared-library/src/main/resources/graphql/auth.graphqls` | on all three classpaths; merged by DGS at runtime and prepended by `compose-supergraph.sh --static` |
| `scalar BigDecimal` | all three, identically | serialises as a **decimal string** |
| `scalar DateTime` | all three, identically | ISO-8601 `Instant` |
| `scalar JSON`, `scalar Long`, `scalar PhoneNumber` | all three, identically | |
| `type PageInfo`, `{X}Connection`, `{X}Edge` | all three, identically | Relay |
| `type {X}Page` | all three, identically | offset |

```graphql
type PageInfo { hasNextPage: Boolean!  hasPreviousPage: Boolean!
                startCursor: String    endCursor: String }
```

### Paging registry

| Field | Subgraph | Shape | Why |
|---|---|---|---|
| `events` (discovery) | catalog | connection | infinite scroll over a live list |
| `eventsByCategory`, `eventsByCity` | catalog | connection | same |
| `myTickets` | booking | connection | a person's list, scrolled |
| `myNotifications` | identity | connection | a feed |
| `Event.tickets` | booking | **page** | an admin table |
| `organizationMembers` | identity | page (bounded, ≤ 500) | small, and shown as a table |
| `pendingInvitations` | identity | **bounded list**, ≤ 100 | small by construction; a larger count is itself a problem |
| `organizerApplications` | identity | page | *page 7 of 41* |
| `payoutRequests` | booking | page | finance table |
| `transactionsForReview`, `stuckTransactions` | booking | page | operator table |
| `auditLogs` | identity | page | operator table |

Maximum page size is 100 for offset pages and 100 for `first`/`last` on connections;
a request above it is refused with `PAGE_SIZE_EXCEEDED`, never clamped — a clamped page is
a client that believes it has read everything.

### Tag vocabulary

| Tag | Meaning | Excluded from |
|---|---|---|
| `admin` | only the platform console may see this | `public` contract |
| `internal` | only another service may call this | `public` contract |
| *(untagged)* | visible to every client | — |

Contract variants published to GraphOS:

| Variant | Includes | Generated by |
|---|---|---|
| full supergraph | everything | `apps/admin` |
| `public` | excludes `admin`, `internal` | `apps/ticketing`, `apps/organization-admin`, mobile |

### Composition and CI

| Step | Command | When |
|---|---|---|
| offline composition | `compose-supergraph.sh --static` | every CI run |
| breaking-change check | `rover subgraph check <name> --schema …` | every PR touching SDL |
| publish | `rover subgraph publish <name> --schema …` | merge to default branch |
| codegen freshness | `npm run codegen && git diff --exit-code` | every CI run |

All router and supergraph configuration lives in
`../docker-resources/apollo-router/ticketing/`.

## 5. Tasks

- [ ] **T1 · Reconcile ownership: one `@key` per type, stubs elsewhere, no `id` in extends**
  - requirements: R1
  - files: `backend/*/src/main/resources/graphql/schema.graphqls`
  - verify: no `id` is redeclared inside an `extend type` block, all subgraphs link one federation version, the shared scalars agree, and the supergraph composes
  - parallel-safe: no — the three SDL files must agree
  - depends: —

- [ ] **T2 · One `@DgsEntityFetcher` per owned type; strip authorization from all of them**
  - requirements: R1
  - files: `backend/*/src/main/java/com/pml/*/web/graphql/federation/`
  - verify: a cross-subgraph query resolving each entity by key
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T3 · Align federation version, scalars and shared SDL across all three**
  - requirements: R2
  - files: the three `schema.graphqls`, `shared-library/.../auth.graphqls`
  - verify: `compose-supergraph.sh --static`; `BigDecimal` round-trip test
  - parallel-safe: no — shared definitions
  - depends: T1

- [ ] **T4 · Tag every admin and internal element; configure the `public` contract**
  - requirements: R3
  - files: the three `schema.graphqls`, GraphOS contract configuration
  - verify: the public contract composes and contains no admin vocabulary
  - parallel-safe: yes — one service per agent
  - depends: T3

- [ ] **T5 · Remove every `success`/`message` mutation wrapper**
  - requirements: R4
  - files: the three `schema.graphqls`, every mutation resolver
  - verify: no mutation return type declares `success`
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T6 · Declare `PageInfo`/`Connection`/`Edge`/`Page`; apply the §4 registry**
  - requirements: R5
  - files: the three `schema.graphqls`, every list resolver
  - verify: every list field matches its registry row; an over-limit page size is refused
  - parallel-safe: yes — one service per agent
  - depends: T3

- [ ] **T7 · CI: static composition, `subgraph check`, `subgraph publish`, codegen freshness**
  - requirements: R6, R7
  - files: `.github/workflows/`
  - verify: a deliberately broken subgraph fails CI, asserted by a test
  - parallel-safe: no — one workflow
  - depends: T3

- [ ] **T8 · Point codegen at the contracts; delete every hand-written GraphQL type**
  - requirements: R7
  - files: `frontend/web/codegen.ts`, `frontend/web/libs/shared/src/types/`
  - verify: `npm run codegen && git diff --exit-code`
  - parallel-safe: no — one codegen configuration
  - depends: T4

## 6. Out of scope

| Capability | Spec |
|---|---|
| Dependency versions, module boundaries | [ET-PLT-001](../001-runtime-baseline/) |
| What each type's fields mean and where they persist | the spec that introduces the type |
| Error codes, `ErrorType` mapping, `extensions` shape | [ET-PLT-005](../005-error-contract/) |
| Who may call which operation, and `@auth`'s runtime behaviour | [ET-PLT-007](../007-security-and-authorization/) |
| Router runtime configuration, GraphOS credentials, deployment | `docker-resources/apollo-router/ticketing/` |
| Field-level deprecation windows and schema evolution policy | [ET-PLT-010](../010-schema-evolution/) |
| Query cost limiting and depth limiting | [ET-PLT-011](../011-rate-limiting-and-abuse/) |

Deliberately never in scope: **GraphQL subscriptions** (D-12 — dashboards poll), and
**GraphQL multipart uploads** (D-11 — uploads are REST presigned URLs).
