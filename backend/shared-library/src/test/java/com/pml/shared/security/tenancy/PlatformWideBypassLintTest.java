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
 * <h2>The remaining budget</h2>
 * Five lines, none of them a tenant-filter bypass, and each is unavoidable without changing who is
 * allowed rather than how it is recorded:
 * <ul>
 *   <li>{@code OrganizerAccess.isPlatformStaff} — admin, finance or super admin gating a
 *       permission lookup; finance is not a platform-wide authority.</li>
 *   <li>{@code PayoutRequestMutationResolver.isPlatformStaff} — admin or finance acting for an
 *       organizer on a payout; same set, same reason.</li>
 *   <li>{@code ReservationQueryResolver.SUPPORT_AUTHORITIES} — admin, finance or super admin
 *       reading a buyer's reservation; finance is deliberately in the set.</li>
 *   <li>{@code DualControlRules} (two lines) — the role rank that decides who may approve a
 *       finance operation, which is separation of duties and not tenancy.</li>
 * </ul>
 * Routing any of these through {@code PlatformWideAccess.isPlatformWide} would drop finance from
 * the set and narrow who can do their job, so they stay and are counted instead.
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
            "booking-service", 5));

    /** Comments only. String literals are kept because the role names are the thing being counted. */
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static final Pattern BYPASS = Pattern.compile(
            "\\.platformAdmin\\(\\)"
                    + "|TenantScope\\.platformAdministrator\\("
                    + "|\"ROLE_ADMIN\""
                    + "|\"ROLE_SUPER_ADMIN\"");

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
