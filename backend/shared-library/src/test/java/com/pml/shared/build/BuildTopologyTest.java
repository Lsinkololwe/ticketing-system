package com.pml.shared.build;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The build topology, asserted rather than assumed.
 *
 * <h2>Why this test exists at all</h2>
 * The build is the only root in the dependency graph: every module and every other check is
 * downstream of it. Presence is not conformance, and an unchecked invariant is one refactor from
 * being untrue, so the topology is asserted here rather than trusted.
 *
 * <p>{@code keycloak-extensions} deliberately has no {@code <parent>} (a Spring Boot parent would
 * drag Spring onto a Keycloak provider classpath), so it inherits <em>no</em> compiler
 * configuration either. A lower release is still a valid, green build — just not Java 21. The
 * class-file assertion below reads {@code target/classes} rather than the POM because the compiled
 * major version is the fact that matters, and a POM can declare one release while a missing
 * inheritance compiles another.</p>
 *
 * <h2>What this reads, and what it cannot</h2>
 * These are POM and class-file assertions — layer 1, no Spring, no containers. They cover the
 * structural half of the build rules: what is declared, what is inherited, what got compiled, what ended up
 * in the shaded JAR.
 *
 * <p>The behavioural half — "a deliberately introduced back-edge fails the reactor", "a module
 * compiling at a lower release fails the build" — is mutation verification, not a test: it means
 * breaking the POM and watching the enforcer refuse. Those acceptance boxes stay open with a note
 * rather than being claimed here, because a test that asserts a rule <em>is configured</em> has not
 * shown the rule <em>bites</em>. {@link EnforcerRefusalTest} covers the one refusal that can be
 * exercised safely from a test.</p>
 */
@Tag("L1")
@Tag("ET-PLT-012")
@DisplayName("ET-PLT-012 · the build topology")
class BuildTopologyTest {

    /** The Spring modules, all of which inherit from the backend parent. */
    private static final List<String> SPRING_MODULES = List.of(
            "shared-library", "catalog-service", "booking-service", "identity-service",
            "api-gateway");

    /** The SPI module, which deliberately inherits nothing. */
    private static final String SPI_MODULE = "keycloak-extensions";

    /** Java 21. {@code 65 = 21 + 44}, the constant offset between Java and class-file versions. */
    private static final int JAVA_21_CLASS_MAJOR = 65;

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static String pom(String module) throws IOException {
        return Files.readString(backendRoot().resolve(module).resolve("pom.xml"));
    }

    private static String parentPom() throws IOException {
        return Files.readString(backendRoot().resolve("pom.xml"));
    }

