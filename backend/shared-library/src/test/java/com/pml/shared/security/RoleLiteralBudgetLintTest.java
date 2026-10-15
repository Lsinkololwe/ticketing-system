package com.pml.shared.security;

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
 * A service is secured by what a caller may do, not by what the caller is called. A
 * {@code hasRole('ADMIN')} check says who is allowed today and nothing about what the operation
 * is, so a second role that needs the same operation means editing every such check, and a role
 * that is renamed leaves the gates behind.
 *
 * <p>The checks that exist are being replaced with {@link RequiresPermission} or a tenant-scoped
 * permission check one resolver at a time. This test is the ratchet: no service may hold more
 * role-literal gates than its budget, a budget is lowered when a resolver is migrated, and a
 * method that already carries a catalogue permission may not also carry a role literal.</p>
 */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("Services are secured by permission; role-literal gates only go down")
class RoleLiteralBudgetLintTest {

    /** Role-literal {@code @PreAuthorize} gates still to migrate, per service. Lower it, never raise it. */
    private static final Map<String, Integer> BUDGET = new LinkedHashMap<>();

    static {
        BUDGET.put("identity-service", 0);
        BUDGET.put("catalog-service", 0);
        BUDGET.put("booking-service", 0);
        BUDGET.put("api-gateway", 0);
        BUDGET.put("shared-library", 0);
    }

    private static final Path BACKEND = locateBackend();
    private static final Pattern ROLE_GATE = Pattern.compile("@PreAuthorize\\(\\s*\"[^\"]*\\bhas(?:Any)?Role\\(");
    private static final Pattern PERMISSION_THEN_ROLE = Pattern.compile(
            "@RequiresPermission\\([^)]*\\)(?:\\s*@\\w+(?:\\([^)]*\\))?)*?\\s*@PreAuthorize\\(\\s*\"[^\"]*\\bhas(?:Any)?Role\\(");
    private static final Pattern ROLE_THEN_PERMISSION = Pattern.compile(
            "@PreAuthorize\\(\\s*\"[^\"]*\\bhas(?:Any)?Role\\([^\\n]*\\n(?:\\s*@\\w+(?:\\([^)]*\\))?\\s*\\n)*?\\s*@RequiresPermission\\(");

    @Test
    @DisplayName("No service holds more role-literal gates than its budget")
    void roleLiteralGatesOnlyGoDown() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("identity-service")), "backend sources not present");
        List<String> over = new ArrayList<>();
        for (Map.Entry<String, Integer> service : BUDGET.entrySet()) {
            int held = 0;
            for (Path file : sources(service.getKey())) {
                Matcher gate = ROLE_GATE.matcher(Files.readString(file));
                while (gate.find()) {
                    held++;
                }
            }
            if (held > service.getValue()) {
                over.add(service.getKey() + " holds " + held + " role-literal gates; its budget is "
                        + service.getValue() + ". Guard the operation with @RequiresPermission instead.");
            }
        }
        assertThat(over).isEmpty();
    }

    @Test
    @DisplayName("A method guarded by a catalogue permission carries no role literal beside it")
    void permissionAndRoleLiteralAreNotStacked() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("identity-service")), "backend sources not present");
        List<String> stacked = new ArrayList<>();
        for (String service : BUDGET.keySet()) {
            for (Path file : sources(service)) {
                String text = Files.readString(file);
                if (PERMISSION_THEN_ROLE.matcher(text).find() || ROLE_THEN_PERMISSION.matcher(text).find()) {
                    stacked.add(BACKEND.relativize(file).toString());
                }
            }
        }
        assertThat(stacked).as("a role literal stacked on @RequiresPermission is a second, narrower rule").isEmpty();
    }

    private static List<Path> sources(String service) throws IOException {
        Path root = BACKEND.resolve(service).resolve("src/main/java");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static Path locateBackend() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int up = 0; up < 4 && candidate != null; up++, candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("catalog-service/src/main/java"))) {
                return candidate;
            }
        }
        return Path.of("..");
    }
}
