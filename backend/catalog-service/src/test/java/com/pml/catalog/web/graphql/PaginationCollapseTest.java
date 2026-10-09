package com.pml.catalog.web.graphql;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog's fourteen true duplicates are collapsed, and the twenty that are not
 * duplicates are still standing.
 *
 * <h2>Catalog is the subgraph where "collapse the twins" is not one rule</h2>
 * Booking and identity could be swept: every pair there offered the same data to the same
 * audience, so one of the two was pure duplication. Catalog has 34 pairs and only 14 of
 * them are like that. In the other 20 the offset half is {@code @auth(requires: ADMIN)}
 * and the cursor half is {@code PUBLIC} — {@code provinces}, {@code cities},
 * {@code eventsByCategory} and the rest are public discovery on one side and an admin
 * table on the other. Same name, two audiences, and collapsing either one deletes a
 * capability rather than removing duplication.
 *
 * <h2>The twenty are three different things</h2>
 * <ul>
 *   <li><b>Ten reference-data pairs.</b> {@code provinces}, {@code cities} and
 *       {@code categories} are <em>bounded PUBLIC lists</em> — at most 10, 200 and 15 rows.
 *       Zambia has ten provinces, so paging them only hides a list a client can simply ask
 *       for. The three bare lists exist; the twenty paged fields are removed.</li>
 *   <li><b>Three event-discovery pairs.</b> The public contract is the cursor form, e.g.
 *       {@code eventsByCategory(categoryId, first, after) PUBLIC EventConnection!}: the cursor
 *       half takes the bare name, the offset half is removed.</li>
 *   <li><b>Seven pairs with no public contract</b>, in either form. {@code discoverEvents(filter,
 *       pagination)} — the schema's own PRIMARY PUBLIC QUERY — already answers all fourteen
 *       fields as filters. Removed in favour of it.</li>
 * </ul>
 *
 * <p>The suffixed fields were first deprecated and are now deleted, along with every other
 * {@code @deprecated} element: the schema carries none, so a client never reads a field the
 * server is about to drop. The assertions below keep it that way.
 */
@Tag("L4")
@Tag("ET-PLT-004")
@DisplayName("D-19 · catalog's duplicates are collapsed and its audience-split pairs are not")
class PaginationCollapseTest {

    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");

    /** Same audience on both halves: one of the two was duplication. All are admin/organizer tables. */
    private static final List<String> COLLAPSED = List.of(
            "events", "eventsByStatus", "draftEvents", "cancelledEvents", "completedEvents",
            "approvedNotPublishedEvents", "pendingApprovalEvents", "overdueApprovalEvents",
            "approvalTimelines", "approvalTimelinesByOrganizer", "pendingApprovalTimelines",
            "overdueApprovalTimelines", "activeEscalations", "myEscalations");

    /**
     * Fields that had only one pagination form, so there was nothing to collapse — they
     * simply lose the suffix. `referenceData` is excluded: an unsuffixed `referenceData`
     * already exists as a bounded list for clients, so the paged admin variant cannot
     * take that name without a decision about which one survives.
     */
    private static final List<String> DE_SUFFIXED = List.of(
            "locations", "locationsByCity", "locationsByCountry", "locationsNearby",
            "searchLocations", "myDraftEvents", "myEvents");

    /** The public contract is the cursor form, so it takes the bare name. */
    private static final List<String> CURSOR_SURVIVES =
            List.of("searchEvents", "eventsByCategory", "eventsByCity");

    /** The bounded PUBLIC reference lists, returned whole. */
    private static final List<String> BOUNDED_LISTS = List.of("provinces", "cities", "categories");

    private static String sdl;

    @BeforeAll
    static void readSchema() throws IOException {
        sdl = Files.readString(SDL);
    }

