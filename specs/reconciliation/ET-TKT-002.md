# ET-TKT-002 · reconciliation

> **Ticket issuance, the QR identity and delivery**  
> Wave 3 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 6 of 15 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-TKT-002`; gate 0/14.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


> **Amended 2026-09-01 — booking's pagination twins were collapsed.**
> The `*OffsetPagination` / `*CursorPagination` names quoted below no longer exist: under
> [`ROADMAP.md` D-19](../ROADMAP.md) each pair became one field under its bare name, keeping
> the offset form. Read the substitutes below as `<name>` without the suffix. The
> classification itself is unchanged — these operations are still `contradicted`, because the
> shipped name still differs from the one §4 declares; only the shipped name has changed.

### Collections

| Collection | State |
|---|---|
| `booking_tickets` | bound to an `@Document` |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `eventSigningKey` | `absent` |
| `eventTickets` | `contradicted` — built under another name: `ticketsByEventCursorPagination`, `ticketsByEventOffsetPagination` |
| `myTickets` | `contradicted` — built under another name: `searchTicketsCursorPagination`, `searchTicketsOffsetPagination`, `ticketsByBuyerCursorPagination`, `ticketsByBuyerOffsetPagination` |
| `ticket` | `already-satisfied` — in SDL, resolver bound |
| `ticketByReference` | `absent` |
| `ticketQrPayload` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `reissueTicket` | `absent` |
| `resendTicketDelivery` | `absent` |


### Error codes

All 3 registered in `shared-library/.../error/ErrorCode.java`.


### Events

1 of 3 wire names appear in production source. Missing: `TicketIssuedEvent`, `TicketReissuedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
