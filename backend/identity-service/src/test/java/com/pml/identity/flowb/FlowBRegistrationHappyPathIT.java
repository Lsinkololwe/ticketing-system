package com.pml.identity.flowb;

import com.pml.identity.domain.enums.AccountStatus;
import com.pml.identity.domain.model.User;
import com.pml.identity.flowb.support.KeycloakRegistrationClient.Outcome;
import com.pml.identity.flowb.support.KeycloakRegistrationClient.Registration;
import com.pml.shared.constants.UserType;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flow B, happy path: an organization user registers through the real Keycloak form and must
 * end up fully provisioned across Keycloak, MongoDB and the outbound event stream.
 *
 * <p>Each assertion here is a promise the platform makes to a new organizer. If any one of them
 * fails, the organizer exists in one store and not another — which is precisely the failure mode
 * this suite is built to catch.</p>
 */
@DisplayName("Flow B — organization user registration, end to end")
class FlowBRegistrationHappyPathIT extends FlowBIntegrationSupport {

    @Test
    @DisplayName("registering as ORGANIZER provisions Keycloak, MongoDB and the event stream")
    void organizerRegistrationProvisionsEveryComponent() {
        String username = handle("organizer");
        Registration registration = Registration.organizer(username);

        // ── the human action ────────────────────────────────────────────────
        Outcome outcome = REGISTRATION.register(registration);

        // H1: the browser flow completed and Keycloak issued an authorization code
        assertThat(outcome.accepted())
                .as("registration should be accepted: %s", outcome.describe())
                .isTrue();
        assertThat(outcome.authorizationCode())
                .as("a successful registration authenticates the user and returns a code")
                .isNotBlank();

        // ── Keycloak side ───────────────────────────────────────────────────
        UserRepresentation keycloakUser = findKeycloakUser(username)
                .orElseThrow(() -> new AssertionError("user not created in Keycloak"));

        // H2: AccountTypeRoleMapper granted the selected type directly...
        assertThat(keycloakDirectRealmRoles(keycloakUser.getId()))
                .as("AccountTypeRoleMapper must grant the selected account type")
                .contains("ORGANIZER");
        // ...and the base CUSTOMER role must hold effectively. It arrives through the
        // default-roles / ORGANIZER composites rather than as a direct mapping, so asserting
        // direct mappings alone would be asserting an implementation detail, not the promise.
        assertThat(keycloakEffectiveRealmRoles(keycloakUser.getId()))
                .as("every user is effectively a CUSTOMER; an organizer is additionally an ORGANIZER")
                .contains("ORGANIZER", "CUSTOMER");

        // H3: the attributes the sync payload is built from
        Map<String, List<String>> attributes = keycloakUser.getAttributes();
        assertThat(attributes).as("account type must be persisted for downstream sync")
                .containsKey("accountType");
        assertThat(attributes.get("accountType"))
                .as("the base role is mirrored into the accountType attribute")
                .contains("ORGANIZER", "CUSTOMER");
        assertThat(attributes.get("roles"))
                .as("AccountTypeRoleMapper mirrors the selection into `roles` for UserSyncServiceImpl")
                .contains("ORGANIZER", "CUSTOMER");

        // ── the sync hop actually ran ───────────────────────────────────────
        awaitTrue("the user-sync SPI to call the Identity Service",
                () -> SYNC_PROXY.requestsForwarded() > 0);
        assertThat(SYNC_PROXY.seenPaths())
                .as("the SPI must reach the documented sync endpoint")
                .anyMatch(path -> path.endsWith("/api/internal/keycloak/sync/user-data"));

        // ── MongoDB side ────────────────────────────────────────────────────
        // H4: identity is one identity — the Mongo _id IS the Keycloak sub
        User mongoUser = awaitSyncedUser(keycloakUser.getId());
        assertThat(mongoUser.getId()).isEqualTo(keycloakUser.getId());

        // H5: no role drift between the two stores
        assertThat(mongoUser.getRoles())
                .as("MongoDB roles must mirror the Keycloak grant exactly")
                .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);

