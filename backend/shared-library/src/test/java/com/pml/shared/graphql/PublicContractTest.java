package com.pml.shared.graphql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The public contract carries no admin or internal vocabulary.
 *
 * <h2>The leak is the name, not the data</h2>
 * An admin-only operation is already unreachable without the role, so the
 * instinct is that tagging it is tidiness. It is not. The contract variant is
 * what mobile and customer clients <b>generate types from</b>, so an untagged
 * admin field publishes {@code suspendUser}, {@code resolveEscalation},
 * {@code platformRevenueAccount} into a schema every customer can introspect —
 * a map of the platform's internal model, and a list of what to go looking for.
 *
 * <h2>The public contract is derived here rather than trusted</h2>
 * GraphOS applies the exclusion in the cloud, which means the check would
 * otherwise happen after publication, in a system this build cannot see. So this
 * test performs the same removal against the on-disk SDL: strip every element
 * tagged {@code admin} or {@code internal}, then look for admin vocabulary in
 * what is left. It is the exclusion rule reproduced, not the exclusion result
 * assumed. {@link PublicContract} does the removal on the parsed SDL, so a tag on a type, an
 * extension or a multi-line field counts; {@code PublicContractDerivationTest} shows it
 * reporting each kind of leak on small schemas.
 */
@Tag("L4")
@Tag("ET-PLT-004")
@DisplayName("ET-PLT-004-R3 · the public contract excludes admin and internal")
class PublicContractTest {

    /** A tag that removes an element from the public contract. */
    private static final Pattern ANY_TAG = Pattern.compile("@tag\\(\\s*name:\\s*\"([^\"]+)\"\\s*\\)");

