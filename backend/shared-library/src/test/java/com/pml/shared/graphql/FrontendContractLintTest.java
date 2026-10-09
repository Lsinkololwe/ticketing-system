package com.pml.shared.graphql;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code docs/FRONTEND_GRAPHQL_CONTRACT.md} says it is generated, so it is.
 *
 * <h2>The claim, and what it was worth</h2>
 * The document's second line reads <em>"Generated from the subgraph schemas. Do not edit by
 * hand — it is rewritten whenever a {@code .graphqls} file changes."</em> Nothing generated it
 * and nothing rewrote it. The reconciliation pass measured the drift: identity alone carried
 * <b>19 operations advertised that no subgraph implements and 15 implemented operations the
 * document never mentions</b>.
 *
 * <p>Reading it closely turned up something worse than drift. It listed {@code status},
 * {@code type} and {@code verified} as identity <em>queries</em> — repeatedly, once per type
 * that has a field by that name. Whatever produced it scanned field declarations without
 * restricting them to the {@code Query} and {@code Mutation} blocks, so its headline count of
 * 557 operations included object fields, and its 441 "PUBLIC by omission" was inflated by
 * fields that are not operations and have no audience.
 *
 * <p>An authority that is declared with nothing enforcing it recurs in this repository — the
 * index registry and the closed registries are the same shape. The pattern is
 * consistent enough that a "generated, do not edit" header here should be read as a claim to
 * check rather than a fact to trust.
 *
 * <h2>This class is the generator</h2>
 * There is one implementation of the parse, and it lives here rather than in a script, so the
 * document cannot disagree with the thing that checks it. Ordinary runs re-derive the contract
 * and compare; the diff is the failure message.
 *
 * <pre>
 * mvn -q -pl shared-library test -Dtest=FrontendContractLintTest -Dcontract.write=true
 * </pre>
 *
 * rewrites the file. Run it after any schema change — the same moment {@code npm run codegen}
 * is due, and for the same reason.
 *
 * <h2>Where the role column comes from, and why it takes two sources</h2>
 * This platform enforces authorization two ways, and each subgraph picked a different one.
 * Catalog carries {@code @auth(requires: …)} on 102 of its 141 root fields, enforced at runtime
 * by {@link com.pml.shared.graphql.auth.AuthDirective}. Booking and identity carry almost none —
 * identity has <b>zero</b> — and guard at the resolver with Spring Security's
 * {@code @PreAuthorize} instead: 208 of them in booking, 144 in identity.
 *
 * <p>The first version of this generator read only the directive. It would have published a
 * contract labelling 372 operations "PUBLIC by omission", among them every payment-attempt query
 * in booking and every user lookup in identity — all of them guarded, none of them public. That
 * is a worse document than the hand-maintained one it replaced: wrong in the direction a reader
 * acts on. A frontend developer reading it would conclude an admin query needs no token.
 *
 * <p>So the role is resolved from the directive when there is one, and from the resolver's
 * {@code @PreAuthorize} when there is not. When neither exists the answer is still not "public":
 * every subgraph carries {@code .pathMatchers("/graphql/**").authenticated()}, so the floor is a
 * valid token and the field is reported as <b>AUTHENTICATED (endpoint floor only)</b>.
 *
 * <p>That bucket is the one worth watching. It is where {@code event(id)} sat while it returned
 * other organizations' drafts to any signed-in caller, and the floor holding it
 * up is one line of Java in each service — which is why {@link #graphQlEndpointRequiresAToken}
 * asserts that line rather than assuming it.
 */
@Tag("L4")
@Tag("ET-PLT-004")
@DisplayName("F-006 · the frontend contract is generated from the schemas, not maintained by hand")
class FrontendContractLintTest {

    private static final Path ROOT = locateRoot();
    private static final Path CONTRACT = ROOT.resolve("docs/FRONTEND_GRAPHQL_CONTRACT.md");

    /** Subgraph name → the root of its Java sources, scanned for {@code @PreAuthorize}. */
    private static final Map<String, String> RESOLVER_ROOTS = new LinkedHashMap<>(Map.of(
            "booking", "backend/booking-service/src/main/java",
            "catalog", "backend/catalog-service/src/main/java",
            "identity", "backend/identity-service/src/main/java"));

    /** Subgraph name → its SDL. Order is the order the tables are grouped in. */
    private static final Map<String, String> SUBGRAPHS = new LinkedHashMap<>(Map.of(
            "booking", "backend/booking-service/src/main/resources/graphql/schema.graphqls",
            "catalog", "backend/catalog-service/src/main/resources/graphql/schema.graphqls",
            "identity", "backend/identity-service/src/main/resources/graphql/schema.graphqls"));

    /** The prose above the tables. Hand-written, and the only part of the file that is. */
    private static final String PREAMBLE = """
            # Frontend GraphQL contract

            Generated from the subgraph schemas by `FrontendContractLintTest`, which also fails the \
            build when this file and the schemas disagree. To rewrite it after a schema change:

            ```
            mvn -q -pl shared-library test -Dtest=FrontendContractLintTest -Dcontract.write=true
            ```

            ## How the frontend consumes this

            - One endpoint: the Apollo Router at `/graphql` behind the gateway. Never call a subgraph port directly — the router owns query planning.
            - **Types come from codegen, always.** `cd frontend/web && npm run codegen`. A hand-written TypeScript type for a GraphQL shape is a defect.
            - Send the JWT as `Authorization: Bearer <token>`. Every subgraph validates it independently against JWKS, so a token the gateway accepted can still be refused downstream — handle `UNAUTHENTICATED` on any operation.
            - Branch on `extensions.errorCode`, never on `message`. Read `extensions.retryable` to decide whether to offer a retry.
            - `TOKEN_REVOKED` means sign the user out; it will not resolve by retrying.
            - Money-moving mutations take an `idempotencyKey`. Generate it once per user intent and reuse it across retries of that same intent.
            - Dashboards poll (D-12); there are no subscriptions on this path. Use a visibility-aware interval and stop polling on a hidden tab.
            - The role below is resolved from the field's `@auth(requires: …)` directive when it has one, and from the resolver's `@PreAuthorize` when it does not. Catalog uses the directive, booking and identity use `@PreAuthorize`; neither source alone describes the platform.
            - **`AUTHENTICATED (endpoint floor only)` means neither check was found on the field.** It is not public: all three subgraphs carry `.pathMatchers("/graphql/**").authenticated()`, so a token is always required whatever a `# PUBLIC` comment in the schema says. It does mean *any* signed-in caller reaches it — a self-service `CUSTOMER` token included — which is where `event(id)` sat while it was returning other organizations' unpublished events (F-007).
            """;

    private record Operation(String subgraph, String kind, String name, String args, String role) {
    }

    private static Path locateRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int up = 0; up < 4 && candidate != null; up++, candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("specs")) && Files.isDirectory(candidate.resolve("docs"))) {
                return candidate;
            }
        }
        return Path.of("../..");
    }

    @Test
    @DisplayName("ET-PLT-004 · the committed contract matches what the schemas say")
    void contractMatchesTheSchemas() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(ROOT.resolve(SUBGRAPHS.get("booking"))),
                "subgraph schemas not present");

        List<Operation> operations = readAllSubgraphs();

        // A generator that produces nothing regenerates a blank document and passes forever.
        assertThat(operations)
                .as("no root fields parsed out of three subgraph schemas — the parser broke, "
                        + "not the platform")
                .hasSizeGreaterThan(300);

        String rendered = render(operations);

        if (Boolean.getBoolean("contract.write")) {
            Files.writeString(CONTRACT, rendered);
            return;
        }

        String committed = Files.exists(CONTRACT) ? Files.readString(CONTRACT) : "";
        assertThat(rendered)
                .as("""
                    docs/FRONTEND_GRAPHQL_CONTRACT.md no longer matches the subgraph schemas. \
                    It is generated, so do not edit it: run \
                    `mvn -q -pl shared-library test -Dtest=FrontendContractLintTest \
                    -Dcontract.write=true` and commit the result. If the change was not \
                    intended, the schema edit is the thing to look at — this file has no \
                    independent content to lose.""")
                .isEqualTo(committed);
    }

    @Test
    @DisplayName("ET-PLT-004 · only root fields are listed — object fields are not operations")
    void objectFieldsAreNotOperations() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(ROOT.resolve(SUBGRAPHS.get("identity"))),
                "subgraph schemas not present");

        // The specific defect in the hand-maintained file: `status`, `type` and `verified`
        // appeared as identity queries, once per object type carrying a field of that name.
        // They are common enough as object fields that this stays a live risk for any future
        // parser, and generic enough that a count-only check would not notice.
        List<String> names = readAllSubgraphs().stream().map(Operation::name).toList();

        assertThat(names)
                .as("these are fields on object types; a parser that lists them is not "
                        + "restricting itself to the Query and Mutation blocks")
                .doesNotContain("status", "type", "verified", "id", "createdAt");
    }

    /** The chain a domain service runs when it declares none of its own. */
    private static final String SHARED_SERVICE_CHAIN =
            "backend/shared-library/src/main/java/com/pml/shared/security/ServiceSecurity.java";

    @Test
    @DisplayName("ET-PLT-004 · every subgraph still requires a token on /graphql")
    void graphQlEndpointRequiresAToken() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(ROOT.resolve("backend/catalog-service")),
                "backend sources not present");

        // 76 of the 506 operations carry no field-level check of any kind. What stands between
        // them and the open internet is this one line per service. Relaxing it to permitAll —
        // plausible while chasing an anonymous-browsing bug, since the schema does call these
        // queries PUBLIC — would publish every one of them, and nothing else in the build
        // would notice. The contract document would go on saying AUTHENTICATED, truthfully
        // when written and wrongly from that commit on.
        List<String> problems = new ArrayList<>();
        for (String subgraph : RESOLVER_ROOTS.keySet()) {
            Path root = ROOT.resolve(RESOLVER_ROOTS.get(subgraph));
            List<Path> chains;
            try (var files = Files.walk(root)) {
                chains = files.filter(f -> f.getFileName().toString().equals("SecurityConfig.java")).toList();
            }
            if (chains.isEmpty()) {
                // No chain of its own: the service runs the platform's, which steps aside only
                // for a service that declares one.
                chains = List.of(ROOT.resolve(SHARED_SERVICE_CHAIN));
            }
            String rule = null;
            for (Path file : chains) {
                Matcher m = Pattern.compile("pathMatchers\\(\"/graphql[^\"]*\"\\)\\s*\\.(\\w+)")
                        .matcher(Files.readString(file));
                if (m.find()) {
                    rule = m.group(1);
                }
            }
            if (rule == null) {
                problems.add(subgraph + " has no /graphql rule in its security chain — "
                        + "either it moved, or the endpoint fell through to anyExchange");
            } else if (!rule.equals("authenticated")) {
                problems.add("%s answers /graphql with %s() rather than authenticated(). "
                        .formatted(subgraph, rule)
                        + "That publishes every field with no @auth and no @PreAuthorize.");
            }
        }
        assertThat(problems)
                .as("the floor under every unguarded operation in the contract")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-004 · every declared operation has a resolver — F-003, closed")
    void nothingIsAdvertisedWithoutBeingImplemented() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(ROOT.resolve("backend/catalog-service")),
                "backend sources not present");

        // A root field declared in the SDL with nothing bound behind it still composes into the
        // supergraph, generates client types, and appears in this very document as an operation
        // a frontend can call. Calling one gets an error.
        //
        // The dangerous case is a ledger operation such as `creditPlatformAccount` or
        // `trialBalance` declared with neither @auth nor @PreAuthorize. Harmless while nothing
        // answers it, and unguarded money movement the moment somebody binds a resolver
        // without thinking to add a guard the schema never showed was missing.
        //
        // This keeps the number at zero, because the cheapest way to "add an operation" is to
        // write the SDL line and mean to come back to it.
        List<String> orphans = new ArrayList<>();
        Set<String> bound = resolverBoundFields();

        for (Map.Entry<String, String> subgraph : SUBGRAPHS.entrySet()) {
            Path sdl = ROOT.resolve(subgraph.getValue());
            if (!Files.isRegularFile(sdl)) {
                continue;
            }
            for (Operation operation : operations(subgraph.getKey(), Files.readString(sdl), Map.of())) {
                if (!bound.contains(operation.name())) {
                    orphans.add("%s.%s is declared with no @DgsQuery/@DgsMutation behind it"
                            .formatted(subgraph.getKey(), operation.name()));
                }
            }
        }
        assertThat(orphans)
                .as("""
                    An operation the schema advertises and the server cannot answer is worse than \
                    one that is absent: clients generate types for it, this contract lists it, and \
                    an unguarded declaration is a hole waiting for whoever binds it. Either \
                    implement it with a guard, or delete the line.""")
                .isEmpty();
    }

    /** Every field a {@code @DgsQuery} or {@code @DgsMutation} answers, across all three services. */
    private static Set<String> resolverBoundFields() throws IOException {
        Set<String> bound = new LinkedHashSet<>();
        for (String subgraph : RESOLVER_ROOTS.keySet()) {
            Path root = ROOT.resolve(RESOLVER_ROOTS.get(subgraph));
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    collectBound(Files.readString(file), bound);
                }
            }
        }
        return bound;
    }

    private static void collectBound(String source, Set<String> bound) {
        String declaredField = null;
        boolean isEntryPoint = false;
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@DgsQuery") || trimmed.startsWith("@DgsMutation")) {
                isEntryPoint = true;
                Matcher named = Pattern.compile("field\\s*=\\s*\"(\\w+)\"").matcher(trimmed);
                declaredField = named.find() ? named.group(1) : null;
                continue;
            }
            Matcher method = Pattern.compile("^public\\s+.*?\\b(\\w+)\\s*\\(").matcher(trimmed);
            if (method.find()) {
                if (isEntryPoint) {
                    bound.add(declaredField != null ? declaredField : method.group(1));
                }
                declaredField = null;
                isEntryPoint = false;
                continue;
            }
            if (trimmed.isEmpty() || trimmed.equals("}")) {
                declaredField = null;
                isEntryPoint = false;
            }
        }
    }

    // ── the generator ───────────────────────────────────────────────────────

    private static List<Operation> readAllSubgraphs() throws IOException {
        List<Operation> all = new ArrayList<>();
        for (Map.Entry<String, String> subgraph : SUBGRAPHS.entrySet()) {
            Path sdl = ROOT.resolve(subgraph.getValue());
            if (Files.isRegularFile(sdl)) {
                all.addAll(operations(subgraph.getKey(), Files.readString(sdl),
                        preAuthorizeByField(subgraph.getKey())));
            }
        }
        return all;
    }

    /** Root fields of {@code Query} and {@code Mutation}, and nothing else. */
    private static List<Operation> operations(String subgraph, String sdl, Map<String, String> guards) {
        List<Operation> found = new ArrayList<>();
        Matcher header = Pattern.compile("(?m)^(?:extend\\s+)?type\\s+(Query|Mutation)\\b[^{]*\\{")
                .matcher(sdl);
        while (header.find()) {
            String kind = header.group(1);
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
            found.addAll(fields(subgraph, kind,
                    sdl.substring(header.end(), Math.max(header.end(), i - 1)), guards));
        }
        return found;
    }

    /**
     * Field declarations inside one root block.
     *
     * <p>Character-walked rather than matched per line, because an argument list may span
     * several lines — {@code myEffectivePermissions(organizationId: ID, eventId: ID)} is
     * written across four in identity's schema, and a line-oriented pattern reports it with
     * no arguments.
     */
    private static List<Operation> fields(
            String subgraph, String kind, String block, Map<String, String> guards) {
        List<Operation> found = new ArrayList<>();
        int i = 0;
        while (i < block.length()) {
            char c = block.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '#') {
                i = endOfLine(block, i);
                continue;
            }
            if (block.startsWith("\"\"\"", i)) {
                int close = block.indexOf("\"\"\"", i + 3);
                i = close < 0 ? block.length() : close + 3;
                continue;
            }
            if (!Character.isJavaIdentifierStart(c)) {
                i++;
                continue;
            }
            int nameStart = i;
            while (i < block.length() && Character.isJavaIdentifierPart(block.charAt(i))) {
                i++;
            }
            String name = block.substring(nameStart, i);

            int afterName = skipSpace(block, i);
            String args = "";
            if (afterName < block.length() && block.charAt(afterName) == '(') {
                int close = matchParen(block, afterName);
                if (close < 0) {
                    break;
                }
                args = block.substring(afterName + 1, close);
                afterName = skipSpace(block, close + 1);
            }
            if (afterName >= block.length() || block.charAt(afterName) != ':') {
                // A bare token that is not a field — an enum-ish stray or a directive.
                i = afterName;
                continue;
            }
            int lineEnd = endOfLine(block, afterName);
            found.add(new Operation(subgraph, kind, name, normaliseArguments(args),
                    role(block.substring(afterName, lineEnd), guards.get(name))));
            i = lineEnd;
        }
        return found;
    }

    /**
     * The role a caller needs, from whichever mechanism this subgraph uses.
     *
     * <p>The directive wins where present because it is declared on the field itself; the
     * resolver annotation is the fallback, not a lesser answer. {@code unrestricted} is
     * reserved for a field neither guards, and is deliberately not spelled "PUBLIC": a field
     * nobody protected is not the same thing as a field somebody decided to publish.
     */
    private static String role(String typeAndDirectives, String preAuthorize) {
        Matcher auth = Pattern.compile("@auth\\s*\\(\\s*requires\\s*:\\s*(\\w+)")
                .matcher(typeAndDirectives);
        if (auth.find()) {
            return auth.group(1);
        }
        return preAuthorize == null ? "AUTHENTICATED (endpoint floor only)" : summarise(preAuthorize);
    }

    /**
     * {@code hasAnyRole('ORGANIZER','ADMIN')} → {@code ADMIN or ORGANIZER}.
     *
     * <p>An ownership check is a grant in its own right and is kept: {@code hasAnyRole('ADMIN',
     * 'FINANCE') or @eventSecurityService.isEventOrganizer(…)} → {@code @isEventOrganizer or ADMIN
     * or FINANCE}, and a comparison with the caller's own id → {@code self}. Dropping them made an
     * organizer's own query read as console-only.</p>
     */
    private static String summarise(String expression) {
        List<String> roles = new ArrayList<>();
        Matcher named = Pattern.compile("has(?:Any)?(?:Role|Authority)\\s*\\(([^)]*)\\)")
                .matcher(expression);
        while (named.find()) {
            Matcher literal = Pattern.compile("'([^']+)'|\"([^\"]+)\"").matcher(named.group(1));
            while (literal.find()) {
                String role = literal.group(1) != null ? literal.group(1) : literal.group(2);
                role = role.replaceFirst("^ROLE_", "");
                if (!roles.contains(role)) {
                    roles.add(role);
                }
            }
        }
        Matcher ownership = Pattern.compile("@\\w+\\.(\\w+)\\(").matcher(expression);
        while (ownership.find()) {
            String grant = "@" + ownership.group(1);
            if (!roles.contains(grant)) {
                roles.add(grant);
            }
        }
        if (expression.contains("principal") && !roles.contains("self")) {
            roles.add("self");
        }
        if (!roles.isEmpty()) {
            return String.join(" or ", roles.stream().sorted().toList());
        }
        if (expression.contains("isAuthenticated")) {
            return "AUTHENTICATED";
        }
        if (expression.contains("permitAll")) {
            return "PUBLIC";
        }
        return expression;
    }

    /**
     * DGS field name → the {@code @PreAuthorize} expression guarding its resolver.
     *
     * <p>Scanned line by line rather than with one annotation-block pattern, because
     * {@code @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")} contains a closing parenthesis
     * inside its own argument and every naive {@code \\([^)]*\\)} form stops early on it —
     * which reports the whole corpus as unguarded, the exact false negative worth avoiding here.
     */
    private static Map<String, String> preAuthorizeByField(String subgraph) throws IOException {
        Path root = ROOT.resolve(RESOLVER_ROOTS.get(subgraph));
        Map<String, String> guards = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            return guards;
        }
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                collectGuards(Files.readString(file), guards);
            }
        }
        return guards;
    }

    private static void collectGuards(String source, Map<String, String> guards) {
        String pending = null;
        boolean isEntryPoint = false;
        String declaredField = null;
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@PreAuthorize")) {
                Matcher expression = Pattern.compile("@PreAuthorize\\(\"(.*)\"\\)").matcher(trimmed);
                pending = expression.find() ? expression.group(1) : null;
                continue;
            }
            if (trimmed.startsWith("@DgsQuery") || trimmed.startsWith("@DgsMutation")) {
                isEntryPoint = true;
                // @DgsQuery(field = "x") names a field that differs from the method name.
                Matcher named = Pattern.compile("field\\s*=\\s*\"(\\w+)\"").matcher(trimmed);
                declaredField = named.find() ? named.group(1) : null;
                continue;
            }
            Matcher method = Pattern.compile("^public\\s+.*?\\b(\\w+)\\s*\\(").matcher(trimmed);
            if (method.find()) {
                if (isEntryPoint && pending != null) {
                    guards.put(declaredField != null ? declaredField : method.group(1), pending);
                }
                pending = null;
                isEntryPoint = false;
                declaredField = null;
                continue;
            }
            // A blank line or a closing brace between annotations and a method means the
            // annotation belonged to something else; anything else (javadoc, further
            // annotations) leaves the pending guard in place.
            if (trimmed.isEmpty() || trimmed.equals("}")) {
                pending = null;
                isEntryPoint = false;
                declaredField = null;
            }
        }
    }

    /** Argument list as one line: newline-separated declarations become comma-separated. */
    private static String normaliseArguments(String raw) {
        if (raw.isBlank()) {
            return "—";
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : raw.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            }
            if (depth == 0 && (c == ',' || c == '\n')) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());

        List<String> cleaned = parts.stream()
                .map(part -> part.replaceAll("#.*", ""))
                .map(part -> part.replaceAll("\\s+", " ").trim())
                .filter(part -> !part.isEmpty())
                .toList();
        return cleaned.isEmpty() ? "—" : String.join(", ", cleaned);
    }

    private static String render(List<Operation> operations) {
        Map<String, List<Operation>> byRole = new TreeMap<>();
        for (Operation operation : operations) {
            byRole.computeIfAbsent(operation.role(), r -> new ArrayList<>()).add(operation);
        }

        StringBuilder out = new StringBuilder(PREAMBLE);
        out.append("\n## Operations by required role (").append(operations.size()).append(" total)\n");

        Comparator<Operation> order = Comparator.comparing(Operation::subgraph)
                .thenComparing(Operation::kind)
                .thenComparing(Operation::name);

        byRole.forEach((role, rows) -> {
            out.append("\n### `").append(role).append("` — ").append(rows.size())
                    .append(" operation(s)\n\n")
                    .append("| Subgraph | Kind | Operation | Arguments |\n")
                    .append("|---|---|---|---|\n");
            rows.stream().sorted(order).forEach(row -> out
                    .append("| ").append(row.subgraph())
                    .append(" | ").append(row.kind())
                    .append(" | `").append(row.name())
                    .append("` | ").append("—".equals(row.args()) ? "—" : "`" + row.args() + "`")
                    .append(" |\n"));
        });
        return out.toString();
    }

    private static int skipSpace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int endOfLine(String text, int from) {
        int i = text.indexOf('\n', from);
        return i < 0 ? text.length() : i;
    }

    private static int matchParen(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (text.charAt(i) == '(') {
                depth++;
            } else if (text.charAt(i) == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
