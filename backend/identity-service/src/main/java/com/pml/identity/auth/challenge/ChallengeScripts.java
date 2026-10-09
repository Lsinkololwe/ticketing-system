package com.pml.identity.auth.challenge;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * The Lua scripts behind the challenge. Each runs atomically in Redis.
 *
 * <p>Time comes from the caller ({@code ARGV}), never from Redis, so expiry, cooldown and lock
 * boundaries follow the service {@link java.time.Clock}; the Redis TTLs set next to them only
 * clean up. Numbers are formatted with {@code %d} because Lua prints large numbers in
 * scientific notation.</p>
 */
final class ChallengeScripts {

    private ChallengeScripts() {
    }

    /**
     * KEYS: lock, cooldown. ARGV: nowMs, cooldownMs.
     * Refuses while locked or cooling down; otherwise starts the cooldown (so two simultaneous
     * requests cannot both pass).
     */
    static final RedisScript<String> GUARD = script("""
            local now = tonumber(ARGV[1])
            local lock = redis.call('GET', KEYS[1])
            if lock then
              local u = tonumber(lock)
              if u and u > now then return 'LOCKED:' .. string.format('%d', u) end
            end
            local cool = redis.call('GET', KEYS[2])
            if cool then
              local r = tonumber(cool)
              if r and r > now then return 'COOLDOWN:' .. string.format('%d', r - now) end
            end
            redis.call('SET', KEYS[2], string.format('%d', now + tonumber(ARGV[2])), 'PX', ARGV[2])
            return 'OK'
            """);

    /**
     * KEYS: challenge, new challenge-id key, attempts. ARGV: digest, challengeId, expiryMs, ttlMs,
     * type, valueEncrypted, masked, contactKey, challenge-id key prefix.
     * Replaces the live code: one live code per contact.
     */
    static final RedisScript<String> STORE = script("""
            local old = redis.call('HGET', KEYS[1], 'cid')
            if old then redis.call('DEL', ARGV[9] .. old) end
            redis.call('DEL', KEYS[1], KEYS[3])
            redis.call('HSET', KEYS[1], 'digest', ARGV[1], 'cid', ARGV[2], 'exp', ARGV[3],
                       'type', ARGV[5], 'venc', ARGV[6], 'masked', ARGV[7])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('SET', KEYS[2], ARGV[8], 'PX', ARGV[4])
            return 'OK'
            """);

    /**
     * KEYS: challenge, attempts, lock, challenge-id key, cooldown.
     * ARGV: candidate digest, challengeId, maxAttempts, nowMs, lockMs, attemptsTtlMs.
     *
     * <p>The attempt is consumed (DECR) BEFORE the comparison, in the same atomic script, so
     * concurrent guesses cannot exceed the cap. The comparison is a full-length XOR/OR fold over
     * both digests (no early exit), so its duration does not depend on how much matched.</p>
     */
    static final RedisScript<String> VERIFY = script("""
            local function fmt(n) return string.format('%d', n) end
            local function ceq(a, b)
              if #a ~= #b then return false end
              local d = 0
              for i = 1, #a do
                d = bit.bor(d, bit.bxor(string.byte(a, i), string.byte(b, i)))
              end
              return d == 0
            end
            local now = tonumber(ARGV[4])
            local lock = redis.call('GET', KEYS[3])
            if lock then
              local u = tonumber(lock)
              if u and u > now then return 'LOCKED:' .. fmt(u) end
            end
            local rec = redis.call('HMGET', KEYS[1], 'digest', 'cid', 'exp', 'type', 'venc', 'masked')
            if (not rec[1]) or rec[2] ~= ARGV[2] or tonumber(rec[3]) <= now then return 'EXPIRED' end
            if redis.call('EXISTS', KEYS[2]) == 0 then
              redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[6])
            end
            local left = redis.call('DECR', KEYS[2])
            local ok = false
            if left >= 0 then ok = ceq(rec[1], ARGV[1]) end
            if ok then
              redis.call('DEL', KEYS[1], KEYS[2], KEYS[4], KEYS[5])
              return 'OK|' .. rec[4] .. '|' .. rec[5] .. '|' .. rec[6]
            end
            if left <= 0 then
              local u = now + tonumber(ARGV[5])
              redis.call('SET', KEYS[3], fmt(u), 'PX', ARGV[5])
              redis.call('DEL', KEYS[1], KEYS[2])
              -- Keep the challenge-id key for the length of the lock so a later attempt with this
              -- challenge sees OTP_LOCKED (not "expired") until the lock runs out.
              redis.call('PEXPIRE', KEYS[4], ARGV[5])
              return 'EXHAUSTED:' .. fmt(u)
            end
            return 'INVALID:' .. fmt(left)
            """);

    /** KEYS: lock. ARGV: lockedUntilMs, nowMs. Restores a lock lost from Redis; never shortens one. */
    static final RedisScript<String> RESTORE_LOCK = script("""
            local until_ms = tonumber(ARGV[1])
            local now = tonumber(ARGV[2])
            if until_ms <= now then return 'STALE' end
            local cur = redis.call('GET', KEYS[1])
            if cur and tonumber(cur) and tonumber(cur) >= until_ms then return 'KEPT' end
            redis.call('SET', KEYS[1], string.format('%d', until_ms), 'PX', string.format('%d', until_ms - now))
            return 'RESTORED'
            """);

    private static RedisScript<String> script(String lua) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(String.class);
        return script;
    }
}
