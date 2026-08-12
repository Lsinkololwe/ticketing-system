package com.pml.identity.flowb.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the <em>production</em> {@code myticketzm} realm from {@code docker-resources},
 * resolves its {@code ${...}} placeholders, and imports it into the Keycloak container
 * through the Admin API.
 *
 * <p>Importing the real file (rather than a hand-written test fixture) is deliberate: Flow B
 * depends on the realm's registration flow wiring, the declarative user-profile definition of
 * {@code accountType}, and the client scopes that grant {@code internal-write}. A fixture realm
 * would validate the test's own assumptions instead of the shipped configuration.</p>
 *
 * <p>The raw document is kept accessible via {@link #productionRealmJson()} so tests can assert
 * on what the shipped file actually says — including where it disagrees with what Flow B needs.</p>
 */
public final class RealmProvisioner {

    /** Keycloak's own message placeholders — these must survive substitution untouched. */
    private static final Set<String> KEYCLOAK_OWNED_PLACEHOLDERS =
            Set.of("client_id", "profileScopeConsentText", "emailScopeConsentText");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Public client used by the tests to drive the browser registration flow. */
    public static final String TEST_CLIENT_ID = "flowb-test-client";
    public static final String TEST_REDIRECT_URI = "http://localhost:9999/callback";

    private final Path dockerResources;
    private final JsonNode productionRealmJson;
    private final Map<String, String> substitutions;
    private final String realmName;

    private RealmProvisioner(Path dockerResources,
                             JsonNode productionRealmJson,
                             Map<String, String> substitutions,
                             String realmName) {
        this.dockerResources = dockerResources;
        this.productionRealmJson = productionRealmJson;
        this.substitutions = substitutions;
        this.realmName = realmName;
    }

    // ------------------------------------------------------------------
    // location
    // ------------------------------------------------------------------

    /**
     * Resolves the {@code docker-resources} checkout.
     *
     * <p>Order: {@code -Dflowb.docker-resources}, then {@code DOCKER_RESOURCES_DIR}, then the
     * conventional sibling of the {@code ticketing-system} repository.</p>
     */
    public static Path locateDockerResources() {
        String override = System.getProperty("flowb.docker-resources",
                System.getenv("DOCKER_RESOURCES_DIR"));
        Path candidate = override != null
                ? Path.of(override)
                // module dir is backend/identity-service
                : Path.of("").toAbsolutePath().resolve("../../../docker-resources").normalize();

        if (!Files.isDirectory(candidate)) {
            throw new IllegalStateException("""
                    Flow B is an integration flow and cannot be validated against a fixture.
                    It needs the docker-resources checkout (realm JSON + Keycloak login theme),
                    expected at: %s
                    Point the suite at it with -Dflowb.docker-resources=/path/to/docker-resources
                    """.formatted(candidate));
        }
        return candidate;
    }

    public static RealmProvisioner load() {
        Path dockerResources = locateDockerResources();
        Path realmFile = dockerResources.resolve("keycloak/myticketzm-realm.json");
        if (!Files.isRegularFile(realmFile)) {
            throw new IllegalStateException("Realm file not found: " + realmFile);
        }
        try {
            String raw = Files.readString(realmFile, StandardCharsets.UTF_8);
            JsonNode production = MAPPER.readTree(raw);
            Map<String, String> values = buildSubstitutions(dockerResources, raw);
            return new RealmProvisioner(dockerResources, production, values, values.get("TICKETING_REALM_NAME"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read realm file: " + realmFile, e);
        }
    }

    // ------------------------------------------------------------------
    // substitution
    // ------------------------------------------------------------------

    /**
     * Every {@code ${NAME}} in the realm file must resolve to something. Values come from
     * {@code docker-resources/.env.ticketing} when present, otherwise from deterministic test
     * defaults, so the suite runs on a machine that has never started the compose stack.
     */
    private static Map<String, String> buildSubstitutions(Path dockerResources, String rawRealm) {
        Map<String, String> env = readEnvFile(dockerResources.resolve(".env.ticketing"));

        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = PLACEHOLDER.matcher(rawRealm);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (KEYCLOAK_OWNED_PLACEHOLDERS.contains(key)) {
                continue;
            }
            values.computeIfAbsent(key, k -> env.getOrDefault(k, defaultFor(k)));
        }

        // Pinned regardless of the environment file: these change the shape of the test.
        values.put("TICKETING_REALM_NAME", env.getOrDefault("TICKETING_REALM_NAME", "myticketzm"));
        values.put("TICKETING_DEFAULT_ROLE",
                "default-roles-" + values.get("TICKETING_REALM_NAME"));
        // The registration form under test lives in this theme; it is mounted into the container.
        values.put("TICKETING_LOGIN_THEME", "myticketzm");
        // Rewritten after the container starts, once the callback host is known.
        values.put("OTP_SERVICE_URL", "http://host.testcontainers.internal:0");
        return values;
    }

    private static String defaultFor(String key) {
        if (key.endsWith("_SECRET")) {
            // Deterministic, non-guessable-looking, and never a real credential.
            return "flowb-test-secret-" + key.toLowerCase().replace('_', '-');
        }
        if (key.endsWith("_URI") || key.endsWith("_LOGOUT") || key.endsWith("_ORIGIN")) {
            return "http://localhost:9999/*";
        }
        return "flowb-" + key.toLowerCase().replace('_', '-');
    }

    private static Map<String, String> readEnvFile(Path envFile) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(envFile)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                // strip trailing "  # comment"
                int hash = value.indexOf('#');
                if (hash >= 0) {
                    value = value.substring(0, hash).trim();
                }
                if ((value.startsWith("\"") && value.endsWith("\"") && value.length() > 1)
                        || (value.startsWith("'") && value.endsWith("'") && value.length() > 1)) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + envFile, e);
        }
        return values;
    }

    private String substituted() {
        String raw = productionRealmJson.toString();
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = substitutions.get(key);
            matcher.appendReplacement(out, replacement == null
                    ? Matcher.quoteReplacement(matcher.group(0))
                    // JSON-escape: the value is being spliced into a JSON string literal.
                    : Matcher.quoteReplacement(jsonEscape(replacement)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ------------------------------------------------------------------
    // import
    // ------------------------------------------------------------------

    /**
     * Imports the realm and applies the two fixtures Flow B needs that the shipped file does
     * not provide: a public client to drive the browser flow, and registration of the
     * {@code user-sync} event listener.
     *
     * @return the names of fixtures that had to be applied — each one is a gap in the
     *         shipped realm configuration and is asserted on by
     *         {@code FlowBOwaspComplianceIT}.
     */
    public List<String> importInto(Keycloak admin, String otpServiceUrl) {
        substitutions.put("OTP_SERVICE_URL", otpServiceUrl);

        RealmRepresentation realm;
        try {
            realm = MAPPER.readValue(substituted(), RealmRepresentation.class);
        } catch (IOException e) {
            throw new IllegalStateException("Realm JSON is not a valid RealmRepresentation", e);
        }
        realm.setEnabled(true);
        admin.realms().create(realm);

        RealmResource realmResource = admin.realm(realmName);
        List<String> appliedFixtures = new ArrayList<>();

        RealmRepresentation live = realmResource.toRepresentation();
        List<String> listeners = new ArrayList<>(
                live.getEventsListeners() == null ? List.of() : live.getEventsListeners());
        if (!listeners.contains("user-sync")) {
            listeners.add("user-sync");
            live.setEventsListeners(listeners);
            appliedFixtures.add("eventsListeners += user-sync");
        }
        live.setEventsEnabled(Boolean.TRUE);
        live.setAdminEventsEnabled(Boolean.TRUE);
        live.setAdminEventsDetailsEnabled(Boolean.TRUE);
        realmResource.update(live);

        ClientRepresentation testClient = new ClientRepresentation();
        testClient.setClientId(TEST_CLIENT_ID);
        testClient.setPublicClient(true);
        testClient.setStandardFlowEnabled(true);
        testClient.setDirectAccessGrantsEnabled(true);
        testClient.setRedirectUris(List.of(TEST_REDIRECT_URI, "http://localhost:9999/*"));
        testClient.setWebOrigins(List.of("*"));
        testClient.setEnabled(true);
        realmResource.clients().create(testClient).close();
        appliedFixtures.add("public test client " + TEST_CLIENT_ID);

        return appliedFixtures;
    }

    // ------------------------------------------------------------------
    // accessors
    // ------------------------------------------------------------------

    public String realmName() {
        return realmName;
    }

    public Path dockerResources() {
        return dockerResources;
    }

    public Path themesDirectory() {
        return dockerResources.resolve("keycloak/themes");
    }

    /** The shipped realm document, before substitution. */
    public JsonNode productionRealmJson() {
        return productionRealmJson;
    }

    /** Resolved value of a {@code ${...}} placeholder, e.g. the OTP authenticator secret. */
    public String value(String placeholder) {
        return substitutions.get(placeholder);
    }
}
