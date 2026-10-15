package com.pml.shared.security;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A permission nobody checks looks like protection and is not: it appears in the matrix, gets
 * granted, and stops nothing. Every catalogue permission therefore has a use site outside the
 * tables that grant it, or is on the register below with the reason it is still gated by a role.
 * The register is a list of debts, not a place to park: an entry that gains a use site fails the
 * test until it is removed.
 */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("Every catalogue permission is enforced somewhere, or is registered with the reason it is not")
class PermissionEnforcementTest {

    private static final Path BACKEND = locateBackend();
    private static final List<String> SERVICES = List.of(
            "shared-library", "identity-service", "catalog-service", "booking-service");

    /** Files that grant permissions to roles and so mention every constant without checking one. */
    private static final List<String> GRANTING_TABLES = List.of(
            "shared/security/Permission.java", "shared/security/PlatformRole.java",
            "valueobject/OrganizationRole.java", "valueobject/EventRole.java",
            "migration/PermissionModelMigrationService.java");

    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    /** Permissions with no use site yet, and why. Shrinks only; empty since every permission is enforced. */
    private static final Map<Permission, String> ROLE_GATED = new EnumMap<>(Permission.class);

    /** A permission code named in an expression such as {@code @permissionGuards.holds(authentication, 'x:y')}. */
    private static final Pattern GUARD_CODE = Pattern.compile("@permissionGuards\\.\\w+\\([^\"]*'([a-z_]+:[a-z_]+)'");

    @Test
    @DisplayName("Each permission has a use site outside the tables that grant it, or a registered reason")
    void everyPermissionIsEnforced() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("identity-service")), "backend sources not present");
        List<String> sources = new ArrayList<>();
        for (Path file : mainSources()) {
            String path = file.toString().replace('\\', '/');
            if (GRANTING_TABLES.stream().noneMatch(path::endsWith)) {
                sources.add(COMMENT.matcher(Files.readString(file)).replaceAll(""));
            }
        }
        assertThat(sources.size()).as("no sources found — the lint is looking in the wrong place").isGreaterThan(100);

        java.util.Set<String> guardedByCode = new java.util.HashSet<>();
        for (String source : sources) {
            Matcher guard = GUARD_CODE.matcher(source);
            while (guard.find()) {
                guardedByCode.add(guard.group(1));
            }
        }

        List<String> unenforced = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (Permission permission : Permission.values()) {
            Pattern use = Pattern.compile("Permission\\." + permission.name() + "\\b");
            boolean used = guardedByCode.contains(permission.code())
                    || sources.stream().anyMatch(source -> use.matcher(source).find());
            if (!used && !ROLE_GATED.containsKey(permission)) {
                unenforced.add(permission.code());
            }
            if (used && ROLE_GATED.containsKey(permission)) {
                stale.add(permission.code());
            }
        }
        assertThat(unenforced).as("permissions granted to roles and checked nowhere").isEmpty();
        assertThat(stale).as("registered permissions that now have a use site: remove them from the register").isEmpty();
    }

    private static List<Path> mainSources() throws IOException {
        List<Path> all = new ArrayList<>();
        for (String service : SERVICES) {
            Path root = BACKEND.resolve(service).resolve("src/main/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(all::add);
            }
        }
        return all;
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
