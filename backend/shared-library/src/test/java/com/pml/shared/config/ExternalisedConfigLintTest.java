package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Credentials are external, and a missing one fails fast by name.
 *
 * <h2>The defect this was written for</h2>
 * {@code identity-service/application.yml} carried three credentials with working defaults:
 *
 * <pre>
 *   admin-password: ${KEYCLOAK_ADMIN_PASSWORD:admin}
 *   client-secret:  ${IDENTITY_SERVICE_SECRET:identity-service-secret}
 * </pre>
 *
 * <p>The base file is inherited by every profile, and {@code application-prod.yml} overrode
 * neither. So in production, a missing environment variable did not fail — the service started
 * against Keycloak using a credential anyone could read in the repository, and nothing
 * anywhere said so. That is the precise scenario this lint exists to prevent: <em>failing fast with
 * the variable's name beats starting with a default and failing at 3am against the wrong
 * Keycloak</em> — except this one would not have failed at 3am either.
 *
 * <h2>Empty defaults are a different thing</h2>
 * {@code ${REDIS_PASSWORD:}} means "no password", which is a legitimate configuration, not a
 * secret hiding in git. {@code ${VAR:something}} on a credential is the defect. The lint draws
 * the line there rather than banning defaults outright, which would flag every URL and realm
 * name and be switched off within a week.
 *
 * <h2>Profile files may carry development values</h2>
 * {@code application-local.yml} is where the dev credentials now live. A value in a file whose
 * name says {@code local} is visibly a development value; the same value in the base file is a
 * production fallback nobody intended.
 */
