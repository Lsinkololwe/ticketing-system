# ET-FIN-004 · reconciliation

> **Refunds, cancellation refunds and chargebacks**  
> Wave 4 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 13 of 22 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-FIN-004`; gate 0/14.

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
| `booking_chargebacks` | bound to an `@Document` |
| `booking_refund_requests` | bound to an `@Document` |


5 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `chargebacks` | `contradicted` — built under another name: `chargebacksByEvent`, `chargebacksByOrganizer`, `chargebacksOffsetPagination`, `chargebacksPendingRecovery` |
| `myRefundRequests` | `contradicted` — built under another name: `pendingRefundRequestsCursorPagination`, `pendingRefundRequestsOffsetPagination`, `refundRequestsByBuyerCursorPagination`, `refundRequestsByBuyerOffsetPagination` |
| `refundQuote` | `absent` |
| `refundRequest` | `already-satisfied` — in SDL, resolver bound |
| `refundRequests` | `contradicted` — built under another name: `pendingRefundRequestsCursorPagination`, `pendingRefundRequestsOffsetPagination`, `refundRequestsByBuyerCursorPagination`, `refundRequestsByBuyerOffsetPagination` |


### Mutations

| Operation | State |
|---|---|
| `acceptChargeback` | `already-satisfied` — in SDL, resolver bound |
| `approveRefund` | `contradicted` — built under another name: `approveRefundRequest` |
| `contestChargeback` | `absent` |
| `rejectRefund` | `contradicted` — built under another name: `rejectRefundRequest` |
| `requestRefund` | `contradicted` — built under another name: `approveRefundRequest`, `cancelRefundRequest`, `createAdminRefundRequest`, `createUserRefundRequest` |
| `retryRefund` | `absent` |


### Error codes

All 4 registered in `shared-library/.../error/ErrorCode.java`.


### Events

5 of 5 wire names appear in production source.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
