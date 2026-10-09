package com.pml.catalog.security;

import org.junit.jupiter.api.Assumptions;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tenant filter and the permission check ship together, and neither can be dropped.
 * OWASP A01:2021.
 *
 * <h2>What is being protected against</h2>
 * Not malice — an optimisation. The filter is a database predicate and the check is a call to
 * another service, so at some point somebody profiling event mutations will notice that removing
 * the round trip makes them faster and that the filter "already checks ownership". It does not
 * check the same thing. This platform ships five organization roles: a MARKETER cannot edit
 * events, a CONTRIBUTOR is view-only, and event-level roles override the organization role for a
 * single event. A filter that only knows membership would hand all of them owner-level power over
 * everything the organization runs.
 *
 * <p>The reverse is the cross-tenant hole: a mutation that loads by bare id and forgets to ask
 * identity-service compiles, reads naturally, and passes every test that does not specifically
 * probe another tenant.
 *
 * <h2>Why this is a lint and not a type</h2>
 * {@code TenantGuard.locateAndPermit} takes both as required arguments, so neither can be
 * <em>omitted</em>. What no signature can stop is a caller passing
 * {@code event -> Mono.empty()} and calling it a permission check. That is what these assertions
 * are for: they read the source and require the real call to be present.
 *
 * <p>The companion is {@link EventWriteBothLocksTest}, which proves at runtime that each lock
 * refuses while the other is wide open. Between them: this one says the code is there, that one
 * says it works.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("D-20 · the filter and the permission check cannot be separated")
class EventWriteGuardLintTest {

    private static final Path SOURCES = Path.of("src/main/java/com/pml/catalog");
    private static final Path GUARD = SOURCES.resolve("security/EventWriteGuard.java");

    /** The permissions an event write may require; anything else would be weaker than the write deserves. */
    private static final List<String> EVENT_PERMISSIONS =
            List.of("EVENT_EDIT", "EVENT_PUBLISH", "EVENT_DELETE", "EVENT_CANCEL");

    /**
     * Comments and string literals, removed before any of the assertions below read the source.
     *
     * <p>Not tidiness — the first version of this lint stayed green through a mutation that
     * replaced {@code findByIdAndOrganizationIdIn} with a bare {@code findById}, because the
     * javadoc explaining the filter still contained the word. A lint that a comment can satisfy
     * is a lint that documents the fix rather than checking it, and this file is heavily
     * commented precisely because the reasoning matters.
     */
    private static final Pattern NOT_CODE = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*|\"(?:\\\\.|[^\"\\\\\\n])*\"",
            Pattern.DOTALL);

    private static String guard;

    @BeforeAll
    static void readGuard() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(GUARD), "EventWriteGuard not present");
        guard = NOT_CODE.matcher(Files.readString(GUARD)).replaceAll(match -> "");
    }

    @Test
    @DisplayName("ET-PLT-007 · the guard performs both the scoped lookup and the permission check")
    void bothLocksArePresent() {
        assertThat(guard)
                .as("the tenant filter: an event owned elsewhere must not come back from the query")
                .contains("findByIdAndOrganizationIdIn");
        assertThat(guard)
                .as("""
                    the permission check: D-10 resolves EVENT_EDIT through platform role, event \
                    grant, organization role, custom permission and explicit deny. Membership \
                    answers none of those.""")
                .contains("checkEventAccess");
        assertThat(guard)
                .as("both must be handed to one call, so that neither can be reached without the other")
                .contains("TenantGuard.locateAndPermit");
    }

    @Test
    @DisplayName("ET-PLT-007 · the permission check is a real call, not a lambda that returns empty")
    void thePermissionCheckIsNotAStub() {
        // locateAndPermit accepts any Function<T, Mono<Void>>. `event -> Mono.empty()` type-checks
        // and permits everything, and would be an entirely reasonable-looking placeholder during
        // a refactor. The method reference passed as the fourth argument has to lead somewhere
        // that calls identity-service.
        Matcher permits = Pattern.compile(
                        "private\\s+Mono<Void>\\s+permits\\s*\\([^)]*\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(guard);

        assertThat(permits.find())
                .as("EventWriteGuard.permits has moved or been renamed — re-point this lint")
                .isTrue();
        assertThat(permits.group(1))
                .as("the second lock must ask identity-service and must refuse when told no")
                .contains("checkEventAccess")
                .contains("isAuthorized")
                .contains("Mono.error");
    }

    @Test
    @DisplayName("ET-PLT-007 · no event mutation loads an event by unscoped id")
    void mutationsDoNotBypassTheGuard() throws IOException {
        // The whole point of routing seven mutations through one component: an eighth that
        // reaches past it is visible here rather than in an incident. `findVisibleById` is
        // permitted — it is the public-visibility read guard, and duplicateEvent legitimately reads an event
        // it is not writing to.
        List<String> problems = new ArrayList<>();
        for (Path file : mutationResolvers()) {
            String source = Files.readString(file);
            Matcher unguarded = Pattern.compile("\\beventService\\.findById\\(")
                    .matcher(NOT_CODE.matcher(source).replaceAll(match -> ""));
            if (unguarded.find()) {
                problems.add("""
                        %s calls eventService.findById. A mutation that loads an event by bare id \
                        has no tenant in the query and is F-001 again — use \
                        eventWriteGuard.forWrite(id, permission), which applies both locks, or \
                        eventService.findVisibleById for a read."""
                        .formatted(file.getFileName()));
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-007 · every forWrite call names an event-write permission")
    void everyCallSiteNamesAKnownPermission() throws IOException {
        // forWrite takes a Permission, so a misspelt name no longer compiles. What the compiler
        // cannot catch is a real permission that is weaker than the write — event:view on a
        // delete — so each call site must name one of the event-write permissions.
        List<String> problems = new ArrayList<>();
        int callSites = 0;
        for (Path file : mutationResolvers()) {
            Matcher call = Pattern.compile("forWrite\\(\\s*[\\w.()]+\\s*,\\s*Permission\\.(\\w+)\\)")
                    .matcher(Files.readString(file));
            while (call.find()) {
                callSites++;
                if (!EVENT_PERMISSIONS.contains(call.group(1))) {
                    problems.add("%s passes Permission.%s, which is not one of %s"
                            .formatted(file.getFileName(), call.group(1), EVENT_PERMISSIONS));
                }
            }
        }
        assertThat(callSites)
                .as("no forWrite call sites found — the conversion was reverted, or this lint "
                        + "is looking in the wrong place")
                .isGreaterThanOrEqualTo(7);
        assertThat(problems).isEmpty();
    }

    private static List<Path> mutationResolvers() throws IOException {
        Path root = SOURCES.resolve("web/graphql/mutation");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }
}
