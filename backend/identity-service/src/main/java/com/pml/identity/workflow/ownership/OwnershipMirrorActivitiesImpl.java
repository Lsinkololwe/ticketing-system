package com.pml.identity.workflow.ownership;

import com.pml.identity.domain.valueobject.OrganizationGroups;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.OwnershipTransferService;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.error.ErrorCode;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Writes a completed transfer to Keycloak through the strict writes, which raise,
 * so a failed attempt is retried rather than logged and forgotten.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.ONBOARDING)
public class OwnershipMirrorActivitiesImpl implements OwnershipMirrorActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final OwnershipTransferService transfers;
    private final OrganizationService organizations;
    private final KeycloakService keycloak;

    public OwnershipMirrorActivitiesImpl(OwnershipTransferService transfers, OrganizationService organizations,
                                         KeycloakService keycloak) {
        this.transfers = transfers;
        this.organizations = organizations;
        this.keycloak = keycloak;
    }

    @Override
    public void mirror(String transferId) {
        await(transfers.findById(transferId)
                .switchIfEmpty(Mono.error(() -> Refusals.refusal(ErrorCode.TRANSFER_NOT_PENDING, "no transfer " + transferId)))
                .flatMap(transfer -> organizations.findById(transfer.getOrganizationId())
                        .switchIfEmpty(Mono.error(() -> Refusals.refusal(ErrorCode.ORGANIZATION_UNKNOWN,
                                "no organization " + transfer.getOrganizationId())))
                        .flatMap(organization -> {
                            String slug = organization.getSlug();
                            return keycloak.leaveOrganizationGroup(transfer.getCurrentOwnerId(), slug, OrganizationGroups.OWNERS)
                                    .then(keycloak.joinOrganizationGroup(transfer.getCurrentOwnerId(), slug, OrganizationGroups.ADMINS))
                                    .then(keycloak.leaveOrganizationGroup(transfer.getNewOwnerId(), slug, OrganizationGroups.ADMINS))
                                    .then(keycloak.joinOrganizationGroup(transfer.getNewOwnerId(), slug, OrganizationGroups.OWNERS));
                        }))
                .thenReturn(Boolean.TRUE));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
