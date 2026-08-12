package com.pml.identity.flowb;

import com.fasterxml.jackson.databind.JsonNode;
import com.pml.identity.flowb.support.KeycloakRegistrationClient.Outcome;
import com.pml.identity.flowb.support.KeycloakRegistrationClient.Registration;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.representations.idm.EventRepresentation;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OWASP Top 10 (2021) coverage for Flow B.
 *
 * <p>Registration is the moment an untrusted party first writes to the identity store, so the
 * controls that matter are the ones that stop that write from becoming privilege, injection or
 * silent corruption. Each nested class names the risk it defends.</p>
 */
@DisplayName("Flow B — OWASP compliance")
class FlowBOwaspComplianceIT extends FlowBIntegrationSupport {

    @Nested
    @DisplayName("A01 — Broken Access Control")
    class BrokenAccessControl {

        @Test
        @DisplayName("the sync endpoint refuses anonymous callers")
        void anonymousSyncIsRefused() {
            HttpResponse<String> response = callIdentityService(
                    "POST", "/api/internal/keycloak/sync/user-data", minimalPayload(), null);

            assertThat(response.statusCode())
                    .as("an unauthenticated caller could otherwise mint roles for any user")
                    .isEqualTo(401);
        }

        @Test
        @DisplayName("a normal user's token cannot reach the internal sync endpoint")
        void userTokenIsRefused() {
            String username = handle("lowpriv");
            Registration registration = Registration.customer(username);
            assertThat(REGISTRATION.register(registration).accepted()).isTrue();

            String userToken = REGISTRATION
                    .passwordGrantToken(username, registration.password())
                    .orElseThrow(() -> new AssertionError("could not authenticate the test user"));

            HttpResponse<String> response = callIdentityService(
                    "POST", "/api/internal/keycloak/sync/user-data", minimalPayload(), userToken);

            assertThat(response.statusCode())
                    .as("a CUSTOMER token carries no internal-write scope, so this is authenticated "
                            + "but not authorized")
                    .isEqualTo(403);
        }

        @Test
        @DisplayName("the service-account token with internal-write is accepted")
        void internalServiceTokenIsAccepted() {
            HttpResponse<String> response = callIdentityService(
                    "POST", "/api/internal/keycloak/sync/user-data",
                    minimalPayload(), internalServiceToken());

            assertThat(response.statusCode())
                    .as("this is the credential the Keycloak SPI actually uses; if it were "
                            + "rejected the whole flow would be broken")
                    .isEqualTo(200);
        }

        @Test
        @DisplayName("the full-resync endpoint is admin-only")
        void fullResyncRequiresAdmin() {
            HttpResponse<String> response = callIdentityService(
                    "POST", "/api/internal/keycloak/sync/all", null, internalServiceToken());

            assertThat(response.statusCode())
                    .as("a full resync rewrites every user document; internal-write is not enough")
                    .isEqualTo(403);
        }

        @ParameterizedTest(name = "self-assigning {0} through the registration form fails")
        @ValueSource(strings = {"ADMIN", "SUPER_ADMIN", "FINANCE", "SCANNER"})
        @DisplayName("privilege cannot be self-assigned at registration")
        void privilegeCannotBeSelfAssigned(String role) {
            String username = handle("esc");
            Outcome outcome = REGISTRATION.register(
                    Registration.of(username, List.of("CUSTOMER", role)));

            assertThat(outcome.accepted())
                    .as("vertical privilege escalation at the front door: %s", outcome.describe())
                    .isFalse();
            assertThat(findKeycloakUser(username)).isEmpty();

            long holders = realm().roles().list().stream()
                    .filter(r -> r.getName().equals(role))
                    .count();
            assertThat(holders)
                    .as("%s must not even exist as a self-assignable realm role in this realm", role)
                    .isZero();
        }
    }

    @Nested
    @DisplayName("A03 — Injection")
    class Injection {

