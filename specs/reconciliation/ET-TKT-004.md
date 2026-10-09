# ET-TKT-004 · reconciliation

> **Ticket transfer and controlled resale**  
> Wave 5 · `booking-service` · subgraph `booking` · priority `should` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `absent` — 4 of 16 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-TKT-004`; gate 0/14.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_ticket_transfers` | **constant declared, used nowhere** |


4 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `myTransfers` | `absent` |
| `resaleListings` | `absent` |
| `ticketTransferChain` | `absent` |
| `transferByToken` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `buyResaleTicket` | `absent` |
| `cancelTransfer` | `absent` |
| `claimTransfer` | `absent` |
| `initiateTransfer` | `absent` |
| `listForResale` | `absent` |
| `withdrawResaleListing` | `absent` |


### Error codes

All 2 registered in `shared-library/.../error/ErrorCode.java`.


### Events

2 of 3 wire names appear in production source. Missing: `TransferExpiredEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
