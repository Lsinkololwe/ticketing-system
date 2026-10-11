package com.pml.identity.service;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A person belongs to one organization at a time: everything they see and do is scoped to the
 * organization derived from them, and a second one would make that ambiguous. Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("A person belongs to one organization at a time")
class OneOrganizationPerPersonTest {

    private OrganizationMemberRepository members;
    private OneOrganizationPerPerson rule;

    @BeforeEach
    void setUp() {
        members = Mockito.mock(OrganizationMemberRepository.class);
        rule = new OneOrganizationPerPerson(members);
    }

    private static OrganizationMember membership(String organizationId, MemberStatus status) {
        OrganizationMember member = new OrganizationMember();
        member.setUserId("user-1");
        member.setOrganizationId(organizationId);
        member.setStatus(status);
        return member;
    }

    private void holds(OrganizationMember... memberships) {
        Mockito.when(members.findByUserId("user-1")).thenReturn(Flux.just(memberships));
    }

    @Test
    @DisplayName("a person with no membership may join")
    void noMembershipMayJoin() {
        holds();

        assertThat(rule.require("user-1", "org-1").blockOptional()).isEmpty();
    }

    @Test
    @DisplayName("an active member of another organization is refused with a code the person can act on")
    void activeElsewhereIsRefused() {
        holds(membership("org-other", MemberStatus.ACTIVE));

        Throwable refused = catchThrowable(() -> rule.require("user-1", "org-1").block());

        assertThat(refused).isInstanceOfSatisfying(DomainRefusal.class,
                refusal -> assertThat(refusal.errorCode()).isEqualTo(ErrorCode.MEMBER_IN_ANOTHER_ORGANIZATION));
    }

    @Test
    @DisplayName("a suspended membership elsewhere still counts: the person may be reinstated")
    void suspendedElsewhereCounts() {
        holds(membership("org-other", MemberStatus.SUSPENDED));

        assertThat(catchThrowable(() -> rule.require("user-1", "org-1").block())).isInstanceOf(DomainRefusal.class);
    }

    @Test
    @DisplayName("having left another organization does not block joining")
    void removedElsewhereDoesNotCount() {
        holds(membership("org-other", MemberStatus.REMOVED));

        assertThat(rule.require("user-1", "org-1").blockOptional()).isEmpty();
    }

    @Test
    @DisplayName("a membership in the same organization is not another organization")
    void sameOrganizationIsNotAnother() {
        holds(membership("org-1", MemberStatus.ACTIVE));

        assertThat(rule.require("user-1", "org-1").blockOptional()).isEmpty();
    }
}
