package com.pml.identity.auth.challenge;

import com.pml.identity.auth.delivery.DeliveryChannel;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.auth.limits.LimitService;
import com.pml.identity.config.IdentityChallengeProperties;
import com.pml.identity.config.IdentityLimitsProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.security.ContactCrypto;
import com.pml.identity.security.ContactHasher;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.util.ContactKeys;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-time sign-in codes: what a buyer asking for a code and typing it back goes through.
 *
 * <p>The buyer gives a phone number or an email in checkout and asks for a code. The system
 * promises: one live code per contact; a new code is not sent again within a minute; the code
 * lives five minutes; five wrong guesses lock that contact for fifteen minutes, and the lock
 * holds even if Redis is wiped; and the answer never reveals whether the contact already has an
 * account. When a refusal happens the buyer gets one clear reason: contact not acceptable,
 * channel unavailable, wait before asking again, too many requests, locked until a given time,
 * wrong code with attempts left, or code expired. If the message cannot be delivered the buyer is
 * told to try again and nothing stays behind that would block the retry.</p>
 *
 * <h2>Issue</h2>
 * normalise (CONTACT_INVALID) -> channel available -> lock/cooldown guard (one atomic script) ->
 * volume limits -> store {@code HMAC-SHA256(code, pepper)} replacing any live code -> deliver. On a
 * total delivery failure the challenge and cooldown are deleted and the request is refused,
 * retryable. Nothing here asks whether an account exists, so a known and an unknown contact take
 * exactly the same path.
 *
 * <h2>Verify</h2>
 * One Lua script: lock check, atomic attempt decrement BEFORE the comparison, constant-time digest
 * comparison, delete on success, lock on exhaustion. A lock is mirrored to MongoDB; when Redis has
 * no lock for a contact (or for a challenge id) while the mirror holds one still running, the lock
 * is put back and the caller is refused as locked.
 *
 * <p>The code and the contact are never logged and never put in an exception message.</p>
 */
