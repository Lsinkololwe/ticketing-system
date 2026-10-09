# ET-CAT-003 · Venues, geography, categories and discovery — tasks

> **Spec** [`specs/catalog/003-locations-and-reference-data/spec.md`](../catalog/003-locations-and-reference-data/spec.md) · **Wave 2** · `blocked_by:` ET-PLT-002, 004, 005, ET-CAT-001
> **Screens** `Ticketing - Discover & Checkout.dc.html` *(browse/search)* · `Admin - Transactions & System.dc.html` *(reference data)* · `Org Admin - Event Editor.dc.html` *(venue field — the Coverage map marks this **partial**)*
> **Verify** `mvn -q -f backend/catalog-service test -Dgroups=ET-CAT-003 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

Provinces, cities, venues, categories and the discovery path that every buyer enters through. The
performance requirements here are real: discovery is the highest-volume read on the platform and
the one most likely to become a `COLLSCAN` nobody notices until launch day.

## R0 · Reconcile

`ReferenceDataBootstrapper`, `ReferenceDataRegistrations` and `ReferenceDataSource` already exist
in catalog-service (untracked). Classify them — in particular whether seeding is **idempotent**.
Also check `BookingStatusSemanticStampers` and the several `*MigrationService` classes: some may
be `orphaned` — registered as beans but never invoked at boot, which passes every test while doing
nothing.

## A · Backend

### BE-1 · Seed provinces, cities and categories; make seeding idempotent
- **Spec** R1, R3 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** running the seeder **twice** produces identical rows; **no category carries a
  parent**.
- Flat categories are a decision, not an oversight: a hierarchy invites a taxonomy nobody
  maintains and a filter query that needs a recursive lookup on the hottest read path.

### BE-2 · `catalog_locations`, venue creation within a seeded city
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** an unknown `cityId` refuses; **venues are visible across organizations**.
- Shared venues are right for this market — the same handful of halls host most events, and a
  per-organization venue list produces fifteen spellings of one address.

### BE-3 · `minTierPrice` denormalisation, maintained by the tier mutations
- **Spec** R4 · **§5** T3 · **depends** BE-1 · **parallel-safe** **no — touches [`ET-CAT-002`](ET-CAT-002.md)'s mutations**
- **Acceptance** a price filter issues **no join**; the value **tracks** tier changes.
- Denormalised for the filter, so it must be maintained by every tier mutation — including tier
  deletion, which is the one that gets forgotten and leaves a price filter matching an event whose
  cheapest tier is gone.

### BE-4 · Declare every discovery index; the plan-enumeration test
- **Spec** R4 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** `explain()` reports `IXSCAN` for **every filter combination**, not the common
  one. Enumerate them; a combination that falls back to `COLLSCAN` is an outage waiting for the
  moment traffic arrives.
- Confirm the indexes exist live with MongoDB MCP `collection-indexes`.

### BE-5 · The width and depth caps, and the short-term refusal
- **Spec** R5 · **§5** T5 · **depends** BE-4 · **parallel-safe** yes
- **Acceptance** a full catalogue walk is stopped by the **depth cap**; `first: 500` refuses with
  `PAGE_SIZE_EXCEEDED`.
- Both caps. Width alone still lets a client walk the whole catalogue in pages; depth alone still
  lets one query ask for everything.

### BE-6 · Reference caching with eviction on mutation
- **Spec** R6 · **§5** T6 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** cold and warm answers are **identical**; **nothing polls**.
- Eviction on mutation, not a TTL refresh loop. Reference data changes a few times a year; polling
  it is pure cost.

### BE-7 · Deactivation everywhere; the past-event rendering test
- **Spec** R7 · **§5** T7 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **no mutation deletes a reference document**; a past event renders correctly
  after **each kind** of reference is deactivated — venue, city, category, in turn.
- Deleting a venue breaks every historical event that used it. Deactivation keeps history readable
  while removing it from selection.

### BE-8 · The subgraph half; `@auth` on every field
- **Spec** R1–R7 · **§5** T8 · **depends** BE-5 · **parallel-safe** **no — shared catalog SDL with CAT-001 and CAT-002**

## B · Contract

### GQL-1 · 5 queries, 9 mutations
- **depends** BE-8 · **parallel-safe** no
- Reference reads are **public**; every mutation is `ADMIN` and `@tag`ged.
- `compose-supergraph.sh --static` → `npm run codegen` → commit; restart the local router.

## C · Frontend

### Buyer · `Ticketing - Discover & Checkout.dc.html`

### FE-1 · Discovery filters
- **depends** GQL-1, F0-1 · **parallel-safe** no
- Category, city, date range, price range. Filter state in the URL so a filtered view is
  shareable and the back button works.
- Every combination must hit BE-4's indexes — if the UI offers a combination the backend cannot
  serve from an index, that is a **backend** gap to close, not a filter to quietly drop.
- **testids** `filter-category`, `filter-city`, `filter-date`, `filter-price`, `filter-clear`

### FE-2 · Search results with real empty and loading states
- **depends** FE-1 · **parallel-safe** yes
- **Empty is a designed screen** — "No events in Lusaka this weekend" with a route back to a
  broader filter, not a blank panel.
- Skeletons reserve the card's space so results do not reflow in.
- Cursor pagination against BE-5's caps.
- **testids** `search-results`, `search-empty`, `search-loading`, `search-error`, `search-load-more`

### FE-3 · Venue display
- **depends** GQL-1 · **parallel-safe** yes
- Name, address, city, province on the event detail. A **deactivated** venue still renders on a
  past event (BE-7) — that is the test case, not an edge case.
- **testids** `venue-name`, `venue-address`

### Author · `Org Admin - Event Editor.dc.html` *(venue field — marked **partial** in the Coverage map)*

### FE-4 · Venue picker
- **depends** GQL-1, F0-2 · **parallel-safe** yes
- Searchable, scoped to a chosen city, with "create venue" inline for a hall that is genuinely new.
- Only **active** venues are selectable; deactivated ones remain visible on events that already
  use them.
- **The Coverage map calls this surface partial.** If the editor needs a reference field the
  schema does not expose, the **schema change comes first** — inventing a client-side type is a
  defect. Record the gap rather than working around it.
- **testids** `venue-picker`, `venue-search`, `venue-create-inline`, `venue-selected`

### Admin · `Admin - Transactions & System.dc.html`

### FE-5 · Reference data management
- **depends** GQL-1 · **parallel-safe** yes
- Provinces, cities, categories, venues. Create, edit, **deactivate** — there is no delete, and
  the UI must not offer one.
- Deactivation confirms and states the consequence: it disappears from selection, existing events
  keep it.
- **testids** `reference-<kind>-table`, `reference-create`, `reference-deactivate`, `reference-deactivate-confirm`

## D · Tests

### TS-1 · Seeding *(L3)*
Twice → identical rows. No category has a parent.

### TS-2 · Venues *(L3)*
Unknown `cityId` refuses; venues visible across organizations.

### TS-3 · Denormalisation *(L3)*
`minTierPrice` tracks tier create, update, **and delete**; a price filter issues no join.

### TS-4 · Query plans *(L3 — enumerate, do not sample)*
`IXSCAN` for **every** filter combination the UI can produce. Drive it from the filter set FE-1
exposes, so the two cannot drift.

### TS-5 · Caps *(L2)*
Depth cap stops a full walk; `first: 500` → `PAGE_SIZE_EXCEEDED`.

### TS-6 · Cache *(L3)*
Cold and warm identical; eviction on mutation; **nothing polls** (assert on the absence of a
scheduled task, not on behaviour).

### TS-7 · Deactivation *(L3)*
No delete mutation exists in the composed schema; a past event renders after each reference kind
is deactivated.

### TS-8 · e2e *(L5)*
- Ticketing: filters, URL state, back button, empty, loading, error, populated, pagination.
- Org-admin: venue picker, inline create, deactivated-venue behaviour.
- Admin: reference CRUD with deactivate-not-delete.
- Compliance on all three.

## E · Gate

- [ ] R0 recorded, including whether the existing reference bootstrapper is idempotent — and whether it is actually invoked at boot
- [ ] Seeding twice is a no-op; categories are flat
- [x] Venues shared across organizations; unknown city refuses — the typed city resolves to an active `CITY` row of the reference data or is refused `LOCATION_UNKNOWN`; a second organization naming the same hall reuses the venue. Evidence: `EventAuthoringTest.TheCity`, `venuesAreShared` (L2, 2026-09-19).
- [x] `minTierPrice` tracks every tier mutation including delete — held as `Event.lowestTicketPrice` (Decimal128) and rewritten by `EventTierMirror` after every tier create, update, delete, activate and deactivate. Evidence: `EventAuthoringTest.storesEverything`, `mirrorFollowsTierWrites` (L2, 2026-09-19).
- [ ] `IXSCAN` on **every** filter combination the UI can produce, confirmed live via MCP
- [x] Depth **and** width caps enforced — `first` above 100 refused `PAGE_SIZE_EXCEEDED`; the feed stops at `catalog.discovery.max-depth` and a forged cursor cannot pass it. Evidence: `EventDiscoveryTest.Refusals`, `depthCap` (L2, 2026-09-19).
- [ ] Reference cache evicts on mutation and polls nothing
- [ ] No delete mutation exists for any reference kind
- [ ] Past events render after every reference kind is deactivated
- [ ] Discovery empty state is designed, not blank; filter state lives in the URL
- [ ] Any venue-field gap recorded rather than worked around with a client-side type
- [ ] `mvn -q -f backend/catalog-service test -Dgroups=ET-CAT-003 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented` — **Wave 3 does not open until all of Wave 2 is**
