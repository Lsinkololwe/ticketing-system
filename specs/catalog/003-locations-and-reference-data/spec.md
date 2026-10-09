# ET-CAT-003 · Venues, geography, categories and discovery

> **Conformance** · V3 §7.1 catalog collections · US Part III §16 buyer stories

## 1. Capability

A buyer opening the app wants one of three things: *what is on near me*, *what is on this
weekend*, or *where is the thing I already heard about*. Every one of those is a query over
reference data — a place, a date, a category — and none of them works if the underlying
data is free text. Two organizers typing "Lusaka" and "lusaka " produce two cities, and the
city filter then shows half the events.

This spec builds the reference layer discovery stands on. It declares the geography —
provinces, cities and venues, with venues owned by organizations but cities and provinces
owned by the platform — and the categories events are filed under. It declares the search
that ties them together: a text index over title and description, filters over category,
city, date range and price, and one paging shape.

The controlling decision is that **geography and categories are platform reference data,
not organizer input**. An organizer picks a city from a list; they do not type one. That
makes the city filter work, makes "events in Ndola" a real query, and makes the analytics
in [ET-ADM-004](../../admin/004-analytics-and-statistics/) countable. Venues are the
exception — organizers genuinely do run events in places the platform has never heard of,
so a venue may be created, but it is created *within* a city that already exists.

It also declares the thing discovery must not become: a query surface that lets an
unauthenticated caller page through the platform's entire catalogue at whatever depth and
cost they like.

## 2. Design decisions

**Provinces and cities are seeded platform data; organizers select, never type.**
Zambia has ten provinces and a bounded set of cities that matter for events. They are
seeded from a migration, editable only by `SUPER_ADMIN`, and referenced by id. The
alternative — a free-text city field — produces a filter that silently splits its own
results, and no amount of later normalisation recovers what was typed.

**A venue belongs to a city and may be created by an organizer.** A venue is genuinely
open-ended: a farm outside Chisamba is a real venue and the platform cannot pre-seed it. So
`createLocation` exists, requires `event:create`, and demands a `cityId` from the seeded
list. The venue carries coordinates, which is what makes *near me* possible later, and a
capacity, which is a sanity check against the event's own.

**Categories are a flat, closed, seeded list.** Not a tree. A hierarchy invites *Music →
Live → Rock* and then an argument about whether a filter on *Music* includes *Rock*, which
every implementation answers differently on different screens. A flat list of fifteen
categories is what a buyer scans, and it is what the platform can count.

**Search is a MongoDB text index, and it is honestly limited.** A text index over title and
description with a weight favouring title. No fuzzy matching, no synonyms, no relevance
tuning. It is the right tool at this platform's scale, and pretending otherwise by bolting
on a search cluster before there is anything to search is how a dependency arrives with no
traffic to justify it. The spec says what the limit is so that the decision to move to a
real search engine is made deliberately.

**Discovery filters compose, and every one of them is indexed.** Category, city, date
range, price range and free text. Each combination that the client can produce is served by
a declared index or is refused — a filter combination that falls back to a collection scan
is a filter combination that takes the platform down during a popular on-sale.

**Discovery is bounded in depth as well as width.** A `PUBLIC` connection with no depth
limit is a full catalogue export at whatever page size a caller chooses. Page size is
capped at 100 ([ET-PLT-004](../../_platform/004-federation-contract/) R5) and cursor depth
is capped, beyond which the caller is told to narrow the filter rather than paged further.

**Reference data is cached aggressively, because it does not change.** Provinces, cities
and categories change a few times a year. They are cached with a long TTL and the cache is
invalidated by the mutation that changes them — not polled.

**Nothing in reference data is deleted while anything references it.** A city with venues,
a venue with events, a category with events — all are deactivated, not removed. A venue
that vanishes takes with it the ability of a past attendee to remember where they went.

**Rejected alternatives**

