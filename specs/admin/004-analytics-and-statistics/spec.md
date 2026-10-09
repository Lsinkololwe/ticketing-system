# ET-ADM-004 · Dashboard analytics and the polling contract

> **Conformance** · MISSING_FEATURES Part 1 dashboard analytics · MISSING_FEATURES Part 5 real-time updates

## 1. Capability

Three audiences ask the platform for numbers. An **organizer** wants to know how their event
is selling and what they will be paid. A **platform administrator** wants to know how many
users, organizations and events exist and how they are trending. A **finance operator**
wants revenue, commission and settlement figures they can trust against the ledger.

Each of those is a different query with a different scope, and every one of them is a
counting problem that is easy to get wrong in the same three ways: counting client-side over
a fetched page, computing a figure two different ways in two different screens, and refreshing
by hammering the API.

This spec fixes all three. It declares that every statistic is a **server-side MongoDB
aggregation with `$match` first**, that every figure has exactly one definition in one place,
and that dashboards refresh by **polling on a declared schedule** (D-12) rather than by
subscription or by a timer somebody set to two seconds.

It also draws the line the platform must not cross: **analytics never invents a financial
figure**. Revenue, commission and settlement come from the ledger
([ET-FIN-001](../../finance/001-escrow-and-ledger/)), not from summing tickets, because two
sources for one number is how a dashboard and a trial balance disagree in public.

## 2. Design decisions

**Every statistic is a server-side aggregation with `$match` first.** Counting in the
resolver over a fetched page is wrong the moment there is a second page, and it is the
single most common way a dashboard lies. `$match` first is stated explicitly because an
aggregation that sorts or projects before matching scans the collection.

**Every figure has one definition, in one place, used by every consumer.** "Tickets sold"
means tickets in `ISSUED` or `VALIDATED` — not `REFUNDED`, not `TRANSFER_PENDING`. That
definition lives in one aggregation, and the organizer dashboard, the admin dashboard and
the export all call it. Two definitions is two numbers, and somebody notices.

**Financial figures come from the ledger, never from summing tickets.** Revenue is the
`4010` balance. Commission collected is `2020`. Escrow held is the sum of escrow balances.
Summing `commissionAmount` across tickets gives a number that is *nearly* right and diverges
the moment a refund settles — and reconciling a dashboard against a trial balance in public
is a bad afternoon.

**Dashboards poll; there are no subscriptions (D-12).** Apollo Router's managed federation
does not carry subscriptions on this platform's path, and half-building a transport is worse
than polling honestly. Each dashboard declares its interval, pauses when the tab is hidden,
and backs off when a request fails.

**Poll intervals are per widget and are declared here.** A check-in count during an event
wants 15 seconds. A monthly revenue chart wants 5 minutes. One global interval either
hammers the API for the chart or makes the check-in count useless.

**Expensive aggregations are pre-computed on a schedule; cheap ones run live.** The §4 table
marks each. Platform-wide trends over all time are nightly rollups; an event's live sales
count is a live aggregation over an indexed range. Deciding per statistic rather than
globally is what keeps both fast.

**Every figure carries the instant it was computed.** A pre-computed rollup shown without
its timestamp is a number a user believes is current. `computedAt` is on every response.

**Analytics is read-only and never on the write path.** No aggregation runs inside a
transaction, no dashboard query blocks a purchase, and heavy rollups run against a secondary
where one exists ([ET-FIN-005](../../finance/005-reconciliation/) R7's rule applies here
too).

**Rejected alternatives**

- *Client-side counting over a fetched page.* Wrong from the second page onward.
- *GraphQL subscriptions for dashboards.* The transport is not on this platform's path; half-building it is worse than polling.
- *One global poll interval.* Either hammers the API or makes live figures useless.
- *Summing ticket rows for revenue.* Nearly right, and it diverges from the ledger the moment a refund settles.
- *Pre-computing everything nightly.* An organizer watching an on-sale sees yesterday's number.
- *Computing everything live.* A platform-wide all-time trend scans every collection on every dashboard load.
- *A figure without its computation time.* A user believes a rollup is current.

