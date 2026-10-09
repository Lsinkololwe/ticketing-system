# ET-CAT-004 · Event page content, the image library, hidden tiers and the ranked feed

> **Conformance** · the organizer's event editor (tagline, running order, policies, go-live time) and the buyer's event page (FAQs, directions, hidden tiers, trending) · the admin media moderation and stock-image screens

## 1. Capability

An event page is more than a title, a date and a price. A buyer deciding whether to go wants a
one-line pitch, the running order, whether children are welcome, where to park, what not to bring and
a few answered questions; an organizer wants to write all of that once, choose a picture for it,
decide when it goes live, and hold back a tier for people who have a code. This spec adds those
fields to the event, a library for the pictures, and the orders of the public feed that depend on
what is selling.

Pictures need an owner and a moderator. An organization uploads and names its images and attaches
them to its events; an administrator can flag an image, remove it (and every event that showed it
loses the picture at once), restore it, or replace an event's banner outright. The platform also keeps
its own stock images, for organizers with no photograph and for the tile that represents each
category on the discovery screen.

The public feed gains four orders (soonest, newest, cheapest, dearest, most sold), a trending list and
a "because you booked" list. None of them reads anyone's history: the caller names the events they
hold and only those events' categories are read.

## 2. Design decisions

**Event content lives on the event, as optional fields.** Tagline, age restriction, doors-open time,
FAQs, running order, directions, parking, bag policy, checkout settings, banner alt text. A document
written before this spec has none of them and reads as an event with nothing to say; no backfill is
needed for them. They are *editable* in the field classification (`EventFields`): changing them never
sends an approved event back for review.

**Scheduled publication is a workflow, not a sweep.** An approved event whose `publishAt` is still
ahead is *scheduled* when the organizer presses Publish: the event stays `APPROVED`, hidden from
buyers, `publishScheduled` is true, and an `EventPublishScheduleWorkflow` sleeps until `publishAt`.
The workflow publishes through `EventLifecycleProcess` — the path an organizer's own Publish takes —
so the lifecycle workflow that completes the event starts with it. Moving `publishAt` signals the
wait; sending the event back for review, cancelling it, or `cancelScheduledPublish` ends it; and the
activity re-reads the event before acting, so a schedule that no longer applies publishes nothing.

**Hidden tiers are opened by a code, and a wrong code reveals nothing.** `unlockTierWithAccessCode`
answers `TIER_UNKNOWN` for a wrong code, an event that is not public, and an event with no hidden tier.
Guesses are rationed per buyer and event in Redis (five in fifteen minutes, then
`RATE_LIMIT_EXCEEDED` with the seconds to wait); the count is incremented before it is compared so
parallel guesses cannot pass the limit. `Event.ticketTiers` no longer returns hidden tiers to anyone but
the owning organization and platform administrators, and `TicketTier.accessCode` is readable only by
them. Booking asks `POST /api/internal/tiers/{tierId}/access-code/verify` before it reserves a hidden
tier.

**Images are stored through the platform's file-store switch.** `file-storage.type` (`local` by
default, `s3`) is the key identity-service already uses for verification documents; catalog carries a
byte-based `MediaStorage` with the same two stores. The public address is computed from the key
(`{public-base-url}/media/files/{key}`), never stored, and is served by catalog from the asset row, so
an image moderation removed is a 404 at the origin at once. The bucket stays private.

**The picture is checked by its bytes.** JPEG, PNG or WebP, at most 5 MB, the leading bytes agree with
the declared type, the name is sanitised into a random key. An organization may hold at most 500
images.

**Two ways in, one set of checks.** The platform's rule for files is multipart over REST
(`POST /api/v1/media`, `POST /api/v1/media/stock`): real progress in the browser, no base64 inflation,
and the gateway's upload ceiling instead of its 1 MB GraphQL one. The GraphQL `uploadMedia` and
`uploadStockImage` carry the picture as base64 for small images and for clients that cannot send
multipart. Both reach `MediaService`, which checks the bytes.

**Moderation is audited and reversible.** Flag keeps serving; remove stops serving and detaches the
image from every event banner, thumbnail and gallery; restore returns it to `ACTIVE`. Every
administrator action appends to the asset's `moderationLog`, which is never rewritten. A banner
override with no image (clearing the banner) has no asset to carry the entry and goes to the audit log.

**A category's picture is a stock image, not reference data.** Reference data carries no presentation
(ET-PLT-014), so `EventCategory.imageUrl` resolves to the newest active `CATEGORY_TILE` stock image
filed under the category's code. An administrator manages the library; nothing about the category row
changes.

