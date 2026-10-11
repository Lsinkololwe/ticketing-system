package com.pml.shared.graphql.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The requirement names the platform's access decisions are allowed to use are the ones its
 * written registry lists, no more and no fewer.
 *
 * <h2>Why compare against the document</h2>
 * The {@code Role} enum, the schema, and the registry table each describe the same vocabulary.
 * Checking the schemas against the enum alone proves the code agrees with itself: add a value to
 * the enum, use it in a schema, and everything stays green while the document that reviewers
 * and operators read is silently out of date. The table is parsed and compared directly so a new
 * requirement name has to be written down before it can be used.
 */
@Tag("L4")
@Tag("ET-PLT-007")
@DisplayName("every @auth requirement and every Role value is a row of the operation-gate registry")
class OperationGateRegistryTest {

    /** Resolved from the module directory, which is the working directory a test runs in. */
    private static final Path SPEC =
            Path.of("../../specs/_platform/007-security-and-authorization/spec.md");

    private static final Map<String, Path> SCHEMAS = schemas();

    private static Map<String, Path> schemas() {
        Map<String, Path> schemas = new LinkedHashMap<>();
        for (String service : List.of("identity", "catalog", "booking")) {
            schemas.put(service, Path.of("../" + service + "-service/src/main/resources/graphql/schema.graphqls"));
        }
        return schemas;
    }

    /** First column of the registry table: a backticked upper-case name opening each row. */
    private static final Pattern REGISTRY_ROW = Pattern.compile("(?m)^\\|\\s*`([A-Z_]+)`\\s*\\|");

    /**
     * The value of {@code requires:} on an applied {@code @auth}. The word boundary is what keeps
     * the directive's own definition ({@code requires: Role = AUTHENTICATED}) from matching.
     */
    private static final Pattern AUTH_USE =
            Pattern.compile("@auth\\s*\\(\\s*requires\\s*:\\s*([A-Z_]+)\\b");

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            throw new AssertionError("expected to find " + file.toAbsolutePath().normalize()
                    + " relative to the module directory", unreadable);
        }
    }

    private static Set<String> registryRequirements() {
        String[] afterHeading = read(SPEC).split("### Operation-gate registry", 2);
        assertThat(afterHeading)
                .as("the spec has an 'Operation-gate registry' section")
                .hasSize(2);
        String section = afterHeading[1].split("(?m)^#{2,3} ", 2)[0];

        Set<String> names = new LinkedHashSet<>();
        Matcher row = REGISTRY_ROW.matcher(section);
        while (row.find()) {
            names.add(row.group(1));
        }
        return names;
    }

    /** Requirement names applied in a schema, with comment lines removed so prose is not read as use. */
    private static Set<String> requirementsUsedIn(Path schema) {
        String withoutComments = read(schema).replaceAll("(?m)#.*$", "");
        Set<String> used = new LinkedHashSet<>();
        Matcher use = AUTH_USE.matcher(withoutComments);
        while (use.find()) {
            used.add(use.group(1));
        }
        return used;
    }

    @Test
    @DisplayName("the registry table is found and lists requirement names")
    void registryIsParsed() {
        assertThat(registryRequirements())
                .as("a parse that finds no rows would let every later assertion pass vacuously")
                .isNotEmpty();
    }

    @Test
    @DisplayName("every @auth(requires: X) used in a service schema is a registry row")
    void everyUsedRequirementIsRegistered() {
        Set<String> registered = registryRequirements();

        Map<String, Set<String>> unregistered = new LinkedHashMap<>();
        SCHEMAS.forEach((service, schema) -> {
            Set<String> used = requirementsUsedIn(schema);
            assertThat(used)
                    .as("%s's schema applies @auth somewhere; none found means the scan is broken", service)
                    .isNotEmpty();
            Set<String> missing = new LinkedHashSet<>(used);
            missing.removeAll(registered);
            if (!missing.isEmpty()) {
                unregistered.put(service, missing);
            }
        });

        assertThat(unregistered)
                .as("requirement names used in a schema but absent from the operation-gate registry "
                        + "(registry rows: %s)", registered)
                .isEmpty();
    }

    @Test
    @DisplayName("every value of the shared Role enum is a registry row")
    void everyRoleValueIsRegistered() {
        Set<String> registered = registryRequirements();

        List<String> missing = new ArrayList<>();
        Arrays.stream(Role.values()).map(Enum::name).filter(name -> !registered.contains(name))
                .forEach(missing::add);

        assertThat(missing)
                .as("Role values with no row in the operation-gate registry (registry rows: %s)", registered)
                .isEmpty();
    }

    @Test
    @DisplayName("every registry row is a value the Role enum can express")
    void everyRegistryRowIsARole() {
        Set<String> roles = new LinkedHashSet<>();
        Arrays.stream(Role.values()).map(Enum::name).forEach(roles::add);

        Set<String> unexpressible = new LinkedHashSet<>(registryRequirements());
        unexpressible.removeAll(roles);

        assertThat(unexpressible)
                .as("registry rows that no @auth(requires: ...) could ever name")
                .isEmpty();
    }
}
