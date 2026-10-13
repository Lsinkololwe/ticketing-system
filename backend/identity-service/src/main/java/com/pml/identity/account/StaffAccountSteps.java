package com.pml.identity.account;

import com.pml.identity.account.StaffAccountSeal.Details;
import com.pml.identity.auth.delivery.ContactNotice;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.model.User.StaffCreation;
import com.pml.identity.security.ContactHasher;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.EnumSet;
import java.util.UUID;

/**
 * The MongoDB and Keycloak steps behind {@code StaffAccountWorkflow}, the process that opens a platform
 * staff account.
 *
 * <p>The order is the safety argument. The platform's own record is written first, in a
 * {@code PROVISIONING} state that carries the role the person is to receive. Only then is the person
 * created in the login system, and there they are created <b>unable to sign in</b>. The role is granted,
 * and only when it is granted is the account made {@code ACTIVE} and the sign-in opened. So at every
 * moment a person who exists in Keycloak also exists in the records, and nobody can sign in with a
 * half-finished account.</p>
 *
 * <p>Every step may run twice, because the activity that calls it is retried. A step that cannot be made
 * to succeed ends in {@link #discard}, which removes the Keycloak user this process made (never one that
 * was already there) and then the record.</p>
 */
@Service
public class StaffAccountSteps {

    private static final Logger log = LoggerFactory.getLogger(StaffAccountSteps.class);

    /** The Keycloak user a creation settled on, and whether this process made it. */
    public record KeycloakUser(String id, boolean createdHere) {
    }

    /**
     * What a claim found or wrote: the account's id, and whether it is already a finished account that this
     * same administrator opened with this same role (a repeated request, which has nothing left to do).
     */
    public record Claimed(String accountId, boolean finished) {
    }

    private final ReactiveMongoTemplate template;
    private final TransactionalOperator transaction;
    private final KeycloakUserAdminPort keycloak;
    private final KeycloakProperties keycloakProperties;
    private final AccountService accounts;
    private final ContactService contacts;
    private final StaffAccountSeal seal;
    private final ContactHasher hasher;
    private final DeliveryOrchestrator delivery;
    private final Clock clock;

    public StaffAccountSteps(ReactiveMongoTemplate template, TransactionalOperator transaction,
                             KeycloakUserAdminPort keycloak, KeycloakProperties keycloakProperties,
                             AccountService accounts, ContactService contacts, StaffAccountSeal seal,
                             ContactHasher hasher, DeliveryOrchestrator delivery, Clock clock) {
        this.template = template;
        this.transaction = transaction;
        this.keycloak = keycloak;
        this.keycloakProperties = keycloakProperties;
        this.accounts = accounts;
        this.contacts = contacts;
        this.seal = seal;
        this.hasher = hasher;
        this.delivery = delivery;
        this.clock = clock;
    }

