package com.pml.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The enum equals the error-code registry table, row for row.
 *
 * <h2>Equals, not contains</h2>
 * The distinction is the whole point.
 * "Contains" permits an enum with an extra code — a refusal the platform can
 * return that the registry does not describe, so no client knows to handle it and
 * no reviewer ever agreed to it. It equally permits a missing code, which is a
 * documented refusal nothing can raise. Both directions are asserted.
 *
 * <h2>The table is parsed, never copied</h2>
 * 93 rows retyped into a fixture would be a third description of the registry,
 * free to drift from both the table and the enum. Parsing means a code added to
 * the table fails this test until the enum catches up, which is the direction
 * the pressure should run: the registry moves first.
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R1 · the error registry is closed and matches §4")
class ErrorCodeRegistryTest {

    private static final Path SPEC =
            Path.of("../../specs/_platform/005-error-contract/spec.md");

    private static final String CLASSIFICATIONS =
            "BAD_REQUEST|UNAUTHENTICATED|PERMISSION_DENIED|NOT_FOUND"
                    + "|FAILED_PRECONDITION|UNAVAILABLE|INTERNAL|UNKNOWN";

    /** code → (classification, retryable), read from the registry table. */
    private static Map<String, RegistryRow> registryFromSpec() throws IOException {
        Matcher row = Pattern.compile(
                        "(?m)^\\|\\s*`([A-Z][A-Z_]+)`\\s*\\|\\s*`?([^|`]*)`?\\s*\\|"
                                + "\\s*`?(" + CLASSIFICATIONS + ")`?\\s*\\|"
                                + "\\s*\\*{0,2}(yes|no)\\*{0,2}\\s*\\|")
                .matcher(Files.readString(SPEC));

        Map<String, RegistryRow> registry = new LinkedHashMap<>();
        while (row.find()) {
            registry.put(row.group(1),
                    new RegistryRow(
                            ErrorClassification.valueOf(row.group(3)),
                            "yes".equals(row.group(4))));
        }
        return registry;
    }

    private record RegistryRow(ErrorClassification classification, boolean retryable) {
    }

    @Test
    @DisplayName("the enum holds exactly the codes §4 declares — no more, no fewer")
    void theEnumEqualsTheRegistry() throws IOException {
        Map<String, RegistryRow> registry = registryFromSpec();

        assertThat(registry)
                .as("no rows parsed from §4 — the table format changed, and every "
                        + "assertion below would pass vacuously")
                .hasSizeGreaterThan(50);

        TreeSet<String> declared = Arrays.stream(ErrorCode.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        TreeSet<String> specified = new TreeSet<>(registry.keySet());

        TreeSet<String> undocumented = new TreeSet<>(declared);
        undocumented.removeAll(specified);

        TreeSet<String> unimplemented = new TreeSet<>(specified);
        unimplemented.removeAll(declared);

        assertThat(undocumented)
                .as("codes the platform can return that §4 does not describe — no client "
                        + "knows to handle these and no reviewer agreed to them")
                .isEmpty();

        assertThat(unimplemented)
                .as("codes §4 documents that nothing can raise — a client branch that "
                        + "never fires")
                .isEmpty();
    }

    @Test
    @DisplayName("every code carries the classification and retryability §4 assigns it")
    void classificationAndRetryabilityMatchTheSpec() throws IOException {
        Map<String, RegistryRow> registry = registryFromSpec();

        var mismatches = Arrays.stream(ErrorCode.values())
                .filter(registry::containsKey)
                .filter(code -> {
                    RegistryRow expected = registry.get(code.name());
                    return expected.classification() != code.classification()
                            || expected.retryable() != code.retryable();
                })
                .map(code -> {
                    RegistryRow expected = registry.get(code.name());
                    return "%s — §4 says %s/retryable=%s, enum says %s/retryable=%s".formatted(
                            code.name(), expected.classification(), expected.retryable(),
                            code.classification(), code.retryable());
                })
                .toList();

        assertThat(mismatches)
                .as("""
                    retryable is the field a client reads to decide whether to offer a retry \
                    button. Disagreeing with §4 means the UI retries a declined payment, or \
                    treats a transient outage as permanent.""")
                .isEmpty();
    }

    @Test
    @DisplayName("INTERNAL_ERROR is the only catch-all, and it is retryable")
    void theCatchAllIsUsable() {
        // It is retryable deliberately: a defect is not the caller's fault
        // and the same request may well succeed once the defect is fixed or the
        // transient cause passes. A non-retryable internal error tells a user
        // their work is lost when it usually is not.
        assertThat(ErrorCode.INTERNAL_ERROR.classification()).isEqualTo(ErrorClassification.INTERNAL);
        assertThat(ErrorCode.INTERNAL_ERROR.retryable()).isTrue();
    }
}
