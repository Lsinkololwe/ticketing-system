# ET-PLT-011 · Rate limits, on-sale fairness and abuse control

## 1. Capability

The platform has three adversaries and they need different answers. A **scraper** pulling
the whole catalogue costs money and gives a competitor a price list. A **bot** hitting a hot
on-sale at machine speed takes tickets from the people the event was for. An **attacker**
brute-forcing an OTP or replaying a webhook is trying to get in.

None of those is solved by one rate limit. A limit tight enough to stop a bot at on-sale
would refuse a family buying four tickets from one Wi-Fi network; a limit loose enough for
that family lets a bot take the front row.

This spec declares limits that fit their target. It declares **per-scope limits** — by IP,
by authenticated subject, by phone number, by organization — each with a bucket sized for
what it protects. It declares **on-sale fairness**, which is the harder problem: a queue for
events that need one, so that arrival order is preserved and the platform is not deciding
between refusing legitimate buyers and letting a script win.

And it declares what a refusal looks like: `RATE_LIMIT_EXCEEDED`, retryable, with
`retryAfterSeconds`, so a well-behaved client backs off rather than retrying into the wall.

## 2. Design decisions

**Limits are scoped to what they protect, not applied globally.** A discovery limit by IP, an
OTP limit by phone number, a reservation limit by subject, an API limit by organization. One
global limit either fails to protect the sensitive endpoints or throttles the ordinary ones.

**Authenticated subjects get their own bucket, and it is more generous than the IP bucket.**
A university, an office or a household behind one address is many legitimate users. Once a
request carries an identity, that identity is what is limited, and the IP bucket becomes a
floor for unauthenticated traffic only.

**Discovery is limited hard; the purchase path is limited lightly.** Scraping is what the
public read surface is abused for, and a scraper does not authenticate. Purchasing is
already bounded by inventory, purchase limits and idempotency, so limiting it aggressively
protects nothing and refuses buyers.

**On-sale fairness is a queue, and it exists only for events that need one.** An event
flagged for high demand puts arrivals in a token queue: a position, an estimated wait, and a
time-limited pass to the reservation endpoint. Everything else has no queue at all —
imposing one on every event makes ordinary buying worse to solve a problem ordinary events
do not have.

**The queue preserves arrival order and is not a lottery.** A person who arrived first goes
first. A random draw is defensible in principle and is impossible to explain to somebody who
was there at ten o'clock exactly.

**A queue pass is bound to a subject and expires.** Otherwise a script collects passes and
sells them, which is the same problem one layer up.

**OTP abuse is bounded on four axes: per contact, per IP, per device, per country (amended 2026-10-04, F-044; the numbers are [CONTRACT §7](../../identity/004-accounts-and-contacts/CONTRACT.md)).** A single contact's
cooldown ([ET-IDN-001](../../identity/001-phone-otp-identity/) R3) stops one victim being
spammed; the IP and device limits stop one attacker walking a list of numbers.

**Limits are enforced at the gateway where they can be, and in the service where they must
be.** IP and coarse subject limits are gateway concerns. Anything needing domain knowledge —
per-phone OTP, per-organization API, per-event queue — is enforced in the service that owns
the domain.

**A refusal is honest and actionable.** `RATE_LIMIT_EXCEEDED`, retryable, with
`retryAfterSeconds` and the scope that was exceeded. A client that cannot tell how long to
wait retries immediately, which is the behaviour the limit exists to stop.

**Rejected alternatives**

- *One global rate limit.* Either fails to protect the sensitive endpoints or throttles the ordinary ones.
- *IP-only limiting.* Refuses a whole office and lets one attacker with a proxy pool through.
- *A queue on every on-sale.* Makes ordinary buying worse to solve a problem ordinary events do not have.
- *A lottery instead of a queue.* Impossible to explain to somebody who arrived first.
- *Transferable or unbounded queue passes.* The same problem one layer up.
- *CAPTCHA on the purchase path.* Refuses the users least able to complete it, and bots solve them.
- *Silently dropping over-limit requests.* The client retries immediately, which is worse.
- *Rate limiting by API key only.* The platform has no API keys — it has users.

