package com.pml.identity.service;

import com.pml.identity.account.AccountService;
import com.pml.identity.account.AccountStates;
import com.pml.identity.account.KeycloakUserAdminPort;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a person can do to their own account, and the reads an administrator needs about
 * accounts: deletion requests with a grace period, live sessions, and why an account is suspended.
 *
 * <p>A deletion request only records intent and starts the clock; the account keeps working until
 * the person cancels or the grace period ends. Nothing is removed here (see {@link AccountService#delete},
 * which soft-deletes and is the step that runs once the request is due).
 */
@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private final ReactiveMongoTemplate template;
    private final AccountService accounts;
    private final OrganizationRepository organizations;
    private final AdminAuditService audit;
    private final KeycloakUserAdminPort keycloak;
    private final Clock clock;

    // ---- deletion request --------------------------------------------------------------------------

    /** Starts (or, if already open, returns) the caller's deletion request. */
    public Mono<User> requestDeletion(String subject, String reason) {
        return accounts.bySubject(subject)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN, "no account for the token")))
                .flatMap(account -> {
                    if (account.getDeletionRequestedAt() != null) {
                        return Mono.just(account);
                    }
                    AccountLifecycleRules.requireCanRequestDeletion(AccountStates.of(account));
                    // An organization owner must hand over or delete the organization first: deleting
                    // the account under it would orphan a tenant that still holds money and events.
                    return organizations.findByOwnerId(account.getId())
                            .filter(org -> org.getStatus() != OrganizationStatus.PENDING_DELETION)
                            .hasElement()
                            .flatMap(ownsOrganization -> {
                                if (ownsOrganization) {
                                    return Mono.<User>error(new TranslatedRefusal(ErrorCode.OWNER_CANNOT_BE_REMOVED,
                                            "transfer or delete the organization first"));
                                }
                                Instant now = clock.instant();
                                account.setDeletionRequestedAt(now);
                                account.setDeletionScheduledFor(AccountLifecycleRules.scheduledFor(now));
                                account.setUpdatedAt(now);
                                return template.save(account)
                                        .flatMap(saved -> record(saved, "DELETION_REQUESTED",
                                                AuditLog.AuditAction.ACCOUNT_DELETION_REQUESTED, reason).thenReturn(saved));
                            });
                });
    }

    /** Withdraws an open request. A request that is not open is refused, not ignored. */
    public Mono<User> cancelDeletion(String subject) {
        return accounts.bySubject(subject)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN, "no account for the token")))
                .flatMap(account -> {
                    if (account.getDeletionRequestedAt() == null) {
                        return Mono.<User>error(new TranslatedRefusal(ErrorCode.RESOURCE_CONFLICT,
                                "no deletion request is open"));
                    }
                    account.setDeletionRequestedAt(null);
                    account.setDeletionScheduledFor(null);
                    account.setUpdatedAt(clock.instant());
                    return template.save(account)
                            .flatMap(saved -> record(saved, "DELETION_CANCELLED",
                                    AuditLog.AuditAction.ACCOUNT_DELETION_CANCELLED, null).thenReturn(saved));
                });
    }

    private Mono<Void> record(User account, String kind, AuditLog.AuditAction action, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("by", account.getId());
        if (reason != null && !reason.isBlank()) {
            data.put("reason", reason.substring(0, Math.min(reason.length(), 200)));
        }
        AccountEvent event = AccountEvent.builder()
                .id(kind.toLowerCase() + ":" + account.getId() + ":" + UUID.randomUUID())
                .accountId(account.getId()).kind(kind).at(clock.instant()).data(data).build();
        return template.insert(event)
                .then(audit.record(action, "User", account.getId(), account.getId(), null))
                .then();
    }

    // ---- sessions ----------------------------------------------------------------------------------

    /** One live session of the caller. */
    public record Session(String id, String ipAddress, Instant startedAt, Instant lastAccessAt,
                           List<String> clients, boolean current) {
    }

    /** The caller's live sessions; {@code currentSessionId} marks the one making this request. */
    public Flux<Session> sessions(String subject, String currentSessionId) {
        return accounts.bySubject(subject)
                .filter(account -> account.getKeycloakUserId() != null)
                .flatMapMany(account -> keycloak.sessions(accounts.realmOf(account), account.getKeycloakUserId()))
                .map(view -> new Session(view.id(), view.ipAddress(), view.startedAt(), view.lastAccessAt(),
                        view.clients(), view.id().equals(currentSessionId)));
    }

    /**
     * Ends one of the caller's own sessions. The id must be one of the caller's sessions, so a
     * guessed or leaked session id of somebody else is refused as unknown, not acted on.
     */
    public Mono<Boolean> revokeSession(String subject, String sessionId) {
        return accounts.bySubject(subject)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.USER_UNKNOWN, "no account for the token")))
                .flatMap(account -> keycloak.sessions(accounts.realmOf(account), account.getKeycloakUserId())
                        .filter(view -> view.id().equals(sessionId))
                        .hasElements()
                        .flatMap(owned -> {
                            if (!owned) {
                                return Mono.<Boolean>error(new TranslatedRefusal(ErrorCode.RESOURCE_CONFLICT,
                                        "no such session"));
                            }
                            return keycloak.endSession(accounts.realmOf(account), sessionId)
                                    .then(audit.record(AuditLog.AuditAction.SESSION_REVOKED, "User", account.getId(),
                                            account.getId(), null))
                                    .thenReturn(true);
                        }));
    }

    // ---- why an account is suspended ---------------------------------------------------------------

    /** The reason given when the account was last suspended or locked; empty when there was none. */
    public Mono<String> suspensionReason(String accountId) {
        return template.findOne(Query.query(Criteria.where("accountId").is(accountId).and("kind").is("SUSPENDED"))
                        .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "at"))
                        .limit(1), AccountEvent.class)
                .mapNotNull(event -> event.getData() == null ? null : (String) event.getData().get("reason"));
    }
}
