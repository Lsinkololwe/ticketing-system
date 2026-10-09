package com.pml.keycloak.it;

import static com.pml.keycloak.it.ContactOtpKeycloakIT.ADMIN_APP;
import static com.pml.keycloak.it.ContactOtpKeycloakIT.ADMIN_CLIENT;
import static com.pml.keycloak.it.ContactOtpKeycloakIT.ADMIN_CLIENT_SECRET;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The two realm exports, imported into a real Keycloak 26.5.2, behave as a hardened deployment must.
 *
 * <p>Settings are read back from the running server rather than from the JSON, so what is asserted
 * is what Keycloak made of the import, including the clients it adds on its own. The behavioural
 * cases drive real sign-ins: a staff member with only a password cannot get a session, the password
 * grant is refused on every client, and a refresh token cannot be used twice.</p>
 */
@Tag("ET-PLT-007")
@Tag("layer-5-integration")
class RealmConformanceIT {

    static final String BUYERS = "myticketzm";
    static final String STAFF = "myticketzm-admin";
    static final String CALLBACK = ADMIN_APP + "/api/auth/callback";

    @BeforeAll
    static void keycloak() throws Exception {
        ContactOtpKeycloakIT.start();
    }

    // ---- helpers ------------------------------------------------------------------------------

    static JsonNode json(String path) throws Exception {
        ContactOtpKeycloakIT.Resp r = ContactOtpKeycloakIT.admin("GET", path, null);
        assertThat(r.status()).as(path + " " + r.body()).isEqualTo(200);
        return Browser.JSON.readTree(r.body());
    }

    static boolean reachedApp(Browser.Step step) {
        return step.location() != null && step.location().startsWith(CALLBACK + "?") && step.location().contains("code=");
    }

    static String createStaff(String username, String password) throws Exception {
        ContactOtpKeycloakIT.Resp r = ContactOtpKeycloakIT.admin("POST", "/" + STAFF + "/users",
                "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.test\",\"firstName\":\"Conformance\",\"lastName\":\"Tester\",\"emailVerified\":true,"
                        + "\"enabled\":true,\"credentials\":[{\"type\":\"password\",\"value\":\"" + password + "\",\"temporary\":false}]}");
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.location().substring(r.location().lastIndexOf('/') + 1);
    }

    static int resetPassword(String userId, String password) throws Exception {
        return ContactOtpKeycloakIT.admin("PUT", "/" + STAFF + "/users/" + userId + "/reset-password",
                "{\"type\":\"password\",\"value\":\"" + password + "\",\"temporary\":false}").status();
    }

    static String authorizationCode(Browser.Step step) {
        return step.location().replaceAll(".*[?&]code=([^&]+).*", "$1");
    }

    // ---- realm settings -----------------------------------------------------------------------

    @Test
    @DisplayName("both realms issue five-minute tokens, rotate refresh tokens, and require TLS off the local network")
    void tokenAndTransportSettings() throws Exception {
        for (String realm : List.of(BUYERS, STAFF)) {
            JsonNode r = json("/" + realm);
            assertThat(r.path("accessTokenLifespan").asInt()).as(realm + " access token lifespan").isEqualTo(300);
            assertThat(r.path("revokeRefreshToken").asBoolean()).as(realm + " refresh rotation").isTrue();
            assertThat(r.path("refreshTokenMaxReuse").asInt()).as(realm + " refresh reuse allowance").isZero();
            assertThat(r.path("sslRequired").asText()).as(realm + " TLS requirement").isEqualTo("external");
            assertThat(r.path("registrationAllowed").asBoolean()).as(realm + " self-registration").isFalse();
            assertThat(r.path("bruteForceProtected").asBoolean()).as(realm + " brute-force protection").isTrue();
            assertThat(r.path("permanentLockout").asBoolean()).as(realm + " lockout is temporary, not an account-denial lever").isFalse();
            assertThat(r.path("eventsEnabled").asBoolean()).as(realm + " events").isTrue();
            assertThat(r.path("adminEventsEnabled").asBoolean()).as(realm + " admin events").isTrue();
            assertThat(r.path("eventsListeners").toString()).as(realm + " listeners").contains("user-sync");
        }
        assertThat(json("/" + BUYERS).path("resetPasswordAllowed").asBoolean())
                .as("buyers have no password to reset").isFalse();
    }

