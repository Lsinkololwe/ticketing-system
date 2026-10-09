package com.pml.identity.auth.limits;

import com.pml.identity.config.IdentityLimitsProperties;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Volume limits on code requests (ET-IDN-001-R3): codes per contact per day, per IP per hour,
 * distinct contacts per device per day, codes per country per day.
 *
 * <p>All four counters are checked, and only if every one has room are all of them advanced, in
 * one atomic script: a refused request consumes nothing, so nothing is sent past a cap and a
 * refusal by one scope does not burn the allowance of another. Keys are
 * {@code lim:{scope}:{id}:{window}}; the id of an IP or device is hashed, and a contact is its
 * contact key, so a Redis dump holds no raw contact. Metrics carry the scope only.</p>
 */
@Service
public class LimitService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private static final RedisScript<String> CHECK_AND_COUNT = script();

    private final ReactiveStringRedisTemplate redis;
    private final IdentityLimitsProperties limits;
    private final MeterRegistry meters;

    public LimitService(ReactiveStringRedisTemplate redis, IdentityLimitsProperties limits, MeterRegistry meters) {
        this.redis = redis;
        this.limits = limits;
        this.meters = meters;
    }

    /**
     * Counts one code request.
     *
     * @param contactKey the contact key (never the raw contact)
     * @param clientIp   the caller's IP as vouched for by the trusted proxy chain; blank counts as one shared "unknown" bucket
     * @param deviceId   optional device identifier
     * @param country    ISO country of a phone contact, null for email
     * @return completes when allowed; fails with OTP_RATE_LIMITED carrying {@code retryAfterSeconds}
     */
    public Mono<Void> consume(String contactKey, String clientIp, String deviceId, String country) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
        boolean hasDevice = deviceId != null && !deviceId.isBlank();
        boolean hasCountry = country != null && !country.isBlank();
        String countryCode = hasCountry ? country.toUpperCase(java.util.Locale.ROOT) : "";

        List<String> keys = List.of(
                key("contact", contactKey, "day"),
                key("ip", sha256(ip), "hour"),
                key("device", hasDevice ? sha256(deviceId.trim()) : "none", "day"),
                key("country", hasCountry ? countryCode : "none", "day"));
        List<String> args = List.of(
                String.valueOf(limits.getContactCodesPerDay()), String.valueOf(DAY.toSeconds()),
                String.valueOf(limits.getIpCodesPerHour()), String.valueOf(HOUR.toSeconds()),
                String.valueOf(limits.getDeviceDistinctContactsPerDay()), String.valueOf(DAY.toSeconds()),
                String.valueOf(hasCountry ? limits.countryLimit(countryCode) : 0), String.valueOf(DAY.toSeconds()),
                contactKey,
                hasDevice ? "1" : "0",
                hasCountry ? "1" : "0");

        return redis.execute(CHECK_AND_COUNT, keys, args).next().flatMap(result -> {
            if ("OK".equals(result)) {
                return Mono.<Void>empty();
            }
            // "RATE_LIMITED:<scope>:<seconds>"
            String[] parts = result.split(":");
            String scope = parts.length > 1 ? parts[1] : "unknown";
            long retryAfter = parts.length > 2 ? Math.max(1, Long.parseLong(parts[2])) : 60;
            meters.counter("identity.otp.limit.refused", "scope", scope).increment();
            return Mono.error(new TranslatedRefusal(ErrorCode.OTP_RATE_LIMITED,
                    "code request limit reached for scope " + scope,
                    Map.of("retryAfterSeconds", retryAfter)));
        });
    }

    private static String key(String scope, String id, String window) {
        return "lim:" + scope + ":" + id + ":" + window;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * KEYS: contact counter, ip counter, device set, country counter.
     * ARGV: (limit, ttlSeconds) x4 for contact, ip, device, country; contactKey; deviceActive; countryActive.
     */
    private static RedisScript<String> script() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setResultType(String.class);
        script.setScriptText("""
                local function ttl(k, def)
                  local t = redis.call('TTL', k)
                  if t < 1 then return def end
                  return t
                end
                local deviceActive = ARGV[10] == '1'
                local countryActive = ARGV[11] == '1'

                local c = tonumber(redis.call('GET', KEYS[1]) or '0')
                if c >= tonumber(ARGV[1]) then return 'RATE_LIMITED:contact:' .. ttl(KEYS[1], ARGV[2]) end

                local i = tonumber(redis.call('GET', KEYS[2]) or '0')
                if i >= tonumber(ARGV[3]) then return 'RATE_LIMITED:ip:' .. ttl(KEYS[2], ARGV[4]) end

                if deviceActive then
                  if redis.call('SISMEMBER', KEYS[3], ARGV[9]) == 0
                     and redis.call('SCARD', KEYS[3]) >= tonumber(ARGV[5]) then
                    return 'RATE_LIMITED:device:' .. ttl(KEYS[3], ARGV[6])
                  end
                end

                if countryActive then
                  local k = tonumber(redis.call('GET', KEYS[4]) or '0')
                  if k >= tonumber(ARGV[7]) then return 'RATE_LIMITED:country:' .. ttl(KEYS[4], ARGV[8]) end
                end

                if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
                if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], ARGV[4]) end
                if deviceActive then
                  redis.call('SADD', KEYS[3], ARGV[9])
                  if redis.call('TTL', KEYS[3]) < 0 then redis.call('EXPIRE', KEYS[3], ARGV[6]) end
                end
                if countryActive then
                  if redis.call('INCR', KEYS[4]) == 1 then redis.call('EXPIRE', KEYS[4], ARGV[8]) end
                end
                return 'OK'
                """);
        return script;
    }
}
