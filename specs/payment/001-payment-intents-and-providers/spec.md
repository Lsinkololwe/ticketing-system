# ET-PAY-001 · Payment intents, the provider port and mobile-money collection

> **Conformance** · PDI Phase 5 payment idempotency · V3 §3.2 fund flow · V3 §8 payment integration

## 1. Capability

Every ticket this platform sells is paid for from a mobile-money wallet on a handset in
Zambia — MTN, Airtel or Zamtel. That transaction has properties nothing in a card world
prepares you for: it is asynchronous by design, the subscriber must physically approve it,
the result arrives minutes later over a channel the platform does not control, and the
words *pending*, *failed* and *timed out* mean three different things about where the money
is.

This spec builds the payment side of the purchase. It declares the **payment intent** — one
document per attempt to collect one reservation's total, carrying the idempotency key that
makes a retry safe — and the **provider port** behind which PawaPay sits, so that the day a
second aggregator is needed the answer is an adapter rather than a rewrite. It declares how
a subscriber's number is routed to the right mobile-money operator, how a decline is
translated into something a client can act on without leaking the subscriber's account
state, and what the platform does when the provider says nothing at all.

The rule that governs everything here is that **the platform never assumes a payment
failed.** A timeout is not a failure; it is an absence of information about money that may
well have moved. An intent with no answer stays pending, gets polled, and is escalated to a
human before anything is released — because releasing inventory and telling a buyer their
payment failed, while their wallet is K350 lighter, is the single worst thing this platform
can do.

## 2. Design decisions

**One intent per attempt, keyed by the client's idempotency key.** `booking_payment_intents`
carries a unique index on `idempotencyKey`, which is what makes a retry return the original
intent rather than creating a second charge. The Redis guard of
[ET-PLT-007](../../_platform/007-security-and-authorization/) R6 is the fast path; the
unique index is the guarantee.

**An intent and its attempts are separate documents.** One intent may involve several
provider calls — an initial collect, a status poll, a retry after a network error. Folding
those into the intent loses the sequence, and the sequence is exactly what an operator
needs when the money is somewhere ambiguous. `booking_payment_attempts` is one row per
call, carrying the provider's reference and its raw status.

**Everything the provider gives back is translated at the boundary.** PawaPay's status and
failure vocabulary is mapped into the platform's own `PaymentStatus` and `DeclineCategory`
in the adapter, in one table, and nothing downstream ever sees a provider string. This is
what makes the port real rather than nominal — a second aggregator writes its own table and
nothing else changes.

**The provider's raw message is logged, never returned.** A decline reason can name a
balance, an account state or a subscriber status. It is correlated in the log; the client
gets `PAYMENT_DECLINED` and a `declineCategory` from a closed five-value enum
([ET-PLT-005 §4](../../_platform/005-error-contract/)).

**A timeout is `PENDING`, not `FAILED`, and the difference is enforced.** Three outcomes,
three meanings: `SUCCEEDED` (the money moved), `FAILED` (the provider says it did not), and
`PENDING` (nobody knows). Only the second releases the reservation. A `PENDING` intent is
polled on a backoff schedule and, past `booking.payment.max-pending`, escalated to
[ET-ADM-003](../../admin/003-transaction-recovery/) with the inventory still held.

**The operator is derived from the MSISDN, and an unroutable number is refused before any
money moves.** Zambian mobile prefixes map to MTN, Airtel and Zamtel deterministically. A
number the platform cannot route is refused with `MSISDN_PROVIDER_UNSUPPORTED` carrying the
providers it does support — at reservation time, not after a failed collect.

**The buyer pays the ticket price; the platform's commission comes out of the organizer's
side.** There is no buyer-facing service fee (D-04, V3 §3.2). The provider's collection fee
is a platform cost recorded in the ledger, not an addition to the buyer's total. This is a
product decision with real margin consequences and it is recorded here so that adding a
buyer fee later is a deliberate change rather than a drift.

