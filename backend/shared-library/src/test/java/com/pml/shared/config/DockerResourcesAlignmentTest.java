package com.pml.shared.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The services' Keycloak configuration must match the realm that is actually provisioned.
 *
 * <h2>What this caught</h2>
 * Every service declared a {@code client_credentials} client id that the realm does not
 * contain — {@code identity-service} against a provisioned
 * {@code myticketzm-identity-service}, and the same for catalog, booking and the gateway. No
 * service could have obtained a token. The mismatch is invisible from either repository alone:
 * the service config looks reasonable, the realm looks reasonable, and only the pair is wrong.
 *
 * <h2>Where the truth lives</h2>
 * {@code docker-resources/} is a sibling repository ({@code specs/README.md}: infrastructure
 * lives elsewhere, and a spec proposing to recreate it inside {@code ticketing-system} is
 * wrong). Its {@code keycloak/myticketzm-realm.json} is the realm import, parameterised by
 * {@code .env.ticketing}. This test reads the identifiers from there and compares.
 *
 * <h2>Identifiers only — never values</h2>
 * Client ids and realm names are compared. Secrets are not read, printed, or asserted on: the
 * env files are gitignored precisely so their contents stay out of the repository, and a test
 * that echoed them into a build log would undo that.
 *
 * <h2>Skips when the sibling repository is absent</h2>
 * A CI checkout of {@code ticketing-system} alone cannot see {@code docker-resources}. Skipping
 * is honest there; failing would train people to ignore it.
 */
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R6 · service config matches the provisioned Keycloak realm")
class DockerResourcesAlignmentTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    private static final Path DOCKER_RESOURCES = Path.of("../../../docker-resources");

    private static final Path REALM = DOCKER_RESOURCES.resolve("keycloak/myticketzm-realm.json");

    private static final Path ENV = DOCKER_RESOURCES.resolve(".env.ticketing");

    /** {@code client-id: ${SOME_VAR:the-default}} — the default is what local development uses. */
    private static final Pattern CLIENT_ID =
            Pattern.compile("client-id:\\s*\\$\\{[A-Z_]+:([a-z0-9-]+)}");

    @Test
    @DisplayName("every client id a service defaults to exists in the provisioned realm")
    void serviceClientIdsExistInTheRealm() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(REALM) && Files.isRegularFile(ENV),
                "docker-resources is not checked out beside this repository — skipping");

        Map<String, String> env = readEnv();
        List<String> provisioned = env.entrySet().stream()
                .filter(e -> e.getKey().endsWith("_CLIENT_ID"))
                .map(Map.Entry::getValue)
                .toList();

        assertThat(provisioned)
                .as("the realm env declares no client ids — the file moved or its format changed")
                .isNotEmpty();

        List<String> mismatches = new ArrayList<>();
        for (Path config : baseConfigFiles()) {
            for (String line : Files.readString(config).split("\n", -1)) {
                if (line.strip().startsWith("#")) {
                    continue;
                }
                Matcher matcher = CLIENT_ID.matcher(line);
                while (matcher.find()) {
                    String declared = matcher.group(1);
                    if (!provisioned.contains(declared)) {
                        mismatches.add("%s → '%s' is not a client in the realm"
                                .formatted(BACKEND_ROOT.relativize(config), declared));
                    }
                }
            }
        }

        assertThat(mismatches)
                .as("""
                    A service defaulting to a client id the realm does not contain cannot obtain \
                    a token, and neither repository looks wrong on its own. The provisioned ids \
                    come from docker-resources/.env.ticketing and are substituted into \
                    keycloak/myticketzm-realm.json at import.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the realm name the services target is the one provisioned")
    void realmNameMatches() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(ENV),
                "docker-resources is not checked out beside this repository — skipping");

        String provisionedRealm = readEnv().get("TICKETING_REALM_NAME");
        assertThat(provisionedRealm).as("TICKETING_REALM_NAME is absent from .env.ticketing").isNotBlank();

        List<String> mismatches = new ArrayList<>();
        for (Path config : baseConfigFiles()) {
            for (String line : Files.readString(config).split("\n", -1)) {
                if (line.strip().startsWith("#") || !line.contains("/realms/")) {
                    continue;
                }
                Matcher matcher = Pattern.compile("/realms/([a-z0-9-]+)").matcher(line);
                while (matcher.find()) {
                    String declared = matcher.group(1);
                    // The admin realm is a deliberate second realm (ET-IDN-003 trusted issuers).
                    if (!declared.equals(provisionedRealm)
                            && !declared.equals(provisionedRealm + "-admin")) {
                        mismatches.add("%s → /realms/%s".formatted(
                                BACKEND_ROOT.relativize(config), declared));
                    }
                }
            }
        }

        assertThat(mismatches)
                .as("a token issued by one realm does not validate against another's JWKS")
                .isEmpty();
    }

    @Test
    @DisplayName("no secret value from the sibling repository is echoed into this one")
    void secretsAreNotCopiedIntoServiceConfig() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(ENV),
                "docker-resources is not checked out beside this repository — skipping");

        List<String> secretValues = readEnv().entrySet().stream()
                .filter(e -> e.getKey().endsWith("_SECRET"))
                .map(Map.Entry::getValue)
                .filter(v -> v.length() > 8)
                .toList();

        List<String> leaks = new ArrayList<>();
        for (Path config : allConfigFiles()) {
            String body = Files.readString(config);
            for (String secret : secretValues) {
                if (body.contains(secret)) {
                    // Deliberately reports the file and not the value.
                    leaks.add(BACKEND_ROOT.relativize(config).toString());
                }
            }
        }

        assertThat(leaks)
                .as("""
                    A real credential was copied out of the gitignored env file into config that \
                    is committed. The env files exist so these values stay out of the repository; \
                    reference the variable, never the value.""")
                .isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    private static Map<String, String> readEnv() throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readString(ENV).split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            // Values may carry a trailing `# comment`, as .env.ticketing does throughout.
            String value = parts[1].split("\\s+#", 2)[0].strip().replaceAll("^[\"']|[\"']$", "");
            values.put(parts[0].strip(), value);
        }
        return values;
    }

    private static List<Path> baseConfigFiles() throws IOException {
        return configFiles("application.yml"::equals);
    }

    private static List<Path> allConfigFiles() throws IOException {
        return configFiles(name -> name.startsWith("application") && name.endsWith(".yml"));
    }

    private static List<Path> configFiles(java.util.function.Predicate<String> nameMatches)
            throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path resources = module.resolve("src/main/resources");
                if (!Files.isDirectory(resources)) {
                    continue;
                }
                try (Stream<Path> candidates = Files.list(resources)) {
                    candidates.filter(p -> nameMatches.test(p.getFileName().toString()))
                            .forEach(files::add);
                }
            }
        }
        return files;
    }
}
