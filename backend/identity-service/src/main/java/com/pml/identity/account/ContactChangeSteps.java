package com.pml.identity.account;

import com.pml.identity.auth.delivery.ContactNotice;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.config.IdentityContactProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.security.ContactCrypto;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.Outbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The MongoDB and Keycloak steps behind {@code ContactChangeWorkflow} (ET-IDN-004 R3, R5).
 *
 * <p>Every method may run twice, because the activity that calls it is retried: a second claim finds the
 * contact already this account's, a second commit finds its account event, a second Keycloak write
 * writes the same email. Nothing here undoes anything on failure; a change moves forward or is
 * abandoned before the claim (the marker is cleared and the account is as it was).</p>
 *
 * <p>The order the workflow runs them in is the safety argument: <b>claim</b> the new contact under the
 * unique index, <b>write Keycloak</b>, only then <b>release</b> the old contact in {@link #commit}. The
 * old contact keeps signing the person in until that last write.</p>
 */
@Service
public class ContactChangeSteps {

    private static final Logger log = LoggerFactory.getLogger(ContactChangeSteps.class);

    public static final String SOURCE = "CONTACT_CHANGE";

    /** The contact a claim produced or found, for the workflow to carry as an id. */
    public record Claimed(String contactId, boolean alreadyHeld) {
    }

    private final ReactiveMongoTemplate template;
    private final TransactionalOperator transaction;
    private final Outbox outbox;
    private final ProofLookup proofs;
    private final KeycloakAccountPort keycloak;
    private final KeycloakUserAdminPort sessions;
    private final ContactCrypto crypto;
    private final DeliveryOrchestrator delivery;
    private final IdentityContactProperties properties;
    private final Clock clock;

    public ContactChangeSteps(ReactiveMongoTemplate template, TransactionalOperator transaction, Outbox outbox,
                              ProofLookup proofs, KeycloakAccountPort keycloak, KeycloakUserAdminPort sessions,
                              ContactCrypto crypto, DeliveryOrchestrator delivery,
                              IdentityContactProperties properties, Clock clock) {
        this.template = template;
        this.transaction = transaction;
        this.outbox = outbox;
        this.proofs = proofs;
        this.keycloak = keycloak;
        this.sessions = sessions;
        this.crypto = crypto;
        this.delivery = delivery;
        this.properties = properties;
        this.clock = clock;
    }

    // ---- 1 · the marker -------------------------------------------------------------------------------

    /** Sets {@code pendingKind=CHANGING}; refuses an account that is not ACTIVE or is being merged or deleted. */
    public Mono<Void> begin(String accountId) {
        return template.findById(accountId, User.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN, "account " + accountId)))
                .flatMap(account -> {
                    AccountState state = AccountStates.of(account);
                    if (state != AccountState.ACTIVE) {
                        return Mono.<Void>error(new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE, "account " + accountId));
                    }
                    if (account.getPendingKind() == PendingKind.MERGING) {
                        return Mono.<Void>error(new TranslatedRefusal(ErrorCode.ACCOUNT_MERGING, "account " + accountId));
                    }
                    if (account.getPendingKind() == PendingKind.DELETION_REQUESTED) {
                        return Mono.<Void>error(new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE, "account " + accountId));
                    }
                    if (account.getPendingKind() == PendingKind.CHANGING) {
                        return Mono.<Void>empty();
                    }
                    Instant now = clock.instant();
                    return template.updateFirst(Query.query(Criteria.where("_id").is(accountId)
                                            .and("status").is(AccountState.ACTIVE).and("pendingKind").is(null)),
                                    new Update().set("pendingKind", PendingKind.CHANGING).set("pendingSince", now)
                                            .set("updatedAt", now), User.class)
                            .flatMap(result -> result.getModifiedCount() == 1 ? Mono.<Void>empty()
                                    : Mono.<Void>error(new TranslatedRefusal(ErrorCode.CONTACT_CHANGE_IN_PROGRESS,
                                    "account " + accountId)));
                });
    }

    /** Clears the marker; nothing else about the account changes. Safe to repeat. */
    public Mono<Void> clearMarker(String accountId) {
        return template.updateFirst(Query.query(Criteria.where("_id").is(accountId).and("pendingKind").is(PendingKind.CHANGING)),
                new Update().unset("pendingKind").unset("pendingSince").set("updatedAt", clock.instant()), User.class).then();
    }

    // ---- 2 · claim -----------------------------------------------------------------------------------

    /**
     * Claims the new contact for the account under the unique index. The contact is stored verified and
     * NOT primary; {@link #commit} decides primacy together with the release. A contact another account
     * holds, or released less than the quarantine ago by another account, is refused with
     * {@code CONTACT_ALREADY_CLAIMED}, and the account is untouched.
     */
    public Mono<Claimed> claim(String accountId, String changeId, String proofId, String contactKey, ContactType type) {
        String contactId = contactId(accountId, changeId, contactKey);
        return owner(type, contactKey).map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()).flatMap(found -> {
            if (found.isPresent()) {
                return found.get().getAccountId().equals(accountId)
                        ? Mono.just(new Claimed(found.get().getId(), true))
                        : Mono.<Claimed>error(taken());
            }
            return quarantinedByOther(accountId, type, contactKey)
                    .flatMap(blocked -> blocked ? Mono.<Claimed>error(taken()) : insert(accountId, contactId, proofId, contactKey, type));
        });
    }

    private Mono<Claimed> insert(String accountId, String contactId, String proofId, String contactKey, ContactType type) {
        return proofs.find(proofId)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.PROOF_INVALID, "the proof is unknown or has expired")))
                .flatMap(proof -> {
                    if (!contactKey.equals(proof.contactKey()) || type != proof.type()
                            || proof.valueEncrypted() == null || proof.valueEncrypted().isBlank()) {
                        return Mono.<Claimed>error(new TranslatedRefusal(ErrorCode.PROOF_INVALID, "the proof does not belong to this contact"));
                    }
                    Instant now = clock.instant();
                    Contact contact = Contact.builder()
                            .id(contactId).accountId(accountId).type(type).valueHash(contactKey)
                            .valueEncrypted(proof.valueEncrypted()).valueMasked(proof.valueMasked())
                            .verifiedAt(now).primary(false).source(SOURCE).createdAt(now).build();
                    return template.insert(contact).thenReturn(new Claimed(contactId, false))
                            .onErrorResume(DuplicateKeyException.class, race -> owner(type, contactKey)
                                    .flatMap(winner -> winner.getAccountId().equals(accountId)
                                            ? Mono.just(new Claimed(winner.getId(), true))
                                            : Mono.<Claimed>error(taken()))
                                    .switchIfEmpty(Mono.error(race)));
                });
    }

    private Mono<Boolean> quarantinedByOther(String accountId, ContactType type, String contactKey) {
        if (properties.getQuarantine() == null || properties.getQuarantine().isZero()) {
            return Mono.just(false);
        }
        Instant now = clock.instant();
        return template.find(Query.query(Criteria.where("type").is(type).and("valueHash").is(contactKey)
                                .and("releasedAt").ne(null).and("accountId").ne(accountId)), Contact.class)
                .any(released -> ContactRules.quarantined(released.getReleasedAt(), properties.getQuarantine(), now));
    }

    private static TranslatedRefusal taken() {
        // One answer for "another account holds it" and "another account held it recently": neither is for the caller to learn.
        return new TranslatedRefusal(ErrorCode.CONTACT_ALREADY_CLAIMED, "the contact cannot be claimed by this account");
    }

    private Mono<Contact> owner(ContactType type, String contactKey) {
        return template.findOne(Query.query(Criteria.where("type").is(type).and("valueHash").is(contactKey)
                .and("verifiedAt").exists(true).and("releasedAt").is(null)), Contact.class);
    }

    static String contactId(String accountId, String changeId, String contactKey) {
        return UUID.nameUUIDFromBytes((accountId + ":" + changeId + ":" + contactKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // ---- 3 · Keycloak --------------------------------------------------------------------------------

    /**
     * Writes the account's email to Keycloak as it will be once {@code excludeContactId} is released:
     * the account's verified email contact, or none. The username is not touched; the user is sent whole.
     */
    public Mono<Void> syncKeycloak(String accountId, String excludeContactId) {
        return template.findById(accountId, User.class)
                .flatMap(account -> account.getKeycloakUserId() == null || account.getKeycloakUserId().isBlank()
                        ? Mono.<Void>empty()
                        : contactsOf(accountId)
                        .map(all -> ContactRules.active(all).stream()
                                .filter(c -> c.getType() == ContactType.EMAIL && !c.getId().equals(excludeContactId))
                                .findFirst())
                        .flatMap(email -> email.isPresent()
                                ? crypto.decrypt(email.get().getValueEncrypted())
                                .flatMap(value -> keycloak.setEmail(account.getKeycloakUserId(), value, true))
                                : keycloak.setEmail(account.getKeycloakUserId(), null, false)));
    }

    /** Ends the account's Keycloak sessions so every device signs in again with the contacts as they now are. */
    public Mono<Void> endSessions(String accountId) {
        return template.findById(accountId, User.class)
                .flatMap(account -> account.getKeycloakUserId() == null || account.getKeycloakUserId().isBlank()
                        ? Mono.<Void>empty() : sessions.endSessions(null, account.getKeycloakUserId()));
    }

    // ---- 4 · the switch ------------------------------------------------------------------------------

    /**
     * The one write that switches the account over, in one transaction: releases the old contact, decides
     * the primary, recomputes the verified flags, and writes the account event and the outbox row.
     * Idempotent on {@code changeId}: a repeat finds its account event and does nothing.
     */
    public Mono<Void> commit(String accountId, String changeId, ContactChangeKind kind, String contactId, String newContactId) {
        String eventId = "contact-" + kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + changeId;
        return template.exists(Query.query(Criteria.where("_id").is(eventId)), AccountEvent.class).flatMap(done -> {
            if (done) {
                return Mono.<Void>empty();
            }
            return transaction.transactional(contactsOf(accountId).flatMap(all -> apply(accountId, changeId, kind, contactId,
                    newContactId, all, eventId)));
        });
    }

    private Mono<Void> apply(String accountId, String changeId, ContactChangeKind kind, String contactId, String newContactId,
                             List<Contact> all, String eventId) {
        Instant now = clock.instant();
        List<Contact> active = ContactRules.active(all);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("changeId", changeId);
        Mono<Void> writes;
        String eventKind;
        String outboxType;
        switch (kind) {
            case ADD -> {
                Contact added = byId(active, newContactId);
                if (added == null) {
                    return Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "claimed contact missing"));
                }
                data.put("type", added.getType().name());
                data.put("masked", added.getValueMasked());
                boolean needsPrimary = ContactRules.primary(all).isEmpty();
                writes = needsPrimary ? makePrimary(accountId, added.getId()) : Mono.empty();
                eventKind = "CONTACT_ADDED";
                outboxType = "identity.ContactAdded";
            }
            case CHANGE -> {
                Contact old = byId(active, contactId);
                Contact added = byId(active, newContactId);
                if (added == null) {
                    return Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "claimed contact missing"));
                }
                if (old == null) {
                    return Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "contact to change is gone"));
                }
                data.put("type", added.getType().name());
                data.put("oldMasked", old.getValueMasked());
                data.put("newMasked", added.getValueMasked());
                writes = release(old.getId(), now)
                        .then(old.isPrimary() ? makePrimary(accountId, added.getId()) : Mono.empty());
                eventKind = "CONTACT_CHANGED";
                outboxType = "identity.ContactChanged";
            }
            case REMOVE -> {
                var refusal = ContactRules.removalRefusal(all, contactId);
                if (refusal.isPresent()) {
                    return Mono.error(new TranslatedRefusal(refusal.get(), "contact " + contactId));
                }
                Contact old = byId(active, contactId);
                data.put("type", old.getType().name());
                data.put("masked", old.getValueMasked());
                List<Contact> remaining = active.stream().filter(c -> !c.getId().equals(contactId)).toList();
                writes = release(old.getId(), now).then(old.isPrimary()
                        ? makePrimary(accountId, ContactRules.successor(remaining).orElseThrow().getId()) : Mono.empty());
                eventKind = "CONTACT_REMOVED";
                outboxType = "identity.ContactRemoved";
            }
            case PRIMARY -> {
                Contact target = byId(active, contactId);
                if (target == null) {
                    return Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_UNKNOWN, "contact " + contactId));
                }
                data.put("type", target.getType().name());
                data.put("masked", target.getValueMasked());
                writes = makePrimary(accountId, target.getId());
                eventKind = "CONTACT_PRIMARY_SET";
                outboxType = null;
            }
            default -> throw new IllegalStateException(kind.name());
        }
        String released = kind == ContactChangeKind.CHANGE || kind == ContactChangeKind.REMOVE ? contactId : null;
        AccountEvent event = AccountEvent.builder().id(eventId).accountId(accountId).kind(eventKind).at(now).data(data).build();
        Mono<Void> flags = contactsOf(accountId).flatMap(after -> {
            boolean email = ContactRules.hasType(after, ContactType.EMAIL);
            boolean phone = ContactRules.hasType(after, ContactType.WHATSAPP);
            return template.updateFirst(Query.query(Criteria.where("_id").is(accountId)),
                    new Update().set("emailVerified", email).set("phoneVerified", phone).set("updatedAt", now), User.class).then();
        });
        Mono<Void> staged = outboxType == null ? Mono.empty()
                : outbox.stage(new EventEnvelope(eventId, outboxType, 1, now, accountId, null, "identity",
                Map.of("userId", accountId, "channel", String.valueOf(data.get("type")), "changeId", changeId)));
        return writes.then(flags).then(template.insert(event)).then(staged)
                .doOnSuccess(done -> log.debug("Contact change {} committed for account {} ({})", changeId, accountId, released == null ? kind : kind + "/released"));
    }

    private Mono<Void> release(String contactId, Instant now) {
        return template.updateFirst(Query.query(Criteria.where("_id").is(contactId).and("releasedAt").is(null)),
                new Update().set("releasedAt", now).set("primary", false), Contact.class).then();
    }

    /** Exactly one primary: every other active contact of the account is cleared in the same transaction. */
    private Mono<Void> makePrimary(String accountId, String contactId) {
        return template.updateMulti(Query.query(Criteria.where("accountId").is(accountId).and("releasedAt").is(null)
                                .and("_id").ne(contactId)), new Update().set("primary", false), Contact.class)
                .then(template.updateFirst(Query.query(Criteria.where("_id").is(contactId)), new Update().set("primary", true), Contact.class))
                .then(template.updateFirst(Query.query(Criteria.where("_id").is(accountId)),
                        new Update().set("primaryContactId", contactId), User.class))
                .then();
    }

    private static Contact byId(List<Contact> contacts, String id) {
        return contacts.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    Mono<List<Contact>> contactsOf(String accountId) {
        return template.find(Query.query(Criteria.where("accountId").is(accountId)).with(Sort.by("createdAt")), Contact.class).collectList();
    }

    // ---- 5 · telling people --------------------------------------------------------------------------

    /**
     * Tells the old and the new contact (and, for removals, the removed one) what happened, in fixed words.
     * Best effort: the change has been made; a notice that cannot be delivered is logged without the recipient.
     */
    public Mono<Void> notifyContacts(String accountId, ContactChangeKind kind, String contactId, String newContactId) {
        ContactNotice notice = switch (kind) {
            case ADD -> ContactNotice.CONTACT_ADDED;
            case CHANGE -> ContactNotice.CONTACT_CHANGED;
            case REMOVE -> ContactNotice.CONTACT_REMOVED;
            case PRIMARY -> ContactNotice.PRIMARY_CHANGED;
        };
        Mono<Void> told = Mono.empty();
        for (String id : new String[]{contactId, newContactId}) {
            if (id == null) {
                continue;
            }
            told = told.then(template.findById(id, Contact.class)
                    .filter(c -> c.getAccountId().equals(accountId))
                    .flatMap(contact -> crypto.decrypt(contact.getValueEncrypted())
                            .flatMap(value -> delivery.notice(contact.getType(), value, notice)))
                    .onErrorResume(error -> {
                        log.warn("A contact notice could not be delivered: {}", error.getClass().getSimpleName());
                        return Mono.empty();
                    }));
        }
        return told;
    }
}
