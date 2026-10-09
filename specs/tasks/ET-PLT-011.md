# ET-PLT-011 · Rate limits, on-sale fairness and abuse control — tasks

> **Spec** [`specs/_platform/011-rate-limiting-and-abuse/spec.md`](../_platform/011-rate-limiting-and-abuse/spec.md) · **Wave 7** · `blocked_by:` ET-PLT-001, 005, 007, ET-IDN-001, ET-CAT-002, ET-TKT-001, ET-PLT-009
> **Screen** the queue state in `Ticketing - Discover & Checkout.dc.html` · blocks in `Admin - Transactions & System.dc.html`
> **Verify** `mvn -q -f backend test -Dgroups=ET-PLT-011 -DfailIfNoTests=false` · `compose-supergraph.sh --static`

8 requirements, **64 acceptance boxes** — tied with ADM-005 for the most in the corpus, because
this spec has to be fair at **5,000 reservations/minute against a single event** (**D-16**) and
honest to every buyer who does not get in.

## R0 · Reconcile

```bash
grep -rn 'RateLimiter\|RequestRateLimiter\|ratelimit' backend --include='*.java' --include='*.yml' | grep -v /src/test/
```

The gateway already declares a `RequestRateLimiter` with a Redis limiter. Classify it, and check
the two things that decide whether it is real:
- Is the limit **also enforced at the service**, or only at the gateway? Gateway-only means a
  service reachable inside the network is unlimited.
- Does the refusal carry an **honest `retryAfterSeconds`**, or a constant?

## A · Backend

### BE-1 · The gateway limiter — IP and coarse subject buckets
- **Spec** R1, R3, R7 · **§5** T1 · **depends** R0 · **parallel-safe** no
- Namespace `spring.cloud.gateway.server.webflux.*` ([`ET-PLT-001`](ET-PLT-001.md) BE-7).
- **Acceptance** each bucket refuses **independently** and **names its scope**. A refusal that does
  not say which limit was hit is a refusal the client cannot act on.

### BE-2 · The refusal shape — extension, header, honest `retryAfterSeconds`
- **Spec** R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** yes
- **Acceptance** **the header and the extension agree**; **a client honouring it is not refused
  twice**.
- That second clause is the honesty test. If `retryAfterSeconds` is a constant rather than the real
  window remaining, a well-behaved client waits exactly as told and is refused again — which trains
  clients to ignore the field and retry immediately, making the limiter's job harder.
- `RATE_LIMIT_EXCEEDED` is **retryable: true** in the [`ET-PLT-005`](ET-PLT-005.md) registry, so the
  UI offers a retry.

### BE-3 · Service-enforced domain limits, effective when the gateway is bypassed
- **Spec** R7 · **§5** T3 · **depends** BE-2 · **parallel-safe** yes *(one service per agent)*
- **Acceptance** **over-limit traffic sent directly to a service is still refused.**
- Defence in depth, the same reasoning as [`ET-PLT-001`](ET-PLT-001.md) BE-7's JWT rule: the gateway
  is not the security boundary.

### BE-4 · The three OTP axes
- **Spec** R6 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** **20 numbers from one IP refuse after 10, with no messages sent and no
  disclosure.**
- Three things at once: the limit holds, **no SMS or WhatsApp is paid for** past it, and the refusal
  does not reveal whether any number was registered ([`ET-IDN-001`](ET-IDN-001.md) BE-4). OTP abuse
  is the cheapest attack on this platform — every attempt costs the business money.

### BE-5 · The fairness queue — join, order, admit, pass, expiry
- **Spec** R4 · **§5** T5 · **depends** BE-3 · **parallel-safe** no
- **Acceptance** **a pass is unusable by another subject**; **reservation without one is refused**.
- A transferable pass is a market in queue positions. Bind it to the subject.

### BE-6 · Admission rate, the estimate, and the sell-out notification
- **Spec** R5 · **§5** T6 · **depends** BE-5 · **parallel-safe** no
- **Acceptance** **5,000 arrivals against 500 tickets preserve order and everybody learns their
  outcome.**
- The second half is the part that is usually skipped and matters most. 4,500 people will not get a
  ticket; telling them promptly is the difference between a queue and an outage. A queue that goes
  silent is indistinguishable from a broken site.

### BE-7 · Fail-open, its alert and the timeout
- **Spec** R8 · **§5** T7 · **depends** BE-2 · **parallel-safe** yes
- **Acceptance** **Redis stopped: requests are served and an alert fires.**
- The deliberate inversion of this corpus's usual fail-closed rule, and it is right here: rate
  limiting is availability protection, not authorization. Failing closed on a Redis blip takes the
  whole platform down to prevent abuse that may not be happening. **The alert is mandatory** — an
  unlimited platform nobody knows about is the actual danger.
- Contrast it explicitly with [`ET-IDN-003`](ET-IDN-003.md) BE-5, which fails **closed** for money
  operations. Both are correct; the difference is what the check protects.

### BE-8 · Temporary blocks, their expiry and their audit
- **Spec** R8 · **§5** T8 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** **every block expires**; **every block is audited** ([`ET-PLT-009`](ET-PLT-009.md)).
- No permanent blocks. A permanent block placed during an incident outlives the incident and the
  person who placed it, and nobody dares remove it.

### BE-9 · The subgraph halves; the buyer's queue view
- **Spec** R4, R8 · **§5** T9 · **depends** BE-6 · **parallel-safe** **no — two SDL files**
- **Acceptance** **`QueuePosition` never reports the queue's total length**; static composition green.
- Publishing "you are 4,300 of 12,000" for 500 tickets tells 11,500 people to leave, and tells
  scalpers exactly how many sessions to open.