- *Free-text city on the event.* Splits the filter it exists to serve, permanently.
- *A category tree.* Produces an unresolvable argument about whether a parent filter includes children.
- *Elasticsearch or OpenSearch from the start.* A cluster to operate before there is a corpus that needs one; the text index is honest about what it is.
- *Letting organizers create cities.* Twelve spellings of Lusaka within a year.
- *Unbounded cursor depth on a `PUBLIC` connection.* A catalogue export with no authentication.
- *Geospatial `$near` queries at launch.* The index and the coordinates are declared so it is possible later; the query is not built until a client asks for it, because *near me* on a national platform with events in six cities is a filter, not a radius.
- *Deleting an unused venue.* It is never provably unused — a past event still names it.

## 3. Requirements

### ET-CAT-003-R1 · Geography is seeded reference data, selected by id

THE SYSTEM SHALL hold provinces and cities as platform reference data, and an event's
location SHALL reference a seeded city.

**Acceptance**
- [ ] `catalog_provinces` and `catalog_cities` are seeded from a migration covering Zambia's ten provinces and its event-relevant cities
- [ ] Only `SUPER_ADMIN` may create, update or deactivate a province or city
- [ ] No event, venue or search input accepts a free-text city or province name
- [ ] Every `catalog_locations` row carries a `cityId` that resolves
- [ ] Seeding is idempotent — running the migration twice produces the same rows
- [ ] A city carries `provinceId`, `name`, `latitude`, `longitude` and `active`

> **Amended 2026-09-19.** There is one reference-data engine ([ET-PLT-014](../../_platform/014-reference-data-engine/)),
> and geography is not duplicated outside it: cities are `CITY` rows of `catalog_reference_data`,
> parented by `PROVINCE`, with `latitude` and `longitude` as metadata. `catalog_provinces` and
> `catalog_cities` are not read or seeded. A venue's `cityId` and an event's `cityId` are the `CITY`
> code. The organizer app's typed city is resolved to an active `CITY` row by name or code, ignoring
> case, and refused with `LOCATION_UNKNOWN` otherwise — it is never stored as typed.

### ET-CAT-003-R2 · Venues are organization-created within a seeded city

WHEN an organizer creates a venue, THE SYSTEM SHALL require an existing city and SHALL
attribute the venue to their organization.

**Acceptance**
- [ ] `createLocation` requires `event:create` and a `cityId` that resolves; an unknown city is refused with `LOCATION_UNKNOWN`
- [ ] The venue records `organizationId` and `createdById`
- [ ] A venue carries `name`, `addressLine`, `cityId`, `latitude`, `longitude`, `capacity`, `active`
- [ ] Coordinates are validated as being within the city's province's bounding box, or the mutation warns rather than refuses — a wrong pin is not worth blocking an event over
- [ ] An event whose tier capacities exceed its venue's capacity is warned, not refused; the venue capacity is advisory
- [ ] A venue is visible to every organization — venues are shared, because two organizations do use the same hall

### ET-CAT-003-R3 · Categories are a flat closed list

THE SYSTEM SHALL file every event under exactly one category from a flat seeded list.

**Acceptance**
- [ ] `catalog_categories` is seeded with the §4 list and carries no parent reference
- [ ] Only `SUPER_ADMIN` may add, rename or deactivate a category
- [ ] Every event carries exactly one `categoryId`; there is no multi-category field and no tag list
- [ ] A deactivated category keeps resolving for events already filed under it and disappears from the selection list
- [ ] `categories` is `PUBLIC`, bounded, and cached
- [ ] A test asserts no category document carries a parent or children field

> **Amended 2026-09-19.** An event's `categoryId` is an active `EVENT_CATEGORY` code of the reference
> data, checked on create and edit. `tags` on `CreateEventInput`, `UpdateEventInput` and
> `EventDiscoveryFilterInput` was `@deprecated` and is **removed (2026-10-06, product owner — F-045)**;
> it is no longer stored.
>
> **Amended 2026-09-19 (product owner).** A category is a reference-data row and nothing more: a
> code and a name. It carries **no colour, icon or display styling** — how a category looks is
> each application's design, not the reference engine's. `iconUrl`, `color` and `sortOrder` on
> `EventCategory` and its two inputs were `@deprecated` and are **removed (2026-10-06 — F-045)**; the
> seed's colours are removed and existing rows cleared (`strip-reference-presentation`).
>
> **Amended 2026-10-06 (product owner).** The catalog-specific category, city and province
> pagination operations (`activeEventCategories…`, `eventCategories…`, the city and province
> connections) are removed. A category is read as `categories` (public) and administered through
> `referenceDataAll(type: EVENT_CATEGORY)` and the reference-data mutations; `EventCategory.id`
> equals the category code. The admin Categories screen therefore edits name and description only
> (the code is fixed once created), and shows no event count for a deactivated category.

