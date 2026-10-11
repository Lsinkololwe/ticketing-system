package com.pml.catalog.security;

import com.pml.shared.testing.InternalSurface;
import com.pml.shared.testing.InternalSurface.Endpoint;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The internal-surface table in the security specification lists exactly the {@code /api/internal/**}
 * endpoints this service's controllers declare, so a path added or removed without the table
 * following fails here instead of drifting.
 *
 * <p>Both sides are reduced to {@code METHOD path} with every path variable written as {@code x},
 * the form {@link InternalSurface#discover} already produces.</p>
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("the specification's internal-surface table matches the catalog endpoints exactly")
class InternalSurfaceTableTest {

    private static final Path SPEC = Path.of("../../specs/_platform/007-security-and-authorization/spec.md");
    private static final String SERVICE = "catalog";

    /** A table row: the backticked {@code METHOD path} cell, then the service cell. */
    private static final Pattern ROW = Pattern.compile(
            "^\\|\\s*`(GET|POST|PUT|DELETE|PATCH) (/api/internal\\S*)`\\s*\\|\\s*([a-z-]+)\\s*\\|");

    @Test
    @DisplayName("every catalog internal endpoint is a table row and every catalog row is an endpoint")
    void tableMatchesControllers() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(SPEC), "specification not present");

        Set<String> documented = new TreeSet<>();
        for (String line : Files.readAllLines(SPEC)) {
            Matcher row = ROW.matcher(line);
            if (row.find() && SERVICE.equals(row.group(3))) {
                documented.add(row.group(1) + " " + row.group(2).replaceAll("\\{[^}/]+}", "x"));
            }
        }

        List<Endpoint> found = InternalSurface.discover(
                Path.of("src/main/java/com/pml/catalog/web"), Path.of("src/main/java"));
        Set<String> declared = new TreeSet<>();
        found.forEach(endpoint -> declared.add(endpoint.toString()));

        Set<String> missing = new TreeSet<>(declared);
        missing.removeAll(documented);
        Set<String> extra = new TreeSet<>(documented);
        extra.removeAll(declared);

        assertThat(declared).as("the controllers must expose an internal surface for this test to mean anything").isNotEmpty();
        assertThat(missing).as("declared by a controller but missing from the table").isEmpty();
        assertThat(extra).as("in the table but declared by no controller").isEmpty();
        assertThat(documented).isEqualTo(declared);
    }
}
