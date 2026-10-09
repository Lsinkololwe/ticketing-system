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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The count of id lookups with no tenant filter, frozen so it can only fall.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>Why a ratchet and not a ban</h2>
 * Unscoped id lookups span roughly 37 entry points across three services. Banning the pattern
 * outright today would fail the build on 104 pre-existing call sites, and a lint
 * that cannot go green is a lint someone deletes by the afternoon. Freezing the
 * number instead makes the debt visible, stops it growing, and turns every
 * conversion into a required edit here — which is the only way a number this size
 * comes down at all.
 *
 * <p>The budgets fall as call sites are converted. They may never rise: each new unscoped
 * lookup is another place one tenant can read another's rows.
 *
 * <h2>What is actually counted</h2>
 * Not a proxy. A call site counts when <em>all three</em> hold, which is what makes
 * it a genuine measure of the vulnerability rather than of {@code findById} usage:
 *
 * <ol>
 *   <li>the model has an {@code organizationId} field — it is tenant-owned;</li>
 *   <li>the repository is typed on that model;</li>
 *   <li>a field of that repository type has {@code .findById(} called on it.</li>
 * </ol>
 *
 * A load by primary key on a tenant-owned document, with the tenant nowhere in the
 * query. Whether the caller compares ownership afterwards is exactly the thing that
 * cannot be checked from here, and exactly the thing people forget — which is why
 * {@code TenantGuard.locate} takes the scoped lookup as an argument instead.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("F-001 · unscoped lookups on tenant-owned documents may only decrease")
class TenantBoundaryLintTest {

    /**
     * Surefire runs with the module directory as its working directory, so {@code backend}
     * is one level up. Resolved by walking rather than hard-coding, because getting this
     * wrong does not fail — it makes every assumption below decline, and two skipped tests
     * read as two passing ones in the summary.
     */
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

    /**
     * Frozen 2026-09-01, the day the mechanism landed. Lower these as call sites convert.
     *
     * <p>catalog is 25 rather than 20 because {@code TenantGuard} needs an unscoped
     * lookup itself — for a platform administrator, and for the existence probe that
     * decides whether a refusal was a cross-tenant reach worth logging. Each guard
     * built therefore costs one, which is the one direction this number is allowed to
     * move for a good reason: {@code EventServiceImpl.findVisibleById} and
     * {@code EventWriteGuard} each hold one on the event read and write paths; identity holds
     * one for the group-mirror sweep's own marking write ({@code markMirrorPending} reloads the
     * membership it is flagging, and there is no tenant to filter by: the caller is a
     * scheduler, not a person); and booking holds one for the ORGANIZER-scoped
     * {@code escrowTransactions} — the clearest case of the rule, since the probe is the price
     * of not publishing every organization's money history.
     *
     * <h2>What this number does not measure, learned the hard way</h2>
     * Converting all seven event mutations to {@code EventWriteGuard} moved this number
     * <em>up</em> by one and not down by seven, because the mutations never called the
     * repository — they called {@code eventService.findById}, one layer above what this
     * census counts. The number is a proxy for a boundary and it measures the layer where
     * the query is written, not the layer where the caller's id arrives. {@code
     * EventWriteGuardLintTest} measures the entry points, which is the half that moves when
     * a mutation is converted. Raising it is only ever justified by a new call to
     * {@code TenantGuard.locate} on the same commit, and {@link #GUARDED_PATHS} is what
     * holds that claim to account afterwards.
     */
    private static final Map<String, Integer> BUDGET = new LinkedHashMap<>(Map.of(
            "catalog-service", 21, // +2: scheduleEventPublish/clearPublishSchedule, post-guard and system-actor (publish workflow) paths
            "identity-service", 20,
            "booking-service", 50));

    /**
     * The paths known to reach across tenants, and the helper each must route through.
     *
     * <p>A count alone would go green if someone deleted the call sites rather than
     * scoping them. This asserts the fix is present, not merely that the smell is gone.
     *
     * <p>The third entry is a read rather than a write, and belongs here for the same
     * reason as the other two: {@code event(id)} is the schema's PUBLIC single-event
     * query, and it returned drafts, rejected events and soft-deleted ones to anyone
     * holding an id. Nothing about that is visible in a diff that swaps
     * {@code findVisibleById} back to {@code findById} — the code still compiles, the
     * schema is unchanged, and the query still answers.
     */
    private static final Map<String, List<String>> GUARDED_PATHS = new LinkedHashMap<>(Map.of(
            "catalog-service/src/main/java/com/pml/catalog/service/impl/TicketTierServiceImpl.java",
            List.of("tierVisibleToCaller", "eventOwnedByCaller"),
            "catalog-service/src/main/java/com/pml/catalog/service/impl/EventAccessibilityServiceImpl.java",
            List.of("eventVisibleToCaller"),
            "catalog-service/src/main/java/com/pml/catalog/service/impl/EventServiceImpl.java",
            List.of("findVisibleById"),
            "catalog-service/src/main/java/com/pml/catalog/service/impl/EventLifecycleServiceImpl.java",
            List.of("eventForCaller"),
            "booking-service/src/main/java/com/pml/booking/security/TenantReads.java",
            List.of("ticketForCaller", "ticketByNumberForCaller", "promoCodeForCaller", "promoCodeByCodeForCaller",
                    "bankAccountForCaller", "payoutRequestForCaller", "payoutRequestByRequestIdForCaller"),
            "identity-service/src/main/java/com/pml/identity/security/IdentityTenantReads.java",
            List.of("grantForCaller", "memberForCaller", "invitationForCaller", "documentForCaller")));

    private static final Pattern TENANT_OWNED =
            Pattern.compile("\\bprivate\\s+String\\s+organizationId\\s*;");
    private static final Pattern REPOSITORY =
            Pattern.compile("interface\\s+(\\w+)\\s+extends\\s+Reactive\\w*MongoRepository<\\s*(\\w+)\\s*,");
    private static final Pattern FIELD =
            Pattern.compile("(?:private|protected|final)\\s+(?:final\\s+)?(\\w+)\\s+(\\w+)\\s*;");
    private static final Pattern FIND_BY_ID = Pattern.compile("\\b(\\w+)\\.findById\\(");

    /**
     * Comments and string literals, removed before counting.
     *
     * <p>Not fastidiousness. Documenting the fix means writing the vulnerable call in
     * prose — {@code eventRepository.findById(id)} appears in this very sentence — and a
     * census that counts those charges a service for explaining itself, which is the
     * incentive least worth creating in a security lint. It also lets the number drift
     * upward on a pure documentation edit, and a ratchet that moves for reasons nobody
     * can reconstruct is one that gets raised rather than investigated.
     */
    private static final Pattern NOT_CODE = Pattern.compile(
            // Reluctant, not the (?:[^*]|\*(?!/))* form that reads more precisely: that
            // one backtracks catastrophically and overflowed the stack on the first file
            // over a few hundred lines. A lint has to survive the corpus it scans.
            "/\\*.*?\\*/"
                    + "|//[^\\n]*"
                    + "|\"(?:\\\\.|[^\"\\\\\\n])*\""
                    + "|'(?:\\\\.|[^'\\\\])'",
            Pattern.DOTALL);

    @Test
    @DisplayName("ET-PLT-007 · every service is at or under its frozen budget")
    void unscopedLookupsOnlyDecrease() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("catalog-service")),
                "backend sources not present");

        List<String> problems = new ArrayList<>();

        BUDGET.forEach((service, budget) -> {
            int actual;
            try {
                actual = countUnscopedLookups(service);
            } catch (IOException unreadable) {
                problems.add(service + " could not be scanned: " + unreadable.getMessage());
                return;
            }
            if (actual == 0 && budget > 0) {
                problems.add("""
                        %s scanned to zero unscoped lookups against a budget of %d. That is not \
                        progress, it is a broken census — the model, repository or field-type \
                        patterns stopped matching. A lint that finds nothing must fail, or it \
                        reads as compliance forever."""
                        .formatted(service, budget));
                return;
            }
            if (actual > budget) {
                problems.add("""
                        %s has %d unscoped lookups on tenant-owned documents, over its budget of %d. \
                        A load by id with no tenant in the query is F-001. Scope it through \
                        TenantGuard.locate with a findByIdAndOrganizationIdIn finder."""
                        .formatted(service, actual, budget));
            }
            if (actual < budget) {
                problems.add("""
                        %s is down to %d from a budget of %d — lower BUDGET to %d so the \
                        ground gained cannot be given back."""
                        .formatted(service, actual, budget, actual));
            }
        });

        assertThat(problems)
                .as("OWASP A01:2021 — an authorization decision made without the tenant in the query")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-007 · the paths F-001 and F-007 named still route through a tenant guard")
    void namedPathsStayGuarded() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("catalog-service")),
                "backend sources not present");

        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : GUARDED_PATHS.entrySet()) {
            Path file = BACKEND.resolve(entry.getKey());
            if (!Files.isRegularFile(file)) {
                problems.add(entry.getKey() + " has moved or been deleted — re-point this lint");
                continue;
            }
            String source = Files.readString(file);
            for (String method : entry.getValue()) {
                if (!source.contains(method)) {
                    problems.add("%s no longer calls %s: the path is unguarded again"
                            .formatted(entry.getKey(), method));
                }
            }
            if (!source.contains("TenantGuard.locate")) {
                problems.add(entry.getKey() + " no longer reaches TenantGuard.locate");
            }
        }

        assertThat(problems).isEmpty();
    }

    // ── the census ──────────────────────────────────────────────────────────

    /** Source with comments and string literals blanked out, so only real calls are counted. */
    private static String code(String source) {
        return NOT_CODE.matcher(source).replaceAll(match -> "");
    }

    private static int countUnscopedLookups(String service) throws IOException {
        Path root = BACKEND.resolve(service).resolve("src/main/java");
        if (!Files.isDirectory(root)) {
            return 0;
        }

        Map<Path, String> sources = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                sources.put(file, code(Files.readString(file)));
            }
        }

        Set<String> tenantOwnedModels = new LinkedHashSet<>();
        for (Map.Entry<Path, String> source : sources.entrySet()) {
            if (TENANT_OWNED.matcher(source.getValue()).find()) {
                String name = source.getKey().getFileName().toString();
                tenantOwnedModels.add(name.substring(0, name.length() - ".java".length()));
            }
        }

        Set<String> tenantRepositories = new LinkedHashSet<>();
        for (String source : sources.values()) {
            Matcher repository = REPOSITORY.matcher(source);
            while (repository.find()) {
                if (tenantOwnedModels.contains(repository.group(2))) {
                    tenantRepositories.add(repository.group(1));
                }
            }
        }

        int count = 0;
        for (String source : sources.values()) {
            Map<String, String> fieldTypes = new LinkedHashMap<>();
            Matcher field = FIELD.matcher(source);
            while (field.find()) {
                fieldTypes.put(field.group(2), field.group(1));
            }
            Matcher lookup = FIND_BY_ID.matcher(source);
            while (lookup.find()) {
                if (tenantRepositories.contains(fieldTypes.get(lookup.group(1)))) {
                    count++;
                }
            }
        }
        return count;
    }
}
