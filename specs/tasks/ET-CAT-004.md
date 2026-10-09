# ET-CAT-004 · Event page content, the image library, hidden tiers and the ranked feed — tasks

> **Spec** [`specs/catalog/004-event-content-media-and-ranking/spec.md`](../catalog/004-event-content-media-and-ranking/spec.md) · **Wave 2** · `blocked_by:` ET-CAT-001, 002, 003
> **Screens** the organizer's event editor and media library · the buyer's event page and discovery screen · the admin media moderation and stock-image tabs
> **Verify** `mvn -q -f backend/catalog-service test -Dgroups=ET-CAT-004 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

## R0 · Reconcile

`cancelEvent` already admitted administrators at runtime (`@auth(ORGANIZER)` includes `ADMIN`, the resolver allows
`ADMIN`, and the tenant guard lets a platform administrator through); what it lacked was the `admin` audience tag the
admin client generates from. `event(id)` already answered an administrator and the owning organization. Both are now
held by tests.

`Event.soldTickets` was never moved by a sale — only the tier's counters were — so any ranking by it ranked zeros. The
inventory commit, release and restore now move it, and the tier mirror recomputes it.

## A · Backend

### BE-1 · Visibility and authority
- **Spec** R1 · **depends** R0 · **parallel-safe** yes
- **Acceptance** an administrator's `cancelEvent` on another organization's event succeeds and records them as the actor; a stranger's refuses `EVENT_UNKNOWN`.

### BE-2 · Event page content and the tier category
- **Spec** R2, R3 · **depends** BE-1 · **parallel-safe** no — touches the event model, inputs and validators together
- **Acceptance** every field stores, trims and refuses out-of-range values; editing content does not return an approved event to draft.

### BE-3 · The ranked feed
- **Spec** R4, R5 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** five orders, each indexed; trending and recommendations never include an unpublished or ended event or one the caller holds.

### BE-4 · Hidden tiers
- **Spec** R6 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** a wrong code, a draft event and an inactive tier answer alike; five wrong codes ration; parallel guesses cannot pass the limit.

### BE-5 · Scheduled publication
- **Spec** R7 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** the wait fires once at `publishAt`; a move restarts it; a cancel or a send-back-for-review ends it; the history replays.

### BE-6 · The image library, moderation and stock images
- **Spec** R8, R9, R10 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** bytes decide what is an image; another organization's image is `MEDIA_UNKNOWN`; a removed image is a 404 at the origin and leaves every event.

### BE-7 · The organizer's list, sales totals and profile counts
- **Spec** R11, R12 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** the list is scoped and cursor-stable; a replayed commit adds nothing; a stranger reads null money.

## E · Gate

- [x] R0 recorded: what already worked, and what was only untagged or never moved
- [x] `cancelEvent` allows platform administrators and carries the `admin` audience; `event(id)` returns non-published events to administrators and the owning organization — `EventEditingTest.AdminCancel`, `EventVisibilityTest.administratorSeesEverything`, `CatalogSchemaContractTest`
- [x] Event page content stored and bounded — `EventContentRulesTest`, `EventAuthoringTest.PageContent`, `EventEditingTest.PageContent`
- [x] Ticket tier category stored; an old tier reads `GENERAL`
- [x] Five feed orders, trending and recommendations — `EventDiscoverySortTest`, `EventRankingTest`
- [x] Hidden tiers: unlock, ration, never leak — `TierAccessCodeTest`, `EventFieldVisibilityTest`
- [x] Scheduled publication — `EventPublishScheduleWorkflowTest`, `EventEditingTest.Scheduling`
- [x] Organizer media, moderation and stock images, scoped and audited — `MediaServiceTest`, `MediaRulesTest`
- [x] Organizer event list by cursor — `OrganizerEventFeedTest`
- [x] Event sales totals and the organization's counts — `InventoryEventTotalsTest`, `EventRankingTest`
- [x] Registry rows added: `catalog_media`, nine indexes, `EventPublishScheduleWorkflow`, `MEDIA_UNKNOWN`, `MEDIA_STATE_INVALID`
- [ ] Presigned upload flow for images over 5 MB — deferred
- [ ] Spec `status:` → `implemented`