    @Test
    @DisplayName("staff passwords must be long, distinct from the username and email, and not recently used")
    void staffPasswordPolicy() throws Exception {
        String username = "policy-" + UUID.randomUUID().toString().substring(0, 8);
        String id = createStaff(username, "Initial-Passphrase-1x");

        assertThat(resetPassword(id, "Short1!x")).as("under twelve characters").isEqualTo(400);
        assertThat(resetPassword(id, username)).as("is the username").isEqualTo(400);
        assertThat(resetPassword(id, username + "@example.test")).as("is the email").isEqualTo(400);
        assertThat(resetPassword(id, "Second-Passphrase-2y")).isEqualTo(204);
        assertThat(resetPassword(id, "Initial-Passphrase-1x")).as("recently used").isEqualTo(400);
        assertThat(resetPassword(id, "Third-Passphrase-3z")).isEqualTo(204);
    }

    // ---- clients ------------------------------------------------------------------------------

    @Test
    @DisplayName("no client in either realm can use the password grant, and the server refuses it when tried")
    void passwordGrantIsOffEverywhere() throws Exception {
        for (String realm : List.of(BUYERS, STAFF)) {
            List<String> offenders = new ArrayList<>();
            for (JsonNode client : json("/" + realm + "/clients?max=200")) {
                String clientId = client.path("clientId").asText();
                if (client.path("directAccessGrantsEnabled").asBoolean()) {
                    offenders.add(clientId);
                }
                Map<String, String> form = new LinkedHashMap<>();
                form.put("grant_type", "password");
                form.put("client_id", clientId);
                form.put("username", "admin");
                form.put("password", "admin_password");
                JsonNode answer = new Browser(ContactOtpKeycloakIT.base).tokenPost(realm, form);
                assertThat(answer.path("_status").asInt()).as("%s / %s password grant: %s", realm, clientId, answer).isNotEqualTo(200);
                assertThat(answer.has("access_token")).as("%s / %s issued a token", realm, clientId).isFalse();
            }
            assertThat(offenders).as(realm + " clients with the password grant switched on").isEmpty();
        }
    }

    @Test
    @DisplayName("every redirect URI is exact, no client allows the implicit flow, and no web origin is a wildcard")
    void redirectsAreExact() throws Exception {
        for (String realm : List.of(BUYERS, STAFF)) {
            for (JsonNode client : json("/" + realm + "/clients?max=200")) {
                String clientId = client.path("clientId").asText();
                for (JsonNode uri : client.path("redirectUris")) {
                    if (clientId.equals("security-admin-console") || clientId.equals("account-console") || clientId.equals("account")) {
                        continue; // Keycloak's own console clients use relative paths under the realm
                    }
                    assertThat(uri.asText()).as("%s / %s redirect URI", realm, clientId).doesNotContain("*");
                }
                assertThat(client.path("webOrigins").toString()).as("%s / %s web origins", realm, clientId).doesNotContain("\"*\"");
                assertThat(client.path("implicitFlowEnabled").asBoolean()).as("%s / %s implicit flow", realm, clientId).isFalse();
                boolean keycloakOwn = List.of("account", "account-console", "security-admin-console").contains(clientId);
                if (!keycloakOwn && client.path("publicClient").asBoolean() && client.path("standardFlowEnabled").asBoolean()) {
                    assertThat(client.path("attributes").path("pkce.code.challenge.method").asText())
                            .as("%s / %s is public, so PKCE S256 is its only protection", realm, clientId).isEqualTo("S256");
                }
            }
        }
    }

    // ---- staff second factor ------------------------------------------------------------------

