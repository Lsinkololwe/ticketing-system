package com.pml.identity.web.graphql;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each of identity's paged operations has exactly one pagination form, and it is the form that
 * operation's consumers need.
 *
 * <h2>Why the survivor is not always the offset form</h2>
 * Booking's paged operations are all admin and organizer tables, so offset fits every one and
 * "keep the offset one" would pass for a rule. Identity is where that rule breaks:
 * {@code myNotifications} is {@code @tag(name: "mobile")} and returns
 * {@code NotificationConnection!} with {@code (first, after)} — a customer feed, which is
 * the cursor case. Collapsing it to offset would satisfy a tidy-looking convention
 * and give the mobile app a page-numbered notification list.
 *
 * <p>So the survivor is chosen per operation, and this asserts it per operation rather than
 * asserting a shape across the board.
 */
@Tag("L4")
@Tag("ET-PLT-004")
@DisplayName("D-19 · identity's twins are collapsed to the shape §4 declares")
class PaginationCollapseTest {

    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");

    /** operation → the pagination form its return type takes. */
    private static final Map<String, String> SURVIVOR = Map.of(
            "eventAccessGrants", "OffsetPage",          // EventAccessGrantPage
            "organizationMembers", "OffsetPage",        // OrganizationMemberPage
            "organizations", "OffsetPage",              // OrganizationPage
            "users", "OffsetPage",                      // UserPage
            "pendingInvitations", "OffsetPage",         // an organizer table
            "organizationApplications", "OffsetPage",   // an admin queue
            "myNotifications", "Connection");           // NotificationConnection

    private static String sdl;

    @BeforeAll
    static void readSchema() throws IOException {
        sdl = Files.readString(SDL);
    }

    @Test
    @DisplayName("ET-PLT-004 · each operation exists once, under its bare name, with neither suffix")
    void twinsAreGone() {
        List<String> problems = new ArrayList<>();
        SURVIVOR.keySet().forEach(operation -> {
            if (rootField(operation) == null) {
                problems.add(operation + " did not survive the collapse under its bare name");
            }
            for (String suffix : List.of("OffsetPagination", "CursorPagination")) {
                if (rootField(operation + suffix) != null) {
                    problems.add(operation + suffix + " still exists — the collapse is incomplete");
                }
            }
        });
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-004 · the surviving shape is the one §4 declares, not a house style")
    void survivorMatchesTheSpec() {
        List<String> problems = new ArrayList<>();
        SURVIVOR.forEach((operation, expected) -> {
            String declaration = rootField(operation);
            if (declaration == null) {
                problems.add(operation + " is missing entirely");
                return;
            }
            if (!declaration.contains(expected)) {
                problems.add("%s should return a %s per §4, but declares: %s"
                        .formatted(operation, expected, declaration.strip()));
            }
        });
        assertThat(problems)
                .as("""
                    D-19 says §4 names the survivor. myNotifications is the operation that \
                    proves the rule is not "always keep offset" — it is a mobile feed, and \
                    ET-NTF-001 asks for a cursor connection.""")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-004 · no Connection or Edge type is left with nothing referencing it")
    void noOrphanedTypes() {
        // Deleting six cursor fields orphaned six Connection types and their Edges. A type
        // nothing points at still composes and still generates a TypeScript interface, so
        // it reads to the next person as a shape the server supports.
        List<String> orphans = new ArrayList<>();
        Matcher declared = Pattern.compile("(?m)^type\\s+(\\w+(?:Connection|Edge))\\b").matcher(sdl);
        while (declared.find()) {
            String type = declared.group(1);
            long elsewhere = sdl.lines()
                    .filter(line -> line.contains(type))
                    .filter(line -> !line.trim().startsWith("type " + type))
                    .count();
            if (elsewhere == 0) {
                orphans.add(type);
            }
        }
        assertThat(orphans).as("defined but referenced by nothing — dead schema").isEmpty();
    }

    /**
     * A field on {@code Query} or {@code Mutation}, and nowhere else.
     *
     * <p>Scoped to the root blocks because several of these names are also fields on other
     * types — {@code pendingInvitations} is an {@code Int!} on a dashboard summary and a
     * {@code [TeamInvitation!]} on an organization. A document-wide search finds one of
     * those and reports on the wrong declaration.
     *
     * <p>Identity declares fields across several lines, so this returns the whole
     * declaration through its return type rather than a single line.
     */
    private static String rootField(String name) {
        for (String block : rootBlocks()) {
            Matcher m = Pattern.compile("(?m)^[ \\t]{2,}" + Pattern.quote(name) + "[ \\t]*[(:]")
                    .matcher(block);
            if (!m.find()) {
                continue;
            }
            int i = m.end() - 1;
            if (block.charAt(i) == '(') {
                int depth = 0;
                while (i < block.length()) {
                    if (block.charAt(i) == '(') {
                        depth++;
                    } else if (block.charAt(i) == ')') {
                        depth--;
                        if (depth == 0) {
                            break;
                        }
                    }
                    i++;
                }
            }
            int end = block.indexOf('\n', i);
            return block.substring(m.start(), end < 0 ? block.length() : end);
        }
        return null;
    }

    private static List<String> rootBlocks() {
        List<String> blocks = new ArrayList<>();
        Matcher header = Pattern.compile("(?m)^(?:extend\\s+)?type\\s+(Query|Mutation)\\b[^{]*\\{")
                .matcher(sdl);
        while (header.find()) {
            int depth = 1;
            int i = header.end();
            while (i < sdl.length() && depth > 0) {
                char c = sdl.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
                i++;
            }
            blocks.add(sdl.substring(header.end(), i));
        }
        return blocks;
    }
}