@Service
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What a caller asks for. */
    public record IssueCommand(String contact, ContactType type, String regionHint, String clientIp,
                               String deviceId, DeliveryChannel preferredChannel) {
    }

    /** What a caller gets back; identical in shape for known and unknown contacts. */
    public record Issued(String challengeId, ContactType contactType, String maskedContact, DeliveryChannel channel,
                         long expiresInSeconds, long resendAfterSeconds) {
    }

    /** The contact a correct code proved. {@code valueEncrypted} is AES-GCM ciphertext. */
    public record Verified(String contactKey, ContactType type, String valueEncrypted, String valueMasked) {
        @Override
        public String toString() {
            return "Verified[" + type + ", " + valueMasked + "]";
        }
    }

    private final ReactiveStringRedisTemplate redis;
    private final IdentityChallengeProperties properties;
    private final IdentityLimitsProperties limitProperties;
    private final ContactHasher hasher;
    private final ContactCrypto crypto;
    private final LimitService limits;
    private final DeliveryOrchestrator delivery;
    private final LockMirror mirror;
    private final Clock clock;
    private final MeterRegistry meters;
    private final byte[] pepper;

    public ChallengeService(ReactiveStringRedisTemplate redis, IdentityChallengeProperties properties,
                            IdentityLimitsProperties limitProperties, ContactHasher hasher, ContactCrypto crypto,
                            LimitService limits, DeliveryOrchestrator delivery, LockMirror mirror, Clock clock,
                            MeterRegistry meters) {
        this.redis = redis;
        this.properties = properties;
        this.limitProperties = limitProperties;
        this.hasher = hasher;
        this.crypto = crypto;
        this.limits = limits;
        this.delivery = delivery;
        this.mirror = mirror;
        this.clock = clock;
        this.meters = meters;
        String configured = properties.getHmacPepper();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("identity.challenge.hmac-pepper must be set");
        }
        this.pepper = configured.getBytes(StandardCharsets.UTF_8);
    }

    public Mono<Issued> issue(IssueCommand command) {
        return Mono.defer(() -> {
            if (command.clientIp() == null || command.clientIp().isBlank()) {
                // The per-IP limit has to count the real caller; without an address there is nothing to count.
                return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "clientIp is required"));
            }
            ContactHasher.Normalized contact = hasher.normalize(command.contact(), command.type(),
                            command.regionHint(), limitProperties.getAllowedCountries())
                    .orElseThrow(() -> new TranslatedRefusal(ErrorCode.CONTACT_INVALID, "contact not acceptable"));
            delivery.assertAvailable(contact.type());

            String key = contact.key();
            long now = clock.millis();
            long ttlMs = properties.getTtl().toMillis();
            long cooldownMs = properties.getCooldown().toMillis();

            // A lock the mirror still holds is put back first, so the one atomic guard script below
            // refuses a locked contact even when Redis lost its lock key while running.
            return reseedLockFromMirror(key)
                    .then(Mono.defer(() -> redis.execute(ChallengeScripts.GUARD,
                                    List.of(ChallengeKeys.lock(key), ChallengeKeys.cooldown(key)),
                                    List.of(String.valueOf(now), String.valueOf(cooldownMs)))
                            .next()))
                    .flatMap(guard -> afterGuard(guard, contact, command, now, ttlMs));
        });
    }

    private Mono<Issued> afterGuard(String guard, ContactHasher.Normalized contact, IssueCommand command,
                                    long now, long ttlMs) {
        String key = contact.key();
        if (guard.startsWith("LOCKED:")) {
            return Mono.error(locked(Long.parseLong(guard.substring(7))));
        }
        if (guard.startsWith("COOLDOWN:")) {
            long seconds = Math.max(1, (Long.parseLong(guard.substring(9)) + 999) / 1000);
            meters.counter("identity.otp.challenge", "outcome", "cooldown").increment();
            return Mono.error(new TranslatedRefusal(ErrorCode.OTP_COOLDOWN_ACTIVE, "a code was sent recently",
                    Map.of("retryAfterSeconds", seconds)));
        }

        String challengeId = UUID.randomUUID().toString();
        String code = newCode();
        String digest = digest(code);

        // A refusal by the limits releases only the cooldown: a code that is already live stays live.
        Mono<Void> allowed = limits.consume(key, command.clientIp(), command.deviceId(), contact.region())
                .onErrorResume(error -> redis.delete(ChallengeKeys.cooldown(key))
                        .onErrorResume(ignored -> Mono.empty())
                        .then(Mono.<Void>error(error)));

        Mono<DeliveryChannel> sent = crypto.encrypt(contact.value())
                .flatMap(encrypted -> redis.execute(ChallengeScripts.STORE,
                                List.of(ChallengeKeys.challenge(key), ChallengeKeys.challengeId(challengeId),
                                        ChallengeKeys.attempts(key)),
                                List.of(digest, challengeId, String.valueOf(now + ttlMs), String.valueOf(ttlMs),
                                        contact.type().name(), encrypted, contact.masked(), key,
                                        ChallengeKeys.CHALLENGE_ID_PREFIX))
                        .next())
                .then(delivery.deliver(contact.type(), contact.value(), code, command.preferredChannel()))
                .onErrorResume(error -> discard(key, challengeId).then(Mono.<DeliveryChannel>error(error)));

        return allowed.then(sent)
                .map(channel -> {
                    meters.counter("identity.otp.challenge", "outcome", "issued", "channel", channel.name()).increment();
                    return new Issued(challengeId, contact.type(), contact.masked(), channel,
                            properties.getTtl().toSeconds(), properties.getCooldown().toSeconds());
                });
    }

    /** Removes a challenge that was never delivered (or never stored) and releases its cooldown. */
    private Mono<Void> discard(String contactKey, String challengeId) {
        return redis.delete(ChallengeKeys.challenge(contactKey), ChallengeKeys.challengeId(challengeId),
                        ChallengeKeys.attempts(contactKey), ChallengeKeys.cooldown(contactKey))
                .then()
                .onErrorResume(error -> {
                    log.warn("Could not discard an undelivered challenge: {}", error.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    /**
     * Checks a code. Atomic: of N concurrent wrong guesses at most {@code max-attempts} are
     * evaluated; the rest see the lock.
     */
    public Mono<Verified> verify(String challengeId, String code) {
        return Mono.defer(() -> {
            if (challengeId == null || code == null || code.length() > 64) {
                return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "challengeId and code are required"));
            }
            if (!plausibleId(challengeId)) {
                return Mono.error(expired());
            }
            return redis.opsForValue().get(ChallengeKeys.challengeId(challengeId))
                    .switchIfEmpty(Mono.defer(() -> refuseFromMirror(challengeId)))
                    .flatMap(contactKey -> reseedLockFromMirror(contactKey)
                            .then(Mono.defer(() -> verifyAtomically(contactKey, challengeId, code))));
        });
    }

    private Mono<Verified> verifyAtomically(String contactKey, String challengeId, String code) {
        long now = clock.millis();
        List<String> keys = List.of(ChallengeKeys.challenge(contactKey), ChallengeKeys.attempts(contactKey),
                ChallengeKeys.lock(contactKey), ChallengeKeys.challengeId(challengeId), ChallengeKeys.cooldown(contactKey));
        List<String> args = List.of(digest(code), challengeId, String.valueOf(properties.getMaxAttempts()),
                String.valueOf(now), String.valueOf(properties.getLock().toMillis()),
                String.valueOf(properties.getTtl().toMillis()));

        return redis.execute(ChallengeScripts.VERIFY, keys, args).next().flatMap(result -> {
            if (result.startsWith("OK|")) {
                String[] parts = result.split("\\|", 4);
                meters.counter("identity.otp.verify", "outcome", "ok").increment();
                return Mono.just(new Verified(contactKey, ContactType.valueOf(parts[1]), parts[2], parts[3]));
            }
            if (result.equals("EXPIRED")) {
                meters.counter("identity.otp.verify", "outcome", "expired").increment();
                return Mono.error(expired());
            }
            if (result.startsWith("LOCKED:")) {
                meters.counter("identity.otp.verify", "outcome", "locked").increment();
                return Mono.error(locked(Long.parseLong(result.substring(7))));
            }
            if (result.startsWith("EXHAUSTED:")) {
                long until = Long.parseLong(result.substring(10));
                meters.counter("identity.otp.verify", "outcome", "exhausted").increment();
                // The lock in Redis applies whether or not the durable copy could be written; a
                // failed write is counted and logged by class name only.
                return mirror.record(contactKey, challengeId, Instant.ofEpochMilli(until), clock.instant())
                        .onErrorResume(error -> {
                            meters.counter("identity.otp.lock.mirror", "outcome", "write_failed").increment();
                            log.warn("Could not mirror a contact lock: {}", error.getClass().getSimpleName());
                            return Mono.empty();
                        })
                        .then(Mono.error(locked(until)));
            }
            long left = Long.parseLong(result.substring(result.indexOf(':') + 1));
            meters.counter("identity.otp.verify", "outcome", "invalid").increment();
            return Mono.error(new TranslatedRefusal(ErrorCode.OTP_INVALID, "wrong code",
                    Map.of("attemptsRemaining", left)));
        });
    }

    /**
     * The challenge id has no live code in Redis. When Redis lost its keys while running, the
     * mirror may still hold the lock that this challenge's wrong guesses caused: the lock is put
     * back and the caller is refused as locked until it ends. With no such lock the code simply
     * has expired.
     */
    private Mono<String> refuseFromMirror(String challengeId) {
        return mirror.activeForChallenge(challengeId, clock.instant())
                .onErrorResume(error -> mirrorReadFailed(error).then(Mono.empty()))
                .flatMap(lock -> putBack(lock)
                        .then(Mono.<String>error(locked(lock.lockedUntil().toEpochMilli()))))
                .switchIfEmpty(Mono.error(ChallengeService::expired));
    }

    /**
     * If Redis holds no lock key for this contact but the mirror holds one still running, writes it
     * back. The write is the same script startup uses: it never shortens a lock that is there, so
     * two requests doing this at once cannot undo each other.
     */
    private Mono<Void> reseedLockFromMirror(String contactKey) {
        return redis.hasKey(ChallengeKeys.lock(contactKey)).flatMap(present -> {
            if (present) {
                return Mono.<Void>empty();
            }
            return mirror.activeForContact(contactKey, clock.instant())
                    .onErrorResume(error -> mirrorReadFailed(error).then(Mono.empty()))
                    .flatMap(this::putBack);
        });
    }

    private Mono<Void> putBack(LockMirror.ActiveLock lock) {
        return redis.execute(ChallengeScripts.RESTORE_LOCK, List.of(ChallengeKeys.lock(lock.contactKey())),
                        List.of(String.valueOf(lock.lockedUntil().toEpochMilli()), String.valueOf(clock.millis())))
                .next()
                .doOnNext(result -> {
                    if ("RESTORED".equals(result)) {
                        meters.counter("identity.otp.lock.mirror", "outcome", "reseeded").increment();
                    }
                })
                .then();
    }

    /** The durable copy could not be read: Redis stays the authority, the miss is counted and logged. */
    private Mono<Void> mirrorReadFailed(Throwable error) {
        meters.counter("identity.otp.lock.mirror", "outcome", "read_failed").increment();
        log.warn("Could not read the contact lock mirror: {}", error.getClass().getSimpleName());
        return Mono.empty();
    }

    /** Puts back locks Redis lost; returns how many were restored. */
    public Mono<Long> restoreLocks() {
        long now = clock.millis();
        return mirror.active(clock.instant())
                .flatMap(lock -> redis.execute(ChallengeScripts.RESTORE_LOCK,
                                List.of(ChallengeKeys.lock(lock.contactKey())),
                                List.of(String.valueOf(lock.lockedUntil().toEpochMilli()), String.valueOf(now)))
                        .next())
                .filter("RESTORED"::equals)
                .count();
    }

    private String newCode() {
        int length = properties.getCodeLength();
        long bound = 1;
        for (int i = 0; i < length; i++) {
            bound *= 10;
        }
        return String.format("%0" + length + "d", RANDOM.nextLong(bound));
    }

    private String digest(String code) {
        return ContactKeys.hmacHex(pepper, code);
    }

    private static boolean plausibleId(String id) {
        return id.length() >= 8 && id.length() <= 64 && id.matches("[A-Za-z0-9-]+");
    }

    private static TranslatedRefusal expired() {
        return new TranslatedRefusal(ErrorCode.OTP_EXPIRED, "no live code for this challenge");
    }

    private static TranslatedRefusal locked(long untilMillis) {
        return new TranslatedRefusal(ErrorCode.OTP_LOCKED, "contact locked",
                Map.of("lockedUntil", Instant.ofEpochMilli(untilMillis).toString()));
    }
}
