package com.pml.identity.account;

import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.security.revocation.MongoRevocationStore;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.Outbox;
import com.pml.shared.security.revocation.RevocationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The state changes of an account: suspension, deletion and roles (CONTRACT 2, 9).
 *
 * <h2>Order of writes</h2>
 * <ul>
 *   <li><b>Suspend:</b> MongoDB first (the status, the account event, the audit row and the outbox row
 *       in one transaction), so no new sign-in can start; then the revocation of the user's tokens;
 *       then Keycloak disabled by id with retry, and the sessions ended. A Keycloak failure after the
 *       status committed is raised to the caller and the operation can be repeated safely.</li>
 *   <li><b>Unsuspend:</b> MongoDB, then Keycloak enabled, then the revocation lifted - so the first
 *       token the person gets is one the revocation does not cover.</li>
 *   <li><b>Roles:</b> Keycloak first (the token is what authorises), then the stored copy. A crash in
 *       between leaves Keycloak ahead and the same call repairs it.</li>
 *   <li><b>Delete:</b> the status, the released contacts and the facts commit together; Keycloak is
 *       disabled, never deleted. The person's data is erased by a separate, audited process.</li>
 * </ul>
 *
 * <p>The account's {@code status}, legacy {@code accountStatus} and {@code active} flag are only ever
 * written together, through {@link AccountStates#apply}.</p>
 */
@Slf4j
@Service
public class AccountService {

    /** {@code createdVia} of accounts that belong to the platform-staff realm. */
    public static final String STAFF_SYNC = "STAFF_SYNC";
    public static final String STAFF_ADMIN = "STAFF_ADMIN";

    private static final int REASON_LIMIT = 200;

    private final ReactiveMongoTemplate template;
    private final TransactionalOperator transaction;
    private final Outbox outbox;
    private final KeycloakUserAdminPort keycloak;
    private final ObjectProvider<MongoRevocationStore> revocations;
    private final KeycloakProperties keycloakProperties;
    private final Clock clock;
    private final Retry keycloakRetry;

    @Autowired
    public AccountService(ReactiveMongoTemplate template, TransactionalOperator transaction, Outbox outbox,
                          KeycloakUserAdminPort keycloak, ObjectProvider<MongoRevocationStore> revocations,
                          KeycloakProperties keycloakProperties, Clock clock) {
        this(template, transaction, outbox, keycloak, revocations, keycloakProperties, clock,
                Retry.backoff(4, Duration.ofMillis(250)).maxBackoff(Duration.ofSeconds(4)));
    }

    AccountService(ReactiveMongoTemplate template, TransactionalOperator transaction, Outbox outbox,
                   KeycloakUserAdminPort keycloak, ObjectProvider<MongoRevocationStore> revocations,
                   KeycloakProperties keycloakProperties, Clock clock, Retry keycloakRetry) {
        this.template = template;
        this.transaction = transaction;
        this.outbox = outbox;
        this.keycloak = keycloak;
        this.revocations = revocations;
        this.keycloakProperties = keycloakProperties;
        this.clock = clock;
        this.keycloakRetry = keycloakRetry;
    }

    // ---- reads --------------------------------------------------------------------------------------

    public Mono<User> require(String accountId) {
        return template.findById(accountId, User.class)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN, "account " + accountId)));
    }

    /** The account a token's {@code sub} belongs to: by id, else by the linked Keycloak user. */
    public Mono<User> bySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return Mono.empty();
        }
        return template.findById(subject, User.class)
                .switchIfEmpty(Mono.defer(() -> template.findOne(
                        Query.query(Criteria.where("keycloakUserId").is(subject)), User.class)));
    }

    /** The realm a Keycloak operation on this account goes to: null for buyers, the staff realm for staff. */
    public String realmOf(User account) {
        return STAFF_SYNC.equals(account.getCreatedVia()) || STAFF_ADMIN.equals(account.getCreatedVia())
                ? keycloakProperties.getStaffRealm() : null;
    }

    // ---- suspension ---------------------------------------------------------------------------------

    public Mono<User> suspend(String accountId, String reason, String actorId) {
        return require(accountId).flatMap(account -> {
            AccountState state = AccountStates.of(account);
            if (state == AccountState.MERGED || state == AccountState.DELETED) {
                return Mono.<User>error(new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE,
                        "account " + accountId + " is " + state));
            }
            Mono<User> committed = state == AccountState.SUSPENDED
                    ? Mono.just(account)
                    : transaction.transactional(change(account, AccountState.SUSPENDED, "SUSPENDED", actorId,
                    AuditLog.AuditAction.USER_SUSPENDED, reason, true));
            return committed.flatMap(saved -> enforceSuspension(saved, reason, actorId).thenReturn(saved));
        });
    }

    private Mono<Void> enforceSuspension(User account, String reason, String actorId) {
        String keycloakUserId = account.getKeycloakUserId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return Mono.empty();
        }
        String realm = realmOf(account);
        Mono<Void> revoke = revocations.stream().findFirst()
                .map(store -> store.revoke(RevocationType.USER, keycloakUserId, "account-suspended",
                        actorId == null ? "system" : actorId).then())
                .orElseGet(() -> {
                    log.warn("Token revocation is switched off; the tokens of suspended account {} live until they expire",
                            account.getId());
                    return Mono.empty();
                });
        return revoke
                .then(Mono.defer(() -> keycloak.setEnabled(realm, keycloakUserId, false).retryWhen(keycloakRetry)))
                .then(Mono.defer(() -> keycloak.endSessions(realm, keycloakUserId).retryWhen(keycloakRetry)));
    }

    public Mono<User> unsuspend(String accountId, String actorId) {
        return require(accountId).flatMap(account -> {
            AccountState state = AccountStates.of(account);
            if (state != AccountState.SUSPENDED && state != AccountState.ACTIVE) {
                return Mono.<User>error(new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE,
                        "account " + accountId + " is " + state));
            }
            Mono<User> committed = state == AccountState.ACTIVE
                    ? Mono.just(account)
                    : transaction.transactional(change(account, AccountState.ACTIVE, "UNSUSPENDED", actorId,
                    AuditLog.AuditAction.USER_ACTIVATED, null, false));
            return committed.flatMap(saved -> restore(saved).thenReturn(saved));
        });
    }

    private Mono<Void> restore(User account) {
        String keycloakUserId = account.getKeycloakUserId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return Mono.empty();
        }
        return keycloak.setEnabled(realmOf(account), keycloakUserId, true).retryWhen(keycloakRetry)
                .then(Mono.defer(() -> revocations.stream().findFirst()
                        .map(store -> store.lift(RevocationType.USER, keycloakUserId))
                        .orElse(Mono.empty())));
    }

    // ---- deletion -----------------------------------------------------------------------------------

    /**
     * Removes the account from circulation: status DELETED, contacts released so the numbers can be
     * registered again, Keycloak disabled and its sessions ended. Nothing is deleted. Safe to repeat.
     *
     * @param keycloakUserExists false when Keycloak itself reported the deletion, so there is nothing to disable
     */
    public Mono<User> delete(String accountId, String actorId, boolean keycloakUserExists) {
        return require(accountId).flatMap(account -> {
            if (AccountStates.of(account) == AccountState.DELETED) {
                return Mono.just(account);
            }
            Instant now = clock.instant();
            Mono<User> committed = transaction.transactional(
                    template.updateMulti(Query.query(Criteria.where("accountId").is(accountId).and("releasedAt").is(null)),
                                    new Update().set("releasedAt", now), Contact.class)
                            .then(change(account, AccountState.DELETED, "DELETED", actorId,
                                    AuditLog.AuditAction.USER_DELETED, null, true)));
            return committed.flatMap(saved -> keycloakUserExists
                    ? enforceSuspension(saved, "account-deleted", actorId).thenReturn(saved)
                    : revokeOnly(saved, actorId).thenReturn(saved));
        });
    }

    private Mono<Void> revokeOnly(User account, String actorId) {
        String keycloakUserId = account.getKeycloakUserId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return Mono.empty();
        }
        return revocations.stream().findFirst()
                .map(store -> store.revoke(RevocationType.USER, keycloakUserId, "account-deleted",
                        actorId == null ? "system" : actorId).then())
                .orElse(Mono.empty());
    }

    // ---- roles --------------------------------------------------------------------------------------

    public Mono<User> addRole(String accountId, UserType role, String actorId) {
        return require(accountId).flatMap(account -> {
            if (account.hasRole(role)) {
                return Mono.just(account);
            }
            if (!UserType.canAddRole(account.getRoles(), role)) {
                return Mono.<User>error(new IllegalArgumentException(
                        "Cannot add role " + role + " to user. Invalid role combination."));
            }
            Set<UserType> next = EnumSet.copyOf(account.getRoles());
            next.add(role);
            return applyRoles(account, next, actorId);
        });
    }

    public Mono<User> removeRole(String accountId, UserType role, String actorId) {
        if (role == UserType.CUSTOMER) {
            return Mono.error(new IllegalStateException("Cannot remove CUSTOMER role. It is the base role for all users."));
        }
        return require(accountId).flatMap(account -> {
            if (!UserType.canRemoveRole(account.getRoles(), role)) {
                return Mono.<User>error(new IllegalArgumentException(
                        "Cannot remove role " + role + " from user. User does not have this role or it would result in an invalid state."));
            }
            Set<UserType> next = EnumSet.copyOf(account.getRoles());
            next.remove(role);
            return applyRoles(account, next, actorId);
        });
    }

    public Mono<User> setRoles(String accountId, Set<UserType> roles, String actorId) {
        if (!UserType.isValidRoleCombination(roles)) {
            return Mono.error(new IllegalArgumentException(
                    "Invalid role combination. Roles must include CUSTOMER and follow business rules."));
        }
        return require(accountId).flatMap(account -> applyRoles(account, EnumSet.copyOf(roles), actorId));
    }

    public Mono<Set<UserType>> rolesOf(String accountId) {
        return template.findById(accountId, User.class)
                .map(User::getRoles)
                .defaultIfEmpty(EnumSet.of(UserType.CUSTOMER));
    }

    private Mono<User> applyRoles(User account, Set<UserType> next, String actorId) {
        String keycloakUserId = account.getKeycloakUserId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            // No Keycloak user means no token the role would ever reach; refuse rather than store a role nothing enforces.
            return Mono.error(new TranslatedRefusal(ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "account " + account.getId() + " has no Keycloak user yet"));
        }
        Set<UserType> before = account.getRoles() == null ? EnumSet.of(UserType.CUSTOMER) : EnumSet.copyOf(account.getRoles());
        Set<UserType> toAdd = EnumSet.copyOf(next);
        toAdd.removeAll(before);
        Set<UserType> toRemove = EnumSet.copyOf(before);
        toRemove.removeAll(next);
        String realm = realmOf(account);

        Mono<Void> keycloakFirst = Flux.fromIterable(toAdd)
                .concatMap(role -> keycloak.grantRealmRole(realm, keycloakUserId, role.name()))
                .thenMany(Flux.fromIterable(toRemove)
                        .concatMap(role -> keycloak.revokeRealmRole(realm, keycloakUserId, role.name())))
                .then();

        return keycloakFirst.then(Mono.defer(() -> {
            Instant now = clock.instant();
            account.setRoles(next);
            account.setUpdatedAt(now);
            account.setUpdatedBy(actorId);
            Mono<?> audits = Flux.concat(
                    Flux.fromIterable(toAdd).concatMap(role -> audit(AuditLog.AuditAction.ROLE_GRANT, account, actorId, role.name())),
                    Flux.fromIterable(toRemove).concatMap(role -> audit(AuditLog.AuditAction.ROLE_REVOKE, account, actorId, role.name()))
            ).then();
            return transaction.transactional(template.save(account).flatMap(saved -> audits.thenReturn(saved)));
        }));
    }

    // ---- the one state transition -------------------------------------------------------------------

    /** Writes the state, the account event, the audit row and, when asked, the outbox row; one transaction's worth. */
    private Mono<User> change(User account, AccountState state, String kind, String actorId,
                              AuditLog.AuditAction action, String reason, boolean stageOutbox) {
        Instant now = clock.instant();
        AccountStates.apply(account, state);
        account.setUpdatedAt(now);
        account.setUpdatedBy(actorId);
        Map<String, Object> data = new LinkedHashMap<>();
        if (actorId != null) {
            data.put("by", actorId);
        }
        String cleanReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), REASON_LIMIT));
        if (cleanReason != null && !cleanReason.isBlank()) {
            data.put("reason", cleanReason);
        }
        AccountEvent event = AccountEvent.builder()
                .id(kind.toLowerCase() + ":" + account.getId() + ":" + UUID.randomUUID())
                .accountId(account.getId())
                .kind(kind)
                .at(now)
                .data(data)
                .build();
        Mono<Void> staged = stageOutbox
                ? outbox.stage(new EventEnvelope(UUID.randomUUID().toString(), "identity.Account" + capitalised(kind), 1, now,
                account.getId(), null, "identity", Map.of("userId", account.getId())))
                : Mono.empty();
        return template.save(account)
                .flatMap(saved -> template.insert(event)
                        .then(audit(action, saved, actorId, cleanReason))
                        .then(staged)
                        .thenReturn(saved));
    }

    private Mono<AuditLog> audit(AuditLog.AuditAction action, User account, String actorId, String detail) {
        AuditLog row = AuditLog.success(action, account.getId(), actorId == null ? "system" : actorId, clock.instant());
        row.setResourceType("User");
        row.setResourceId(account.getId());
        if (detail != null && !detail.isBlank()) {
            row.setMetadata(Map.of("detail", detail));
        }
        return template.insert(row);
    }

    private static String capitalised(String kind) {
        String lower = kind.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
