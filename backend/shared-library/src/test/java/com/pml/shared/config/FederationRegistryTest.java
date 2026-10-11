package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The type ownership registry and the SDL describe the same graph.
 *
 * <h2>The direction this checks, which nothing else did</h2>
 * {@code FederationContractLintTest} reads the SDL and asserts it is internally consistent: one
 * owner per key, a fetcher for every key, no {@code id} inside an extend block. All true, and all
 * of it would still pass if the registry described a completely different graph — which it
 * does, in three places.
 *
 * <p>So this reads the <b>registry</b> and asks whether the SDL implements it. That is the
 * direction that catches a design recorded and never built: a registry row saying booking extends
 * `TicketTier` with `availableQuantity` is only true if the SDL says so, and a gap there means
 * booking gets the number some other way that nobody has reviewed as part of the graph.</p>
 *
 * <h2>Two known deviations, allowlisted with reasons</h2>
 * Each is a design decision, not a defect, and each changes the client contract — so none is
 * silently "fixed" to match the registry. They are pinned here so a fourth cannot appear unnoticed,
 * and {@link #theAllowlistDoesNotOutliveItsEntries()} fails if one is resolved and left behind.
 */
@Tag("L4")
@Tag("ET-PLT-004")
@DisplayName("ET-PLT-004-R1 · §4's ownership registry matches the SDL")
class FederationRegistryTest {

    private static final List<String> SUBGRAPHS = List.of("catalog", "booking", "identity");

    /**
     * Registry rows the SDL does not implement, each with why it is not simply a bug to fix.
     *
     * <p>Keyed {@code Type/subgraph-that-should-reference-it}.</p>
     */
    private static final Map<String, String> KNOWN_DEVIATIONS = new LinkedHashMap<>(Map.of(
            "TicketTier/booking",
            "§4 says booking extends TicketTier with availableQuantity and soldQuantity. The code "
                    + "instead has catalog own the counts and booking reserve against them over "
                    + "POST /api/internal/inventory/tiers/{id}/reserve. That is coherent — one "
                    + "owner, one counter, no distributed decrement — and it is what ships. §4 is "
                    + "the stale half here, not the code.",

            "Event/identity",
            "§4 says identity extends Event with accessGrants. No such field exists in identity's "
                    + "SDL. Absent rather than contradicted — the design was recorded and never "
                    + "built."));

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static Path specPath() {
        return backendRoot().getParent()
                .resolve("specs/_platform/004-federation-contract/spec.md");
    }

    private static String sdl(String subgraph) throws IOException {
        String raw = Files.readString(backendRoot()
                .resolve(subgraph + "-service/src/main/resources/graphql/schema.graphqls"));
        // `#` comments stripped: several types are discussed in prose above their declaration, and
        // a lint that counts prose reports a type as declared because somebody explained it.
        return raw.replaceAll("(?m)#.*$", "");
    }

    /** One row of the type ownership table. */
    private record Row(String type, String owner, Set<String> stubbedBy, Set<String> extendedBy) {}

    private static List<Row> ownershipRegistry() throws IOException {
        String spec = Files.readString(specPath());
        int start = spec.indexOf("### Type ownership registry");
        assertThat(start).as("§4's ownership registry heading must exist").isGreaterThan(0);
        // Bounded to this table only. Parsing to the end of the section would swallow the paging
        // registry that follows and report `myTickets` as a type with a missing stub.
        String table = spec.substring(start, spec.indexOf("###", start + 10));

        List<Row> rows = new ArrayList<>();
        Matcher row = Pattern.compile(
                "\\|\\s*`(\\w+)`\\s*\\|\\s*(\\w+)\\s*\\|([^|]*)\\|([^|]*)\\|").matcher(table);
        while (row.find()) {
            rows.add(new Row(row.group(1), row.group(2),
                    subgraphsIn(row.group(3)), subgraphsIn(row.group(4))));
        }
        return rows;
    }

    private static Set<String> subgraphsIn(String cell) {
        Set<String> found = new LinkedHashSet<>();
        for (String subgraph : SUBGRAPHS) {
            if (cell.contains(subgraph)) {
                found.add(subgraph);
            }
        }
        return found;
    }

    @Test
    @DisplayName("every type another subgraph references is owned with a resolvable @key")
    void referencedTypesAreOwnedWithAKey() throws IOException {
        assumeTrue(Files.exists(specPath()), "specs/ not checked out beside backend/");
        List<Row> registry = ownershipRegistry();
        List<String> problems = new ArrayList<>();

        for (Row r : registry) {
            if (r.stubbedBy().isEmpty() && r.extendedBy().isEmpty()) {
                continue;   // owned but referenced by nobody — a @key would be unused ceremony
            }
            String owner = sdl(r.owner());
            boolean owned = Pattern
                    .compile("^type\\s+" + r.type() + "\\s[^{]*@key\\(fields:\\s*\"id\"\\)(?![^{]*resolvable)",
                            Pattern.MULTILINE)
                    .matcher(owner).find();
            if (!owned && !isAllowlisted(r, problems)) {
                problems.add(r.type() + " is referenced by another subgraph but " + r.owner()
                        + " does not own it with a resolvable @key");
            }
        }

        assertThat(registry).as("no registry rows parsed — the table moved or the regex is wrong")
                .hasSizeGreaterThan(10);
        assertThat(problems)
                .as("a stub pointing at a type nobody owns with a key does not compose, and the "
                        + "router has nothing to call when a client traverses into it")
                .isEmpty();
    }

    @Test
    @DisplayName("every subgraph §4 says references a type actually declares a stub or extend")
    void referencesInTheRegistryExistInTheSdl() throws IOException {
        assumeTrue(Files.exists(specPath()), "specs/ not checked out beside backend/");
        List<String> problems = new ArrayList<>();

        for (Row r : ownershipRegistry()) {
            Set<String> referencing = new LinkedHashSet<>(r.stubbedBy());
            referencing.addAll(r.extendedBy());
            for (String subgraph : referencing) {
                if (KNOWN_DEVIATIONS.containsKey(r.type() + "/" + subgraph)) {
                    continue;
                }
                String sdl = sdl(subgraph);
                boolean declares = Pattern
                        .compile("^(extend\\s+)?type\\s+" + r.type() + "\\s[^{]*@key", Pattern.MULTILINE)
                        .matcher(sdl).find();
                if (!declares) {
                    problems.add(subgraph + " should stub or extend " + r.type()
                            + " per §4, and declares neither");
                }
            }
        }

        assertThat(problems)
                .as("§4 and the SDL describing different graphs is how a design gets recorded, "
                        + "never built, and relied on anyway — add the declaration, or move the "
                        + "row to KNOWN_DEVIATIONS with the reason it is not a defect")
                .isEmpty();
    }

    @Test
    @DisplayName("the allowlist does not outlive its entries")
    void theAllowlistDoesNotOutliveItsEntries() throws IOException {
        assumeTrue(Files.exists(specPath()), "specs/ not checked out beside backend/");
        List<String> resolved = new ArrayList<>();

        for (Map.Entry<String, String> deviation : KNOWN_DEVIATIONS.entrySet()) {
            String[] parts = deviation.getKey().split("/");
            boolean declares = Pattern
                    .compile("^(extend\\s+)?type\\s+" + parts[0] + "\\s[^{]*@key", Pattern.MULTILINE)
                    .matcher(sdl(parts[1])).find();
            if (declares) {
                resolved.add(deviation.getKey() + " now exists in the SDL");
            }
        }

        // An allowlist entry for something that has since been built grants an exemption nobody is
        // using, and quietly re-permits the divergence if it ever returns.
        assertThat(resolved)
                .as("this deviation has been resolved — delete its entry so the rule applies again")
                .isEmpty();
        assertThat(KNOWN_DEVIATIONS.values())
                .as("every deviation carries a reason, because the next reader's question is "
                        + "always why, not what")
                .allSatisfy(reason -> assertThat(reason).hasSizeGreaterThan(80));
    }

    private static boolean isAllowlisted(Row r, List<String> unusedProblems) {
        Set<String> referencing = new LinkedHashSet<>(r.stubbedBy());
        referencing.addAll(r.extendedBy());
        return referencing.stream()
                .allMatch(s -> KNOWN_DEVIATIONS.containsKey(r.type() + "/" + s));
    }
}
