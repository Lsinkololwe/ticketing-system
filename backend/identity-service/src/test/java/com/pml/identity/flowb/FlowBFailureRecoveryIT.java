package com.pml.identity.flowb;

import com.pml.identity.domain.model.User;
import com.pml.identity.flowb.support.FaultInjectingProxy.Mode;
import com.pml.identity.flowb.support.KeycloakRegistrationClient.Registration;
import com.pml.identity.service.UserSyncService;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "If a step fails, the system must finish from where it stopped."
 *
 * <p>The Keycloak SPI deliberately swallows sync failures so a dead Identity Service cannot
 * break authentication. That choice is right for availability and creates a hazard for
 * consistency: the user exists in Keycloak and nowhere else. These tests break the hop on
 * purpose, prove the drift is real and detectable, then prove the reconciliation path converges
 * on exactly the state a clean run would have produced — including publishing the deferred
 * cross-service event exactly once.</p>
 */
@DisplayName("Flow B — failure and recovery")
class FlowBFailureRecoveryIT extends FlowBIntegrationSupport {

    @Autowired
    private UserSyncService userSyncService;

    @Test
    @DisplayName("R1–R3: sync hop down → drift → reconciliation converges and emits one event")
    void syncOutageIsRepairedByReconciliation() {
        // ── R1: cut the wire between Keycloak and the Identity Service ──────
        SYNC_PROXY.setMode(Mode.FAIL_503);

        String username = handle("outage");
        assertThat(REGISTRATION.register(Registration.organizer(username)).accepted())
                .as("a dead downstream must never block a human from registering")
                .isTrue();

        UserRepresentation keycloakUser = findKeycloakUser(username)
                .orElseThrow(() -> new AssertionError("Keycloak must still have created the user"));
        assertThat(keycloakEffectiveRealmRoles(keycloakUser.getId()))
                .as("role minting happens inside Keycloak and is unaffected by the outage")
                .contains("ORGANIZER", "CUSTOMER");

        awaitTrue("the SPI to have attempted the sync",
                () -> SYNC_PROXY.requestsReceived() > 0);
        assertThat(SYNC_PROXY.requestsForwarded())
                .as("nothing should have reached the Identity Service")
                .isZero();
        assertNotSynced(keycloakUser.getId());

        // the drift is detectable through the documented status endpoint
        HttpResponse<String> status = callIdentityService(
                "GET", "/api/internal/keycloak/sync/user/" + keycloakUser.getId(),
                null, internalServiceToken());
        assertThat(status.statusCode())
                .as("operators need a way to see that a Keycloak user has no MongoDB document")
                .isEqualTo(404);

        assertThat(awaitUserRegisteredEventsFor(keycloakUser.getId(), 1))
                .as("no event may be emitted for a user that was never persisted")
                .isEmpty();

        // ── R2: restore the wire and run reconciliation ─────────────────────
        SYNC_PROXY.setMode(Mode.PASS_THROUGH);

        User recovered = userSyncService.syncUserFromKeycloak(keycloakUser.getId())
                .block(Duration.ofSeconds(30));

        assertThat(recovered).as("reconciliation must produce the user").isNotNull();
        assertThat(recovered.getId()).isEqualTo(keycloakUser.getId());
        assertThat(recovered.getUsername()).isEqualTo(username);

        // the converged state is the happy-path state, not an approximation of it
        User persisted = findMongoUser(keycloakUser.getId()).orElseThrow();
        assertThat(persisted.getRoles())
                .as("recovery must mint the same roles the live path would have")
                .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);
        assertThat(persisted.isActive()).isTrue();
        assertThat(persisted.getFirstName()).isEqualTo("Flow");
        assertThat(persisted.getLastName()).isEqualTo("Bee");
        assertThat(persisted.isRegistrationEventPublished()).isTrue();

