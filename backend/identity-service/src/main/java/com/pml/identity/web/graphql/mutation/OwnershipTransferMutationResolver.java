package com.pml.identity.web.graphql.mutation;

import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OwnershipTransferService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String currentOwnerId = jwt.getSubject();
        log.info("User {} initiating ownership transfer of organization {} to user {}",
                currentOwnerId, organizationId, newOwnerId);

        // Verify current user is the owner
        return memberService.findOwner(organizationId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organization owner not found")))
                .flatMap(owner -> {
                    if (!owner.getUserId().equals(currentOwnerId)) {
                        return Mono.error(new IllegalStateException("Only the owner can initiate a transfer"));
                    }

                    return transferService.initiate(organizationId, currentOwnerId, newOwnerId, reason);
                });
    }

    /**
     * Cancel ownership transfer.
     * Only the current owner can cancel.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> cancelOwnershipTransfer(
            @InputArgument String organizationId,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String currentOwnerId = jwt.getSubject();
        log.info("User {} cancelling ownership transfer for organization {}", currentOwnerId, organizationId);

        return transferService.cancel(organizationId, currentOwnerId);
    }

    /**
     * Accept ownership transfer.
     * Only the designated new owner can accept.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> acceptOwnershipTransfer(
            @InputArgument String token,
            @InputArgument String confirmationCode,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String newOwnerId = jwt.getSubject();
        log.info("User {} accepting ownership transfer", newOwnerId);

        return transferService.accept(token, newOwnerId, confirmationCode);
    }

    /**
     * Decline ownership transfer.
     * Only the designated new owner can decline.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> declineOwnershipTransfer(
            @InputArgument String token,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String newOwnerId = jwt.getSubject();
        log.info("User {} declining ownership transfer", newOwnerId);

        return transferService.decline(token, newOwnerId);
    }
}