**Ranking reads one number.** `Event.soldTickets`, kept current by booking's inventory commits and
refunds, orders `POPULAR`, `trendingEvents` and the "because you booked" list. Event sales totals
(`soldTickets`, `availableTickets`, `grossSales`, `commissionAmount`) move with the tier in the same
internal call; booking may report the money with each commit and refund, and a replayed commit adds
nothing.

**Rejected alternatives**

- *Presigned uploads straight to the bucket.* Right for large documents; an event picture is capped at
  5 MB and the editor needs the asset back in the same round trip as its metadata. The bucket stays
  private and catalog serves the picture, so removal takes effect at the origin.
- *A `categoryImage` field on the category row.* It would put presentation into reference data, which
  ET-PLT-014 forbids.
- *A scheduled sweep that publishes due events.* Timers live in workflows (ET-PLT-015).
- *An offset on `myEvents`.* A list that grows while it is read shifts under an offset; the new
  `myEventsConnection` pages by `(createdAt, id)`.

## 3. Requirements

### ET-CAT-004-R1 · Visibility and authority

WHEN a platform administrator cancels an event, THE SYSTEM SHALL cancel it exactly as the owning
organizer could, and WHEN an administrator or the owning organization asks for an event in any status,
THE SYSTEM SHALL return it, while any other caller receives only a published, active event.

**Acceptance**
- [ ] `cancelEvent` carries the `admin` tag and an administrator's cancel succeeds on an event of another organization
- [ ] `event(id)` returns a draft to an administrator and to the owning organization and `null` to another organization
- [ ] the cancellation reason and time are stored on the event and exposed

### ET-CAT-004-R2 · Event page content

WHEN an organizer creates or edits an event, THE SYSTEM SHALL store the tagline, age restriction,
doors-open time, FAQs, running order, directions, parking and bag policy, checkout settings and banner
alt text, and refuse each that is out of range.

**Acceptance**
- [ ] the age restriction is one of `ALL_AGES`, `13+`, `16+`, `18+`, `21+`
- [ ] doors open no later than the start; `publishAt` is in the future and before the start
- [ ] at most 20 FAQs and 40 running-order lines; a running-order time is `HH:mm`
- [ ] editing any of these on an approved event leaves it approved
- [ ] a duplicated event starts with no schedule, no cancellation and zero totals

### ET-CAT-004-R3 · Ticket tier category

WHEN a tier is created or edited, THE SYSTEM SHALL record its `TicketCategory`, and a tier without one
SHALL read as `GENERAL`.

**Acceptance**
- [ ] `createTicketTier` and `updateTicketTier` accept `category`
- [ ] a tier stored before categories existed reads `GENERAL`

### ET-CAT-004-R4 · Ordering the feed

WHEN a buyer asks `discoverEvents` with a sort, THE SYSTEM SHALL order the same filtered feed by it
with a stable tie-break, and the cursor SHALL page it without repeating or skipping an event.

**Acceptance**
- [ ] `SOONEST`, `NEWEST`, `PRICE_ASC`, `PRICE_DESC`, `POPULAR` order as named
- [ ] a price order omits an event with no tier on public sale
- [ ] each order has an index; the plan for the unfiltered feed is an index scan
- [ ] the depth cap and page-size refusal apply to every order

### ET-CAT-004-R5 · Trending and recommendations

WHEN a buyer asks for trending or recommended events, THE SYSTEM SHALL return upcoming published
events ranked by tickets sold, and for recommendations SHALL put events in the categories of the
events the caller names first, each with the event it resembles, then fill with trending.

**Acceptance**
- [ ] never an event the caller named, never an unpublished one
- [ ] only a published named event contributes a category
- [ ] more than 20 named events is refused

### ET-CAT-004-R6 · Hidden tiers

WHEN a buyer submits a code, THE SYSTEM SHALL return the hidden tier it opens, and otherwise
`TIER_UNKNOWN`, and after five wrong codes in fifteen minutes SHALL refuse with `RATE_LIMIT_EXCEEDED`.

**Acceptance**
- [ ] codes match case-insensitively, in constant time
- [ ] a correct code clears the count
- [ ] hidden tiers and access codes never reach a caller outside the owning organization and administrators

### ET-CAT-004-R7 · Scheduled publication

WHEN an organizer publishes an approved event whose `publishAt` is ahead, THE SYSTEM SHALL schedule it,
and at `publishAt` SHALL publish it through the lifecycle workflow.

