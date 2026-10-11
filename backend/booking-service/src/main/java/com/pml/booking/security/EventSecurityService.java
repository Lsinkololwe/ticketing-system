package com.pml.booking.security;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.service.EscrowService;
import com.pml.shared.security.tenancy.CurrentTenantScope;
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

    /**
     * Whether the caller belongs to the organization the event's money belongs to.
     *
     * <p>The escrow account names the organization, and the caller's tenant scope says which
     * organizations they act for: a member of the organization passes, a platform administrator
     * passes, and everyone else is refused. Who created the event is not asked.
     */
    public Mono<Boolean> isEventOrganizer(String eventId, Authentication authentication) {
        if (!hasUser(authentication)) {
            return Mono.just(false);
        }
        return escrowService.findByEventId(eventId)
                .flatMap(EventSecurityService::callerActsFor)
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    log.error("Error checking event organizer access: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /** The same question, asked by escrow account number. */
    public Mono<Boolean> isEscrowOwner(String escrowAccountId, Authentication authentication) {
        if (!hasUser(authentication)) {
            return Mono.just(false);
        }
        return escrowService.findByAccountNumber(escrowAccountId)
                .flatMap(EventSecurityService::callerActsFor)
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    log.error("Error checking escrow owner access: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    private static Mono<Boolean> callerActsFor(EventEscrowAccount escrow) {
        String organizationId = escrow.getOrganizationId();
        if (organizationId == null || organizationId.isBlank()) {
            return Mono.just(false);
        }
        return CurrentTenantScope.get().map(scope -> scope.permits(organizationId));
    }

    private static boolean hasUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt
                && com.pml.shared.security.AccountIdentity.userIdOf(jwt) != null;
    }
}
