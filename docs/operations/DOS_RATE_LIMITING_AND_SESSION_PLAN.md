# Denial-of-service protection, rate limiting and Redis: analysis and plan

Written 2026-10-09 after a full-stack run (three apps, gateway, router, three services, Keycloak, Redis).
Everything marked **verified** was read in the code or reproduced in the running stack. Production
deployment values (proxy hop counts, router file in use, Redis topology) are not in the repository and are
marked **to confirm**.

Contents: 1 · The organizer sign-in loop (fixed) · 2 · What protects the platform today · 3 · Gaps ·
4 · Design principles · 5 · The plan, in work packages · 6 · `__typename` · 7 · Rollout and proof

---

## 1 · The organizer sign-in loop: cause and fix

**Symptom.** In the organizer app, "Sign in" and "Apply to become an organizer" returned to the landing page.

**Cause (reproduced, verified).** Keycloak keeps one SSO session per browser. Its id (`sid`) is shared by every
app signed in through it, here the buyer and organizer apps. Signing out of one app revoked that shared `sid` at
identity-service but left the Keycloak SSO session alive. The next sign-in came back through SSO carrying the
same, now revoked, `sid`; the first API call answered `TOKEN_REVOKED`; the app signed the person out again; and
because that path destroyed the app session first, the follow-up logout had nothing to work with and also never
ended the SSO session. Repeat until the SSO session expired.

Evidence: the Keycloak session list after an organizer sign-out still showed the session (with the buyer client
in it); `identity_token_revocations` held `SESSION:<same sid>`; the callback URL's `session_state` was that same
id on every retry.

**Fix (built, tested).** `libs/shared/src/auth/bff/sso.ts` adds `endSso`, called from two places:
- sign-out now ends the whole Keycloak session from the server (`endSessionServerSide`, POST with
  `id_token_hint`) before revoking the refresh token, instead of depending on the browser following a redirect;
  the redirect through Keycloak remains only as a fallback;
- the revoked-token path (`upstream.ts`) ends the Keycloak session before it discards the app session, which also
  heals anyone already stuck: one bounce, then sign-in works.

Tests: unit (handlers, upstream) and real Keycloak 26.5.2 integration for the shared library, the admin app and
the organizer app; each fails if the fix is removed. Verified in Chrome: stuck state healed in one bounce;
sign-out logs `sso: ended`; Keycloak holds no session; "Sign in" reaches the sign-in page.

**Still to do:** the organizer integration test for sign-out then sign-in did not fail under mutation (the
one-client Keycloak session in that test ends on its own); a faithful two-app reproduction belongs in the
Playwright suite (WP12).

---

## 2 · What protects the platform today (verified unless noted)

| Layer | Control | Numbers | Redis failure mode |
|---|---|---|---|
| Next.js BFF (all 3 apps) | Redis sliding-window limiter, per policy (`ratelimit.ts`) | start ip 20/min; callback ip 30/min, failures 5 per 10 min then 15 min block; OTP send ip 10/min + 30/h, contact 5 per 10 min + 10/day, device 5 per 10 min; verify ip 15/min, flow 8 per 5 min; authenticated proxy 600/min per session; anonymous proxy 120/min per ip | auth policies **fail closed** (5/min per subject in memory, then 503); proxy policies **fail open** to a per-instance in-memory limiter |
| BFF proxy | 1 MiB body cap (declared and streamed), 15 s timeout, same-origin check, client `X-Forwarded-For` never copied | | n/a |
| Gateway | URI 8 KiB, header 8 KiB, body 1 MiB GraphQL / 50 MiB upload / 10 MiB other (by `Content-Length`); pool 1000, acquire 1 s, response 30 s; circuit breakers; brute-force filter on token/login paths | 5 requests / 5 min per IP, lockout 15 min up to 24 h | brute-force **fails open**; session blacklist **fails open** |
| Apollo Router | production file: introspection off, `max_depth 15`, `max_height 200`, `max_aliases 30`, 30 s timeout | | n/a |
| Subgraphs (identity, catalog) | anonymous GraphQL: single allowlisted query, 16 KiB, depth 10, 300 nodes, 120/min per client address, introspection refused | | **fails open** |
| All services | authenticated GraphQL depth 10, complexity 200; page size capped at 100 | | n/a |
| Identity OTP | atomic Lua over contact/IP/device/country: 10 per contact per day, 10 per IP per hour, 5 contacts per device per day, 2000 per country per day; code TTL 5 min, 5 attempts, 15 min lock mirrored to Mongo | | **fails closed** |
| Booking / catalog | `ActionRateLimiter` (messaging, resend, transfer), media upload 60/h per organization, 5 MiB per image | | closed |
| Keycloak | brute-force protection on, 5 failures, temporary lockout up to 15 min, no permanent lockout | | n/a |

