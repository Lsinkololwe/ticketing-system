package com.pml.identity.infrastructure.keycloak;

import com.pml.identity.account.KeycloakAccountPort;
import com.pml.identity.account.KeycloakUserAdminPort.KeycloakUserView;
import com.pml.identity.config.KeycloakProperties;
import com.pml.shared.constants.UserType;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link KeycloakService} against a real Keycloak 26.5.2: the behaviours the account process rests on
 * and that a fake can only claim - a 409 on create is read back by exact username, an update sends
 * the whole user so nothing else on it is lost, disabling keeps the user, a role is granted and
 * revoked by id, and ending sessions really ends them.
 *
 * <p>The realms are small ones built for this test (a buyer realm with optional email and names, an
 * unmanaged-attribute policy that lets the admin API set {@code accountId}, and a staff realm); the
 * production realm files are templates the keycloak-extensions work owns.
 */
@Tag("L2")
@Tag("ET-IDN-004")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.testcontainers.junit.jupiter.Testcontainers
@DisplayName("ET-IDN-004 · KeycloakService writes users by id against a real Keycloak")
class KeycloakServiceContainerTest {

    @org.testcontainers.junit.jupiter.Container
    private static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.5.2")
            .withRealmImportFiles("/keycloak/myticketzm-realm.json", "/keycloak/myticketzm-admin-realm.json");

    private static KeycloakService service;
    private static Keycloak admin;
    private static final String BUYERS = "myticketzm";
    private static final String STAFF = "myticketzm-admin";

    @BeforeAll
    static void start() {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setServerUrl(KEYCLOAK.getAuthServerUrl());
        properties.setAdminUsername(KEYCLOAK.getAdminUsername());
        properties.setAdminPassword(KEYCLOAK.getAdminPassword());
        service = new KeycloakService(properties);
        admin = KEYCLOAK.getKeycloakAdminClient();
    }

    private static UserRepresentation reload(String keycloakUserId) {
        return admin.realm(BUYERS).users().get(keycloakUserId).toRepresentation();
    }

    @Test
    @Order(1)
    @DisplayName("an admin login that is not configured fails naming the property, with no admin/admin fallback")
    void noDefaultAdminCredentials() {
        KeycloakService unconfigured = new KeycloakService(new KeycloakProperties());

        assertThatThrownBy(() -> unconfigured.findByUsername("anyone").block())
                .isInstanceOf(KeycloakWriteFailed.class)
                .hasMessageContaining("keycloak.server-url");
        KeycloakProperties noPassword = new KeycloakProperties();
        noPassword.setServerUrl(KEYCLOAK.getAuthServerUrl());
        assertThatThrownBy(() -> new KeycloakService(noPassword).findByUsername("anyone").block())
                .isInstanceOf(KeycloakWriteFailed.class)
                .hasMessageContaining("keycloak.admin-password");
    }

