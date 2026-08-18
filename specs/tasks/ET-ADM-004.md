# ET-ADM-004 · Dashboard analytics and the polling contract — tasks

> **Spec** [`specs/admin/004-analytics-and-statistics/spec.md`](../admin/004-analytics-and-statistics/spec.md) · **Wave 6** · `blocked_by:` ET-PLT-004, 007, ET-FIN-001, ET-FIN-005, ET-TKT-003
> **Screens** `Admin - Analytics & Statistics.dc.html`, `Admin - Dashboard.dc.html`, `Org Admin - Dashboard.dc.html` — **read all three**
> **Routes** admin `(dashboard)/dashboard`, `analytics/{page,revenue,users}` · org-admin `(dashboard)/dashboard`, `analytics`
> **Verify** `mvn -q -f backend test -Dgroups=ET-ADM-004 -DfailIfNoTests=true`

**Fourteen figures**, each one aggregation, `$match` first (**D-13**), never client-side counting
over a fetched page. Freshness by **smart polling, not subscriptions** (**D-12**) — Apollo Router's
managed federation does not carry subscriptions on the path this platform uses, and polling with a
visibility-aware interval is honest about that rather than half-building a transport.

## R0 · Reconcile — and this one has a known answer

> ⚠️ **The admin dashboard is currently mock data.** Nine fabricated values, no data hooks, and the
> real queries that exist go unused. Treat the entire dashboard as `contradicted`, not
> `partially-satisfied` — a screen that displays invented numbers is worse than an empty one,
> because it looks like it works.

Also expect **`.dc.html` fixtures to overclaim**: the design screens routinely show fields the
backend does not expose. Before binding anything, confirm the operation exists in
`docs/FRONTEND_GRAPHQL_CONTRACT.md`. **If the design wants a field the schema lacks, the schema
change comes first** — inventing a client-side type is a defect.

Produce, in R0, a table: figure → does the aggregation exist → does the operation exist → is it
in the contract with the right role.

## A · Backend

### BE-1 · The fourteen figure definitions, each as one aggregation
- **Spec** R1, R2 · **§5** T1 · **depends** R0 · **parallel-safe** yes *(one service per agent)*
- **Acceptance** **every pipeline's first stage is `$match`**; `explain()` reports `IXSCAN`.
- `$match` first is not style. A `$group` before `$match` aggregates the whole collection and then
  throws most of it away — at 200,000 tickets/month that is the difference between 40ms and a
  timeout.

### BE-2 · Financial figures from the ledger, reconciled to the trial balance
- **Spec** R3 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** a seeded purchase and refund **match the trial balance exactly**.
- Financial figures come from the ledger, not from counting payment documents. Two sources for one
  number is two numbers, and the dashboard will disagree with
  [`ET-FIN-005`](ET-FIN-005.md)'s reconciliation.

### BE-3 · The rollup document, the two jobs and the staleness flag
- **Spec** R5 · **§5** T3 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **a failed job leaves the previous rollup**; **`computedAt` is always present**.
- Never blank a rollup on failure — stale-but-labelled beats empty. `computedAt` is what lets the
  UI say "as at 14:05" instead of implying live.

### BE-4 · Repository-level scoping for every organizer statistic
- **Spec** R6 · **§5** T4 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **a second organization's ids return nothing across every organizer statistic.**
  Every one — one unscoped aggregation leaks a competitor's revenue.
- Scoping in the repository, per [`ET-PLT-007`](ET-PLT-007.md) BE-4.

### BE-5 · The polling client contract — interval, pause, backoff, jitter, no overlap
- **Spec** R4 · **§5** T5 · **depends** R0 · **parallel-safe** yes
- **Files** `frontend/web/libs/shared/src/api/polling/` — **this is a frontend task in the spec's
  own §5**, and it belongs in the shared barrel so all three apps poll the same way.
- **Acceptance** **no poll while one is in flight**; **polling pauses when the tab is hidden**.
- Jitter matters at scale: without it, every open dashboard polls on the same second and the
  platform builds its own thundering herd.

