package com.pml.identity.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OwnershipTransferService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Mutation Resolver for Ownership Transfer operations.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OwnershipTransferMutationResolver {

    private final OwnershipTransferService transferService;
    private final OrganizationMemberService memberService;

    /**
     * Initiate ownership transfer.
     * Only the current owner can initiate a transfer.
     */
    @DgsMutation
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

                            return transferService.initiate(organizationId, currentOwnerId, newOwnerId, reason);
                        }));
    }

    /**
     * Cancel ownership transfer.
     * Only the current owner can cancel.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> cancelOwnershipTransfer(@InputArgument String organizationId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(currentOwnerId -> log.info("User {} cancelling ownership transfer for organization {}", currentOwnerId, organizationId))
                .flatMap(currentOwnerId -> transferService.cancel(organizationId, currentOwnerId));
    }

    /**
     * Accept ownership transfer.
     * Only the designated new owner can accept.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> acceptOwnershipTransfer(
            @InputArgument String token,
            @InputArgument String confirmationCode) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(newOwnerId -> log.info("User {} accepting ownership transfer", newOwnerId))
                .flatMap(newOwnerId -> transferService.accept(token, newOwnerId, confirmationCode));
    }

    /**
     * Decline ownership transfer.
     * Only the designated new owner can decline.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> declineOwnershipTransfer(@InputArgument String token) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(newOwnerId -> log.info("User {} declining ownership transfer", newOwnerId))
                .flatMap(newOwnerId -> transferService.decline(token, newOwnerId));
    }
}