## B · Contract

### GQL-1 · 4 queries, 5 mutations across booking and identity
- **depends** BE-9 · **parallel-safe** no *(two subgraphs — sequence)*

## C · Frontend

### FE-1 · The queue — `Ticketing - Discover & Checkout.dc.html`
- **depends** GQL-1, F0-1 · **parallel-safe** no
- Position and estimated wait; **never the total queue length** (BE-9).
- The estimate must be honest and must **update**. A frozen estimate is worse than none.
- Poll on a visibility-aware interval (**D-12**); the page must survive being backgrounded on a
  phone for ten minutes and resume correctly — the same trap as
  [`ET-TKT-001`](ET-TKT-001.md) FE-3's countdown.
- **testids** `queue-position`, `queue-estimate`, `queue-status`

### FE-2 · Admission and the pass
- **depends** BE-5 · **parallel-safe** no
- On admission the buyer proceeds to checkout with their pass. Pass expiry is visible — an expired
  pass returning them to the queue with no warning reads as the site losing their place.
- **testids** `queue-admitted`, `queue-pass-expiry`, `queue-rejoin`

### FE-3 · Sold out while queueing — the outcome everyone else gets
- **depends** BE-6 · **parallel-safe** no
- A designed screen: sold out, with what to do next — other events, notify-me. **Not** an error, and
  not silence.
- 4,500 of 5,000 arrivals see this screen. It deserves as much design attention as the checkout.
- **testids** `queue-soldout`, `queue-alternatives`, `queue-notify-me`

### FE-4 · Rate-limit refusals
- **depends** BE-2, [`ET-PLT-005`](ET-PLT-005.md) FE-1/FE-2 · **parallel-safe** yes
- `RATE_LIMIT_EXCEEDED` is retryable, so a retry is offered — **after the honest
  `retryAfterSeconds`**, counted down rather than asserted.
- The message says *slow down*, not *something went wrong*.
- **testids** `ratelimit-notice`, `ratelimit-retry-countdown`

### FE-5 · OTP throttling — `Login - Phone OTP & Admin MFA.dc.html`
- **depends** BE-4 · **parallel-safe** yes
- Cooldown and lockout shown as **time remaining** ([`ET-IDN-001`](ET-IDN-001.md) FE-3), with no
  hint about whether the number is registered.
- **testids** `otp-cooldown-remaining`, `otp-locked-until`

### FE-6 · Admin blocks — `Admin - Transactions & System.dc.html`
- **depends** BE-8 · **parallel-safe** yes
- Active blocks with scope, reason, **who placed it** and **when it expires**. Manual release.
- **There is no permanent-block control**, because there are no permanent blocks (BE-8).
- **testids** `block-row`, `block-scope`, `block-expires`, `block-release`, `block-audit-actor`

## D · Tests

### TS-1 · Buckets *(L3)* — each refuses independently and names its scope.

### TS-2 · Refusal honesty *(L3 — the one that decides whether clients cooperate)*
Header and extension agree. **A client that waits exactly `retryAfterSeconds` is not refused
again** — assert it, do not assume it.

### TS-3 · Bypass *(L3)* — over-limit traffic sent **directly to a service** is still refused.

### TS-4 · OTP *(L3, WireMock)*
20 numbers from one IP: refuse after 10, **zero messages sent** past the limit, no disclosure of
registration.

### TS-5 · Queue fairness *(L3 — the flagship test)*
**5,000 arrivals, 500 tickets**: order preserved, **every** arrival learns its outcome, passes are
subject-bound, reservation without a pass refused.

### TS-6 · Fail-open *(L3)*
**Stop the Redis container**: requests are served **and an alert fires**. Both halves — serving
without alerting is the failure.

### TS-7 · Blocks *(L3, frozen clock)* — every block expires; every block audited.

### TS-8 · Contract *(L4)* — `QueuePosition` exposes no total length in the composed schema.

### TS-9 · e2e *(L5, ticketing + admin)*
- Queue: position, moving estimate, admission, pass expiry, **sold-out screen**.
- **Background the tab for ten minutes and return**: position is correct.
- Rate-limit notice with a counted-down retry.
- OTP cooldown and lockout as time remaining, with no disclosure.
- Admin blocks with **no permanent option**.
- Loading, empty, error, populated.

## E · Gate

- [ ] R0 recorded; gateway-only enforcement classified `contradicted`
- [ ] Every bucket refuses independently and names its scope
- [ ] `retryAfterSeconds` is honest — a compliant client is not refused twice
- [ ] Direct-to-service over-limit traffic still refused
- [ ] OTP: refuse after 10, **no messages sent past the limit**, no disclosure
- [ ] 5,000 arrivals against 500 tickets: order preserved, **everyone learns their outcome**
- [ ] Passes are subject-bound; no reservation without one
- [ ] **Fail-open on Redis outage, with a mandatory alert** — and the contrast with ET-IDN-003's fail-closed recorded
- [ ] Every block expires and is audited; no permanent-block path exists
- [ ] `QueuePosition` never exposes total queue length
- [ ] Sold-out-while-queueing is a **designed screen**, not an error
- [ ] Queue survives ten minutes backgrounded on a phone
- [ ] `mvn -q -f backend verify -Dgroups=ET-PLT-011 -DfailIfNoTests=false` green
- [ ] Spec `status:` → `implemented` — **the corpus is complete when this is**
