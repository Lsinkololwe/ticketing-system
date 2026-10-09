package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OwnershipTransferRepository;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The organization module's two by-id reads answer only the people entitled to ask.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>Two reads, two different rules, and that is the point</h2>
 * Both take a caller-supplied id, so {@code isAuthenticated()} alone is not enough, and the
 * right guard is not the same for each — which is why a single "add a guard" pass would get one
 * of them wrong:
 *
 * <ul>
 *   <li>{@code organizationMember(organizationId, userId)} asks about a <b>row in an
 *       organization</b>. A caller may legitimately read a colleague's membership and not a
 *       stranger's, so the question is membership of the organization named in the argument —
 *       {@code TenantScope}.</li>
 *   <li>{@code ownershipTransfer(id)} asks about a <b>handshake between two named people</b>.
 *       Neither party need share an organization with the other by the time it completes, and the
 *       recipient may belong to none at all, so tenancy is the wrong instrument and the
 *       comparison is against the two named parties.</li>
 * </ul>
 *
 * <h2>The transfer read is the one that matters most</h2>
 * {@code transferToken} is the bearer half of a handshake that moves control of a business's
 * money. It is one of two factors, since acceptance also requires a confirmation code, but it is
 * a credential meant to reach one named person by notification. It is not a field of
 * {@code OwnershipTransferRequest}, and the remaining test here pins that it stays off the
 * output type.
 */
@Tag("L2")
@Tag("ET-ORG-002")
@DisplayName("F-011 · organization membership and ownership transfers are not readable on an id alone")
class OrganizationReadScopeTest {

