# ET-PAY-002 · Provider callbacks — signature, replay defence and the confirmation path

> **Conformance** · PDI Phase 6 webhook hardening · V3 §8 payment consistency · V3 §12 settlement

## 1. Capability

The moment a buyer approves a payment on their handset, the platform learns about it from a
callback — an HTTP request, unauthenticated by default, arriving from the public internet,
carrying an assertion that money has moved. Everything downstream of that request is
irreversible: a ticket is issued, escrow is credited, commission is recorded. It is the
single most attackable surface the platform exposes, and it is also the surface most likely
to behave badly for entirely innocent reasons — arriving twice, arriving out of order,
arriving before the API call that caused it has returned, or not arriving at all.

This spec hardens it. It declares the signature verification that is the only thing making
the callback trustworthy, the receipt log that makes a replay a no-op rather than a second
ticket, the ordering defence for the callback that overtakes its own API response, and the
reconciliation that catches what never arrived at all. It declares that a callback is
**evidence, not instruction**: the platform records what the provider said, then verifies it
against the provider's own status API before acting on anything that moves money.

And it draws the line between the two ways the platform learns a payment succeeded — the
callback and the poll of [ET-PAY-001](../001-payment-intents-and-providers/) — by making
them converge on one idempotent operation. Both are allowed to fire. Both frequently will.
Exactly one ticket comes out.

## 2. Design decisions

**Signature first, before anything else, including parsing.** The raw request body is
verified against the provider's HMAC signature before it is deserialised, because a parser
is an attack surface and an unverified body should never reach one. A failed signature is
`WEBHOOK_SIGNATURE_INVALID`, logged with the source address, and returns 401 without
detail.

**Every callback is recorded before it is processed.** `booking_webhook_receipts` carries a
unique index on the provider's event id. The insert is what detects a replay: a duplicate
key means this callback has been seen, and the handler returns 200 without re-applying
anything. Recording first also means a callback that crashes the handler is still evidence
that it arrived.

**A callback is evidence; the provider's status API is the authority.** The platform does
not credit escrow because an HTTP request said so. On a callback claiming success, it calls
`PaymentProviderPort.status(providerReference)` and acts on that answer. This costs one
round trip per payment and removes an entire class of attack — a forged or replayed
callback with a valid-looking signature cannot manufacture a ticket, because the provider
will not confirm it.

**The callback may arrive before the API response, and the platform must survive it.** The
provider can call back faster than its own `collect` returns. The intent document exists
before the call ([ET-PAY-001](../001-payment-intents-and-providers/) R7), so the callback
has something to find — but `providerReference` may not be written yet. The callback
therefore correlates on the platform's own reference, which is sent to the provider as the
`depositId`, and never solely on the provider's.

**A callback for an unknown reference is retained, not rejected.** It is recorded with
`status = ORPHANED` and re-matched by a sweep. Rejecting it loses the only evidence that a
payment the platform lost track of actually succeeded — and that is precisely the payment
that matters.

**Always 200, except for a bad signature.** A provider that receives a 500 retries, often
aggressively, and a handler that 500s on a business condition converts one problem into a
retry storm. Every outcome the platform can classify returns 200; only an unverifiable
signature returns 401 and only genuine unavailability returns 503.

**The callback and the poll converge on one idempotent operation.** `applyPaymentOutcome`
is the only path that transitions an intent to a terminal state, and it is a
compare-and-set. Both the callback and the poll call it. The loser is a no-op.

**Reconciliation is the backstop for silence.** A daily reconciliation against the
provider's transaction list finds payments that succeeded with no callback and no
successful poll. Everything above handles the callbacks that arrive; this handles the ones
that do not, and it is the only mechanism that does.

**Rejected alternatives**

