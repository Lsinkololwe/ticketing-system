package com.pml.identity.auth.challenge;

import com.pml.identity.config.IdentityChallengeProperties;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.repository.AccountEventRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the durable copy of contact locks in {@code identity_account_events} (kind
 * {@code OTP_LOCK}). A lock is found through the primary key: by the event id prefix for a contact key,
 * or by exact id for a challenge, so checking the mirror when Redis has no lock is cheap and needs no
 * extra index.
 */
@Component
public class MongoLockMirror implements LockMirror {

    static final String KIND = "OTP_LOCK";
    private static final String CHALLENGE_KIND = "OTP_LOCK_CHALLENGE";
    private static final String CONTACT_PREFIX = "otp-lock:";
    private static final String CHALLENGE_PREFIX = "otp-lock-challenge:";

    private final AccountEventRepository events;
    private final IdentityChallengeProperties properties;

    public MongoLockMirror(AccountEventRepository events, IdentityChallengeProperties properties) {
        this.events = events;
        this.properties = properties;
    }

    @Override
    public Mono<Void> record(String contactKey, String challengeId, Instant lockedUntil, Instant at) {
        Map<String, Object> data = new HashMap<>();
        data.put("contactKey", contactKey);
        data.put("lockedUntil", lockedUntil.toEpochMilli());
        AccountEvent lock = AccountEvent.builder()
                .id(CONTACT_PREFIX + contactKey + ":" + lockedUntil.toEpochMilli())
                .kind(KIND)
                .at(at)
                .data(data)
                .build();
        Mono<Void> saved = events.save(lock).then();
        if (challengeId == null) {
            return saved;
        }
        // A second small event keyed by the challenge id, so a check that only knows the challenge can
        // find the lock by primary key.
        AccountEvent byChallenge = lock.toBuilder().id(CHALLENGE_PREFIX + challengeId).kind(CHALLENGE_KIND).build();
        return saved.then(events.save(byChallenge).then());
    }

    @Override
    public Flux<ActiveLock> active(Instant now) {
        return events.findByKindAndAtAfter(KIND, now.minus(properties.getLock()).minusSeconds(60))
                .flatMap(event -> toLock(event, now));
    }

    @Override
    public Mono<ActiveLock> activeForContact(String contactKey, Instant now) {
        if (contactKey == null || !contactKey.matches("[0-9a-f]{1,128}")) {
            return Mono.empty();
        }
        return events.findByIdPrefixAndKind("^" + CONTACT_PREFIX + contactKey + ":", KIND)
                .flatMap(event -> toLock(event, now))
                .reduce((a, b) -> a.lockedUntil().isAfter(b.lockedUntil()) ? a : b);
    }

    @Override
    public Mono<ActiveLock> activeForChallenge(String challengeId, Instant now) {
        return events.findById(CHALLENGE_PREFIX + challengeId).flatMap(event -> toLock(event, now));
    }

    private static Mono<ActiveLock> toLock(AccountEvent event, Instant now) {
        Object contactKey = event.getData() == null ? null : event.getData().get("contactKey");
        Object until = event.getData() == null ? null : event.getData().get("lockedUntil");
        if (!(contactKey instanceof String key) || !(until instanceof Number millis)) {
            return Mono.empty();
        }
        Instant lockedUntil = Instant.ofEpochMilli(millis.longValue());
        return lockedUntil.isAfter(now) ? Mono.just(new ActiveLock(key, lockedUntil)) : Mono.empty();
    }
}
