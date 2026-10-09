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
 * Acceptance keeps its three guarantees: addressee, single claim, one transaction.
 *
 * <h2>What the runtime tests cannot hold</h2>
 * {@code InvitationAcceptanceTest} proves the addressee rule and the expiry boundary.
 * {@code ConcurrentAcceptanceTest} proves a conditional update admits exactly one caller. Neither
 * can prove the <em>service</em> still does those things: both mirror the rule rather than invoking
 * it, because the service reaches for Keycloak-backed collaborators no fixture here stands up.
 *
 * <p>So this reads the source. The three properties are independent and each fails silently on its
 * own — an acceptance that skips the addressee check still works for the invitee, one that reads
 * before writing still works when nobody races, and one outside a transaction still works when
 * nothing fails.
 */
@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R5 · the acceptance path keeps its addressee, claim and transaction")
class InvitationAcceptanceLintTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/com/pml/identity/service/impl/TeamInvitationServiceImpl.java");

    /** Comments only; the string literals below are field names the code genuinely uses. */
    private static final Pattern COMMENTS = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static String service;

    @BeforeAll
    static void read() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(SERVICE), "identity sources not present");
        service = COMMENTS.matcher(Files.readString(SERVICE)).replaceAll(match -> "");
    }

    @Test
    @DisplayName("ET-ORG-002-R5 · the accepting user's own account is what is compared")
    void theAddresseeIsCheckedAgainstTheAccount() {
        assertThat(service)
                .as("""
                    The token says which invitation, never who is accepting. Without this, any \
                    signed-in holder of a forwarded link joins in the proposed role — ADMIN for \
                    an admin invitation, which is the team, the events and the settings.""")
                .contains("addressedTo");

        Matcher check = Pattern.compile(
                        "acceptAsAddressee\\([^)]*\\)\\s*\\{(.*?)\\n    \\}", Pattern.DOTALL)
                .matcher(service);
        assertThat(check.find()).as("acceptAsAddressee has moved — re-point this lint").isTrue();
        assertThat(check.group(1))
                .as("""
                    The comparison must be against the account, not the request. An email or phone \
                    taken from the caller's own input is an identity the caller chose.""")
                .contains("userService.findById")
                .contains("user.getEmail()")
                .contains("user.getPhoneNumber()");
    }

    @Test
    @DisplayName("ET-ORG-002-R5 · the claim is conditional on PENDING, not a read-then-write")
    void theClaimIsConditional() {
        Matcher claim = Pattern.compile("claimPending\\([^)]*\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(service);
        assertThat(claim.find()).as("claimPending has moved — re-point this lint").isTrue();

        String body = claim.group(1);
        assertThat(body)
                .as("""
                    The status must be in the query, so a row that moved in between does not \
                    match. A repository save cannot express "only if still PENDING": it writes \
                    whatever the caller read, and both racers wrote.""")
                .contains("\"status\"")
                .contains("InvitationStatus.PENDING")
                .contains("getModifiedCount");
    }

    @Test
    @DisplayName("ET-ORG-002-R5 · the claim and the membership are one transaction")
    void acceptanceIsOneTransaction() {
        Matcher accept = Pattern.compile("claimThenCreate\\([^)]*\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(service);
        assertThat(accept.find()).as("claimThenCreate has moved — re-point this lint").isTrue();

        assertThat(accept.group(1))
                .as("""
                    Three writes — the status, the membership, the event grants. A failure between \
                    any two leaves a member with no grants, or a membership created against an \
                    invitation still PENDING that the next holder of the link can accept again.""")
                .contains("transactionalOperator::transactional");
    }

    @Test
    @DisplayName("ET-ORG-002-R5 · the notification is not inside the transaction")
    void theNotificationIsNotTransactional() {
        // An inviter told somebody joined, by a transaction that then rolls back, is worse than
        // one told a moment late — the second is a delay, the first is a false statement about
        // who is on the team.
        Matcher membership = Pattern.compile("createMembership\\([^)]*\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(service);
        assertThat(membership.find()).isTrue();
        assertThat(membership.group(1))
                .as("nothing inside the transactional membership write requests a message")
                .doesNotContain("notifyAcceptance");

        Matcher accept = Pattern.compile("claimThenCreate\\([^)]*\\)\\s*\\{(.*?)\\n    \\}", Pattern.DOTALL)
                .matcher(service);
        assertThat(accept.find()).isTrue();
        int boundary = accept.group(1).indexOf("transactionalOperator::transactional");
        int notification = accept.group(1).indexOf("notifyAcceptance");
        assertThat(notification)
                .as("the acceptance notification is requested after the transactional boundary closes")
                .isGreaterThan(boundary)
                .isGreaterThan(-1);
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · a re-invitation supersedes the live one instead of refusing")
    void reInvitationSupersedes() {
        assertThat(service)
                .as("""
                    R3: revoke the pending one and create a new one, never two live. Refusing \
                    instead strands the inviter — a mistyped role or a bounced address cannot be \
                    corrected until the first invitation expires a week later.""")
                .contains("revokePendingFor")
                .doesNotContain("already has a pending invitation");

        Matcher supersede = Pattern.compile("revokePendingFor\\([^)]*\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(service);
        assertThat(supersede.find()).as("revokePendingFor has moved — re-point this lint").isTrue();
        assertThat(supersede.group(1))
                .as("""
                    Scoped to this address in this organization, and to PENDING only. Scoped by \
                    organization alone it would revoke the whole team's outstanding invitations; \
                    without the status it would rewrite when and why a settled one ended.""")
                .contains("\"email\"")
                .contains("\"organizationId\"")
                .contains("InvitationStatus.PENDING");
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · the supersession and the new invitation are one transaction")
    void supersessionIsAtomic() {
        Matcher invite = Pattern.compile("revokePendingFor\\(inviteeEmail, inviteePhone, organizationId\\)(.*?);",
                        Pattern.DOTALL)
                .matcher(service);
        assertThat(invite.find()).as("the invite path has moved — re-point this lint").isTrue();
        assertThat(invite.group(1))
                .as("""
                    A revoke that commits without its replacement leaves the invitee with no way \
                    in while the inviter believes one was sent; a create without the revoke is the \
                    two live invitations R3 forbids.""")
                .contains("transactionalOperator::transactional");
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · an existing active member cannot be invited again")
    void activeMembersAreNotReInvited() {
        assertThat(service)
                .as("R3 refuses with MEMBER_ALREADY_EXISTS. Without it the invitee accepts and the "
                        + "{userId, organizationId} unique index throws, surfacing as an internal "
                        + "error rather than an answer")
                .contains("MEMBER_ALREADY_EXISTS")
                .contains("alreadyAMember");

        Matcher membership = Pattern.compile("alreadyAMember\\([^)]*\\)\\s*\\{(.*?)\\n    \\}",
                        Pattern.DOTALL)
                .matcher(service);
        assertThat(membership.find()).isTrue();
        assertThat(membership.group(1))
                .as("""
                    Active membership specifically. A member removed under R6 keeps their document, \
                    and treating that as membership would make the re-invitation R3 explicitly \
                    allows impossible.""")
                .contains("isActiveMember");
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · only an active organization may invite")
    void onlyActiveOrganizationsInvite() {
        assertThat(service)
                .as("inviting into a suspended or unapproved organization builds a team for "
                        + "something that cannot sell a ticket")
                .contains("ORGANIZATION_NOT_ACTIVE")
                .contains("org.isActive()");
    }

    @Test
    @DisplayName("ET-ORG-002-R4 · expiry is read from the clock, never from the wall")
    void expiryUsesTheInjectedClock() {
        assertThat(service)
                .as("ET-PLT-001 R3. A wall-clock read makes the day-6/day-8 boundary tests "
                        + "impossible, which is why they did not exist")
                .contains("clock.instant()")
                .doesNotContain("Instant.now()");
    }
}