### ET-CAT-003-R4 · Every discovery filter combination is indexed

WHEN a buyer filters or searches, THE SYSTEM SHALL serve the query from a declared index.

**Acceptance**
- [ ] The filter set is exactly: `categoryId`, `cityId`, `startsAt` range, price range, free text — and combinations of them
- [ ] Each combination the client can construct is served by an index in §4; `explain()` reports `IXSCAN` for every one
- [ ] A combination with no index is refused at the resolver rather than executed
- [ ] The text index weights `title` above `description`
- [ ] Price filtering is over the event's minimum tier price, denormalised onto the event and maintained by the tier mutations — a price filter never joins to tiers
- [ ] A test enumerates every filter combination and asserts the plan for each

### ET-CAT-003-R5 · Discovery is bounded in width and depth

THE SYSTEM SHALL cap page size and cursor depth on every public discovery query.

**Acceptance**
- [ ] `first` is capped at 100 and a larger request is refused with `PAGE_SIZE_EXCEEDED`, never clamped
- [ ] Cursor depth is capped at `catalog.discovery.max-depth` (1000 results); beyond it the caller is told to narrow the filter
- [ ] `EventConnection` exposes `totalCount` only when a count is cheap for that filter — the §4 table says which, and the field is absent otherwise
- [ ] Search terms shorter than 3 characters are refused
- [ ] Discovery queries are rate-limited per client by [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/)
- [ ] A test attempts a full catalogue walk and is stopped by the depth cap

### ET-CAT-003-R6 · Reference data is cached and invalidated, not polled

THE SYSTEM SHALL cache reference data with a long TTL and SHALL invalidate on change.

**Acceptance**
- [ ] `cache:ref:provinces`, `cache:ref:cities` and `cache:ref:categories` carry a 24-hour TTL
- [ ] Every mutation on reference data evicts the relevant key in the same operation
- [ ] No scheduled job refreshes reference data
- [ ] A cache miss falls through to MongoDB and repopulates; the answer is identical either way, asserted by a cold-and-warm test
- [ ] Reference reads never appear in a slow-query log during a load test

### ET-CAT-003-R7 · Reference data is deactivated, never deleted

IF a province, city, venue or category is removed, THEN THE SYSTEM SHALL deactivate it and
retain it.

**Acceptance**
- [ ] No mutation deletes a `catalog_provinces`, `catalog_cities`, `catalog_locations` or `catalog_categories` document
- [ ] Deactivation sets `active = false`; the row keeps resolving for anything that references it
- [ ] A deactivated city, venue or category is absent from every selection list and from every discovery filter's option set
- [ ] An event already referencing a deactivated row is unaffected and still resolves it
- [ ] A test deactivates each kind and asserts a past event still renders completely

## 4. Model

> **Amended 2026-09-01 under [D-19](../../ROADMAP.md).** 3 operation names below adopt the
> shipped names: `createCategory` → `createEventCategory`, `deactivateCategory` → `deactivateEventCategory`, `updateCategory` → `updateEventCategory`. D-19 rules that where the schema and §4 disagree on an operation's
> *name*, the schema stands and §4 adopts it.
>
> **Only the names were adopted.** Argument lists and return types were not re-verified against
> the schema, so a row here can now name a real operation and still describe it wrongly. That
> gap is unmeasured, and calling it verified would be the same mistake as counting a file's
> existence as proof it runs.

### Documents