- *Trusting the callback body without a status check.* One valid signature — leaked, replayed, or from a compromised provider account — becomes free tickets.
- *Parsing before verifying.* Hands an unauthenticated body to a deserialiser.
- *Deduplicating on the payload's content.* Two genuine callbacks for one payment differ only in a timestamp; two replays are identical. Content hashing conflates them.
- *Returning 4xx for a business condition.* Turns a provider retry policy into a denial of service against the platform.
- *Rejecting a callback whose reference is unknown.* Discards the evidence that the platform lost a payment.
- *Correlating solely on the provider's reference.* Fails for the callback that overtakes the response that would have supplied it.
- *An IP allowlist instead of signature verification.* Aggregators change egress addresses without notice, and an allowlist authenticates a network path rather than a message.

## 3. Requirements

### ET-PAY-002-R1 · The signature is verified before the body is parsed

WHEN a callback arrives, THE SYSTEM SHALL verify its signature over the raw body before
deserialising it, and IF verification fails, THEN THE SYSTEM SHALL reject it.

**Acceptance**
- [ ] The handler reads the raw body as bytes and computes the provider's HMAC over exactly those bytes, before any JSON parsing
- [ ] The comparison is constant-time (`MessageDigest.isEqual`)
- [ ] The signing secret is environment-sourced and appears in no committed file
- [ ] A failed verification returns 401 with no body detail, logs the source address and the reference, and processes nothing
- [ ] A callback with a valid signature over a body that has been modified in transit fails, asserted by a test that mutates one byte
- [ ] A replayed signature older than `booking.webhook.max-age` (PT5M) by its own timestamp header is rejected
- [ ] `WEBHOOK_SIGNATURE_INVALID` is raised and counted as a metric that alerts on a rate

### ET-PAY-002-R2 · Every callback is recorded, and a replay changes nothing

WHEN a callback is received, THE SYSTEM SHALL record it before processing, and IF it has
been seen, THEN THE SYSTEM SHALL return success without re-applying it.

**Acceptance**
- [ ] `booking_webhook_receipts` carries a unique index on `providerEventId`
- [ ] The receipt is inserted **before** any business processing
- [ ] A duplicate insert is caught, mapped to `WEBHOOK_REPLAYED`, logged, and returns 200 with no side effect
- [ ] Two identical callbacks delivered in parallel produce exactly one ticket, asserted under real concurrency
- [ ] The receipt records the raw body, the headers used for verification, the source address and the outcome
- [ ] Receipts are removed by a TTL index at 90 days ([ET-PLT-002](../../_platform/002-persistence-baseline/) §4)
- [ ] A callback that crashes the handler still leaves its receipt

### ET-PAY-002-R3 · The provider's status API is the authority, not the callback

WHEN a callback claims a terminal outcome, THE SYSTEM SHALL verify it against the
provider's status API before applying anything that moves money.

**Acceptance**
- [ ] A success callback triggers `PaymentProviderPort.status(providerReference)` and the platform acts on **that** answer
- [ ] A callback claiming success whose status check does not agree applies nothing, marks the receipt `DISPUTED`, and alerts
- [ ] A failure callback also verifies, so a forged failure cannot release a seat somebody paid for
- [ ] The status check is subject to the timeouts and circuit breaker of [ET-PAY-001](../001-payment-intents-and-providers/) R7
- [ ] IF the status check is unavailable, THEN the receipt is marked `PENDING_VERIFICATION` and retried — the callback is not applied on faith and is not discarded
- [ ] A test forges a signature-valid callback for a payment the provider reports as failed, and asserts no ticket is issued

### ET-PAY-002-R4 · A callback that overtakes its own API response still lands

IF a callback arrives before the initiating call has returned, THEN THE SYSTEM SHALL
correlate it and apply it correctly.

**Acceptance**
- [ ] The platform's own reference is sent to the provider as the deposit identifier and is what the callback correlates on
- [ ] Correlation tries the platform reference first and the provider reference second
- [ ] A callback arriving before `providerReference` is written to the intent is applied correctly, asserted by a test that delivers it during the `collect` call
- [ ] The `collect` response writing `providerReference` never overwrites a terminal status the callback already set
- [ ] The intent's terminal transition is a compare-and-set, so the later writer is a no-op
- [ ] A test interleaves the callback and the response in both orders and asserts one ticket in each

### ET-PAY-002-R5 · An unmatched callback is kept and re-matched

