package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.security.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Whether a person may request a payout from an event's money.
 *
 * <p>The money belongs to the organization, so the answer is the organization's: the caller holds
 * {@code payout:request} there (an administrator only when the owner has switched it on), an event
 * grant on the event decides alone when there is one, and the organization's own status must permit
 * payouts. Which member created the event does not matter. Identity decides; an outage denies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutAccess {

    private final IdentityServiceClient identity;

    /** @return true only when identity authorizes {@code userId} to request payouts for the event */
    public Mono<Boolean> mayRequest(String userId, String organizationId, String eventId) {
        if (userId == null || userId.isBlank() || organizationId == null || organizationId.isBlank()) {
            return Mono.just(false);
        }
        return identity.checkAuthorization(AuthorizationRequest.builder()
                        .userId(userId)
                        .organizationId(organizationId)
                        .eventId(eventId)
                        .requiredPermission(Permission.PAYOUT_REQUEST.code())
                        .build())
                .map(result -> result.isAuthorized())
                .onErrorResume(error -> {
                    log.error("Payout permission check failed for organization {}: {}", organizationId, error.toString());
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }
}