@Tag("L3")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R6 · credentials are external and fail fast by name")
class ExternalisedConfigLintTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    /** Keys whose value is a credential. */
    private static final Pattern CREDENTIAL_KEY = Pattern.compile(
            "^\\s*[a-z-]*(password|secret|private-key|api-key|apikey|access-key|credential)[a-z-]*\\s*:",
            Pattern.CASE_INSENSITIVE);

    /** {@code ${VAR:something}} — a non-empty default. {@code ${VAR:}} deliberately does not match. */
    private static final Pattern NON_EMPTY_DEFAULT =
            Pattern.compile("\\$\\{[^:}]+:[^}]+}");

    /**
     * {@code scheme://user:literal-password@host}. A password written as a placeholder
     * ({@code ${DB_PASSWORD}}) does not match, which is the form a URI is allowed to take. The key
     * of such a line is {@code uri} or {@code url}, so {@link #CREDENTIAL_KEY} never sees it.
     */
    private static final Pattern URI_CREDENTIALS =
            Pattern.compile("[a-z][a-z0-9+.-]*://[^/\\s:@$]+:[^/\\s@$]+@", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("no credential in a deployable config file carries a working default")
    void noCredentialHasAWorkingDefaultInTheBaseProfile() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path config : deployableConfigFiles()) {
            String[] lines = Files.readString(config).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.strip().startsWith("#")) {
                    continue;
                }
                if (CREDENTIAL_KEY.matcher(line).find() && NON_EMPTY_DEFAULT.matcher(line).find()) {
                    offenders.add("%s:%d → %s".formatted(
                            BACKEND_ROOT.relativize(config), i + 1, line.strip()));
                }
            }
        }

        assertThat(offenders)
                .as("""
                    A credential with a working default does not fail when the environment \
                    variable is missing — it starts, against the real system, using a value \
                    committed to the repository. Drop the default so startup fails naming the \
                    variable, and put the development value in application-local.yml where it \
                    is visibly a development value.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no deployable config file embeds a password in a connection URI")
    void noPasswordIsEmbeddedInAUriOutsideTheLocalProfile() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path config : deployableConfigFiles()) {
            String[] lines = Files.readString(config).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (!line.strip().startsWith("#") && URI_CREDENTIALS.matcher(line).find()) {
                    offenders.add("%s:%d → %s".formatted(
                            BACKEND_ROOT.relativize(config), i + 1, line.strip()));
                }
            }
        }

        assertThat(offenders)
                .as("""
                    A password inside a connection string is inherited by every profile that \
                    does not override it, including production, and is invisible to the key-based \
                    check because the key is only 'uri'. Take the whole URI from the environment \
                    and keep the development one in application-local.yml.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the URI check flags a literal password and leaves placeholders and plain hosts alone")
    void uriCredentialPatternMatchesOnlyLiteralPasswords() {
        assertThat(URI_CREDENTIALS.matcher("uri: mongodb://admin:admin_password@localhost:27017/db").find())
                .isTrue();
        assertThat(URI_CREDENTIALS.matcher("uri: mongodb://${DB_USER}:${DB_PASSWORD}@db:27017/x").find())
                .as("a placeholder is the required form").isFalse();
        assertThat(URI_CREDENTIALS.matcher("uri: ${MONGODB_URI}").find()).isFalse();
        assertThat(URI_CREDENTIALS.matcher("url: http://localhost:8084/realms/myticketzm").find())
                .as("a host and port are not a credential").isFalse();
    }

    @Test
    @DisplayName("an unresolved placeholder fails startup and names the variable")
    void unresolvedPlaceholderNamesTheVariable() {
        // The behaviour that matters, proven against Spring rather than assumed: the failure has to
        // name the variable, or an operator at 3am is reading a stack trace that says only that
        // a bean could not be created.
        new ApplicationContextRunner()
                .withUserConfiguration(NeedsACredential.class)
                .run(context -> {
                    assertThat(context).hasFailed();

                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isInstanceOf(BeanCreationException.class);

                    // The variable name is in the cause chain, not the top message — Spring's
                    // outermost frame says only "Unexpected exception during bean creation".
                    // Worth knowing: an operator reading a truncated log sees the useless half.
                    // The name is present, which is what matters, but "fails fast naming
                    // the variable" is true of the stack trace rather than of its first line.
                    assertThat(messageChainOf(failure))
                            .as("startup must identify which variable is missing")
                            .contains("Could not resolve placeholder")
                            .contains("ET_PLT_001_REQUIRED_SECRET");
                });
    }

    @Test
    @DisplayName("an empty default is not flagged — \"no password\" is a configuration, not a secret")
    void emptyDefaultsAreNotCredentialsInGit() {
        assertThat(NON_EMPTY_DEFAULT.matcher("password: ${REDIS_PASSWORD:}").find()).isFalse();
        assertThat(NON_EMPTY_DEFAULT.matcher("secret-access-key: ${AWS_SECRET_ACCESS_KEY:}").find()).isFalse();

        assertThat(NON_EMPTY_DEFAULT.matcher("admin-password: ${KEYCLOAK_ADMIN_PASSWORD:admin}").find())
                .as("a working fallback credential is the defect")
                .isTrue();
    }

    @Test
    @DisplayName("non-credential defaults are left alone — URLs and realm names are convenience, not exposure")
    void nonCredentialDefaultsAreAllowed() {
        assertThat(CREDENTIAL_KEY.matcher("  server-url: ${KEYCLOAK_URL:http://localhost:8084}").find())
                .as("banning every default would flag every URL and the lint would be switched off")
                .isFalse();
        assertThat(CREDENTIAL_KEY.matcher("  realm: ${KEYCLOAK_REALM:myticketzm}").find()).isFalse();
    }

    // --------------------------------------------------------------------- helpers

    private static String messageChainOf(Throwable throwable) {
        StringBuilder chain = new StringBuilder();
        for (Throwable t = throwable; t != null && t != t.getCause(); t = t.getCause()) {
            chain.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append('\n');
        }
        return chain.toString();
    }

    /**
     * Every file that can reach a deployed service: the base {@code application.yml} and each
     * {@code application-<profile>.yml} except the two whose name says they hold development
     * values, {@code local} and {@code test}.
     */
    private static List<Path> deployableConfigFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path resources = module.resolve("src/main/resources");
                if (!Files.isDirectory(resources)) {
                    continue;
                }
                try (Stream<Path> configs = Files.list(resources)) {
                    configs.filter(p -> p.getFileName().toString().matches("application(-[a-z]+)?\\.yml"))
                            .filter(p -> !p.getFileName().toString().matches("application-(local|test)\\.yml"))
                            .forEach(files::add);
                }
            }
        }
        assertThat(files)
                .as("no application.yml found — the layout moved and this lint checked nothing")
                .hasSizeGreaterThanOrEqualTo(4);
        return files;
    }

    @Configuration(proxyBeanMethods = false)
    static class NeedsACredential {

        /**
         * Without this, ApplicationContextRunner resolves no placeholders at all and injects
         * the literal "${...}" — the context starts, and the test would assert nothing.
         */
        @org.springframework.context.annotation.Bean
        static org.springframework.context.support.PropertySourcesPlaceholderConfigurer placeholders() {
            return new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
        }

        @SuppressWarnings("unused")
        NeedsACredential(@Value("${ET_PLT_001_REQUIRED_SECRET}") String secret) {
            // never constructed — the placeholder cannot resolve, which is the point
        }
    }
}