Redis key families: `bff:{app}:sess|sid|sub:*`, `rl:{policy}:{subject}:*`, `rlblock:*`, gateway
`bruteforce:*`, `lockout:*`, `pml:blacklist|session|revoked:*`, services `rl:*`, `lim:*`, challenge keys.

---

## 3 · Gaps, ranked

### High

**G1 · One visitor's traffic is every visitor's traffic.** `TRUST_PROXY_HOPS` defaults to 0 and every app
env file and `docker-resources/local-e2e/apps.sh` sets 0. `resolveClientIp` then returns the string `unknown` for everyone
(verified), so every per-IP rule is one global bucket: OTP send 10/min, verify 15/min, start 20/min, anonymous API
120/min. One script can lock every buyer out of sign-in and browsing. The same `unknown` is sent to identity as the
OTP client address (the 10/h IP cap becomes global), and no `x-forwarded-for` goes downstream, so the public-GraphQL
limiter keys on the peer address, again one bucket. *Production value to confirm.*

**G2 · The gateway brute-force filter is spoofable and mis-targeted.** It takes the *first* `X-Forwarded-For`
element (verified), so a rotating forged header gives unlimited buckets; it counts all requests rather than
failures; it is per IP, not per account; and some of its paths (`/api/auth/login`, `/api/auth/otp/verify`) do not
exist on the gateway. Keycloak's own per-user lockout is the real account defence.

**G3 · Cheap-to-send, costly-to-answer GraphQL.** The anonymous limiter counts one request per POST but allows up
to ~300 nodes: many aliased copies of `discoverEvents` (100 items each) cost one count. The router's alias and
depth limits exist only in the production file; the local and e2e routers have no limits, introspection on. The
file says the `limits` block needs an enterprise licence: **to confirm** whether the router honours it and whether
that file is what is deployed.

**G4 · No throttling of `/graphql` at the gateway or router at all.** `/graphql/**` is `permitAll`; the only
gateway rate limiter is on the payment webhook route. The BFF's 600/min per session is bypassed by calling the
gateway or router directly (port 4001 is published in local setups).

**G5 · Body-size check relies on `Content-Length`.** A chunked request (length −1) skips the check
(`RequestSizeLimitFilter`, verified).

### Medium

**G6 · OTP-send controls can be sidestepped at the BFF.** The contact counter keys on the raw string (`+26097…`
and `097…` count separately); the device cookie is client-controlled and rotates freely. Identity's normalised
10/day per contact and 2000/day per country remain, so cost is bounded, but still tens of thousands of messages a
month.

**G7 · Fail-open on Redis loss** for the public GraphQL limiter, gateway brute force, the blacklist (a revoked
token keeps working) and the proxy limiters. Degrading Redis disables most protections.

**G8 · Sessions and limiter share Redis.** A limiter flood (many unique keys) and eviction pressure can displace
sessions; identity already treats `allkeys-lru` as an untrusted-cache condition.

