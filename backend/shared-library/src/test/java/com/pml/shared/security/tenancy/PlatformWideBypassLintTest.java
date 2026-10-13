package com.pml.shared.security.tenancy;

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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A new bare platform-administrator bypass outside {@link PlatformWideAccess} fails the build.
 * OWASP A01:2021 · A09:2021.
 *
 * <h2>What counts</h2>
 * A line of service code that either asks a scope whether it is platform-wide
 * ({@code .platformAdmin()}), builds a platform-wide scope by hand
 * ({@code TenantScope.platformAdministrator(}), or names an administrator role as a string
 * literal outside a {@code @PreAuthorize} annotation. Each is a place a reach across every
 * organization is decided without a record; {@code PlatformWideAccess} is the one place that
 * decides and records it.
 *
 * <h2>Finance is counted too</h2>
 * A finance officer is platform staff, but whether a service treats finance as tenant-wide is that
 * service's configuration (booking does, identity and catalog do not). A service that wrote its own
 * {@code "ROLE_FINANCE"} check decided the reach privately and left no record.
 * {@link StaffRole} and {@code PlatformWideAccess.isPlatformStaff} are the one place the staff
 * question is answered and recorded, so the literal is counted exactly as the administrator roles are.
 *
 * <h2>The remaining budget</h2>
 * None. The staff checks booking once kept for finance now go through
 * {@code PlatformWideAccess.isPlatformStaff} (a payout is no longer one of them: only a member of
 * the organization requests its payout, so that check was removed, not moved), and the approval
 * rank rule that was inline in {@code DualControlRules} is {@code StaffRole.covers}. Every budget
 * is zero, and the third test pins that the rank rule is still reached through the shared class
 * rather than rewritten.
 *
 * <h2>Staff reading an organizer's permission grants</h2>
 * A staff role that carries a permission through the platform role reads an organizer's data
 * without belonging to the organization. That check names no role literal, so the patterns above
 * cannot see it; the last test instead pins that booking reads the platform roles' grants in one
 * place only, and that this place asks through {@code isPlatformStaff} so each use is recorded.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-007 · platform-administrator bypasses outside PlatformWideAccess may only decrease")
class PlatformWideBypassLintTest {

    private static final Path BACKEND = locateBackend();

    private static Path locateBackend() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int up = 0; up < 4 && candidate != null; up++, candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("catalog-service/src/main/java"))) {
                return candidate;
            }
        }
        return Path.of("..");
    }

    /** Lines of bypass-shaped code allowed per service. They may fall; they may never rise. */
    private static final Map<String, Integer> BUDGET = new LinkedHashMap<>(Map.of(
            "catalog-service", 0,
            "identity-service", 0,
            "booking-service", 0));

    /** Comments only. String literals are kept because the role names are the thing being counted. */
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static final Pattern BYPASS = Pattern.compile(
            "\\.platformAdmin\\(\\)"
                    + "|TenantScope\\.platformAdministrator\\("
                    + "|\"ROLE_ADMIN\""
                    + "|\"ROLE_SUPER_ADMIN\""
                    + "|\"ROLE_FINANCE\"");

    private static final Pattern ANNOTATION = Pattern.compile("@(?:Pre|Post)Authorize\\(");

    @Test
    @DisplayName("ET-PLT-007 · every service is at its frozen budget of bare administrator bypasses")
    void bypassesOnlyDecrease() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("catalog-service")),
                "backend sources not present");

        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : BUDGET.entrySet()) {
            List<String> sites = bypassLines(entry.getKey());
            if (sites.size() > entry.getValue()) {
                problems.add("""
                        %s has %d lines that decide a platform-wide reach without PlatformWideAccess, \
                        over its budget of %d. Route the decision through PlatformWideAccess so it is \
                        recorded: %s"""
                        .formatted(entry.getKey(), sites.size(), entry.getValue(), sites));
            }
            if (sites.size() < entry.getValue()) {
                problems.add("%s is down to %d from a budget of %d — lower BUDGET so the ground cannot be given back"
                        .formatted(entry.getKey(), sites.size(), entry.getValue()));
            }
        }

        assertThat(problems)
                .as("OWASP A01:2021 — a platform-wide reach decided outside the audited path")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-007 · each service really reaches platform-wide through PlatformWideAccess")
    void servicesUseTheNamedPath() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("catalog-service")),
                "backend sources not present");

        List<String> problems = new ArrayList<>();
        for (String service : BUDGET.keySet()) {
            if (!usesNamedPath(service)) {
                problems.add(service + " no longer references PlatformWideAccess — its bypasses are unrecorded again");
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-007 · the approval rank rule is the shared one, not a copy with role names in it")
    void rankRuleIsShared() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("booking-service")), "backend sources not present");

        Path rules = BACKEND.resolve("booking-service/src/main/java/com/pml/booking/domain/DualControlRules.java");
        String code = COMMENTS.matcher(Files.readString(rules)).replaceAll("");

        assertThat(code)
                .as("DualControlRules must delegate the rank comparison to StaffRole so finance's place in it is decided once")
                .contains("StaffRole.covers(")
                .doesNotContain("ROLE_");
    }

    @Test
    @DisplayName("ET-PLT-007 · booking reads staff permission grants in one place, and that place records each use")
    void staffGrantsAreReadOnlyThroughTheAuditedHelper() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("booking-service")), "backend sources not present");

        Pattern grantsByRole = Pattern.compile("grantedByPlatformRoles\\(");
        List<String> readers = new ArrayList<>();
        String helper = null;
        for (Path file : mainSources("booking-service")) {
            String code = COMMENTS.matcher(Files.readString(file)).replaceAll("");
            if (grantsByRole.matcher(code).find()) {
                readers.add(file.getFileName().toString());
                helper = code;
            }
        }

        assertThat(readers)
                .as("a second reader of the platform roles' grants decides a staff reach with no record")
                .containsExactly("OrganizerAccess.java");
        assertThat(helper)
                .as("OrganizerAccess must ask through isPlatformStaff so the use is recorded")
                .contains("PlatformWideAccess.isPlatformStaff(");
    }

    private static List<String> bypassLines(String service) throws IOException {
        List<String> sites = new ArrayList<>();
        for (Path file : mainSources(service)) {
            String code = COMMENTS.matcher(Files.readString(file)).replaceAll("");
            int lineNumber = 0;
            for (String line : code.split("\\n")) {
                lineNumber++;
                if (BYPASS.matcher(line).find() && !ANNOTATION.matcher(line).find()) {
                    sites.add(file.getFileName() + ":" + lineNumber);
                }
            }
        }
        return sites;
    }

    private static boolean usesNamedPath(String service) throws IOException {
        for (Path file : mainSources(service)) {
            if (Files.readString(file).contains("PlatformWideAccess.")) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> mainSources(String service) throws IOException {
        Path root = BACKEND.resolve(service).resolve("src/main/java");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