**The provider is called from outside the reservation transaction.** An HTTP call inside a
MongoDB transaction holds it open for the provider's latency, which at mobile-money speeds
is seconds. The intent is written and committed, then the provider is called, then the
outcome is applied — which is precisely why the intent document must exist before the call,
so a crash between the two leaves something to recover from.

**Amount is verified against the reservation at every step.** The intent charges the
reservation's `totalAmount`; the callback is checked against the intent's amount. A
mismatch is `PAYMENT_AMOUNT_MISMATCH` and is never reconciled silently — a provider
reporting a different figure than the platform expects is either a bug or a compromise, and
both need a human.

**Rejected alternatives**

- *Treating a timeout as a failure.* Releases the seat and tells a buyer their payment failed while their wallet is lighter.
- *One document for the intent and its attempts.* Loses the call sequence, which is the only thing that makes an ambiguous payment investigable.
- *Passing the provider's status through to the platform's domain.* Makes the port nominal; the second aggregator then changes every consumer.
- *Returning the provider's decline text to the client.* Leaks the subscriber's account state to whoever is holding the phone.
- *Calling the provider inside the reservation transaction.* Holds a transaction open across a mobile-money round trip during the on-sale minute.
- *Asking the buyer which network they are on.* They mistype it, and the platform can derive it from the number it already has.
- *A buyer-facing service fee.* A margin decision, not an implementation detail; recorded as rejected so that introducing one is deliberate.
- *Automatic retry of a declined payment.* It declines again, while the user watches.

## 3. Requirements

### ET-PAY-001-R1 · One intent per idempotency key, guaranteed at the database

WHEN a payment is initiated, THE SYSTEM SHALL create at most one intent per idempotency
key.

**Acceptance**
- [ ] `booking_payment_intents` carries a unique index on `idempotencyKey`
- [ ] A repeat with the same key returns the existing intent without calling the provider
- [ ] A `DuplicateKeyException` on that index maps to `IDEMPOTENCY_KEY_REUSED` when the fingerprint differs, and to returning the original when it matches ([ET-PLT-005](../../_platform/005-error-contract/) R7)
- [ ] Two parallel `initiatePayment` calls with one key produce exactly one intent and exactly one provider call
- [ ] The intent is written and **committed** before the provider is called
- [ ] The intent records `reservationId`, `amount`, `currency`, `msisdn`, `provider`, `status`, `idempotencyKey`

### ET-PAY-001-R2 · Six states, and only one of them releases the seat

THE SYSTEM SHALL admit exactly the six intent states of §4, and only `FAILED` and
`CANCELLED` SHALL release the reservation.

**Acceptance**
- [ ] `PaymentStatus` declares exactly `CREATED`, `SUBMITTED`, `PENDING`, `SUCCEEDED`, `FAILED`, `CANCELLED`
- [ ] `SUCCEEDED` confirms the reservation; `FAILED` and `CANCELLED` release it; `PENDING` does neither
- [ ] A transition to a terminal state from a terminal state is refused with `PAYMENT_ALREADY_COMPLETED` carrying `completedAt`
- [ ] A test drives all `(status, outcome)` pairs and asserts the reservation's fate for each
- [ ] No code path infers `FAILED` from an absent response, a timeout, or an exception
- [ ] `SUCCEEDED`, `FAILED` and `CANCELLED` are terminal

### ET-PAY-001-R3 · The provider sits behind a port, and its vocabulary stops at the adapter

THE SYSTEM SHALL define a `PaymentProviderPort` and SHALL translate every provider value at
the adapter.

**Acceptance**
- [ ] `PaymentProviderPort` declares `collect`, `status` and `refund`, each returning platform types
- [ ] `PawaPayAdapter` is the only class in the platform that names PawaPay, imports its types, or knows its URLs
- [ ] The provider's status vocabulary is mapped to `PaymentStatus` in one table in the adapter
- [ ] The provider's failure vocabulary is mapped to `DeclineCategory` — `INSUFFICIENT_FUNDS`, `SUBSCRIBER_UNREACHABLE`, `LIMIT_EXCEEDED`, `REJECTED_BY_USER`, `OTHER` — in one table
- [ ] Any provider value with no mapping becomes `OTHER` and increments a metric, so an unmapped value is visible rather than silent
- [ ] No provider string appears in any GraphQL response, error extension or event payload
- [ ] A second adapter can be added without changing any service, asserted by a test double implementing the port