## 3. Requirements

### ET-ADM-004-R1 · Every statistic is a server-side aggregation, `$match` first

THE SYSTEM SHALL compute every statistic in MongoDB with a `$match` stage first, and no
resolver SHALL count in memory.

**Acceptance**
- [ ] Every statistic in §4 is a repository aggregation, not a resolver loop
- [ ] Every pipeline begins with `$match`; a test asserts the first stage of each
- [ ] No resolver calls `.count()` on a fetched list or sums a page
- [ ] `explain()` reports `IXSCAN` on the `$match` stage of every pipeline
- [ ] A statistic over an unbounded range is refused or pre-computed — never scanned live
- [ ] Each aggregation has a test asserting its figure against a seeded fixture

### ET-ADM-004-R2 · Every figure has exactly one definition

THE SYSTEM SHALL define each figure once and SHALL use that definition everywhere it
appears.

**Acceptance**
- [ ] The §4 definitions table names the exact filter for every figure
- [ ] "Tickets sold" is `ISSUED` + `VALIDATED` everywhere it appears — organizer, admin and export
- [ ] "Active users" is one definition, stated in §4, used by every consumer
- [ ] A figure appearing on two dashboards is computed by one method
- [ ] A test asserts the organizer's and the admin's view of one event's sold count are identical
- [ ] Adding a figure adds a row to §4 in the same commit

### ET-ADM-004-R3 · Financial figures come from the ledger

WHEN a financial figure is reported, THE SYSTEM SHALL derive it from the ledger and SHALL
NOT compute it from transactional rows.

**Acceptance**
- [ ] Platform revenue is the `4010 Earned Revenue` balance
- [ ] Commission collected is the `2020 Pending Commission` balance
- [ ] Escrow held is the sum of `booking_escrow_accounts.currentBalance`
- [ ] Gross transaction value is the sum of `1010` debits over the period
- [ ] No financial figure sums `booking_tickets.commissionAmount` or `netAmount`
- [ ] A test seeds a purchase and a refund and asserts every financial figure matches the trial balance exactly
- [ ] Where a dashboard figure and the trial balance disagree, the dashboard is the defect

### ET-ADM-004-R4 · Dashboards poll on a declared interval

THE SYSTEM SHALL refresh dashboards by polling at the §4 intervals, and no subscription
SHALL exist.

**Acceptance**
- [ ] Every widget's poll interval is a row of the §4 table
- [ ] No GraphQL subscription is declared in any subgraph
- [ ] Polling pauses when the document is hidden and resumes on focus
- [ ] A failed poll backs off exponentially to a stated maximum and recovers
- [ ] The client sends no poll while a previous one is in flight
- [ ] A test asserts the composed schema declares no `Subscription` type

### ET-ADM-004-R5 · Expensive statistics are pre-computed and carry their timestamp

WHERE a statistic is marked pre-computed, THE SYSTEM SHALL compute it on a schedule and
SHALL report when.

**Acceptance**
- [ ] The §4 table marks each statistic `live` or `rollup`
- [ ] Rollups are computed by each service's rollup workflow, started by Temporal Schedules with overlap `SKIP`, and stored by that service in its own `{catalog,booking,identity}_statistics_rollups`
- [ ] No service writes a rollup row into another service's collection; the dashboard composes the three over the graph
- [ ] Every response carries `computedAt`, for live and rollup figures alike
- [ ] A rollup older than twice its interval is flagged stale in the response
- [ ] A rollup job failure alerts and leaves the previous rollup in place rather than clearing it
- [ ] Live statistics are bounded to a range the index serves; an unbounded request is refused
- [ ] A test asserts a rollup's `computedAt` is present and correct

### ET-ADM-004-R6 · Each audience sees its own scope, enforced at the repository

THE SYSTEM SHALL scope every statistic to the requesting actor's permitted data.

**Acceptance**
- [ ] Organizer statistics are filtered to their organizations at the repository ([ET-PLT-007](../../_platform/007-security-and-authorization/) R4)
- [ ] Platform statistics require `ADMIN`; financial ones require `FINANCE`
- [ ] An organizer cannot obtain a platform-wide figure through any query
- [ ] Every admin statistic carries `@tag(name: "admin")`
- [ ] A test requests every organizer statistic as a second organization and asserts no data
- [ ] Cross-tenant aggregation happens only through the `isPlatformWide` path, which is audited

### ET-ADM-004-R7 · Analytics never touches the write path

THE SYSTEM SHALL run analytics without contending with purchases.

**Acceptance**
- [ ] No aggregation runs inside a transaction
- [ ] Rollup jobs read from a secondary where the deployment provides one
- [ ] Rollup jobs are scheduled off-peak and are batched
- [ ] A load test runs every rollup during a simulated on-sale and asserts reservation latency is unaffected beyond a stated bound
- [ ] A dashboard query has a timeout, and exceeding it returns a partial result flagged as such rather than hanging
- [ ] Live aggregations are capped at `admin.analytics.max-range` (P90D); a wider range must use a rollup

## 4. Model

### Figure definitions — the single source

| Figure | Definition |
|---|---|
| tickets sold | `booking_tickets` where `status` ∈ `{ISSUED, VALIDATED}` |
| tickets refunded | `booking_tickets` where `status = REFUNDED` |
| tickets checked in | `booking_checkins`, distinct `ticketId` |
| gross transaction value | Σ `1010` debits, `entryType = TICKET_SALE`, over the period |
| platform revenue | `4010 Earned Revenue` balance |
| commission collected | `2020 Pending Commission` balance |
| escrow held | Σ `booking_escrow_accounts.currentBalance` where `status ≠ CLOSED` |
| refunds paid | Σ `2030` debits over the period |
| active users | `identity_users` where `accountStatus = ACTIVE` |
| new users | `identity_users` where `createdAt` in the period |
| active organizations | `identity_organizations` where `status = ACTIVE` |
| published events | `catalog_events` where `status = PUBLISHED` and `endsAt` in the future |
| conversion rate | confirmed reservations ÷ created reservations, over the period |
| sell-through | tickets sold ÷ capacity, per event |

Fourteen definitions. Every dashboard figure is one of these or is composed from them.

### Statistics by audience

**Organizer** — scoped to their organizations.

| Statistic | Kind | Poll | Source |
|---|---|---|---|
| `eventSalesSummary(eventId)` | live | `PT30S` | `booking_tickets`, `booking_tier_inventory` |
| `eventRevenueSummary(eventId)` | live | `PT60S` | ledger, scoped to the event's escrow |
| `checkInSummary(eventId)` | live | `PT15S` | [ET-TKT-003](../../ticketing/003-validation-and-checkin/) R7 |
| `organizationSummary(organizationId)` | rollup, hourly | `PT5M` | rollup |
| `salesTimeSeries(eventId, granularity)` | live ≤ 90 d | `PT60S` | `booking_tickets` by `createdAt` |
| `tierPerformance(eventId)` | live | `PT60S` | per-tier sold and sell-through |

**Platform administrator** — `ADMIN`.

| Statistic | Kind | Poll | Source |
|---|---|---|---|
| `userStats` | rollup, hourly | `PT5M` | `identity_users` |
| `organizationStats` | rollup, hourly | `PT5M` | `identity_organizations` |
| `eventStats` | rollup, hourly | `PT5M` | `catalog_events` |
| `ticketStats` | rollup, hourly | `PT5M` | `booking_tickets` |
| `platformSummary` | rollup, hourly | `PT5M` | composed |
| `growthTrends(from, to, granularity)` | rollup, nightly | `PT30M` | `catalog_statistics_rollups` |
| `topCities(limit)` | rollup, nightly | `PT30M` | events by city |
| `topCategories(limit)` | rollup, nightly | `PT30M` | events by category |

**Finance** — `FINANCE`, every figure from the ledger.