IF a callback names a reference the platform does not recognise, THEN THE SYSTEM SHALL
retain it and attempt to match it later.

**Acceptance**
- [ ] An unmatched callback is recorded with `status = ORPHANED` and returns 200
- [ ] A sweep under `lock:sweep:webhook-orphans` re-attempts matching on `booking.webhook.orphan-retry` (PT5M)
- [ ] A match found later applies the outcome through the same idempotent path
- [ ] An orphan unmatched past `booking.webhook.orphan-escalate` (PT1H) is surfaced to [ET-ADM-003](../../admin/003-transaction-recovery/)
- [ ] The orphan count is a metric and alerts when it is non-zero for longer than the escalation window
- [ ] No orphan is deleted before its TTL, regardless of whether it ever matched

### ET-PAY-002-R6 · The endpoint answers 200 for everything it can classify

THE SYSTEM SHALL return 200 for every business outcome and reserve non-2xx for
unverifiable signatures and genuine unavailability.

**Acceptance**
- [ ] Replay, orphan, disputed, already-terminal and unknown-status all return 200
- [ ] Only an invalid or expired signature returns 401
- [ ] Only an unavailable dependency the handler needs returns 503
- [ ] No business exception escapes the handler as a 500
- [ ] The endpoint responds within `booking.webhook.response-budget` (PT3S), deferring verification and application to an asynchronous path when it cannot
- [ ] A test drives each outcome and asserts the status code

### ET-PAY-002-R7 · Callback and poll converge on one idempotent transition

THE SYSTEM SHALL apply every payment outcome through one operation, and concurrent
applications SHALL produce one effect.

**Acceptance**
- [ ] `applyPaymentOutcome(intentId, outcome, providerReference)` is the only method that sets an intent to `SUCCEEDED`, `FAILED` or `CANCELLED`
- [ ] It is a compare-and-set from a non-terminal status; a losing writer returns without side effect
- [ ] The callback path and the poll path both call it, asserted by a test that fires both simultaneously and observes one ticket
- [ ] Applying to an already-terminal intent returns the existing outcome and is not an error
- [ ] The confirmation it triggers is [ET-TKT-001](../../ticketing/001-reservation-and-hold/) R7's single transaction
- [ ] `Inventory.assertConserved()` and `Ledger.assertBalanced()` hold after a doubled application

### ET-PAY-002-R8 · Reconciliation finds the payments that were never announced

THE SYSTEM SHALL reconcile against the provider's own records and resolve every payment the
callbacks missed.

**Acceptance**
- [ ] A daily reconciliation fetches the provider's transactions for the period and compares them to `booking_payment_intents`
- [ ] A provider transaction with no matching intent is recorded and escalated — it is money the platform holds and cannot attribute
- [ ] An intent the provider reports as succeeded but which the platform has as `PENDING` is applied through `applyPaymentOutcome`
- [ ] An intent the platform has as `SUCCEEDED` that the provider does not report is escalated as a discrepancy, never silently reversed
- [ ] Every discrepancy class is counted as a metric and a non-zero unresolved count alerts
- [ ] The run is recorded in `booking_reconciliation_runs` ([ET-FIN-005](../../finance/005-reconciliation/))
- [ ] A test seeds each discrepancy class and asserts one run classifies all of them

## 4. Model

### The receipt

`booking_webhook_receipts`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `providerEventId` | `String` | **unique** — the replay guard |
| `provider` | `String` | |
| `platformReference`, `providerReference` | `String` | correlation, in that order |
| `paymentIntentId` | `String` | null while `ORPHANED` |
| `rawBody` | `String` | as received, for dispute |
| `signatureHeader`, `timestampHeader` | `String` | what was verified |
| `sourceAddress` | `String` | |
| `status` | `ReceiptStatus` | `RECEIVED`, `VERIFIED`, `APPLIED`, `REPLAYED`, `ORPHANED`, `DISPUTED`, `PENDING_VERIFICATION` |
| `claimedOutcome`, `verifiedOutcome` | `PaymentStatus` | what it said, and what the provider confirmed |
| `receivedAt`, `verifiedAt`, `appliedAt` | `Instant` | `receivedAt` carries a **90-day TTL index** |

