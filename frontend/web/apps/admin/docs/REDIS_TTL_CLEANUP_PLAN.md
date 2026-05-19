# Redis TTL & Automatic Cleanup Plan

## PML Ticketing System - Enterprise Scale (20M Users)

This document outlines how Redis TTL (Time To Live) is used for automatic data cleanup, ensuring no data is stored indefinitely.

---

## Current TTL Status

| Data Type | Key Pattern | TTL | Auto-Cleanup |
|-----------|-------------|-----|--------------|
| Token Blacklist | `pml-admin:blacklist:{jti}` | Remaining token lifetime | ✅ Native Redis |
| Sessions | `pml-admin:session:{sessionId}` | 8 hours (Better Auth) | ✅ Native Redis |
| JTI→Session Map | `pml-admin:jti:{jti}` | Token lifetime (15 min) | ✅ Native Redis |
| User Session Index | `pml-admin:user:{userId}:sessions` | ❌ **NO TTL** | ❌ **NEEDS FIX** |

---

## The Problem: User Session Index Without TTL

```
Scenario:
1. User logs in → session created + added to user index
2. Session expires after 8 hours (Better Auth TTL)
3. User session index STILL EXISTS with stale session ID
4. Over time: orphaned index entries accumulate

Result at 20M users:
- 20M user index keys × 500 bytes = 10GB of potential orphaned data
```

---

## Solution: TTL for User Session Index

### Option 1: Set TTL on User Index (Recommended)

Set the user session index TTL to match session lifetime (8 hours):

```typescript
// When adding session to user index
await client.sadd(`user:{userId}:sessions`, sessionId);
await client.expire(`user:{userId}:sessions`, 8 * 60 * 60); // 8 hours
```

**Pros:**
- Simple implementation
- Native Redis cleanup
- No background jobs needed

**Cons:**
- TTL resets on each SADD (need to handle carefully)

### Option 2: Session Expiry Event Handler

Use Redis Keyspace Notifications to clean up index when session expires:

```typescript
// Subscribe to session expiry events
redis.subscribe('__keyevent@0__:expired');

redis.on('message', (channel, key) => {
  if (key.startsWith('pml-admin:session:')) {
    const sessionId = key.replace('pml-admin:session:', '');
    // Clean up user index (requires stored userId)
  }
});
```

**Pros:**
- Real-time cleanup
- No polling needed

**Cons:**
- Requires additional session→user mapping
- Keyspace notifications have overhead at scale

### Option 3: Periodic Cleanup Job (Background)

Run a scheduled job to clean up orphaned index entries:

```typescript
// Every hour, clean up orphaned entries
async function cleanupOrphanedIndexes() {
  let cursor = '0';
  do {
    const [next, keys] = await redis.scan(cursor, 'MATCH', 'pml-admin:user:*:sessions', 'COUNT', 100);
    cursor = next;

    for (const indexKey of keys) {
      const sessionIds = await redis.smembers(indexKey);
      for (const sessionId of sessionIds) {
        const exists = await redis.exists(`pml-admin:session:${sessionId}`);
        if (!exists) {
          await redis.srem(indexKey, sessionId);
        }
      }
      // Delete empty index
      const remaining = await redis.scard(indexKey);
      if (remaining === 0) {
        await redis.del(indexKey);
      }
    }
  } while (cursor !== '0');
}
```

**Pros:**
- Handles all edge cases
- No real-time overhead

**Cons:**
- Requires scheduled job infrastructure
- Slight delay in cleanup

---

## Recommended Implementation: Hybrid Approach

Combine **Option 1 (TTL)** with **Option 3 (Periodic Cleanup)** for robustness:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      HYBRID TTL + CLEANUP APPROACH                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  PRIMARY: Native Redis TTL                                                       │
│  ─────────────────────────                                                       │
│                                                                                  │
│  1. Token Blacklist                                                              │
│     Key: pml-admin:blacklist:{jti}                                              │
│     TTL: remaining token lifetime (calculated from exp claim)                    │
│     Command: SET key "1" EX {ttl}                                               │
│                                                                                  │
│  2. Sessions (Better Auth)                                                       │
│     Key: pml-admin:session:{sessionId}                                          │
│     TTL: 8 hours (configured in session.expiresIn)                              │
│     Managed by: Better Auth + Redis Storage adapter                             │
│                                                                                  │
│  3. JTI→Session Mapping                                                          │
│     Key: pml-admin:jti:{jti}                                                    │
│     TTL: token lifetime (15 minutes)                                            │
│     Command: SET key {sessionId} EX {ttl}                                       │
│                                                                                  │
│  4. User Session Index (NEW)                                                     │
│     Key: pml-admin:user:{userId}:sessions                                       │
│     TTL: 8 hours (match session lifetime)                                       │
│     Command: SADD + EXPIRE                                                       │
│                                                                                  │
│  ─────────────────────────────────────────────────────────────────────────────  │
│                                                                                  │
│  SECONDARY: Periodic Cleanup Job                                                 │
│  ──────────────────────────────                                                  │
│                                                                                  │
│  Frequency: Every 1 hour                                                        │
│  Purpose: Clean orphaned entries (edge cases, crashes, etc.)                    │
│  Implementation: Cron job or scheduled task                                     │
│                                                                                  │
│  Cleanup targets:                                                               │
│  - Empty user session indexes                                                   │
│  - Session IDs in index that don't exist                                        │
│  - Any keys without TTL older than 24 hours                                     │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Complete TTL Configuration

### 1. Token Blacklist (Already Implemented ✅)