### ET-PAY-001-R4 · A subscriber's number routes to an operator, or is refused early

WHEN a payment is initiated, THE SYSTEM SHALL derive the mobile-money operator from the
MSISDN, and IF it cannot, THEN THE SYSTEM SHALL refuse before calling the provider.

**Acceptance**
- [ ] The MSISDN is normalised to E.164 by `shared-library`'s `PhoneNumbers` ([ET-IDN-001](../../identity/001-phone-otp-identity/) R1)
- [ ] Zambian prefixes map deterministically to `MTN`, `AIRTEL` and `ZAMTEL` in one declared table
- [ ] An unroutable number is refused with `MSISDN_PROVIDER_UNSUPPORTED` carrying `supportedProviders`, and no intent is created
- [ ] The buyer is never asked to select their network
- [ ] The mapping table is configuration, so a new prefix range is a config change and not a deploy
- [ ] A test covers every declared prefix and a number outside all of them

### ET-PAY-001-R5 · A pending payment is polled, then escalated — never assumed failed

WHILE an intent is `PENDING`, THE SYSTEM SHALL poll the provider on a backoff schedule, and
IF it is still pending past the limit, THEN THE SYSTEM SHALL escalate it with the inventory
still held.

**Acceptance**
- [ ] A poll sweep under `lock:sweep:payment-poll` queries the provider for every `PENDING` intent on the §4 backoff schedule
- [ ] Polling stops at the first terminal answer
- [ ] An intent still `PENDING` past `booking.payment.max-pending` (PT30M) is flagged `needsReview` and surfaced to [ET-ADM-003](../../admin/003-transaction-recovery/)
- [ ] An escalated intent does **not** release its reservation
- [ ] No exception, timeout or connection failure transitions an intent to `FAILED`
- [ ] A test simulates a provider that never answers and asserts the intent is escalated, the seat is still held, and no refund is attempted
- [ ] The count of intents past `max-pending` is a metric and alerts

### ET-PAY-001-R6 · The amount is verified at every step

THE SYSTEM SHALL charge exactly the reservation's total and SHALL verify every reported
amount against the intent.

**Acceptance**
- [ ] The intent's `amount` equals the reservation's `totalAmount` exactly, in the same currency
- [ ] A provider response reporting a different amount refuses with `PAYMENT_AMOUNT_MISMATCH` carrying `expectedAmount`, does not confirm the reservation, and raises an alert
- [ ] No code path reconciles a mismatch by adopting the provider's figure
- [ ] Amounts cross the provider boundary as decimal strings, never as floats
- [ ] The buyer's total contains no platform service fee — the commission is deducted from the organizer's side ([ET-FIN-002](../../finance/002-commission/))
- [ ] The provider's collection fee is recorded as a platform cost in the ledger, not added to the buyer's charge

### ET-PAY-001-R7 · The provider is never called inside a transaction

THE SYSTEM SHALL call the provider outside any database transaction, with a bounded
timeout, from a contained adapter.

**Acceptance**
- [ ] No `@Transactional` method, and nothing it calls, invokes `PaymentProviderPort`
- [ ] Every provider call carries a connect and read timeout, both configured, neither defaulted
- [ ] The adapter's blocking work, if the SDK is blocking, is confined per [ET-PLT-001](../../_platform/001-runtime-baseline/) R1
- [ ] A circuit breaker opens after the configured failure rate and refuses with `PAYMENT_PROVIDER_UNAVAILABLE`, marked retryable, rather than queueing
- [ ] A provider outage produces `PAYMENT_PROVIDER_UNAVAILABLE` and leaves the reservation `HELD` for the buyer to retry
- [ ] Every provider call is recorded as a `booking_payment_attempts` row before it is made, so a crash mid-call leaves evidence

## 4. Model

### Documents

