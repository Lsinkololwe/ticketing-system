package com.pml.identity.organization;

import org.junit.jupiter.api.Assumptions;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No authorization decision reads a Keycloak group.
 *
 * <h2>The rule this protects, and why it is a lint rather than a test</h2>
 * The Keycloak group tree is a <em>projection</em> of membership. MongoDB owns the truth, and the
 * mirror is one-directional precisely so that a group write may fail without the platform
 * being wrong — which is what lets every membership change commit before it mirrors.
 *
 * <p>That guarantee collapses the moment one decision consults a group. Groups drift by design:
 * between a change committing and the sweep repairing, Keycloak is stale, and a token minted in
 * that window carries the old groups for its whole lifetime. A demoted admin keeps admin access
 * until their token expires; a removed member keeps theirs. Groups are written from MongoDB and
 * never read as truth.
 *
 * <p>No runtime test can catch this, because reading a group works perfectly whenever the mirror
 * happens to be current, which is almost always. The failure surfaces as an intermittent
 * authorization anomaly nobody can reproduce.
 *
 * <h2>What counts as a decision</h2>
 * The permission and authorization services, and the resolvers' guards. Deliberately not the
 * mirror-writing code, which must name groups in order to write them, nor the scheduled repair
 * that corrects them.
 */
@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R8 · authorization never reads a Keycloak group")
class GroupMirrorLintTest {

    private static final Path SOURCES = Path.of("src/main/java/com/pml/identity");

    /**
     * Files that legitimately name groups: the mirror writes them and the scheduled repair corrects them.
     *
     * <p>An allowlist rather than a package rule, because the point is that the set of places
     * touching groups stays small enough to enumerate. A new file here is a decision somebody
     * makes deliberately.
     */
    private static final List<String> MAY_TOUCH_GROUPS = List.of(
            "KeycloakService.java",
            "GroupMirrorActivitiesImpl.java",
            "OwnershipMirrorActivitiesImpl.java",
            "OrganizationMemberServiceImpl.java",
            "OwnershipTransferServiceImpl.java",
            "UserSyncService.java");

    /** Reading a group, as opposed to writing one. */
    private static final Pattern READS_A_GROUP = Pattern.compile(
            "getGroups|groupsOf|listGroups|getUserGroups|\\bgroups\\(\\)|\"groups\"");

    private static final Pattern COMMENTS = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    @Test
    @DisplayName("ET-ORG-002-R8 · no decision code reads a group membership")
    void decisionsDoNotReadGroups() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SOURCES), "identity sources not present");

        List<String> problems = new ArrayList<>();
        int examined = 0;

        for (Path file : decisionSources()) {
            String name = file.getFileName().toString();
            if (MAY_TOUCH_GROUPS.contains(name)) {
                continue;
            }
            examined++;
            String source = COMMENTS.matcher(Files.readString(file)).replaceAll(match -> "");
            Matcher reads = READS_A_GROUP.matcher(source);
            if (reads.find()) {
                problems.add("""
                        %s reads a Keycloak group (`%s`). Groups are a projection of membership \
                        and drift by design — between a change committing and the sweep repairing \
                        it, Keycloak is stale, and a token minted in that window carries the old \
                        groups for its whole lifetime. Resolve from MongoDB."""
                        .formatted(name, reads.group()));
            }
        }

        // A rule that scans nothing passes forever.
        assertThat(examined)
                .as("no decision sources scanned — the walk is broken, not the platform")
                .isGreaterThan(20);
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-ORG-002-R8 · the set of files touching groups stays small enough to enumerate")
    void theAllowlistStaysHonest() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SOURCES), "identity sources not present");

        // The allowlist is only useful while it is shorter than the codebase. If half the service
        // needs to be exempted, groups have stopped being a projection and the guarantee is gone.
        List<String> stale = new ArrayList<>();
        List<String> names = decisionSources().stream()
                .map(file -> file.getFileName().toString())
                .toList();

        for (String allowed : MAY_TOUCH_GROUPS) {
            if (!names.contains(allowed)) {
                stale.add(allowed + " is allowlisted but no longer exists — drop it");
            }
        }
        assertThat(stale)
                .as("an allowlist entry for a file that is gone is an exemption nobody is using "
                        + "and nobody will notice granting again")
                .isEmpty();
        assertThat(MAY_TOUCH_GROUPS).hasSizeLessThan(12);
    }

    private static List<Path> decisionSources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