```typescript
// token-blacklist.ts - Line 81-98
export async function blacklistToken(jti: string, expiresAt: number): Promise<boolean> {
  const now = Math.floor(Date.now() / 1000);
  const ttl = expiresAt - now;  // Calculate remaining lifetime

  if (ttl <= 0) {
    return false;  // Token already expired
  }

  // SET with EX (expire) - automatic cleanup after TTL
  await client.set(`${BLACKLIST_PREFIX}${jti}`, '1', 'EX', ttl);
  return true;
}
```

### 2. Sessions (Better Auth Managed ✅)

```typescript
// auth/index.ts - Line 96-108
const SESSION_CONFIG = {
  expiresIn: 8 * 60 * 60, // 8 hours in seconds - Better Auth sets TTL
  updateAge: 5 * 60,
};
```

### 3. JTI→Session Mapping (Already Implemented ✅)

```typescript
// token-blacklist.ts - Line 227-234
export async function mapJtiToSession(jti: string, sessionId: string, ttl: number): Promise<void> {
  // SET with EX - automatic cleanup after TTL
  await client.set(`${JTI_SESSION_PREFIX}${jti}`, sessionId, 'EX', ttl);
}
```

### 4. User Session Index (NEEDS UPDATE ⚠️)

```typescript
// token-blacklist.ts - UPDATED
const SESSION_TTL_SECONDS = 8 * 60 * 60; // 8 hours (match session lifetime)

export async function addSessionToUserIndex(userId: string, sessionId: string): Promise<void> {
  const client = getRedis();
  const key = `${USER_SESSIONS_PREFIX}${userId}:sessions`;

  try {
    // Add session to set
    await client.sadd(key, sessionId);

    // Set/refresh TTL to match session lifetime
    // This ensures the index is cleaned up even if logout doesn't happen
    await client.expire(key, SESSION_TTL_SECONDS);

    console.log(`[TokenBlacklist] Added session to user index: ${maskJti(userId)}`);
  } catch (error) {
    console.error('[TokenBlacklist] Failed to add session to user index:', error);
  }
}
```

---

## Memory Projection with TTL

### With Proper TTL (Recommended)

```
┌──────────────────────────────────────────────────────────────────┐
│ Data Type          │ Max Entries  │ Size Each │ Total Memory    │
├──────────────────────────────────────────────────────────────────┤
│ Token Blacklist    │ 200K         │ 50B       │ 10 MB           │
│ Sessions           │ 30M          │ 2KB       │ 60 GB           │
│ User Session Index │ 20M          │ 500B      │ 10 GB           │
│ JTI→Session Map    │ 30M          │ 100B      │ 3 GB            │
├──────────────────────────────────────────────────────────────────┤
│ TOTAL (with TTL)   │              │           │ ~73 GB          │
│ + Redis overhead   │              │ ~30%      │ ~95 GB          │
└──────────────────────────────────────────────────────────────────┘

All data auto-expires - NO indefinite storage!
```

### Without TTL on User Index (Problem)

```
┌──────────────────────────────────────────────────────────────────┐
│ Over time, orphaned user indexes accumulate:                     │
│                                                                  │
│ Month 1:  10 GB user indexes                                    │
│ Month 6:  40 GB user indexes (stale entries accumulate)         │
│ Month 12: 80 GB user indexes (memory pressure)                  │
│                                                                  │
│ Result: Redis OOM errors, degraded performance                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Implementation Checklist

### Phase 1: Add TTL to User Session Index ✅

- [ ] Update `addSessionToUserIndex()` to set EXPIRE
- [ ] Test that TTL is refreshed on new session additions
- [ ] Verify cleanup happens after 8 hours

### Phase 2: Periodic Cleanup Job

- [ ] Create cleanup function
- [ ] Schedule with cron/Bull/Agenda
- [ ] Add monitoring for orphaned entries
- [ ] Alert if cleanup finds excessive orphans

### Phase 3: Monitoring & Alerting

- [ ] Monitor Redis memory usage
- [ ] Alert if memory > 80% capacity
- [ ] Track TTL key count vs non-TTL key count
- [ ] Dashboard for session/token metrics

---

## Redis Commands for Verification

```bash
# Check if key has TTL
redis-cli TTL pml-admin:user:abc123:sessions
# Returns: seconds remaining, -1 if no TTL, -2 if key doesn't exist

# Check all keys without TTL (DANGEROUS at scale, use sparingly)
redis-cli --scan --pattern 'pml-admin:*' | while read key; do
  ttl=$(redis-cli TTL "$key")
  if [ "$ttl" = "-1" ]; then
    echo "NO TTL: $key"
  fi
done

# Count keys by pattern
redis-cli --scan --pattern 'pml-admin:blacklist:*' | wc -l
redis-cli --scan --pattern 'pml-admin:session:*' | wc -l
redis-cli --scan --pattern 'pml-admin:user:*' | wc -l

# Memory usage for a key
redis-cli MEMORY USAGE pml-admin:session:abc123
```

---

## Summary

| Aspect | Status | Action |
|--------|--------|--------|
| Token Blacklist TTL | ✅ Implemented | None |
| Session TTL | ✅ Better Auth | None |
| JTI→Session Map TTL | ✅ Implemented | None |
| User Session Index TTL | ⚠️ Missing | **Update code** |
| Periodic Cleanup | ❌ Not implemented | Optional (Phase 2) |

**Key Principle**: Every key in Redis MUST have a TTL. No indefinite storage. Native Redis handles all cleanup.
