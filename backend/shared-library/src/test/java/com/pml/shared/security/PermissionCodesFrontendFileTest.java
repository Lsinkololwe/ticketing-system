package com.pml.shared.security;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frontend's permission codes are generated from {@link Permission}, never typed by hand: two
 * lists that mirror each other by discipline drift the first time someone adds a code to one. This
 * test renders the file and compares it with the committed copy, so a catalogue change that is not
 * followed by a regeneration fails the build. Regenerate with
 * {@code mvn -pl shared-library test -Dtest=PermissionCodesFrontendFileTest -Dpermission.codes.write=true}.
 */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("The frontend permission codes are generated from the catalogue and up to date")
class PermissionCodesFrontendFileTest {

    private static final String RELATIVE = "frontend/web/libs/shared/src/generated/permission-codes.ts";

    @Test
    @DisplayName("The committed TypeScript file equals what the catalogue renders")
    void committedFileIsCurrent() throws IOException {
        Path file = locateRepository().resolve(RELATIVE);
        Assumptions.assumeTrue(Files.isDirectory(file.getParent().getParent()), "frontend sources not present");
        String expected = render();

        if (Boolean.getBoolean("permission.codes.write")) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, expected);
        }

        assertThat(file).as("generate it with -Dpermission.codes.write=true").exists();
        assertThat(Files.readString(file)).as("%s is stale; regenerate it", RELATIVE).isEqualTo(expected);
    }

    static String render() {
        Permission[] sorted = Arrays.stream(Permission.values())
                .sorted(Comparator.comparing(Permission::code)).toArray(Permission[]::new);
        String codes = Arrays.stream(sorted).map(p -> "  '" + p.code() + "',").collect(Collectors.joining("\n"));
        String scopes = Arrays.stream(sorted)
                .map(p -> "  '" + p.code() + "': '" + p.scope().name() + "',").collect(Collectors.joining("\n"));
        String grants = Arrays.stream(PlatformRole.values()).map(role -> {
            String held = role.grants().stream().map(Permission::code).sorted()
                    .map(code -> "'" + code + "'").collect(Collectors.joining(", "));
            return "  " + role.name() + ": [" + held + "],";
        }).collect(Collectors.joining("\n"));
        String roleNames = Arrays.stream(PlatformRole.values()).map(role -> "'" + role.name() + "'")
                .collect(Collectors.joining(" | "));
        return """
                // Generated from com.pml.shared.security.Permission. Do not edit by hand.
                // Regenerate: mvn -pl shared-library test -Dtest=PermissionCodesFrontendFileTest -Dpermission.codes.write=true

                /** Every permission code the platform checks; a code that is not here grants nothing. */
                export const PERMISSION_CODES = [
                %s
                ] as const;

                export type PermissionCode = (typeof PERMISSION_CODES)[number];

                export type PermissionScope = 'PLATFORM' | 'ORGANIZATION' | 'EVENT';

                /** Where each permission is held: on the platform, in an organization, or on one event. */
                export const PERMISSION_SCOPE: Readonly<Record<PermissionCode, PermissionScope>> = {
                %s
                };

                export type PlatformRoleName = %s;

                /** What each platform role grants on its own, from PlatformRole.grants(); membership grants are not here. */
                export const PLATFORM_ROLE_GRANTS: Readonly<Record<PlatformRoleName, readonly PermissionCode[]>> = {
                %s
                };

                /** The union of what the named platform roles grant; unknown names grant nothing. */
                export function permissionsOfRoles(roles: readonly string[] | undefined): PermissionCode[] {
                  const held = new Set<PermissionCode>();
                  for (const raw of roles ?? []) {
                    const name = raw.replace(/^ROLE_/, '').toUpperCase() as PlatformRoleName;
                    (PLATFORM_ROLE_GRANTS[name] ?? []).forEach((code) => held.add(code));
                  }
                  return [...held].sort();
                }

                /** Narrows an arbitrary string, such as a code read from the server, to a known code. */
                export function isPermissionCode(value: string): value is PermissionCode {
                  return (PERMISSION_CODES as readonly string[]).includes(value);
                }
                """.formatted(codes, scopes, roleNames, grants);
    }

    private static Path locateRepository() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int up = 0; up < 5 && candidate != null; up++, candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("frontend")) && Files.isDirectory(candidate.resolve("backend"))) {
                return candidate;
            }
        }
        return Path.of("..", "..");
    }
}
