# ET-PAY-001 · reconciliation

> **Payment intents, the provider port and mobile-money collection**  
> Wave 3 · `booking-service` · subgraph `booking` · priority `must` · spec `status: approved`  
> Measured 2026-08-31 against the working tree.

**Presence** `mostly-absent` — 12 of 18 §4 names exist.  
**Confidence** `presence only` — 0 test class(es) tagged `ET-PAY-001`; gate 0/14.

Presence is not conformance: this file records which names the tree contains, not whether they behave as §3 requires. The §3 requirement read is the second half of R0 and is **not** recorded here.


### Collections

| Collection | State |
|---|---|
| `booking_payment_attempts` | bound to an `@Document` |
| `booking_payment_intents` | bound to an `@Document` |


3 indexes are declared in §4. Index *definitions* are not checked here — verify them against a running database with the MongoDB MCP, per F-002.


### Queries

| Operation | State |
|---|---|
| `paymentAttempts` | `already-satisfied` as `paymentAttemptsByReservation` — **see the O-7 note below**; classified `contradicted` on 2026-08-31 and briefly `absent` on 2026-09-01, both wrong |
| `paymentIntent` | `absent` |
| `paymentIntentForReservation` | `absent` |


### Mutations

| Operation | State |
|---|---|
| `cancelPayment` | `contradicted` — built under another name: `cancelPaymentAttempt` |
| `initiatePayment` | `contradicted` — built under another name: `initiatePaymentAttempt` |


### Error codes

All 6 registered in `shared-library/.../error/ErrorCode.java`.


### Events

4 of 5 wire names appear in production source. Missing: `PaymentSubmittedEvent`


---

## Still to do for this spec

- [ ] Read §3 and classify **every requirement** `already-satisfied` / `partially-satisfied` / `contradicted` / `absent`
- [ ] Record the evidence that each `already-satisfied` requirement *executes*
- [ ] Verify each §4 index against a running database (MongoDB MCP)
- [ ] Classify the frontend surface against its `.dc.html` layout contract

---

## O-7 · `paymentAttempts(intentId)` — twice misclassified, and the second time was mine

This row moved three times, which is worth recording because each move was a different kind of
mistake.

**`contradicted` (2026-08-31).** A stem match found four `paymentAttemptsBy*` fields and reported
the §4 name as built under another name. That is what a near-name heuristic produces: leads, not
answers.

**`absent` (2026-09-01).** Re-reading §4 against the model showed `PaymentAttempt` carries no
`intentId` — no field, no finder, nothing. I concluded §4 asked for a query over a relationship
the model does not record, and reclassified it as a model gap needing `intentId` added.

**`already-satisfied` (2026-09-01, same day).** That conclusion was half right and the wrong half
mattered. `PaymentAttempt` really has no `intentId`, and it could not easily acquire one:
attempts are created in `initiatePayment(TicketReservation, …)` where no intent is in hand at all.
But `PaymentIntent.reservationId` carries `@Indexed(unique = true)`. **Intent and reservation are
1:1, and the database enforces it.** `PaymentAttempt.reservationId` therefore identifies the
intent exactly as an `intentId` would, and `paymentAttemptsByReservation(reservationId)` —
`ADMIN`, returning `[PaymentAttempt!]!` — is §4's operation under a different argument name.

§4 was corrected rather than the model. Adding `intentId` would have denormalised a key the unique
index already guarantees equivalent, with a backfill over existing documents and a permanent drift
risk between two fields that must agree, in exchange for no information the platform did not have.

**What the two wrong answers have in common:** both were reached by comparing the §4 row against
one file. The first compared it against a list of field names; the second against the
`PaymentAttempt` model. Neither looked at `PaymentIntent`, where the answer was one annotation.