| Statistic | Kind | Poll | Source |
|---|---|---|---|
| `revenueSummary(from, to)` | live ≤ 90 d | `PT5M` | ledger |
| `commissionSummary(from, to)` | live ≤ 90 d | `PT5M` | ledger + `booking_commission_records` |
| `escrowSummary` | live | `PT5M` | escrow balances |
| `payoutSummary(from, to)` | live ≤ 90 d | `PT5M` | `booking_payout_requests` |
| `settlementSummary(from, to)` | live ≤ 90 d | `PT5M` | ledger `1010`, `1020` |

Every finance figure is reconcilable to `trialBalance`
([ET-FIN-005](../../finance/005-reconciliation/) R1) — that is what R3 asserts.

### The rollup

`catalog_statistics_rollups`, `booking_statistics_rollups`, `identity_statistics_rollups`
— identical shape, one per writing service. A service rolls up only the domain it owns,
and the dashboard composes the three across the graph. There is no shared rollup
collection, because a collection with three writers has no owner.

| Field | Notes |
|---|---|
| `_id`, `statisticKey`, `scope`, `scopeId` | `scope` is `PLATFORM` or `ORGANIZATION` |
| `granularity` | `HOURLY`, `DAILY`, `MONTHLY` |
| `periodStart`, `periodEnd` | |
| `values` | `Map<String, Object>` — the figures |
| `computedAt`, `durationMs` | |

`{statisticKey, scope, scopeId, granularity, periodStart}` is unique, so a re-run of a
period replaces rather than duplicates.

| Schedule | Starts | Queue | Cadence (UTC) | Overlap |
|---|---|---|---|---|
| `{service}-rollup-hourly` | `BookingRollupWorkflow`, `CatalogRollupWorkflow`, `IdentityRollupWorkflow` with `HOURLY` | `booking-recon`, `catalog-lifecycle`, `identity-onboarding` | every hour at :17 | `SKIP` |
| `{service}-rollup-nightly` | the same types with `NIGHTLY` | the same queues | daily 01:30 | `SKIP` |

Each service's Schedules start its own workflow type; no service computes another's rollup. A
failed run leaves the previous rollup in place and the next fire tries again.

### The pipeline shape

```java
// $match FIRST — an aggregation that sorts or projects before matching scans the collection
Aggregation.newAggregation(
    match(Criteria.where("eventId").is(eventId)
            .and("status").in(ISSUED, VALIDATED)),          // the ONE definition of "sold"
    group("tierId").count().as("sold")
                   .sum("netAmount").as("net"),
    sort(Sort.Direction.DESC, "sold")
);
```

### The polling contract

| Rule | Value |
|---|---|
| interval | per widget, from the §4 tables |
| pause | when `document.hidden` |
| backoff | exponential on failure to `admin.analytics.max-backoff` (PT5M) |
| overlap | never — no poll while one is in flight |
| jitter | ±10%, so replicas do not synchronise |

No `Subscription` type exists in any subgraph. R4's last box asserts it.

### GraphQL

Split by subgraph — each service exposes statistics over its own data, and composed figures
are assembled by the router.

| Subgraph | Statistics |
|---|---|
| `catalog` | `eventStats`, `topCities`, `topCategories`, `sellThrough` |
| `booking` | `ticketStats`, `eventSalesSummary`, `checkInSummary`, every finance figure |
| `identity` | `userStats`, `organizationStats` |

`platformSummary` and `growthTrends` are composed client-side from the three subgraphs'
rollups rather than by a cross-service aggregation, because no service may read another's
collections ([ET-PLT-002](../../_platform/002-persistence-baseline/) R2).

Every admin statistic carries `@tag(name: "admin")`; every finance statistic requires
`FINANCE`.

### Configuration

| Property | Value |
|---|---|
| `admin.analytics.max-range` | `P90D` for live aggregations |
| `admin.analytics.query-timeout` | `PT10S` |
| `admin.analytics.max-backoff` | `PT5M` |
| `admin.analytics.rollup-hourly-schedule` | `17 * * * *` UTC |
| `admin.analytics.rollup-nightly-schedule` | `30 1 * * *` UTC |
| `admin.analytics.poll-jitter` | 10% |

### Error codes

None introduced. A range beyond `max-range` is refused with `COMMAND_NOT_WELL_FORMED`
carrying the bound.