`booking_payment_intents`

| Field | Type | Notes |
|---|---|---|
| `_id` | `String` | |
| `reservationId`, `userId`, `eventId` | `String` | |
| `amount`, `currency` | `BigDecimal`, `String` | equals the reservation's total |
| `msisdn` | `String` | E.164 |
| `provider` | `MobileMoneyProvider` | `MTN`, `AIRTEL`, `ZAMTEL` |
| `status` | `PaymentStatus` | the six |
| `idempotencyKey` | `String` | **unique index** |
| `requestFingerprint` | `String` | SHA-256, [ET-PLT-007](../../_platform/007-security-and-authorization/) §4 |
| `providerReference` | `String` | the aggregator's id |
| `declineCategory` | `DeclineCategory` | on failure |
| `needsReview` | `boolean` | R5 escalation |
| `attemptCount`, `lastPolledAt` | `int`, `Instant` | |
| `createdAt`, `submittedAt`, `completedAt` | `Instant` | |
| `version` | `Long` | `@Version` |

`booking_payment_attempts` — one row per provider call.

| Field | Type | Notes |
|---|---|---|
| `_id`, `paymentIntentId` | `String` | |
| `attemptType` | `AttemptType` | `COLLECT`, `POLL`, `REFUND` |
| `providerReference` | `String` | unique sparse |
| `providerStatusRaw`, `providerMessageRaw` | `String` | **logged, never returned** |
| `mappedStatus` | `PaymentStatus` | the translation |
| `httpStatus`, `latencyMs` | `int` | |
| `requestedAt`, `respondedAt` | `Instant` | |

### The intent state machine

| # | From | Trigger | To |
|---|---|---|---|
| 1 | — | `initiatePayment` | `CREATED` |
| 2 | `CREATED` | provider accepted the collect | `SUBMITTED` |
| 3 | `CREATED` | provider unreachable | `PENDING` |
| 4 | `SUBMITTED` | provider reports awaiting subscriber | `PENDING` |
| 5 | `SUBMITTED` / `PENDING` | provider reports success | `SUCCEEDED` |
| 6 | `SUBMITTED` / `PENDING` | provider reports failure | `FAILED` |
| 7 | `CREATED` / `PENDING` | buyer cancels | `CANCELLED` |

`SUCCEEDED`, `FAILED` and `CANCELLED` are terminal. **Row 3 is the important one**: an
unreachable provider produces `PENDING`, never `FAILED`.

| Terminal state | Reservation |
|---|---|
| `SUCCEEDED` | confirmed ([ET-TKT-001](../../ticketing/001-reservation-and-hold/) R7) |
| `FAILED` | released |
| `CANCELLED` | released |
| *(still `PENDING` past the limit)* | **held**, escalated |

### The port

```java
public interface PaymentProviderPort {
    Mono<CollectResult> collect(CollectRequest request);   // idempotent on our reference
    Mono<PaymentStatusResult> status(String providerReference);
    Mono<RefundResult> refund(RefundRequest request);      // ET-FIN-004
}
```

Every type in that signature is a platform type. `PawaPayAdapter` is the only implementation
and the only class that names the provider.

### Status translation — the adapter's table

| PawaPay status | `PaymentStatus` |
|---|---|
| `ACCEPTED` | `SUBMITTED` |
| `SUBMITTED`, `ENQUEUED`, `PROCESSING` | `PENDING` |
| `COMPLETED` | `SUCCEEDED` |
| `FAILED`, `REJECTED` | `FAILED` |
| `DUPLICATE_IGNORED` | resolve by `status()` on the original reference |
| *(anything else)* | `PENDING` + `unmapped_provider_status` metric |

An unrecognised status maps to `PENDING`, never `FAILED` — the platform does not invent a
failure from a value it does not understand.

### Decline translation

| Provider reason | `DeclineCategory` |
|---|---|
| insufficient balance | `INSUFFICIENT_FUNDS` |
| subscriber not found, barred, unreachable | `SUBSCRIBER_UNREACHABLE` |
| transaction/daily limit | `LIMIT_EXCEEDED` |
| rejected by payer, timeout on PIN | `REJECTED_BY_USER` |
| *(anything else)* | `OTHER` + `unmapped_decline_reason` metric |