    @Test
    @DisplayName("a staff member who knows only the password is made to enrol an authenticator and gets no session until they do")
    void staffCannotSkipTheSecondFactor() throws Exception {
        String username = "enrol-" + UUID.randomUUID().toString().substring(0, 8);
        createStaff(username, "Initial-Passphrase-1x");

        Browser browser = new Browser(ContactOtpKeycloakIT.base);
        Browser.Step login = browser.authorize(STAFF, ADMIN_CLIENT, CALLBACK, "");
        Browser.Step afterPassword = StaffAuth.passwordOnly(browser, login, username, "Initial-Passphrase-1x");

        assertThat(reachedApp(afterPassword)).as("a password alone must not yield an authorization code").isFalse();
        assertThat(afterPassword.html()).as("the authenticator enrolment page").contains("name=\"totpSecret\"");

        Browser.Step again = browser.authorize(STAFF, ADMIN_CLIENT, CALLBACK, "");
        assertThat(reachedApp(again)).as("restarting the flow with the half-finished session must not skip enrolment").isFalse();

        Browser.Step enrolled = StaffAuth.enrol(browser, afterPassword, username);
        assertThat(reachedApp(enrolled)).as("enrolling completes the sign-in: %s %s", enrolled.status(), StaffAuth.excerpt(afterPassword.html()) + " ||| " + StaffAuth.excerpt(enrolled.html())).isTrue();
        assertThat(StaffAuth.enrolled(username)).isTrue();
    }

    @Test
    @DisplayName("once enrolled, a staff sign-in needs a fresh, correct code: a wrong one and a replayed one are refused")
    void staffCodeIsEnforced() throws Exception {
        String username = "otp-" + UUID.randomUUID().toString().substring(0, 8);
        createStaff(username, "Initial-Passphrase-1x");
        Browser first = new Browser(ContactOtpKeycloakIT.base);
        Browser.Step enrolled = StaffAuth.completePasswordLogin(first,
                first.authorize(STAFF, ADMIN_CLIENT, CALLBACK, ""), username, "Initial-Passphrase-1x");
        assertThat(reachedApp(enrolled)).isTrue();

        Browser second = new Browser(ContactOtpKeycloakIT.base);
        Browser.Step prompt = StaffAuth.passwordOnly(second, second.authorize(STAFF, ADMIN_CLIENT, CALLBACK, ""),
                username, "Initial-Passphrase-1x");
        assertThat(reachedApp(prompt)).as("password alone, second factor configured").isFalse();
        assertThat(prompt.html()).contains("name=\"otp\"");

        Browser.Step wrong = second.submit(prompt, Map.of("otp", "000000"));
        assertThat(reachedApp(wrong)).as("wrong code").isFalse();
        assertThat(wrong.html()).contains("name=\"otp\"");

        // Two failures inside Keycloak's one-second quick-login window lock the account for a minute,
        // which is the intended brute-force behaviour, so the failures are spaced out.
        Thread.sleep(1500);
        Browser.Step replay = second.submit(wrong, Map.of("otp", StaffAuth.staleCode(username, 0)));
        assertThat(reachedApp(replay)).as("a code that already signed someone in cannot sign in again").isFalse();

        Thread.sleep(1500);
        Browser.Step accepted = second.submit(replay, Map.of("otp", StaffAuth.nextCode(username)));
        assertThat(reachedApp(accepted)).as("a fresh correct code is accepted").isTrue();
    }

    @Test
    @DisplayName("a staff account seeded with an authenticator (as the local stack does) is asked for the code, not for enrolment")
    void seededAuthenticatorIsHonoured() throws Exception {
        String username = "seeded-" + UUID.randomUUID().toString().substring(0, 8);
        String secret = "AbCdEfGhIjKlMnOpQrSt";
        ContactOtpKeycloakIT.Resp created = ContactOtpKeycloakIT.admin("POST", "/" + STAFF + "/users",
                "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.test\",\"firstName\":\"Seeded\","
                        + "\"lastName\":\"Staff\",\"emailVerified\":true,\"enabled\":true,\"credentials\":["
                        + "{\"type\":\"password\",\"value\":\"Initial-Passphrase-1x\",\"temporary\":false},"
                        + "{\"type\":\"otp\",\"secretData\":\"{\\\"value\\\":\\\"" + secret + "\\\"}\","
                        + "\"credentialData\":\"{\\\"subType\\\":\\\"totp\\\",\\\"digits\\\":6,\\\"period\\\":30,"
                        + "\\\"algorithm\\\":\\\"HmacSHA1\\\",\\\"counter\\\":0}\"}]}");
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        StaffAuth.register(username, secret);

        Browser browser = new Browser(ContactOtpKeycloakIT.base);
        Browser.Step prompt = StaffAuth.passwordOnly(browser, browser.authorize(STAFF, ADMIN_CLIENT, CALLBACK, ""),
                username, "Initial-Passphrase-1x");
        assertThat(prompt.html()).as("asks for the code, does not offer enrolment").contains("name=\"otp\"").doesNotContain("totpSecret");
        assertThat(reachedApp(browser.submit(prompt, Map.of("otp", StaffAuth.nextCode(username))))).isTrue();
    }