**Acceptance**
- [ ] the event stays `APPROVED` and unlisted until then
- [ ] changing `publishAt` moves the wait; `cancelScheduledPublish` ends it
- [ ] sending the event back for review, or cancelling it, ends the schedule
- [ ] a schedule that no longer applies publishes nothing

### ET-CAT-004-R8 · Organizer media

WHEN an organizer uploads, renames or deletes an image, THE SYSTEM SHALL scope it to the caller's
organization, and another organization's image SHALL answer `MEDIA_UNKNOWN`.

**Acceptance**
- [ ] the bytes must be a JPEG, PNG or WebP agreeing with the declared type, at most 5 MB
- [ ] an `eventId` must be an event of the same organization
- [ ] deleting an image removes the file and detaches it from every event
- [ ] `myMedia` pages newest first with a cursor and never shows another organization's image

### ET-CAT-004-R9 · Moderation

WHEN an administrator flags, removes or restores an image, or overrides an event's banner, THE SYSTEM
SHALL record who did it and why, and a removed image SHALL no longer be served.

**Acceptance**
- [ ] flag keeps serving; remove returns 404 from the file endpoint; restore serves again
- [ ] removal clears the banner, thumbnail and gallery entries that used the image
- [ ] `moderationLog` is readable only by administrators
- [ ] a banner override accepts an organization's image only for that organization's event, or a stock image

### ET-CAT-004-R10 · Stock images and category tiles

WHEN an administrator uploads a stock image, THE SYSTEM SHALL file it as an event cover or a category
tile, and `EventCategory.imageUrl` SHALL be the newest active tile for the category.

**Acceptance**
- [ ] a tile requires an active category code; a cover refuses one
- [ ] an organizer sees only active stock images; an administrator may ask for all
- [ ] deleting a stock image detaches it from events

### ET-CAT-004-R11 · The organizer's event list

WHEN an organizer pages their events, THE SYSTEM SHALL return their organizations' events newest first
by cursor, filtered by status, search text and date range in the database.

**Acceptance**
- [ ] a team member sees the organization's events; nobody sees another organization's
- [ ] a cursor the list did not issue is refused

### ET-CAT-004-R12 · Sales totals and the organizer's profile

WHEN booking commits or restores tickets, THE SYSTEM SHALL move the event's `soldTickets`,
`availableTickets` and, if reported, `grossSales` and `commissionAmount` with the tier, once per
reservation; `grossSales`, `commissionAmount` and `netSales` SHALL be readable only by the owning
organization and administrators, and `Organization.publishedEventCount` and `completedEventCount`
SHALL be public counts.

**Acceptance**
- [ ] a replayed commit adds nothing; releasing a committed reservation reverses the same money
- [ ] a refund subtracts what it reports
- [ ] a stranger reads null for the money fields

### ET-CAT-004-R13 · A signed-out buyer browses *(added 2026-10-05)*

WHEN a caller without a token queries the catalog, THE SYSTEM SHALL answer `discoverEvents`, `trendingEvents`,
`event(id)`, `categories`, `provinces`, `cities` and `citiesWithEvents`, and nothing else, with published events only
and none of the organizer's, finance's or an administrator's fields.

Browsing precedes the account: the buyer's home, search and event pages load before sign-in
([ET-PLT-007](../../_platform/007-security-and-authorization/) R9 owns the mechanism). Booking has no signed-out
operation; the first booking call is made after sign-in. `recommendedEvents` is personal and stays `CUSTOMER`.

**Acceptance**
- [ ] The allowlist is exactly `discoverEvents`, `trendingEvents`, `event`, `categories`, `provinces`, `cities`, `citiesWithEvents`; every other query, every mutation and `_service` get 401 without a token; `_entities` is admitted only for `Organization.publishedEventCount` and `completedEventCount`
- [ ] Anonymous `discoverEvents`, `trendingEvents` and `event(id)` never return a draft, pending, approved-unpublished, rejected, cancelled-unpublished or soft-deleted event; the check is the absence of a principal in the service layer
- [ ] An anonymous caller reads a published event's open tiers only; a hidden tier and its access code stay hidden
- [ ] Every field of a type reachable from an allowlisted root that carries an `organizer`, `admin` or `internal` tag also carries `@auth`, so an anonymous request for it is refused (`organizerEmail`, approval fields, sales totals, audit fields); the tier inventory fields the event page reads are the documented exceptions
- [ ] An admitted anonymous caller is limited to 120 requests a minute per client address; the 121st gets 429 with `Retry-After`