`providerMessageRaw` is written to `booking_payment_attempts` and to the correlated log.
It never reaches a response.

### MSISDN routing

| Prefix (after `+260`) | Operator |
|---|---|
| `95`, `96`, `76` | `MTN` |
| `97`, `77` | `AIRTEL` |
| `95x` reserved ranges, `50`, `21` | `ZAMTEL` |

Held in `booking.payment.msisdn-routing` as configuration so a new range is a config change.
Any number matching none is `MSISDN_PROVIDER_UNSUPPORTED`.

### Poll backoff

| Attempt | Delay after the previous |
|---|---|
| 1 | 10 s |
| 2 | 20 s |
| 3 | 40 s |
| 4 | 80 s |
| 5+ | 160 s, capped |

Polling stops at the first terminal answer, or at `booking.payment.max-pending`, whichever
comes first. Past that the intent is `needsReview` and the seat stays held.

### GraphQL

Subgraph `booking`. Every field carries `@auth` explicitly.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `paymentIntent(id)` | query | `AUTHENTICATED` | `PaymentIntent` |
| `paymentIntentForReservation(reservationId)` | query | `AUTHENTICATED` | `PaymentIntent` |
| `initiatePayment(input)` | mutation | `CUSTOMER` | `PaymentIntent!` |
| `cancelPayment(id)` | mutation | `CUSTOMER` | `PaymentIntent!` |
| `paymentAttempts(intentId)` | query | `ADMIN` | `[PaymentAttempt!]!` `@tag(name: "admin")` |

```graphql
input InitiatePaymentInput {
    reservationId: ID!
    msisdn: PhoneNumber!
    idempotencyKey: String!
}
```

`PaymentIntent` exposes `status`, `amount`, `currency`, `provider`, `declineCategory`,
`createdAt` and `completedAt`. It exposes **no** `providerStatusRaw`, `providerMessageRaw`
or `providerReference` outside the `@tag(name: "admin")` attempt type.

### Events

| Tier | Name | When | Consumers |
|---|---|---|---|
| module | `PaymentSubmittedEvent` | state 2 | metrics |
| module | `PaymentCompletedEvent` | state 5 | confirmation ([ET-TKT-001](../../ticketing/001-reservation-and-hold/)) |
| module | `PaymentFailedEvent` | states 6, 7 | release |
| bus | `booking.PaymentCompleted` v1 | after commit | identity → receipt |
| bus | `booking.PaymentFailed` v1 | after commit | identity → notify |

### Redis keys

| Key | TTL | Purpose |
|---|---|---|
| `idem:{key}` | 24 h | [ET-PLT-007](../../_platform/007-security-and-authorization/) §4 |
| `lock:sweep:payment-poll` | 30 s | the R5 poll mutex |

### Configuration

| Property | Value |
|---|---|
| `booking.payment.provider` | `PAWAPAY` |
| `booking.payment.connect-timeout` | `PT5S` |
| `booking.payment.read-timeout` | `PT30S` |
| `booking.payment.max-pending` | `PT30M` |
| `booking.payment.poll-interval` | `PT10S` base, exponential, capped at `PT160S` |
| `booking.payment.circuit-breaker.failure-rate` | 50% over 20 calls |
| `booking.payment.msisdn-routing` | the §4 table |
| `PAWAPAY_API_URL`, `PAWAPAY_API_TOKEN` | environment only |

### Error codes

`PAYMENT_INTENT_UNKNOWN`, `PAYMENT_ALREADY_COMPLETED`, `PAYMENT_DECLINED`,
`PAYMENT_AMOUNT_MISMATCH`, `PAYMENT_PROVIDER_UNAVAILABLE`, `MSISDN_PROVIDER_UNSUPPORTED` —
rows of [ET-PLT-005 §4](../../_platform/005-error-contract/) introduced by this spec.

## 5. Tasks

