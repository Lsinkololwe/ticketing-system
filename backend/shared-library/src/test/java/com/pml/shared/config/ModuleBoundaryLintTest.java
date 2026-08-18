package com.pml.shared.config;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ET-PLT-001 R4, R5 — module boundaries, enforced rather than described.
 *
 * <h2>Two properties, both currently true</h2>
 * <ol>
 *   <li><b>No module reaches into another's internals.</b> A service may depend on another
 *       service's published contract; it may never import its {@code repository} or
 *       {@code service.impl}. Those are the packages whose shape is nobody else's business, and
 *       an import of one turns a private decision into a public one without anybody deciding to.</li>
 *   <li><b>{@code shared-library} is a leaf.</b> It imports nothing from {@code catalog},
 *       {@code booking} or {@code identity}. The moment it does, every service inherits that
 *       service's types transitively, and the library stops being shared and starts being a
 *       distribution channel for one domain's model.</li>
 * </ol>
 *
 * <h2>Why lock in a property that already holds</h2>
 * Both were measured at zero on 2026-08-18. That is precisely when to write the test: a
 * boundary that is currently clean costs nothing to assert and everything to recover once a
 * dozen imports have crossed it. The alternative is discovering it after the fact, when the
 * fix is a refactor rather than a rejected import.
 */
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R4/R5 · modules do not reach into each other's internals")
class ModuleBoundaryLintTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    /** Packages whose contents are one module's private business. */
    private static final Set<String> INTERNAL_PACKAGES = Set.of("repository", "service.impl");

    private static final Set<String> SERVICE_DOMAINS = Set.of("catalog", "booking", "identity");

    private static final Pattern PLATFORM_IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?com\\.pml\\.([a-z]+)\\.(.+?);");

    /** {@code com/pml/<domain>/...} — the domain a source file belongs to. */
    private static final Pattern OWNING_DOMAIN =
            Pattern.compile("com[/\\\\]pml[/\\\\]([a-z]+)[/\\\\]");

    @Test
    @DisplayName("no module imports another module's repository or service.impl")
    void noModuleReachesIntoAnothersInternals() throws IOException {
        List<String> violations = new ArrayList<>();

        forEachSourceFile((path, owner, target, imported) -> {
            if (owner.equals(target)) {
                return;                     // a module's own internals are its own business
            }
            if (INTERNAL_PACKAGES.stream().anyMatch(imported::startsWith)) {
                violations.add("%s → com.pml.%s.%s".formatted(
                        BACKEND_ROOT.relativize(path), target, imported));
            }
        });

        assertThat(violations)
                .as("""
                    Depend on the other module's published contract, not on how it stores or \
                    implements things. An import of a repository or a service.impl makes a \
                    private decision public without anyone deciding to, and the next change to \
                    that package silently becomes a breaking change for a module its author \
                    never thought about.""")
                .isEmpty();
    }

    @Test
    @DisplayName("shared-library is a leaf — it imports nothing from catalog, booking or identity")
    void sharedLibraryIsALeaf() throws IOException {
        List<String> violations = new ArrayList<>();

        forEachSourceFile((path, owner, target, imported) -> {
            if (!"shared".equals(owner)) {
                return;
            }
            if (SERVICE_DOMAINS.contains(target)) {
                violations.add("%s → com.pml.%s.%s".formatted(
                        BACKEND_ROOT.relativize(path), target, imported));
            }
        });

        assertThat(violations)
                .as("""
                    Every service depends on shared-library, so anything it imports becomes a \
                    transitive dependency of all three. One import of a domain type here and the \
                    library stops being shared and becomes a distribution channel for one \
                    domain's model — which is also how a cycle appears in a reactor that \
                    currently has none.""")
                .isEmpty();
    }

    @Test
    @DisplayName("service-to-service coupling is visible and frozen")
    void serviceToServiceCouplingIsFrozen() throws IOException {
        Map<String, Integer> coupling = new LinkedHashMap<>();

        forEachSourceFile((path, owner, target, imported) -> {
            if (SERVICE_DOMAINS.contains(owner) && SERVICE_DOMAINS.contains(target)
                    && !owner.equals(target)) {
                coupling.merge(owner + " → " + target, 1, Integer::sum);
            }
        });

        // Measured at zero on 2026-08-18. Services talk over the bus and the graph, never by
        // compiling against each other — CONVENTIONS §0's three services, three subgraphs. If
        // this ever needs to be non-zero, that is a platform decision and belongs in a spec,
        // not in an import statement.
        assertThat(coupling)
                .as("a service compiled against another service is a distributed monolith, "
                        + "and the reactor will tell you so as a cycle sooner or later")
                .isEmpty();
    }

    /**
     * Frozen 2026-08-18. shared-library is complete; the rest are burned down per module.
     *
     * <p>R4 asks every top-level package to declare what it exposes, what it may depend on and
     * what is internal. That is documentation, so a ratchet rather than a gate: it stops the
     * ratio sliding while the remaining packages are written.
     */
    private static final Map<String, int[]> PACKAGE_INFO_COVERAGE = new LinkedHashMap<>(Map.of(
            "shared-library", new int[]{11, 11},
            "catalog-service", new int[]{9, 13},
            "booking-service", new int[]{11, 14},
            "identity-service", new int[]{11, 15},
            "api-gateway", new int[]{0, 5}));

    @Test
    @DisplayName("package-info coverage does not regress")
    void packageInfoCoverageDoesNotRegress() throws IOException {
        List<String> regressions = new ArrayList<>();
        List<String> improvements = new ArrayList<>();

        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                String name = module.getFileName().toString();
                int[] frozen = PACKAGE_INFO_COVERAGE.get(name);
                if (frozen == null) {
                    continue;
                }
                Path sourceRoot = module.resolve("src/main/java");
                long documented;
                try (Stream<Path> sources = Files.walk(sourceRoot)) {
                    documented = sources.filter(p -> p.getFileName().toString().equals("package-info.java"))
                            .count();
                }
                if (documented < frozen[0]) {
                    regressions.add("%s: %d package-info files, was %d".formatted(name, documented, frozen[0]));
                } else if (documented > frozen[0]) {
                    improvements.add("%s: up to %d from %d — raise the frozen count to lock the gain in"
                            .formatted(name, documented, frozen[0]));
                }
            }
        }

        assertThat(regressions)
                .as("a package-info was deleted — the boundary it declared is now undeclared")
                .isEmpty();
        assertThat(improvements)
                .as("packages were documented but the frozen count was not raised; "
                        + "a ratchet that is not tightened stops ratcheting")
                .isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    @FunctionalInterface
    private interface ImportVisitor {
        void visit(Path path, String owningDomain, String targetDomain, String importedSuffix);
    }

    private static void forEachSourceFile(ImportVisitor visitor) throws IOException {
        int scanned = 0;

        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path sourceRoot = module.resolve("src/main/java");
                if (!Files.isDirectory(sourceRoot)) {
                    continue;
                }
                try (Stream<Path> sources = Files.walk(sourceRoot)) {
                    for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                        scanned++;
                        Matcher owner = OWNING_DOMAIN.matcher(path.toString());
                        if (!owner.find()) {
                            continue;
                        }
                        String owningDomain = owner.group(1);
                        for (String line : Files.readString(path).split("\n", -1)) {
                            Matcher imported = PLATFORM_IMPORT.matcher(line);
                            if (imported.find()) {
                                visitor.visit(path, owningDomain, imported.group(1), imported.group(2));
                            }
                        }
                    }
                }
            }
        }

        assertThat(scanned)
                .as("an empty sweep is not a passing lint")
                .isGreaterThan(200);
    }
}
