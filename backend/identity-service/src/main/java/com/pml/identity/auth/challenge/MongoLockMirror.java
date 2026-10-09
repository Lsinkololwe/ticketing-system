package com.pml.identity.auth.challenge;

import com.pml.identity.config.IdentityChallengeProperties;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.repository.AccountEventRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/** Mirrors contact locks to {@code identity_account_events} (kind {@code OTP_LOCK}). */
@Component
public class MongoLockMirror implements LockMirror {

    static final String KIND = "OTP_LOCK";

    private final AccountEventRepository events;
    private final IdentityChallengeProperties properties;

    public MongoLockMirror(AccountEventRepository events, IdentityChallengeProperties properties) {
        this.events = events;
        this.properties = properties;
    }

    @Override
    public Mono<Void> record(String contactKey, Instant lockedUntil, Instant at) {
        AccountEvent event = AccountEvent.builder()
                .id("otp-lock:" + contactKey + ":" + lockedUntil.toEpochMilli())
                .kind(KIND)
                .at(at)
                .data(Map.of("contactKey", contactKey, "lockedUntil", lockedUntil.toEpochMilli()))
                .build();
        return events.save(event).then();
    }

    @Override
    public Flux<ActiveLock> active(Instant now) {
        return events.findByKindAndAtAfter(KIND, now.minus(properties.getLock()).minusSeconds(60))
                .flatMap(event -> {
                    Object contactKey = event.getData() == null ? null : event.getData().get("contactKey");
                    Object until = event.getData() == null ? null : event.getData().get("lockedUntil");
                    if (!(contactKey instanceof String key) || !(until instanceof Number millis)) {
                        return Mono.empty();
                    }
                    Instant lockedUntil = Instant.ofEpochMilli(millis.longValue());
                    return lockedUntil.isAfter(now) ? Mono.just(new ActiveLock(key, lockedUntil)) : Mono.empty();
                });
    }
}