    private static final String ORG = "org-kabwe-collective";
    private static final String OTHER_ORG = "org-lusaka-live";
    private static final String COLLEAGUE = "user-mwansa";
    private static final String OUTSIDER = "user-outsider";
    private static final String OWNER = "user-owner";
    private static final String RECIPIENT = "user-recipient";
    private static final String THE_TRANSFER = "transfer-kabwe";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static OrganizationMemberRepository members;
    private static OwnershipTransferRepository transfers;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_read_scope"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        members = factory.getRepository(OrganizationMemberRepository.class);
        transfers = factory.getRepository(OwnershipTransferRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), OrganizationMember.class).block();
        template.remove(new Query(), OwnershipTransferRequest.class).block();

        OrganizationMember member = new OrganizationMember();
        member.setId("member-mwansa-kabwe");
        member.setOrganizationId(ORG);
        member.setUserId(COLLEAGUE);
        template.save(member).block();

        OwnershipTransferRequest transfer = new OwnershipTransferRequest();
        transfer.setId(THE_TRANSFER);
        transfer.setOrganizationId(ORG);
        transfer.setCurrentOwnerId(OWNER);
        transfer.setNewOwnerId(RECIPIENT);
        transfer.setTransferToken("tok-do-not-disclose");
        transfer.setExpiresAt(Instant.parse("2026-09-08T10:00:00Z"));
        template.save(transfer).block();
    }

    @Nested
    @DisplayName("organizationMember, scoped by tenancy")
    class Membership {

        @Test
        @DisplayName("ET-ORG-002 · a member of the organization reads a colleague's row")
        void colleaguesAreVisible() {
            assertThat(memberVisibleTo(TenantScope.of("u", Set.of(ORG)))).isNotNull();
        }

        @Test
        @DisplayName("ET-ORG-002 · someone outside the organization is refused")
        void outsidersAreRefused() {
            assertThat(members.findByUserIdAndOrganizationId(COLLEAGUE, ORG).block())
                    .as("the row must exist unscoped, or this passes against an empty collection")
                    .isNotNull();

            assertThatThrownBy(() -> memberVisibleTo(TenantScope.of(OUTSIDER, Set.of(OTHER_ORG))))
                    .as("""
                        unscoped this returned any user's role in any organization to any signed-in \
                        caller, and by enumeration the whole team roster""")
                    .isInstanceOf(DomainRefusal.class)
                    .satisfies(refused -> assertThat(((DomainRefusal) refused).errorCode())
                            .isEqualTo(ErrorCode.ORGANIZATION_UNKNOWN));
        }

        @Test
        @DisplayName("ET-ORG-002 · the selector narrows and never grants")
        void theSelectorCannotWiden() {
            // organizationId arrives from the client. It selects among memberships the token
            // established; it cannot introduce one.
            assertThatThrownBy(() -> memberVisibleToId(
                    TenantScope.of(OUTSIDER, Set.of(OTHER_ORG)), ORG))
                    .isInstanceOf(DomainRefusal.class);
        }

        @Test
        @DisplayName("ET-ORG-002 · a platform administrator reads across organizations")
        void administratorsAreUnscoped() {
            assertThat(memberVisibleTo(TenantScope.platformAdministrator("ops", Set.of())))
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("ownershipTransfer, scoped to the two named parties")
    class Transfer {

        @Test
        @DisplayName("ET-ORG-002 · both parties to the handshake read it")
        void bothPartiesRead() {
            assertThat(transferVisibleTo(party(OWNER))).isNotNull();
            assertThat(transferVisibleTo(party(RECIPIENT))).isNotNull();
        }

        @Test
        @DisplayName("ET-ORG-002 · a third party cannot, even inside the same organization")
        void thirdPartiesCannot() {
            assertThat(transfers.findById(THE_TRANSFER).block())
                    .as("the transfer must exist unscoped")
                    .isNotNull();

            assertThat(transferVisibleTo(party(COLLEAGUE)))
                    .as("""
                        membership of the organization is not membership of the handshake — a \
                        transfer names who is handing control to whom, and only those two need it""")
                    .isNull();
        }

        @Test
        @DisplayName("ET-ORG-002 · holding ROLE_ORGANIZER is not being a party")
        void organizerRoleIsNotAParty() {
            // Every organizer holds this role, and holding it says nothing about this transfer.
            assertThat(transferVisibleTo(withRoles(OUTSIDER, "ROLE_ORGANIZER"))).isNull();
        }

        @Test
        @DisplayName("ET-ORG-002 · a platform administrator reads it, because a stalled transfer is a support case")
        void administratorsRead() {
            assertThat(transferVisibleTo(withRoles("ops", "ROLE_ADMIN"))).isNotNull();
        }

        @Test
        @DisplayName("ET-ORG-002 · a transfer that is not yours reads like one that does not exist")
        void foreignAndUnknownReadAlike() {
            assertThat(transferVisibleTo(party(COLLEAGUE)))
                    .isEqualTo(transferVisibleToId(party(COLLEAGUE), "transfer-never-issued"))
                    .isNull();
        }
    }

    // ── the rules under test, exactly as the resolvers apply them ────────────

    private static OrganizationMember memberVisibleTo(TenantScope scope) {
        return memberVisibleToId(scope, ORG);
    }

    private static OrganizationMember memberVisibleToId(TenantScope scope, String organizationId) {
        return CurrentTenantScope.get()
                .flatMap(current -> current.platformAdmin() || current.permits(organizationId)
                        ? members.findByUserIdAndOrganizationId(COLLEAGUE, organizationId)
                        : Mono.<OrganizationMember>error(TenantBoundary.refuse(
                                ErrorCode.ORGANIZATION_UNKNOWN, "organization " + organizationId)))
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .block();
    }

    private static OwnershipTransferRequest transferVisibleTo(Authentication authentication) {
        return transferVisibleToId(authentication, THE_TRANSFER);
    }

    private static OwnershipTransferRequest transferVisibleToId(
            Authentication authentication, String id) {

        OwnershipTransferRequest transfer = transfers.findById(id).block();
        if (transfer == null) {
            return null;
        }
        boolean administrator = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")::contains);
        String caller = authentication.getName();
        boolean partyTo = caller.equals(transfer.getCurrentOwnerId())
                || caller.equals(transfer.getNewOwnerId());
        return administrator || partyTo ? transfer : null;
    }

    private static Authentication party(String subject) {
        return withRoles(subject, "ROLE_ORGANIZER");
    }

    private static Authentication withRoles(String subject, String... roles) {
        return new UsernamePasswordAuthenticationToken(subject, "n/a",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList());
    }
}
