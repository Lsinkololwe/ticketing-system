# ET-ADM-003 · reconciliation

> **Transaction recovery — stuck money and the operator's tools**  
> Wave 6 · `booking-service` · subgraph `booking` · priority `should` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 2 of 17 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-ADM-003`; gate 0/13.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_recovery_proposals` | **constant declared, used nowhere** |


2 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `deadLetters` | `absent` |
| `myRecoveryProposals` | `absent` |
| `pendingRecoveryProposals` | `absent` |
| `recoveryItemDetail` | `absent` |
| `recoveryMetrics` | `absent` |
| `recoveryQueue` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `bulkRetry` | `contradicted` — built under another name: `bulkRetryFailedPayouts` |
| `confirmRecoveryAction` | `absent` |
| `discardDeadLetter` | `absent` |
| `markForReview` | `contradicted` — built under another name: `bulkMarkPayoutsForReview`, `markPayoutForReview` |
| `proposeRecoveryAction` | `absent` |
| `replayDeadLetter` | `absent` |
| `requeryProvider` | `absent` |
| `resolveReconciliationItem` | `already-satisfied` — in SDL, resolver bound |
| `withdrawRecoveryProposal` | `absent` |


### Error codes

All 1 registered in `shared-library/.../error/ErrorCode.java`.


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
