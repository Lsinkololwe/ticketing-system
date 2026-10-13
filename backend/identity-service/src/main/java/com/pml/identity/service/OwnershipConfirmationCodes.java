package com.pml.identity.service;

import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.repository.ContactRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.security.ContactCrypto;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * The second factor on an ownership transfer: a one-time code sent to a contact the nominee has
 * verified, through the same delivery orchestrator the sign-in codes use.
 *
 * <p>Holding the transfer link and a session is not enough to take over an organization: a session
 * can be stolen and a link forwarded. The nominee also has to show they hold the WhatsApp number or
 * the email the account verified, which is the factor the platform's own sign-in rests on. The
 * contact is read from the account's verified contacts (the same store sign-in writes), so an
 * account that was opened with a code and never had a number typed into a profile works. WhatsApp
 * is preferred; the primary contact breaks a tie, and an email is used when there is no WhatsApp
 * number. A nominee with no verified contact is told so and nothing is sent.
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

    /** Where a code goes: the contact's type and its normalised value (never logged). */
    private record Target(ContactType type, String value) {
        @Override
        public String toString() {
            return "Target[" + type + "]";
        }
    }

    private final ReactiveRedisTemplate<String, String> redis;
    private final UserRepository users;
    private final ContactRepository contacts;
    private final ContactCrypto crypto;
    private final DeliveryOrchestrator delivery;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OwnershipConfirmationCodes(ReactiveRedisTemplate<String, String> redis, UserRepository users,
                                      ContactRepository contacts, ContactCrypto crypto,
                                      DeliveryOrchestrator delivery, Clock clock) {
        this.redis = redis;
        this.users = users;
        this.contacts = contacts;
        this.crypto = crypto;
        this.delivery = delivery;
        this.clock = clock;
    }

    /** Sends a fresh code to the nominee's verified contact. Only the nominee of a pending transfer may ask. */
    public Mono<Void> issue(OwnershipTransferRequest transfer, String callerId) {
        return Mono.defer(() -> {
            refuseUnlessNomineeOfPending(transfer, callerId);
            String scope = scope(transfer, callerId);
            return redis.opsForValue().setIfAbsent(COOLDOWN_KEY + scope, "1", COOLDOWN)
                    .flatMap(first -> first
                            ? deliverableContact(callerId)
                            : Mono.error(new TranslatedRefusal(ErrorCode.OTP_COOLDOWN_ACTIVE,
                                    "a code was sent less than a minute ago")))
                    .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.PHONE_NUMBER_INVALID,
                            "the nominee has no verified contact to send a code to")))
                    .flatMap(target -> {
                        String code = "%06d".formatted(random.nextInt(1_000_000));
                        return redis.opsForValue().set(CODE_KEY + scope, digest(scope, code), LIFETIME)
                                .then(redis.delete(ATTEMPTS_KEY + scope))
                                .then(delivery.deliver(target.type(), target.value(), code, null).thenReturn(true));
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
     * The nominee's verified, unreleased contact to send the code to: WhatsApp before email, the
     * primary one first. The nominee id may be the account id or the Keycloak user id, as a token's
     * subject can be either. Empty when the account holds no verified contact.
     */
    private Mono<Target> deliverableContact(String nomineeId) {
        return users.findById(nomineeId)
                .switchIfEmpty(Mono.defer(() -> users.findByKeycloakUserId(nomineeId)))
                .flatMapMany(account -> contacts.findByAccountIdAndReleasedAtIsNull(account.getId()))
                .filter(contact -> contact.getVerifiedAt() != null && contact.getType() != null)
                .collectList()
                .flatMap(held -> {
                    List<Contact> ordered = held.stream()
                            .sorted(Comparator
                                    .comparing((Contact c) -> c.getType() == ContactType.WHATSAPP ? 0 : 1)
                                    .thenComparing(c -> c.isPrimary() ? 0 : 1))
                            .toList();
                    return ordered.isEmpty() ? Mono.<Target>empty()
                            : crypto.decrypt(ordered.get(0).getValueEncrypted())
                            .map(value -> new Target(ordered.get(0).getType(), value));
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
