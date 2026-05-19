package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.service.EscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Security Service for Event-based Access Control
 *
 * <p>Validates if a user is the organizer of an event or belongs to the same
 * organization as the organizer. Used in @PreAuthorize expressions to control
 * access to event-specific operations like viewing tickets, managing escrow,
 * and approving refunds.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Validates organization membership via Identity Service</li>
 *   <li>A04:2021 - Insecure Design: Defense in depth with centralized authorization</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Service("eventSecurityService")
@RequiredArgsConstructor
public class EventSecurityService {

    private final EscrowService escrowService;
    private final IdentityServiceClient identityServiceClient;

    /**
     * Check if the authenticated user is the organizer of the event OR belongs
     * to the same organization as the organizer.
     *
     * <p>Determines ownership by:</p>
     * <ol>
     *   <li>Finding the escrow account linked to the event</li>
     *   <li>Checking if user is the organizer (direct match)</li>
     *   <li>Checking if user belongs to the same organization as the organizer</li>
     * </ol>
     */
    public Mono<Boolean> isEventOrganizer(String eventId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("Not authenticated - denying event access");
            return Mono.just(false);
        }

        String userId = extractUserId(authentication);
        if (userId == null) {
            log.debug("Could not extract user ID from authentication");
            return Mono.just(false);
        }

        return escrowService.findByEventId(eventId)
                .flatMap(escrow -> {
                    String organizerId = escrow.getOrganizerId();

                    // Direct match - user is the organizer
                    if (organizerId.equals(userId)) {
                        log.debug("Direct organizer access allowed: userId={}, eventId={}", userId, eventId);
                        return Mono.just(true);
                    }

                    // Check if user belongs to the same organization as the organizer
                    return identityServiceClient.checkSameOrganization(userId, organizerId)
                            .map(result -> {
                                boolean allowed = result.sharesOrganization();
                                if (allowed) {
                                    log.debug("Team member access allowed: userId={}, organizerId={}, sharedOrg={}",
                                            userId, organizerId, result.sharedOrganizationId());
                                } else {
                                    log.debug("Access denied: userId={} does not share organization with organizerId={}",
                                            userId, organizerId);
                                }
                                return allowed;
                            })
                            .onErrorResume(e -> {
                                log.error("Error checking organization membership for event access: {}", e.getMessage());
                                return Mono.just(false);
                            });
                })
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("Error checking event organizer access: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Check if the authenticated user is the organizer by escrow account ID/number
     * OR belongs to the same organization as the organizer.
     */
    public Mono<Boolean> isEscrowOwner(String escrowAccountId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("Not authenticated - denying escrow access");
            return Mono.just(false);
        }

        String userId = extractUserId(authentication);
        if (userId == null) {
            log.debug("Could not extract user ID from authentication");
            return Mono.just(false);
        }

        return escrowService.findByAccountNumber(escrowAccountId)
                .flatMap(escrow -> {
                    String organizerId = escrow.getOrganizerId();

                    // Direct match - user is the organizer
                    if (organizerId.equals(userId)) {
                        log.debug("Direct escrow owner access allowed: userId={}, accountId={}", userId, escrowAccountId);
                        return Mono.just(true);
                    }

                    // Check if user belongs to the same organization as the organizer
                    return identityServiceClient.checkSameOrganization(userId, organizerId)
                            .map(result -> {
                                boolean allowed = result.sharesOrganization();
                                if (allowed) {
                                    log.debug("Team member escrow access allowed: userId={}, organizerId={}", userId, organizerId);
                                } else {
                                    log.debug("Escrow access denied: userId={} does not share organization with organizerId={}",
                                            userId, organizerId);
                                }
                                return allowed;
                            })
                            .onErrorResume(e -> {
                                log.error("Error checking organization membership for escrow access: {}", e.getMessage());
                                return Mono.just(false);
                            });
                })
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("Error checking escrow owner access: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    private String extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return null;
    }
}