    /** The id of the platform record of a creation before its Keycloak user is known: the same for every attempt. */
    public static String creationId(String staffKey) {
        return UUID.nameUUIDFromBytes(("staff-account:" + staffKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // ---- 0 · the request ------------------------------------------------------------------------------

    /**
     * Refuses a request that cannot succeed, before anything is written or started: a role that is not a
     * staff role, an address that is taken by another account. An account this same creation already
     * holds in PROVISIONING is not "taken"; it is the one being finished.
     */
    public Mono<Void> admit(Details details) {
        if (details.role() == null || !details.role().isStaffRole()) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "a staff user is created with one of the staff roles"));
        }
        if (details.email() == null || details.email().isBlank()) {
            return Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_INVALID, "a staff user needs a valid email"));
        }
        return openCreation(details.email()).hasElement().flatMap(mine -> mine ? Mono.<Void>empty() : refuseIfTaken(details.email()));
    }

    private Mono<Void> refuseIfTaken(String email) {
        Mono<User> byContact = contacts.accountByContact(email, ContactType.EMAIL);
        Mono<User> byRecord = template.findOne(Query.query(new Criteria().orOperator(
                Criteria.where("email").is(email), Criteria.where("username").is(email))), User.class);
        return byContact.switchIfEmpty(byRecord)
                .flatMap(existing -> Mono.<Void>error(new TranslatedRefusal(ErrorCode.RESOURCE_CONFLICT,
                        "a user with this email already exists")));
    }

    /** The unfinished account of a creation for this address, found by what only a creation sets. */
    private Mono<User> openCreation(String email) {
        return template.findOne(Query.query(Criteria.where("username").is(email)
                .and("createdVia").is(AccountService.STAFF_ADMIN)
                .and("status").is(AccountState.PROVISIONING)
                .and("staffCreation").ne(null)), User.class);
    }

    // ---- 1 · the record -------------------------------------------------------------------------------

    /**
     * Writes the platform's record of the account in PROVISIONING with the requested role, or finds the
     * one an earlier attempt for the same address and role left, and returns its id. A record left by an
     * attempt for another role is discarded first: the request in hand is the one being honoured. An
     * account this administrator already finished for this address and role is returned as finished.
     */
    public Mono<Claimed> claim(String sealed) {
        return seal.open(sealed).flatMap(details -> openCreation(details.email())
                .flatMap(existing -> sameRole(existing, details)
                        ? adopt(existing, details).map(id -> new Claimed(id, false))
                        : discard(existing).then(insert(details)).map(id -> new Claimed(id, false)))
                .switchIfEmpty(Mono.defer(() -> finishedAccount(details)
                        .map(done -> new Claimed(done.getId(), true))
                        .switchIfEmpty(Mono.defer(() -> admit(details).then(insert(details))
                                .map(id -> new Claimed(id, false))))))
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("no staff account could be recorded"))));
    }

    /**
     * The account an earlier identical request already finished, so a repeat returns it instead of being
     * refused as a duplicate: opened by the same administrator, for this address, with this role.
     */
    public Mono<User> finishedAccount(Details details) {
        return template.findOne(Query.query(Criteria.where("username").is(details.email())
                .and("createdVia").is(AccountService.STAFF_ADMIN)
                .and("createdBy").is(details.actorId())
                .and("status").is(AccountState.ACTIVE)
                .and("roles").is(details.role())), User.class);
    }

    private static boolean sameRole(User existing, Details details) {
        return existing.getStaffCreation() != null && details.role() == existing.getStaffCreation().getRole();
    }

    /** The administrator who repeats an unfinished request is the one who completes it, and is recorded as such. */
    private Mono<String> adopt(User existing, Details details) {
        return template.updateFirst(Query.query(Criteria.where("_id").is(existing.getId())),
                        new Update().set("createdBy", details.actorId()).set("updatedAt", clock.instant()), User.class)
                .thenReturn(existing.getId());
    }

    private Mono<String> insert(Details details) {
        Instant now = clock.instant();
        String id = creationId(hasher.hash(ContactType.EMAIL, details.email()));
        User staff = User.builder()
                .id(id)
                .username(details.email())
                .email(details.email())
                .firstName(details.firstName())
                .lastName(details.lastName())
                .phoneNumber(details.phoneNumber())
                .phoneCountry(details.phoneCountry())
                .roles(EnumSet.of(UserType.CUSTOMER))
                .createdVia(AccountService.STAFF_ADMIN)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(details.actorId())
                .staffCreation(new StaffCreation(details.role(), false, false))
                .build();
        AccountStates.apply(staff, AccountState.PROVISIONING);
        return template.insert(staff)
                .map(User::getId)
                .onErrorMap(DuplicateKeyException.class, duplicate -> String.valueOf(duplicate.getMessage()).contains("phoneNumber")
                        ? new TranslatedRefusal(ErrorCode.RESOURCE_CONFLICT, "that number belongs to another account")
                        : new TranslatedRefusal(ErrorCode.RESOURCE_CONFLICT, "a user with this email already exists"));
    }

    // ---- 2 · Keycloak ---------------------------------------------------------------------------------

    /**
     * Creates the person in the staff realm, unable to sign in, or reads back the user an earlier attempt
     * made. A Keycloak user that was already there before this process began is adopted and left exactly as
     * it is: it is never removed by an undo and never enabled by this process.
     */
    public Mono<KeycloakUser> createInKeycloak(String accountId, String sealed) {
        return require(accountId).flatMap(account -> {
            if (account.getKeycloakUserId() != null && !account.getKeycloakUserId().isBlank()) {
                KeycloakUser linked = new KeycloakUser(account.getKeycloakUserId(),
                        account.getStaffCreation() != null && account.getStaffCreation().isKeycloakCreatedHere());
                return seal.open(sealed).flatMap(details -> withPassword(linked, details));
            }
            StaffCreation creation = account.getStaffCreation();
            if (creation == null) {
                return Mono.<KeycloakUser>error(new IllegalStateException("account " + accountId + " is not being created"));
            }
            return seal.open(sealed).flatMap(details -> {
                // Created with no credential; the password is applied below, the same way for a user this
                // attempt made, one an earlier attempt made, and a repeat that carries a fresh password.
                Mono<KeycloakUser> create = Mono.defer(() -> keycloak.createStaffUser(account.getUsername(), account.getEmail(),
                                details.firstName(), details.lastName(), null, false)
                        .map(id -> new KeycloakUser(id, true)));
                if (creation.isKeycloakStarted()) {
                    return create.flatMap(user -> withPassword(user, details));
                }
                return keycloak.findStaffUserId(account.getUsername())
                        .map(id -> new KeycloakUser(id, false))
                        .switchIfEmpty(Mono.defer(() -> template.updateFirst(
                                        Query.query(Criteria.where("_id").is(accountId)),
                                        new Update().set("staffCreation.keycloakStarted", true), User.class)
                                .then(create)))
                        .flatMap(user -> withPassword(user, details));
            });
        });
    }

    /**
     * Applies the request's temporary password to a Keycloak user this process made. A user that was
     * already there before the creation began is adopted as it is: nobody's password is changed by it.
     * Applying is repeatable, so a retried step, or a repeated request that carries a new password,
     * leaves the user with the password of the request being finished.
     */
    private Mono<KeycloakUser> withPassword(KeycloakUser user, Details details) {
        if (!user.createdHere() || details.temporaryPassword() == null || details.temporaryPassword().isBlank()) {
            return Mono.just(user);
        }
        return keycloak.issueTemporaryPassword(user.id(), details.temporaryPassword()).thenReturn(user);
    }

    /**
     * Gives the record the Keycloak user's id as its own, in one transaction, so the account's id is the
     * Keycloak id as it is for every staff account. Returns the id the record now has.
     */
    public Mono<String> link(String accountId, String keycloakUserId, boolean createdHere) {
        if (accountId.equals(keycloakUserId)) {
            return Mono.just(accountId);
        }
        return template.findById(accountId, User.class).flatMap(claim -> {
            StaffCreation creation = claim.getStaffCreation();
            boolean started = creation != null && (creation.isKeycloakStarted() || createdHere);
            User linked = claim.toBuilder()
                    .id(keycloakUserId)
                    .keycloakUserId(keycloakUserId)
                    .staffCreation(new StaffCreation(creation == null ? null : creation.getRole(), started, createdHere))
                    .updatedAt(clock.instant())
                    .build();
            return transaction.transactional(template.remove(Query.query(Criteria.where("_id").is(accountId)), User.class)
                            .then(template.insert(linked)))
                    .thenReturn(keycloakUserId);
        }).switchIfEmpty(Mono.defer(() -> template.exists(Query.query(Criteria.where("_id").is(keycloakUserId)), User.class)
                .flatMap(done -> done ? Mono.just(keycloakUserId)
                        : Mono.<String>error(new IllegalStateException("account " + accountId + " is gone")))));
    }

    // ---- 3 · the role, then the sign-in ---------------------------------------------------------------

    /** Grants the requested staff role in the staff realm and on the record, with the administrator named. */
    public Mono<Void> grantRole(String accountId) {
        return require(accountId).flatMap(account -> account.getStaffCreation() == null
                ? Mono.<Void>empty()
                : accounts.addRole(accountId, account.getStaffCreation().getRole(), account.getCreatedBy()).then());
    }

    /**
     * Opens the sign-in and makes the account ACTIVE. Keycloak first: an account that is ACTIVE with a user
     * who cannot sign in is merely unfinished, whereas the other order could leave a person able to sign in
     * to an account the platform does not yet call active.
     */
    public Mono<Void> activate(String accountId) {
        return require(accountId).flatMap(account -> {
            StaffCreation creation = account.getStaffCreation();
            if (creation == null) {
                return Mono.<Void>empty();
            }
            if (!account.hasRole(creation.getRole())) {
                return Mono.<Void>error(new IllegalStateException("account " + accountId + " has not been given its role"));
            }
            Mono<Void> enable = creation.isKeycloakCreatedHere()
                    ? keycloak.setEnabled(keycloakProperties.getStaffRealm(), account.getKeycloakUserId(), true)
                    : Mono.empty();
            User states = new User();
            AccountStates.apply(states, AccountState.ACTIVE);
            return enable.then(Mono.defer(() -> template.updateFirst(
                            Query.query(Criteria.where("_id").is(accountId).and("status").is(AccountState.PROVISIONING)),
                            new Update().set("status", states.getStatus()).set("accountStatus", states.getAccountStatus())
                                    .set("active", states.isActive()).set("updatedAt", clock.instant())
                                    .unset("staffCreation"), User.class)))
                    .then();
        });
    }

    /** Tells the person, in fixed words, that an account was opened for the address. Best effort. */
    public Mono<Void> notifyCreated(String accountId) {
        return require(accountId)
                .flatMap(account -> account.getEmail() == null ? Mono.<Void>empty()
                        : delivery.notice(ContactType.EMAIL, account.getEmail(), ContactNotice.STAFF_ACCOUNT_CREATED))
                .onErrorResume(error -> {
                    log.warn("A staff account notice could not be delivered: {}", error.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    // ---- 4 · undo ------------------------------------------------------------------------------------

    /**
     * Takes back a creation that cannot finish: the Keycloak user this process made, then the record.
     * Keycloak goes first, so a failure part way leaves the record that still names what is left to remove,
     * never a Keycloak user with no record. An account that is already ACTIVE is never touched.
     */
    public Mono<Void> compensate(String accountId) {
        return template.findById(accountId, User.class)
                .filter(account -> account.getStaffCreation() != null && AccountStates.of(account) == AccountState.PROVISIONING)
                .flatMap(this::discard);
    }

    private Mono<Void> discard(User account) {
        StaffCreation creation = account.getStaffCreation();
        boolean ours = creation != null && (creation.isKeycloakCreatedHere() || creation.isKeycloakStarted());
        Mono<Void> keycloakUser;
        if (!ours) {
            keycloakUser = Mono.empty();
        } else if (account.getKeycloakUserId() != null && !account.getKeycloakUserId().isBlank()) {
            keycloakUser = keycloak.deleteStaffUser(account.getKeycloakUserId());
        } else {
            keycloakUser = keycloak.findStaffUserId(account.getUsername()).flatMap(keycloak::deleteStaffUser);
        }
        return keycloakUser.then(Mono.defer(() -> template.remove(Query.query(Criteria.where("_id").is(account.getId())
                .and("status").is(AccountState.PROVISIONING)), User.class).then()));
    }

    private Mono<User> require(String accountId) {
        return template.findById(accountId, User.class)
                .switchIfEmpty(Mono.error(() -> new IllegalStateException("staff account " + accountId + " is gone")));
    }
}