    /**
     * XML comments removed before matching.
     *
     * <p>Every lint in this corpus that skipped this step has been wrong at least once: a POM that
     * explains in a comment why it does not declare something then reads as though it declares it.
     * Reluctant quantifier deliberately — the obvious {@code (?:[^-]|-(?!->))*} form overflows the
     * stack on a file of any size.</p>
     */
    private static String withoutComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
    }

    // ── R1 · one parent declares every managed version ────────────────────────────────────────────

    @Test
    @DisplayName("R1 · one parent declares every managed version · the parent is a POM inheriting the Spring Boot parent")
    void oneParent_parentIsAPomInheritingBoot() throws IOException {
        String parent = withoutComments(parentPom());

        assertThat(parent)
                .as("the aggregator must be <packaging>pom</packaging>")
                .contains("<packaging>pom</packaging>");
        assertThat(parent)
                .as("every module's versions come from the Boot parent")
                .contains("<artifactId>spring-boot-starter-parent</artifactId>");
    }

    @Test
    @DisplayName("R1 · one parent declares every managed version · all five BOMs are imported")
    void oneParent_allFiveBomsAreImported() throws IOException {
        String parent = withoutComments(parentPom());

        for (String bom : List.of(
                "graphql-dgs-platform-dependencies",
                "spring-cloud-azure-dependencies",
                "spring-cloud-dependencies",
                "testcontainers-bom",
                "temporal-bom")) {
            assertThat(parent)
                    .as(bom + " must be imported by the parent, so no module pins its members")
                    .contains(bom);
        }
    }

    @Test
    @DisplayName("R1 · one parent declares every managed version · no module re-declares a version property the parent owns")
    void oneParent_noModuleRedeclaresAManagedProperty() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (String module : SPRING_MODULES) {
            String body = withoutComments(pom(module));
            for (String property : List.of(
                    "java.version", "dgs.version", "spring-cloud-azure.version",
                    "spring-cloud.version", "testcontainers.version")) {
                if (body.contains("<" + property + ">")) {
                    offenders.add(module + " re-declares " + property);
                }
            }
        }

        assertThat(offenders)
                .as("a module-local copy diverges silently: the parent is bumped, the module "
                        + "keeps the old value, and both builds are green")
                .isEmpty();
    }

    /**
     * The platform has no relational dependency, and this is the assertion that keeps it that
     * way.
     *
     * <p>No module declares {@code @ApplicationModuleListener}, a {@code spring-modulith-*}
     * dependency, or a datasource in any {@code application.yml}; events go through the
     * MongoDB outbox. Modulith's event
     * publication needs a <em>blocking</em> driver, which is the one thing a reactive platform
     * cannot give it, so its absence is the correct state and its return would be a
     * regression.</p>
     */
    @Test
    @DisplayName("R1 · one parent declares every managed version · nothing relational: no Modulith, no JDBC, no PostgreSQL driver")
    void oneParent_nothingRelationalIsDeclared() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (String module : SPRING_MODULES) {
            String body = withoutComments(pom(module));
            for (String banned : List.of(
                    "spring-modulith", "spring-boot-starter-jdbc",
                    "<artifactId>postgresql</artifactId>",
                    "spring-boot-starter-data-jpa")) {
                if (body.contains(banned)) {
                    offenders.add(module + " declares " + banned);
                }
            }
        }

        assertThat(offenders)
                .as("a blocking driver on a reactive platform stalls Netty workers; ET-PLT-012 "
                        + "§4 bans the family and the enforcer refuses it")
                .isEmpty();
    }

    // ── R3 · the Keycloak SPI module inherits nothing ─────────────────────────────────────────────

    @Test
    @DisplayName("R3 · the Keycloak SPI module inherits nothing · it is in the reactor and declares no parent")
    void spiModule_inTheReactorWithNoParent() throws IOException {
        assertThat(withoutComments(parentPom()))
                .as("the SPI must build with everything else, or it is built by hand and drifts")
                .contains("<module>" + SPI_MODULE + "</module>");

        assertThat(withoutComments(pom(SPI_MODULE)))
                .as("a Boot parent here would put Spring on a Keycloak provider classpath")
                .doesNotContain("<parent>");
    }

    @Test
    @DisplayName("R3 · the Keycloak SPI module inherits nothing · its Keycloak dependencies are all provided scope")
    void spiModule_keycloakDependenciesAreProvided() throws IOException {
        String body = withoutComments(pom(SPI_MODULE));
        Matcher dependency = Pattern
                .compile("(?s)<dependency>(.*?)</dependency>")
                .matcher(body);

        List<String> offenders = new ArrayList<>();
        int keycloakDependencies = 0;
        while (dependency.find()) {
            String declaration = dependency.group(1);
            if (!declaration.contains("org.keycloak")) {
                continue;
            }
            keycloakDependencies++;
            if (!declaration.contains("<scope>provided</scope>")) {
                offenders.add(declaration.replaceAll("\\s+", " ").trim());
            }
        }

        assertThat(keycloakDependencies)
                .as("if this is zero the loop asserted nothing and would pass forever")
                .isGreaterThan(0);
        assertThat(offenders)
                .as("a compile-scoped Keycloak class gets shaded into the JAR and then loads "
                        + "beside the server's own copy of itself")
                .isEmpty();
    }

    @Test
    @DisplayName("R3 · the Keycloak SPI module inherits nothing · the shaded JAR carries Gson and no Spring or Keycloak")
    void spiModule_shadedJarCarriesGsonOnly() throws IOException {
        Path jar = shadedJar();
        assumeTrue(jar != null, "run `mvn -f backend package` to assert the shaded output");

        Set<String> roots = new LinkedHashSet<>();
        try (JarFile opened = new JarFile(jar.toFile())) {
            opened.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.endsWith(".class"))
                    .forEach(name -> roots.add(topLevelPackage(name)));
        }

        assertThat(roots)
                .as("Gson is shaded in deliberately — Keycloak does not provide it")
                .contains("com");
        assertThat(roots)
                .as("Spring must never reach a Keycloak provider classpath")
                .doesNotContain("org.springframework");

        try (JarFile opened = new JarFile(jar.toFile())) {
            List<String> leaked = opened.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith("org/springframework/")
                            || name.startsWith("org/keycloak/"))
                    .toList();
            assertThat(leaked)
                    .as("provided scope means the server supplies it; a shaded copy shadows "
                            + "the server's own classes and fails at load, not at build")
                    .isEmpty();
        }
    }

    private static String topLevelPackage(String entry) {
        int slash = entry.indexOf('/');
        return slash < 0 ? "" : entry.substring(0, slash);
    }

    // ── R4 · the module graph is acyclic and shared-library is a leaf ─────────────────────────────

    @Test
    @DisplayName("Module graph · shared-library depends on no other module")
    void moduleGraph_sharedLibraryIsALeaf() throws IOException {
        String body = withoutComments(pom("shared-library"));

        for (String service : List.of(
                "catalog-service", "booking-service", "identity-service", "api-gateway")) {
            assertThat(body)
                    .as("shared-library depending on " + service + " is the back-edge that "
                            + "makes the reactor unorderable")
                    .doesNotContain("<artifactId>" + service + "</artifactId>");
        }
    }

    @Test
    @DisplayName("Module graph · every service depends on shared-library and on no other service")
    void moduleGraph_servicesDependOnTheLibraryOnly() throws IOException {
        List<String> services = List.of(
                "catalog-service", "booking-service", "identity-service", "api-gateway");

        for (String module : services) {
            String body = withoutComments(pom(module));

            assertThat(body)
                    .as(module + " must consume the shared library, not re-implement it")
                    .contains("<artifactId>shared-library</artifactId>");

            for (String other : services) {
                if (other.equals(module)) {
                    continue;
                }
                assertThat(body)
                        .as(module + " must not depend on " + other + " — services talk over "
                                + "the graph and the broker, never by linking")
                        .doesNotContain("<artifactId>" + other + "</artifactId>");
            }
        }
    }

    // ── R5 · Java 21 everywhere, configured once ──────────────────────────────────────────────────

    @Test
    @DisplayName("R5 · Java 21 everywhere, configured once · the parent declares 21 and UTF-8 once")
    void javaVersion_parentDeclaresTwentyOne() throws IOException {
        String parent = withoutComments(parentPom());

        assertThat(parent).contains("<java.version>21</java.version>");
        assertThat(parent).contains("<project.build.sourceEncoding>UTF-8"
                + "</project.build.sourceEncoding>");
    }

    /**
     * The assertion that caught the real defect.
     *
     * <p>Reads compiled output rather than configuration, because configuration was what lied:
     * {@code keycloak-extensions} declared {@code source}/{@code target} 17 and built green for
     * as long as it has existed. A POM assertion would have had to know to look at a module
     * whose whole point is that it inherits nothing.</p>
     */
    @Test
    @DisplayName("R5 · Java 21 everywhere, configured once · every module's classes report class-file major 65, the SPI included")
    void javaVersion_everyModuleCompilesToJava21() throws IOException {
        List<String> modules = new ArrayList<>(SPRING_MODULES);
        modules.add(SPI_MODULE);

        List<String> offenders = new ArrayList<>();
        int examined = 0;

        for (String module : modules) {
            Path classes = backendRoot().resolve(module).resolve("target/classes");
            if (!Files.isDirectory(classes)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(classes)) {
                List<Path> compiled = tree
                        .filter(path -> path.toString().endsWith(".class"))
                        .limit(25)
                        .toList();
                for (Path each : compiled) {
                    examined++;
                    int major = classFileMajor(each);
                    if (major != JAVA_21_CLASS_MAJOR) {
                        offenders.add(module + " → " + backendRoot().relativize(each)
                                + " is major " + major + " (Java " + (major - 44) + ")");
                    }
                }
            }
        }

        assumeTrue(examined > 0, "nothing compiled yet — run `mvn -f backend package` first");
        assertThat(offenders)
                .as("a module compiled below 21 links against a smaller class library than the "
                        + "platform assumes, and only fails when it reaches the missing API")
                .isEmpty();
    }

    private static int classFileMajor(Path classFile) throws IOException {
        try (InputStream raw = Files.newInputStream(classFile);
             DataInputStream in = new DataInputStream(raw)) {
            in.readInt();       // 0xCAFEBABE
            in.readUnsignedShort();  // minor
            return in.readUnsignedShort();
        }
    }

    // ── R6 · plugin versions are managed, never floating ──────────────────────────────────────────

    @Test
    @DisplayName("R6 · plugin versions are managed, never floating · every plugin the SPI module declares carries an explicit version")
    void pluginVersions_spiPluginsArePinned() throws IOException {
        String body = withoutComments(pom(SPI_MODULE));
        Matcher plugin = Pattern.compile("(?s)<plugin>(.*?)</plugin>").matcher(body);

        List<String> unpinned = new ArrayList<>();
        int plugins = 0;
        while (plugin.find()) {
            plugins++;
            String declaration = plugin.group(1);
            if (!declaration.contains("<version>")) {
                unpinned.add(declaration.replaceAll("\\s+", " ").trim());
            }
        }

        assertThat(plugins)
                .as("no plugins found means the regex is wrong, not that the module is clean")
                .isGreaterThan(0);
        assertThat(unpinned)
                .as("this module has no parent, so an unpinned plugin resolves to whatever "
                        + "the running Maven defaults to — surefire silently ran 3.2.5 here "
                        + "against 3.5.3 everywhere else")
                .isEmpty();
    }

    // ── R7 · the build enforces its own structure ─────────────────────────────────────────────────

    @Test
    @DisplayName("R7 · the build enforces its own structure · the enforcer runs at validate with all five rules")
    void enforcer_allFiveRulesRunAtValidate() throws IOException {
        String parent = withoutComments(parentPom());

        assertThat(parent).contains("maven-enforcer-plugin");
        assertThat(parent)
                .as("validate is before compile — the topology is checked before anything "
                        + "is built on top of it")
                .contains("<phase>validate</phase>");

        for (String rule : List.of(
                "requireMavenVersion", "requireJavaVersion",
                "banDuplicatePomDependencyVersions", "requireUpperBoundDeps",
                "bannedDependencies")) {
            // Both spellings count: a rule with no options is written self-closing,
            // `<banDuplicatePomDependencyVersions/>`, and an assertion demanding `>` right
            // after the name reports a configured rule as missing. That is this test's own
            // first false positive, kept in mind rather than kept quiet.
            assertThat(parent)
                    .as(rule + " is named by ET-PLT-012 §4 and must actually be configured")
                    .containsPattern("<" + rule + "\\s*[/>]");
        }
    }

    @Test
    @DisplayName("R7 · the build enforces its own structure · the servlet stack and the relational stack are both banned")
    void enforcer_theBannedListIsComplete() throws IOException {
        String parent = withoutComments(parentPom());

        for (String banned : List.of(
                "spring-boot-starter-web",
                "spring-boot-starter-tomcat",
                "spring-boot-starter-jetty",
                "spring-boot-starter-undertow",
                "org.postgresql:postgresql",
                "spring-boot-starter-jdbc",
                "spring-boot-starter-data-jpa",
                "org.springframework.modulith")) {
            assertThat(parent)
                    .as(banned + " is banned by §4 — the enforcer is what makes that true "
                            + "rather than aspirational")
                    .contains(banned);
        }
    }

    private static Path shadedJar() throws IOException {
        Path target = backendRoot().resolve(SPI_MODULE).resolve("target");
        if (!Files.isDirectory(target)) {
            return null;
        }
        try (Stream<Path> entries = Files.list(target)) {
            return entries
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().contains("original"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
