package com.pml.booking.security;

import com.pml.booking.domain.model.RefundRequest;

import com.pml.booking.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Security Service for Refund Request Access Control
 *
 * Business Intent: Provides fine-grained access control for refund operations.
 * Used in @PreAuthorize expressions to verify refund request ownership.
 */
@Slf4j
@Service("refundSecurityService")
@RequiredArgsConstructor
public class RefundSecurityService {

    private final RefundService refundService;

    /**
     * Check if the authenticated user is the requester of the refund.
     */
    /** Whether the caller requested the refund with this document id. */
    public Mono<Boolean> isRefundRequestOwner(String id, Authentication authentication) {
        return requestedByCaller(refundService.findById(id), authentication);
    }

    /** Whether the caller requested the refund with this business request id. */
    public Mono<Boolean> isRefundRequestOwnerByRequestId(String requestId, Authentication authentication) {
        return requestedByCaller(refundService.findByRequestId(requestId), authentication);
    }

    private Mono<Boolean> requestedByCaller(Mono<RefundRequest> refund, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(false);
        }
        String userId = extractUserId(authentication);
        if (userId == null) {
            return Mono.just(false);
        }
        return refund
                .map(refundRequest -> userId.equals(refundRequest.getRequestedBy()))
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    private String extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return com.pml.shared.security.AccountIdentity.userIdOf(jwt);
        }
        return null;
    }
}
