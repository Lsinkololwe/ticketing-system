package com.pml.identity.account;

import com.mongodb.MongoException;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.Consent;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.security.ContactCrypto;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.Outbox;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The MongoDB and Keycloak steps behind {@code AccountEnsureWorkflow} (CONTRACT 10).
 *
 * <p>Each method is safe to run twice, because the activity that calls it will be retried:
 * a second claim finds the contact already owned, a second Keycloak create reads the user back by
 * username, a second activation finds the account already ACTIVE. Nothing here deletes anything on
 * failure - an account that cannot be finished stays PROVISIONING and is completed forward by the
 * next ensure or by the repair schedule.</p>
 *
 * <p>The raw contact never leaves {@code valueEncrypted} except in {@link #applyAttributes}, which
 * hands the email (and only the email) to Keycloak.</p>
 */
@Service
public class AccountProvisioning {

    public static final String SOURCE_OTP = "OTP";

    private static final int RACE_LOOKS = 20;
    private static final java.time.Duration RACE_PAUSE = java.time.Duration.ofMillis(100);

    /** What a claim found or made. {@code created} is true only for the call that inserted the account. */
    public record Claimed(String accountId, AccountState state, PendingKind pendingKind, boolean created) {
    }

    /** The account after an activation attempt. */
    public record Activated(String accountId, AccountState state) {
    }

    private final ReactiveMongoTemplate template;
    private final TransactionalOperator transaction;
    private final Outbox outbox;
    private final ProofLookup proofs;
    private final KeycloakAccountPort keycloak;
    private final ContactCrypto crypto;
    private final Clock clock;
    private final java.time.Duration quarantine;

    /** Without a quarantine (tests that do not exercise one). */
    public AccountProvisioning(ReactiveMongoTemplate template, TransactionalOperator transaction, Outbox outbox,
                               ProofLookup proofs, KeycloakAccountPort keycloak, ContactCrypto crypto, Clock clock) {
        this(template, transaction, outbox, proofs, keycloak, crypto, clock, java.time.Duration.ZERO);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AccountProvisioning(ReactiveMongoTemplate template, TransactionalOperator transaction, Outbox outbox,
                               ProofLookup proofs, KeycloakAccountPort keycloak, ContactCrypto crypto, Clock clock,
                               com.pml.identity.config.IdentityContactProperties contactProperties) {
        this(template, transaction, outbox, proofs, keycloak, crypto, clock, contactProperties.getQuarantine());
    }

    AccountProvisioning(ReactiveMongoTemplate template, TransactionalOperator transaction, Outbox outbox,
                        ProofLookup proofs, KeycloakAccountPort keycloak, ContactCrypto crypto, Clock clock,
                        java.time.Duration quarantine) {
        this.quarantine = quarantine;
        this.template = template;
        this.transaction = transaction;
        this.outbox = outbox;
        this.proofs = proofs;
        this.keycloak = keycloak;
        this.crypto = crypto;
        this.clock = clock;
    }

    // ---- step 1 · claim --------------------------------------------------------------------------

    /**
     * Gives the contact an owner. A contact that already has one returns that account; otherwise a
     * PROVISIONING account and its verified contact are inserted in one transaction, from the
     * encrypted contact the proof holds. Losing the unique-index race returns the winner.
     */
    public Mono<Claimed> claim(String proofId, String contactKey, ContactType type) {
        return owner(type, contactKey)
                .flatMap(this::existing)
                .switchIfEmpty(Mono.defer(() -> create(proofId, contactKey, type)
                        .onErrorResume(AccountProvisioning::lostRace, race -> awaitOwner(type, contactKey)
                                .flatMap(this::existing)
                                .switchIfEmpty(Mono.error(race)))));
    }

    /**
     * The winner of a race, once its transaction has committed. The loser of a write conflict learns
     * of the loss while the winner is still mid-transaction, so the owner is not readable yet: look
     * again for a couple of seconds before giving the failure back to the activity's retry.
     */
    private Mono<Contact> awaitOwner(ContactType type, String contactKey) {
        return Mono.defer(() -> owner(type, contactKey))
                .repeatWhenEmpty(attempts -> attempts.take(RACE_LOOKS).delayElements(RACE_PAUSE));
    }

    private Mono<Claimed> existing(Contact owner) {
        return template.findById(owner.getAccountId(), User.class)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException(
                        "contact " + owner.getId() + " belongs to an account that does not exist")))
                .map(account -> new Claimed(account.getId(), AccountStates.of(account), account.getPendingKind(), false));
    }

    private Mono<Claimed> create(String proofId, String contactKey, ContactType type) {
        return quarantined(type, contactKey)
                .flatMap(blocked -> blocked
                        ? Mono.<Claimed>error(new TranslatedRefusal(ErrorCode.CONTACT_ALREADY_CLAIMED,
                        "the contact cannot be claimed yet"))
                        : createUnquarantined(proofId, contactKey, type));
    }

    /** A contact an account released less than the quarantine ago is not for a brand-new account (decision b). */
    private Mono<Boolean> quarantined(ContactType type, String contactKey) {
        if (quarantine == null || quarantine.isZero()) {
            return Mono.just(false);
        }
        Instant now = clock.instant();
        return template.find(Query.query(Criteria.where("type").is(type).and("valueHash").is(contactKey)
                        .and("releasedAt").ne(null)), Contact.class)
                .any(released -> ContactRules.quarantined(released.getReleasedAt(), quarantine, now));
    }

    private Mono<Claimed> createUnquarantined(String proofId, String contactKey, ContactType type) {
        return proofs.find(proofId)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.PROOF_INVALID,
                        "the proof is unknown or has expired")))
                .flatMap(proof -> {
                    if (!contactKey.equals(proof.contactKey()) || type != proof.type()
                            || proof.valueEncrypted() == null || proof.valueEncrypted().isBlank()) {
                        return Mono.error(new TranslatedRefusal(ErrorCode.PROOF_INVALID,
                                "the proof does not belong to this contact"));
                    }
                    Instant now = clock.instant();
                    String accountId = UUID.randomUUID().toString();
                    Contact contact = Contact.builder()
                            .id(UUID.randomUUID().toString())
                            .accountId(accountId)
                            .type(type)
                            .valueHash(contactKey)
                            .valueEncrypted(proof.valueEncrypted())
                            .valueMasked(proof.valueMasked())
                            .verifiedAt(now)
                            .primary(true)
                            .source(SOURCE_OTP)
                            .createdAt(now)
                            .build();
                    User account = User.builder()
                            .id(accountId)
                            .username(accountId)
                            .roles(EnumSet.of(UserType.CUSTOMER))
                            .primaryContactId(contact.getId())
                            .preferredChannel(type)
                            .emailVerified(type == ContactType.EMAIL)
                            .phoneVerified(type == ContactType.WHATSAPP)
                            .createdVia(SOURCE_OTP)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    AccountStates.apply(account, AccountState.PROVISIONING);
                    AccountEvent provisioned = AccountEvent.builder()
                            .id("provisioned:" + accountId)
                            .accountId(accountId)
                            .kind("PROVISIONED")
                            .at(now)
                            .data(Map.of("type", type.name()))
                            .build();
                    return transaction.transactional(template.insert(account)
                                    .then(template.insert(contact))
                                    .then(template.insert(provisioned)))
                            .thenReturn(new Claimed(accountId, AccountState.PROVISIONING, null, true));
                });
    }

    /** True for the failures two simultaneous claims of one contact produce: a duplicate key or a write conflict. */
    static boolean lostRace(Throwable error) {
        for (Throwable cause = error; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
            if (cause instanceof MongoException mongo
                    && (mongo.getCode() == 11000 || mongo.getCode() == 112
                    || mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL))) {
                return true;
            }
        }
        return false;
    }

    private Mono<Contact> owner(ContactType type, String contactKey) {
        return template.findOne(Query.query(Criteria.where("type").is(type)
                .and("valueHash").is(contactKey)
                .and("verifiedAt").exists(true)
                .and("releasedAt").is(null)), Contact.class);
    }

    // ---- step 2 · the Keycloak user ----------------------------------------------------------------

    /** Creates the Keycloak user whose username is the account id, or reads back the one an earlier attempt made. */
    public Mono<String> createKeycloakUser(String accountId) {
        return template.findById(accountId, User.class)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("no account " + accountId)))
                .flatMap(account -> account.getKeycloakUserId() != null && !account.getKeycloakUserId().isBlank()
                        ? Mono.just(account.getKeycloakUserId())
                        : keycloak.createUser(accountId, true, UserType.CUSTOMER));
    }

    // ---- step 3 · attributes and roles ---------------------------------------------------------------

    /** Writes the account id, the email contact (if any) and the CUSTOMER role onto the Keycloak user. */
    public Mono<Void> applyAttributes(String accountId, String keycloakUserId) {
        return template.findById(accountId, User.class)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("no account " + accountId)))
                .flatMap(account -> template.find(Query.query(Criteria.where("accountId").is(accountId)
                                        .and("type").is(ContactType.EMAIL)
                                        .and("verifiedAt").exists(true)
                                        .and("releasedAt").is(null)), Contact.class)
                                .next()
                                .flatMap(contact -> crypto.decrypt(contact.getValueEncrypted())
                                        .map(email -> new KeycloakAccountPort.AccountAttributes(
                                                accountId, email, true, account.getDisplayName(), account.getLocale())))
                                .defaultIfEmpty(new KeycloakAccountPort.AccountAttributes(
                                        accountId, null, false, account.getDisplayName(), account.getLocale()))
                                .flatMap(attributes -> keycloak.applyAttributesAndRoles(keycloakUserId, attributes,
                                        account.getRoles() == null || account.getRoles().isEmpty()
                                                ? EnumSet.of(UserType.CUSTOMER) : account.getRoles())));
    }

    // ---- step 4 · activation -------------------------------------------------------------------------

    /**
     * Compare-and-set {@code PROVISIONING -> ACTIVE}, with the consents, the account event and the
     * outbox row in the same transaction. Requires the Keycloak user id. An account that is already
     * ACTIVE is returned unchanged; a suspended or merging one is refused.
     */
    public Mono<Activated> activate(String accountId, String keycloakUserId, String clientId, String displayName,
                                    List<ConsentGrant> consents) {
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return Mono.error(new IllegalArgumentException("an account is not activated without its Keycloak user"));
        }
        Instant now = clock.instant();
        Update update = new Update()
                .set("status", AccountState.ACTIVE)
                .set("accountStatus", AccountStates.legacyStatus(AccountState.ACTIVE))
                .set("active", true)
                .set("keycloakUserId", keycloakUserId)
                .set("provisionedAt", now)
                .set("updatedAt", now)
                .unset("pendingKind")
                .unset("pendingSince");
        if (displayName != null && !displayName.isBlank()) {
            update.set("displayName", displayName);
        }
        Mono<Boolean> cas = template.updateFirst(Query.query(Criteria.where("_id").is(accountId)
                                .and("status").is(AccountState.PROVISIONING)), update, User.class)
                .map(result -> result.getModifiedCount() == 1);

        Mono<Boolean> activation = cas.flatMap(won -> won
                ? facts(accountId, clientId, consents, now).thenReturn(true)
                : Mono.just(false));

        return transaction.transactional(activation)
                .then(Mono.defer(() -> template.findById(accountId, User.class)))
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("no account " + accountId)))
                .flatMap(account -> AccountGuard.refusal(account)
                        .<Mono<Activated>>map(code -> Mono.error(new TranslatedRefusal(code, "account " + accountId)))
                        .orElseGet(() -> Mono.just(new Activated(accountId, AccountStates.of(account)))));
    }

    private Mono<Void> facts(String accountId, String clientId, List<ConsentGrant> consents, Instant now) {
        Set<ConsentGrant> distinct = consents == null ? Set.of() : new LinkedHashSet<>(consents);
        Flux<Consent> grants = Flux.fromIterable(distinct)
                .filter(grant -> grant.purpose() != null && !grant.purpose().isBlank())
                .map(grant -> Consent.builder()
                        .id(accountId + ":" + grant.purpose() + ":" + grant.version())
                        .accountId(accountId)
                        .purpose(grant.purpose())
                        .version(grant.version())
                        .grantedAt(now)
                        .source("ensure:" + clientId)
                        .build());
        Map<String, Object> data = new LinkedHashMap<>();
        if (clientId != null) {
            data.put("clientId", clientId);
        }
        AccountEvent activated = AccountEvent.builder()
                .id("activated:" + accountId)
                .accountId(accountId)
                .kind("ACTIVATED")
                .at(now)
                .data(data)
                .build();
        return grants.concatMap(consent -> template.insert(consent))
                .then(template.insert(activated))
                .then(outbox.stage(activatedEnvelope(accountId, now)));
    }

    private static EventEnvelope activatedEnvelope(String accountId, Instant at) {
        // The id is derived from the account, so staging it twice (here and in stageOutbox) is one row.
        return new EventEnvelope("account-activated:" + accountId, "identity.AccountActivated", 1, at,
                accountId, null, "identity", Map.of("userId", accountId));
    }

    // ---- step 5 · outbox guard -----------------------------------------------------------------------

    /** Stages the activation fact if an ACTIVE account does not have it yet; a second call is a no-op. */
    public Mono<Void> stageOutbox(String accountId) {
        return template.findById(accountId, User.class)
                .filter(account -> AccountStates.of(account) == AccountState.ACTIVE)
                .flatMap(account -> outbox.stage(activatedEnvelope(accountId, clock.instant()))
                        .onErrorResume(DuplicateKeyException.class, alreadyStaged -> Mono.empty()));
    }
}
