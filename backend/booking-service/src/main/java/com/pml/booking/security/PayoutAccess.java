package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.shared.security.Permission;
import lombok.RequiredArgsConstructor;
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
@Component
@RequiredArgsConstructor
public class PayoutAccess {

    private final IdentityServiceClient identity;

    /** @return true only when identity authorizes {@code userId} to request payouts for the event */
    public Mono<Boolean> mayRequest(String userId, String organizationId, String eventId) {
        return holds(userId, organizationId, eventId, Permission.PAYOUT_REQUEST);
    }

    /**
     * @return true only when identity authorizes {@code userId} to add, verify and remove the
     * organization's payout accounts; the owner's payout switch does not decide this
     */
    public Mono<Boolean> mayManageBankAccounts(String userId, String organizationId) {
        return holds(userId, organizationId, null, Permission.BANK_MANAGE);
    }

    private Mono<Boolean> holds(String userId, String organizationId, String eventId, Permission permission) {
        return OrganizationPermissionCheck.holds(identity, userId, organizationId, eventId, permission);
    }
}
