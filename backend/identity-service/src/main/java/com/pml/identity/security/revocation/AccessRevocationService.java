package com.pml.identity.security.revocation;

import com.pml.identity.account.AccountService;
import com.pml.identity.account.KeycloakUserAdminPort;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.AdminAuditService;
import com.pml.identity.web.graphql.dto.pagination.OffsetPaginationInput;
import com.pml.identity.web.graphql.dto.pagination.PageInfo;
import com.pml.identity.web.graphql.dto.platform.RevocationRecordOffsetPage;
import com.pml.identity.web.graphql.dto.platform.RevocationRecordView;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.revocation.RevocationIdentifier;
import com.pml.shared.security.revocation.RevocationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every way access is cut, in one place: the automatic triggers (a member removed, a grant
 * revoked, an organization suspended, a contact or credential changed, an account merged or
 * erased), the administrator's manual revocations, and a person ending their own sessions.
 *
 * <h2>What a revocation writes</h2>
 * <p>The durable MongoDB record and the Redis keys, through {@link MongoRevocationStore}. Redis
 * is shared by the gateway and every service, so the cache write is the platform-wide
 * propagation; the periodic reload from MongoDB repairs a service that lost a key. There is
 * deliberately no domain event: a revocation is not business history to replay, and a second
 * channel would only add a way for the two to disagree.</p>
 *
 * <h2>Audit</h2>
 * <p>Only a revocation an administrator makes by hand writes an {@code ACCESS_REVOKED} audit row,
 * because that is a person's decision somebody may later have to answer for. Automatic triggers
 * and a person's own sign-out are already recorded by the event that caused them. The
 * revocation goes first and the audit write never undoes it: a failed audit row is logged as an
 * error and the access stays cut.</p>
 */
@Slf4j
@Service
public class AccessRevocationService implements AccessCutter {

    private final ObjectProvider<MongoRevocationStore> store;
    private final AdminAuditService audit;
    private final AccountService accounts;
    private final KeycloakUserAdminPort keycloak;
    private final ReactiveMongoTemplate template;
    private final Clock clock;

    public AccessRevocationService(ObjectProvider<MongoRevocationStore> store, AdminAuditService audit,
                                   AccountService accounts, KeycloakUserAdminPort keycloak,
                                   ReactiveMongoTemplate template, Clock clock) {
        this.store = store;
        this.audit = audit;
        this.accounts = accounts;
        this.keycloak = keycloak;
        this.template = template;
        this.clock = clock;
    }

    // ---- automatic triggers -------------------------------------------------------------------------

    /**
     * Cuts every token of an account, found by its account id. An account with no Keycloak user
     * has no tokens, so there is nothing to cut.
     */
    public Mono<Void> cutAccount(String accountId, RevocationTrigger trigger, String actorId) {
        if (accountId == null || accountId.isBlank()) {
            return Mono.empty();
        }
        return template.findById(accountId, User.class)
                .flatMap(account -> cutKeycloakUser(account.getKeycloakUserId(), accountId, trigger, actorId))
                .then();
    }

    @Override
    public Mono<Void> cutSessions(String accountId, RevocationTrigger trigger, String actorId) {
        MongoRevocationStore revocations = store.getIfAvailable();
        if (accountId == null || accountId.isBlank() || revocations == null) {
            return Mono.empty();
        }
        String actor = actorId == null || actorId.isBlank() ? "system" : actorId;
        return template.findById(accountId, User.class)
                .filter(account -> account.getKeycloakUserId() != null && !account.getKeycloakUserId().isBlank())
                .flatMap(account -> endSessionsExcept(revocations, account, null, trigger, actor))
                .then();
    }