**G9 · Webhook limiter keys on the peer address** (the load balancer in production): one shared bucket.

**G10 · Introspection** is on in the local router and identity's DGS configuration; no per-request timeout in the
subgraph services beyond Redis 2 s and booking's 30 s read timeout; service-level body limits unverified.

### Low

BFF `ensure`/`challenge` bodies are read without a size cap on those routes; the in-memory fallback limiter is
per instance, so its effective limit scales with replicas; Spring Security TRACE logging is on by default in the
gateway (log amplification under attack); the staff realm lacks `maxDeltaTimeSeconds` (a known username can be
delayed repeatedly, up to 15 minutes at a time).

---

## 4 · Design principles

1. **Identify the caller correctly before counting anything.** A limiter keyed on a wrong identity is worse than
   none: it blocks everyone or no one. One shared definition of "client address" (right-to-left over a configured
   number of trusted proxies) used by BFF, gateway and services.
2. **Limit at the edge, again at the app, and a third time at the expensive operation.** Defence in depth, each
   layer keyed on what it knows best: edge on address, BFF on session and flow, service on account, contact and
   resource.
3. **Charge by cost, not by request.** A GraphQL request is not a unit of work. Count complexity or root fields.
4. **Fail by risk class, never by default.** Anything that spends money or can lock people out (OTP, payment,
   sign-in) fails closed with a small local allowance; public read fails open to a stricter local limit, never to
   unlimited.