    /** The roles of the platform console. An operation only these may call is an admin operation. */
    private static final Set<String> CONSOLE_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "FINANCE");

    /** `### `ADMIN or FINANCE` — 60 operation(s)` in the generated contract document. */
    private static final Pattern ROLE_SECTION = Pattern.compile("^### `([^`]+)`");

    /** `| booking | Mutation | `acceptChargeback` | … |`. */
    private static final Pattern OPERATION_ROW =
            Pattern.compile("^\\|\\s*(\\w+)\\s*\\|\\s*(Query|Mutation|Subscription)\\s*\\|\\s*`(\\w+)`");

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static List<Path> subgraphSchemas() {
        List<Path> schemas = new ArrayList<>();
        for (String service : List.of("booking-service", "catalog-service", "identity-service")) {
            Path schema = backendRoot()
                    .resolve(service)
                    .resolve("src/main/resources/graphql/schema.graphqls");
            if (Files.isRegularFile(schema)) {
                schemas.add(schema);
            }
        }
        return schemas;
    }

    private static PublicContract publicContract() throws IOException {
        Map<String, String> sdl = new LinkedHashMap<>();
        for (Path schema : subgraphSchemas()) {
            sdl.put(schema.toString(), Files.readString(schema));
        }
        return PublicContract.derive(sdl);
    }

    @Test
    @DisplayName("the three subgraph schemas are found, so this test is not vacuous")
    void schemasAreFound() {
        assertThat(subgraphSchemas())
                .as("no schema.graphqls located — every assertion below would pass on an "
                        + "empty contract, which is the most convincing kind of wrong")
                .hasSize(3);
    }

    @Test
    @DisplayName("no admin vocabulary survives into the public contract")
    void publicContractHasNoAdminVocabulary() throws IOException {
        assertThat(publicContract().adminVocabulary())
                .as("""
                    these reach the public contract, so every customer and mobile client \
                    generates types for them and can introspect them by name. Add \
                    @tag(name: "admin") — or @tag(name: "internal") for service-to-service \
                    operations — to the field.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the public contract still composes: nothing public points at a removed type")
    void publicContractComposes() throws IOException {
        PublicContract contract = publicContract();

        assertThat(contract.unknownReferences())
                .as("a public field names a type no subgraph defines — the derivation is misreading the SDL")
                .isEmpty();
        assertThat(contract.dangling())
                .as("a public field or argument uses a type tagged admin or internal; the contract "
                        + "variant would fail to compose. Tag the field too, or untag the type.")
                .isEmpty();
        assertThat(contract.emptied())
                .as("every field of these public types is tagged, leaving an empty type the "
                        + "contract cannot publish. Tag the type itself.")
                .isEmpty();
    }

    @Test
    @DisplayName("an operation only the platform console may call is never in the public contract")
    void consoleOnlyOperationsAreTagged() throws IOException {
        // The two mechanisms answer different questions — @auth or @PreAuthorize decides who may
        // call it, @tag decides who may see that it exists — and it is easy to add the first and
        // forget the second. The result passes every authorization test while publishing the
        // operation's name to everyone. The role comes from the generated contract document, which
        // resolves both mechanisms and which FrontendContractLintTest keeps current.
        PublicContract contract = publicContract();
        List<String> exposed = new ArrayList<>();
        int consoleOnly = 0;

        String role = null;
        for (String line : Files.readAllLines(backendRoot().getParent().resolve("docs/FRONTEND_GRAPHQL_CONTRACT.md"))) {
            Matcher section = ROLE_SECTION.matcher(line);
            if (section.find()) {
                role = section.group(1);
                continue;
            }
            Matcher row = OPERATION_ROW.matcher(line);
            if (role == null || !row.find() || !consoleOnly(role)) {
                continue;
            }
            consoleOnly++;
            if (contract.exposes(row.group(2), row.group(3))) {
                exposed.add("%s %s.%s (%s)".formatted(row.group(1), row.group(2), row.group(3), role));
            }
        }

        assertThat(consoleOnly).as("no console-only operation was read — the document's shape changed").isGreaterThan(100);
        assertThat(exposed)
                .as("only the platform console may call these, and they are in the public contract. "
                        + "Add @tag(name: \"admin\").")
                .isEmpty();
    }

    /** {@code ADMIN or FINANCE} is console-only; {@code ADMIN or ORGANIZER} is not. */
    private static boolean consoleOnly(String role) {
        return List.of(role.split(" or ")).stream().allMatch(CONSOLE_ROLES::contains);
    }

    @Test
    @DisplayName("the exclusion rule actually removes something")
    void theExclusionRemovesSomething() throws IOException {
        // Guards the shape of the check itself: if tags stopped being read — a parser change, a
        // renamed directive — every element would survive into the "public" contract and the
        // vocabulary test would still pass, because it would find no admin fields to complain about.
        PublicContract contract = publicContract();

        assertThat(contract.exposes("Mutation", "suspendUser")).as("an admin-tagged mutation").isFalse();
        assertThat(contract.exposes("Query", "event")).as("an untagged public query").isTrue();
    }

    @Test
    @DisplayName("tag names come from the closed set the contract is configured with")
    void onlyKnownTagsAreUsed() throws IOException {
        // A typo'd tag excludes nothing. `@tag(name: "Admin")` reads correct in a
        // diff and leaves the field fully public, which is the worst available
        // outcome: the author believes it is protected.
        List<String> unknown = new ArrayList<>();

        for (Path schema : subgraphSchemas()) {
            Matcher matcher = ANY_TAG.matcher(Files.readString(schema));
            while (matcher.find()) {
                String name = matcher.group(1);
                if (!List.of("admin", "internal", "organizer", "mobile").contains(name)) {
                    unknown.add(backendRoot().relativize(schema) + " → @tag(name: \"" + name + "\")");
                }
            }
        }

        assertThat(unknown)
                .as("unrecognised tag name. The contract excludes exactly `admin` and "
                        + "`internal`; anything else — including a differently-cased %s — "
                        + "excludes nothing.", "\"Admin\"".toLowerCase(Locale.ROOT))
                .isEmpty();
    }
}
