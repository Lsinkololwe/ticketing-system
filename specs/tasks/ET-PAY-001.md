# ET-PAY-001 · Payment intents, the provider port and mobile-money collection — tasks

> **Spec** [`specs/payment/001-payment-intents-and-providers/spec.md`](../payment/001-payment-intents-and-providers/spec.md) · **Wave 3** · `blocked_by:` ET-PLT-002, 003, 005, 007, ET-TKT-001
> **Screens** the payment step of `Ticketing - Discover & Checkout.dc.html` only. Provider internals are **admin-tagged** and surface in [`ET-ADM-003`](ET-ADM-003.md)'s recovery drawer — the Coverage map places them there, not in a screen of their own.
> **Verify** `mvn -q -f backend/booking-service test -Dgroups=ET-PAY-001 -DfailIfNoTests=true` · `compose-supergraph.sh --static`

**D-06**: PawaPay only at launch, behind a `PaymentProviderPort`. MTN, Airtel and Zamtel reach the
platform through one aggregator; the port exists so the second aggregator is an adapter, not a
rewrite.

## R0 · Reconcile

`PaymentIntent`, `PaymentAttempt`, `PaymentService`, `PaymentAttemptService` and
`PaymentSubjectMigrationService` exist. Classify — and check the two things that decide whether
this is safe:
- Does any service class **name PawaPay**? Every such reference is `contradicted` (R2).
- Does `initiatePayment` call the provider **inside** a `@Transactional` method? That holds a
  MongoDB transaction open across a network call of unknown duration, which under on-sale load
  exhausts the session pool.

## A · Backend

### BE-1 · The intent and attempt documents; the unique idempotency index
- **Spec** R1 · **§5** T1 · **depends** R0 · **parallel-safe** no
- **Acceptance** two parallel initiations with one key produce **one intent and one provider
  call**. Two provider calls means two debits on the buyer's handset.

### BE-2 · `PaymentProviderPort` and the state machine
- **Spec** R2, R3 · **§5** T2 · **depends** BE-1 · **parallel-safe** no
- **Acceptance** **no service names PawaPay**; a test double implements the port with **no service
  change**. That second clause is the real test of the abstraction — if swapping the
  implementation requires touching a service, the port is decorative.

### BE-3 · `PawaPayAdapter` — the two translation tables and the unmapped metrics
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** an unmapped provider status becomes **`PENDING`** and increments a metric; **no
  provider string escapes** the adapter.
- Unmapped → `PENDING`, never `FAILED`. Treating an unknown status as failure releases a seat for
  a payment that may still succeed, and then the buyer is charged for nothing. `PENDING` costs a
  poll; the alternative costs a refund and a support ticket.

### BE-4 · MSISDN routing and the early refusal
- **Spec** R4 · **§5** T4 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** every declared prefix routes; an unroutable number **creates no intent**. Refuse
  before the intent exists, not after — an intent for a number no provider serves is a row that
  will sit `PENDING` until a sweep escalates it.

### BE-5 · `initiatePayment` — commit the intent, **then** call, outside any transaction
- **Spec** R1, R6, R7 · **§5** T5 · **depends** BE-3 · **parallel-safe** no
- **Acceptance** no `StreamBridge.send` inside a `@Transactional` method; **no `@Transactional`
  reaches the port**.
- Commit-then-call is the order that survives a crash: an intent with no provider call is
  recoverable by the poll sweep; a provider call with no intent is money in flight the platform
  has no record of.

### BE-6 · The poll sweep, the backoff, and the escalation that holds the seat
- **Spec** R5 · **§5** T6 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** a never-answering provider **escalates with the seat still held and no refund
  attempted**.
- Same rule as [`ET-TKT-001`](ET-TKT-001.md) BE-7: while the outcome is unknown, hold. Refunding a
  payment that later succeeds, or releasing a seat that was paid for, are both worse than a
  ten-minute wait and an operator's attention.

### BE-7 · The circuit breaker and the timeout budget
- **Spec** R7 · **§5** T7 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** a provider outage yields `PAYMENT_PROVIDER_UNAVAILABLE` and leaves the
  reservation **`HELD`** — the buyer keeps their seat and can retry when the provider returns.

### BE-8 · Amount verification and the mismatch alert
- **Spec** R6 · **§5** T8 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** a mismatched provider amount **refuses, does not confirm, and alerts**. All
  three. Confirming on a mismatch issues a ticket for money that was not collected.

### BE-9 · The subgraph half; provider fields behind `@tag(name: "admin")`
- **Spec** R3 · **§5** T9 · **depends** BE-5 · **parallel-safe** **no — shared booking SDL**
- **Acceptance** the **public contract exposes no provider field.** Provider names, reference ids
  and raw statuses are operational detail; leaking them tells every client which aggregator the
  platform depends on.

## B · Contract

