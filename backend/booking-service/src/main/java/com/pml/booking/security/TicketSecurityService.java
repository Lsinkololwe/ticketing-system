package com.pml.booking.security;

import com.pml.booking.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Security Service for Ticket Access Control
 *
 * Business Intent: Provides fine-grained access control for ticket operations.
 * Used in @PreAuthorize expressions to verify ticket ownership before allowing
 * operations like viewing, transferring, or refunding tickets.
 */
@Slf4j
@Service("ticketSecurityService")
@RequiredArgsConstructor
public class TicketSecurityService {

    private final TicketService ticketService;

    /**
     * Check if the authenticated user owns the ticket.
     */
    public Mono<Boolean> isTicketOwner(String ticketId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(false);
        }

        String userId = extractUserId(authentication);
        if (userId == null) {
            return Mono.just(false);
        }

        return ticketService.findById(ticketId)
                .map(ticket -> ticket.getBuyerId().equals(userId))
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    /**
     * Check if the authenticated user owns the ticket by ticket number.
     */
    public Mono<Boolean> isTicketOwnerByNumber(String ticketNumber, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(false);
        }

        String userId = extractUserId(authentication);
        if (userId == null) {
            return Mono.just(false);
        }

        return ticketService.findByTicketNumber(ticketNumber)
                .map(ticket -> ticket.getBuyerId().equals(userId))
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

    /** Holds the permission on the platform, or owns the ticket. */
    public Mono<Boolean> permissionOrTicketOwner(Authentication authentication, String permissionCode, String ticketId) {
        if (com.pml.shared.security.PermissionGuards.holdsPermission(authentication, permissionCode)) {
            return Mono.just(true);
        }
        return isTicketOwner(ticketId, authentication);
    }

    /** Holds the permission on the platform, or owns the ticket with this number. */
    public Mono<Boolean> permissionOrTicketOwnerByNumber(Authentication authentication, String permissionCode, String ticketNumber) {
        if (com.pml.shared.security.PermissionGuards.holdsPermission(authentication, permissionCode)) {
            return Mono.just(true);
        }
        return isTicketOwnerByNumber(ticketNumber, authentication);
    }
}
