# ET-ADM-001 · reconciliation

> **The approvals workbench — queues, SLA and escalation**  
> Wave 6 · `all` · subgraph `all` · priority `should` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 3 of 14 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-ADM-001`; gate 0/14.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `catalog_approval_escalations` | bound to an `@Document` |
| `catalog_approval_timelines` | bound to an `@Document` |
| `identity_review_claims` | **constant declared, used nowhere** |


6 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `approvalMetrics` | `absent` |
| `approvalTimeline` | `absent` |
| `eventApprovalQueue` | `absent` |
| `myClaimedItems` | `absent` |
| `openEscalations` | `absent` |
| `organizerApprovalQueue` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `bulkApproveEvents` | `absent` |
| `claimReviewItem` | `absent` |
| `releaseReviewItem` | `absent` |


### Events

1 of 2 wire names appear in production source. Missing: `OrganizationSubmittedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
