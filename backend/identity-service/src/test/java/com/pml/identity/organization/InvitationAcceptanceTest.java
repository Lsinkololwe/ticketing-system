package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.TeamInvitationRepository;
import com.pml.shared.constants.InvitationStatus;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An invitation expires on a boundary, and the token does not say who is accepting.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>The token is not an identity</h2>
 * Acceptance verifies that the accepting identity matches the invitee. Without that check,
 * {@code accept(token, userId)} creates a membership for whoever the caller is — so anyone
 * holding the link joins the organization in the invitation's proposed role. For an `ADMIN`
 * invitation that is the team, the events and the organization's settings.
 *
 * <p>An invitation link is forwarded, screenshotted and pasted into group chats as a matter of
 * course; the holder and the addressee being different people is the normal case, not the attack.
 * Combined with a preview that disclosed the invitee's contact details, holding a forwarded link
 * would be enough to read those details and then take their seat.
 *
 * <h2>Boundaries, not the middle of the range</h2>
 * Acceptance holds at day 6 and is refused at day 8. Boundaries are where the bugs are, and a
 * suite that cannot reach them tests only the middle of every range. Day 7 exactly is included here as well, because an expiry written with
 * {@code isBefore} rather than {@code isAfter} admits a token for the whole of its final instant
 * and no test in the middle of the range would notice.
 *
 * <p>The predicates take an {@code Instant} so these run in a millisecond rather than a week —
 * passing the time in rather than reading a clock is what makes a boundary test possible at all.
 */
@Tag("L2")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002 R4/R5 · invitations expire on the boundary and only the addressee may accept")
class InvitationAcceptanceTest {