### BE-6 · Assert no `Subscription` type in the composed schema
- **Spec** R4 · **§5** T6 · **depends** R0 · **parallel-safe** yes
- **Acceptance** the composed supergraph declares **no `Subscription`**. A test, so D-12 cannot be
  quietly reversed by someone adding one field.

### BE-7 · Range caps, query timeouts and the partial-result flag
- **Spec** R7 · **§5** T7 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** a **180-day live range is refused**; **a timeout returns a flagged partial**.
- A flagged partial is honest. Silently returning incomplete data as though complete is how a
  finance decision gets made on half a month.

### BE-8 · Secondary reads, off-peak scheduling and the contention test
- **Spec** R7 · **§5** T8 · **depends** BE-3 · **parallel-safe** no
- **Acceptance** **reservation latency during a full rollup stays within the stated bound.** Same
  discipline as [`ET-FIN-005`](ET-FIN-005.md) BE-8 — analytics must never compete with on-sale.

### BE-9 · The subgraph halves; `@tag(name: "admin")` on every admin figure
- **Spec** R6 · **§5** T9 · **depends** BE-4 · **parallel-safe** **no — three SDL files**
- **Acceptance** the public contract exposes **no admin or finance statistic**.

## B · Contract

### GQL-1 · Across all three subgraphs
- **depends** BE-9 · **parallel-safe** no *(three SDL files — sequence carefully)*
- After composition, **regenerate `docs/FRONTEND_GRAPHQL_CONTRACT.md`** so every dashboard figure
  is verifiably backed by a real operation with a real role.

## C · Frontend — three dashboards

> **Run the infographics gate for each screen separately.** Three dashboards is three kernels, not
> one kernel at three sizes — delivering the same graphic three times is a named failure mode.
>
> - **Admin dashboard** — *"Is the platform healthy right now?"*
> - **Admin analytics** — *"Where is the money coming from, and is that changing?"*
> - **Org-admin dashboard** — *"How is my event selling, and what do I get paid?"*
>
> Per screen: one focal point. Rank every element focal / supporting / reference and enforce it
> with size, weight and colour. Three competing focal points is the most common failure in
> generated dashboards, and a bento grid makes it easy to commit by accident — equal tiles imply
> equal importance.

### FE-1 · The polling hook *(shared, from BE-5)*
- **depends** BE-5 · **parallel-safe** no
- Barrel-exported. Visibility-aware, backoff, jitter, no overlap. Every dashboard uses **this** —
  a second polling implementation is a second set of bugs.
- Every figure shows **`computedAt`** (BE-3): "as at 14:05", not an implied live number.
- **testids** `poll-status`, `figure-computed-at`, `figure-stale-badge`

### FE-2 · Admin dashboard — replacing the mock data
- **depends** GQL-1, FE-1 · **parallel-safe** no
- **Delete all nine fabricated values.** Every number is a real query or the tile does not ship.
- Bento layout per the screen — `auto-fit minmax(240px, 1fr)` stat rows, `minmax(340px, 1fr)`
  activity panels, bento tiles at 14px radius.
- One focal figure. If the design shows a figure the schema lacks, **raise the schema gap** rather
  than reinstating a mock.
- **testids** `dashboard-stat-<name>`, `dashboard-activity`, `dashboard-empty`

### FE-3 · Admin analytics
- **depends** GQL-1, FE-1 · **parallel-safe** yes
- Revenue, users, ledger. Range selector honouring BE-7's caps — an over-range selection is
  refused **before** submission, with the cap named.
- Partial results are **labelled as partial** (BE-7), never rendered as complete.
- **Charts only if they survive the gate**: bar baselines at zero always; one y-axis (dual axes
  manufacture correlations); direct labels over legends; ≤5 categorical hues; sequential data is
  one hue varying in lightness, never a rainbow; never encode a quantity by hue alone.
- The takeaway goes **on the chart**, next to the evidence — not in a caption.
- **testids** `analytics-range`, `analytics-range-capped`, `analytics-partial-flag`, `analytics-chart-<name>`

