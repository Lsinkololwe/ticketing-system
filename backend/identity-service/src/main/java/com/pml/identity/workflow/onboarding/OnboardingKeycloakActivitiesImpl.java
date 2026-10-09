package com.pml.identity.workflow.onboarding;

import com.pml.identity.domain.enums.OnboardingStep;
import com.pml.identity.domain.valueobject.OrganizationGroups;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.service.OrganizationApprovalService;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Steps 4 and 5 against the Keycloak admin API.
 *
 * <p>Calls the strict {@link KeycloakService} writes, which raise on failure, so the retry budget
 * and the compensation that follows it both see what Keycloak actually did.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.ONBOARDING)
public class OnboardingKeycloakActivitiesImpl implements OnboardingKeycloakActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final OrganizationApprovalService approvals;
    private final KeycloakService keycloak;

    public OnboardingKeycloakActivitiesImpl(OrganizationApprovalService approvals, KeycloakService keycloak) {
        this.approvals = approvals;
        this.keycloak = keycloak;
    }

    @Override
    public void grantOrganizerRole(String organizationId) {
        await(organization(organizationId)
                .flatMap(organization -> keycloak.grantRealmRole(organization.getOwnerId(), UserType.ORGANIZER.name()))
                .then(approvals.markStep(organizationId, OnboardingStep.REALM_ROLE))
                .thenReturn(Boolean.TRUE));
    }

    @Override
    public void revokeOrganizerRole(String organizationId) {
        await(organization(organizationId)
                .filter(organization -> Boolean.FALSE.equals(organization.getApprovalOwnerWasOrganizer()))
                .flatMap(organization -> keycloak.revokeRealmRole(organization.getOwnerId(), UserType.ORGANIZER.name()))
                .thenReturn(Boolean.TRUE));
    }

    @Override
    public String ensureGroupTree(String organizationId) {
        return await(organization(organizationId)
                .flatMap(organization -> keycloak.ensureOrganizationGroupTree(organization.getSlug())
                        .flatMap(groupId -> keycloak.joinOrganizationGroup(organization.getOwnerId(),
                                        organization.getSlug(), OrganizationGroups.OWNERS)
                                .then(approvals.markStep(organizationId, OnboardingStep.GROUP_TREE))
                                .thenReturn(groupId))));
    }

    @Override
    public void removeOwnerFromGroups(String organizationId) {
        await(organization(organizationId)
                .flatMap(organization -> keycloak.leaveOrganizationGroup(organization.getOwnerId(),
                        organization.getSlug(), OrganizationGroups.OWNERS))
                .thenReturn(Boolean.TRUE));
    }

    private Mono<Organization> organization(String organizationId) {
        return approvals.load(organizationId)
                .switchIfEmpty(Mono.error(() -> Refusals.refusal(ErrorCode.ORGANIZATION_UNKNOWN,
                        "no organization " + organizationId)));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