    @Test
    @Order(2)
    @DisplayName("createUser makes a user whose username is the account id, enabled, with the CUSTOMER role and the accountId attribute")
    void createUser() {
        String accountId = UUID.randomUUID().toString();

        String keycloakUserId = service.createUser(accountId, true, UserType.CUSTOMER).block();

        UserRepresentation user = reload(keycloakUserId);
        assertThat(user.getUsername()).isEqualTo(accountId);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getEmail()).as("email is optional").isNull();
        assertThat(user.getAttributes().get("accountId")).containsExactly(accountId);
        assertThat(admin.realm(BUYERS).users().get(keycloakUserId).roles().realmLevel().listAll())
                .extracting(role -> role.getName()).contains("CUSTOMER");
        // Keycloak mints the id itself - a supplied one is ignored on create - so a token's sub is the
        // Keycloak id and an account is found from it by `keycloakUserId`, never by sub == account id.
        assertThat(keycloakUserId).isNotEqualTo(accountId);
    }

    @Test
    @Order(3)
    @DisplayName("a 409 on create reads back the user with that exact username; it never adopts anyone else's user and creates no second")
    void createIsRepeatable() {
        String accountId = UUID.randomUUID().toString();
        String first = service.createUser(accountId, true, UserType.CUSTOMER).block();
        String second = service.createUser(accountId, true, UserType.CUSTOMER).block();

        assertThat(second).isEqualTo(first);
        assertThat(admin.realm(BUYERS).users().searchByUsername(accountId, true)).hasSize(1);

        // a user that merely shares an email is a different person
        UserRepresentation stranger = new UserRepresentation();
        stranger.setUsername("stranger-" + UUID.randomUUID());
        stranger.setEmail("shared@example.com");
        stranger.setEnabled(true);
        try (Response response = admin.realm(BUYERS).users().create(stranger)) {
            assertThat(response.getStatus()).isEqualTo(201);
        }
        String other = UUID.randomUUID().toString();
        String otherId = service.createUser(other, true, UserType.CUSTOMER).block();
        assertThat(otherId).isNotEqualTo(first);
        assertThat(reload(otherId).getUsername()).isEqualTo(other);
    }

    @Test
    @Order(4)
    @DisplayName("applyAttributesAndRoles merges into the full representation: what else was on the user survives the PUT")
    void applyMergesIntoTheFullRepresentation() {
        String accountId = UUID.randomUUID().toString();
        String keycloakUserId = service.createUser(accountId, true, null).block();
        // something an operator put on the user that identity-service does not manage
        UserRepresentation existing = reload(keycloakUserId);
        existing.setFirstName("Chanda");
        existing.getAttributes().put("supportNote", List.of("vip"));
        admin.realm(BUYERS).users().get(keycloakUserId).update(existing);

        service.applyAttributesAndRoles(keycloakUserId,
                new KeycloakAccountPort.AccountAttributes(accountId, "chanda@example.com", true, "Chanda M", "en"),
                EnumSet.of(UserType.CUSTOMER)).block();
        service.applyAttributesAndRoles(keycloakUserId,
                new KeycloakAccountPort.AccountAttributes(accountId, "chanda@example.com", true, "Chanda M", "en"),
                EnumSet.of(UserType.CUSTOMER)).block();

        UserRepresentation merged = reload(keycloakUserId);
        assertThat(merged.getFirstName()).isEqualTo("Chanda");
        assertThat(merged.getEmail()).isEqualTo("chanda@example.com");
        assertThat(merged.isEmailVerified()).isTrue();
        assertThat(merged.getAttributes().get("supportNote")).containsExactly("vip");
        assertThat(merged.getAttributes().get("accountId")).containsExactly(accountId);
        assertThat(admin.realm(BUYERS).users().get(keycloakUserId).roles().realmLevel().listAll())
                .extracting(role -> role.getName()).containsOnlyOnce("CUSTOMER");
    }

    @Test
    @Order(5)
    @DisplayName("disabling and re-enabling keep everything else on the user; the user is never deleted")
    void setEnabledKeepsTheUser() {
        String accountId = UUID.randomUUID().toString();
        String keycloakUserId = service.createUser(accountId, true, UserType.CUSTOMER).block();
        service.applyAttributesAndRoles(keycloakUserId,
                new KeycloakAccountPort.AccountAttributes(accountId, "d@example.com", true, null, null),
                EnumSet.of(UserType.CUSTOMER)).block();

        service.setEnabled(keycloakUserId, false).block();
        UserRepresentation disabled = reload(keycloakUserId);
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.getEmail()).isEqualTo("d@example.com");
        assertThat(disabled.getAttributes().get("accountId")).containsExactly(accountId);

        service.setEnabled(keycloakUserId, true).block();
        assertThat(reload(keycloakUserId).isEnabled()).isTrue();
        assertThat(service.findByUsername(accountId).block().enabled()).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("findByUsername is exact: a prefix or another user's name finds nothing")
    void findByUsernameIsExact() {
        String accountId = UUID.randomUUID().toString();
        String keycloakUserId = service.createUser(accountId, true, null).block();

        assertThat(service.findByUsername(accountId).block().keycloakUserId()).isEqualTo(keycloakUserId);
        assertThat(service.findByUsername(accountId.substring(0, 12)).block()).isNull();
        assertThat(service.findByUsername("nobody-" + UUID.randomUUID()).block()).isNull();
    }

    @Test
    @Order(7)
    @DisplayName("roles are granted and revoked by id; revoking what is not held is not an error")
    void rolesByKeycloakId() {
        String keycloakUserId = service.createUser(UUID.randomUUID().toString(), true, UserType.CUSTOMER).block();

        service.grantRealmRole(keycloakUserId, "ORGANIZER").block();
        service.grantRealmRole(keycloakUserId, "ORGANIZER").block();
        assertThat(roleNames(keycloakUserId)).contains("CUSTOMER", "ORGANIZER");

        service.revokeRealmRole(keycloakUserId, "ORGANIZER").block();
        service.revokeRealmRole(keycloakUserId, "ORGANIZER").block();
        assertThat(roleNames(keycloakUserId)).doesNotContain("ORGANIZER");
        service.revokeRealmRole(UUID.randomUUID().toString(), "ORGANIZER").block();
    }

    @Test
    @Order(7)
    @DisplayName("roles can be granted and revoked by the account id (the username), which is what onboarding holds")
    void rolesByAccountId() {
        String accountId = UUID.randomUUID().toString();
        String keycloakUserId = service.createUser(accountId, true, UserType.CUSTOMER).block();
        assertThat(accountId).isNotEqualTo(keycloakUserId);

        service.grantRealmRole(accountId, "ORGANIZER").block();
        assertThat(roleNames(keycloakUserId)).contains("ORGANIZER");
        service.revokeRealmRole(accountId, "ORGANIZER").block();
        assertThat(roleNames(keycloakUserId)).doesNotContain("ORGANIZER");
    }

    @Test
    @Order(8)
    @DisplayName("ending a user's sessions makes their refresh token useless")
    void endSessions() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String keycloakUserId = service.createUser(accountId, true, UserType.CUSTOMER).block();
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue("Passw0rd!x");
        password.setTemporary(false);
        admin.realm(BUYERS).users().get(keycloakUserId).resetPassword(password);

        String refresh = token("password", Map.of("username", accountId, "password", "Passw0rd!x")).refreshToken();
        assertThat(token("refresh_token", Map.of("refresh_token", refresh)).status()).isEqualTo(200);

        service.endSessions(keycloakUserId).block();

        assertThat(token("refresh_token", Map.of("refresh_token", refresh)).status()).isEqualTo(400);
    }

    @Test
    @Order(9)
    @DisplayName("staff are created in the staff realm with a password and a second factor required; a repeat reads the user back")
    void staffUsers() {
        String username = "ops-" + UUID.randomUUID() + "@example.org";

        String id = service.createStaffUser(username, username, "Ops", "Admin", "Temp-Passw0rd!").block();
        String again = service.createStaffUser(username, username, "Ops", "Admin", "Temp-Passw0rd!").block();

        assertThat(again).isEqualTo(id);
        UserRepresentation staff = admin.realm(STAFF).users().get(id).toRepresentation();
        assertThat(staff.getRequiredActions()).contains("UPDATE_PASSWORD", "CONFIGURE_TOTP");
        assertThat(admin.realm(BUYERS).users().searchByUsername(username, true)).as("not in the buyer realm").isEmpty();

        service.grantRealmRole(STAFF, id, "ADMIN").block();
        KeycloakUserView view = service.readUser(STAFF, id).block();
        assertThat(view.realmRoles()).contains("ADMIN");
        assertThat(view.enabled()).isTrue();
        assertThat(service.readUser(STAFF, UUID.randomUUID().toString()).block()).isNull();
    }

    @Test
    @Order(10)
    @DisplayName("organization groups: joined by account id (the Keycloak username) and by Keycloak id (accounts that predate contacts)")
    void groupsResolveBothKindsOfUserId() {
        String accountId = UUID.randomUUID().toString();
        String keycloakUserId = service.createUser(accountId, true, UserType.CUSTOMER).block();

        service.ensureOrganizationGroupTree("kabwe-collective").block();
        service.joinOrganizationGroup(accountId, "kabwe-collective", "owners").block();

        assertThat(admin.realm(BUYERS).users().get(keycloakUserId).groups()).extracting(group -> group.getPath())
                .contains("/organizations/kabwe-collective/owners");

        service.leaveOrganizationGroup(keycloakUserId, "kabwe-collective", "owners").block();
        assertThat(admin.realm(BUYERS).users().get(keycloakUserId).groups()).isEmpty();
        assertThatThrownBy(() -> service.joinOrganizationGroup("no-such-account", "kabwe-collective", "owners").block())
                .isInstanceOf(KeycloakWriteFailed.class);
    }

    @Test
    @Order(9)
    @Tag("ET-PLT-007")
    @DisplayName("ET-PLT-007-R7 · a disabled account's own refresh token is dead at Keycloak, not only in this platform's stores")
    void disabledAccountCannotRefresh() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String keycloakUserId = service.createUser(accountId, true, UserType.CUSTOMER).block();
        CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue("Passw0rd!y");
        password.setTemporary(false);
        admin.realm(BUYERS).users().get(keycloakUserId).resetPassword(password);

        String refresh = token("password", Map.of("username", accountId, "password", "Passw0rd!y")).refreshToken();
        assertThat(token("refresh_token", Map.of("refresh_token", refresh)).status())
                .as("the account can refresh while enabled")
                .isEqualTo(200);

        service.setEnabled(keycloakUserId, false).block();

        assertThat(token("refresh_token", Map.of("refresh_token", refresh)).status())
                .as("Keycloak itself, not a platform revocation record, refuses the grant once the account is disabled")
                .isEqualTo(400);
        assertThat(token("password", Map.of("username", accountId, "password", "Passw0rd!y")).status())
                .as("a fresh password grant is refused too")
                .isEqualTo(400);
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private static List<String> roleNames(String keycloakUserId) {
        return admin.realm(BUYERS).users().get(keycloakUserId).roles().realmLevel().listAll().stream()
                .map(role -> role.getName()).toList();
    }

    private record Token(int status, String refreshToken) {
    }

    private static Token token(String grantType, Map<String, String> parameters) throws Exception {
        StringBuilder body = new StringBuilder("grant_type=" + grantType + "&client_id=test-direct");
        parameters.forEach((key, value) -> body.append('&').append(key).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8)));
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create(KEYCLOAK.getAuthServerUrl() + "/realms/" + BUYERS + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        String json = response.body();
        int start = json.indexOf("\"refresh_token\":\"");
        String refresh = start < 0 ? null : json.substring(start + 17, json.indexOf('"', start + 17));
        return new Token(response.statusCode(), refresh);
    }
}