    /** Cuts every token a Keycloak user holds. */
    public Mono<Void> cutKeycloakUser(String keycloakUserId, String accountId, RevocationTrigger trigger,
                                      String actorId) {
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return Mono.empty();
        }
        MongoRevocationStore revocations = store.getIfAvailable();
        if (revocations == null) {
            log.warn("Token revocation is switched off; the tokens of an account cut for {} live until "
                    + "they expire", trigger);
            return Mono.empty();
        }
        String actor = actorId == null || actorId.isBlank() ? "system" : actorId;
        return revocations.revoke(RevocationType.USER, keycloakUserId, trigger, trigger.name(), actor, null)
                .then();
    }

    /**
     * Revokes and ends every session of a Keycloak user except {@code keepSid}: a credential change
     * signs out whoever held the old credential without signing out the person making the change.
     * A user with no account here has no sessions this platform knows of, so nothing happens.
     *
     * @return the number of sessions ended
     */
    public Mono<Long> cutOtherSessions(String keycloakUserId, String keepSid, RevocationTrigger trigger) {
        MongoRevocationStore revocations = store.getIfAvailable();
        if (revocations == null) {
            return Mono.error(new IllegalStateException("token revocation is switched off"));
        }
        return template.findOne(Query.query(Criteria.where("keycloakUserId").is(keycloakUserId)), User.class)
                .flatMap(account -> endSessionsExcept(revocations, account, keepSid, trigger, account.getId()))
                .defaultIfEmpty(0L);
    }

    private Mono<Long> endSessionsExcept(MongoRevocationStore revocations, User account, String keepSid,
                                         RevocationTrigger trigger, String actorId) {
        String realm = accounts.realmOf(account);
        return keycloak.sessions(realm, account.getKeycloakUserId())
                .filter(view -> keepSid == null || !view.id().equals(keepSid))
                .concatMap(view -> revocations.revoke(RevocationType.SESSION, view.id(), trigger,
                                trigger.name(), actorId, null)
                        .then(keycloak.endSession(realm, view.id()).onErrorResume(error -> {
                            log.warn("Keycloak session {} could not be ended: {}",
                                    new RevocationIdentifier(RevocationType.SESSION, view.id()).masked(),
                                    error.toString());
                            return Mono.empty();
                        }))
                        .thenReturn(1L))
                .count();
    }

    /** Cuts every active member of an organization. */
    public Mono<Void> cutOrganizationMembers(String organizationId, RevocationTrigger trigger, String actorId) {
        return cutAccounts(template.find(Query.query(Criteria.where("organizationId").is(organizationId)
                                .and("status").is(MemberStatus.ACTIVE)), OrganizationMember.class)
                .map(OrganizationMember::getUserId), trigger, actorId);
    }

    /** Cuts the access of each of the given accounts, one after another. */
    public Mono<Void> cutAccounts(Flux<String> accountIds, RevocationTrigger trigger, String actorId) {
        return accountIds.concatMap(id -> cutAccount(id, trigger, actorId)).then();
    }

    // ---- administrator ------------------------------------------------------------------------------

    /**
     * Revokes one token, one session or one user's tokens on an administrator's word. The
     * written reason is mandatory and at least {@link RevocationTrigger#MIN_WRITTEN_REASON}
     * characters.
     */
    public Mono<RevocationRecord> revokeManually(RevocationType type, String value, String reason,
                                                 String actorId) {
        return revokeManually(type, value, reason, actorId, null);
    }

    private Mono<RevocationRecord> revokeManually(RevocationType type, String value, String reason,
                                                  String actorId, String accountId) {
        MongoRevocationStore revocations = store.getIfAvailable();
        if (revocations == null) {
            return Mono.error(new IllegalStateException("token revocation is switched off"));
        }
        return revocations.revoke(type, value, RevocationTrigger.ADMIN_REVOKE, reason, actorId, null)
                .onErrorMap(IllegalArgumentException.class,
                        error -> new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, error.getMessage()))
                .flatMap(record -> record(record, accountId, actorId).thenReturn(record));
    }

    /** Revokes every token of an account, named by its account id, on an administrator's word. */
    public Mono<RevocationRecord> revokeAccountManually(String accountId, String reason, String actorId) {
        return accounts.require(accountId)
                .flatMap(account -> {
                    if (account.getKeycloakUserId() == null || account.getKeycloakUserId().isBlank()) {
                        return Mono.<RevocationRecord>error(new TranslatedRefusal(ErrorCode.USER_UNKNOWN,
                                "account " + accountId + " has no sign-in to revoke"));
                    }
                    return revokeManually(RevocationType.USER, account.getKeycloakUserId(), reason, actorId,
                            accountId);
                });
    }

    // ---- the person ----------------------------------------------------------------------------------

    /**
     * Ends every session of the caller except the one making this request: each is revoked and
     * ended in Keycloak. The current session stays valid so the response can be delivered; the
     * caller's own app then signs out in the usual way.
     *
     * @return the number of other sessions ended
     */
    public Mono<Long> signOutOtherSessions(String subject, String currentSessionId) {
        MongoRevocationStore revocations = store.getIfAvailable();
        if (revocations == null) {
            return Mono.error(new IllegalStateException("token revocation is switched off"));
        }
        return accounts.bySubject(subject)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN,
                        "no account for the token")))
                .flatMap(account -> endSessionsExcept(revocations, account, currentSessionId,
                        RevocationTrigger.LOGOUT, account.getId()));
    }

    /** Revokes one of a person's own sessions, after the caller has proved it is theirs. */
    public Mono<Void> revokeOwnSession(String sessionId, String actorId) {
        MongoRevocationStore revocations = store.getIfAvailable();
        if (revocations == null) {
            return Mono.empty();
        }
        return revocations.revoke(RevocationType.SESSION, sessionId, RevocationTrigger.LOGOUT,
                        RevocationTrigger.LOGOUT.name(), actorId, null)
                .then();
    }

    // ---- the log -------------------------------------------------------------------------------------

    /** The revocations still in force, newest first. */
    public Mono<RevocationRecordOffsetPage> active(RevocationType type, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        Criteria live = Criteria.where("expiresAt").gt(clock.instant());
        if (type != null) {
            live = live.and("type").is(type.name());
        }
        Query query = new Query(live);
        Mono<Long> total = template.count(query, RevocationRecord.class);
        Mono<java.util.List<RevocationRecordView>> rows = template.find(
                        new Query(live).with(Sort.by(Sort.Direction.DESC, "revokedAt"))
                                .skip(p.getOffset()).limit(p.getLimit()), RevocationRecord.class)
                .map(record -> new RevocationRecordView(record.getId(), record.getType(), record.getValue(),
                        record.getReason(), record.getTrigger(), record.getRevokedBy(),
                        record.getRevokedAt(), record.getExpiresAt()))
                .collectList();
        return Mono.zip(rows, total).map(t -> {
            int pages = (int) Math.ceil((double) t.getT2() / p.getLimit());
            return new RevocationRecordOffsetPage(t.getT1(), PageInfo.forOffset((int) (long) t.getT2(),
                    p.getLimit(), p.page(), pages, (long) p.getOffset() + p.getLimit() < t.getT2(),
                    p.page() > 0));
        });
    }

    // ---- audit ---------------------------------------------------------------------------------------

    private Mono<Void> record(RevocationRecord record, String accountId, String actorId) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("trigger", String.valueOf(record.getTrigger()));
        metadata.put("type", record.getType().name());
        metadata.put("identifier", new RevocationIdentifier(record.getType(), record.getValue()).masked());
        return audit.record(AuditLog.AuditAction.ACCESS_REVOKED, accountId == null ? "Revocation" : "User",
                        accountId == null ? record.getId() : accountId, actorId, metadata)
                .then()
                .onErrorResume(error -> {
                    log.error("Access was revoked ({}) but its audit row could not be written: {}",
                            metadata.get("identifier"), error.toString());
                    return Mono.empty();
                });
    }
}
