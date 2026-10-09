# ET-FIN-003 · reconciliation

> **Payout eligibility, bank accounts and the settlement saga**  
> Wave 4 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 14 of 27 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-FIN-003`; gate 0/15.

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
| `booking_bank_accounts` | bound to an `@Document` |
| `booking_payout_requests` | bound to an `@Document` |


5 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `bankAccounts` | `contradicted` — built under another name: `bankAccountsByOrganizer` |
| `myPayoutRequests` | `contradicted` — built under another name: `failedPayoutRequestsCursorPagination`, `failedPayoutRequestsOffsetPagination`, `payoutRequestsByEventCursorPagination`, `payoutRequestsByEventOffsetPagination` |
| `payoutEligibility` | `already-satisfied` — in SDL, resolver bound |
| `payoutRequest` | `already-satisfied` — in SDL, resolver bound |
| `payoutRequests` | `contradicted` — built under another name: `failedPayoutRequestsCursorPagination`, `failedPayoutRequestsOffsetPagination`, `payoutRequestsByEventCursorPagination`, `payoutRequestsByEventOffsetPagination` |
| `payoutRequestsForReview` | `contradicted` — built under another name: `payoutRequestsForReviewCursorPagination`, `payoutRequestsForReviewOffsetPagination` |


### Mutations

| Operation | State |
|---|---|
| `approvePayout` | `contradicted` — built under another name: `approvePayoutRequest` |
| `cancelPayout` | `contradicted` — built under another name: `cancelPayoutRequest` |
| `confirmBankVerification` | `absent` |
| `createBankAccount` | `already-satisfied` — in SDL, resolver bound |
| `deleteBankAccount` | `already-satisfied` — in SDL, resolver bound |
| `rejectPayout` | `contradicted` — built under another name: `rejectPayoutRequest` |
| `requestPayout` | `contradicted` — built under another name: `approvePayoutRequest`, `cancelPayoutRequest`, `completePayoutRequest`, `createPayoutRequest` |
| `retryPayout` | `contradicted` — built under another name: `retryPayoutRequest` |
| `setDefaultBankAccount` | `already-satisfied` — in SDL, resolver bound |
| `startBankVerification` | `absent` |
| `updateBankAccount` | `already-satisfied` — in SDL, resolver bound |


### Error codes

All 5 registered in `shared-library/.../error/ErrorCode.java`.


### Events

1 of 3 wire names appear in production source. Missing: `PayoutApprovedEvent`, `PayoutFailedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