## 5. Tasks

- [ ] **T1 · The fourteen figure definitions, each as one aggregation**
  - requirements: R1, R2
  - files: `backend/*/src/main/java/com/pml/*/repository/impl/StatsRepository.java`
  - verify: every pipeline's first stage is `$match`; `explain()` reports `IXSCAN`
  - parallel-safe: yes — one service per agent
  - depends: —

- [ ] **T2 · Financial figures from the ledger, reconciled to the trial balance**
  - requirements: R3
  - files: `backend/booking-service/.../repository/impl/FinancialStatsRepository.java`
  - verify: a seeded purchase and refund match the trial balance exactly
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · The rollup document, the two Schedules and the staleness flag**
  - requirements: R5
  - files: `backend/*/src/main/java/com/pml/*/workflow/rollup/`
  - verify: a failed job leaves the previous rollup; `computedAt` is always present
  - parallel-safe: yes — one service per agent
  - depends: T1

- [ ] **T4 · Repository-level scoping for every organizer statistic**
  - requirements: R6
  - files: every stats repository
  - verify: a second organization's ids return nothing across every organizer statistic
  - parallel-safe: yes
  - depends: T1

- [ ] **T5 · The polling client contract: interval, pause, backoff, jitter, no overlap**
  - requirements: R4
  - files: `frontend/web/libs/shared/src/api/polling/`
  - verify: no poll while one is in flight; polling pauses when hidden
  - parallel-safe: yes
  - depends: —

- [ ] **T6 · Assert no `Subscription` type in the composed schema**
  - requirements: R4
  - files: `backend/*/src/test/.../SchemaContractTest.java`
  - verify: the composed supergraph declares no `Subscription`
  - parallel-safe: yes
  - depends: —

- [ ] **T7 · Range caps, query timeouts and the partial-result flag**
  - requirements: R7
  - files: every stats resolver
  - verify: a 180-day live range is refused; a timeout returns a flagged partial
  - parallel-safe: yes
  - depends: T1

- [ ] **T8 · Secondary reads, off-peak scheduling and the contention test**
  - requirements: R7
  - files: `backend/*/src/main/java/com/pml/*/config/MongoConfig.java`
  - verify: reservation latency during a full rollup stays within the stated bound
  - parallel-safe: no
  - depends: T3

- [ ] **T9 · The subgraph halves; `@tag(name: "admin")` on every admin figure**
  - requirements: R6
  - files: all three `schema.graphqls`
  - verify: the public contract exposes no admin or finance statistic
  - parallel-safe: no — three SDL files
  - depends: T4

## 6. Out of scope

| Capability | Spec |
|---|---|
| The ledger every financial figure derives from | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| The trial balance these figures reconcile to | [ET-FIN-005](../../finance/005-reconciliation/) |
| The live check-in summary during an event | [ET-TKT-003](../../ticketing/003-validation-and-checkin/) |
| The approvals workbench's own metrics | [ET-ADM-001](../001-approvals-workbench/) |
| The recovery queue's risk figures | [ET-ADM-003](../003-transaction-recovery/) |
| Operational metrics, tracing and alerting | [ET-ADM-005](../005-observability-and-health/) |
| Tenant scoping mechanics | [ET-PLT-007](../../_platform/007-security-and-authorization/) |
| Data export and its PII handling | [ET-PLT-008](../../_platform/008-data-protection/) |

Deliberately never in scope: **GraphQL subscriptions** (D-12 — the transport is not on this
platform's path), **client-side counting** (wrong from the second page onward), and
**summing ticket rows for revenue** (it diverges from the ledger the moment a refund
settles).

---

## Amendment, 2026-10-04 — user growth

### ET-ADM-004-R9 · User-growth series

**Acceptance**
- [ ] `userGrowthSeries(from, to, bucket, role)` returns new accounts per day, week (Monday) or month in the platform time zone, with the running total
- [ ] Every bucket in the range is present, with zero where nobody joined; a range that runs backwards or exceeds ten years is refused

**Tests** `GrowthBucketsTest` (L1)
