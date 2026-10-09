package com.pml.identity.security;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.TeamInvitationRepository;
import com.pml.identity.repository.VerificationDocumentRepository;
import com.pml.identity.web.graphql.resolver.OrganizationPrivateFields;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Identity's organization-owned records reach only their own organization, the person a grant was
 * given to, and platform administrators — against a MongoDB replica set with the real derived queries.
 * A record from another organization must be indistinguishable from one that was never issued.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("Identity records stay inside their organization")
class IdentityTenantBoundaryTest {

    private static final String OWNER_ORG = "org-owner";
    private static final String OTHER_ORG = "org-other";
    private static final String GRANTEE = "user-grantee";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventAccessGrantRepository grants;
    private static OrganizationMemberRepository members;
    private static TeamInvitationRepository invitations;
    private static VerificationDocumentRepository documents;

    private IdentityTenantReads reads;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_tenant_boundary"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        grants = factory.getRepository(EventAccessGrantRepository.class);
        members = factory.getRepository(OrganizationMemberRepository.class);
        invitations = factory.getRepository(TeamInvitationRepository.class);
        documents = factory.getRepository(VerificationDocumentRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        for (Class<?> type : List.of(EventAccessGrant.class, OrganizationMember.class, TeamInvitation.class, VerificationDocument.class)) {
            template.remove(new Query(), type).block();
        }
        EventAccessGrant grant = new EventAccessGrant();
        grant.setId("grant-1");
        grant.setUserId(GRANTEE);
        grant.setEventId("event-1");
        grant.setOrganizationId(OWNER_ORG);
        grants.save(grant).block();

        OrganizationMember member = new OrganizationMember();
        member.setId("member-1");
        member.setUserId("user-member");
        member.setOrganizationId(OWNER_ORG);
        members.save(member).block();

        TeamInvitation invitation = new TeamInvitation();
        invitation.setId("invitation-1");
        invitation.setOrganizationId(OWNER_ORG);
        invitations.save(invitation).block();

        VerificationDocument document = new VerificationDocument();
        document.setId("document-1");
        document.setOrganizationId(OWNER_ORG);
        documents.save(document).block();

        reads = new IdentityTenantReads(grants, members, invitations, documents);
    }

    @Test
    @DisplayName("Another organization is refused every record, with the answer an unissued id gets")
    void anotherOrganizationIsRefused() {
        assertThat(grants.findById("grant-1").block()).as("reachable without the guard").isNotNull();
        TenantScope outsider = TenantScope.of("user-outsider", Set.of(OTHER_ORG));

        assertRefusedLikeUnknown(outsider, reads.grantForCaller("grant-1"), reads.grantForCaller("grant-never"), ErrorCode.ACCESS_GRANT_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.memberForCaller("member-1"), reads.memberForCaller("member-never"), ErrorCode.MEMBER_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.invitationForCaller("invitation-1"), reads.invitationForCaller("invitation-never"), ErrorCode.INVITATION_UNKNOWN);
        assertRefusedLikeUnknown(outsider, reads.documentForCaller("document-1"), reads.documentForCaller("document-never"), ErrorCode.DOCUMENT_UNKNOWN);
        assertThat(as(outsider, reads.grantsForEvent("event-1")).collectList().block()).isEmpty();
        assertThat(as(outsider, reads.userGrantForCaller(GRANTEE, "event-1")).block()).isNull();
    }

    @Test
    @DisplayName("A member of the owning organization reaches every record")
    void aMemberReachesEverything() {
        TenantScope member = TenantScope.of("user-member", Set.of(OWNER_ORG));

        assertThat(as(member, reads.grantForCaller("grant-1")).block()).isNotNull();
        assertThat(as(member, reads.memberForCaller("member-1")).block()).isNotNull();
        assertThat(as(member, reads.invitationForCaller("invitation-1")).block()).isNotNull();
        assertThat(as(member, reads.documentForCaller("document-1")).block()).isNotNull();
        assertThat(as(member, reads.grantsForEvent("event-1")).collectList().block()).hasSize(1);
        assertThat(as(member, reads.userGrantForCaller(GRANTEE, "event-1")).block()).isNotNull();
    }

    @Test
    @DisplayName("The person a grant was given to reads it without belonging to the organization")
    void theGranteeReadsTheirGrant() {
        TenantScope grantee = TenantScope.of(GRANTEE, Set.of());

        assertThat(as(grantee, reads.grantForCaller("grant-1")).block()).isNotNull();
        assertThat(as(grantee, reads.userGrantForCaller(GRANTEE, "event-1")).block()).isNotNull();
    }

    @Test
    @DisplayName("A platform administrator reaches any organization's record")
    void anAdministratorReachesEverything() {
        TenantScope admin = TenantScope.platformAdministrator("user-admin", Set.of());

        assertThat(as(admin, reads.memberForCaller("member-1")).block()).isNotNull();
        assertThat(as(admin, reads.grantsForEvent("event-1")).collectList().block()).hasSize(1);
        assertThat(as(admin, reads.userGrantForCaller(GRANTEE, "event-1")).block()).isNotNull();
    }

    @Test
    @DisplayName("An organization's tax, contact and payout details reach its members and administrators only")
    void privateOrganizationFieldsReachMembersOnly() {
        Organization organization = new Organization();
        organization.setId(OWNER_ORG);
        organization.setTaxId("1001234567");
        organization.setBusinessEmail("accounts@example.test");

        TenantScope member = TenantScope.of("user-member", Set.of(OWNER_ORG));
        TenantScope outsider = TenantScope.of("user-outsider", Set.of(OTHER_ORG));
        TenantScope admin = TenantScope.platformAdministrator("user-admin", Set.of());

        assertThat(as(member, OrganizationPrivateFields.visibleTo(organization, Organization::getTaxId)).block()).isEqualTo("1001234567");
        assertThat(as(admin, OrganizationPrivateFields.visibleTo(organization, Organization::getBusinessEmail)).block()).isEqualTo("accounts@example.test");
        assertThat(as(outsider, OrganizationPrivateFields.visibleTo(organization, Organization::getTaxId)).block()).isNull();
        assertThat(as(outsider, OrganizationPrivateFields.visibleTo(organization, Organization::getBusinessEmail)).block()).isNull();
    }

    private static <T> Mono<T> as(TenantScope scope, Mono<T> call) {
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(scope)));
    }

    private static <T> Flux<T> as(TenantScope scope, Flux<T> call) {
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(scope)));
    }

    private static JwtAuthenticationToken auth(TenantScope scope) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("token").header("alg", "none").subject(scope.subject()).build(), List.of());
    }

    private static void assertRefusedLikeUnknown(TenantScope scope, Mono<?> someoneElses, Mono<?> neverIssued, ErrorCode code) {
        DomainRefusal owned = refusal(scope, someoneElses);
        DomainRefusal unknown = refusal(scope, neverIssued);
        assertThat(owned.errorCode()).isEqualTo(code);
        assertThat(unknown.errorCode()).isEqualTo(code);
        assertThat(owned.details()).isEqualTo(unknown.details());
    }

    private static DomainRefusal refusal(TenantScope scope, Mono<?> call) {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        assertThatThrownBy(() -> as(scope, call).block()).satisfies(thrown::set).isInstanceOf(DomainRefusal.class);
        return (DomainRefusal) thrown.get();
    }
}