## 3. Requirements

### ET-PLT-011-R1 · Limits are scoped, and each scope has its own bucket

THE SYSTEM SHALL apply the §4 limits per scope, and a request SHALL be evaluated against
every scope that applies to it.

**Acceptance**
- [ ] Scopes are `IP`, `SUBJECT`, `PHONE`, `ORGANIZATION` and `EVENT`
- [ ] Every limit in §4 declares its scope, window, capacity and enforcement point
- [ ] A request is refused when **any** applicable bucket is exhausted, and the response names which
- [ ] An authenticated request is limited by `SUBJECT`, not by `IP`, except where §4 says both
- [ ] Buckets are Redis counters with a rolling window, keyed per §4
- [ ] Bucket state is not a business record — losing it fails open, not closed
- [ ] A test exhausts each bucket independently and asserts the refusal names the right scope

### ET-PLT-011-R2 · A refusal tells the client when to come back

WHEN a limit is exceeded, THE SYSTEM SHALL refuse with `RATE_LIMIT_EXCEEDED` carrying the
wait.

**Acceptance**
- [ ] The error carries `retryAfterSeconds`, the scope exceeded and `retryable: true` ([ET-PLT-005](../005-error-contract/) §4)
- [ ] The REST surface returns 429 with a `Retry-After` header
- [ ] `retryAfterSeconds` is the actual time until capacity, not a fixed number
- [ ] No refusal reveals another subject's usage or the platform's total capacity
- [ ] A client honouring `retryAfterSeconds` is never refused twice in a row for the same scope
- [ ] A test asserts the header and the extension agree

### ET-PLT-011-R3 · Discovery is limited hard and the purchase path lightly

THE SYSTEM SHALL apply the §4 asymmetry between read and purchase limits.