        @ParameterizedTest(name = "first name payload {0} is rejected")
        @ValueSource(strings = {
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "Flow\"><svg/onload=alert(1)>"
        })
        @DisplayName("script payloads in a name never reach a store")
        void scriptPayloadsInNamesAreRejected(String payload) {
            String username = handle("xss");
            Outcome outcome = REGISTRATION.register(
                    Registration.organizer(username).withFirstName(payload));

            assertThat(outcome.accepted())
                    .as("person-name-prohibited-characters must reject this: %s", outcome.describe())
                    .isFalse();
            assertThat(findKeycloakUser(username)).isEmpty();
        }

        @ParameterizedTest(name = "username payload {0} is rejected")
        @ValueSource(strings = {
                "{\"$ne\":null}",
                "admin'; return true; //",
                "user$where"
        })
        @DisplayName("operator-shaped usernames are rejected before they reach MongoDB")
        void operatorShapedUsernamesAreRejected(String payload) {
            Outcome outcome = REGISTRATION.register(
                    Registration.organizer(handle("inj")).withUsername(payload));

            assertThat(outcome.accepted())
                    .as("Keycloak's username validator and users-schema.json both forbid this: %s",
                            outcome.describe())
                    .isFalse();
        }

        @Test
        @DisplayName("a header-injection payload in the email is rejected")
        void crlfInEmailIsRejected() {
            Outcome outcome = REGISTRATION.register(Registration.organizer(handle("crlf"))
                    .withEmail("victim@flowb.test\r\nBcc: attacker@evil.test"));

            assertThat(outcome.accepted())
                    .as("this address would otherwise be used to send verification mail: %s",
                            outcome.describe())
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("A04 / A08 — Insecure Design and Data Integrity")
    class DataIntegrity {

        @Test
        @DisplayName("the database itself refuses an unknown role, not just the application")
        void databaseRejectsUnknownRole() {
            Document rogue = new Document("_id", "owasp-a08-" + Instant.now().toEpochMilli())
                    .append("username", "rogueuser")
                    .append("email", "rogue@flowb.test")
                    .append("firstName", "Rogue")
                    .append("lastName", "Role")
                    .append("roles", List.of("ROOT"))
                    .append("createdAt", java.util.Date.from(Instant.now()));

            assertThatThrownBy(() -> mongoTemplate.getCollection("users")
                    .flatMap(collection -> reactor.core.publisher.Mono.from(collection.insertOne(rogue)))
                    .block(Duration.ofSeconds(10)))
                    .as("defence in depth: a compromised service must not be able to write a role "
                            + "the schema does not know")
                    .hasMessageContaining("validation");
        }

        @Test
        @DisplayName("the database refuses a user document with no role at all")
        void databaseRejectsRolelessUser() {
            Document roleless = new Document("_id", "owasp-a04-" + Instant.now().toEpochMilli())
                    .append("username", "rolelessuser")
                    .append("email", "roleless@flowb.test")
                    .append("firstName", "No")
                    .append("lastName", "Roles")
                    .append("roles", List.of())
                    .append("createdAt", java.util.Date.from(Instant.now()));

            assertThatThrownBy(() -> mongoTemplate.getCollection("users")
                    .flatMap(collection -> reactor.core.publisher.Mono.from(collection.insertOne(roleless)))
                    .block(Duration.ofSeconds(10)))
                    .as("an empty role set would make authorization checks ambiguous")
                    .hasMessageContaining("validation");
        }
    }

    @Nested
    @DisplayName("A05 / A07 — Misconfiguration and Authentication Failures")
    class ConfigurationAndAuthentication {

        @Test
        @DisplayName("the shipped realm keeps identity keys unique")
        void realmKeepsIdentityKeysUnique() {
            JsonNode realmJson = REALM.productionRealmJson();

            assertThat(realmJson.path("duplicateEmailsAllowed").asBoolean(false))
                    .as("duplicate emails would break email as an identity key")
                    .isFalse();
            assertThat(realmJson.path("bruteForceProtected").asBoolean(false))
                    .as("registration and login share one endpoint surface")
                    .isTrue();
            assertThat(realmJson.path("registrationAllowed").asBoolean(false))
                    .as("Flow B does not exist without self-registration")
                    .isTrue();
        }

        @Test
        @DisplayName("the shipped realm's event listeners are asserted explicitly")
        void realmEventListenersAreAsserted() {
            List<String> listeners = new java.util.ArrayList<>();
            REALM.productionRealmJson().path("eventsListeners")
                    .forEach(node -> listeners.add(node.asText()));

            assertThat(listeners)
                    .as("""
                            The user-sync listener is what carries a registration from Keycloak to
                            MongoDB. Without it in the shipped realm, a fresh deployment registers
                            users that never reach the Identity Service. The suite adds it as a
                            fixture (%s) purely so the rest of Flow B can be exercised.""",
                            REALM_FIXTURES_APPLIED)
                    .contains("user-sync");
        }

        @Test
        @DisplayName("credential strength: the realm's stance is recorded, and empty passwords still fail")
        void credentialStrengthStanceIsRecorded() {
            String policy = REALM.productionRealmJson().path("passwordPolicy").asText("");

            // OWASP A07. The realm ships with no password *strength* policy — that is a product
            // decision, kept deliberately, so this test does not assert one into existence. What
            // it does assert is that the controls the platform DOES rely on are actually in
            // force: Keycloak's own non-empty-password requirement, and brute-force lockout
            // (covered by bruteForceProtectionLocksTheAccount).
            //
            // Recorded exposure, should the decision ever be revisited: with policy "%s", a
            // one-character password is accepted at registration.
            String username = handle("weakpw");
            Outcome outcome = REGISTRATION.register(
                    Registration.organizer(username).withPassword(""));

            assertThat(outcome.accepted())
                    .as("an empty password must never create an account (policy in effect: '%s')",
                            policy)
                    .isFalse();
            assertThat(findKeycloakUser(username)).isEmpty();

            assertThat(REALM.productionRealmJson().path("bruteForceProtected").asBoolean(false))
                    .as("with no strength policy, lockout is the control that carries A07")
                    .isTrue();
        }

        @Test
        @DisplayName("repeated failed logins lock the account")
        void bruteForceProtectionLocksTheAccount() {
            String username = handle("brute");
            Registration registration = Registration.customer(username);
            assertThat(REGISTRATION.register(registration).accepted()).isTrue();

            int failureFactor = REALM.productionRealmJson().path("failureFactor").asInt(5);
            for (int attempt = 0; attempt <= failureFactor; attempt++) {
                REGISTRATION.attemptPasswordLogin(username, "definitely-not-the-password");
            }

            assertThat(REGISTRATION.passwordGrantToken(username, registration.password()))
                    .as("after %d failures the correct password must stop working too, "
                            + "otherwise the lockout is decorative", failureFactor + 1)
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("A09 — Security Logging and Monitoring Failures")
    class Logging {

        @Test
        @DisplayName("a registration is recorded as an auditable Keycloak event")
        void registrationIsAudited() {
            String username = handle("audit");
            assertThat(REGISTRATION.register(Registration.organizer(username)).accepted()).isTrue();
            String userId = findKeycloakUser(username).orElseThrow().getId();

            awaitTrue("a REGISTER event for " + userId, () -> registerEvents(userId).size() >= 1);

            EventRepresentation event = registerEvents(userId).get(0);
            assertThat(event.getRealmId()).isNotBlank();
            assertThat(event.getIpAddress())
                    .as("the source address is what makes an abuse investigation possible")
                    .isNotBlank();
        }

        private List<EventRepresentation> registerEvents(String userId) {
            return realm().getEvents().stream()
                    .filter(event -> "REGISTER".equals(event.getType()))
                    .filter(event -> userId.equals(event.getUserId()))
                    .toList();
        }
    }

    // ------------------------------------------------------------------

    private String minimalPayload() {
        return """
                {
                  "id": "owasp-probe-user",
                  "username": "owaspprobe",
                  "email": "owaspprobe@flowb.test",
                  "firstName": "Owasp",
                  "lastName": "Probe",
                  "emailVerified": false,
                  "enabled": true,
                  "roles": ["CUSTOMER"],
                  "accountTypes": ["CUSTOMER"],
                  "eventType": "UPDATE_PROFILE",
                  "timestamp": 0
                }
                """;
    }
}