### GQL-1 · 3 queries, 2 mutations
- **depends** BE-9 · **parallel-safe** no *(sequence against booking's other SDL tasks)*
- `compose-supergraph.sh --static` → `npm run codegen` → commit; restart the local router.

## C · Frontend — the payment step only

### FE-1 · Mobile-money entry
- **depends** GQL-1, F0-1 · **parallel-safe** no
- MSISDN input, normalised as in [`ET-IDN-001`](ET-IDN-001.md) BE-1. The **detected provider is
  shown** (MTN / Airtel / Zamtel) so the buyer can catch a mistyped number before a PIN prompt
  arrives on the wrong handset.
- Unroutable prefixes refuse **client-side too**, with the reason — but the server is the rule.
- **testids** `payment-msisdn`, `payment-provider-detected`, `payment-submit`, `payment-msisdn-error`

### FE-2 · The waiting state — **eight seconds to four minutes**
- **depends** FE-1 · **parallel-safe** no
- This is the longest wait in the product and the one most likely to be abandoned. It must say
  what is happening: *check your phone and approve the payment*.
- Poll on a **visibility-aware interval** (**D-12** — polling, not subscriptions). Back off; do
  not hammer during a four-minute wait.
- **Show the reservation countdown alongside** ([`ET-TKT-001`](ET-TKT-001.md) FE-3). The buyer
  needs to know both that the payment is pending and how long the seat is held.
- A skeleton or spinner alone is not enough here — an unexplained four-minute spinner reads as a
  hung app.
- **testids** `payment-waiting`, `payment-waiting-instruction`, `payment-hold-remaining`

### FE-3 · Outcome states
- **depends** FE-2 · **parallel-safe** yes
- **Succeeded** → tickets ([`ET-TKT-002`](ET-TKT-002.md)). **Failed** → the seat is still held;
  offer retry with the **same** idempotency key. **Provider unavailable** → seat held, retry
  later, explicitly reassuring. **Pending past the budget** → escalated; tell the buyer the seat
  is held and they will be contacted — do **not** tell them it failed.
- That last state is the one implementations get wrong: reporting failure on an unresolved payment
  invites a second attempt and a genuine double charge.
- **testids** `payment-success`, `payment-failed`, `payment-unavailable`, `payment-escalated`, `payment-retry`

### FE-4 · No provider internals on screen
- **depends** BE-9 · **parallel-safe** yes
- No aggregator name, no provider reference id, no raw status string. The detected **network** is
  fine; the **aggregator** is not.
- **Acceptance** the compliance suite asserts no admin-tagged field is queried by the ticketing app.

## D · Tests

### TS-1 · Idempotency *(L3)* — two parallel initiations with one key → one intent, one provider call.

### TS-2 · The port *(L1/L2)*
No service names PawaPay (source scan). A test double satisfies the port with **zero** service
changes — assert by compiling the services against the double.

### TS-3 · Adapter *(L3, WireMock)*
Both translation tables; an unmapped status → `PENDING` + metric; no provider string escapes.

### TS-4 · Routing *(L1)* — every prefix routes; unroutable creates no intent.

### TS-5 · Transaction discipline *(L2 — lint-as-test)*
No `@Transactional` reaches the port; no `StreamBridge.send` inside a transaction.

### TS-6 · Failure modes *(L3, the six `Providers` stubs from [`ET-PLT-006`](ET-PLT-006.md) BE-5)*
- Never-answering provider → escalate, **seat held, no refund attempted**.
- Outage → `PAYMENT_PROVIDER_UNAVAILABLE`, reservation stays `HELD`.
- Amount mismatch → refuse + no confirm + alert.
- Timeout budget and circuit breaker observed.

### TS-7 · Contract *(L4)* — the public contract exposes no provider field.

### TS-8 · e2e *(L5, ticketing — needs F0-1, F0-4)*
- Entry → waiting → each of the four outcomes.
- Retry after failure reuses the **same** key and produces one charge.
- Waiting state survives tab backgrounding and resumes polling on focus.
- No provider internals rendered.
- Loading, empty, error, populated.

## E · Gate

- [ ] R0 recorded; every PawaPay reference in a service and every in-transaction provider call classified `contradicted`
- [ ] One key → one intent and one provider call
- [ ] No service names PawaPay; a test double needs no service change
- [ ] Unmapped status → `PENDING` + metric, never `FAILED`
- [ ] Unroutable MSISDN creates no intent
- [ ] Intent commits before the provider call; no `@Transactional` reaches the port
- [ ] Never-answering provider escalates with the seat held and no refund attempted
- [ ] Outage leaves the reservation `HELD`
- [ ] Amount mismatch refuses, does not confirm, and alerts
- [ ] Public contract exposes no provider field; ticketing queries no admin-tagged field
- [ ] Waiting state explains itself and shows the hold countdown
- [ ] Escalated payments are **not** reported to the buyer as failures
- [ ] `mvn -q -f backend/booking-service test -Dgroups=ET-PAY-001 -DfailIfNoTests=true` green
- [ ] Spec `status:` → `implemented`
