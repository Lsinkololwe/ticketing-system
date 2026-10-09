# ET-CAT-002 · reconciliation

> **Ticket tiers, capacity and the authoritative inventory**  
> Wave 2 · `catalog-service` · subgraph `catalog` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 14 of 25 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-CAT-002`; gate 0/14.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_promo_codes` | bound to an `@Document` |
| `booking_tier_inventory` | named and used, but no `@Document` binds it |
| `catalog_ticket_tiers` | bound to an `@Document` |


4 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `promoCode` | `absent` |
| `ticketTier` | `already-satisfied` — in SDL, resolver bound |
| `ticketTiers` | `contradicted` — built under another name: `availableTicketTiers`, `eventTicketTiers` |


### Mutations

| Operation | State |
|---|---|
| `changeTierCapacity` | `absent` |
| `closeTier` | `absent` |
| `createPromoCode` | `absent` |
| `createTicketTier` | `already-satisfied` — in SDL, resolver bound |
| `deactivatePromoCode` | `absent` |
| `deleteTier` | `contradicted` — built under another name: `deleteTicketTier` |
| `pauseTierSales` | `absent` |
| `resumeTierSales` | `absent` |
| `updatePromoCode` | `absent` |
| `updateTicketTier` | `already-satisfied` — in SDL, resolver bound |


### Error codes

All 6 registered in `shared-library/.../error/ErrorCode.java`.


### Events

2 of 3 wire names appear in production source. Missing: `TierClosedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
