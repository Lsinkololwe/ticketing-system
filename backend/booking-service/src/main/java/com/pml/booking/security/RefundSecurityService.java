package com.pml.booking.security;

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
    public Mono<Boolean> isRefundRequestOwner(String requestId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(false);
        }

        String userId = extractUserId(authentication);
        if (userId == null) {
            return Mono.just(false);
        }

        return refundService.findById(requestId)
                .map(refundRequest -> refundRequest.getRequestedBy().equals(userId))
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    private String extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return null;
    }
}