5. **Redis is a cache and a coordinator, not an authority** (the platform's existing rule). Limits must still hold
   approximately if it is flushed; business state never lives there.
6. **Every refusal is observable and honest:** `429` with `Retry-After`, a counter by policy and outcome, no
   personal data in keys (hashed subjects, as the BFF already does).

---

## 5 · The plan

Ordered by risk reduced per unit of effort. Each package lists what changes, how Redis is used, and the proof.

### WP1 · Correct client identity everywhere (fixes G1, part of G2, G9) · small, do first
- BFF: require `TRUST_PROXY_HOPS` explicitly in production (startup refuses to boot without it); document the
  value per environment; when the address truly cannot be resolved, key on the socket peer plus a stricter policy,
  never a shared `unknown` bucket.
- Gateway: replace the first-element parse in `BruteForceProtectionFilter` and the webhook key resolver with the
  shared trusted-proxy logic that `PublicGraphQlFilter` already implements (`TrustedProxies`, right to left,
  IP-literal check); one library class used by all.
- Forward the resolved address downstream (the BFF already does) and have services honour it only from trusted peers.
- Proof: unit tests for spoofed, missing, multi-hop and IPv6 headers; an integration test in which two clients
  behind one proxy get separate buckets.

### WP2 · A real edge limiter on `/graphql` (G4, part of G3) · medium
- Gateway: Spring Cloud Gateway `RedisRateLimiter` with a custom key resolver and a **cost-aware token bucket** Lua
  script (atomic, one round trip). Keys `rl:edge:{class}:{subjectHash}` with TTL = refill time. Classes: public read,
  authenticated read, mutation, expensive search, OTP-adjacent. Subject = address for anonymous, `sub` for
  authenticated, both for mutations.
- Cost = root fields × (1 + aliases) plus a surcharge for known list queries; reject at the edge before the router.
- Emit `RateLimit-Limit/Remaining/Reset` and `Retry-After` on 429.
- Proof: Testcontainers Redis; a k6 scenario (aliased `discoverEvents`, 1000 requests) that must be throttled; a
  Redis-down test showing the fallback limit is applied.

### WP3 · GraphQL request limits that actually ship (G3, G10) · small
- Router: `limits` (`max_depth`, `max_height`, `max_aliases`, `max_root_fields`, `http_max_request_bytes`,
  `parser_max_tokens`) in **every** router file, including local and e2e, so developers see the same refusals as
  production; introspection and sandbox off outside local. Confirm the licence question in G3 with a boot test.
- Subgraph public filter: count **root fields and aliases** (not only nodes), cap `first` per root field, refuse
  more than a handful of root fields per anonymous request.
- Optional, higher assurance: because the three apps are first party, the BFF can enforce an **operation
  allowlist** (operation name plus hash) so the proxy never forwards an arbitrary query; keep the existing
  anonymous allowlist for the public surface.
- Proof: tests that an aliased flood and a deep query are refused at the router and at the subgraph.

### WP4 · Request bodies and slow clients (G5, Low items) · small
- Gateway: cap the streamed body as it is read (not only `Content-Length`) with a codec/limit that applies to
  chunked requests; header-read and idle timeouts on the Netty server; keep upload paths on their own, smaller,
  rate-limited route.
- BFF routes (`ensure`, `challenge`, contacts): read through the same bounded reader the proxy uses.
- Proof: tests with a chunked body larger than the cap, and a slow-header client.

### WP5 · Rework the gateway brute-force filter (G2) · small
- Count **failures** (responses 401/403 from the token and login-action paths), keyed on address **and** username
  hash; reset on success; correct the path list to what the gateway really routes; remove the dead paths. Keep
  Keycloak's per-user lockout as the account-level control and add `maxDeltaTimeSeconds` to the staff realm.
- Fail closed to a conservative local limit when Redis is down (sign-in is a closed-class path).

### WP6 · Harden OTP and contact abuse (G6) · medium
- Normalise before counting: the BFF asks the same normaliser identity uses (E.164 for phones, lower-cased
  canonical email) and keys counters on the normalised contact hash.
- Replace the client-controlled device cookie by a server-issued, signed, rate-limited device token; add a cheap
  proof-of-work or a CAPTCHA step-up (Turnstile or hCaptcha) after a threshold of sends per address or ASN.
- Global spend guard: alert and automatically tighten when daily OTP sends cross 70 percent of the budget.
- Proof: tests that formatting variants of one contact share a counter; load test of rotating cookies.

### WP7 · Redis layout, durability and failure modes (G7, G8) · medium
- Separate concerns: sessions and revocation on one Redis (or logical database with `noeviction` and its own memory
  budget), rate-limit counters on another with `volatile-ttl` eviction, so a counter flood cannot evict sessions.
- A single `failMode` per policy declared in code and lint-tested (`CLOSED_LOCAL`, `OPEN_STRICT_LOCAL`), replacing
  today's mixture; a circuit breaker with a 2 s timeout so a slow Redis cannot stall requests.
- Key hygiene: hashed subjects, TTL on every key (already enforced by a flush test), cluster-safe hash tags if
  Redis moves to cluster mode.
- Proof: extend the existing `RedisFlushTest` family: flush, outage and slow-Redis cases for each class.

### WP8 · Protect the expensive work behind the API (G10) · medium
- `maxTimeMS` on public discovery queries; bulkheads (Resilience4j) around search and export resolvers; bounded
  Temporal concurrency per task queue (already set for identity); Mongo and Redis pool ceilings reviewed.
- Per-request timeouts in the Spring services so a slow dependency cannot hold threads past the gateway timeout.

### WP9 · Observability and alerting · small
- `rate_limit_decisions_total{layer,policy,outcome}`, `rate_limit_redis_degraded{layer}`, OTP sends per day,
  429 ratio, router rejections by reason. Alerts: sustained 429s from one subject, Redis fallback active more than
  60 s, OTP budget over 70 percent, public GraphQL requests per address.
- Turn the gateway's default Spring Security TRACE logging off; log refusals at INFO with hashed subject.

### WP10 · Edge, outside the code (confirm with whoever operates production)
- A managed edge (CDN or WAF: Cloudflare, Azure Front Door) in front of the three apps and the gateway: volumetric
  and L7 DDoS absorption, bot management on the sign-in and OTP routes, geo and ASN rules, TLS and HTTP/2 limits.
  The application limits above are the second line, not the first.
- Only the edge may reach the gateway and router (network policy); the router and services are never published.

### WP11 · Session and sign-out hygiene (follows from section 1)
- Keep sign-out ending the Keycloak session (done). Add a periodic reconciliation that logs any user session whose
  `sid` is revoked at identity but still present in Keycloak, as an alert.
- Decide whether a sign-out in one app should end the SSO session for all apps (current behaviour, safe) or only
  that app (needs per-client revocation; not recommended).

### WP12 · Proof that stays: tests and drills
- Playwright (real Keycloak): sign in to buyer and organizer, sign out of one, sign in again in the other and in the
  same one; the exact loop regression.
- k6 load profiles in CI against a compose stack: anonymous browse, aliased query flood, OTP send flood, spoofed
  `X-Forwarded-For`; pass criteria are the refusals above and p95 latency for a well-behaved user staying flat.
- Game day: stop Redis for 5 minutes in staging; expected result is documented per class.

### Suggested order and size
WP1 → WP5 → WP3 → WP2 → WP6 → WP7 → WP4 → WP8 → WP9 → WP12, with WP10 agreed in parallel. WP1 to WP5 are each a
few days; WP2, WP6, WP7 about a week each. WP1 removes the highest-risk gap and should ship alone first.

OWASP mapping: API4:2023 unrestricted resource consumption (WP2, 3, 4, 8), API2 broken authentication (WP5, 6),
API6 unrestricted access to sensitive business flows (WP6), API8 security misconfiguration (WP1, 3, 7),
A05 and A07 from the Top 10 (misconfiguration, identification and authentication failures).

---

## 6 · Is `__typename` safe in GraphQL responses?

Yes. It is a built-in meta field of the GraphQL specification that returns the name of the object type. The
response you pasted (`"__typename": "User"`) was added by the client, not by the server's own choice: Apollo Client
adds `__typename` to every selection set automatically so it can normalise its cache by type and id. Federation
also requires it: the router and subgraphs use `__typename` in `_entities` to resolve an entity across services.
Removing it would break the cache and federation.

**Risk.** It discloses the type name only, which is already in the schema. It matters only if the schema itself
is meant to be secret, and the real defence for that is not hiding names but:

| Production recommendation | Why |
|---|---|
| Introspection off in production (router and every subgraph); sandbox off | stops enumeration of every type and field; `__typename` alone reveals nothing else |
| Field suggestions off ("Did you mean ...") | otherwise errors leak valid field names even with introspection off |
| Anonymous allowlist stays (already built) | unauthenticated callers can only reach named, public operations |
| Authorisation on every field, not "by obscurity" | an attacker who guesses a type name must still be refused (this is what the `@auth` and `@PreAuthorize` lint tests enforce) |
| Masked errors, no stack traces or internal class names | already done by the platform's error contract |
| Persisted or allow-listed operations for first-party apps | an unknown query never reaches the graph |
| Do not name types after internal systems or customers | type names are visible in responses |

Do not strip `__typename` from responses; do keep naming neutral and disable introspection and suggestions.
Note the local router has introspection **on** (by design for developers); WP3 makes the difference explicit.

---

## 7 · Rollout and proof

- Ship WP1 behind a startup check, in staging first, with a canary on one app (buyer) and a rollback flag per
  policy. Measure the 429 rate for a week with limits in **report-only** mode before enforcing.
- Enforce in this order: edge limiter on mutations, OTP, anonymous reads, authenticated reads.
- Keep every limit configurable per environment (`rl.<layer>.<policy>.limit|window|failMode`) and committed as a
  table in this document so a change is a reviewed diff.
- Definition of done for the whole plan: the k6 profiles and the game-day results are attached to the spec's gate,
  and the "one visitor locks everyone out" test (WP1) passes in CI.