**Tests** `CatalogPublicSurfaceTest` (L4), `CatalogPublicPolicyTest` (L1), `AnonymousDiscoveryTest` (L2)

## 4. Model

### Documents

| Collection | Owning service | Key fields | Notes |
|---|---|---|---|
| `catalog_media` | catalog-service | `_id`, `kind`, `organizationId`, `fileKey`, `contentType`, `status`, `purpose`, `categoryCode`, `moderationLog`, `deleted` | `@Version`; images of organizations and the platform |
| `catalog_events` | catalog-service | adds `tagline`, `ageRestriction`, `doorsOpenAt`, `faqs`, `runningOrder`, `gettingThere`, `parkingInfo`, `bagPolicy`, `checkoutSettings`, `bannerAltText`, `publishAt`, `publishScheduled`, `cancellationReason`, `cancelledAt`, `grossSales`, `commissionAmount` | all optional |
| `catalog_ticket_tiers` | catalog-service | adds `category`; movements gain `grossAmount`, `commissionAmount` | |

### Indexes

See `spec.yaml`. The four feed orders and the organizer list are served by the compound indexes declared in `CatalogIndexInitializer`.

### Workflows and Schedules

| Workflow | Id | Task queue | Start and conflict | Updates · signals | Timers |
|---|---|---|---|---|---|
| `EventPublishScheduleWorkflow` | `event-publish/{eventId}` | `catalog-lifecycle` | signal-with-start, `USE_EXISTING` | signals `reschedule`, `cancel` | to `publishAt` |

### GraphQL

See `spec.yaml`.

### Redis keys

| Key | Type | TTL | Purpose |
|---|---|---|---|
| `rl:tier-unlock:{userId}:{eventId}` | string counter | PT15M | failed access-code guesses |
| `rl:catalog:public-graphql:{clientAddress}` | string counter | PT1M | signed-out GraphQL requests (R13) |

### Error registry rows

| Code | Refusal type | GraphQL `ErrorType` | Retryable |
|---|---|---|---|
| `MEDIA_UNKNOWN` | `MediaUnknown` | `NOT_FOUND` | no |
| `MEDIA_STATE_INVALID` | `MediaNotInExpectedState` | `FAILED_PRECONDITION` | no |

### Flow

```
uploadMedia → MediaRules (bytes) → MediaStorage → catalog_media → GET /media/files/{key}
removeMedia → catalog_media.status=REMOVED → events lose the URL → file endpoint 404
publishEvent(publishAt ahead) → publishScheduled → EventPublishScheduleWorkflow → EventLifecycleProcess.publish
booking commit → tier $inc → event soldTickets / availableTickets / grossSales / commissionAmount $inc
```

## 5. Tasks

- [x] **T1 · Visibility and authority** — requirements: R1 — files: `schema.graphqls`, `EventServiceImpl`, `EventVisibilityTest`
- [x] **T2 · Event content, tier category** — requirements: R2, R3 — files: `Event`, `EventDetails`, `CreateEventInput`, `UpdateEventInput`, `TicketTier`
- [x] **T3 · Feed orders, trending, recommendations** — requirements: R4, R5 — files: `EventDiscovery`, `EventRanking`
- [x] **T4 · Hidden tiers** — requirements: R6 — files: `TierAccessService`, `InternalTierAccessController`
- [x] **T5 · Scheduled publication** — requirements: R7 — files: `workflow/schedule/*`
- [x] **T6 · Organizer media, moderation, stock images** — requirements: R8, R9, R10 — files: `MediaService`, `MediaStorage`, `MediaFileController`
- [x] **T7 · Organizer list, sales totals, profile counts** — requirements: R11, R12 — files: `OrganizerEventFeed`, `InventoryServiceImpl`, `OrganizerProfileService`
- [x] **T9 · Signed-out browsing** — requirements: R13 — files: `PublicDiscoveryConfig`, `schema.graphqls` (`@auth` on non-public fields), `PublicGraphQlFilter`
- [ ] **T8 · Presigned upload flow for images over 5 MB** — deferred; both upload paths stop at 5 MB

## 6. Out of scope

- A virtual waiting room for hot on-sales — ET-PLT-011.
- The verified badge on an organizer: identity owns `Organization.verified`; catalog adds only event counts.
- Booking enforcing `checkoutSettings` and the access code on reserve: booking reads the settings from the event and calls the internal verify endpoint.
