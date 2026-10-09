# ET-CAT-001 · reconciliation

> **Event lifecycle — eight states, approval, publish, reschedule, cancel**  
> Wave 2 · `catalog-service` · subgraph `catalog` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 23 of 31 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-CAT-001`; gate 0/13.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


> **Amended 2026-09-01 — catalog's fourteen true duplicates were collapsed.**
> The `*OffsetPagination` / `*CursorPagination` names quoted below no longer exist for those
> fourteen: under [`ROADMAP.md` D-19](../ROADMAP.md) each pair became one field under its bare
> name, and §4 was extended to name them. Twenty other pairs are untouched — their two halves
> serve different audiences and are not duplicates.

> **Amended 2026-09-01 — `event` was `already-satisfied` and was still wrong.**
> The row below records that the name exists and a resolver is bound to it. Both were true
> while the query returned drafts, rejected events and soft-deleted ones to an unauthenticated
> caller holding an id — see
> [F-007](../FINDINGS.md#f-007--the-one-public-query-that-took-an-id-did-not-filter-on-visibility).
> This is the sharpest example so far of the caveat at the top of every one of these files:
> presence is not conformance, and a `already-satisfied` row is a statement about the schema,
> never about behaviour. The §3 read is what would have caught it, and it is still the open
> box at the foot of this page.

### Collections

| Collection | State |
|---|---|
| `catalog_events` | bound to an `@Document` |


4 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `event` | `already-satisfied` — in SDL, resolver bound |
| `events` | `contradicted` — built under another name: `approvedNotPublishedEventsCursorPagination`, `approvedNotPublishedEventsOffsetPagination`, `cancelledEventsCursorPagination`, `cancelledEventsOffsetPagination` |
| `eventsByCategory` | `contradicted` — built under another name: `eventsByCategoryCursorPagination`, `eventsByCategoryOffsetPagination` |
| `eventsByCity` | `contradicted` — built under another name: `eventsByCityCursorPagination`, `eventsByCityOffsetPagination` |
| `eventsPendingApproval` | `contradicted` — built under another name: `pendingApprovalEventsCursorPagination`, `pendingApprovalEventsOffsetPagination` |
| `myOrganizationEvents` | `absent` |
| `searchEvents` | `contradicted` — built under another name: `searchEventsCursorPagination`, `searchEventsOffsetPagination` |


### Mutations

| Operation | State |
|---|---|
| `approveEvent` | `already-satisfied` — in SDL, resolver bound |
| `cancelEvent` | `already-satisfied` — in SDL, resolver bound |
| `createEvent` | `already-satisfied` — in SDL, resolver bound |
| `deleteEvent` | `already-satisfied` — in SDL, resolver bound |
| `publishEvent` | `already-satisfied` — in SDL, resolver bound |
| `rejectEvent` | `already-satisfied` — in SDL, resolver bound |
| `rescheduleEvent` | `absent` |
| `submitEventForApproval` | `already-satisfied` — in SDL, resolver bound |
| `unpublishEvent` | `already-satisfied` — in SDL, resolver bound |
| `updateEvent` | `already-satisfied` — in SDL, resolver bound |


### Error codes

All 3 registered in `shared-library/.../error/ErrorCode.java`.


### Events

9 of 10 wire names appear in production source. Missing: `EventDecidedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
