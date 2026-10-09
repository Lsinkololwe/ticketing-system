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
 * The transfer keeps its eligibility check, its claim and its transaction.
 *
 * <p>{@code OwnershipTransferTest} proves each rule against the replica set but mirrors them,
 * because the service reaches for Keycloak's 2FA verification. These four assertions hold the
 * service to the same shape, and each guards a failure that is invisible in ordinary use: an
 * eligibility check missing half its condition passes for every active admin, a read-then-write
 * passes whenever nobody races, and an untransacted handover passes whenever nothing fails.
 */
@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R7 · eligibility, claim and transaction stay in the transfer path")
class OwnershipTransferLintTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/com/pml/identity/service/impl/OwnershipTransferServiceImpl.java");

    private static final Pattern COMMENTS = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static String service;

    @BeforeAll
    static void read() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(SERVICE), "identity sources not present");
        service = COMMENTS.matcher(Files.readString(SERVICE)).replaceAll(match -> "");
    }

    @Test
    @DisplayName("ET-ORG-002-R7 · the nominee must be ADMIN and ACTIVE, both")
    void eligibilityChecksBothHalves() {
        assertThat(service)
                .as("""
                    R7 says active ADMIN. The role alone admits a SUSPENDED member — somebody the \
                    organization deliberately shut out mid-dispute — and a REMOVED one, whose \
                    document survives by design under R6 and still reads role = ADMIN.""")
                .contains("OrganizationRole.ADMIN")
                .contains("MemberStatus.ACTIVE");

        assertThat(service)
                .as("and the refusal names the requirement, so a caller learns what to fix")
                .contains("TRANSFER_TARGET_INELIGIBLE");
    }

    @Test
    @DisplayName("ET-ORG-002-R7 · confirmation claims the transfer before performing it")
    void confirmationClaimsFirst() {
        String body = bodyOf("private Mono<Boolean> claimPending");

        assertThat(body)
                .as("""
                    The status must be in the query. Two confirmations racing is the ordinary \
                    case — a nominee tapping twice, or a retry after a timeout — and both \
                    proceeding runs the handover twice, the second demoting the owner it just \
                    promoted.""")
                .contains("TransferStatus.PENDING")
                .contains("getModifiedCount");
    }

    @Test
    @DisplayName("ET-ORG-002-R7 · the two role changes and the ownerId are one transaction")
    void theHandoverIsOneTransaction() {
        String body = bodyOf("private Mono<OwnershipTransferRequest> executeTransfer");

        assertThat(body)
                .as("""
                    A demote that commits without its promote leaves the organization with no \
                    owner at all — worse than the two owners the partial unique index prevents, \
                    because nothing can then be transferred, no payout requested and no member \
                    removed.""")
                .contains("transactionalOperator::transactional");
    }

    @Test
    @DisplayName("ET-ORG-002-R7 · the demote precedes the promote, as the one-owner index requires")
    void demoteBeforePromote() {
        // Not style. The partial unique index on {organizationId} where role = OWNER holds one
        // owner at a time, so promoting the nominee while the current owner still holds the key
        // is a duplicate-key failure rather than a handover.
        String body = bodyOf("private Mono<OwnershipTransferRequest> executeTransfer");
        int demote = body.indexOf("currentOwnerMember.setRole(OrganizationRole.ADMIN)");
        int promote = body.indexOf("newOwnerMember.setRole(OrganizationRole.OWNER)");

        assertThat(demote).as("the demote has moved — re-point this lint").isGreaterThan(-1);
        assertThat(promote).as("the promote has moved — re-point this lint").isGreaterThan(-1);
        assertThat(demote)
                .as("the outgoing owner must give up the key before the incoming one takes it")
                .isLessThan(promote);
    }

    private static String bodyOf(String signature) {
        Matcher method = Pattern.compile(Pattern.quote(signature) + "[^)]*\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(service);
        assertThat(method.find()).as("%s has moved — re-point this lint", signature).isTrue();
        return method.group(1);
    }
}