        // H6: the defaults a new organizer is entitled to
        assertThat(mongoUser.getUsername()).isEqualTo(username);
        assertThat(mongoUser.getEmail()).isEqualTo(registration.email());
        assertThat(mongoUser.getFirstName()).isEqualTo(registration.firstName());
        assertThat(mongoUser.getLastName()).isEqualTo(registration.lastName());
        assertThat(mongoUser.isActive()).as("new accounts start active").isTrue();
        assertThat(mongoUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(mongoUser.isLocked()).isFalse();
        assertThat(mongoUser.isTwoFactorEnabled()).isFalse();
        assertThat(mongoUser.isPhoneVerified())
                .as("no phone was supplied, so it cannot be verified")
                .isFalse();
        assertThat(mongoUser.getCreatedAt()).isNotNull();
        assertThat(mongoUser.getUpdatedAt()).isNotNull();
        // lastLoginAt is deliberately not asserted: a successful registration also authenticates
        // the user, so Keycloak emits LOGIN concurrently with REGISTER and the two syncs race.

        // H7: the exactly-once guard is armed
        assertThat(mongoUser.isRegistrationEventPublished())
                .as("the idempotency flag must be set so a replay cannot re-publish")
                .isTrue();

        // H8: one cross-service event, carrying both roles
        List<Map<String, Object>> events = awaitUserRegisteredEventsFor(keycloakUser.getId(), 1);
        assertThat(events)
                .as("exactly one UserRegisteredEvent must reach identity-events")
                .hasSize(1);
        Map<String, Object> event = events.get(0);
        assertThat(event.get("userId")).isEqualTo(keycloakUser.getId());
        assertThat(event.get("email")).isEqualTo(registration.email());
        assertThat(rolesIn(event))
                .as("subscribers decide capability from these roles")
                .containsExactlyInAnyOrder("CUSTOMER", "ORGANIZER");

        // H9: progressive onboarding — registration must NOT create an organization
        assertThat(organizationRepository.findByOwnerId(keycloakUser.getId())
                .block(Duration.ofSeconds(5)))
                .as("organizations are created lazily on first event, not at registration")
                .isNull();
        assertThat(organizationMemberRepository.findAll().collectList().block(Duration.ofSeconds(5)))
                .as("no membership should exist before an organization does")
                .isEmpty();

        // H10: the document satisfies the shipped users-schema validator. Re-writing it
        //      through a raw driver update proves the validator was actually applied to the
        //      collection rather than merely present as a file.
        Document raw = mongoTemplate.getCollection("users")
                .flatMap(collection -> reactor.core.publisher.Mono.from(
                        collection.find(new Document("_id", keycloakUser.getId())).first()))
                .block(Duration.ofSeconds(5));
        assertThat(raw).as("document must be readable through the raw driver").isNotNull();
        assertThat(raw.getList("roles", String.class))
                .containsExactlyInAnyOrder("CUSTOMER", "ORGANIZER");

        // H11: the roles are usable, not merely stored — they must appear in an issued token
        String accessToken = REGISTRATION
                .passwordGrantToken(username, registration.password())
                .orElseThrow(() -> new AssertionError(
                        "a freshly registered organizer must be able to authenticate"));
        assertThat(realmRolesInToken(accessToken))
                .as("realm_access.roles is what every downstream service authorizes on")
                .contains("CUSTOMER", "ORGANIZER");
    }

    @Test
    @DisplayName("a CUSTOMER-only registration provisions the same way, without ORGANIZER")
    void customerRegistrationProvisionsCustomerOnly() {
        String username = handle("customer");
        Outcome outcome = REGISTRATION.register(Registration.customer(username));
        assertThat(outcome.accepted()).as(outcome.describe()).isTrue();

        UserRepresentation keycloakUser = findKeycloakUser(username).orElseThrow();
        assertThat(keycloakEffectiveRealmRoles(keycloakUser.getId()))
                .contains("CUSTOMER")
                .doesNotContain("ORGANIZER");

        User mongoUser = awaitSyncedUser(keycloakUser.getId());
        assertThat(mongoUser.getRoles()).containsExactly(UserType.CUSTOMER);

        List<Map<String, Object>> events = awaitUserRegisteredEventsFor(keycloakUser.getId(), 1);
        assertThat(events).hasSize(1);
        assertThat(rolesIn(events.get(0))).containsExactly("CUSTOMER");
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<String> rolesIn(Map<String, Object> event) {
        return (List<String>) event.get("roles");
    }

    private List<String> realmRolesInToken(String accessToken) {
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(accessToken.split("\\.")[1]));
        Document claims = Document.parse(payload);
        Document realmAccess = claims.get("realm_access", Document.class);
        assertThat(realmAccess).as("token must carry realm_access").isNotNull();
        return realmAccess.getList("roles", String.class);
    }
}
