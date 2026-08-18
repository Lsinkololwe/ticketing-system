# ET-IDN-003 · Token revocation — tasks

> **Spec** [`specs/identity/003-token-revocation/spec.md`](../identity/003-token-revocation/spec.md) · **Wave 1** · `blocked_by:` ET-PLT-002, ET-PLT-003, ET-PLT-005, ET-PLT-007, ET-IDN-001
> **Screens** — no screen of its own. It surfaces as **sign-out everywhere** in all three apps and as a session list in settings.
> **Verify** `mvn -q -f backend/identity-service test` · `mvn -q -f backend/shared-library test` · `mvn -q -f backend verify`

Cutting a live session before its token expires. A JWT is valid until it is not, which means
"remove this person's access" is a lie unless something checks a revocation list on every request.
This spec makes that check **fail closed** — and 8 requirements, 58 acceptance boxes, is the price
of doing it without adding a database round-trip to every request.

## R0 · Reconcile *(do this first)*

`docs/BACKCHANNEL_LOGOUT.md` exists and the recent commit *"improved the revocation services"*
touched this area. Read both before classifying.

Check the two things that decide whether this is real:
- Is the revocation store **durable** (MongoDB), or Redis-only? Redis-only is `contradicted` —
  a flush restores access to a revoked token.
- Does the check **fail closed** on store outage for sensitive operations, or fail open?

## A · Backend

### BE-1 · The document, its three indexes, the durable store
- **Spec** R1, R8 · **§5** T1 · **depends** R0 · **parallel-safe** no *(everything else reads this)*
- Three identifier types: `jti` (one token), `sid` (one session), `sub` (every token for a user).
- **Acceptance** the unique index refuses a duplicate identifier; the TTL index removes an expired
  row. Confirm both **live**, with MongoDB MCP `collection-indexes` — a TTL index that was never
  created grows this collection forever and nothing fails until it does.

### BE-2 · The revocation check — **one** lookup over all three identifiers
- **Spec** R1, R2 · **§5** T2 · **depends** BE-1 · **parallel-safe** no *(shared by all three services)*
- One lookup, not three. This runs on every authenticated request; three round-trips is three
  times the latency floor for the whole platform.
- **Acceptance** revoking by each type refuses **exactly** the intended tokens **and no others** —
  a `sub` revocation must not take out an unrelated user, and a `jti` revocation must not take out
  the session.

### BE-3 · The Redis cache, its completeness marker, the boot warm
- **Spec** R3 · **§5** T3 · **depends** BE-2 · **parallel-safe** no
- `revoked:token:{jti}`, `revoked:session:{sid}`, `revoked:user:{sub}` — TTL = token lifespan + skew.
- **The completeness marker is the whole design.** A cache miss means "not revoked" **only if**
  the cache is known complete. Without the marker, a cold cache silently means "nothing is
  revoked", which is exactly backwards.
- **Acceptance** a mid-suite `FLUSHALL` **still refuses** a previously revoked token.

### BE-4 · The eviction-policy probe and the untrusted-cache path
- **Spec** R4 · **§5** T4 · **depends** BE-3 · **parallel-safe** yes
- **Acceptance** an `allkeys-lru` Redis yields an **untrusted** cache and a health condition
  naming it.
- Under `allkeys-lru`, Redis may evict a revocation key under memory pressure — so the cache can
  no longer answer "not present" authoritatively. Probing the policy at boot and refusing to trust
  a cache that can lie is the difference between a fast path and a silent security hole.

### BE-5 · The sensitive-operation guard — fail closed, degrade for reads
- **Spec** R5 · **§5** T5 · **depends** BE-4 · **parallel-safe** no *(touches every sensitive operation)*
- **Acceptance** under an induced store outage a **payout mutation refuses** and an **event query
  succeeds**.
- Fail-closed everywhere would take the platform down whenever Mongo hiccups; fail-open everywhere
  makes revocation advisory. The split is by consequence: money and permissions fail closed,
  public reads degrade.

### BE-6 · Publish `identity.TokenRevoked`; idempotent consumers in the other two services
- **Spec** R6 · **§5** T6 · **depends** BE-3 · **parallel-safe** yes *(one consumer per agent)*
- **Acceptance** a revocation is effective in **all three** services within **5 s at p99**.
- Consumers idempotent on `eventId` per [`ET-PLT-003`](ET-PLT-003.md) R5.

### BE-7 · The automatic triggers, each with its audit row
- **Spec** R7 · **§5** T7 · **depends** BE-6 · **parallel-safe** yes *(one trigger per agent)*
- **Acceptance** removing a member refuses that member's **next** request; suspending an
  organization refuses **every** member.
