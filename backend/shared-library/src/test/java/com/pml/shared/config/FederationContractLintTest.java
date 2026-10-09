package com.pml.shared.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The federation contract, checked without a router.
 *
 * <h2>Composition is not enough</h2>
 * {@code compose-supergraph.sh} answers one question: do the three subgraphs fit together. It
 * says nothing about whether the graph <em>works</em>, and the two gaps below both compose
 * cleanly and both fail at runtime as a <b>null</b> rather than an error — which is the hardest
 * kind of failure to notice, because the query succeeds and the response is well-formed.
 *
 * <ul>
 *   <li><b>A resolvable {@code @key} with no entity fetcher.</b> The type advertises that it can
 *       be resolved here. The router sends {@code _entities}, finds nothing registered, and the
 *       field comes back null. A client cannot tell that from a genuinely absent record.</li>
 *   <li><b>Authorization inside an entity fetcher.</b> The caller is the router, resolving a
 *       reference the user already reached legitimately — not the user. A check here denies a
 *       field the user is entitled to, again as a null. Authorization belongs on the field
 *       (the {@code @auth} directive).</li>
 * </ul>
 *
 * <h2>And two that fail loudly, kept because they are cheap</h2>
 * Redefining {@code id} inside an {@code extend type} and mixing federation versions both break
 * composition. They are asserted here so the message names the file, rather than arriving as
 * rover output in CI.
 */
@Tag("L4")
@Tag("ET-PLT-004")
@DisplayName("ET-PLT-004-R1/R2 · ownership, keys and entity resolution")
class FederationContractLintTest {

    /**
     * The gate the CI workflow runs: the three subgraphs compose at the pinned federation version,
     * and three deliberately broken variants are each refused with their own error. Run here too,
     * so a schema change that breaks composition fails the local build. Skipped only where
     * {@code rover} is not installed; CI installs it and runs the script directly.
     */
    @Test
    @DisplayName("ET-PLT-004-R6 · the supergraph composes, and each broken subgraph is refused for its own reason")
    void compositionGate() throws IOException, InterruptedException {
        Path gate = Path.of("../tools/federation/composition-gate.sh");
        assertThat(gate).exists();
        assertThat(Files.isExecutable(gate)).isTrue();
        Assumptions.assumeTrue(roverInstalled(), "rover is not installed");

        Process run = new ProcessBuilder("bash", gate.toString()).redirectErrorStream(true).start();
        assertThat(run.waitFor(5, TimeUnit.MINUTES)).as("the gate finished").isTrue();
        String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(run.exitValue()).as(output).isZero();
        assertThat(output)
                .contains("PASS  the three subgraphs compose")
                .contains("PASS  a redeclared id in an extend type is refused")
                .contains("PASS  a shared field with a mismatched scalar is refused (FIELD_TYPE_MISMATCH)")
                .contains("PASS  a field of a type another subgraph owns is refused (INVALID_FIELD_SHARING)");
    }

