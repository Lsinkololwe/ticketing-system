# ET-TKT-001 · reconciliation

> **Reservation, the inventory hold, and the purchase saga**  
> Wave 3 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 12 of 13 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-TKT-001`; gate 0/16.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_reservations` | bound to an `@Document` |


5 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `myActiveReservations` | `already-satisfied` — in SDL, resolver bound |
| `reservation` | `already-satisfied` — in SDL, resolver bound |


### Mutations

| Operation | State |
|---|---|
| `cancelReservation` | `already-satisfied` — in SDL, resolver bound |
| `reserveTickets` | `already-satisfied` — in SDL, resolver bound |


### Error codes

All 4 registered in `shared-library/.../error/ErrorCode.java`.


### Events

3 of 4 wire names appear in production source. Missing: `ReservationReleasedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
