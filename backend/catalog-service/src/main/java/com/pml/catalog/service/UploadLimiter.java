package com.pml.catalog.service;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * How fast an organization may add pictures: {@code catalog.media.max-uploads-per-hour} (60) in a
 * rolling hour, counted in Redis. The library cap bounds what an organization holds; this bounds how
 * quickly one account can push bytes through the service, which is the abuse the cap does not stop (an
 * upload-and-delete loop).
 *
 * <p>The counter is incremented first and compared after, so parallel uploads cannot slip past it.
 */
@Component
public class UploadLimiter {

    static final String KEY_PREFIX = "rl:media-upload:";

    private final ReactiveStringRedisTemplate redis;
    private final int maxPerWindow;
    private final Duration window;

    public UploadLimiter(ReactiveStringRedisTemplate redis,
                         @Value("${catalog.media.max-uploads-per-hour:60}") int maxPerWindow,
                         @Value("${catalog.media.upload-window:PT1H}") Duration window) {
        this.redis = redis;
        this.maxPerWindow = maxPerWindow;
        this.window = window;
    }

    /** Counts one upload by {@code organizationId}; errors with {@code RATE_LIMIT_EXCEEDED} past the limit. */
    public Mono<Void> take(String organizationId) {
        String key = KEY_PREFIX + organizationId;
        return redis.opsForValue().increment(key).flatMap(count -> {
            Mono<Boolean> expiry = count == 1 ? redis.expire(key, window) : Mono.just(true);
            if (count <= maxPerWindow) {
                return expiry.then();
            }
            return expiry.then(redis.getExpire(key).defaultIfEmpty(window)).flatMap(remaining -> Mono.<Void>error(
                    new TranslatedRefusal(ErrorCode.RATE_LIMIT_EXCEEDED, "too many uploads; try again later",
                            Map.of("retryAfterSeconds", Math.max(1L, remaining.toSeconds())))));
        });
    }
}