### FE-4 · Org-admin dashboard
- **depends** GQL-1, FE-1, F0-2 · **parallel-safe** yes
- Revenue, tickets, check-in rate, team — per `Org Admin - Dashboard.dc.html`.
- **Own organization only** (BE-4). No platform aggregate, ever.
- Check-in rate comes from [`ET-TKT-003`](ET-TKT-003.md) BE-8.
- **testids** `org-dashboard-revenue`, `org-dashboard-tickets`, `org-dashboard-checkin`, `org-dashboard-team`

### FE-5 · Empty and loading states
- **depends** FE-2, FE-3, FE-4 · **parallel-safe** yes
- **A new organizer with no events is the common case, not the edge case.** Their dashboard is a
  designed screen with a route to creating an event — not a grid of zeroes.
- Skeletons reserve exact space so tiles do not jump when data lands.
- **testids** `dashboard-loading`, `dashboard-empty-newuser`

### FE-6 · Money and number formatting
- **depends** F0-7 · **parallel-safe** yes
- `K 125,430` — Kwacha symbol, space, **tabular Fira Code**, right-aligned so digits align.
  Never `ZMW`, never `$`.
- Jade (`--color-money`) is **semantic only** — a revenue figure is money, a brand accent is not.

## D · Tests

### TS-1 · Aggregations *(L3)*
**Every** pipeline's first stage is `$match` — assert by inspecting the pipelines, not by timing.
`IXSCAN` on all fourteen.

### TS-2 · Financial parity *(L3)*
Seeded purchase and refund match the trial balance **exactly**. This is what keeps the dashboard
and [`ET-FIN-005`](ET-FIN-005.md) telling the same story.

### TS-3 · Rollups *(L3)*
A failed job leaves the previous rollup intact; `computedAt` always present; staleness flagged.

### TS-4 · Tenant scoping *(L3)*
A second organization's ids return nothing across **every** organizer statistic — enumerate them,
do not sample.

### TS-5 · Polling *(L1/L5)*
No poll while one is in flight; pauses when hidden; backoff and jitter observed; resumes on focus.

### TS-6 · No subscriptions *(L4)* — the composed supergraph declares no `Subscription`.

### TS-7 · Caps *(L2)* — 180-day live range refused; timeout returns a flagged partial.

### TS-8 · Contention *(L3)*
Full rollup **concurrent with** 200-against-50 reservations: latency within bound, inventory
conserved.

### TS-9 · e2e *(L5, admin + org-admin)*
- **Assert no mock values remain** — every rendered figure traces to a network call.
- `computedAt` visible; stale badge appears when a rollup is old.
- Range cap refused before submission; partial results labelled.
- Org-admin sees own data only; a second org's data is unreachable.
- New-organizer empty state is a designed screen, not zeroes.
- Charts: zero baselines, single y-axis, ≤5 hues, direct labels — assert in the compliance suite.
- Loading, empty, error, populated on all three dashboards.

## E · Gate

- [ ] R0 recorded, including the figure → aggregation → operation → contract table
- [ ] **All nine mock dashboard values deleted; no figure renders without a real query**
- [ ] Every pipeline `$match`-first with `IXSCAN`
- [ ] Financial figures reconcile to the trial balance exactly
- [ ] Failed rollups leave prior data; `computedAt` always present and displayed
- [ ] Every organizer statistic scoped at the repository; cross-org returns nothing
- [ ] One shared polling implementation: visibility-aware, jittered, non-overlapping
- [ ] **No `Subscription` in the composed schema**, asserted by test
- [ ] Range caps refused before submission; partials labelled
- [ ] Reservation latency holds during a full rollup
- [ ] **Infographics gate run separately for all three dashboards**; one focal point each
- [ ] Chart rules honoured: zero baselines, one y-axis, ≤5 hues, direct labels, on-chart takeaway
- [ ] Any design-fixture field the schema lacks was raised as a schema gap, **not** mocked
- [ ] `mvn -q -f backend verify -Dgroups=ET-ADM-004 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