| Collection | Owner | Key fields |
|---|---|---|
| `catalog_provinces` | platform | `name`, `code`, `active` |
| `catalog_cities` | platform | `provinceId`, `name`, `latitude`, `longitude`, `active` |
| `catalog_locations` | organization | `organizationId`, `cityId`, `name`, `addressLine`, `latitude`, `longitude`, `capacity`, `active`, `createdById` |
| `catalog_categories` | platform | `name`, `slug`, `displayOrder`, `active` — **no parent**, no presentation (amended 2026-09-19) |
| `catalog_reference_data` | platform | typed lookup lists that are not worth their own collection |

### Seeded categories

`MUSIC`, `FESTIVAL`, `CONFERENCE`, `SPORTS`, `THEATRE`, `COMEDY`, `NIGHTLIFE`, `FOOD_DRINK`,
`ARTS_CULTURE`, `BUSINESS`, `EDUCATION`, `RELIGIOUS`, `COMMUNITY`, `CHARITY`, `OTHER`.

Fifteen, flat, closed. `OTHER` exists so that an organizer is never blocked, and its share
of events is a metric — a rising `OTHER` is the signal that the list needs a sixteenth row.

### Seeded provinces

Central, Copperbelt, Eastern, Luapula, Lusaka, Muchinga, Northern, North-Western, Southern,
Western. Cities are seeded per province, `active` by default, and extended by
`SUPER_ADMIN` as the platform reaches new towns.

### Discovery indexes

| Filter combination | Index | `totalCount` |
|---|---|---|
| none (all upcoming) | `{ status: 1, startsAt: 1 }` | yes |
| category | `{ categoryId: 1, status: 1, startsAt: 1 }` | yes |
| city | `{ cityId: 1, status: 1, startsAt: 1 }` | yes |
| category + city | `{ categoryId: 1, cityId: 1, startsAt: 1 }` | yes |
| date range | `{ status: 1, startsAt: 1 }` | yes |
| price range | `{ status: 1, minTierPrice: 1, startsAt: 1 }` | no |
| free text | `{ title: "text", description: "text" }`, weights `title: 10` | **no** |
| text + any filter | text index, then filtered | **no** |

`minTierPrice` is denormalised onto `catalog_events` and maintained by
[ET-CAT-002](../002-ticket-tiers-and-inventory/)'s tier mutations. A price filter never
joins to tiers.

`totalCount` is absent on text and price queries because counting them is a second full
pass; the connection reports `hasNextPage` and nothing more.

### GraphQL

Subgraph `catalog`. Every field carries `@auth` explicitly.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `provinces` | query | `PUBLIC` | `[Province!]!` — bounded, 10 |
| `cities(provinceId)` | query | `PUBLIC` | `[City!]!` — bounded, ≤ 200 |
| `categories` | query | `PUBLIC` | `[Category!]!` — bounded, 15 |
| `location(id)` | query | `PUBLIC` | `Location` |
| `locations(cityId, page)` | query | `AUTHENTICATED` | `LocationPage!` |
| `createLocation(input)` | mutation | `ORGANIZER` | `Location!` |
| `updateLocation(id, input)` | mutation | `ORGANIZER` | `Location!` |
| `deactivateLocation(id)` | mutation | `ORGANIZER` | `Location!` |
| `createCity(input)` | mutation | `SUPER_ADMIN` | `City!` |
| `updateCity(id, input)` | mutation | `SUPER_ADMIN` | `City!` |
| `deactivateCity(id)` | mutation | `SUPER_ADMIN` | `City!` |
| `createEventCategory(input)` | mutation | `SUPER_ADMIN` | `Category!` |
| `updateEventCategory(id, input)` | mutation | `SUPER_ADMIN` | `Category!` |
| `deactivateEventCategory(id)` | mutation | `SUPER_ADMIN` | `Category!` |

`Province`, `City`, `Location` and `Category` are catalog-owned `@key` types. None is
extended by another subgraph.

`EventFilter` — the closed input shape R4's index table is written against:

```graphql
input EventFilter {
    categoryId: ID
    cityId: ID
    startsAfter: DateTime
    startsBefore: DateTime
    minPrice: BigDecimal
    maxPrice: BigDecimal
    query: String          # >= 3 characters
}
```

### Redis keys

