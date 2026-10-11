package com.pml.identity.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.workflow.ownership.OwnershipTransferProcess;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;
import org.springframework.validation.annotation.Validated;
import com.pml.shared.security.revocation.FailClosedOnRevocation;

/**
 * GraphQL Mutation Resolver for Ownership Transfer operations.
 *
 * <p>Each mutation is a command to the transfer's {@code OwnershipTransferWorkflow},
 * which owns the three-day expiry and the Keycloak mirror.
 */
@Slf4j

@DgsComponent
@Validated
@RequiredArgsConstructor
public class OwnershipTransferMutationResolver {

    private final OwnershipTransferProcess transferProcess;
    private final OrganizationMemberService memberService;

    /**
     * Initiate ownership transfer.
     * Only the current owner can initiate a transfer.
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.initiateOwnershipTransfer")
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> initiateOwnershipTransfer(
            @InputArgument String organizationId,
            @InputArgument String newOwnerId,
            @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(currentOwnerId -> log.info("User {} initiating ownership transfer of organization {} to user {}",
                        currentOwnerId, organizationId, newOwnerId))
                .flatMap(currentOwnerId -> memberService.findOwner(organizationId)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Organization owner not found")))
                        .flatMap(owner -> {
                            if (!owner.getUserId().equals(currentOwnerId)) {
                                return Mono.error(new IllegalStateException("Only the owner can initiate a transfer"));
                            }

                            return transferProcess.initiate(organizationId, currentOwnerId, newOwnerId, reason);
                        }));
    }

    /**
     * Cancel ownership transfer.
     * Only the current owner can cancel.
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.cancelOwnershipTransfer")
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> cancelOwnershipTransfer(@InputArgument String organizationId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(currentOwnerId -> log.info("User {} cancelling ownership transfer for organization {}", currentOwnerId, organizationId))
                .flatMap(currentOwnerId -> transferProcess.cancel(organizationId, currentOwnerId));
    }

    /**
     * Sends the nominee a one-time code to their verified phone; {@code acceptOwnershipTransfer}
     * requires it.
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.requestOwnershipTransferCode")
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> requestOwnershipTransferCode(@InputArgument String token) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(callerId -> transferProcess.requestConfirmationCode(token, callerId))
                .thenReturn(true);
    }

    /**
     * Accept ownership transfer.
     * Only the designated new owner can accept, with the code sent to their phone.
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.acceptOwnershipTransfer")
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> acceptOwnershipTransfer(
            @InputArgument String token,
            @InputArgument String confirmationCode) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(newOwnerId -> log.info("User {} accepting ownership transfer", newOwnerId))
                .flatMap(newOwnerId -> transferProcess.accept(token, newOwnerId, confirmationCode));
    }

    /**
     * Decline ownership transfer.
     * Only the designated new owner can decline.
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.declineOwnershipTransfer")
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> declineOwnershipTransfer(@InputArgument String token) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(newOwnerId -> log.info("User {} declining ownership transfer", newOwnerId))
                .flatMap(newOwnerId -> transferProcess.decline(token, newOwnerId));
    }
}