        // ── R3: the deferred event is published exactly once ────────────────
        List<Map<String, Object>> events =
                awaitUserRegisteredEventsFor(keycloakUser.getId(), 1);
        assertThat(events)
                .as("the cross-service event is owed to catalog and booking; recovery pays it")
                .hasSize(1);
        assertThat(roles(events.get(0)))
                .containsExactlyInAnyOrder("CUSTOMER", "ORGANIZER");
    }

    @Test
    @DisplayName("R4: a connection reset mid-sync is recovered the same way")
    void connectionResetIsRepaired() {
        SYNC_PROXY.setMode(Mode.RESET);

        String username = handle("reset");
        assertThat(REGISTRATION.register(Registration.organizer(username)).accepted()).isTrue();
        String userId = findKeycloakUser(username).orElseThrow().getId();
        assertNotSynced(userId);

        SYNC_PROXY.setMode(Mode.PASS_THROUGH);
        assertThat(userSyncService.syncUserFromKeycloak(userId).block(Duration.ofSeconds(30)))
                .isNotNull();

        assertThat(findMongoUser(userId).orElseThrow().getRoles())
                .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);
        assertThat(awaitUserRegisteredEventsFor(userId, 1)).hasSize(1);
    }

    @Test
    @DisplayName("R5: an Identity Service 500 leaves no half-written document")
    void serverErrorLeavesNoPartialState() {
        SYNC_PROXY.setMode(Mode.FAIL_500);

        String username = handle("err500");
        assertThat(REGISTRATION.register(Registration.organizer(username)).accepted()).isTrue();
        String userId = findKeycloakUser(username).orElseThrow().getId();

        assertNotSynced(userId);
        assertThat(awaitUserRegisteredEventsFor(userId, 1))
                .as("a failed write must not emit an event describing state that does not exist")
                .isEmpty();

        SYNC_PROXY.setMode(Mode.PASS_THROUGH);
        userSyncService.syncUserFromKeycloak(userId).block(Duration.ofSeconds(30));

        assertThat(findMongoUser(userId)).isPresent();
        assertThat(awaitUserRegisteredEventsFor(userId, 1)).hasSize(1);
    }

    @Test
    @DisplayName("R6–R7: bulk reconciliation repairs many users, and repeating it changes nothing")
    void bulkReconciliationIsCompleteAndIdempotent() {
        SYNC_PROXY.setMode(Mode.FAIL_503);

        List<String> drifted = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String username = handle("bulk");
            assertThat(REGISTRATION.register(Registration.organizer(username)).accepted()).isTrue();
            drifted.add(findKeycloakUser(username).orElseThrow().getId());
        }
        drifted.forEach(this::assertNotSynced);

        // ── R6: one operator action repairs all of them ─────────────────────
        SYNC_PROXY.setMode(Mode.PASS_THROUGH);
        userSyncService.syncAllUsersFromKeycloak().block(Duration.ofMinutes(2));

        // The destination is shared, so events are drained once and then attributed. Draining
        // per user would discard the other users' events while waiting for the first.
        List<Map<String, Object>> firstPass = collectUserRegisteredEvents(Duration.ofSeconds(10));

        for (String userId : drifted) {
            User user = findMongoUser(userId)
                    .orElseThrow(() -> new AssertionError("not repaired: " + userId));
            assertThat(user.getRoles())
                    .as("user %s", userId)
                    .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);
            assertThat(user.isRegistrationEventPublished()).isTrue();
            assertThat(eventsFor(firstPass, userId))
                    .as("each repaired user owes exactly one event")
                    .hasSize(1);
        }

        // ── R7: running it again must be a no-op ────────────────────────────
        drainOutboundEvents();
        userSyncService.syncAllUsersFromKeycloak().block(Duration.ofMinutes(2));

        List<Map<String, Object>> secondPass = collectUserRegisteredEvents(Duration.ofSeconds(10));
        for (String userId : drifted) {
            assertThat(eventsFor(secondPass, userId))
                    .as("a second reconciliation must not re-announce user %s", userId)
                    .isEmpty();
            assertThat(findMongoUser(userId).orElseThrow().getRoles())
                    .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);
        }
    }

    private static List<Map<String, Object>> eventsFor(List<Map<String, Object>> events,
                                                       String userId) {
        return events.stream().filter(event -> userId.equals(event.get("userId"))).toList();
    }

    @Test
    @DisplayName("R8: a Keycloak user missing profile fields fails loudly instead of corrupting the store")
    void unsyncableUserFailsLoudly() {
        // Admin-created users bypass the registration form's user-profile requirements, so they
        // can lack the firstName/lastName that users-schema.json insists on. The sync must
        // refuse the write rather than persist a document that violates the schema.
        String username = handle("nonames");
        String userId = createBareKeycloakUser(username);

        HttpResponse<String> response = callIdentityService(
                "POST", "/api/internal/keycloak/sync/user-data",
                """
                {
                  "id": "%s",
                  "username": "%s",
                  "email": "%s@flowb.test",
                  "emailVerified": false,
                  "enabled": true,
                  "roles": ["CUSTOMER"],
                  "accountTypes": ["CUSTOMER"],
                  "eventType": "ADMIN_CREATE",
                  "timestamp": 0
                }
                """.formatted(userId, username, username),
                internalServiceToken());

        assertThat(response.statusCode())
                .as("the caller must learn the sync failed; a 200 here would hide the drift")
                .isEqualTo(500);
        assertThat(findMongoUser(userId))
                .as("no partially valid document may be left behind")
                .isEmpty();
        assertThat(awaitUserRegisteredEventsFor(userId, 1))
                .as("nothing was persisted, so nothing may be announced")
                .isEmpty();
    }

    // ------------------------------------------------------------------

    private String createBareKeycloakUser(String username) {
        org.keycloak.representations.idm.UserRepresentation user =
                new org.keycloak.representations.idm.UserRepresentation();
        user.setUsername(username);
        user.setEmail(username + "@flowb.test");
        user.setEnabled(true);
        try (jakarta.ws.rs.core.Response response = realm().users().create(user)) {
            assertThat(response.getStatus())
                    .as("admin user creation should succeed")
                    .isEqualTo(201);
        }
        return findKeycloakUser(username).orElseThrow().getId();
    }

    @SuppressWarnings("unchecked")
    private static List<String> roles(Map<String, Object> event) {
        return (List<String>) event.get("roles");
    }
}
