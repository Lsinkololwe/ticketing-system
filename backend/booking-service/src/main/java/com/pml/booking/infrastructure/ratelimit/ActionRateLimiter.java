package com.pml.booking.infrastructure.ratelimit;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A fixed-window counter in Redis for actions that can be abused — messaging ticket holders,
 * re-sending a ticket, starting transfers.
 *
 * <p>The increment and the expiry are one script so a crash between them cannot leave a counter
 * that never expires. Redis holds nothing but the counter and its TTL: losing it forgives a window,
 * which is the right failure for a limiter. If Redis cannot be reached the action is <em>refused</em>,
 * because the actions guarded here send messages to strangers and an outage must not remove the cap.
 */
@Component
public class ActionRateLimiter {

    private static final RedisScript<Long> COUNT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
            return count
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;

    public ActionRateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Counts one use of {@code action} by {@code subject}; refuses with {@code RATE_LIMIT_EXCEEDED} when
     * the window already holds {@code limit} uses.
     */
    public Mono<Void> consume(String action, String subject, int limit, Duration window) {
        String key = "rl:booking:" + action + ":" + subject;
        return redis.execute(COUNT, List.of(key), List.of(String.valueOf(window.toSeconds()))).next()
                .onErrorMap(failure -> new TranslatedRefusal(ErrorCode.RATE_LIMIT_EXCEEDED,
                        "the rate limiter is unavailable, so " + action + " is refused for now"))
                .flatMap(count -> count <= limit
                        ? Mono.<Void>empty()
                        : redis.getExpire(key).defaultIfEmpty(window).flatMap(remaining ->
                                Mono.<Void>error(new TranslatedRefusal(ErrorCode.RATE_LIMIT_EXCEEDED,
                                        action + " limit of " + limit + " per " + window + " reached",
                                        Map.of("retryAfterSeconds", Math.max(1, remaining.toSeconds()))))));
    }
}
