package com.pml.shared.security;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Permissions are named in code only through {@link Permission}. A permission spelled as a string
 * — a catalogue code typed by hand, or one of the old upper-case names — is a check that can drift
 * from the catalogue without failing to compile: identity refuses a name it does not know, so a
 * typo denies everyone, and an old name silently grants nothing.
 */
@Tag("L1")
@Tag("ET-ORG-003")
@DisplayName("Permissions are named through the catalogue enum, never as strings")
class PermissionNameLintTest {

    private static final Path BACKEND = locateBackend();
    private static final List<String> SERVICES = List.of("shared-library", "identity-service", "catalog-service", "booking-service");

    /** The one file that spells every code: the catalogue itself. */
    private static final String CATALOGUE = "shared/security/Permission.java";

    /** The one file that may spell the old names: the migration translating stored data. */
    private static final String LEGACY_TABLE = "migration/PermissionModelMigrationService.java";

    private static final Pattern CODE_LITERAL = Pattern.compile(
            "\"(event|attendee|ticket|analytics|promotion|financial|payout|bank|team|event_access|organization|platform|transaction|audit):[a-z_]+\"");

    private static final Set<String> LEGACY_NAMES = Set.of(
            "EVENT_VIEW", "EVENT_CREATE", "EVENT_EDIT", "EVENT_MANAGE_TICKETS", "EVENT_PUBLISH", "EVENT_DELETE",
            "TICKET_SCAN", "EVENT_MANAGE_CHECK_IN", "ATTENDEE_VIEW", "EVENT_VIEW_ATTENDEES", "ANALYTICS_VIEW",
            "EVENT_VIEW_ANALYTICS", "REFUND_ISSUE", "PROMOTION_MANAGE", "FINANCIAL_VIEW", "FIN_VIEW_REVENUE",
            "FIN_VIEW_TRANSACTIONS", "PAYOUT_REQUEST", "FIN_REQUEST_PAYOUT", "FIN_MANAGE_PAYOUT", "MEMBER_INVITE",
            "MEMBER_REMOVE", "MEMBER_ROLE_CHANGE", "MEMBER_EDIT_ROLE", "ORG_MANAGE_MEMBERS", "ORG_VIEW_MEMBERS",
            "EVENT_MANAGE_ACCESS", "ORG_VIEW", "ORG_EDIT", "ORG_SETTINGS", "ORG_MANAGE_SETTINGS", "ORG_DELETE",
            "OWNERSHIP_TRANSFER", "TRANSFER_OWNERSHIP", "NOTIFICATION_SEND");

    private static final Pattern STRING_LITERAL = Pattern.compile("\"([A-Z][A-Z_]+)\"");

    /** Comments removed before reading, so prose may mention a code; string literals are kept. */
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    @Test
    @DisplayName("No code outside the catalogue spells a permission code as a string")
    void codesAreNotSpelledByHand() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("identity-service")), "backend sources not present");
        List<String> problems = new ArrayList<>();
        for (Path file : sources()) {
            if (file.toString().endsWith(CATALOGUE)) {
                continue;
            }
            Matcher literal = CODE_LITERAL.matcher(code(file));
            while (literal.find()) {
                problems.add(BACKEND.relativize(file) + " spells " + literal.group() + "; use Permission." + constantFor(literal.group()));
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("No code outside the data migration uses an old upper-case permission name")
    void oldNamesAreGone() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("identity-service")), "backend sources not present");
        List<String> problems = new ArrayList<>();
        int scanned = 0;
        for (Path file : sources()) {
            scanned++;
            if (file.toString().endsWith(LEGACY_TABLE)) {
                continue;
            }
            Matcher literal = STRING_LITERAL.matcher(code(file));
            while (literal.find()) {
                if (LEGACY_NAMES.contains(literal.group(1))) {
                    problems.add(BACKEND.relativize(file) + " uses the old permission name \"" + literal.group(1) + "\"");
                }
            }
        }
        assertThat(scanned).as("no sources found — the lint is looking in the wrong place").isGreaterThan(100);
        assertThat(problems).isEmpty();
    }

    private static String constantFor(String quoted) {
        String code = quoted.substring(1, quoted.length() - 1);
        return Permission.fromCode(code).map(Enum::name).orElse("<not in the catalogue>");
    }

    private static String code(Path file) throws IOException {
        return COMMENT.matcher(Files.readString(file)).replaceAll("");
    }

    private static List<Path> sources() throws IOException {
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
