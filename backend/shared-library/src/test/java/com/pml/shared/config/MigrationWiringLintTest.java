package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every migration service is actually invoked by its service's runner.
 *
 * <h2>The failure this closes</h2>
 * {@code MigrationRunner}'s javadoc records what happened without it: seven of booking's eight
 * migrations were {@code @Service} beans that nothing called, identity's organization backfill
 * was another, catalog's reference-data bootstrapper a third. Each compiled. Each had a passing
 * integration test, because the test invoked the migration directly. None had ever run against a
 * database it did not create itself.
 *
 * <p>That is a particularly quiet failure: the tests are honest — they prove the migration works
 * <em>when run</em> — and nothing was testing that it ever would be. A migration nobody calls is
 * indistinguishable from a migration with nothing to do, because both report zero.</p>
 *
 * <h2>Source scanning, deliberately</h2>
 * The property is "this bean is referenced from that method", which is a fact about the code
 * rather than about a running context. Asserting it by starting three application contexts would
 * need a database, a broker and a Keycloak to prove something the source states plainly.
 */
@Tag("L1")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002 · no migration service is left uninvoked")
class MigrationWiringLintTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    @Test
    @DisplayName("every *MigrationService is registered in its module's runner")
    void everyMigrationIsRegistered() throws IOException {
        List<String> orphans = new ArrayList<>();
        int servicesFound = 0;

        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path migrationPackage = findMigrationPackage(module);
                if (migrationPackage == null) {
                    continue;
                }

                String runners = readRunners(migrationPackage);
                if (runners.isEmpty()) {
                    orphans.add(module.getFileName() + " has migration services and no runner");
                    continue;
                }

                try (Stream<Path> sources = Files.list(migrationPackage)) {
                    for (Path service : sources.filter(p -> p.getFileName().toString()
                            .endsWith("MigrationService.java")).toList()) {
                        servicesFound++;
                        String className = service.getFileName().toString().replace(".java", "");
                        // The runner injects it by type and invokes it by method reference, so
                        // the type name appearing there is what "wired" means.
                        if (!runners.contains(className)) {
                            orphans.add(module.getFileName() + " → " + className
                                    + " is never invoked by a runner");
                        }
                    }
                }
            }
        }

        assertThat(servicesFound)
                .as("no migration services found — an empty sweep is not a passing lint")
                .isGreaterThan(10);

        assertThat(orphans)
                .as("""
                    A migration that nothing calls reports the same clean zero as a migration \
                    with nothing to do, so the database silently stays on the old shape while \
                    the build stays green.""")
                .isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    private static Path findMigrationPackage(Path module) throws IOException {
        Path sourceRoot = module.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            return null;
        }
        try (Stream<Path> tree = Files.walk(sourceRoot)) {
            return tree.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals("migration"))
                    .findFirst()
                    .orElse(null);
        }
    }

    /** Every runner in the package, concatenated — a service may be invoked by any of them. */
    private static String readRunners(Path migrationPackage) throws IOException {
        StringBuilder runners = new StringBuilder();
        try (Stream<Path> sources = Files.list(migrationPackage)) {
            for (Path path : sources.filter(p -> p.getFileName().toString().endsWith("Runner.java")).toList()) {
                runners.append(Files.readString(path));
            }
        }
        return runners.toString();
    }
}
