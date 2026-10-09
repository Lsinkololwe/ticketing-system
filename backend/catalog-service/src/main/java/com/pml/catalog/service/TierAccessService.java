package com.pml.catalog.service;

import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.error.ValidationRefusal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The hidden tiers of a published event, and the access codes that open them.
 *
 * <h2>What a wrong code reveals</h2>
 * Nothing. A wrong code, an event that is not public, an event with no hidden tier and a tier that is
 * switched off all answer {@code TIER_UNKNOWN}, so a buyer cannot tell a mistyped code from an event
 * with nothing to unlock.
 *
 * <h2>Guessing is rationed</h2>
 * A code is short enough to be guessed, so every attempt counts against the buyer and the event, in
 * Redis, for fifteen minutes: five attempts, then {@code RATE_LIMIT_EXCEEDED} with how long to wait.
 * The count is incremented first and compared after, so parallel guesses cannot slip past the limit.
 * A correct code clears the count. Codes are compared in constant time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TierAccessService {

    public static final String KEY_PREFIX = "rl:tier-unlock:";

    private final TicketTierRepository tiers;
    private final EventRepository events;
    private final ReactiveStringRedisTemplate redis;

    @Value("${catalog.tier.unlock.max-attempts:5}")
    private int maxAttempts = 5;

    @Value("${catalog.tier.unlock.window:PT15M}")
    private Duration window = Duration.ofMinutes(15);

    /** The hidden tier of a public event that {@code accessCode} opens, for this buyer. */
    public Mono<TicketTier> unlock(String eventId, String accessCode, String userId) {
        String wanted = normalise(accessCode);
        if (wanted.isEmpty() || wanted.length() > 64) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("accessCode", "must be 1 to 64 characters"))));
        }
        String key = KEY_PREFIX + userId + ":" + eventId;
        return redis.opsForValue().increment(key).flatMap(attempts -> {
            Mono<Boolean> expiry = attempts == 1 ? redis.expire(key, window) : Mono.just(true);
            return expiry.then(attempts > maxAttempts ? refuseTooMany(key) : open(eventId, wanted)
                    .flatMap(tier -> redis.delete(key).thenReturn(tier)));
        });
    }

    /**
     * Whether {@code accessCode} opens {@code tierId}, for booking to ask before it reserves a hidden
     * tier. Rationed per tier rather than per buyer, since booking cannot name the buyer's guesses.
     */
    public Mono<Boolean> opens(String tierId, String accessCode) {
        String wanted = normalise(accessCode);
        if (wanted.isEmpty() || wanted.length() > 64) {
            return Mono.just(false);
        }
        return tiers.findByIdAndIsHiddenTrueAndIsActiveTrue(tierId)
                .filter(tier -> matches(tier, wanted))
                .hasElement();
    }

    private Mono<TicketTier> open(String eventId, String wanted) {
        return events.findByIdAndPublishedTrueAndIsActiveTrue(eventId)
                .flatMapMany(event -> tiers.findByEventIdOrderBySortOrderAsc(eventId))
                .filter(TicketTier::isHidden)
                .filter(TicketTier::isActive)
                .filter(tier -> matches(tier, wanted))
                .next()
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.TIER_UNKNOWN, "no such ticket tier")));
    }

    private Mono<TicketTier> refuseTooMany(String key) {
        return redis.getExpire(key).defaultIfEmpty(window).flatMap(remaining -> Mono.<TicketTier>error(
                new TranslatedRefusal(ErrorCode.RATE_LIMIT_EXCEEDED,
                        "too many attempts; try again later",
                        Map.of("retryAfterSeconds", Math.max(1L, remaining.toSeconds())))));
    }

    /** Constant-time, and case-insensitive: a code read off a flyer is typed in either case. */
    static boolean matches(TicketTier tier, String normalisedAttempt) {
        if (tier.getAccessCode() == null) {
            return false;
        }
        return MessageDigest.isEqual(
                normalise(tier.getAccessCode()).getBytes(StandardCharsets.UTF_8),
                normalisedAttempt.getBytes(StandardCharsets.UTF_8));
    }

    static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