`rawBody` is retained because a payment dispute months later is argued from what the
provider actually sent. It is subject to [ET-PLT-008](../../_platform/008-data-protection/)'s
PII rules — it carries an MSISDN.

### The handler, in order

```
1  read the raw body as bytes                       ← no parsing yet
2  verify HMAC over those bytes, constant-time      ← 401 on failure, nothing else runs
3  check the timestamp header against max-age       ← 401 on an old replay
4  parse
5  INSERT the receipt (unique on providerEventId)   ← duplicate ⇒ REPLAYED, 200, stop
6  correlate: platformReference, then providerReference
       no match ⇒ ORPHANED, 200, stop
7  call PaymentProviderPort.status(reference)       ← the authority
       unavailable ⇒ PENDING_VERIFICATION, 200, retried
       disagrees   ⇒ DISPUTED, 200, alert, apply nothing
8  applyPaymentOutcome(...)                         ← compare-and-set, idempotent
9  200
```

Steps 1–3 run before any allocation driven by the body. Step 5 is the replay defence and
runs before any business logic. Step 7 is what makes a forged callback useless.

### The convergent transition

```java
// the ONLY path to a terminal payment status — callback and poll both arrive here
public Mono<ApplyResult> applyPaymentOutcome(String intentId, PaymentStatus outcome,
                                             String providerReference) {
    return intents.compareAndSetTerminal(intentId, outcome, providerReference, clock.instant())
        .flatMap(won -> won
            ? switch (outcome) {
                case SUCCEEDED -> purchases.confirm(intentId);      // ET-TKT-001 R7, one transaction
                case FAILED, CANCELLED -> reservations.release(intentId);
                default -> Mono.error(new IllegalArgumentException("not terminal"));
              }
            : intents.find(intentId).map(ApplyResult::alreadyApplied));   // a no-op, not an error
}
```

### Response codes

| Outcome | Code | Why |
|---|---|---|
| applied | 200 | |
| replayed | 200 | a retry must not be encouraged |
| orphaned | 200 | retained and re-matched |
| disputed | 200 | recorded, alerted, applied nothing |
| pending verification | 200 | retried asynchronously |
| already terminal | 200 | |
| invalid or expired signature | **401** | the only untrusted case |
| dependency unavailable | 503 | genuine, and a retry is correct |

Nothing returns 4xx for a business condition and nothing returns 500.

### Correlation order

| # | Key | Source |
|---|---|---|
| 1 | `platformReference` | the platform's own id, sent as the provider's deposit identifier |
| 2 | `providerReference` | the aggregator's id, which may not be recorded yet |

Trying 1 first is what makes R4's overtaking callback work.

### REST

| Method | Path | Auth | Notes |
|---|---|---|---|
| `POST` | `/api/webhooks/pawapay` | **signature only** | public path; no JWT; not under `/api/internal/**` |

The path is excluded from the JWT filter chain and is protected exclusively by R1. It is
declared here so that the exclusion is a deliberate, reviewed entry rather than a gap in
[ET-PLT-007](../../_platform/007-security-and-authorization/) R5's internal surface.

### Sweeps

| Sweep | Lock | Interval | Purpose |
|---|---|---|---|
| orphan re-match | `lock:sweep:webhook-orphans` | `PT5M` | R5 |
| pending verification retry | `lock:sweep:webhook-verify` | `PT1M` | R3 |
| daily reconciliation | `lock:sweep:payment-reconciliation` | `P1D` | R8 |

### Configuration

| Property | Value |
|---|---|
| `booking.webhook.max-age` | `PT5M` |
| `booking.webhook.response-budget` | `PT3S` |
| `booking.webhook.orphan-retry` | `PT5M` |
| `booking.webhook.orphan-escalate` | `PT1H` |
| `booking.webhook.reconciliation-cron` | daily, off-peak |
| `PAWAPAY_WEBHOOK_SECRET` | environment only |

### Error codes

