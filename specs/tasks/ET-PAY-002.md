# ET-PAY-002 · Provider callbacks — signature, replay defence, the confirmation path — tasks

> **Spec** [`specs/payment/002-webhooks-and-settlement/spec.md`](../payment/002-webhooks-and-settlement/spec.md) · **Wave 3** · `blocked_by:` ET-PLT-002, 005, 007, ET-TKT-001, ET-PAY-001
> **Screens** — **none.** The Coverage map: *"payment intents and webhook processing are provider-facing backend flows; their operator-facing surface is the Transaction Recovery queue's provider-detail drawer"* ([`ET-ADM-003`](ET-ADM-003.md)).
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-PAY-002 -DfailIfNoTests=false`
> `spec.yaml` declares **0 queries, 0 mutations** — this spec has no GraphQL surface at all.

The one unauthenticated entry point in the platform. Everything else is protected by a JWT; this
path is protected by a signature, and if that verification is wrong an attacker mints tickets for
free. It is also the path that **drives confirmation** — the client never asserts that it paid.

## R0 · Reconcile

`docs/PAYMENT_DATA_INTEGRITY_IMPLEMENTATION_PLAN.md` Phase 6 is authoritative on webhook
hardening. Existing: a `@Tag(name = "Payment Webhooks")` Swagger annotation and controller.

Classify, with these as the decisive questions:
- Is the signature verified against the **raw body**, **before** parsing? Parsing first means the
  parser runs on unauthenticated attacker input.
- Is the comparison **constant-time**?
- Is there a **timestamp/replay** check?
- Is a receipt recorded **before** processing, with a unique index?

## A · Backend

### BE-1 · Raw-body signature verification, constant-time, before parsing
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** a **one-byte mutation fails**; an **old timestamp fails**; **nothing parses
  first**. Assert the third by instrumenting the parser and proving it is never reached on an
  invalid signature.

### BE-2 · The receipt document, its unique index, and record-before-process
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** two parallel identical callbacks produce **one ticket**.
- Record first, then process. Processing first and recording after leaves a window where a
  retried callback — and providers do retry — issues a second ticket. Verify the unique index
  live via MongoDB MCP.

### BE-3 · The status-API verification and the `DISPUTED` path
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** no
- **Acceptance** a **signature-valid callback for a provider-failed payment issues no ticket**.
- A valid signature proves the message came from the provider, not that the payment succeeded.
  Confirming against the callback alone means anyone who obtains the signing key mints tickets;
  confirming against the provider's status API means they must also compromise the provider.

### BE-4 · Correlation order and the overtaking-callback tests
- **Spec** R4 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** **callback-then-response** and **response-then-callback** each yield **one
  ticket**.
- The callback frequently arrives before the initiation call returns. An implementation that
  assumes otherwise drops the callback as an orphan and the buyer waits for a poll.

### BE-5 · `applyPaymentOutcome` as the single convergent transition
- **Spec** R7 · **§5** T5 · **depends** BE-3 · **parallel-safe** **no — the convergence point**
- Callback, poll and reconciliation all funnel through **one** transition. Three paths writing the
  same outcome three ways is three chances to disagree.
- **Acceptance** callback and poll fired **simultaneously** produce one ticket; conservation and
  balance hold.

### BE-6 · Orphan retention, the re-match workflow and escalation
- **Spec** R5 · **§5** T6 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** an orphan matched later applies **once**; one unmatched past an hour **escalates**.
- Orphans are retained, never discarded. A callback the platform cannot match is money that moved.

### BE-7 · The response-code table and the budget
- **Spec** R6 · **§5** T7 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** every outcome returns its declared code; **nothing returns 500**.
- A 500 tells the provider to retry, which is right for a transient fault and wrong for a
  permanently malformed message — that one retries forever.

### BE-8 · Daily reconciliation and its four discrepancy classes
- **Spec** R8 · **§5** T8 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** seeded discrepancies of **each class** are classified in one run. Feeds
  [`ET-FIN-005`](ET-FIN-005.md).

### BE-9 · Exclude the webhook path from the JWT chain — deliberately and visibly
- **Spec** R1 · **§5** T9 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** the path is reachable unauthenticated and **rejects every unsigned request**.
- Write the exclusion so a reader sees it is intentional and sees what replaces the JWT. An
  unexplained `permitAll()` in a security config is indistinguishable from a mistake, and the next
  person to audit it will either break it or leave a real one alone.

## B · Contract

None. No GraphQL surface. The REST refusal shape follows
[`ET-PLT-005`](ET-PLT-005.md) BE-7 (RFC 9457), sharing one error vocabulary with GraphQL.

## C · Frontend

**None.** The buyer's view of this flow is [`ET-PAY-001`](ET-PAY-001.md) FE-2/FE-3's waiting and
outcome states, which observe the intent — the callback itself is invisible. The operator's view
is [`ET-ADM-003`](ET-ADM-003.md)'s provider-detail drawer.

Building a screen for this spec is a defect.

## D · Tests — this spec is almost entirely tests

### TS-1 · Signature *(L3 — the security boundary)*
- One-byte mutation of the body → reject.
- Old timestamp → reject (replay).
- Constant-time comparison.
- **The parser is never reached** on an invalid signature — instrument and assert.
- A valid signature over a **different** payment does not apply to this one.

### TS-2 · Replay and idempotency *(L3)*
Two parallel identical callbacks → one ticket. Unique index confirmed live via MCP. Delivery ×10
→ one effect.

### TS-3 · Trust *(L3, WireMock)*
Signature-valid callback + provider status `FAILED` → **no ticket**. This is the test that proves
the platform does not trust the callback's own claim.

### TS-4 · Ordering *(L3)*
Callback-then-response and response-then-callback → one ticket each. Plus callback arriving
during the initiation transaction.

### TS-5 · Convergence *(L3)*
Callback and poll simultaneously → one ticket; `Inventory.assertConserved` and
`Ledger.assertBalanced` hold. Add reconciliation as a third simultaneous path.

### TS-6 · Orphans *(L3)*
Late match applies once; unmatched past an hour escalates; orphans are never deleted.

### TS-7 · Response codes *(L2)*
Every outcome returns its declared code; **no path returns 500**, including on a malformed body.

### TS-8 · Reconciliation *(L3)*
Each of the four discrepancy classes seeded and classified in one run.

### TS-9 · Security config *(L3)*
The path is reachable unauthenticated; every unsigned request is rejected; **no other path** was
accidentally opened by the exclusion — enumerate the security chain and assert.

## E · Gate

- [ ] R0 recorded against PDI Phase 6; parse-before-verify classified `contradicted` if present
- [ ] Signature verified on the **raw body, before parsing**, in constant time
- [ ] Parser proven unreachable on invalid signature
- [ ] Replay window enforced; one-byte mutation rejected
- [ ] Receipt recorded **before** processing; unique index live
- [x] Two parallel identical callbacks → one ticket — `PaymentConfirmationPathTest.simultaneousCallbacksConfirmOnce` (ten callbacks, one CAS transition, one envelope, one confirm); mutation-verified 2026-09-13 by dropping the status criterion
- [x] Provider status API consulted; a signature-valid callback for a failed payment issues no ticket — `aForgedSuccessCallbackIssuesNothing`, `anUnavailableStatusApiIsNotAFailure`
- [x] Both callback orderings yield one ticket — `aCallbackAheadOfTheSubmissionResponseStands`, `submissionUsesTheStoredDepositId`
- [ ] Callback, poll and reconciliation converge through one transition
- [ ] Orphans retained, re-matched once, escalated past an hour
- [ ] Every outcome has a declared code; **nothing returns 500**
- [ ] JWT exclusion is explicit, documented, and opens no other path
- [ ] **No screen was built for this spec**
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-PAY-002 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented`
