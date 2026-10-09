package com.pml.identity.auth;

import com.pml.shared.testing.RedisNode;
import com.pml.identity.auth.challenge.ChallengeKeys;
import com.pml.identity.auth.challenge.ChallengeService;
import com.pml.identity.auth.delivery.DeliveryChannel;
import com.pml.identity.domain.enums.ContactType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.util.ContactKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The challenge against a real Redis and a clock that only the test moves (ET-IDN-001 R2-R4). */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001-R2/R3/R4 · a code is delivered, expires, throttles, locks, and is checked atomically")
class ChallengeServiceTest {

    /** The tests run against a real Redis, from the shared test fixtures. */
    private static final Class<?> STORE = RedisNode.class;

    private final AuthEngine engine = new AuthEngine();

    @Test
    @DisplayName("R2 · a code is issued, delivered on the contact's channel, and works exactly once")
    void issueDeliverVerifyOnce() {
        String phone = AuthEngine.randomPhone();
        var issued = engine.issue(phone);

        assertThat(issued.channel()).isEqualTo(DeliveryChannel.WHATSAPP);
        assertThat(issued.contactType()).isEqualTo(ContactType.WHATSAPP);
        assertThat(issued.expiresInSeconds()).isEqualTo(300);
        assertThat(issued.resendAfterSeconds()).isEqualTo(60);
        assertThat(issued.maskedContact()).startsWith("+260 97").contains("*");

        String code = engine.codeFor(phone);
        assertThat(code).matches("\\d{6}");

        var verified = engine.challenges.verify(issued.challengeId(), code).block();
        assertThat(verified.type()).isEqualTo(ContactType.WHATSAPP);
        assertThat(verified.valueEncrypted()).startsWith("v1.").doesNotContain(phone);

        Refusals.of(ErrorCode.OTP_EXPIRED, engine.challenges.verify(issued.challengeId(), code));
    }

    @Test
    @DisplayName("R2 · an email contact is delivered by email")
    void emailChannel() {
        String email = AuthEngine.randomEmail();
        var issued = engine.issue(email.toUpperCase());
        assertThat(issued.channel()).isEqualTo(DeliveryChannel.EMAIL);
        assertThat(issued.contactType()).isEqualTo(ContactType.EMAIL);
        assertThat(engine.captured.lastCodeTo(email)).isPresent();
    }