    @Test
    @DisplayName("ET-PLT-004 · each collapsed operation exists once, under its bare name")
    void duplicatesAreCollapsed() {
        List<String> problems = new ArrayList<>();
        for (String operation : COLLAPSED) {
            if (rootField(operation) == null) {
                problems.add(operation + " did not survive under its bare name");
            }
            for (String suffix : List.of("OffsetPagination", "CursorPagination")) {
                if (rootField(operation + suffix) != null) {
                    problems.add(operation + suffix + " still exists — the collapse is incomplete");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-004 · the collapsed survivors are the admin tables, so they page by number")
    void survivorsAreOffsetPages() {
        List<String> problems = new ArrayList<>();
        for (String operation : COLLAPSED) {
            String declaration = rootField(operation);
            if (declaration != null && !declaration.contains("OffsetPage")) {
                problems.add("%s should page by number: %s".formatted(operation, declaration.strip()));
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-004 · §4's three bounded reference lists exist, unpaged")
    void boundedListsExist() {
        // Ten provinces need a way to just ask for them, not six ways to page them. Nothing else
        // catches a missing bare list: pagination over ten rows is never observably wrong, so a
        // paginated variant is easily mistaken for this operation's implementation.
        List<String> missing = new ArrayList<>();
        for (String operation : BOUNDED_LISTS) {
            String declaration = rootField(operation);
            if (declaration == null) {
                missing.add(operation + " is not declared");
            } else if (declaration.contains("Page!") || declaration.contains("Connection!")) {
                missing.add("%s is paginated: %s".formatted(operation, declaration.strip()));
            }
        }
        assertThat(missing)
                .as("ET-CAT-003 §4 declares these bounded — `bounded 10`, `bounded <= 200`, "
                        + "`bounded 15` — and a bounded list takes no page argument")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-004 · where §4 names the cursor form, it holds the bare name")
    void cursorHalvesTookTheBareName() {
        List<String> problems = new ArrayList<>();
        for (String operation : CURSOR_SURVIVES) {
            String bare = rootField(operation);
            if (bare == null) {
                problems.add(operation + " did not survive under its bare name");
            } else if (!bare.contains("Connection!")) {
                problems.add("%s should be the cursor feed §4 declares: %s"
                        .formatted(operation, bare.strip()));
            }
            if (rootField(operation + "CursorPagination") != null) {
                problems.add(operation + "CursorPagination still carries the suffix");
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-004 · no field keeps a pagination suffix: the collapsed forms are gone, not deprecated")
    void noPaginationSuffixSurvives() {
        List<String> problems = new ArrayList<>();
        for (String declaration : allRootFields()) {
            String name = declaration.strip().split("[(:]")[0];
            if (name.endsWith("OffsetPagination") || name.endsWith("CursorPagination")) {
                problems.add(name + " still carries a pagination suffix");
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("the schema deprecates nothing: an element is either served or removed")
    void nothingIsDeprecated() {
        // A deprecated field is one the server intends to drop while clients still read it, and it
        // shows up in every schema browser as an API nobody should call. Remove it instead.
        List<String> deprecated = sdl.lines()
                .filter(line -> !line.trim().startsWith("#"))
                .filter(line -> line.contains("@deprecated"))
                .map(String::strip)
                .toList();
        assertThat(deprecated).as("@deprecated elements in the catalog schema").isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-004 · no Connection, Edge or OffsetPage type is left with nothing referencing it")
    void noOrphanedTypes() {
        List<String> orphans = new ArrayList<>();
        Matcher declared = Pattern.compile("(?m)^type\\s+(\\w+(?:Connection|Edge|OffsetPage))\\b").matcher(sdl);
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


    @Test
    @DisplayName("ET-PLT-004 · lone paginated fields lost their suffix")
    void loneVariantsAreDeSuffixed() {
        List<String> problems = new ArrayList<>();
        for (String operation : DE_SUFFIXED) {
            if (rootField(operation) == null) {
                problems.add(operation + " is missing under its bare name");
            }
            for (String suffix : List.of("OffsetPagination", "CursorPagination")) {
                if (rootField(operation + suffix) != null) {
                    problems.add(operation + suffix + " still carries the suffix");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-004 · the reference-data collision was resolved by adopting §4's name")
    void referenceDataCollisionResolved() {
        // The paged admin query cannot share the name `referenceData`, because that is the
        // public active-only dropdown query. They are two operations, not two spellings of one,
        // so the admin one is `referenceDataAll`.
        assertThat(rootField("referenceData"))
                .as("the public dropdown query must survive")
                .isNotNull();
        assertThat(rootField("referenceDataAll"))
                .as("§4's name for the admin table — every row of a type, including inactive")
                .isNotNull();
        assertThat(rootField("referenceDataOffsetPagination"))
                .as("the suffixed name should be gone, not deprecated: it was renamed, "
                        + "and the rename is the whole point")
                .isNull();
    }

    /** Every field declaration on {@code Query} or {@code Mutation}. */
    private static List<String> allRootFields() {
        List<String> declarations = new ArrayList<>();
        for (String block : rootBlocks()) {
            Matcher m = Pattern.compile("(?m)^[ \\t]{2,}\\w+[ \\t]*[(:].*$").matcher(block);
            while (m.find()) {
                declarations.add(m.group());
            }
        }
        return declarations;
    }

    /** A field on {@code Query} or {@code Mutation}, and nowhere else — several of these names are also object fields. */
    private static String rootField(String name) {
        for (String block : rootBlocks()) {
            Matcher m = Pattern.compile("(?m)^[ \\t]{2,}" + Pattern.quote(name) + "[ \\t]*[(:].*$")
                    .matcher(block);
            if (m.find()) {
                return m.group();
            }
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
