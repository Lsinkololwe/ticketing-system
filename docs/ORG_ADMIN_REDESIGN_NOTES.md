# Organization Admin redesign — handover notes

Covers the design-system import, the dashboard rebuild, the backend analytics
added to support it, and two defects found along the way.

Related documents:

- `frontend/web/docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md` — the chart contract
  every data tile is built against. Amend it before changing an encoding.
- `frontend/web/design-system/README.md` — import provenance and contrast corrections.
- `frontend/web/design-system/component-contracts.json` — the 15 declared component APIs.

---

## 1. Defects found

### 1.1 Every money aggregation in booking-service returned zero — FIXED

**Severity: high. Pre-existing, unrelated to the redesign.**

Spring Data MongoDB persists `BigDecimal` as a BSON **String**, not `Decimal128`.
MongoDB's `$sum` silently ignores non-numeric values — no error, no warning. So
every aggregation of the form `.sum("price")` returned `0` regardless of how many
tickets had been sold.

Confirmed against a real MongoDB container:

```
RAW DOC:    { price: "100", status: "PURCHASED", … }
PIPELINE:   { $group: { _id: …, revenue: { $sum: "$price" } } }
RESULT:     { revenue: 0, ticketsSold: 1 }      ← one ticket, zero revenue
```

Affected, and now fixed:

| Site | Field |
|---|---|
| `OrganizerDashboardServiceImpl#getRevenueAndTicketStats` | `totalRevenue` → `myDashboardStats.totalRevenue` |
| `OrganizerDashboardServiceImpl#getRevenueBreakdown` | `grossRevenue`, `fees` → `myFinanceOverview` |
| `OrganizerDashboardServiceImpl#getMonthlyEarnings` | monthly earnings + growth % |
| `OrganizerDashboardServiceImpl#getUpcomingEvents` | per-event `revenue` |
| `OrganizerDashboardServiceImpl#getRevenueSeries` | new |
| `OrganizerDashboardServiceImpl#getTicketMix` | new |
| `TicketStatsService#getCategoryStatsAggregation` | `totalRevenue` per category |

**Fix applied:** `OrganizerDashboardServiceImpl#asDecimal(field)` wraps each money
field in `$convert → decimal` with `onError`/`onNull` falling back to zero. This
works against the documents already in the database.

**Fix NOT applied, and it is your call:** registering a `MongoCustomConversions`
BigDecimal↔Decimal128 converter so amounts are stored numerically. That is the
better long-term shape, but it is a **data migration** — every existing document
holds a string, and a numeric-only pipeline would silently skip all of them. It
also touches all three services, not just booking. Deliberately left alone rather
than done quietly.

Regression guard: `OrganizerDashboardAnalyticsIntegrationTest.TicketMix
#revenueSumsAreNotSilentlyZero` fails the moment the `$convert` wrapper is removed.

### 1.2 The imported design system fails WCAG contrast in five places — FIXED

Measured, not eyeballed. Details and the corrected values are in
`frontend/web/design-system/README.md` and the chart spec §A4.

The worst of them: the design's own dashboard mock paints teal bars on gray
tracks at **2.32:1** (needs 3:1), and separates focal from non-focal segment bars
by **hue alone at 1.36:1** — indistinguishable in grayscale and to a deuteranope.

Corrected additively in `apps/organization-admin/src/app/global.css` §2b. The
Radix scales are untouched, so the admin and ticketing apps are unaffected. If
you re-import the design system, **re-measure before adopting new colour values.**

---

## 2. What was built

### Backend — booking-service

Four organizer-scoped queries, all extracting `organizerId` from the JWT
(OWASP A01):

| Query | Returns |
|---|---|
| `myRevenueSeries(months: Int)` | revenue per **complete** calendar month, oldest first |
| `myTicketMix` | sold tickets by tier, with the denominator |
| `myCheckInRate` | gate attendance for the most recent event that has run, or null |
| `myPayoutWindow` | withdrawable balance + the escrow hold on the next tranche |

Design decisions worth knowing:

- **The partial current month is excluded** from the revenue series. A part-month
  column beside full months reads as a revenue collapse.