- These are the triggers [`ET-ORG-002`](ET-ORG-002.md) BE-6 and [`ET-ORG-001`](ET-ORG-001.md) BE-8
  fire. Without them, "removed from the team" means "removed in 15 minutes, when the token
  expires".

### BE-8 · The GraphQL surface, the internal API, the two error codes
- **Spec** R1, R2, R5 · **§5** T8 · **depends** BE-5 · **parallel-safe** yes
- **Acceptance** `signOutEverywhere` refuses the caller's **other** tokens (not the current one —
  signing yourself out of the request you are making is a confusing 401); every admin field
  carries `@tag(admin)`.

## B · Contract

### GQL-1 · 1 query, 5 mutations
- **depends** BE-8 · **parallel-safe** no *(shared identity SDL — coordinate with ORG-001/002/003)*
- `compose-supergraph.sh --static` → `npm run codegen` → commit. Restart the local router.

## C · Frontend

### FE-1 · `TOKEN_REVOKED` signs the user out — **never retries**
- **depends** GQL-1, [`ET-PLT-005`](ET-PLT-005.md) FE-3 · **parallel-safe** no
- All three apps. Clear local session state, route to login, show a plain reason ("You were
  signed out"). **No retry affordance** — the check is fail-closed, so a retry loops forever
  against a token that will never be accepted again.
- **Acceptance** an e2e revokes a live session and the app lands on login within one request cycle.

### FE-2 · Sign out everywhere
- **depends** GQL-1 · **parallel-safe** yes
- In settings for all three apps. Confirm before firing — it ends every session on every device,
  which is not recoverable by pressing back.
- **testids** `sign-out-everywhere`, `sign-out-everywhere-confirm`

### FE-3 · Active sessions list
- **depends** GQL-1 · **parallel-safe** yes
- Device, last seen, current-session marker. Revoke a single session.
- Timestamps humanised; `Fira Code` only for identifiers, not for prose.
- **testids** `session-row`, `session-revoke`, `session-current-badge`
- Cover the four states — loading, **empty** (single session; still a designed screen), error,
  populated.

### FE-4 · Removed-member experience
- **depends** BE-7 · **parallel-safe** yes
- A member removed mid-session gets the sign-out path, not a wall of `ACTOR_NOT_PERMITTED` errors
  on every panel.

## D · Tests

### TS-1 · Identifier semantics *(L1/L3)*
Revoking by `jti`, `sid`, `sub` each refuses **exactly** the intended set. Include a negative
assertion per type — the failure that matters is over-revocation.

### TS-2 · Durability *(L3 — the load-bearing test)*
Redis `FLUSHALL` mid-suite; a previously revoked token is **still refused**. This is what proves
the store is MongoDB and Redis is only a cache.

### TS-3 · Untrusted cache *(L3)*
Configure the Redis container `allkeys-lru`; assert the cache is marked untrusted and a health
condition names it.

### TS-4 · Fail-closed split *(L3)*
Induce a store outage: a payout mutation **refuses**; an event query **succeeds**. Both, in the
same test, or the split is untested.

### TS-5 · Propagation *(L3, Service Bus)*
Revocation effective in all three services within 5 s at p99. Assert p99, not a single sample.

### TS-6 · Triggers *(L3)*
Member removal refuses the next request; organization suspension refuses every member; each
writes an audit row.

### TS-7 · Frontend *(L5, Playwright)*
- Revoked session → login, **no retry attempted** (assert on network calls, not just the route).
- Sign-out-everywhere confirms first.
- Sessions list: loading, empty, error, populated, by `data-testid`.
- Apollo-driven — use the **F0-4** Testcontainers fixture.

## E · Gate

- [ ] R0 recorded; a Redis-only store classified `contradicted`
- [ ] Durable store in MongoDB; unique + TTL indexes confirmed **live** via MCP
- [ ] One lookup covers all three identifier types
- [ ] Revocation by each type refuses exactly the intended tokens, proven by negative tests
- [ ] Redis `FLUSHALL` does not restore a revoked token
- [ ] `allkeys-lru` yields an untrusted cache and a named health condition
- [ ] Payout refuses and event query succeeds under store outage
- [ ] Effective in all three services within 5 s at p99
- [ ] Member removal and org suspension revoke, each with an audit row
- [ ] `TOKEN_REVOKED` signs out with **no** retry, in all three apps
- [ ] Sessions list covers loading, empty, error, populated
- [ ] `compose-supergraph.sh --static` green; codegen clean
- [ ] Spec `status:` → `implemented`