    private static boolean roverInstalled() {
        try {
            return new ProcessBuilder("rover", "--version").start().waitFor(30, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static final List<String> SERVICES =
            List.of("catalog-service", "booking-service", "identity-service");

    /** {@code type X @key(fields: "…")} — without {@code resolvable: false}, this subgraph owns X. */
    private static final Pattern OWNED_TYPE = Pattern.compile(
            "(?m)^type\\s+(\\w+)\\s+@key\\(fields:\\s*\"[^\"]+\"\\s*\\)");

    private static final Pattern ENTITY_FETCHER =
            Pattern.compile("@DgsEntityFetcher\\s*\\(\\s*name\\s*=\\s*\"(\\w+)\"");

    @Test
    @DisplayName("every type this subgraph owns can actually be resolved by key")
    void everyOwnedTypeHasAnEntityFetcher() throws IOException {
        List<String> problems = new ArrayList<>();

        for (String service : SERVICES) {
            Set<String> owned = ownedTypes(service);
            Set<String> resolvable = entityFetcherNames(service);

            Set<String> unresolvable = new TreeSet<>(owned);
            unresolvable.removeAll(resolvable);

            unresolvable.forEach(type -> problems.add(
                    "%s declares `type %s @key(...)` but registers no @DgsEntityFetcher for it"
                            .formatted(service, type)));
        }

        assertThat(problems)
                .as("""
                    A resolvable @key with no fetcher composes cleanly and resolves to null at \
                    runtime. The query succeeds, the response is well-formed, and the entity is \
                    simply absent — indistinguishable from a record that was deleted.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no entity fetcher registers a type its subgraph does not own")
    void noFetcherResolvesAForeignType() throws IOException {
        List<String> problems = new ArrayList<>();

        for (String service : SERVICES) {
            Set<String> owned = ownedTypes(service);
            Set<String> orphaned = new TreeSet<>(entityFetcherNames(service));
            orphaned.removeAll(owned);

            orphaned.forEach(type -> problems.add(
                    "%s has an @DgsEntityFetcher for %s, which it does not declare with a @key"
                            .formatted(service, type)));
        }

        assertThat(problems)
                .as("a fetcher for a type this subgraph does not own is never called — the router "
                        + "routes _entities by the @key it composed, so the code is dead")
                .isEmpty();
    }

    @Test
    @DisplayName("no entity fetcher performs authorization")
    void entityFetchersDoNotAuthorize() throws IOException {
        List<String> problems = new ArrayList<>();
        Pattern authorization = Pattern.compile(
                "@PreAuthorize|@PostAuthorize|hasRole\\(|hasAuthority\\(|checkPermission|requirePermission");

        for (String service : SERVICES) {
            for (Path source : javaSources(service)) {
                String code = Files.readString(source);
                if (!code.contains("@DgsEntityFetcher")) {
                    continue;
                }
                if (authorization.matcher(code).find()) {
                    problems.add("%s performs authorization — the router is the caller, not the user"
                            .formatted(source.getFileName()));
                }
            }
        }

        assertThat(problems)
                .as("""
                    An entity fetcher resolves a reference the user already reached legitimately. \
                    A check here denies a field they are entitled to, and it denies it as a null. \
                    Authorization belongs on the field, under ET-PLT-007's @auth.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no extend type redeclares id, and all subgraphs link one federation version")
    void sdlIsComposable() throws IOException {
        List<String> problems = new ArrayList<>();
        Map<String, Set<String>> versions = new LinkedHashMap<>();

        for (String service : SERVICES) {
            String sdl = Files.readString(schema(service));

            problems.addAll(redeclaredIds(service, sdl));

            Matcher link = Pattern.compile("specs\\.apollo\\.dev/federation/(v[\\d.]+)").matcher(sdl);
            while (link.find()) {
                versions.computeIfAbsent(link.group(1), k -> new LinkedHashSet<>()).add(service);
            }
        }

        if (versions.size() > 1) {
            problems.add("subgraphs link different federation versions: " + versions);
        }
        if (versions.isEmpty()) {
            problems.add("no federation @link found in any subgraph — nothing was parsed");
        }

        assertThat(problems).as("R2: one federation version across the three subgraphs").isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-006 · a broken subgraph fails this lint — the rule is exercised, not described")
    void aBrokenSubgraphIsRejected() {
        // A broken subgraph has to *fail* this lint. Every assertion above runs
        // against three schemas that are correct, so all four would pass just as happily if the
        // rule matched nothing at all — a lint is only worth its green when its red has been
        // seen. This feeds the same checker a subgraph carrying the classic composition error —
        // `id` redefined inside an extend block — and requires it to be caught.
        String broken = """
                extend schema @link(url: "https://specs.apollo.dev/federation/v2.9")

                type Event @key(fields: "id", resolvable: false) {
                    id: ID!
                }

                extend type Event @key(fields: "id") {
                    id: ID! @external
                    ticketsSold: Int!
                }
                """;

        assertThat(redeclaredIds("fixture", broken))
                .as("`extend type Event` redeclaring id is the \"tried to redefine field 'id'\" "
                        + "composition failure; a checker that misses it protects nothing")
                .isNotEmpty()
                .allSatisfy(problem -> assertThat(problem).contains("Event").contains("redeclares id"));
    }

    @Test
    @DisplayName("ET-PLT-006 · a correct subgraph is not rejected")
    void aCorrectSubgraphPasses() {
        // The other half. A checker that flags everything catches the broken fixture above and
        // is equally useless — it would fail the build on every honest schema until somebody
        // deleted it.
        String correct = """
                extend schema @link(url: "https://specs.apollo.dev/federation/v2.9")

                type Event @key(fields: "id", resolvable: false) {
                    id: ID!
                }

                extend type Event @key(fields: "id") {
                    ticketsSold: Int!
                }
                """;

        assertThat(redeclaredIds("fixture", correct)).isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    /**
     * {@code extend type X { id: … }} — the "tried to redefine field 'id'" composition error.
     *
     * <p>Extracted from {@link #sdlIsComposable} so the same code can be pointed at a deliberately
     * broken fixture. A rule that only ever runs against schemas known to be correct has never
     * demonstrated that it can fail.
     */
    private static List<String> redeclaredIds(String service, String sdl) {
        List<String> problems = new ArrayList<>();
        Matcher extension = Pattern.compile(
                        "(?ms)^extend\\s+type\\s+(\\w+)[^{]*\\{(.*?)^\\}").matcher(sdl);
        while (extension.find()) {
            if (Pattern.compile("(?m)^\\s+id\\s*:").matcher(extension.group(2)).find()) {
                problems.add("%s: `extend type %s` redeclares id"
                        .formatted(service, extension.group(1)));
            }
        }
        return problems;
    }


    private static Path schema(String service) {
        return Path.of("../%s/src/main/resources/graphql/schema.graphqls".formatted(service));
    }

    private static Set<String> ownedTypes(String service) throws IOException {
        String sdl = Files.readString(schema(service));
        Set<String> owned = new LinkedHashSet<>();
        Matcher matcher = OWNED_TYPE.matcher(sdl);
        while (matcher.find()) {
            // resolvable: false marks a stub — a reference this subgraph can hold but not resolve.
            int lineEnd = sdl.indexOf('\n', matcher.end());
            String rest = sdl.substring(matcher.start(), lineEnd < 0 ? sdl.length() : lineEnd);
            if (!rest.contains("resolvable: false")) {
                owned.add(matcher.group(1));
            }
        }
        return owned;
    }

    private static Set<String> entityFetcherNames(String service) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (Path source : javaSources(service)) {
            Matcher matcher = ENTITY_FETCHER.matcher(Files.readString(source));
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static List<Path> javaSources(String service) throws IOException {
        Path root = Path.of("../%s/src/main/java".formatted(service));
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
        }
    }
}
