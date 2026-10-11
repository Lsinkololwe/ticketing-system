package com.pml.identity.web.rest;

import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.security.revocation.KeycloakSessionRevoker;
import com.pml.identity.web.rest.dto.KeycloakEventDto;
import com.pml.identity.web.rest.dto.SyncResponse;
import com.pml.identity.workflow.usersync.UserSyncProcess;
import com.pml.identity.workflow.usersync.UserSyncRules;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Change;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Kind;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST Controller for Keycloak synchronization webhooks.
 *
 * Endpoints are called by the Keycloak UserSyncEventListener (in both realms) to notify the
 * Identity Service of user changes in Keycloak. The body is the slim event of CONTRACT 4.6; the
 * full-profile {@code /user-data} endpoint and the unauthenticated-looking {@code /health} are gone.
 *
 * Security:
 * - All endpoints require internal service authentication
 * - Uses OAuth2 client credentials flow from Keycloak EventListener
 *
 * These endpoints enable Keycloak → MongoDB synchronization, ensuring that
 * changes made in Keycloak (via Admin Console, custom authenticators, or
 * user self-service) are reflected in MongoDB.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/keycloak/sync")
@RequiredArgsConstructor
public class KeycloakSyncController {

    private final UserSyncProcess userSyncProcess;
    private final KeycloakProperties keycloak;
    private final KeycloakSessionRevoker sessionRevoker;

    /**
     * A Keycloak event notification.
     *
     * <p>A delete, login or profile event is recorded as a signal to the user's workflow and answered
     * with {@code 202}; an event nothing syncs is answered {@code 200} as skipped.
     *
     * @param event The Keycloak event data
     * @return SyncResponse with the result
     */
    @PostMapping("/event")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<SyncResponse>> handleEvent(@Valid @RequestBody KeycloakEventDto event) {
        log.info("Received Keycloak event: type={}, userId={}, realm={}", event.getEventType(), event.getUserId(), event.getRealm());

        if (!UserSyncRules.knownRealm(event.getRealm(), keycloak.getRealm(), keycloak.getStaffRealm())) {
            return Mono.just(ResponseEntity.ok(SyncResponse.skipped(event.getUserId(),
                    "Realm not synced: " + event.getRealm())));
        }
        if (KeycloakSessionRevoker.endsSession(event.getEventType())) {
            return revokeSession(event);
        }
        long timestamp = event.getTimestamp() == null ? 0L : event.getTimestamp();
        return UserSyncRules.kindOf(event.getEventType())
                .map(kind -> accepted(event.getUserId(), new Change(
                        UserSyncRules.listenerEventId(event.getEventId(), event.getUserId(), event.getEventType(), timestamp),
                        kind,
                        UserSyncRules.registration(event.getEventType(), null),
                        timestamp,
                        event.getRealm())))
                .orElseGet(() -> Mono.just(ResponseEntity.ok(SyncResponse.skipped(event.getUserId(),
                        "Event type not handled: " + event.getEventType()))));
    }

    /**
     * A Keycloak logout (or a refresh token the server refused) ends the SSO session, so its
     * {@code sid} is revoked: every access token minted for it stops working at the gateway and in
     * the services, while tokens of the user's other sessions are untouched. The write is an
     * idempotent upsert and happens here, in the request, not in a workflow: the listener retries
     * on a 5xx, and the revocation must not wait behind a queue. An event with no {@code sid}
     * has nothing to revoke and is skipped, never widened to the whole user.
     */
    private Mono<ResponseEntity<SyncResponse>> revokeSession(KeycloakEventDto event) {
        if (event.getSid() == null || event.getSid().isBlank()) {
            return Mono.just(ResponseEntity.ok(SyncResponse.skipped(event.getUserId(),
                    "No session id on " + event.getEventType())));
        }
        return sessionRevoker.revoke(event.getEventType(), event.getSid(), event.getRealm())
                .map(written -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(SyncResponse.success(event.getUserId(), "REVOKED", "Session revoked")))
                .onErrorResume(e -> {
                    log.error("Keycloak {} for user {} could not be recorded as a revocation: {}",
                            event.getEventType(), event.getUserId(), e.toString());
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(SyncResponse.error(event.getUserId(), "Revocation could not be recorded")));
                });
    }

    private Mono<ResponseEntity<SyncResponse>> accepted(String userId, Change change) {
        return userSyncProcess.accept(userId, change)
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(SyncResponse.success(userId, "ACCEPTED", "Change recorded; the sync runs in the user's workflow")))
                .onErrorResume(e -> {
                    log.error("Keycloak change for user {} could not be recorded: {}", userId, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(SyncResponse.error(userId, "Change could not be recorded")));
                });
    }
}
