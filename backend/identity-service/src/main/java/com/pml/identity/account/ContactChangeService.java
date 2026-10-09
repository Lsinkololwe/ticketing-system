package com.pml.identity.account;

import com.pml.identity.auth.challenge.ChallengeService;
import com.pml.identity.auth.proof.ProofService;
import com.pml.identity.config.IdentityChallengeProperties;
import com.pml.identity.config.IdentityContactProperties;
import com.pml.identity.config.IdentityLimitsProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.security.ContactCrypto;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.workflow.contactchange.ContactChangeProcess;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflow.ChangeCommand;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflow.Status;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adding, changing and removing a contact, and moving the primary, for the signed-in buyer (ET-IDN-004 R3, R5).
 *
 * <p>Decided for implementation on 2026-10-04 (specs/FINDINGS.md F-044):</p>
 * <ul>
 *   <li>a change, a removal and a primary switch are authorised by a fresh code sent to the account's
 *       CURRENT primary contact; there is no support-recovery path, an account with no verified contact
 *       able to receive a code is refused with {@code NO_VERIFIED_CONTACT};</li>
 *   <li>a released contact is quarantined (identity.contact.quarantine) before another account may claim it;</li>
 *   <li>the last verified contact cannot be removed; at most one contact is primary.</li>
 * </ul>
 *
 * <p>Every operation acts on the account the caller passes (the token's own account); there is no account
 * argument anywhere above this class. A contact id that is not this account's is "unknown", exactly as one
 * that does not exist. Codes are issued and checked by {@link ChallengeService}, so every rate limit, lock
 * and attempt budget of sign-in applies. No raw contact is returned: only masked values.</p>
 */
@Service
public class ContactChangeService {

    private static final String OP_PREFIX = "contactop:";

    /** A code was sent. */
    public record ContactCodeSent(String challengeId, ContactType contactType, String maskedContact,
                                  long expiresInSeconds, long resendAfterSeconds) {
    }

    public record ContactChangeRequested(String changeId, ContactCodeSent newContact, ContactCodeSent currentContact,
                                         Instant expiresAt) {
    }

    /** {@code status}: COMPLETED, or APPLYING when the change is still finishing in the background. */
    public record ContactChangeResult(String changeId, ContactChangeKind kind, String status, List<Contact> contacts) {
    }

    public record PendingContactChange(String changeId, ContactChangeKind kind, String newContactMasked, Instant expiresAt,
                                       boolean currentContactVerified, int attemptsRemaining) {
    }

    public record MyContacts(List<Contact> contacts, PendingContactChange pendingChange) {
    }

    private final org.springframework.data.mongodb.core.ReactiveMongoTemplate template;
    private final ChallengeService challenges;
    private final ProofService proofs;
    private final ContactChangeProcess process;
    private final ContactHasher hasher;
    private final ContactCrypto crypto;
    private final ReactiveStringRedisTemplate redis;
    private final IdentityChallengeProperties challengeProperties;
    private final IdentityLimitsProperties limits;
    private final IdentityContactProperties properties;
    private final ContactService contacts;
    private final java.time.Clock clock;

    public ContactChangeService(org.springframework.data.mongodb.core.ReactiveMongoTemplate template,
                                ChallengeService challenges, ProofService proofs, ContactChangeProcess process,
                                ContactHasher hasher, ContactCrypto crypto, ReactiveStringRedisTemplate redis,
                                IdentityChallengeProperties challengeProperties, IdentityLimitsProperties limits,
                                IdentityContactProperties properties, ContactService contacts, java.time.Clock clock) {
        this.template = template;
        this.challenges = challenges;
        this.proofs = proofs;
        this.process = process;
        this.hasher = hasher;
        this.crypto = crypto;
        this.redis = redis;
        this.challengeProperties = challengeProperties;
        this.limits = limits;
        this.properties = properties;
        this.contacts = contacts;
        this.clock = clock;
    }

    // ---- reads ---------------------------------------------------------------------------------------

    public Mono<MyContacts> myContacts(String accountId) {
        return template.findById(accountId, User.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN, "account")))
                .flatMap(account -> contacts.contactsOf(accountId).collectList().flatMap(list -> {
                    if (account.getPendingKind() != PendingKind.CHANGING) {
                        return Mono.just(new MyContacts(list, null));
                    }
                    return process.status(accountId).map(status -> new MyContacts(list, status.map(ContactChangeService::pending).orElse(null)));
                }));
    }

    private static PendingContactChange pending(Status status) {
        return new PendingContactChange(status.changeId(), status.kind(), status.newMasked(),
                Instant.ofEpochMilli(status.expiresAtMillis()), status.authorised(), status.attemptsRemaining());
    }

    // ---- add -----------------------------------------------------------------------------------------

    public Mono<ContactCodeSent> requestAdd(String accountId, ContactType type, String value, String regionHint) {
        return guard(accountId).flatMap(account -> issueTracked(accountId, "ADD", "", value, type, regionHint, SEND_TTL));
    }

    public Mono<ContactChangeResult> confirmAdd(String accountId, String challengeId, String code) {
        return guard(accountId).then(Mono.defer(() -> challenges.verify(challengeId, code)))
                .flatMap(verified -> proofOf(verified).flatMap(proofId -> {
                    ChangeCommand command = new ChangeCommand(UUID.randomUUID().toString(), accountId, ContactChangeKind.ADD, null,
                            verified.type(), verified.contactKey(), verified.valueMasked(), proofId, null, null);
                    return run(command);
                }));
    }

    // ---- change --------------------------------------------------------------------------------------

    public Mono<ContactChangeRequested> requestChange(String accountId, String contactId, ContactType type, String value,
                                                      String regionHint) {
        return guard(accountId).flatMap(account -> owned(accountId, contactId).flatMap(target -> {
            if (target.getType() != type) {
                return Mono.<ContactChangeRequested>error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                        "a contact is changed to another of the same type; add and remove to switch type"));
            }
            ContactHasher.Normalized normalized = hasher.normalize(value, type, regionHint, limits.getAllowedCountries())
                    .orElseThrow(() -> new TranslatedRefusal(ErrorCode.CONTACT_INVALID, "contact not acceptable"));
            if (normalized.key().equals(target.getValueHash())) {
                return Mono.<ContactChangeRequested>error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                        "that is already this contact"));
            }
            String changeId = UUID.randomUUID().toString();
            return primaryOf(accountId).flatMap(primary -> issueTracked(accountId, "CHANGE_NEW", changeId, value, type, regionHint, CHANGE_SEND_TTL)
                    .flatMap(toNew -> issueToContact(accountId, primary, "CHANGE_CURRENT", changeId, CHANGE_SEND_TTL)
                            .flatMap(toCurrent -> {
                                ChangeCommand command = new ChangeCommand(changeId, accountId, ContactChangeKind.CHANGE,
                                        contactId, type, normalized.key(), normalized.masked(), null,
                                        toNew.challengeId(), toCurrent.challengeId());
                                return process.start(command).then(process.awaitOpen(accountId, Duration.ofSeconds(10))).thenReturn(new ContactChangeRequested(changeId, toNew, toCurrent,
                                        clock.instant().plus(com.pml.identity.workflow.contactchange.ContactChangeRules.EXPIRY)));
                            })));
        }));
    }

    public Mono<ContactChangeResult> confirmChange(String accountId, String changeId, String newCode, String currentCode) {
        return guard(accountId, true).then(Mono.defer(() -> process.status(accountId)))
                .flatMap(maybe -> maybe.filter(s -> s.changeId().equals(changeId) && s.kind() == ContactChangeKind.CHANGE)
                        .map(Mono::just).orElseGet(() -> Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "no such change"))))
                .flatMap(status -> {
                    if (status.attemptsRemaining() <= 0) {
                        return Mono.<ContactChangeResult>error(new TranslatedRefusal(ErrorCode.OTP_ATTEMPTS_EXHAUSTED, "too many wrong codes"));
                    }
                    Mono<Void> authorised = status.authorised() ? Mono.empty() : primaryOf(accountId).flatMap(primary -> {
                        if (currentCode == null || currentCode.isBlank()) {
                            return Mono.<Void>error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                                    "the code sent to your current contact is required"));
                        }
                        return checked(accountId, status.currentChallengeId(), currentCode, primary.getValueHash(), primary.getType())
                                .then(process.authorise(accountId));
                    });
                    return authorised
                            .then(Mono.defer(() -> status.newAccepted() ? Mono.<Void>empty()
                                    : checked(accountId, status.newChallengeId(), newCode, status.newContactKey(), null)
                                    .flatMap(verified -> proofOf(verified))
                                    .flatMap(proofId -> process.acceptNew(accountId, proofId))))
                            .then(Mono.defer(() -> finish(accountId, changeId, ContactChangeKind.CHANGE)));
                });
    }

    public Mono<Boolean> cancelChange(String accountId, String changeId) {
        return guard(accountId, true).then(process.status(accountId)).flatMap(maybe -> maybe
                .filter(s -> s.changeId().equals(changeId))
                .map(s -> process.cancel(accountId).thenReturn(true))
                .orElseGet(() -> Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "no such change"))));
    }

    // ---- remove and primary: a code to the current primary authorises them ------------------------------

    public Mono<ContactCodeSent> requestRemoval(String accountId, String contactId) {
        return guard(accountId).flatMap(account -> contacts.contactsOf(accountId).collectList().flatMap(all -> {
            var refusal = ContactRules.removalRefusal(all, contactId);
            if (refusal.isPresent()) {
                return Mono.<ContactCodeSent>error(new TranslatedRefusal(refusal.get(), "contact " + contactId));
            }
            return stepUp(accountId, all, ContactChangeKind.REMOVE, contactId);
        }));
    }

    public Mono<ContactChangeResult> confirmRemoval(String accountId, String challengeId, String code) {
        return confirmStepUp(accountId, challengeId, code, ContactChangeKind.REMOVE, null);
    }

    public Mono<ContactCodeSent> requestPrimary(String accountId, String contactId) {
        return guard(accountId).flatMap(account -> contacts.contactsOf(accountId).collectList().flatMap(all -> {
            Contact target = ContactRules.active(all).stream().filter(c -> c.getId().equals(contactId)).findFirst().orElse(null);
            if (target == null) {
                return Mono.<ContactCodeSent>error(new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "contact " + contactId));
            }
            return stepUp(accountId, all, ContactChangeKind.PRIMARY, contactId);
        }));
    }

    public Mono<ContactChangeResult> setPrimary(String accountId, String contactId, String challengeId, String code) {
        return confirmStepUp(accountId, challengeId, code, ContactChangeKind.PRIMARY, contactId);
    }

    private Mono<ContactCodeSent> stepUp(String accountId, List<Contact> all, ContactChangeKind kind, String contactId) {
        return ContactRules.primary(all).map(Mono::just).orElseGet(() -> Mono.error(noVerifiedContact()))
                .flatMap(primary -> issueToContact(accountId, primary, kind.name(), contactId, SEND_TTL).flatMap(sent -> redis.opsForHash()
                        .putAll(OP_PREFIX + sent.challengeId(), Map.of("accountId", accountId, "kind", kind.name(), "contactId", contactId))
                        .then(redis.expire(OP_PREFIX + sent.challengeId(), challengeProperties.getTtl().plus(SEND_TTL)))
                        .thenReturn(sent)));
    }

    private Mono<ContactChangeResult> confirmStepUp(String accountId, String challengeId, String code, ContactChangeKind kind,
                                                    String expectedContactId) {
        return guard(accountId).then(Mono.defer(() -> redis.<String, String>opsForHash().entries(OP_PREFIX + String.valueOf(challengeId))
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)))
                .flatMap(op -> {
                    if (op.isEmpty() || !accountId.equals(op.get("accountId")) || !kind.name().equals(op.get("kind"))
                            || (expectedContactId != null && !expectedContactId.equals(op.get("contactId")))) {
                        return Mono.<ContactChangeResult>error(new TranslatedRefusal(ErrorCode.OTP_EXPIRED, "no live code for this request"));
                    }
                    String contactId = op.get("contactId");
                    return primaryOf(accountId)
                            .flatMap(primary -> checked(accountId, challengeId, code, primary.getValueHash(), primary.getType()))
                            .then(redis.delete(OP_PREFIX + challengeId))
                            .then(Mono.defer(() -> run(new ChangeCommand(UUID.randomUUID().toString(), accountId, kind, contactId,
                                    null, null, null, null, null, null))));
                });
    }

    // ---- common --------------------------------------------------------------------------------------

    private Mono<ContactChangeResult> run(ChangeCommand command) {
        return process.start(command).then(finish(command.accountId(), command.changeId(), command.kind()));
    }

    private Mono<ContactChangeResult> finish(String accountId, String changeId, ContactChangeKind kind) {
        return process.await(accountId, properties.getChangeWait()).flatMap(outcome -> contacts.contactsOf(accountId).collectList()
                .map(list -> new ContactChangeResult(changeId, kind, outcome.isPresent() ? "COMPLETED" : "APPLYING", list)));
    }

    /** Verifies a code; a wrong or expired one counts against the open change. The contact must be the expected one. */
    private Mono<ChallengeService.Verified> checked(String accountId, String challengeId, String code, String expectedKey,
                                                    ContactType expectedType) {
        return challenges.verify(challengeId, code)
                .onErrorResume(error -> isWrongCode(error)
                        ? process.failedAttempt(accountId).then(Mono.<ChallengeService.Verified>error(error)) : Mono.error(error))
                .flatMap(verified -> verified.contactKey().equals(expectedKey) && (expectedType == null || expectedType == verified.type())
                        ? Mono.just(verified)
                        : Mono.<ChallengeService.Verified>error(new TranslatedRefusal(ErrorCode.PROOF_INVALID, "the code is for another contact")));
    }

    private static boolean isWrongCode(Throwable error) {
        return error instanceof TranslatedRefusal refusal
                && (refusal.errorCode() == ErrorCode.OTP_INVALID || refusal.errorCode() == ErrorCode.OTP_EXPIRED
                || refusal.errorCode() == ErrorCode.OTP_LOCKED);
    }

    private Mono<String> proofOf(ChallengeService.Verified verified) {
        return proofs.create(verified.contactKey(), verified.type(), verified.valueEncrypted(), verified.valueMasked())
                .flatMap(id -> proofs.markConsumed(id).thenReturn(id));
    }

    private Mono<ContactCodeSent> issue(String accountId, String value, ContactType type, String regionHint) {
        return challenges.issue(new ChallengeService.IssueCommand(value, type, regionHint, "account:" + accountId, null, null))
                .map(this::sent);
    }

    private Mono<ContactCodeSent> issueToContact(String accountId, Contact contact, String purpose, String ref, Duration ttl) {
        return crypto.decrypt(contact.getValueEncrypted())
                .flatMap(value -> issueTracked(accountId, purpose, ref, value, contact.getType(), null, ttl));
    }

    private ContactCodeSent sent(ChallengeService.Issued issued) {
        long resend = properties.getResendAfter() != null && !properties.getResendAfter().isZero()
                ? properties.getResendAfter().toSeconds() : issued.resendAfterSeconds();
        return new ContactCodeSent(issued.challengeId(), issued.contactType(), issued.maskedContact(),
                issued.expiresInSeconds(), resend);
    }

    // ---- resend ----------------------------------------------------------------------------------------

    private static final Duration SEND_TTL = Duration.ofHours(1);
    private static final Duration CHANGE_SEND_TTL = Duration.ofHours(49);
    private static final String SEND = "csend:";

    /** Issues a code and remembers (encrypted) what is needed to send it again; the raw contact is never stored. */
    private Mono<ContactCodeSent> issueTracked(String accountId, String purpose, String ref, String value, ContactType type,
                                               String regionHint, Duration ttl) {
        return issue(accountId, value, type, regionHint).flatMap(sent -> crypto.encrypt(value).flatMap(encrypted -> {
            Map<String, String> fields = new java.util.HashMap<>();
            fields.put("accountId", accountId);
            fields.put("purpose", purpose);
            fields.put("ref", ref);
            fields.put("type", type.name());
            fields.put("enc", encrypted);
            fields.put("region", regionHint == null ? "" : regionHint);
            fields.put("sentAt", String.valueOf(clock.millis()));
            return redis.opsForHash().putAll(SEND + sent.challengeId(), fields)
                    .then(redis.expire(SEND + sent.challengeId(), ttl)).thenReturn(sent);
        }));
    }

    /**
     * Sends the code of a pending add, change, removal or primary switch again, once
     * {@code identity.contact.resend-after} has passed since it was last sent. The new code replaces the old one
     * (and has a new challenge id); the open change keeps its overall expiry and its attempt budget.
     *
     * @param challengeId the challenge of an add, removal or primary switch; null for a change
     * @param changeId    the open change; with {@code target} NEW or CURRENT
     */
    public Mono<ContactCodeSent> resend(String accountId, String challengeId, String changeId, String target) {
        boolean forChange = changeId != null && !changeId.isBlank();
        if (!forChange && (challengeId == null || challengeId.isBlank())) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "name a challengeId or a changeId and a target"));
        }
        if (forChange && !"NEW".equals(target) && !"CURRENT".equals(target)) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "target is NEW or CURRENT"));
        }
        return guard(accountId, true).then(Mono.defer(() -> forChange
                ? process.status(accountId).flatMap(open -> open.filter(st -> st.changeId().equals(changeId) && st.kind() == ContactChangeKind.CHANGE)
                        .map(st -> {
                            if ("NEW".equals(target) ? st.newAccepted() : st.authorised()) {
                                return Mono.<String>error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "that code was already accepted"));
                            }
                            return Mono.just("NEW".equals(target) ? st.newChallengeId() : st.currentChallengeId());
                        })
                        .orElseGet(() -> Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "no such change"))))
                : Mono.just(challengeId)))
                .flatMap(oldId -> redis.<String, String>opsForHash().entries(SEND + oldId)
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                        .flatMap(send -> {
                            if (send.isEmpty() || !accountId.equals(send.get("accountId"))
                                    || (forChange != send.get("purpose").startsWith("CHANGE_"))) {
                                return Mono.<ContactCodeSent>error(new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "no code to send again"));
                            }
                            long wait = ContactRules.resendWaitSeconds(Instant.ofEpochMilli(Long.parseLong(send.get("sentAt"))),
                                    properties.getResendAfter(), clock.instant());
                            if (wait > 0) {
                                return Mono.<ContactCodeSent>error(new TranslatedRefusal(ErrorCode.OTP_RATE_LIMITED,
                                        "a code was sent recently", Map.of("retryAfterSeconds", wait)));
                            }
                            String purpose = send.get("purpose");
                            Duration ttl = purpose.startsWith("CHANGE_") ? CHANGE_SEND_TTL : SEND_TTL;
                            return crypto.decrypt(send.get("enc")).flatMap(value -> issueTracked(accountId, purpose, send.get("ref"),
                                            value, ContactType.valueOf(send.get("type")),
                                            send.get("region").isEmpty() ? null : send.get("region"), ttl))
                                    .flatMap(fresh -> redis.delete(SEND + oldId)
                                            .then(rekey(oldId, fresh.challengeId(), purpose))
                                            .then(forChange ? process.codeResent(accountId, target, fresh.challengeId()) : Mono.empty())
                                            .thenReturn(fresh));
                        }));
    }

    /** A removal or primary switch is bound to its challenge id; the replacement code carries the binding over. */
    private Mono<Void> rekey(String oldId, String newId, String purpose) {
        if (!"REMOVE".equals(purpose) && !"PRIMARY".equals(purpose)) {
            return Mono.empty();
        }
        return redis.<String, String>opsForHash().entries(OP_PREFIX + oldId).collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(op -> op.isEmpty() ? Mono.<Void>empty()
                        : redis.opsForHash().putAll(OP_PREFIX + newId, op)
                        .then(redis.expire(OP_PREFIX + newId, challengeProperties.getTtl().plus(CHANGE_SEND_TTL)))
                        .then(redis.delete(OP_PREFIX + oldId)).then());
    }

    /** The contact must be this account's, active; another account's contact is "unknown", like a missing one. */
    private Mono<Contact> owned(String accountId, String contactId) {
        return contacts.contactsOf(accountId).filter(c -> c.getId().equals(contactId)).next()
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "no such contact")));
    }

    private Mono<Contact> primaryOf(String accountId) {
        return contacts.contactsOf(accountId).collectList()
                .flatMap(all -> ContactRules.primary(all).map(Mono::just).orElseGet(() -> Mono.error(noVerifiedContact())));
    }

    private static TranslatedRefusal noVerifiedContact() {
        return new TranslatedRefusal(ErrorCode.NO_VERIFIED_CONTACT,
                "the account has no verified contact that can receive a code");
    }

    private Mono<User> guard(String accountId) {
        return guard(accountId, false);
    }

    /** The account must be an ACTIVE buyer account with no other change open (unless {@code allowOpen}). */
    private Mono<User> guard(String accountId, boolean allowOpen) {
        return template.findById(accountId, User.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN, "account")))
                .flatMap(account -> {
                    if (AccountService.STAFF_SYNC.equals(account.getCreatedVia()) || AccountService.STAFF_ADMIN.equals(account.getCreatedVia())) {
                        return Mono.<User>error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED, "buyer accounts only"));
                    }
                    if (AccountStates.of(account) != AccountState.ACTIVE) {
                        return Mono.<User>error(new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE, "account is not active",
                                Map.of("currentStatus", AccountStates.of(account).name())));
                    }
                    if (account.getPendingKind() == PendingKind.MERGING || account.getPendingKind() == PendingKind.DELETION_REQUESTED) {
                        return Mono.<User>error(new TranslatedRefusal(ErrorCode.ACCOUNT_MERGING, "account is changing"));
                    }
                    if (!allowOpen && account.getPendingKind() == PendingKind.CHANGING) {
                        return Mono.<User>error(new TranslatedRefusal(ErrorCode.CONTACT_CHANGE_IN_PROGRESS, "a change is open"));
                    }
                    return Mono.just(account);
                });
    }
}