- **Months with no sales are emitted as zero points**, not skipped. A gap would
  compress the axis and make a quiet month look like a shorter span of time.
- **`myCheckInRate` resolves to null**, never a 0% rate, when no event has run.
  0% asserts that nobody turned up.
- **Every rate ships with its denominator.** `issued` and `scanned` are both sent;
  the client prints "1,044 of 1,200" so the figure is checkable.
- Sub-1% ticket tiers fold into a single labelled "Other" row — a 0.3% bar cannot
  carry a label.

### Frontend — organization-admin

- `src/components/charts/primitives.tsx` — `ColumnSeries`, `ShareBars`,
  `BulletMeter`, `VizFigure`, `VizTitle`, `VizInsight`, `VizNote`. The zero
  baseline is enforced in the component: `ColumnSeries` has no `min`/`domain`
  prop, because a truncated baseline is a correctness failure, not a style option.
- `src/lib/format/figure.ts` — one place for currency, month labels, relative
  time, enum humanisation. Currency is always `K 125,430`, never `ZMW` or `$`.
- `src/app/global.css` §2b + the `.viz-*` layer — all chart presentation, so no
  raw colour or px reaches a `.tsx`.
- Dashboard rebuilt to the design's bento layout; shell updated with the org
  identity block and the header greeting/breadcrumb/primary action.

---

## 3. Running the tests

### Backend integration tests (Testcontainers + MongoDB)

```bash
cd backend/booking-service
mvn test -Dtest=OrganizerDashboardAnalyticsIntegrationTest
```

**Local Docker caveat.** `~/.testcontainers.properties` pins
`docker.host = tcp://127.0.0.1:49432` (Testcontainers Desktop). Against Docker
Engine 29.x that proxy negotiates API 1.32, below the daemon's minimum of 1.40,
and every container start fails with *"Could not find a valid Docker
environment"*. Until that is sorted, pass:

```bash
mvn test -Dtest=OrganizerDashboardAnalyticsIntegrationTest \
  -DargLine="-Dapi.version=1.44 -Dtc.host=unix://$HOME/.colima/default/docker.sock"
```

This is an environment issue, not a test issue. The durable fix is to update
Testcontainers Desktop or drop the `docker.host` pin from
`~/.testcontainers.properties` so the active Docker context is used. Your global
config was left untouched.

The test deliberately does **not** use `@DataMongoTest`:
`BookingServiceApplication` component-scans the whole service, so the slice drags
in beans that need a `WebClient.Builder` and fails to start. The template and
repositories are constructed directly, which is also about ten times faster.

### Frontend

```bash
cd frontend/web/apps/organization-admin && npx vitest run       # 56 passed
cd frontend/web && npx tsc -p apps/organization-admin/tsconfig.json --noEmit --declarationMap false
```

If typecheck reports `TS6305` or "has no exported member" for
`@pml.tickets/shared/*`, the shared project's declaration output is stale:

```bash
cd frontend/web && npx tsc -b libs/shared
```

### Regenerating GraphQL types offline

`codegen.ts` introspects a running router by default. To regenerate without the
backend up:

```bash
cd docker-resources/apollo-router/ticketing && ./compose-supergraph.sh --static
cd frontend/web && GRAPHQL_ENDPOINT=<abs-path-to>/supergraph.graphql npx graphql-codegen --config codegen.ts
```

This also validates that schema changes compose cleanly before they reach GraphOS.

---

## 4. Deliberately not built

### 4.1 The team-seats dashboard tile

The source design shows "Team · 4 of 5 seats used". The platform has **no seat
limit anywhere in the domain model** — `Organization` carries members but no plan,
tier or member cap. There is nothing to divide by, so building the tile would mean
inventing the denominator.

The composition band therefore ships two-up rather than three-up. Spec §B5 records
the full design; to implement, add a seat cap to `Organization`, expose
`myOrganizationSeats { used, limit, byRole { name count } }`, and build to that spec
unchanged.

### 4.2 Mock data — REMOVED

**All fixture data and all request mocking is gone from this app.** `grep -rn
"^const mock" apps/organization-admin/src` returns nothing. Any test that needs a
backend talks to a real one in a container.