    // ---- tokens -------------------------------------------------------------------------------

    @Test
    @DisplayName("a staff access token lives five minutes and a used refresh token revokes the whole session")
    void staffTokensRotate() throws Exception {
        String username = "rotate-" + UUID.randomUUID().toString().substring(0, 8);
        createStaff(username, "Initial-Passphrase-1x");
        Browser browser = new Browser(ContactOtpKeycloakIT.base);
        Browser.Step done = StaffAuth.completePasswordLogin(browser,
                browser.authorize(STAFF, ADMIN_CLIENT, CALLBACK, ""), username, "Initial-Passphrase-1x");
        assertThat(reachedApp(done)).isTrue();
        JsonNode tokens = browser.exchange(STAFF, ADMIN_CLIENT, ADMIN_CLIENT_SECRET, CALLBACK, authorizationCode(done));

        assertRotation(STAFF, ADMIN_CLIENT, ADMIN_CLIENT_SECRET, tokens);
    }

    @Test
    @DisplayName("a buyer access token lives five minutes and a used refresh token revokes the whole session")
    void buyerTokensRotate() throws Exception {
        String account = UUID.randomUUID().toString();
        ContactOtpKeycloakIT.createBuyer(account);
        ContactOtpKeycloakIT.stub.handles.put("hConform", account);
        Browser browser = new Browser(ContactOtpKeycloakIT.base);
        Browser.Step step = browser.authorize(BUYERS, ContactOtpKeycloakIT.WEB, Browser.REDIRECT, "login_hint=hConform");
        assertThat(step.hasCode()).isTrue();
        JsonNode tokens = browser.exchange(BUYERS, ContactOtpKeycloakIT.WEB, ContactOtpKeycloakIT.WEB_SECRET,
                Browser.REDIRECT, step.code());

        assertRotation(BUYERS, ContactOtpKeycloakIT.WEB, ContactOtpKeycloakIT.WEB_SECRET, tokens);
    }

    private static void assertRotation(String realm, String client, String secret, JsonNode first) throws Exception {
        JsonNode claims = Browser.claims(first.get("access_token").asText());
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).as("access token lifetime in seconds").isEqualTo(300);

        Browser api = new Browser(ContactOtpKeycloakIT.base);
        String original = first.get("refresh_token").asText();

        JsonNode second = api.tokenPost(realm, refreshForm(client, secret, original));
        assertThat(second.path("_status").asInt()).as(second.toString()).isEqualTo(200);
        String rotated = second.get("refresh_token").asText();
        assertThat(rotated).as("a refresh issues a new refresh token").isNotEqualTo(original);

        JsonNode reuse = api.tokenPost(realm, refreshForm(client, secret, original));
        assertThat(reuse.path("error").asText()).as("the used token is dead").isEqualTo("invalid_grant");

        JsonNode afterReuse = api.tokenPost(realm, refreshForm(client, secret, rotated));
        assertThat(afterReuse.path("error").asText())
                .as("presenting a used token means it may have been stolen, so the session it belongs to ends").isEqualTo("invalid_grant");
    }

    private static Map<String, String> refreshForm(String client, String secret, String token) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", client);
        form.put("client_secret", secret);
        form.put("refresh_token", token);
        return form;
    }
}