**Acceptance**
- [ ] Unauthenticated discovery is limited to `120/minute` per IP
- [ ] Search is limited more tightly, at `30/minute` per IP, and requires ≥ 3 characters ([ET-CAT-003](../../catalog/003-locations-and-reference-data/) R5)
- [ ] Authenticated discovery is `300/minute` per subject
- [ ] Reservation is `20/minute` per subject — generous, because inventory and purchase limits already bound it
- [ ] Payment initiation is `10/minute` per subject
- [ ] A test simulates a legitimate four-ticket purchase and asserts no limit is approached
- [ ] A test simulates a catalogue scrape and asserts it is refused within `catalog.discovery.max-depth` results
- [ ] The per-IP key of public GraphQL (`PublicGraphQlFilter`) is the client address resolved as in R6: `X-Forwarded-For` is read only when the connecting peer is in `platform.public-graphql.trusted-proxies` (addresses/CIDRs; default none, so the peer is the client), right-most untrusted entry wins, a non-IP entry falls back to the peer
- [ ] End to end, anonymous traffic keeps one bucket per visitor: the Next BFF forwards its resolved client IP (`TRUST_PROXY_HOPS`) as the only `X-Forwarded-For` (never the browser's own chain), the Apollo Router propagates `x-forwarded-for` to subgraphs, and the subgraph trusts the router's network
- [ ] Tests: `ClientAddressTest` (spoof from an untrusted peer ignored, right-most untrusted, malformed entry), `PublicGraphQlSecurityTest`/`AnonymousDiscoveryTest` (separate buckets per forwarded address), the BFF `upstream.test.ts` (forwarded header is the resolved IP only)

### ET-PLT-011-R4 · High-demand events use a fairness queue, and others do not

WHERE an event is flagged high-demand, THE SYSTEM SHALL queue arrivals in order and issue
time-limited passes.

**Acceptance**
- [ ] `highDemand` is a per-event flag set by an organizer or an admin before the sales window opens
- [ ] With it unset there is no queue and no pass — the reservation endpoint is reached directly
- [ ] With it set, a buyer joins a queue and receives a position and an estimated wait
- [ ] Order is arrival order, by server receipt time; there is no lottery and no reordering
- [ ] A pass admits `admin.queue.pass-ttl` (PT5M) of access to that event's reservation endpoint
- [ ] A pass is bound to the subject and the event and is unusable by anybody else
- [ ] An unused pass expires and the position is not restored — the buyer rejoins at the back
- [ ] Reservation without a valid pass for a high-demand event is refused

### ET-PLT-011-R5 · The queue admits at a rate the platform can serve

THE SYSTEM SHALL release passes at a configured rate and SHALL report the queue honestly.

**Acceptance**
- [ ] Passes are released at `admin.queue.admit-rate` (100/minute), configurable per event
- [ ] The estimated wait is computed from the position and the rate, and is stated as an estimate
- [ ] The queue's length and admit rate are visible to the organizer
- [ ] A buyer's position never goes backwards
- [ ] When the event sells out, everybody still queued is told immediately rather than admitted to a refusal
- [ ] Queue state is Redis with a TTL past the sales window; losing it drains the queue rather than blocking
- [ ] A test queues 5,000 arrivals against 500 tickets and asserts order is preserved and everybody learns their outcome

### ET-PLT-011-R6 · OTP abuse is bounded on four axes

THE SYSTEM SHALL limit OTP challenges per contact (phone or email), per IP, per device and per country.

**Acceptance**
- [ ] Per contact: the 60-second cooldown, the 5-attempt lock for 15 minutes ([ET-IDN-001](../../identity/001-phone-otp-identity/) R3) and at most `10` codes per day (`identity.limits.contact-codes-per-day`)
- [ ] Per IP: `10` codes per hour (`identity.limits.ip-codes-per-hour`)
- [ ] Per device: `5` distinct contacts per day (`identity.limits.device-distinct-contacts-per-day`)
- [ ] Per country: a configurable daily ceiling (`identity.limits.country-codes-per-day`, default `2000`) to bound toll-fraud style pumping; only countries in `identity.limits.allowed-countries` are accepted at all
- [ ] Exceeding any limit refuses with `OTP_RATE_LIMITED` carrying `retryAfterSeconds`, and sends no message
- [ ] **Trusted proxy IP**: the client IP is the right-most address in `X-Forwarded-For` that is not one of the configured trusted proxies; a caller-supplied header from an untrusted hop is ignored, and the buyer app forwards the browser's IP to identity-service in the request body field `clientIp` only over the authenticated service-to-service call
- [ ] Counters live in Redis as `lim:{scope}:{id}:{window}` where `{id}` is a keyed hash for contact and device scopes, never a raw contact
- [ ] A refusal does not disclose whether the number is registered ([ET-IDN-001](../../identity/001-phone-otp-identity/) R4)
- [ ] The three limits are independent — exhausting one does not affect another's counter
- [ ] A test walks 20 contacts (a mix of phone and email) from one IP and asserts refusal after 10, with no messages sent
- [ ] A test sends `X-Forwarded-For: <other>` from an untrusted hop and asserts the counter keys on the trusted address

### ET-PLT-011-R7 · Limits are enforced where the knowledge is

THE SYSTEM SHALL enforce coarse limits at the gateway and domain limits in the owning
service.

**Acceptance**
- [ ] `IP` and coarse `SUBJECT` limits are enforced by the gateway's Redis rate limiter ([ET-PLT-001](../001-runtime-baseline/) R7)
- [ ] `PHONE`, `ORGANIZATION` and `EVENT` limits are enforced in the owning service
- [ ] A service enforces its own limits even when reached directly, bypassing the gateway
- [ ] Limits and their configuration live with their enforcement point
- [ ] A test sends over-limit traffic directly to a service and asserts it is still refused
- [ ] Rate-limit refusals are a metric labelled by scope, and a spike alerts ([ET-ADM-005](../../admin/005-observability-and-health/))

### ET-PLT-011-R8 · Limits fail open, and abuse is observable

IF the limiting infrastructure is unavailable, THEN THE SYSTEM SHALL serve the request and
SHALL alert.

**Acceptance**
- [ ] Redis unavailable causes requests to be served, not refused — a rate limiter is not a security boundary
- [ ] Failing open raises an alert immediately
- [ ] Every refusal is counted by scope, endpoint and subject class
- [ ] A rising refusal rate on one scope alerts, because it is the signal of an attack in progress
- [ ] An operator can see the top limited subjects and IPs over a window
- [ ] An operator can apply a temporary block on a subject or IP, with a reason, audited, and time-limited
- [ ] A test stops Redis and asserts requests are served and an alert fires

## 4. Model

### Limit registry

| # | Scope | Endpoint or action | Capacity | Window | Enforced at |
|---|---|---|---|---|---|
| 1 | `IP` | unauthenticated discovery | 120 | 1 min | gateway |
| 2 | `IP` | search | 30 | 1 min | gateway |
| 3 | `IP` | any unauthenticated request | 300 | 1 min | gateway |
| 4 | `SUBJECT` | authenticated discovery | 300 | 1 min | gateway |
| 5 | `SUBJECT` | any authenticated request | 600 | 1 min | gateway |
| 6 | `SUBJECT` | `reserveTickets` | 20 | 1 min | booking |
| 7 | `SUBJECT` | `initiatePayment` | 10 | 1 min | booking |
| 8 | `SUBJECT` | `requestRefund` | 5 | 1 h | booking |
| 9 | `SUBJECT` | `initiateTransfer` | 20 | 1 h | booking |
| 10 | `CONTACT` (phone or email) | OTP request | 1 per 60 s; 10 per day; 5 tries then 15 min lock | rolling / 1 d | identity |
| 11 | `IP` | OTP request | 10 | 1 h | identity |
| 12 | `DEVICE` | distinct contacts in an OTP request | 5 | 1 d | identity |
| 12a | `COUNTRY` | OTP request | 2000 (configurable) | 1 d | identity |
| 13 | `SUBJECT` | `claimTransfer` | 10 | 1 h | booking |
| 14 | `ORGANIZATION` | `createEvent` | 50 | 1 d | catalog |
| 15 | `ORGANIZATION` | `inviteTeamMember` | 100 | 1 d | identity |
| 16 | `SUBJECT` | `requestDataExport` | 1 | 1 d | identity |
| 17 | `IP` | webhook endpoint | 1,000 | 1 min | booking |
| 18 | `EVENT` | queue admission | 100 | 1 min | booking |

Eighteen limits. Rows 6 and 7 are deliberately generous: inventory
([ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) R2), purchase limits
([ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) R5) and idempotency
([ET-PLT-007](../007-security-and-authorization/) R6) already bound the purchase path, and a
tight limit there refuses families rather than bots.

### Redis keys

| Key | TTL | Scope |
|---|---|---|
| `ratelimit:ip:{ip}:{bucket}` | window | 1–3, 11, 17 |
| `ratelimit:sub:{userId}:{bucket}` | window | 4–9, 13, 16 |
| `ratelimit:phone:{phone}` | 60 s | 10 |
| `ratelimit:device:{deviceId}` | 1 d | 12 |
| `ratelimit:org:{orgId}:{bucket}` | window | 14, 15 |
| `queue:{eventId}` | sales window + 1 h | the fairness queue |
| `queue:pos:{eventId}:{userId}` | sales window + 1 h | a buyer's position |
| `queue:pass:{eventId}:{userId}` | 5 min | an issued pass |
| `block:{scope}:{value}` | operator-set | R8's temporary block |

None is a business record. Losing any of them fails open (R8).

### The fairness queue

```
join           arrival appended to queue:{eventId}, position returned
               → queue:pos:{eventId}:{userId}
poll           position + estimated wait = position ÷ admitRate
admit          the event's QueueAdmissionWorkflow releases admitRate passes a minute, by timer, in order
               → queue:pass:{eventId}:{userId}, TTL 5 min
reserve        the reservation endpoint requires a valid pass for a high-demand event
expire         an unused pass lapses; the buyer rejoins at the BACK
sell out       every remaining position is told immediately, not admitted to a refusal
```

The pass is bound to `{eventId, userId}` and is checked at reservation. It is not a token
the client holds and could pass on.

A queue exists **only** when `event.highDemand` is set before the sales window opens.
Everything else reaches the reservation endpoint directly.

### The estimate, stated honestly

```
estimatedWaitSeconds = ceil(position ÷ admitRate) × 60
```

Reported as an estimate, and it moves only downward — R5 forbids a position going backwards,
because a buyer watching their position increase assumes they have been cheated.

### The block

An operator may block a subject or an IP temporarily:

`identity_temporary_blocks`

| Field | Notes |
|---|---|
| `_id`, `scope`, `value` | `SUBJECT` or `IP` |
| `reason` | required, ≥ 20 characters |
| `blockedById`, `blockedAt`, `expiresAt` | **always time-limited** |
| `status` | `ACTIVE`, `EXPIRED`, `LIFTED` |

Every block is audited (`ACTOR_BLOCKED` is added to
[ET-PLT-009](../009-audit-trail/) §4 by this spec) and expires — a permanent block is a
ban, which is a product decision this spec does not make.

### Failing open

| Condition | Behaviour |
|---|---|
| Redis unavailable | **serve** the request, alert immediately |
| bucket key missing | treat as empty, serve |
| Redis slow past `ratelimit.timeout` (PT50MS) | serve, count a timeout |

A rate limiter is a fairness and cost control, not a security boundary. The security
boundaries are authentication ([ET-PLT-007](../007-security-and-authorization/)), the OTP
attempt cap ([ET-IDN-001](../../identity/001-phone-otp-identity/) R3) and webhook signatures
([ET-PAY-002](../../payment/002-webhooks-and-settlement/) R1) — none of which fails open.

### GraphQL

Subgraph `booking` for the queue, `identity` for blocks.

| Operation | Kind | `@auth` | Returns |
|---|---|---|---|
| `queuePosition(eventId)` | query | `AUTHENTICATED` | `QueuePosition` |
| `joinQueue(eventId)` | mutation | `CUSTOMER` | `QueuePosition!` |
| `leaveQueue(eventId)` | mutation | `CUSTOMER` | `Boolean!` |
| `eventQueueStatus(eventId)` | query | `ORGANIZER` | `QueueStatus!` |
| `setEventHighDemand(eventId, enabled, admitRate)` | mutation | `ORGANIZER` | `Event!` |
| `rateLimitStatus(scope, value)` | query | `ADMIN` | `RateLimitStatus!` `@tag(name: "admin")` |
| `topLimitedSubjects(window, limit)` | query | `ADMIN` | `[LimitedSubject!]!` `@tag(name: "admin")` |
| `blockActor(input)` | mutation | `ADMIN` | `TemporaryBlock!` `@tag(name: "admin")` |
| `liftBlock(id, reason)` | mutation | `ADMIN` | `TemporaryBlock!` `@tag(name: "admin")` |

`QueuePosition` carries `position`, `estimatedWaitSeconds`, `admitted` and `passExpiresAt`.
It never reports the queue's total length to a buyer — a number that only makes waiting
worse.

### Configuration

| Property | Value |
|---|---|
| `ratelimit.enabled` | `true` |
| `ratelimit.timeout` | `PT50MS` |
| `ratelimit.fail-open` | `true` — not configurable to `false` |
| `admin.queue.admit-rate` | 100/minute, per event |
| `admin.queue.pass-ttl` | `PT5M` |
| `admin.queue.max-size` | 50,000 per event |
| `admin.block.max-duration` | `P7D` |

The eighteen capacities of §4 are runtime-configurable only through
[ET-ADM-002](../../admin/002-platform-configuration/) if added to its registry; today they
are YAML.

### Error codes

`RATE_LIMIT_EXCEEDED` — a row of
[ET-PLT-005 §4](../005-error-contract/) introduced by this spec, `UNAVAILABLE`, retryable.

## 5. Tasks

- [ ] **T1 · The gateway limiter: IP and coarse subject buckets**
  - requirements: R1, R3, R7
  - files: `backend/api-gateway/src/main/resources/application.yml`, `.../config/`
  - verify: each bucket refuses independently and names its scope
  - parallel-safe: no
  - depends: —

- [ ] **T2 · The refusal shape: extension, header, honest `retryAfterSeconds`**
  - requirements: R2
  - files: `backend/shared-library/.../ratelimit/`
  - verify: the header and the extension agree; a client honouring it is not refused twice
  - parallel-safe: yes
  - depends: T1

- [ ] **T3 · Service-enforced domain limits, effective when the gateway is bypassed**
  - requirements: R7
  - files: `backend/*/src/main/java/com/pml/*/config/RateLimitConfig.java`
  - verify: over-limit traffic sent directly to a service is still refused
  - parallel-safe: yes — one service per agent
  - depends: T2

- [ ] **T4 · The four OTP axes (contact, IP, device, country) and the trusted proxy IP**
  - requirements: R6
  - files: `backend/identity-service/.../auth/limits/` (see ET-IDN-001 T3)
  - verify: 20 numbers from one IP refuse after 10, with no messages sent, no disclosure
  - parallel-safe: yes
  - depends: T3

- [ ] **T5 · The fairness queue: join, order, admit, pass, expiry**
  - requirements: R4
  - files: `backend/booking-service/.../service/impl/EventQueueService.java`
  - verify: a pass is unusable by another subject; reservation without one is refused
  - parallel-safe: no
  - depends: T3

- [ ] **T6 · Admission rate, the estimate, and the sell-out notification**
  - requirements: R5
  - files: `backend/booking-service/.../workflow/queue/QueueAdmissionWorkflowImpl.java`
  - verify: 5,000 arrivals against 500 tickets preserve order and everybody learns their outcome
  - parallel-safe: no
  - depends: T5

- [ ] **T7 · Fail-open, its alert and the timeout**
  - requirements: R8
  - files: `backend/shared-library/.../ratelimit/`
  - verify: Redis stopped, requests are served and an alert fires
  - parallel-safe: yes
  - depends: T2

- [ ] **T8 · Temporary blocks, their expiry and their audit**
  - requirements: R8
  - files: `backend/identity-service/.../service/impl/BlockServiceImpl.java`
  - verify: every block expires; every block is audited
  - parallel-safe: yes
  - depends: T3

- [ ] **T9 · The subgraph halves; the buyer's queue view**
  - requirements: R4, R8
  - files: `backend/booking-service/.../schema.graphqls`, `backend/identity-service/.../schema.graphqls`
  - verify: `QueuePosition` never reports the queue's total length; `compose-supergraph.sh --static`
  - parallel-safe: no — two SDL files
  - depends: T6

## 6. Out of scope

| Capability | Spec |
|---|---|
| Authentication, which is the real security boundary | [ET-PLT-007](../007-security-and-authorization/) |
| The OTP cooldown and attempt cap this spec extends | [ET-IDN-001](../../identity/001-phone-otp-identity/) |
| Webhook signature verification | [ET-PAY-002](../../payment/002-webhooks-and-settlement/) |
| Inventory and purchase limits, which bound the purchase path | [ET-CAT-002](../../catalog/002-ticket-tiers-and-inventory/) |
| Discovery paging and depth caps | [ET-CAT-003](../../catalog/003-locations-and-reference-data/) |
| Alert routing for refusal spikes | [ET-ADM-005](../../admin/005-observability-and-health/) |
| Permanent bans, which are a product decision | not specified in this corpus |

Deliberately never in scope: **one global rate limit** (it either fails to protect or
throttles the ordinary), **a queue on every on-sale** (it makes ordinary buying worse to
solve a problem ordinary events do not have), **a lottery instead of a queue** (impossible
to explain to somebody who arrived first), and **failing closed when Redis is down** (a rate
limiter is not a security boundary).
