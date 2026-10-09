# ET-PAY-002 · reconciliation

> **Provider callbacks — signature, replay defence and the confirmation path**  
> Wave 3 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `partially-satisfied` — 3 of 5 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-PAY-002`; gate 0/15.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_webhook_receipts` | named and used, but no `@Document` binds it |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Error codes

All 2 registered in `shared-library/.../error/ErrorCode.java`.


### Events

0 of 2 wire names appear in production source. Missing: `PaymentDisputedEvent`, `WebhookReceivedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract
