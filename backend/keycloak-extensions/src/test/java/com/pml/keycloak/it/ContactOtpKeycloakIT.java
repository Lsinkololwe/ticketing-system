package com.pml.keycloak.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Order;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Real Keycloak 26.5.2 with the built plugin JAR and the imported docker-resources/keycloak realms, a stub
 * identity-service, and a cookie-keeping HTTP browser. Findings (KC-01, KC-02) are written to
 * target/poc-findings.txt so docker-resources/keycloak/POC-FINDINGS.md can quote observed behaviour.
 */
@Tag("ET-IDN-001")
@Tag("layer-5-integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContactOtpKeycloakIT {

    static final String WEB = "myticketzm-web";
    static final String WEB_SECRET = "web-secret-for-it";
    static final String PLUGIN_CLIENT = "keycloak-otp-authenticator";
    static final String PLUGIN_SECRET = "plugin-secret-for-it";
    static final String GATEWAY = "api-gateway-it";
    static final String ADMIN_CLIENT = "myticketzm-admin";
    static final String ADMIN_CLIENT_SECRET = "admin-secret-for-it";
    static final String ORGANIZER = "myticketzm-organizer";
    static final String BUYER_APP = "http://localhost:3000";
    static final String ORGANIZER_APP = "http://localhost:3010";
    static final String ADMIN_APP = "http://localhost:3030";

    static GenericContainer<?> keycloak;
    static StubIdentityService stub;
    static String base;
    static final StringBuilder FINDINGS = new StringBuilder();
    static final HttpClient API = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        if (keycloak != null && keycloak.isRunning()) {
            return; // shared with RealmConformanceIT: one Keycloak per JVM
        }
        Path jar = Path.of(System.getProperty("plugin.jar", "target/keycloak-extensions-1.0.0.jar")).toAbsolutePath();
        assertThat(jar).as("run through `mvn verify` so the plugin jar is packaged first").exists();
        Path infra = Path.of(System.getProperty("repo.infra.dir", "../../../docker-resources/keycloak")).toAbsolutePath().normalize();
        Path tmp = Files.createTempDirectory("kc-it-realms");
        Files.copy(infra.resolve("myticketzm-realm.json"), tmp.resolve("myticketzm-realm.json"));
        // the admin theme lives in docker-resources and is not in this image; everything else is unchanged
        String admin = Files.readString(infra.resolve("myticketzm-admin-realm.json"))
                .replace("\"loginTheme\": \"myticketzm-admin\"", "\"loginTheme\": \"keycloak\"");
        Files.writeString(tmp.resolve("myticketzm-admin-realm.json"), admin);

        stub = new StubIdentityService(PLUGIN_CLIENT);
        String image = System.getProperty("keycloak.image", "quay.io/keycloak/keycloak:26.5.2");
        keycloak = new GenericContainer<>(image)
                .withExposedPorts(8080)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withEnv("KC_HTTP_ENABLED", "true")
                .withEnv("KC_HOSTNAME_STRICT", "false")
                .withEnv("IDENTITY_BASE_URL", "http://host.docker.internal:" + stub.port())
                .withEnv("IDENTITY_CLIENT_ID", PLUGIN_CLIENT)
                .withEnv("IDENTITY_CLIENT_SECRET", PLUGIN_SECRET)
                .withEnv("KEYCLOAK_TOKEN_URL", "http://localhost:8080/realms/myticketzm/protocol/openid-connect/token")
                .withEnv("TICKETING_REALM_NAME", "myticketzm")
                .withEnv("TICKETING_REALM_DISPLAY_NAME", "MyTicket Zambia")
                .withEnv("TICKETING_REALM_DISPLAY_HTML", "MyTicket Zambia")
                .withEnv("TICKETING_LOGIN_THEME", "keycloak")
                .withEnv("TICKETING_DEFAULT_ROLE", "default-roles-myticketzm")
                .withEnv("TICKETING_BUYER_APP_URL", BUYER_APP)
                .withEnv("TICKETING_ORGANIZER_APP_URL", ORGANIZER_APP)
                .withEnv("TICKETING_ADMIN_APP_URL", ADMIN_APP)
                .withEnv("ADMIN_WEB_CLIENT_SECRET", ADMIN_CLIENT_SECRET)
                .withEnv("TICKETING_MOBILE_CLIENT_ID", "myticketzm-mobile")
                .withEnv("TICKETING_CATALOG_SERVICE_CLIENT_ID", "catalog-service")
                .withEnv("TICKETING_BOOKING_SERVICE_CLIENT_ID", "booking-service")
                .withEnv("TICKETING_API_GATEWAY_CLIENT_ID", GATEWAY)
                .withEnv("TICKETING_IDENTITY_SERVICE_CLIENT_ID", "identity-service")
                .withEnv("TICKETING_OTP_AUTHENTICATOR_CLIENT_ID", PLUGIN_CLIENT)
                .withEnv("TICKETING_ORGANIZER_CLIENT_ID", ORGANIZER)
                .withEnv("WEB_CLIENT_SECRET", WEB_SECRET)
                .withEnv("CATALOG_SERVICE_SECRET", "s1").withEnv("BOOKING_SERVICE_SECRET", "s2")
                .withEnv("API_GATEWAY_SECRET", "s3").withEnv("IDENTITY_SERVICE_SECRET", "s4")
                .withEnv("KEYCLOAK_OTP_AUTHENTICATOR_SECRET", PLUGIN_SECRET)
                .withEnv("ORGANIZER_PORTAL_CLIENT_SECRET", "s6")
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/opt/keycloak/providers/keycloak-extensions.jar")
                .withCopyFileToContainer(MountableFile.forHostPath(tmp.resolve("myticketzm-realm.json")), "/opt/keycloak/data/import/myticketzm-realm.json")
                .withCopyFileToContainer(MountableFile.forHostPath(tmp.resolve("myticketzm-admin-realm.json")), "/opt/keycloak/data/import/myticketzm-admin-realm.json")
                .withCommand("start-dev", "--import-realm")
                .waitingFor(Wait.forHttp("/realms/myticketzm-admin").forPort(8080).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(4)));
        keycloak.start();
        base = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            keycloak.stop();
            stub.close();
        }));
    }

    @AfterAll
    static void stop() throws Exception {
        Path out = Path.of("target/poc-findings.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, FINDINGS.toString());
        if (keycloak != null) {
            if (System.getProperty("it.keep.logs") != null) {
                Files.writeString(Path.of("target/keycloak-it.log"), keycloak.getLogs());
            }
        }
    }

    // ---- admin API helpers --------------------------------------------------------------------

    static String adminToken() throws Exception {
        Browser b = new Browser(base);
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "password");
        f.put("client_id", "admin-cli");
        f.put("username", "admin");
        f.put("password", "admin");
        return b.tokenPost("master", f).get("access_token").asText();
    }

    record Resp(int status, String body, String location) {}

    static Resp admin(String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + "/admin/realms" + path))
                .header("Authorization", "Bearer " + adminToken()).header("Content-Type", "application/json");
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> r = API.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body(), r.headers().firstValue("Location").orElse(null));
    }

    /** What identity-service does: a user whose username is the account id, no names, no email. */
    static String createBuyer(String accountId) throws Exception {
        Resp r = admin("POST", "/myticketzm/users", "{\"username\":\"" + accountId + "\",\"enabled\":true,"
                + "\"attributes\":{\"accountId\":[\"" + accountId + "\"]}}");
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.location().substring(r.location().lastIndexOf('/') + 1);
    }

    static String pageId(String html) {
        if (html == null) {
            return "no body";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("data-page-id=\"([^\"]*)\"").matcher(html);
        String id = m.find() ? m.group(1) : "unknown";
        String form = html.contains("kc-contact-form") ? " (contact page)" : html.contains("kc-contact-code-form") ? " (code page)" : "";
        return "page-id=" + id + form;
    }

    static void note(String text) {
        FINDINGS.append(text).append('\n');
    }

    Browser.Step signInByCode(Browser browser, String contact) throws Exception {
        Browser.Step page = browser.authorize("myticketzm", WEB, Browser.REDIRECT, "");
        assertThat(page.isPage()).as("contact page expected, got %s %s", page.status(), page.location()).isTrue();
        page = browser.submit(page, Map.of("contact", contact));
        return browser.submit(page, Map.of("code", StubIdentityService.GOOD_CODE, "action", "verify"));
    }

    // ---- tests --------------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("ET-IDN-001-R14 · (a) SCREEN: provider-jar templates are served, wrong code, resend, change, then a PKCE login")
    void screenModeFullLogin() throws Exception {
        String account = UUID.randomUUID().toString();
        createBuyer(account);
        stub.accounts.put("jane@example.com", account);

        Browser browser = new Browser(base);
        Browser.Step page = browser.authorize("myticketzm", WEB, Browser.REDIRECT, "");
        assertThat(page.isPage()).isTrue();
        assertThat(page.html()).contains("kc-contact-form").contains("name=\"contact\"")
                .contains("autocomplete=\"username\"").contains("aria-live=\"polite\"");
        note("TEMPLATES: provider-jar theme-resources/templates/contact-input.ftl IS served on 26.5.2 (page contains kc-contact-form).");

        Browser.Step code = browser.submit(page, Map.of("contact", "jane@example.com"));
        assertThat(code.html()).contains("kc-contact-code-form").contains("j***@example.com")
                .contains("autocomplete=\"one-time-code\"").contains("5 minutes");

        Browser.Step wrong = browser.submit(code, Map.of("code", "000000", "action", "verify"));
        assertThat(wrong.html()).contains("not correct").contains("4 attempts remaining").contains("kc-contact-code-form");

        Browser.Step resent = browser.submit(wrong, Map.of("code", "", "action", "resend"));
        assertThat(resent.html()).contains("kc-contact-code-form").contains("new code is on its way");
        assertThat(stub.challenges.values().stream().filter("jane@example.com"::equals)).hasSize(2);

        Browser.Step changed = browser.submit(resent, Map.of("code", "", "action", "change"));
        assertThat(changed.html()).contains("kc-contact-form");

        Browser.Step again = browser.submit(changed, Map.of("contact", "jane@example.com"));
        Browser.Step done = browser.submit(again, Map.of("code", StubIdentityService.GOOD_CODE, "action", "verify"));
        assertThat(done.hasCode()).as("redirect with code, got %s %s", done.status(), done.location()).isTrue();
        assertThat(stub.ensureIssueHandleSeen).isEqualTo("false");

        JsonNode tokens = browser.exchange("myticketzm", WEB, WEB_SECRET, Browser.REDIRECT, done.code());
        assertThat(tokens.path("_status").asInt()).as(tokens.toString()).isEqualTo(200);
        JsonNode claims = Browser.claims(tokens.get("access_token").asText());
        assertThat(claims.path("preferred_username").asText()).isEqualTo(account);
        // sub is the Keycloak user id (Keycloak ignores a supplied id); downstream reads accountId
        assertThat(claims.path("accountId").asText()).as("accountId claim from the user attribute").isEqualTo(account);
        assertThat(claims.path("sub").asText()).isNotBlank();
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(300);
        assertThat(claims.path("aud").toString()).contains(GATEWAY);
        assertThat(claims.path("sid").asText()).as("sid claim in the buyer access token").isNotBlank();
        assertThat(claims.path("realm_access").path("roles").toString()).contains("CUSTOMER");
        assertThat(stub.unauthenticated).as("every plugin call carried the plugin client's token").isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("ET-IDN-001-R4 · ACTIVE account without a Keycloak user: generic error, user NOT created")
    void missingUserIsNeverCreated() throws Exception {
        Browser.Step result = signInByCode(new Browser(base), "ghost@example.com");
        assertThat(result.hasCode()).isFalse();
        assertThat(result.html()).contains("could not sign you in");
        Resp users = admin("GET", "/myticketzm/users?search=" + stub.accounts.getOrDefault("ghost@example.com", ""), null);
        Resp all = admin("GET", "/myticketzm/users?max=50", null);
        assertThat(all.body()).doesNotContain("ghost");
        assertThat(users.status()).isEqualTo(200);
    }

    @Test
    @Order(3)
    @DisplayName("ET-IDN-001-R7 · (b) KC-01 HANDOFF: login_hint=<handle> completes with NO form and returns a code; replay falls back to SCREEN")
    void handoffCompletesWithoutForm() throws Exception {
        String account = UUID.randomUUID().toString();
        createBuyer(account);
        stub.handles.put("handle-ok-1", account);

        Browser browser = new Browser(base);
        Browser.Step step = browser.authorize("myticketzm", WEB, Browser.REDIRECT, "login_hint=handle-ok-1");
        assertThat(step.hasCode()).as("HANDOFF must return a code without a page: %s %s", step.status(), step.location()).isTrue();
        JsonNode tokens = browser.exchange("myticketzm", WEB, WEB_SECRET, Browser.REDIRECT, step.code());
        JsonNode handoffClaims = Browser.claims(tokens.get("access_token").asText());
        assertThat(handoffClaims.path("preferred_username").asText()).isEqualTo(account);
        assertThat(handoffClaims.path("accountId").asText()).isEqualTo(account);
        note("KC-01 handoff: GET /auth?login_hint=<handle> -> 302 to redirect_uri?code=... with no HTML page; token preferred_username == accountId.");

        stub.handles.put("handle-replay", account);
        Browser first = new Browser(base);
        assertThat(first.authorize("myticketzm", WEB, Browser.REDIRECT, "login_hint=handle-replay").hasCode()).isTrue();
        Browser second = new Browser(base);
        Browser.Step replay = second.authorize("myticketzm", WEB, Browser.REDIRECT, "login_hint=handle-replay");
        assertThat(replay.isPage()).isTrue();
        assertThat(replay.html()).contains("kc-contact-form");
        Browser.Step junk = new Browser(base).authorize("myticketzm", WEB, Browser.REDIRECT, "login_hint=not-a-handle");
        assertThat(junk.html()).contains("kc-contact-form");
        note("KC-01 invalid or replayed handle: falls back to the SCREEN contact page (HTTP 200), no error shown.");
    }

    @Test
    @Order(4)
    @DisplayName("ET-IDN-001-R7 · KC-01 prompt=login and an existing SSO cookie of ANOTHER user: behaviour recorded")
    void handoffWithExistingSession() throws Exception {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        createBuyer(a);
        createBuyer(b);

        // A signs in (handoff) and keeps the SSO cookie
        stub.handles.put("hA", a);
        Browser browser = new Browser(base);
        assertThat(browser.authorize("myticketzm", WEB, Browser.REDIRECT, "login_hint=hA").hasCode()).isTrue();

        // same cookie jar, a handle for B, no prompt
        stub.handles.put("hB", b);
        Browser.Step noPrompt = browser.authorize("myticketzm", WEB, Browser.REDIRECT, "login_hint=hB");
        String noPromptUser = noPrompt.hasCode()
                ? Browser.claims(browser.exchange("myticketzm", WEB, WEB_SECRET, Browser.REDIRECT, noPrompt.code()).get("access_token").asText()).path("preferred_username").asText()
                : "(page " + noPrompt.status() + ")";
        boolean handleStillUnspent = stub.handles.containsKey("hB");
        note("KC-01 existing SSO cookie of user A + login_hint=<handle of B>, no prompt: signed in as "
                + (noPromptUser.equals(a) ? "A (cookie wins, handle ignored)" : noPromptUser.equals(b) ? "B" : noPromptUser)
                + "; handle of B " + (handleStillUnspent ? "NOT consumed" : "consumed") + ".");

        // same cookie jar, prompt=login
        stub.handles.put("hB2", b);
        Browser.Step prompt = browser.authorize("myticketzm", WEB, Browser.REDIRECT, "prompt=login&login_hint=hB2");
        String promptUser = prompt.hasCode()
                ? Browser.claims(browser.exchange("myticketzm", WEB, WEB_SECRET, Browser.REDIRECT, prompt.code()).get("access_token").asText()).path("preferred_username").asText()
                : "(page " + prompt.status() + ")";
        note("KC-01 existing SSO cookie of A + prompt=login&login_hint=<handle of B>: "
                + (prompt.hasCode() ? "code returned for " + (promptUser.equals(b) ? "B (re-authentication honoured, no form)" : promptUser)
                        : "a page was rendered: status " + prompt.status() + ", " + pageId(prompt.html()))
                + "; handle of B " + (stub.handles.containsKey("hB2") ? "NOT consumed" : "consumed") + ".");
        assertThat(promptUser).as("prompt=login must never sign in as the previous user").isNotEqualTo(a);
    }

    @Test
    @Order(5)
    @DisplayName("ET-IDN-001-R6 · staff (ADMIN role) are refused by the authenticator even with a valid handle")
    void staffRefused() throws Exception {
        String staff = UUID.randomUUID().toString();
        String id = createBuyer(staff);
        assertThat(admin("POST", "/myticketzm/roles", "{\"name\":\"ADMIN\"}").status()).isIn(201, 409);
        String role = admin("GET", "/myticketzm/roles/ADMIN", null).body();
        assertThat(admin("POST", "/myticketzm/users/" + id + "/role-mappings/realm", "[" + role + "]").status()).isEqualTo(204);
        stub.handles.put("hStaff", staff);
        try {
            Browser.Step step = new Browser(base).authorize("myticketzm", WEB, Browser.REDIRECT, "login_hint=hStaff");
            assertThat(step.hasCode()).isFalse();
            assertThat(step.html()).contains("could not sign you in");
        } finally {
            // The buyers realm declares no ADMIN role; this case added one only to model a staff account,
            // and the server is shared with the realm-conformance cases that assert the exact role set.
            admin("DELETE", "/myticketzm/roles/ADMIN", null);
        }
    }

    @Test
    @Order(6)
    @DisplayName("ET-IDN-001-R15 · (c) KC-02 Admin API: username=accountId with NO email/names; supplying id=accountId recorded")
    void adminApiCreatesUserWithoutProfile() throws Exception {
        String account = UUID.randomUUID().toString();
        Resp created = admin("POST", "/myticketzm/users", "{\"username\":\"" + account + "\",\"enabled\":true,"
                + "\"emailVerified\":false,\"attributes\":{\"accountId\":[\"" + account + "\"]}}");
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String kcId = created.location().substring(created.location().lastIndexOf('/') + 1);
        JsonNode user = Browser.JSON.readTree(admin("GET", "/myticketzm/users/" + kcId, null).body());
        assertThat(user.path("username").asText()).isEqualTo(account);
        assertThat(user.has("email")).isFalse();
        assertThat(user.has("firstName")).isFalse();
        assertThat(user.path("attributes").path("accountId").get(0).asText()).isEqualTo(account);
        JsonNode roles = Browser.JSON.readTree(admin("GET", "/myticketzm/users/" + kcId + "/role-mappings/realm/composite", null).body());
        assertThat(roles.toString()).contains("CUSTOMER");
        note("KC-02 create user with only username(+accountId attribute), enabled: HTTP 201; GET shows no email/firstName/lastName; "
                + "composite roles via defaultRole include CUSTOMER automatically. Kept 'accountId' as an unmanaged attribute (policy ADMIN_EDIT): stored.");

        // a full-representation update (what identity-service must send) keeps the user valid
        ObjectNode full = (ObjectNode) user;
        full.put("emailVerified", true);
        Resp put = admin("PUT", "/myticketzm/users/" + kcId, full.toString());
        note("KC-02 PUT full representation of the profile-less user: HTTP " + put.status());
        assertThat(put.status()).isEqualTo(204);

        // id = accountId
        String wanted = UUID.randomUUID().toString();
        Resp withId = admin("POST", "/myticketzm/users", "{\"id\":\"" + wanted + "\",\"username\":\"" + wanted + "\",\"enabled\":true}");
        String got = withId.location() == null ? "(none)" : withId.location().substring(withId.location().lastIndexOf('/') + 1);
        note("KC-02 POST users with \"id\":accountId: HTTP " + withId.status() + ", Keycloak user id "
                + (wanted.equals(got) ? "EQUALS the supplied id (usable: sub == accountId)" : "is " + got + " (supplied id IGNORED; use username=accountId)")
                + (withId.status() >= 400 ? " body=" + withId.body() : ""));
        // Admin-API user creation does not run the buyer browser flow, so a realm default-role grant is the only role
        assertThat(admin("GET", "/myticketzm/users?username=" + account + "&exact=true", null).body()).contains(account);
    }

    @Test
    @Order(7)
    @DisplayName("ET-IDN-001-R16 · hardening: no direct grant, no registration, sslRequired external, listener in both realms")
    void realmHardening() throws Exception {
        Browser b = new Browser(base);
        Map<String, String> pw = new LinkedHashMap<>();
        pw.put("grant_type", "password");
        pw.put("client_id", WEB);
        pw.put("client_secret", WEB_SECRET);
        pw.put("username", "x");
        pw.put("password", "y");
        JsonNode r = b.tokenPost("myticketzm", pw);
        assertThat(r.path("_status").asInt()).isEqualTo(400);
        assertThat(r.path("error").asText()).isEqualTo("unauthorized_client");

        JsonNode realm = Browser.JSON.readTree(admin("GET", "/myticketzm", null).body());
        assertThat(realm.path("registrationAllowed").asBoolean()).isFalse();
        assertThat(realm.path("sslRequired").asText()).isEqualTo("external");
        assertThat(realm.path("browserFlow").asText()).isEqualTo("contact-otp-browser");
        assertThat(realm.path("revokeRefreshToken").asBoolean()).isTrue();
        assertThat(realm.path("refreshTokenMaxReuse").asInt()).isZero();
        assertThat(realm.path("eventsListeners").toString()).contains("user-sync");
        JsonNode adminRealm = Browser.JSON.readTree(admin("GET", "/myticketzm-admin", null).body());
        assertThat(adminRealm.path("eventsListeners").toString()).contains("user-sync");
        assertThat(adminRealm.path("sslRequired").asText()).isEqualTo("external");

        JsonNode flows = Browser.JSON.readTree(admin("GET", "/myticketzm/authentication/flows", null).body());
        assertThat(flows.toString()).doesNotContain("Phone OTP").doesNotContain("account-type-role-mapper");
        for (JsonNode c : Browser.JSON.readTree(admin("GET", "/myticketzm/clients", null).body())) {
            if (c.path("clientId").asText().matches("account|account-console|admin-cli|broker|realm-management|security-admin-console")) {
                continue;                              // Keycloak built-ins, not buyer clients
            }
            assertThat(c.path("directAccessGrantsEnabled").asBoolean()).as(c.path("clientId").asText()).isFalse();
        }
        JsonNode web = Browser.JSON.readTree(admin("GET", "/myticketzm/clients?clientId=" + WEB, null).body()).get(0);
        assertThat(web.path("publicClient").asBoolean()).isFalse();
        assertThat(web.path("attributes").path("pkce.code.challenge.method").asText()).isEqualTo("S256");
        assertThat(web.path("redirectUris").toString()).isEqualTo("[\"" + Browser.REDIRECT + "\"]");

        JsonNode profile = Browser.JSON.readTree(admin("GET", "/myticketzm/users/profile", null).body());
        for (JsonNode a : (ArrayNode) profile.get("attributes")) {
            assertThat(a.path("name").asText()).isNotEqualTo("accountType");
            if (a.path("name").asText().matches("email|firstName|lastName")) {
                assertThat(a.has("required")).as(a.path("name").asText()).isFalse();
            }
        }
        assertThat(admin("GET", "/myticketzm/users?max=100", null).body()).doesNotContain("ORGANIZER").doesNotContain("testuser");
    }

    @Test
    @Order(8)
    @DisplayName("ET-IDN-001-R17 · (d) staff in the admin realm still sign in with a password; LOGIN is synced with the slim payload")
    void staffPasswordLogin() throws Exception {
        Browser browser = new Browser(base);
        Browser.Step page = browser.authorize("myticketzm-admin", ADMIN_CLIENT, ADMIN_APP + "/api/auth/callback", "");
        assertThat(page.isPage()).isTrue();
        assertThat(page.html()).contains("name=\"username\"").contains("name=\"password\"").doesNotContain("kc-contact-form");
        Browser.Step done = StaffAuth.completePasswordLogin(browser, page, "admin", "admin_password");
        assertThat(done.location()).as("status %s", done.status()).startsWith(ADMIN_APP + "/api/auth/callback?").contains("code=");
        String code = done.location().replaceAll(".*[?&]code=([^&]+).*", "$1");
        JsonNode tokens = browser.exchange("myticketzm-admin", ADMIN_CLIENT, ADMIN_CLIENT_SECRET,
                ADMIN_APP + "/api/auth/callback", code);
        assertThat(tokens.path("_status").asInt()).as(tokens.toString()).isEqualTo(200);
        assertThat(Browser.claims(tokens.get("access_token").asText()).path("realm_access").path("roles").toString()).contains("SUPER_ADMIN");

        long deadline = System.currentTimeMillis() + 15_000;
        JsonNode adminLogin = null;
        while (adminLogin == null && System.currentTimeMillis() < deadline) {
            for (JsonNode e : stub.syncEvents) {
                if ("LOGIN".equals(e.path("eventType").asText()) && "myticketzm-admin".equals(e.path("realm").asText())) {
                    adminLogin = e;
                }
            }
            Thread.sleep(200);
        }
        assertThat(adminLogin).as("LOGIN event for the staff realm reached identity-service").isNotNull();
        var names = new java.util.TreeSet<String>();
        adminLogin.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("eventId", "eventType", "userId", "username", "realm", "enabled",
                "emailVerified", "timestamp");
        assertThat(adminLogin.path("username").asText()).isEqualTo("admin");
        assertThat(stub.unauthenticated).isEmpty();
    }

    // ---- realm as code: BFF clients, logout, audience, sid (ET-IDN-003 R7, R9) ------------------

    static JsonNode client(String realm, String clientId) throws Exception {
        JsonNode list = Browser.JSON.readTree(admin("GET", "/" + realm + "/clients?clientId=" + clientId, null).body());
        assertThat(list).as("client %s in %s", clientId, realm).hasSize(1);
        return list.get(0);
    }

    static void assertBffClient(JsonNode c, String app, java.util.List<String> postLogout) {
        assertThat(c.path("publicClient").asBoolean()).isFalse();
        assertThat(c.path("standardFlowEnabled").asBoolean()).isTrue();
        assertThat(c.path("implicitFlowEnabled").asBoolean()).isFalse();
        assertThat(c.path("directAccessGrantsEnabled").asBoolean()).isFalse();
        assertThat(c.path("serviceAccountsEnabled").asBoolean()).isFalse();
        assertThat(c.path("frontchannelLogout").asBoolean()).isFalse();
        assertThat(Browser.JSON.convertValue(c.path("redirectUris"), java.util.List.class))
                .as("exact callback, no wildcard and no legacy Better Auth path").containsExactly(app + "/api/auth/callback");
        assertThat(Browser.JSON.convertValue(c.path("webOrigins"), java.util.List.class)).containsExactly(app);
        JsonNode a = c.path("attributes");
        assertThat(a.path("pkce.code.challenge.method").asText()).isEqualTo("S256");
        assertThat(a.path("backchannel.logout.url").asText()).isEqualTo(app + "/api/auth/backchannel-logout");
        assertThat(a.path("backchannel.logout.session.required").asText()).isEqualTo("true");
        assertThat(a.path("backchannel.logout.revoke.offline.tokens").asText()).isEqualTo("true");
        assertThat(java.util.Arrays.asList(a.path("post.logout.redirect.uris").asText().split("##")))
                .containsExactlyInAnyOrderElementsOf(postLogout);
    }

    @Test
    @Order(9)
    @DisplayName("ET-IDN-003-R9 · realm as code: the three BFF clients, token lifetimes, session limits, refresh reuse")
    void bffClientsAsCode() throws Exception {
        assertBffClient(client("myticketzm", WEB), BUYER_APP,
                java.util.List.of(BUYER_APP + "/*", BUYER_APP + "/api/auth/start?resume=1"));
        assertBffClient(client("myticketzm", ORGANIZER), ORGANIZER_APP,
                java.util.List.of(ORGANIZER_APP + "/*"));
        assertBffClient(client("myticketzm-admin", ADMIN_CLIENT), ADMIN_APP,
                java.util.List.of(ADMIN_APP + "/*"));

        for (String clientId : java.util.List.of(WEB, ORGANIZER)) {
            JsonNode mappers = client("myticketzm", clientId).path("protocolMappers");
            assertThat(mappers.toString()).as(clientId).contains("oidc-audience-mapper").contains(GATEWAY).contains("\"accountId\"");
        }
        assertThat(client("myticketzm-admin", ADMIN_CLIENT).path("protocolMappers").toString())
                .contains("oidc-audience-mapper").contains(GATEWAY).contains("\"accountId\"");

        JsonNode buyer = Browser.JSON.readTree(admin("GET", "/myticketzm", null).body());
        JsonNode staff = Browser.JSON.readTree(admin("GET", "/myticketzm-admin", null).body());
        for (JsonNode realm : new JsonNode[] {buyer, staff}) {
            assertThat(realm.path("accessTokenLifespan").asInt()).isEqualTo(300);
            assertThat(realm.path("ssoSessionIdleTimeout").asInt()).isEqualTo(1800);
            assertThat(realm.path("revokeRefreshToken").asBoolean()).isTrue();
            assertThat(realm.path("refreshTokenMaxReuse").asInt()).isZero();
            assertThat(realm.path("eventsListeners").toString()).contains("user-sync");
            assertThat(realm.path("enabledEventTypes").toString()).contains("LOGOUT").contains("REFRESH_TOKEN_ERROR");
        }
        assertThat(buyer.path("ssoSessionMaxLifespan").asInt()).isEqualTo(36000);
        assertThat(staff.path("ssoSessionMaxLifespan").asInt()).isEqualTo(28800);
        assertThat(buyer.path("browserFlow").asText()).isEqualTo("contact-otp-browser");
        assertThat(staff.path("browserFlow").asText()).isEqualTo("staff-password-browser");
    }

    /** Signs in as the seeded staff user on a fresh browser and returns the token response. */
    JsonNode staffLogin(Browser browser) throws Exception {
        Browser.Step page = browser.authorize("myticketzm-admin", ADMIN_CLIENT, ADMIN_APP + "/api/auth/callback", "");
        Browser.Step done = StaffAuth.completePasswordLogin(browser, page, "admin", "admin_password");
        assertThat(done.location()).as("status %s", done.status()).startsWith(ADMIN_APP + "/api/auth/callback?");
        String code = done.location().replaceAll(".*[?&]code=([^&]+).*", "$1");
        JsonNode tokens = browser.exchange("myticketzm-admin", ADMIN_CLIENT, ADMIN_CLIENT_SECRET, ADMIN_APP + "/api/auth/callback", code);
        assertThat(tokens.path("_status").asInt()).as(tokens.toString()).isEqualTo(200);
        return tokens;
    }

    JsonNode awaitEvent(String type, String sid) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode e : stub.syncEvents) {
                if (type.equals(e.path("eventType").asText()) && sid.equals(e.path("sid").asText())) {
                    return e;
                }
            }
            Thread.sleep(200);
        }
        return null;
    }

    @Test
    @Order(10)
    @DisplayName("ET-IDN-003-R7 · staff realm: audience and sid in tokens; RP logout with id_token_hint redirects with no page; LOGOUT reaches identity with that sid only")
    void staffLogoutReachesIdentityWithSid() throws Exception {
        Browser one = new Browser(base);
        Browser two = new Browser(base);
        JsonNode t1 = staffLogin(one);
        JsonNode t2 = staffLogin(two);
        JsonNode c1 = Browser.claims(t1.get("access_token").asText());
        JsonNode c2 = Browser.claims(t2.get("access_token").asText());
        assertThat(c1.path("aud").toString()).as("gateway audience in the staff access token").contains(GATEWAY);
        assertThat(c1.path("sid").asText()).as("sid in the staff access token").isNotBlank();
        assertThat(c1.path("sub").asText()).as("sub in the staff access token (the user revocation key)").isNotBlank();
        assertThat(Browser.claims(t1.get("id_token").asText()).path("auth_time").asLong()).as("auth_time in the staff id token").isPositive();
        assertThat(Browser.claims(t1.get("id_token").asText()).path("sid").asText()).isEqualTo(c1.path("sid").asText());
        assertThat(c1.path("sid").asText()).isNotEqualTo(c2.path("sid").asText());

        String logout = base + "/realms/myticketzm-admin/protocol/openid-connect/logout?id_token_hint="
                + Browser.enc(t1.get("id_token").asText()) + "&post_logout_redirect_uri=" + Browser.enc(ADMIN_APP + "/logged-out")
                + "&state=xyz";
        Browser.Step out = one.get(logout);
        assertThat(out.status()).as("no confirmation page: %s", out.html()).isIn(302, 303);
        assertThat(out.location()).startsWith(ADMIN_APP + "/logged-out").contains("state=xyz");

        // an unregistered post-logout URI is never honoured
        Browser three = new Browser(base);
        JsonNode t3 = staffLogin(three);
        Browser.Step bad = three.get(base + "/realms/myticketzm-admin/protocol/openid-connect/logout?id_token_hint="
                + Browser.enc(t3.get("id_token").asText()) + "&post_logout_redirect_uri=" + Browser.enc("https://evil.example/"));
        assertThat(bad.location() == null || !bad.location().startsWith("https://evil.example")).isTrue();

        JsonNode event = awaitEvent("LOGOUT", c1.path("sid").asText());
        assertThat(event).as("LOGOUT with the sid of the ended session reached identity-service").isNotNull();
        var names = new java.util.TreeSet<String>();
        event.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("eventId", "eventType", "userId", "username", "realm", "enabled",
                "emailVerified", "timestamp", "sid");
        assertThat(event.path("userId").asText()).isEqualTo(c1.path("sub").asText());
        assertThat(event.path("realm").asText()).isEqualTo("myticketzm-admin");
        assertThat(stub.syncEvents.stream().filter(e -> "LOGOUT".equals(e.path("eventType").asText()))
                .map(e -> e.path("sid").asText())).as("the other session of the same user was not ended")
                .doesNotContain(c2.path("sid").asText());

        // what identity-service does with it: SESSION:{sid} is revoked, the user is not (see KeycloakSessionRevokerTest)
        Map<String, String> refresh = new LinkedHashMap<>();
        refresh.put("grant_type", "refresh_token");
        refresh.put("client_id", ADMIN_CLIENT);
        refresh.put("client_secret", ADMIN_CLIENT_SECRET);
        refresh.put("refresh_token", t1.get("refresh_token").asText());
        assertThat(one.tokenPost("myticketzm-admin", refresh).path("error").asText()).isEqualTo("invalid_grant");
        refresh.put("refresh_token", t2.get("refresh_token").asText());
        assertThat(two.tokenPost("myticketzm-admin", refresh).path("_status").asInt()).isEqualTo(200);
        assertThat(stub.unauthenticated).isEmpty();
        note("LOGOUT: RP-initiated logout with id_token_hint and a registered post_logout_redirect_uri answers 302 directly (no confirmation page); "
                + "identity-service receives the LOGOUT event with the access token's sid and sub as userId.");
    }

    @Test
    @Order(11)
    @DisplayName("ET-IDN-003-R9 · buyer and organizer clients: audience and sid, logout to the /api/auth/start?resume=1 hop with no page")
    void buyerAndOrganizerLogout() throws Exception {
        String account = UUID.randomUUID().toString();
        createBuyer(account);

        Browser buyer = new Browser(base);
        stub.handles.put("hLogout1", account);
        Browser.Step step = buyer.authorize("myticketzm", WEB, Browser.REDIRECT, "login_hint=hLogout1");
        assertThat(step.hasCode()).isTrue();
        JsonNode tokens = buyer.exchange("myticketzm", WEB, WEB_SECRET, Browser.REDIRECT, step.code());
        JsonNode claims = Browser.claims(tokens.get("access_token").asText());
        assertThat(claims.path("aud").toString()).contains(GATEWAY);
        assertThat(claims.path("sid").asText()).isNotBlank();
        assertThat(claims.path("accountId").asText()).isEqualTo(account);
        assertThat(Browser.claims(tokens.get("id_token").asText()).path("auth_time").asLong()).as("auth_time in the buyer id token").isPositive();

        String resume = BUYER_APP + "/api/auth/start?resume=1";
        Browser.Step out = buyer.get(base + "/realms/myticketzm/protocol/openid-connect/logout?id_token_hint="
                + Browser.enc(tokens.get("id_token").asText()) + "&post_logout_redirect_uri=" + Browser.enc(resume));
        assertThat(out.status()).as("no confirmation page: %s", out.html()).isIn(302, 303);
        assertThat(out.location()).startsWith(resume);
        JsonNode event = awaitEvent("LOGOUT", claims.path("sid").asText());
        assertThat(event).isNotNull();
        assertThat(event.path("userId").asText()).isEqualTo(claims.path("sub").asText());
        assertThat(event.path("realm").asText()).isEqualTo("myticketzm");

        // the buyer realm's SSO session is gone: a plain authorize shows the contact page again
        assertThat(buyer.authorize("myticketzm", WEB, Browser.REDIRECT, "").isPage()).isTrue();

        // organizer client, buyer realm
        Browser org = new Browser(base);
        stub.handles.put("hLogout2", account);
        String orgCallback = ORGANIZER_APP + "/api/auth/callback";
        Browser.Step orgStep = org.authorize("myticketzm", ORGANIZER, orgCallback, "login_hint=hLogout2");
        assertThat(orgStep.location()).as("status %s", orgStep.status()).startsWith(orgCallback + "?");
        JsonNode orgTokens = org.exchange("myticketzm", ORGANIZER, "s6", orgCallback,
                orgStep.location().replaceAll(".*[?&]code=([^&]+).*", "$1"));
        JsonNode orgClaims = Browser.claims(orgTokens.get("access_token").asText());
        assertThat(orgClaims.path("aud").toString()).contains(GATEWAY);
        assertThat(orgClaims.path("sid").asText()).isNotBlank();
        Browser.Step orgOut = org.get(base + "/realms/myticketzm/protocol/openid-connect/logout?id_token_hint="
                + Browser.enc(orgTokens.get("id_token").asText()) + "&post_logout_redirect_uri=" + Browser.enc(ORGANIZER_APP + "/logged-out"));
        assertThat(orgOut.status()).isIn(302, 303);
        assertThat(orgOut.location()).startsWith(ORGANIZER_APP + "/logged-out");
        assertThat(awaitEvent("LOGOUT", orgClaims.path("sid").asText())).isNotNull();
    }
}
