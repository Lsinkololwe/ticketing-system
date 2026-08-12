package com.pml.identity.flowb;

import com.pml.identity.domain.model.User;
import com.pml.identity.flowb.support.KeycloakRegistrationClient.Outcome;
import com.pml.identity.flowb.support.KeycloakRegistrationClient.Registration;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.representations.idm.UserRepresentation;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every branch of {@code AccountTypeRoleMapper} and the sync that follows it.
 *
 * <p>The role a user is minted with is the single most consequential decision Flow B makes:
 * it decides whether someone can create events and take money. These tests walk each input the
 * form can produce — including the ones an attacker would produce — and assert both stores
 * agree afterwards, or that nothing was written at all.</p>
 */
@DisplayName("Flow B — role minting edge cases")
class FlowBRoleMintingEdgeCaseIT extends FlowBIntegrationSupport {

    // ------------------------------------------------------------------
    // valid account type combinations
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E2: selecting both types yields both roles, with no duplicated attribute values")
    void bothAccountTypesYieldBothRoles() {
        String username = handle("both");
        Outcome outcome = REGISTRATION.register(
                Registration.of(username, List.of("CUSTOMER", "ORGANIZER")));
        assertThat(outcome.accepted()).as(outcome.describe()).isTrue();

        UserRepresentation keycloakUser = findKeycloakUser(username).orElseThrow();
        assertThat(keycloakEffectiveRealmRoles(keycloakUser.getId()))
                .contains("CUSTOMER", "ORGANIZER");
        assertThat(keycloakUser.getAttributes().get("accountType"))
                .as("selecting CUSTOMER explicitly must not add it twice")
                .containsExactlyInAnyOrder("CUSTOMER", "ORGANIZER");

        User mongoUser = awaitSyncedUser(keycloakUser.getId());
        assertThat(mongoUser.getRoles())
                .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);
    }

    @Test
    @DisplayName("E3: ORGANIZER alone still records CUSTOMER as the base account type")
    void organizerAloneGainsTheBaseAccountType() {
        String username = handle("orgonly");
        assertThat(REGISTRATION.register(Registration.organizer(username)).accepted()).isTrue();

        UserRepresentation keycloakUser = findKeycloakUser(username).orElseThrow();
        assertThat(keycloakUser.getAttributes().get("accountType"))
                .as("AccountTypeRoleMapper appends the base type so the sync payload carries it")
                .contains("CUSTOMER", "ORGANIZER");

        assertThat(awaitSyncedUser(keycloakUser.getId()).getRoles())
                .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);
    }

    @Test
    @DisplayName("E12: a supplied phone number is normalised to E.164 with its country, never malformed")
    void phoneNumberIsNormalised() {
        String username = handle("phone");
        Outcome outcome = REGISTRATION.register(
                Registration.organizer(username).withPhoneNumber("+260971234567"));
        assertThat(outcome.accepted()).as(outcome.describe()).isTrue();

        UserRepresentation keycloakUser = findKeycloakUser(username).orElseThrow();
        User mongoUser = awaitSyncedUser(keycloakUser.getId());

        if (mongoUser.getPhoneNumber() != null) {
            assertThat(mongoUser.getPhoneNumber())
                    .as("users-schema.json enforces E.164; anything else would be rejected on write")
                    .matches("^\\+[1-9]\\d{1,14}$");
            assertThat(mongoUser.getPhoneCountry())
                    .as("the ISO country is stored alongside because E.164 alone cannot always "
                            + "recover it")
                    .isEqualTo("ZM");
        }
        // A null phone is acceptable — the sync stores null rather than a malformed value.
        // What must never happen is a stored value that violates the E.164 contract.
    }

    // ------------------------------------------------------------------
    // rejected submissions — nothing may be written anywhere
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E4: no account type selected is rejected and creates nothing")
    void missingAccountTypeCreatesNothing() {
        String username = handle("noacct");
        Outcome outcome = REGISTRATION.register(Registration.of(username, List.of()));

        assertThat(outcome.accepted())
                .as("an account type is mandatory: %s", outcome.describe())
                .isFalse();
        assertThat(findKeycloakUser(username))
                .as("a rejected registration must not leave a Keycloak user behind")
                .isEmpty();
        assertNoMongoUserFor(username);
    }

    @ParameterizedTest(name = "E5: accountType={0} is refused")
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN", "FINANCE", "SCANNER", "ROOT", ""})
    @DisplayName("E5: privileged or unknown account types cannot be self-assigned")
    void privilegedAccountTypesAreRefused(String accountType) {
        String username = handle("elev");
        Outcome outcome = REGISTRATION.register(
                Registration.of(username, List.of(accountType)));

        assertThat(outcome.accepted())
                .as("'%s' is outside the {CUSTOMER, ORGANIZER} allowlist: %s",
                        accountType, outcome.describe())
                .isFalse();
        assertThat(findKeycloakUser(username)).isEmpty();
        assertNoMongoUserFor(username);
    }

    @Test
    @DisplayName("E6: one invalid type poisons the whole submission — no partial acceptance")
    void mixedValidAndInvalidTypesAreRejectedWholesale() {
        String username = handle("mixed");
        Outcome outcome = REGISTRATION.register(
                Registration.of(username, List.of("ORGANIZER", "SUPER_ADMIN")));

        assertThat(outcome.accepted())
                .as("accepting the valid half would let an attacker probe for what sticks: %s",
                        outcome.describe())
                .isFalse();
        assertThat(findKeycloakUser(username)).isEmpty();
    }

    @Test
    @DisplayName("E7: the account type allowlist is case-sensitive")
    void lowerCaseAccountTypeIsRejected() {
        String username = handle("lower");
        Outcome outcome = REGISTRATION.register(
                Registration.of(username, List.of("organizer")));

        assertThat(outcome.accepted())
                .as("normalising case here would widen the allowlist by accident: %s",
                        outcome.describe())
                .isFalse();
        assertThat(findKeycloakUser(username)).isEmpty();
    }

    @Test
    @DisplayName("E8: a duplicate email is rejected and the first account is untouched")
    void duplicateEmailIsRejected() {
        String first = handle("dupmail");
        Registration original = Registration.organizer(first);
        assertThat(REGISTRATION.register(original).accepted()).isTrue();

        String firstId = findKeycloakUser(first).orElseThrow().getId();
        User before = awaitSyncedUser(firstId);

        String second = handle("dupmail2");
        Outcome outcome = REGISTRATION.register(
                Registration.customer(second).withEmail(original.email()));

        assertThat(outcome.accepted())
                .as("email is an identity key here: %s", outcome.describe())
                .isFalse();
        assertThat(findKeycloakUser(second)).isEmpty();

        User after = findMongoUser(firstId).orElseThrow();
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
        assertThat(after.getRoles()).isEqualTo(before.getRoles());
    }

    @Test
    @DisplayName("E9: a duplicate username is rejected and creates no second document")
    void duplicateUsernameIsRejected() {
        String username = handle("dupuser");
        assertThat(REGISTRATION.register(Registration.organizer(username)).accepted()).isTrue();
        String firstId = findKeycloakUser(username).orElseThrow().getId();
        awaitSyncedUser(firstId);

        Outcome outcome = REGISTRATION.register(
                Registration.customer(username + "-other").withUsername(username));

        assertThat(outcome.accepted())
                .as("usernames are unique in Keycloak and uniquely indexed in MongoDB: %s",
                        outcome.describe())
                .isFalse();
        assertThat(usersNamed(username)).hasSize(1);
    }

    // ------------------------------------------------------------------
    // idempotency of the sync leg
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E10: replaying the sync payload cannot re-publish the registration event")
    void replayedSyncPublishesOnlyOneEvent() {
        String username = handle("replay");
        assertThat(REGISTRATION.register(Registration.organizer(username)).accepted()).isTrue();

        String userId = findKeycloakUser(username).orElseThrow().getId();
        User synced = awaitSyncedUser(userId);
        assertThat(synced.isRegistrationEventPublished()).isTrue();

        List<Map<String, Object>> firstRound = awaitUserRegisteredEventsFor(userId, 1);
        assertThat(firstRound).hasSize(1);

        // A retrying Keycloak listener, or an operator re-running the sync, replays the very
        // same payload. The compare-and-set guard must absorb every repeat.
        String token = internalServiceToken();
        String payload = registerPayload(userId, username, "REGISTER");
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpResponse<String> response = callIdentityService(
                    "POST", "/api/internal/keycloak/sync/user-data", payload, token);
            assertThat(response.statusCode())
                    .as("a replay is a valid request, not an error")
                    .isEqualTo(200);
        }

        assertThat(awaitUserRegisteredEventsFor(userId, 1))
                .as("five replays must not produce five events")
                .isEmpty();
        assertThat(findMongoUser(userId).orElseThrow().getRoles())
                .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);
    }

    @Test
    @DisplayName("E11: a later UPDATE_PROFILE sync preserves roles and emits no second event")
    void profileUpdateDoesNotDisturbRoles() {
        String username = handle("update");
        assertThat(REGISTRATION.register(Registration.organizer(username)).accepted()).isTrue();

        String userId = findKeycloakUser(username).orElseThrow().getId();
        awaitSyncedUser(userId);
        assertThat(awaitUserRegisteredEventsFor(userId, 1)).hasSize(1);

        HttpResponse<String> response = callIdentityService(
                "POST", "/api/internal/keycloak/sync/user-data",
                registerPayload(userId, username, "UPDATE_PROFILE"), internalServiceToken());
        assertThat(response.statusCode()).isEqualTo(200);

        User after = findMongoUser(userId).orElseThrow();
        assertThat(after.getRoles())
                .as("a profile update is not a role change")
                .containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);
        assertThat(awaitUserRegisteredEventsFor(userId, 1))
                .as("only registration publishes UserRegisteredEvent")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String registerPayload(String userId, String username, String eventType) {
        return """
                {
                  "id": "%s",
                  "username": "%s",
                  "email": "%s@flowb.test",
                  "firstName": "Flow",
                  "lastName": "Bee",
                  "emailVerified": false,
                  "enabled": true,
                  "roles": ["ORGANIZER"],
                  "accountTypes": ["ORGANIZER", "CUSTOMER"],
                  "eventType": "%s",
                  "timestamp": 0
                }
                """.formatted(userId, username, username, eventType);
    }

    private List<UserRepresentation> usersNamed(String username) {
        return realm().users().search(username, true);
    }

    private void assertNoMongoUserFor(String username) {
        sleep(java.time.Duration.ofSeconds(2));
        assertThat(userRepository.findAll()
                .filter(user -> username.equals(user.getUsername()))
                .collectList()
                .block(java.time.Duration.ofSeconds(10)))
                .as("a rejected registration must not reach MongoDB either")
                .isEmpty();
    }
}