- [ ] **T1 · The intent and attempt documents; the unique idempotency index**
  - requirements: R1
  - files: `backend/booking-service/.../domain/model/`
  - verify: two parallel initiations with one key produce one intent and one provider call
  - parallel-safe: no
  - depends: —

- [ ] **T2 · `PaymentProviderPort` and the state machine**
  - requirements: R2, R3
  - files: `backend/booking-service/.../infrastructure/gateway/`
  - verify: no service names PawaPay; a test double implements the port with no service change
  - parallel-safe: no
  - depends: T1

- [ ] **T3 · `PawaPayAdapter`: the two translation tables and the unmapped metrics**
  - requirements: R3
  - files: `backend/booking-service/.../infrastructure/gateway/adapter/PawaPayAdapter.java`
  - verify: an unmapped status becomes `PENDING` and increments the metric; no provider string escapes
  - parallel-safe: yes
  - depends: T2

- [ ] **T4 · MSISDN routing and the early refusal**
  - requirements: R4
  - files: `backend/booking-service/.../domain/MsisdnRouting.java`
  - verify: every declared prefix routes; an unroutable number creates no intent
  - parallel-safe: yes
  - depends: T1

- [ ] **T5 · `initiatePayment`: commit the intent, then call, outside any transaction**
  - requirements: R1, R6, R7
  - files: `backend/booking-service/.../service/impl/PaymentServiceImpl.java`
  - verify: `./scripts/spec-lint.sh --events`; no `@Transactional` reaches the port
  - parallel-safe: no
  - depends: T3

- [ ] **T6 · The poll sweep, the backoff, and the escalation that holds the seat**
  - requirements: R5
  - files: `backend/booking-service/.../scheduler/PaymentPollSweeper.java`
  - verify: a never-answering provider escalates with the seat still held and no refund attempted
  - parallel-safe: yes
  - depends: T5

- [ ] **T7 · The circuit breaker and the timeout budget**
  - requirements: R7
  - files: `backend/booking-service/.../infrastructure/gateway/`
  - verify: a provider outage yields `PAYMENT_PROVIDER_UNAVAILABLE` and leaves the reservation `HELD`
  - parallel-safe: yes
  - depends: T3

- [ ] **T8 · Amount verification and the mismatch alert**
  - requirements: R6
  - files: `backend/booking-service/.../service/impl/PaymentServiceImpl.java`
  - verify: a mismatched provider amount refuses, does not confirm, and alerts
  - parallel-safe: yes
  - depends: T5

- [ ] **T9 · The subgraph half; provider fields behind `@tag(name: "admin")`**
  - requirements: R3
  - files: `backend/booking-service/src/main/resources/graphql/schema.graphqls`
  - verify: the public contract exposes no provider field
  - parallel-safe: no — shared SDL across booking's specs
  - depends: T5

## 6. Out of scope

| Capability | Spec |
|---|---|
| The reservation this intent pays for, and its confirmation | [ET-TKT-001](../../ticketing/001-reservation-and-hold/) |
| The provider's callback, its signature and replay defence | [ET-PAY-002](../002-webhooks-and-settlement/) |
| Where the money lands once collected | [ET-FIN-001](../../finance/001-escrow-and-ledger/) |
| Commission on the collected amount | [ET-FIN-002](../../finance/002-commission/) |
| Refunding a collected payment | [ET-FIN-004](../../finance/004-refunds-and-chargebacks/) |
| Reconciling the provider's settlement against the ledger | [ET-FIN-005](../../finance/005-reconciliation/) |
| Operator recovery of a stuck intent | [ET-ADM-003](../../admin/003-transaction-recovery/) |
| The idempotency-key contract itself | [ET-PLT-007](../../_platform/007-security-and-authorization/) |

Deliberately never in scope: **inferring failure from silence** (the seat is released and
the buyer is told they were not charged, while their wallet says otherwise), **a
buyer-facing service fee** (a margin decision, recorded as rejected so introducing one is
deliberate), and **automatic retry of a decline** (it declines again, while the user
watches).
