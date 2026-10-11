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
 * Removal keeps revoking event grants, in one transaction, from one place.
 *
 * <h2>Why one method and not two</h2>
 * `removeMember` and `leaveOrganization` end the same thing for different reasons — an
 * administrator's decision and the member's own — and before this they had two bodies doing the
 * same three steps. Two bodies drift: the next requirement lands in one of them, and a member who
 * leaves keeps grants a member who is removed loses. Both now route through one transition, and
 * this asserts they still do.
 *
 * <p>{@code MemberRemovalTest} proves the transition is right. It cannot prove the service still
 * performs it, because it mirrors the transition rather than invoking one that reaches for
 * Keycloak.
 */
@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R6 · both removal paths revoke grants in one transaction")
class MemberRemovalLintTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/com/pml/identity/service/impl/OrganizationMemberServiceImpl.java");

    /** Comments only; the literals asserted below are values the code genuinely writes. */
    private static final Pattern COMMENTS = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static String service;

    @BeforeAll
    static void read() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(SERVICE), "identity sources not present");
        service = COMMENTS.matcher(Files.readString(SERVICE)).replaceAll(match -> "");
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · both paths end the membership through one transition")
    void bothPathsShareOneTransition() {
        assertThat(countOf("endMembership\\("))
                .as("""
                    Three call sites: the definition plus removeMember and leaveOrganization. \
                    Two bodies doing the same three steps drift — the next requirement lands in \
                    one of them, and a member who leaves keeps grants a member who is removed \
                    loses.""")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · the grants are revoked and the status moves in one transaction")
    void revocationAndStatusAreOneTransaction() {
        String body = bodyOf("private Mono<OrganizationMember> endMembership");

        assertThat(body)
                .as("""
                    The halves are only meaningful together: a status without the revocations is \
                    the defect R6 exists for, and revocations without the status take somebody's \
                    access while leaving them on the team.""")
                .contains("revokeEventGrants")
                .contains("MemberStatus.REMOVED")
                .contains("setRemovedAt")
                .contains("transactionalOperator::transactional");
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · the revocation is scoped to the organization, not the user")
    void revocationIsScopedToTheOrganization() {
        String body = bodyOf("private Mono<Void> revokeEventGrants");

        assertThat(body)
                .as("""
                    A member removed from one organization keeps whatever access they hold in \
                    another. Revoking by userId alone would take access nobody removed them from.""")
                .contains("findByUserIdAndOrganizationId")
                .contains("getOrganizationId()");

        assertThat(body)
                .as("""
                    Only live grants are touched. Re-stamping an already-revoked row moves an \
                    organizer's deliberate revocation to today and relabels their reason, quietly \
                    rewriting the audit trail.""")
                .contains("AccessGrantStatus.ACTIVE");
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · a removal writes no Keycloak state: it marks the row for the mirror workflow")
    void keycloakIsNotWrittenByTheRemoval() {
        // Keycloak mirrors membership; it is not the source of truth. A removal that called Keycloak,
        // inside or after the transaction, would couple the one operation you least want blocked to a
        // third party. The row carries the mirror marker and the group-mirror workflow applies it.
        String body = bodyOf("private Mono<OrganizationMember> endMembership");

        assertThat(body).contains("setMirrorPending(true)").doesNotContain("keycloak").doesNotContain("Keycloak.");
        assertThat(service).as("the service holds no Keycloak client").doesNotContain("KeycloakService");
    }

    private static int countOf(String regex) {
        return (int) Pattern.compile(regex).matcher(service).results().count();
    }

    private static String bodyOf(String signature) {
        Matcher method = Pattern.compile(Pattern.quote(signature) + "[^)]*\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(service);
        assertThat(method.find()).as("%s has moved — re-point this lint", signature).isTrue();
        return method.group(1);
    }
}