| Key | TTL | Purpose | Authority |
|---|---|---|---|
| `cache:ref:provinces` | 24 h | reference read-through | `catalog_provinces` |
| `cache:ref:cities` | 24 h | reference read-through | `catalog_cities` |
| `cache:ref:categories` | 24 h | reference read-through | `catalog_categories` |

Each is evicted by the mutation that changes it. Nothing polls.

### Configuration

| Property | Value |
|---|---|
| `catalog.discovery.max-page-size` | 100 |
| `catalog.discovery.max-depth` | 1000 |
| `catalog.discovery.min-search-length` | 3 |
| `catalog.reference.cache-ttl` | `PT24H` |

### Error codes

`LOCATION_UNKNOWN` — a row of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.
`PAGE_SIZE_EXCEEDED` is [ET-PLT-004](../../_platform/004-federation-contract/)'s.

## 5. Tasks

- [ ] **T1 · Seed provinces, cities and categories; make seeding idempotent**
  - requirements: R1, R3
  - files: `backend/catalog-service/.../migration/ReferenceDataSeeder.java`
  - verify: running the seeder twice produces identical rows; no category carries a parent
  - parallel-safe: no
  - depends: —

- [ ] **T2 · `catalog_locations`, venue creation within a seeded city**
  - requirements: R2
  - files: `backend/catalog-service/.../service/impl/LocationServiceImpl.java`
  - verify: an unknown `cityId` refuses; venues are visible across organizations
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · `minTierPrice` denormalisation, maintained by the tier mutations**
  - requirements: R4
  - files: `backend/catalog-service/.../service/impl/TicketTierServiceImpl.java`
  - verify: a price filter issues no join; the value tracks tier changes
  - parallel-safe: no — touches ET-CAT-002's mutations
  - depends: T1

- [ ] **T4 · Declare every discovery index; the plan-enumeration test**
  - requirements: R4
  - files: `backend/catalog-service/.../config/MongoIndexInitializer.java`
  - verify: `explain()` reports `IXSCAN` for every filter combination
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · The width and depth caps, and the short-term refusal**
  - requirements: R5
  - files: `backend/catalog-service/.../web/graphql/query/EventQueryResolver.java`
  - verify: a full catalogue walk is stopped by the depth cap; `first: 500` refuses
  - parallel-safe: yes
  - depends: T4

- [ ] **T6 · Reference caching with eviction on mutation**
  - requirements: R6
  - files: `backend/catalog-service/.../infrastructure/cache/`
  - verify: cold and warm answers are identical; nothing polls
  - parallel-safe: yes
  - depends: T1

- [ ] **T7 · Deactivation everywhere; the past-event rendering test**
  - requirements: R7
  - files: `backend/catalog-service/.../service/impl/`
  - verify: no mutation deletes a reference document; a past event renders after each kind is deactivated
  - parallel-safe: yes
  - depends: T2

- [ ] **T8 · The subgraph half; `@auth` on every field**
  - requirements: R1–R7
  - files: `backend/catalog-service/src/main/resources/graphql/schema.graphqls`
  - verify: `compose-supergraph.sh --static`
  - parallel-safe: no — shared SDL with ET-CAT-001 and ET-CAT-002
  - depends: T5

## 6. Out of scope

| Capability | Spec |
|---|---|
| The event's lifecycle and its visibility rules | [ET-CAT-001](../001-event-lifecycle/) |
| Tiers, pricing and inventory | [ET-CAT-002](../002-ticket-tiers-and-inventory/) |
| Rate limiting the discovery surface | [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/) |
| City and category analytics | [ET-ADM-004](../../admin/004-analytics-and-statistics/) |
| Platform configuration and feature flags | [ET-ADM-002](../../admin/002-platform-configuration/) |

Deliberately never in scope: **free-text geography** (it splits the filter it exists to
serve), **a category tree** (an unresolvable argument about whether a parent includes its
children), **a search cluster** (a dependency before there is a corpus that needs one), and
**geospatial radius search** (the coordinates and index are declared so it is possible
later; on a platform with events in six cities, *near me* is a city filter).