Three things surfaced while removing it that were worse than stale fixtures:

1. **The gate decided admissibility locally.** `events/[id]/check-in` searched its
   in-memory roster and marked the ticket checked in itself. Against a roster that
   is minutes old — which at a gate it always is — that admits refunded and
   transferred tickets. It now calls `validateTicket` and lets the server decide.
2. **Analytics fabricated its period-over-period growth.** `prevRevenue =
   totalRevenue * 0.85`, presented as a real comparison. Growth is now the latest
   complete month against the one before it, and is omitted entirely when there is
   no prior month — an absent baseline is not "0% change".
3. **Two analytics stat cards had no data source at all.** "Page views" and
   "Conversion rate" rendered invented figures with hardcoded deltas
   (`change={12}`, `change={0.5}`). Nothing in the platform tracks page views, so
   both cards were deleted rather than restyled. The growth badges beside the
   charts were likewise hardcoded green-and-up regardless of the number inside
   them; they now follow the actual direction.

Everything now on real queries:

| Screen | Wired to |
|---|---|
| `dashboard` | `myRevenueSeries`, `myTicketMix`, `myCheckInRate`, `myPayoutWindow` + existing stats/activity/events |
| `finance/payouts` | `myPayouts`, `myBankAccounts`, `myFinanceOverview`, `myPayoutSources`, `createPayoutRequest` |
| `finance/bank-accounts` | `myBankAccounts` + full CRUD mutation set |
| `team` | `myOwnedOrganization.members`, `updateMemberRole`, `removeMember` |
| `events/[id]` | catalog `event(id)` incl. organizer-tagged tier inventory |
| `events/[id]/check-in` | `ticketsByEventOffsetPagination`, `validateTicket` |
| `analytics` | `myRevenueSeries`, `myEventsOffsetPagination` |

New shared modules: `libs/shared/src/api/organization-admin/modules/team` and
`.../checkin`.

**Panels that now say "not tracked yet" instead of showing something invented:**
audience segments and top locations on `analytics`. Buyer demographics and buyer
location are not captured at checkout — `Ticket` carries the *event's* city, not
the purchaser's. Event `views` and `conversionRate` are likewise unsourced, so
those columns stay at zero and are not displayed.

### 4.2.2 Forms that submitted nothing — NOW WIRED

`events/new`, `team/invite`, `settings/profile` and `settings/notifications` all
ended in `await new Promise(r => setTimeout(r, 1000))`, a `console.log`, and a
navigation away. "Publish Event" reported success without creating anything.
`grep -rn "await new Promise((resolve) => setTimeout"` over the app now returns
nothing.

| Form | Wired to | Notes |
|---|---|---|
| `events/new` | `createEvent` | Creates a DRAFT. Publishing stays a separate authorised step — a create that published directly would bypass approval. Failures keep the user on the form with their input intact. |
| `team/invite` | `inviteTeamMember` | One mutation per row, sent sequentially, with per-address outcomes. Rows that succeeded are cleared, so retrying cannot double-invite. |
| `settings/profile` | `updateProfile` | Save success and failure are both stated in place. |
| `settings/notifications` | `myNotificationPreferences`, `updateNotificationPreferences` | Sends a partial patch, not the whole object. |

Three controls were **removed** rather than wired, because nothing could store
what they collected:

1. **"Job title" on the profile form.** `User` has no such field, so every value
   typed into it was discarded on save.
2. **The notifications category x channel matrix.** Eight categories each with
   email/push/SMS toggles — twenty-four switches, none of them persistable.
   `UpdateNotificationPreferencesInput` has five channel booleans and seven
   category booleans, flat and global; there is no field in which "email me
   about payouts but not about marketing" could live. The screen now mirrors the
   storage model: a channels card and a categories card. Restoring the matrix
   means adding per-category channel columns to the backend first.
3. **Analytics "Page views" and "Conversion rate"** (see above).

A control that cannot persist is worse than a missing one — the user believes
they have recorded something.

### 4.2.3 catalog-service now has tests

