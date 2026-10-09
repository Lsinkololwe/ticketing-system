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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An operation declared as scoped to <em>the caller</em> must not be satisfied by one that takes
 * the caller's identity as an argument.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>The mistake this exists to prevent</h2>
 * Reconciling declared operation names against the schema invites mapping `myEscrowAccounts` —
 * an ORGANIZER seeing their own escrow accounts — onto the shipped `escrowAccounts`, on the
 * evidence that it is the one candidate a client actually calls. That field is
 * {@code @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")} over {@code escrowService.findAll()}:
 * every escrow account on the platform. The rename would have rewritten an organizer-scoped
 * requirement as a finance-wide one, in a document nobody re-reads, with no code change to
 * review and nothing failing.
 *
 * <p>The other candidate was worse in a quieter way: {@code escrowAccountsByOrganizer(organizerId)}
 * works, and returns the right rows — for whichever id the client sends. Substituting it for a
 * {@code my*} operation converts an identity taken from the token into one taken from the
 * request body. That is CWE-639 written into the operation contract.
 *
 * <h2>What is asserted</h2>
 * For every declared operation whose name marks it as caller-scoped, the schema must either implement
 * it under that name, or not claim to implement it at all. A {@code my*} requirement pointed at
 * a {@code *By<Identity>(id)} field is refused here, with the pairing named.
 *
 * <p>This is a spec-level guard, deliberately. The runtime half — that a caller cannot reach
 * another tenant's rows — is {@code TicketTierTenantBoundaryTest} against a real replica set,
 * and {@code TenantBoundaryLintTest} ratchets the read paths still to convert. This one stops
 * the boundary being dissolved on paper before any of that runs.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("F-001 · a caller-scoped requirement is never satisfied by a caller-supplied id")
class ImplicitSubjectOperationTest {

    private static final Path SPECS = locate();

    /**
     * Arguments naming <em>a principal other than the caller</em>.
     *
     * <p>{@code organizationId} is deliberately absent, and the distinction is the whole point.
     * A caller may belong to several organizations — {@link TenantScope} carries the set rather
     * than picking one — so {@code myPayoutRequests(organizationId)} is asking "which of my
     * organizations", and the server still resolves it against the caller's memberships. That is
     * a scope selector.
     *
     * <p>{@code payoutRequestsByOrganizer(organizerId)} is a different thing wearing similar
     * clothes: the argument names <em>whose</em> data to return, and nothing in the name obliges
     * anyone to check it against the token. These identify a person, and on a caller-scoped
     * operation they are CWE-639.
     *
     * <p>The line is not cosmetic, and it is not enforced by this test alone: a scope selector is
     * only safe while something checks it. {@code TenantScope.permits} is that check, and
     * {@code TenantBoundaryLintTest} ratchets the read paths that do not call it yet.
     */
    private static final Pattern FOREIGN_PRINCIPAL = Pattern.compile(
            "\\b(organizerId|buyerId|ownerId|userId|customerId|memberId)\\b");

    /**
     * The mappings considered and refused, kept so the reasoning is not re-derived — and so a
     * later pass that "tidies" one of these into a declared operation list fails here rather than
     * shipping.
     */
    private static final Map<String, String> REFUSED = new LinkedHashMap<>(Map.of(
            "myEscrowAccounts", "escrowAccounts / escrowAccountsByOrganizer",
            "myPayoutRequests", "payoutRequestsByOrganizer(organizerId)",
            "myRefundRequests", "refundRequestsByBuyer(buyerId)",
            "paymentAttempts", "paymentAttemptsByBuyer(buyerId) and four siblings"));

    private static Path locate() {
        Path p = Path.of("").toAbsolutePath();
        for (int up = 0; up < 5 && p != null; up++, p = p.getParent()) {
            if (Files.isDirectory(p.resolve("specs"))) {
                return p.resolve("specs");
            }
        }
        return Path.of("../../specs");
    }

    @Test
    @DisplayName("ET-PLT-007 · no `my*` operation in §4 names another principal in its arguments")
    void callerScopedOperationsTakeNoIdentityArgument() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SPECS), "specs/ not present");

        List<String> problems = new ArrayList<>();
        int examined = 0;

        for (Path spec : specYamls()) {
            String yaml = Files.readString(spec);
            String id = firstMatch(yaml, "(?m)^id:\\s*(\\S+)");
            for (String declaration : operations(yaml)) {
                String name = declaration.replaceAll("^\\W*([A-Za-z_]\\w*).*$", "$1");
                if (!name.startsWith("my") || name.length() < 3 || !Character.isUpperCase(name.charAt(2))) {
                    continue;
                }
                examined++;
                String arguments = declaration.contains("(")
                        ? declaration.substring(declaration.indexOf('('), declaration.indexOf(')') + 1)
                        : "";
                Matcher identity = FOREIGN_PRINCIPAL.matcher(arguments);
                if (identity.find()) {
                    problems.add("""
                            %s declares %s — a caller-scoped operation naming another principal \
                            via `%s`. The subject of a `my*` operation comes from the token; an \
                            argument that names whose data to return is CWE-639. Either drop it, \
                            or rename the operation so it stops claiming to be caller-scoped. \
                            (`organizationId` is allowed: it selects among the caller's own \
                            memberships and is resolved against TenantScope.)"""
                            .formatted(id, declaration.strip(), identity.group(1)));
                }
            }
        }

        // A guard that scans nothing passes forever. The corpus has caller-scoped operations;
        // finding none means the extraction broke, not that the platform got safer.
        assertThat(examined)
                .as("no `my*` operations found in §4 — the extraction is broken, not the corpus")
                .isGreaterThan(3);

        assertThat(problems)
                .as("OWASP A01:2021 — an identity the caller chooses is not an identity")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-007 · the refused mappings are still refused — §4 never adopts them")
    void refusedMappingsStayRefused() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SPECS), "specs/ not present");

        List<String> adopted = new ArrayList<>();
        for (Path spec : specYamls()) {
            String yaml = Files.readString(spec);
            for (String declaration : operations(yaml)) {
                String name = declaration.replaceAll("^\\W*([A-Za-z_]\\w*).*$", "$1");
                REFUSED.forEach((callerScoped, unsafe) -> {
                    // The by-id form appearing where the my* form belonged
                    if (name.equals(unsafe.split("[ (/]")[0]) && !name.equals(callerScoped)) {
                        adopted.add("%s appears in §4 in place of %s — see the reconciliation "
                                .formatted(name, callerScoped)
                                + "note in that spec before changing this");
                    }
                });
            }
        }
        assertThat(adopted)
                .as("""
                    These four were examined during the D-19 name reconciliation and refused: \
                    each would have replaced a token-derived subject with a client-supplied one. \
                    They are satisfied by converting a read path under F-001, not by renaming.""")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-007 · every spec that declares operations is actually scanned")
    void noSpecIsSilentlySkipped() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SPECS), "specs/ not present");

        // A section header pattern ending `\\s*$` misses the six headers in this corpus that
        // carry a trailing comment (`queries:      # every one ADMIN + @tag admin`), and every
        // file behind them reads as clean to every test above. A guard that skips a file is
        // indistinguishable from a guard that passes it, which is why the coverage needs
        // asserting separately from the findings.
        List<String> unscanned = new ArrayList<>();
        for (Path spec : specYamls()) {
            String yaml = Files.readString(spec);
            String id = firstMatch(yaml, "(?m)^id:\\s*(\\S+)");
            boolean declaresSome = Pattern
                    .compile("(?m)^  (?:queries|mutations):[^\\S\\n]*(?:#.*)?\\n    - ")
                    .matcher(yaml).find();
            if (declaresSome && operations(yaml).isEmpty()) {
                unscanned.add(id + " declares operations that the parser does not see");
            }
        }
        assertThat(unscanned)
                .as("a spec the extraction cannot read is not a spec with nothing to find")
                .isEmpty();
    }

    /**
     * Declared operations that take a <b>scope selector</b>, and the shipped operation that
     * looks like an answer and ignores the selector entirely.
     *
     * <p>Key is the declaring file's id and the operation as declared; value is the impostor.
     */
    private static final Map<String, String> IGNORES_THE_SELECTOR = Map.of(
            "ET-ORG-003 myEffectivePermissions", "currentUserPermissions");

    @Test
    @DisplayName("ET-PLT-007 · a scope-selecting operation is not satisfied by one that ignores the selector")
    void selectorTakingOperationsKeepTheirSelector() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SPECS), "specs/ not present");

        // `myPermissions` has two meanings. The unscoped one is exactly `currentUserPermissions`
        // — no argument, read from the JWT's realm roles. `myPermissions(organizationId)` is the
        // other, and answering it with the same field would run step one of the five-step
        // permission resolution in place of all five: realm role only, no event grant, no
        // organization role, no custom permission, no explicit deny. Silently, and with a
        // field name that reads like it fits.
        List<String> problems = new ArrayList<>();
        for (Path spec : specYamls()) {
            String yaml = Files.readString(spec);
            String id = firstMatch(yaml, "(?m)^id:\\s*(\\S+)");
            for (String declaration : operations(yaml)) {
                String name = declaration.replaceAll("^\\W*([A-Za-z_]\\w*).*$", "$1");
                IGNORES_THE_SELECTOR.forEach((row, impostor) -> {
                    String[] parts = row.split(" ", 2);
                    if (parts[0].equals(id) && name.equals(impostor)) {
                        problems.add("""
                                %s declares `%s` where §4 asks for `%s`. That field takes no \
                                scope argument, so adopting it drops the selector rather than \
                                honouring it — the answer would be right for a caller in one \
                                organization and wrong for everyone else, with nothing failing."""
                                .formatted(id, impostor, parts[1]));
                    }
                });
            }
        }
        assertThat(problems)
                .as("a selector nothing reads is not a selector")
                .isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static List<Path> specYamls() throws IOException {
        try (var files = Files.walk(SPECS)) {
            return files.filter(p -> p.getFileName().toString().equals("spec.yaml"))
                    .filter(p -> !p.toString().contains("_templates"))
                    .sorted()
                    .toList();
        }
    }

    /** The `queries:` and `mutations:` entries under `graphql:`, as written. */
    private static List<String> operations(String yaml) {
        List<String> out = new ArrayList<>();
        // `[^\\S\\n]*(#.*)?$` and not `\\s*$`: six section headers in this corpus carry a
        // trailing comment — `queries:      # every one ADMIN + @tag admin`. The stricter
        // form matches none of them and leaves those files silently unscanned by every check
        // in this class. A parser that skips a file reports it as clean.
        Matcher section = Pattern.compile("(?m)^  (queries|mutations):[^\\S\\n]*(?:#.*)?$").matcher(yaml);
        while (section.find()) {
            // From the start of the NEXT line: section.end() sits before the newline, so
            // splitting there yields an empty first element and the loop breaks at once —
            // which reads as "this spec declares no operations" rather than as a bug.
            int i = yaml.indexOf('\n', section.end());
            if (i < 0) {
                continue;
            }
            for (String line : yaml.substring(i + 1).split("\n")) {
                if (!line.startsWith("    - ")) {
                    break;
                }
                out.add(line.substring(6).trim().replaceAll("^\"|\"$", ""));
            }
        }
        return out;
    }

    private static String firstMatch(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
