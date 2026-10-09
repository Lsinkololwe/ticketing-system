package com.pml.identity.service;

import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.domain.model.User;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.repository.UserRepository;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

/**
 * The second factor on an ownership transfer: a one-time code sent to the nominee's verified phone (WhatsApp, through the login orchestrator).
 *
 * <p>Holding the transfer link and a session is not enough to take over an organization — a
 * session can be stolen and a link forwarded. The nominee also has to show they hold the phone the
 * account verified, which is the factor the platform's own login rests on.
 *
 * <h2>How a code behaves</h2>
 * <ul>
 *   <li>It is scoped to one transfer and one nominee, and kept apart from login codes, so neither
 *       can stand in for the other.</li>
 *   <li>Only its SHA-256 digest is stored, for {@link #LIFETIME}; it is used once.</li>
 *   <li>{@link #MAX_ATTEMPTS} wrong guesses lock it until a new one is requested, and a new one can be
 *       requested once per {@link #COOLDOWN} — six digits cannot be walked in either direction.</li>
 * </ul>
 */
@Slf4j
@Service
public class OwnershipConfirmationCodes {

    static final Duration LIFETIME = Duration.ofMinutes(10);
    static final Duration COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 3;

    private static final String CODE_KEY = "otp:ownership:code:";
    private static final String ATTEMPTS_KEY = "otp:ownership:attempts:";
    private static final String COOLDOWN_KEY = "otp:ownership:cooldown:";

    private final ReactiveRedisTemplate<String, String> redis;
    private final UserRepository users;
    private final DeliveryOrchestrator delivery;
    private final ContactHasher hasher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OwnershipConfirmationCodes(ReactiveRedisTemplate<String, String> redis, UserRepository users,
                                      DeliveryOrchestrator delivery, ContactHasher hasher, Clock clock) {
        this.redis = redis;
        this.users = users;
        this.delivery = delivery;
        this.hasher = hasher;
        this.clock = clock;
    }

    /** Sends a fresh code to the nominee's verified phone. Only the nominee of a pending transfer may ask. */
    public Mono<Void> issue(OwnershipTransferRequest transfer, String callerId) {
        return Mono.defer(() -> {
            refuseUnlessNomineeOfPending(transfer, callerId);
            String scope = scope(transfer, callerId);
            return redis.opsForValue().setIfAbsent(COOLDOWN_KEY + scope, "1", COOLDOWN)
                    .flatMap(first -> first
                            ? users.findById(callerId)
                            : Mono.error(new TranslatedRefusal(ErrorCode.OTP_COOLDOWN_ACTIVE,
                                    "a code was sent less than a minute ago")))
                    .filter(nominee -> nominee.isPhoneVerified() && nominee.getPhoneNumber() != null)
                    .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.PHONE_NUMBER_INVALID,
                            "the nominee has no verified phone number to send a code to")))
                    .flatMap(nominee -> {
                        String code = "%06d".formatted(random.nextInt(1_000_000));
                        return redis.opsForValue().set(CODE_KEY + scope, digest(scope, code), LIFETIME)
                                .then(redis.delete(ATTEMPTS_KEY + scope))
                                .then(send(nominee, code));
                    })
                    .doOnSuccess(sent -> log.info("Ownership confirmation code sent for transfer {}", transfer.getId()))
                    .then();
        });
    }

    /**
     * Completes when {@code code} is the nominee's current code, which it then consumes; otherwise
     * fails with a refusal that says whether to retry or to ask for a new code.
     */
    public Mono<Void> verify(OwnershipTransferRequest transfer, String callerId, String code) {
        return Mono.defer(() -> {
            refuseUnlessNomineeOfPending(transfer, callerId);
            String scope = scope(transfer, callerId);
            String codeKey = CODE_KEY + scope;
            String attemptsKey = ATTEMPTS_KEY + scope;
            return redis.opsForValue().get(attemptsKey).map(Integer::parseInt).defaultIfEmpty(0)
                    .flatMap(attempts -> attempts >= MAX_ATTEMPTS
                            ? Mono.error(new TranslatedRefusal(ErrorCode.OTP_ATTEMPTS_EXHAUSTED,
                                    "too many wrong codes; request a new one"))
                            : redis.opsForValue().get(codeKey)
                                    .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.OTP_EXPIRED,
                                            "no current code; request a new one"))))
                    .flatMap(stored -> matches(stored, digest(scope, String.valueOf(code)))
                            // Deleting is what makes the code single-use: of two concurrent correct
                            // submissions, only the one whose delete removed the key succeeds.
                            ? redis.delete(codeKey, attemptsKey).flatMap(removed -> removed > 0
                                    ? Mono.<Void>empty()
                                    : Mono.error(new TranslatedRefusal(ErrorCode.OTP_EXPIRED, "the code was already used")))
                            : redis.opsForValue().increment(attemptsKey)
                                    .flatMap(count -> redis.expire(attemptsKey, LIFETIME))
                                    .then(Mono.error(new TranslatedRefusal(ErrorCode.OTP_INVALID,
                                            "the confirmation code is wrong"))));
        });
    }

    private void refuseUnlessNomineeOfPending(OwnershipTransferRequest transfer, String callerId) {
        if (callerId == null || !callerId.equals(transfer.getNewOwnerId())) {
            // Checked before any attempt is counted, so a stranger holding the link cannot lock the
            // nominee out by guessing.
            throw new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED, "only the nominee can confirm a transfer");
        }
        if (!transfer.isValid(clock.instant())) {
            throw new TranslatedRefusal(ErrorCode.TRANSFER_NOT_PENDING, "the transfer is no longer pending");
        }
    }

    /**
     * Sends through the same orchestrator as login codes (WhatsApp for a phone). The number is
     * normalised first; it is never put in a Redis key, a log line or an exception message.
     */
    private Mono<Boolean> send(User nominee, String code) {
        return Mono.defer(() -> {
            ContactHasher.Normalized phone = hasher.normalize(nominee.getPhoneNumber(), ContactType.WHATSAPP, null, null)
                    .orElseThrow(() -> new TranslatedRefusal(ErrorCode.PHONE_NUMBER_INVALID,
                            "the nominee's phone number is not a valid mobile number"));
            return delivery.deliver(ContactType.WHATSAPP, phone.value(), code, null).thenReturn(true);
        });
    }

    private static String scope(OwnershipTransferRequest transfer, String nomineeId) {
        return transfer.getId() + ":" + nomineeId;
    }

    private static String digest(String scope, String code) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((scope + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Constant-time, so the comparison's duration says nothing about how much of a guess was right. */
    private static boolean matches(String stored, String candidate) {
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),
                candidate.getBytes(StandardCharsets.US_ASCII));
    }
}