`EventCreationIntegrationTest` is catalog-service's first test. The dependencies
were already in `pom.xml`; nothing had used them. Six tests against a real
MongoDB container cover: a created event is a DRAFT, tier prices round-trip
exactly (including minor units), available tickets are seeded from capacity,
the domain event is published so booking-service can provision escrow, events
are isolated by organizer, and a new event is not soft-deleted.

Run it the same way as the booking-service suite (see §3 for the Docker caveat):

```bash
cd backend/catalog-service
mvn test -Dtest=EventCreationIntegrationTest \
  -DargLine="-Dapi.version=1.44 -Dtc.host=unix://$HOME/.colima/default/docker.sock"
```

### 4.2.1 Original removal inventory

**Policy: this app holds no fixture data, and no request mocking. Any test that
needs a backend talks to a real one in a container.**

Removed:

| What | Where |
|---|---|
| MSW request mocking | `src/test/mocks/handlers.ts`, `src/test/mocks/server.ts`, and the wiring in `src/test/setup.ts` |
| Mock test helpers | `src/test/utils.tsx` (mock Apollo client, mock router, mock GraphQL responses — imported by nothing) |
| Stale backup file | `app/(dashboard)/events/new/page.tsx.bak2` |
| `mockPayouts`, `mockBankAccounts`, hardcoded `availableBalance` | `finance/payouts` — now on `useMyPayouts` / `useMyBankAccounts` / `useMyFinanceOverview` / `useMyPayoutSources` / `useCreatePayoutRequest` |
| `mockBankAccounts` | `finance/bank-accounts` — now on `useMyBankAccounts` plus the full CRUD mutation set |

What is left, and what each needs:

| Screen | Mock | Needed to remove it |
|---|---|---|
| `team/page.tsx` | `mockMembers` | frontend hooks over `Organization.members` (identity-service; the schema already has it) |
| `events/[id]/page.tsx` | `mockEvent` | frontend hook over the catalog `event(id)` query |
| `events/[id]/check-in/page.tsx` | `mockEvent`, `mockAttendees` | a ticket-holder list + scan mutation; check whether booking-service exposes one |
| `analytics/page.tsx` | 4 arrays | **backend work** — no daily-metric, event-performance, audience-segment or geo query exists |

Screens with no data layer at all (`events/new`, `team/invite`, `settings/profile`,
`settings/notifications`) submit nothing: the forms render, validate and then discard.
They need mutations before they can be called working.

Note on the remaining `analytics` mocks specifically: the four arrays there back
charts. Deleting them without a backend leaves the screen empty, which is the
honest state — but it should ship with an explicit "not available yet" empty state
rather than blank tiles, on the same principle as the dashboard's check-in tile.

### 4.3 BigDecimal storage migration

See §1.1. The `$convert` fix makes the current data readable; it does not change how
new data is written.

---

## 5. Mesh and gradient policy

Asked for explicitly, and the answer falls out of both the design system and the
infographic grammar:

| Surface | Mesh / gradient | Why |
|---|---|---|
| Data marks (bars, meters, sparklines) | **Never** | a gradient on a mark distorts perceived length and encodes nothing |
| Dashboard tiles and cards | **Never** | a background fill lowers contrast for every mark inside it; the DS specifies a flat `--card-bg` |
| Small accent chips (avatar squares, quick-action icons, brand marks) | **Yes** — `linear-gradient(135deg, var(--accent-9), var(--accent-11))` | ≤40px, carries no data; one of the two gradients the DS sanctions |
| Public marketing hero only | **Yes** — layered `radial-gradient` aurora/mesh | the one sanctioned ambient context; already implemented as `.mkt-mesh` / `.mkt-aurora` |

Rules for the marketing mesh so it stays on-theme:

1. **Hue-locked to the brand axis** — every stop derives from
   `--marketing-accent-rgb` (teal, hue 174) or its deep/cool neighbours. No stop
   outside the teal→blue arc.
2. **Alpha-capped at 0.15** — above that the mesh competes with foreground text.
3. **Contrast is computed against the darkest stop**, never the brightest.
4. **Ambient motion only** — 15–30s loops, disabled under `prefers-reduced-motion`.
5. **Never behind a chart.** Ambient background and data visualisation do not share
   a surface.
