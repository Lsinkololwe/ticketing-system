package com.pml.booking.security;

import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.Permission;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Who may create and change an organization's promo codes: whoever holds the promotion permission
 * there, which marketers, managers, administrators and owners carry and contributors do not.
 * Belonging to the organization is not enough, and platform staff act on every organization.
 *
 * <p>Callers resolve the code or the event through the tenant-scoped lookups first, so an
 * identifier from another organization has already read as unknown by the time this runs; what is
 * left to refuse is a member of the right organization who lacks the permission.
 */
@Component
@RequiredArgsConstructor
public class PromoCodeAccess {

    private final IdentityServiceClient identity;

    /** @return the organization id once the caller may manage its promo codes, else an {@code ACTOR_NOT_PERMITTED} refusal */
    public Mono<String> requireManage(String organizationId) {
        return CurrentTenantScope.get().flatMap(scope -> PlatformWideAccess
                .isPlatformWide(scope, PlatformWideAccess.Reason.PROMO_CODE_MANAGE)
                .flatMap(platformWide -> {
                    if (platformWide) {
                        return Mono.just(organizationId);
                    }
                    return SecurityContextUtils.requireCurrentUserId()
                            .flatMap(userId -> OrganizationPermissionCheck.holds(
                                    identity, userId, organizationId, null, Permission.PROMOTION_MANAGE))
                            .filter(Boolean::booleanValue)
                            .map(allowed -> organizationId);
                }))
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(
                        ErrorCode.ACTOR_NOT_PERMITTED, "requires " + Permission.PROMOTION_MANAGE.code())));
    }
}