`WEBHOOK_SIGNATURE_INVALID`, `WEBHOOK_REPLAYED` — rows of
[ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec. Neither
reaches a GraphQL client; both are REST outcomes and metrics.

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| module | `WebhookReceivedEvent` | step 5 | metrics |
| module | `PaymentDisputedEvent` | step 7 disagreement | alerting, [ET-ADM-003](../../admin/003-transaction-recovery/) |

No bus event: the outcome's bus events are
[ET-PAY-001](../001-payment-intents-and-providers/)'s, published from the confirmation.

## 5. Tasks

- [ ] **T1 · Raw-body signature verification, constant-time, before parsing**
  - requirements: R1
  - files: `backend/booking-service/.../infrastructure/webhook/SignatureVerifier.java`
  - verify: a one-byte mutation fails; an old timestamp fails; nothing parses first
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The receipt document, its unique index, and record-before-process**
  - requirements: R2
  - files: `backend/booking-service/.../domain/model/WebhookReceipt.java`
  - verify: two parallel identical callbacks produce one ticket
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · The status-API verification and the `DISPUTED` path**
  - requirements: R3
  - files: `backend/booking-service/.../infrastructure/webhook/WebhookProcessor.java`
  - verify: a signature-valid callback for a provider-failed payment issues no ticket
  - parallel-safe: no
  - depends: T2

- [ ] **T4 · Correlation order and the overtaking-callback tests**
  - requirements: R4
  - files: `backend/booking-service/.../infrastructure/webhook/`
  - verify: callback-then-response and response-then-callback each yield one ticket
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · `applyPaymentOutcome` as the single convergent transition**
  - requirements: R7
  - files: `backend/booking-service/.../service/impl/PaymentOutcomeService.java`
  - verify: callback and poll fired simultaneously produce one ticket; conservation and balance hold
  - parallel-safe: no — the convergence point
  - depends: T3

- [ ] **T6 · Orphan retention, the re-match sweep and escalation**
  - requirements: R5
  - files: `backend/booking-service/.../scheduler/WebhookOrphanSweeper.java`
  - verify: an orphan matched later applies once; one unmatched past an hour escalates
  - parallel-safe: yes
  - depends: T5

- [ ] **T7 · The response-code table and the budget**
  - requirements: R6
  - files: `backend/booking-service/.../web/rest/WebhookController.java`
  - verify: every outcome returns its declared code; nothing returns 500
  - parallel-safe: yes
  - depends: T5

- [ ] **T8 · Daily reconciliation and its four discrepancy classes**
  - requirements: R8
  - files: `backend/booking-service/.../scheduler/PaymentReconciliationJob.java`
  - verify: seeded discrepancies of each class are classified in one run
  - parallel-safe: yes
  - depends: T5

- [ ] **T9 · Exclude the webhook path from the JWT chain, deliberately and visibly**
  - requirements: R1
  - files: `backend/booking-service/.../config/security/SecurityConfig.java`
  - verify: the path is reachable unauthenticated and rejects every unsigned request
  - parallel-safe: no
  - depends: T1

## 6. Out of scope

| Capability | Spec |
|---|---|
| The intent, the provider port and the poll | [ET-PAY-001](../001-payment-intents-and-providers/) |
| The reservation and the confirmation transaction | [ET-TKT-001](../../ticketing/001-reservation-and-hold/) |
| The escrow credit and journal entries | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| Refund callbacks and chargebacks | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| The full financial reconciliation and close | [ET-FIN-005](../../finance/005-reconciliation/) |
| Operator resolution of disputed and orphaned payments | [ET-ADM-003](../../admin/003-transaction-recovery/) |
| Retaining and erasing the MSISDN in `rawBody` | [ET-PLT-008](../../_platform/008-data-protection/) |
| Rate limiting the webhook endpoint | [ET-PLT-011](../../_platform/011-rate-limiting-and-abuse/) |

Deliberately never in scope: **acting on a callback without verifying it against the
provider** (one leaked signature becomes free tickets), **an IP allowlist in place of
signatures** (it authenticates a network path, not a message), and **4xx for a business
condition** (it turns a provider's retry policy into a denial of service).