    @Test
    @DisplayName("R2 · Redis holds HMAC(code, pepper) with a 300 s TTL, never the code")
    void storedAsKeyedHash() {
        String phone = AuthEngine.randomPhone();
        var issued = engine.issue(phone);
        String code = engine.codeFor(phone);
        String contactKey = engine.hasher.normalize(phone, null, null, null).orElseThrow().key();

        Map<Object, Object> stored = engine.redis.opsForHash().entries(ChallengeKeys.challenge(contactKey))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue).block();
        assertThat(stored.get("digest")).isEqualTo(ContactKeys.hmacHex(
                "test-challenge-pepper-not-a-secret".getBytes(), code));
        assertThat(stored.values()).noneMatch(value -> String.valueOf(value).equals(code));
        assertThat(stored.get("cid")).isEqualTo(issued.challengeId());
        Duration ttl = engine.redis.getExpire(ChallengeKeys.challenge(contactKey)).block();
        assertThat(ttl).isBetween(Duration.ofSeconds(290), Duration.ofSeconds(300));
        assertThat(engine.redis.opsForValue().get(ChallengeKeys.challengeId(issued.challengeId())).block())
                .isEqualTo(contactKey);
    }

    @Test
    @DisplayName("R2 · a new request replaces the previous code: one live code per contact")
    void newCodeReplacesOld() {
        String phone = AuthEngine.randomPhone();
        var first = engine.issue(phone);
        String firstCode = engine.codeFor(phone);
        engine.clock.advance(Duration.ofSeconds(61));
        var second = engine.issue(phone);
        String secondCode = engine.codeFor(phone);

        Refusals.of(ErrorCode.OTP_EXPIRED, engine.challenges.verify(first.challengeId(), firstCode));
        // The old id is gone, so the old code cannot even be tried against the new challenge.
        Refusals.of(ErrorCode.OTP_EXPIRED, engine.challenges.verify(first.challengeId(), secondCode));
        assertThat(engine.challenges.verify(second.challengeId(), secondCode).block()).isNotNull();
    }

    @Test
    @DisplayName("R2 · a frozen clock accepts the code at 4:59 and refuses it at 5:01")
    void expiry() {
        String early = AuthEngine.randomPhone();
        String late = AuthEngine.randomPhone();
        var a = engine.issue(early);
        var b = engine.issue(late);

        engine.clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(engine.challenges.verify(a.challengeId(), engine.codeFor(early)).block()).isNotNull();

        engine.clock.advance(Duration.ofSeconds(2));
        Refusals.of(ErrorCode.OTP_EXPIRED, engine.challenges.verify(b.challengeId(), engine.codeFor(late)));
    }

    @Test
    @DisplayName("R3 · a resend is refused at 59 s with retryAfterSeconds and sends nothing, and allowed at 61 s")
    void cooldown() {
        String phone = AuthEngine.randomPhone();
        engine.issue(phone);
        engine.clock.advance(Duration.ofSeconds(59));

        TranslatedRefusal refusal = Refusals.of(ErrorCode.OTP_COOLDOWN_ACTIVE, engine.challenges.issue(engine.command(phone)));
        assertThat(((Number) refusal.details().get("retryAfterSeconds")).longValue()).isBetween(1L, 2L);
        assertThat(refusal.retryable()).isTrue();
        assertThat(engine.captured.countTo(phone)).isEqualTo(1);

        engine.clock.advance(Duration.ofSeconds(2));
        engine.issue(phone);
        assertThat(engine.captured.countTo(phone)).isEqualTo(2);
    }

    @Test
    @DisplayName("R3 · wrong codes count down from five; the fifth deletes the code and locks the contact")
    void attemptsThenLock() {
        String phone = AuthEngine.randomPhone();
        var issued = engine.issue(phone);
        String real = engine.codeFor(phone);
        String wrong = real.equals("000000") ? "000001" : "000000";

        for (int expectedRemaining = 4; expectedRemaining >= 1; expectedRemaining--) {
            TranslatedRefusal refusal = Refusals.of(ErrorCode.OTP_INVALID, engine.challenges.verify(issued.challengeId(), wrong));
            assertThat(((Number) refusal.details().get("attemptsRemaining")).intValue()).isEqualTo(expectedRemaining);
        }
        TranslatedRefusal locked = Refusals.of(ErrorCode.OTP_LOCKED, engine.challenges.verify(issued.challengeId(), wrong));
        assertThat(Instant.parse((String) locked.details().get("lockedUntil")))
                .isEqualTo(AuthEngine.START.plus(Duration.ofMinutes(15)));

        // The correct code no longer works, a new request is refused, and the lock is mirrored (key only).
        Refusals.of(ErrorCode.OTP_LOCKED, engine.challenges.verify(issued.challengeId(), real));
        Refusals.of(ErrorCode.OTP_LOCKED, engine.challenges.issue(engine.command(phone)));
        String contactKey = engine.hasher.normalize(phone, null, null, null).orElseThrow().key();
        assertThat(engine.mirror.contactKeys).containsExactly(contactKey);
        assertThat(engine.redis.hasKey(ChallengeKeys.challenge(contactKey)).block()).isFalse();
    }

    @Test
    @DisplayName("R3 · the lock holds at 14:59 and is released at 15:01")
    void lockExpiry() {
        String phone = AuthEngine.randomPhone();
        var issued = engine.issue(phone);
        for (int i = 0; i < 5; i++) {
            Refusals.of(engine.challenges.verify(issued.challengeId(), "999999x"));
        }
        engine.clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        Refusals.of(ErrorCode.OTP_LOCKED, engine.challenges.issue(engine.command(phone)));
        engine.clock.advance(Duration.ofSeconds(2));
        var again = engine.issue(phone);
        assertThat(engine.challenges.verify(again.challengeId(), engine.codeFor(phone)).block()).isNotNull();
    }

    @Test
    @DisplayName("R3 · 50 parallel wrong guesses consume exactly five attempts, then everything is OTP_LOCKED")
    void parallelWrongGuesses() {
        for (int round = 0; round < 3; round++) {
            String phone = AuthEngine.randomPhone();
            var issued = engine.issue(phone);
            String real = engine.codeFor(phone);

            AtomicInteger invalid = new AtomicInteger();
            AtomicInteger locked = new AtomicInteger();
            Set<Integer> remaining = new HashSet<>();
            Flux.range(0, 50)
                    .flatMap(i -> engine.challenges.verify(issued.challengeId(), "%06d".formatted(100_000 + i))
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(TranslatedRefusal.class, refusal -> {
                                if (refusal.errorCode() == ErrorCode.OTP_INVALID) {
                                    invalid.incrementAndGet();
                                    synchronized (remaining) {
                                        remaining.add(((Number) refusal.details().get("attemptsRemaining")).intValue());
                                    }
                                } else if (refusal.errorCode() == ErrorCode.OTP_LOCKED) {
                                    locked.incrementAndGet();
                                }
                                return reactor.core.publisher.Mono.empty();
                            }), 50)
                    .blockLast();

            // 100000..100049 never contains the real code unless it happens to; skip that vanishing case.
            if (Integer.parseInt(real) >= 100_000 && Integer.parseInt(real) < 100_050) {
                continue;
            }
            assertThat(invalid.get()).as("attempts evaluated before the lock").isEqualTo(4);
            assertThat(locked.get()).isEqualTo(46);
            assertThat(remaining).containsExactlyInAnyOrder(1, 2, 3, 4);
            Refusals.of(ErrorCode.OTP_LOCKED, engine.challenges.verify(issued.challengeId(), real));
        }
    }

    @Test
    @DisplayName("R3 · of 20 parallel correct submissions exactly one succeeds")
    void correctCodeWorksOnce() {
        String phone = AuthEngine.randomPhone();
        var issued = engine.issue(phone);
        String code = engine.codeFor(phone);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger expired = new AtomicInteger();
        Flux.range(0, 20)
                .flatMap(i -> engine.challenges.verify(issued.challengeId(), code)
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(v -> successes.incrementAndGet())
                        .onErrorResume(TranslatedRefusal.class, refusal -> {
                            if (refusal.errorCode() == ErrorCode.OTP_EXPIRED) {
                                expired.incrementAndGet();
                            }
                            return reactor.core.publisher.Mono.empty();
                        }), 20)
                .blockLast();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(expired.get()).isEqualTo(19);
    }

    @Test
    @DisplayName("R3 · success deletes the code, the attempts and the cooldown")
    void successClearsState() {
        String phone = AuthEngine.randomPhone();
        var issued = engine.issue(phone);
        Refusals.of(ErrorCode.OTP_INVALID, engine.challenges.verify(issued.challengeId(), "abcdef"));
        engine.challenges.verify(issued.challengeId(), engine.codeFor(phone)).block();
        String contactKey = engine.hasher.normalize(phone, null, null, null).orElseThrow().key();
        for (String key : List.of(ChallengeKeys.challenge(contactKey), ChallengeKeys.attempts(contactKey),
                ChallengeKeys.cooldown(contactKey), ChallengeKeys.challengeId(issued.challengeId()))) {
            assertThat(engine.redis.hasKey(key).block()).as(key.substring(0, 6)).isFalse();
        }
    }

    @Test
    @DisplayName("R2 · a provider failure deletes the challenge, releases the cooldown and is refused retryable")
    void deliveryFailure() {
        String phone = AuthEngine.randomPhone();
        var failing = new AuthEngine(List.of(AuthEngine.failingProvider()), null);
        TranslatedRefusal refusal = Refusals.of(ErrorCode.OTP_DELIVERY_FAILED, failing.challenges.issue(failing.command(phone)));
        assertThat(refusal.retryable()).isTrue();

        String contactKey = failing.hasher.normalize(phone, null, null, null).orElseThrow().key();
        for (String key : List.of(ChallengeKeys.challenge(contactKey), ChallengeKeys.attempts(contactKey),
                ChallengeKeys.cooldown(contactKey))) {
            assertThat(failing.redis.hasKey(key).block()).isFalse();
        }
        // The person can ask again at once.
        assertThat(engine.issue(phone)).isNotNull();
    }

    @Test
    @DisplayName("R1/R2 · invalid contacts are refused before any state is written or message sent")
    void invalidContacts() {
        for (String bad : List.of("not a contact", "+260211123456", "+81312345678", "a@b", "12345", "")) {
            Refusals.of(ErrorCode.CONTACT_INVALID, engine.challenges.issue(engine.command(bad)));
        }
        assertThat(engine.captured.all()).isEmpty();
    }

    @Test
    @DisplayName("R2 · with no channel enabled the request is refused NOTIFICATION_CHANNEL_UNAVAILABLE and nothing is stored")
    void channelUnavailable() {
        var none = new AuthEngine(List.of(), null);
        String phone = AuthEngine.randomPhone();
        Refusals.of(ErrorCode.NOTIFICATION_CHANNEL_UNAVAILABLE, none.challenges.issue(none.command(phone)));
        String contactKey = none.hasher.normalize(phone, null, null, null).orElseThrow().key();
        assertThat(none.redis.hasKey(ChallengeKeys.cooldown(contactKey)).block()).isFalse();
    }

    @Test
    @DisplayName("R1 · no Redis key and no stored value contains a raw contact")
    void noRawContactInRedis() {
        String phone = AuthEngine.randomPhone();
        String email = AuthEngine.randomEmail();
        var a = engine.issue(phone);
        var b = engine.issue(email);
        var verified = engine.challenges.verify(a.challengeId(), engine.codeFor(phone)).block();
        String proof = engine.proofs.create(verified.contactKey(), verified.type(), verified.valueEncrypted(),
                verified.valueMasked()).block();
        engine.proofs.markConsumed(proof).block();
        engine.handles.issue("account-1", "myticketzm-web").block();

        String phoneDigits = phone.substring(1);
        String emailLocal = email.substring(0, email.indexOf('@'));
        for (String key : engine.allKeys()) {
            assertThat(key).doesNotContain(phoneDigits).doesNotContain(emailLocal).doesNotContain("@example.com");
            if (key.startsWith("ch:") || key.startsWith("proof:") || key.startsWith("lim:")) {
                // Values of the challenge records too (hash fields), other than the AES ciphertext.
                var type = engine.redis.type(key).block();
                if (type != null && "hash".equals(type.code())) {
                    engine.redis.opsForHash().values(key).collectList().block().forEach(value ->
                            assertThat(String.valueOf(value)).doesNotContain(phoneDigits).doesNotContain(emailLocal));
                }
            }
        }
        assertThat(b.maskedContact()).doesNotContain(emailLocal);
    }

    @Test
    @DisplayName("R4 · no log line at any level contains the code, the number or the email across issue, wrong guess, verify, proof and handle")
    void nothingSensitiveIsLogged() {
        String phone = AuthEngine.randomPhone();
        String email = AuthEngine.randomEmail();
        try (com.pml.identity.auth.delivery.LogWatch logs = new com.pml.identity.auth.delivery.LogWatch()) {
            var a = engine.issue(phone);
            var b = engine.issue(email);
            String phoneCode = engine.codeFor(phone);
            String emailCode = engine.codeFor(email);
            Refusals.of(engine.challenges.verify(a.challengeId(), phoneCode.equals("123456") ? "654321" : "123456"));
            var verified = engine.challenges.verify(a.challengeId(), phoneCode).block();
            engine.challenges.verify(b.challengeId(), emailCode).block();
            String proof = engine.proofs.create(verified.contactKey(), verified.type(), verified.valueEncrypted(),
                    verified.valueMasked()).block();
            engine.proofs.markConsumed(proof).block();
            engine.handles.issue("account-1", "myticketzm-web").block();
            Refusals.of(engine.challenges.issue(engine.command("+81312345678")));
            engine.issue(phone);
            String newPhoneCode = engine.codeFor(phone);

            logs.assertNoneContains(phoneCode, emailCode, newPhoneCode, phone, phone.substring(1), phone.substring(4), email,
                    email.substring(0, email.indexOf('@')), proof);
        }
    }
}
