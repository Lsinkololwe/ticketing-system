package com.pml.identity.organization;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two read scopes and the token's absence from the schema stay in place.
 *
 * <h2>What a runtime test cannot hold</h2>
 * {@code OrganizationReadScopeTest} proves the rules are right. It cannot prove the resolvers
 * still apply them, because each rule is a filter a few lines long whose removal leaves a method
 * that compiles and returns the row to whoever asked. Nothing fails unless something looks.
 *
 * <p>The third assertion is a different kind: it holds a decision about the <em>schema</em> rather
 * than about code. {@code transferToken} is the bearer half of an ownership handshake, and it is
 * only safe to scope the read while the token is not a field somebody can select. Adding it
 * to the type would look like filling in a missing field.
 */
@Tag("L4")
@Tag("ET-ORG-002")
@DisplayName("F-011 · the ownership and membership reads stay scoped, and the token stays off the type")
class OrganizationReadScopeLintTest {

    private static final Path QUERIES = Path.of("src/main/java/com/pml/identity/web/graphql/query");
    private static final Path MEMBERS = QUERIES.resolve("OrganizationMemberQueryResolver.java");
    private static final Path TRANSFERS = QUERIES.resolve("OwnershipTransferQueryResolver.java");
    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");

    /** Comments only. String literals stay: the role names checked below are string literals. */
    private static final Pattern COMMENTS = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static String members;
    private static String transfers;
    private static String sdl;

    @BeforeAll
    static void read() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(MEMBERS), "identity sources not present");
        members = COMMENTS.matcher(Files.readString(MEMBERS)).replaceAll(match -> "");
        transfers = COMMENTS.matcher(Files.readString(TRANSFERS)).replaceAll(match -> "");
        sdl = Files.readString(SDL);
    }

    @Test
    @DisplayName("ET-ORG-002 · organizationMember resolves the caller's tenancy before answering")
    void membershipReadIsTenantScoped() {
        String body = bodyOf(members,
                "Mono<OrganizationMember> organizationMember\\(",
                "organizationMember");

        assertThat(body)
                .as("""
                    Both arguments come from the client. The question is not whether the row is \
                    the caller's but whether they belong to the organization it names, which is \
                    what TenantScope answers — a subject comparison would refuse every colleague.""")
                .contains("CurrentTenantScope.get()")
                .contains("permits(organizationId)")
                .contains("TenantBoundary.refuse");
    }

    @Test
    @DisplayName("ET-ORG-002 · ownershipTransfer answers only the two named parties, or an administrator")
    void transferReadIsPartyScoped() {
        String body = bodyOf(transfers,
                "Mono<OwnershipTransferRequest> ownershipTransfer\\(",
                "ownershipTransfer");

        assertThat(body)
                .as("a transfer names who is handing control of a business to whom")
                .contains("isPartyTo");

        assertThat(transfers)
                .as("""
                    The parties are the current owner and the named recipient. Neither need share \
                    an organization with the other, and the recipient may belong to none, so \
                    tenancy is the wrong instrument here.""")
                .contains("getCurrentOwnerId")
                .contains("getNewOwnerId");
    }

    @Test
    @DisplayName("ET-ORG-002 · ROLE_ORGANIZER opens neither read")
    void organizerRoleOpensNothing() throws IOException {
        assertThat(transfers)
                .as("the platform-wide branch of the transfer read must go through the one named, audited path")
                .contains("PlatformWideAccess.isPlatformWide(PlatformWideAccess.Reason.OWNERSHIP_TRANSFER_READ)")
                .doesNotContain("ROLE_ORGANIZER")
                .doesNotContain("ROLE_FINANCE");

        String authorities = Files.readString(Path.of(
                "../shared-library/src/main/java/com/pml/shared/security/tenancy/TenancyProperties.java"));
        assertThat(authorities)
                .as("""
                    Every organizer holds ROLE_ORGANIZER, so admitting it as platform-wide would open every \
                    ownership transfer on the platform to every organizer. FINANCE is absent for a \
                    different reason: a transfer is a control change, not a money movement.""")
                .contains("ROLE_ADMIN")
                .doesNotContain("ROLE_ORGANIZER")
                .doesNotContain("ROLE_FINANCE");
    }

    @Test
    @DisplayName("ET-ORG-002 · transferToken is an input only, never a field on the output type")
    void theTokenIsNotReadable() {
        Matcher type = Pattern.compile(
                        "(?ms)^type\\s+OwnershipTransferRequest\\b[^{]*\\{(.*?)^\\}").matcher(sdl);
        assertThat(type.find())
                .as("OwnershipTransferRequest has moved — re-point this lint")
                .isTrue();

        // Field declarations only. The type carries a comment explaining why the token is not a
        // field, and an assertion over the raw block would read that explanation as the thing it
        // forbids.
        String fields = type.group(1).replaceAll("#[^\\n]*", "");

        assertThat(fields)
                .as("""
                    The token is the bearer half of the handshake and travels to the named \
                    recipient by notification. Acceptance and decline take it as an argument; \
                    nothing reads it back. A field here makes a credential selectable by anyone \
                    the read admits, which is why scoping the read alone would not have been \
                    enough.""")
                .doesNotContain("transferToken");

        assertThat(sdl)
                .as("it remains an input on the confirmation, which is how the recipient supplies it")
                .contains("input ConfirmOwnershipTransferInput");
    }

    private static String bodyOf(String source, String signature, String name) {
        Matcher method = Pattern.compile(signature + "[^)]*\\)\\s*\\{(.*?)\\n    \\}", Pattern.DOTALL)
                .matcher(source);
        assertThat(method.find())
                .as("%s has moved or been renamed — re-point this lint", name)
                .isTrue();
        return method.group(1);
    }
}
