package com.pml.identity.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.shared.constants.InvitationStatus;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.TeamInvitationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.repository.VerificationDocumentRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

/**
 * The relations and counters of an Organization that live in other collections.
 *
 * <p>The schema declares them, and several as non-null, but nothing resolved them: a field the
 * entity does not carry reads as null, which for {@code memberCount: Int!} is a GraphQL error that
 * blanks the whole admin organization page. An applicant organization is the case that exposes it:
 * it has no members, no invitations and no stats yet, and still has to render. Counts are therefore
 * always numbers (zero when there is nothing), and relations are lists, empty when there is nothing.
 *
 * <p>Relations that describe people or documents are answered only to members and administrators,
 * like the private fields beside them; the counts of an organization the caller may not see read as
 * zero rather than as an error.
 */
@DgsComponent
@RequiredArgsConstructor
public class OrganizationRelationFields {

    private final OrganizationMemberRepository members;
    private final TeamInvitationRepository invitations;
    private final VerificationDocumentRepository documents;
    private final UserRepository users;

    @DgsData(parentType = "Organization", field = "memberCount")
    public Mono<Integer> memberCount(DgsDataFetchingEnvironment env) {
        Organization org = env.getSource();
        return members.findByOrganizationIdAndStatus(org.getId(), MemberStatus.ACTIVE)
                .count().map(Long::intValue).defaultIfEmpty(0);
    }

    @DgsData(parentType = "Organization", field = "pendingInvitationCount")
    public Mono<Integer> pendingInvitationCount(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getId)
                .flatMap(id -> invitations.countByOrganizationIdAndStatus(id, InvitationStatus.PENDING))
                .map(Long::intValue)
                .defaultIfEmpty(0);
    }

    @DgsData(parentType = "Organization", field = "members")
    public Mono<List<OrganizationMember>> members(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getId)
                .flatMap(id -> members.findByOrganizationId(id).collectList())
                .defaultIfEmpty(List.of());
    }

    @DgsData(parentType = "Organization", field = "activeMembers")
    public Mono<List<OrganizationMember>> activeMembers(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getId)
                .flatMap(id -> members.findByOrganizationIdAndStatus(id, MemberStatus.ACTIVE).collectList())
                .defaultIfEmpty(List.of());
    }

    @DgsData(parentType = "Organization", field = "pendingInvitations")
    public Mono<List<TeamInvitation>> pendingInvitations(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getId)
                .flatMap(id -> invitations.findByOrganizationIdAndStatus(id, InvitationStatus.PENDING).collectList())
                .defaultIfEmpty(List.of());
    }

    @DgsData(parentType = "Organization", field = "verificationDocuments")
    public Mono<List<VerificationDocument>> verificationDocuments(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getId)
                .flatMap(id -> documents.findByOrganizationId(id).collectList())
                .defaultIfEmpty(List.of());
    }

    @DgsData(parentType = "Organization", field = "owner")
    public Mono<User> owner(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, Organization::getOwnerId)
                .flatMap(users::findById);
    }

    @DgsData(parentType = "Organization", field = "totalEvents")
    public Mono<Integer> totalEvents(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, org -> org.getStats() == null ? 0 : org.getStats().getTotalEvents());
    }

    @DgsData(parentType = "Organization", field = "totalTicketsSold")
    public Mono<Integer> totalTicketsSold(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, org -> org.getStats() == null ? 0 : org.getStats().getTotalTicketsSold());
    }

    @DgsData(parentType = "Organization", field = "totalRevenue")
    public Mono<BigDecimal> totalRevenue(DgsDataFetchingEnvironment env) {
        return OrganizationPrivateFields.forMembers(env, org -> org.getStats() == null ? BigDecimal.ZERO : org.getStats().getTotalRevenue());
    }
}