    private static final String ORG = "org-kabwe-collective";
    private static final String INVITEE_EMAIL = "mwansa@example.zm";
    private static final String INVITEE_PHONE = "+260971234567";
    private static final Instant SENT_AT = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant EXPIRES_AT = SENT_AT.plus(Duration.ofDays(7));

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TeamInvitationRepository invitations;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_invitation_acceptance"));
        invitations = new ReactiveMongoRepositoryFactory(template)
                .getRepository(TeamInvitationRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOnePendingInvitation() {
        template.remove(new Query(), TeamInvitation.class).block();
        template.save(invitation("invite-mwansa", InvitationStatus.PENDING)).block();
    }

    private static TeamInvitation invitation(String id, InvitationStatus status) {
        TeamInvitation invitation = new TeamInvitation();
        invitation.setId(id);
        invitation.setOrganizationId(ORG);
        invitation.setEmail(INVITEE_EMAIL);
        invitation.setPhoneNumber(INVITEE_PHONE);
        invitation.setProposedRole(OrganizationRole.ADMIN);
        invitation.setInvitationToken("tok-" + id);
        invitation.setExpiresAt(EXPIRES_AT);
        invitation.setStatus(status);
        invitation.setCreatedAt(SENT_AT);
        return invitation;
    }

    @Nested
    @DisplayName("R5 · who is accepting")
    class Addressee {

        @Test
        @DisplayName("ET-ORG-002-R5 · the addressee is recognised by the email it was sent to")
        void theAddresseeByEmail() {
            assertThat(stored().addressedTo(INVITEE_EMAIL, "+260000000000")).isTrue();
        }

        @Test
        @DisplayName("ET-ORG-002-R5 · or by the phone number, since either identifier is enough")
        void theAddresseeByPhone() {
            // A Zambian invitee may hold the number the invitation went to and an email the
            // inviter guessed, or the reverse. Requiring both would refuse the ordinary case.
            assertThat(stored().addressedTo("someone.else@example.zm", INVITEE_PHONE)).isTrue();
        }

        @Test
        @DisplayName("ET-ORG-002-R5 · a caller matching neither is not the addressee")
        void aStrangerIsRefused() {
            assertThat(stored().addressedTo("stranger@example.zm", "+260999999999"))
                    .as("""
                        this is the whole of R5: before the check, whoever held a forwarded link \
                        joined the organization in the proposed role — ADMIN here, which is the \
                        team, the events and the organization's settings""")
                    .isFalse();
        }

        @Test
        @DisplayName("ET-ORG-002-R5 · a caller with neither identifier at all is not the addressee")
        void nullsMatchNothing() {
            // The dangerous shape: a null-tolerant comparison that treats "unknown" as "equal"
            // admits every account with an incomplete profile.
            assertThat(stored().addressedTo(null, null)).isFalse();
        }

        @Test
        @DisplayName("ET-ORG-002-R5 · email matching ignores case, phone matching does not guess")
        void emailIsCaseInsensitive() {
            assertThat(stored().addressedTo(INVITEE_EMAIL.toUpperCase(), null)).isTrue();
            // E.164 has one spelling; normalising here would hide a malformed stored number.
            assertThat(stored().addressedTo(null, INVITEE_PHONE.replace("+", ""))).isFalse();
        }

        @Test
        @DisplayName("ET-ORG-002-R5 · an invitation with no phone is not matched by a null phone")
        void absentFieldsNeverMatch() {
            TeamInvitation emailOnly = invitation("invite-email-only", InvitationStatus.PENDING);
            emailOnly.setPhoneNumber(null);

            assertThat(emailOnly.addressedTo(null, null)).isFalse();
            assertThat(emailOnly.addressedTo(INVITEE_EMAIL, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("R4 · when it expires")
    class Expiry {

        @Test
        @DisplayName("ET-ORG-002-R4 · valid on day 6")
        void validAtDaySix() {
            assertThat(stored().isValid(SENT_AT.plus(Duration.ofDays(6)))).isTrue();
        }

        @Test
        @DisplayName("ET-ORG-002-R4 · expired on day 8")
        void expiredAtDayEight() {
            Instant dayEight = SENT_AT.plus(Duration.ofDays(8));
            assertThat(stored().isValid(dayEight)).isFalse();
            assertThat(stored().isExpired(dayEight)).isTrue();
        }

        @Test
        @DisplayName("ET-ORG-002-R4 · expired at exactly seven days, not one instant later")
        void expiredOnTheBoundaryItself() {
            // The case a day-6/day-8 pair alone would miss. `isBefore(now)` at the expiry instant
            // answers false, so the token stays valid through the whole of its final moment and
            // `isValid`/`isExpired` disagree with each other there.
            assertThat(stored().isExpired(EXPIRES_AT))
                    .as("an invitation expiring *at* expiresAt must be expired at that instant")
                    .isTrue();
            assertThat(stored().isValid(EXPIRES_AT)).isFalse();
        }

        @Test
        @DisplayName("ET-ORG-002-R4 · valid one instant before expiry")
        void validJustBeforeTheBoundary() {
            Instant lastMoment = EXPIRES_AT.minusMillis(1);
            assertThat(stored().isValid(lastMoment)).isTrue();
            assertThat(stored().isExpired(lastMoment)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(value = InvitationStatus.class,
            names = {"ACCEPTED", "DECLINED", "EXPIRED", "REVOKED"})
    @DisplayName("ET-ORG-002-R4 · only a PENDING invitation is acceptable, whatever the clock says")
    void onlyPendingIsValid(InvitationStatus terminal) {
        // The five invitation states. A token whose invitation was revoked or already accepted must not be
        // acceptable again even well inside its expiry — accepting twice creates a second
        // membership, and accepting a revoked one undoes the revocation.
        TeamInvitation resolved = invitation("invite-" + terminal, terminal);

        assertThat(resolved.isValid(SENT_AT.plus(Duration.ofDays(1))))
                .as("%s is a resolved invitation; the token is spent", terminal)
                .isFalse();
    }

    @Test
    @DisplayName("ET-ORG-002-R4 · PENDING is the only state that admits acceptance")
    void pendingIsTheOnlyLiveState() {
        long acceptable = java.util.Arrays.stream(InvitationStatus.values())
                .filter(status -> invitation("probe", status)
                        .isValid(SENT_AT.plus(Duration.ofDays(1))))
                .count();
        assertThat(acceptable)
                .as("exactly one of the five states is live")
                .isEqualTo(1);
    }

    /** Read back through the real repository, so the round trip is part of the assertion. */
    private static TeamInvitation stored() {
        TeamInvitation invitation = invitations.findById("invite-mwansa").block();
        assertThat(invitation).as("the fixture must be present").isNotNull();
        return invitation;
    }
}
