package com.pml.booking.security;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Who may add, see and manage an organization's payout accounts.
 *
 * <p>A payout account belongs to the organization. The person who may change where its money goes is
 * whoever holds the payout permission there (an administrator only when the owner has switched it
 * on), which is the same decision that lets them request a payout: adding a destination is the step
 * where someone could send the money to themselves, so it is held to no lower bar than withdrawing.
 * Platform staff act on every organization. Every refusal reads as an unknown account, so an id is
 * never confirmed to someone who may not manage it.
 */
@Component
@RequiredArgsConstructor
public class BankAccountAccess {

    private final PayoutAccess payoutAccess;

    /** @return the organization id once the caller may manage its payout accounts, else an unknown-account refusal */
    public Mono<String> require(String organizationId) {
        return CurrentTenantScope.get().flatMap(scope -> PlatformWideAccess
                .isPlatformWide(scope, PlatformWideAccess.Reason.BANK_ACCOUNT_MANAGE)
                .flatMap(platformWide -> {
                    if (platformWide) {
                        return Mono.just(organizationId);
                    }
                    if (!scope.permits(organizationId)) {
                        return Mono.<String>empty();
                    }
                    return SecurityContextUtils.requireCurrentUserId()
                            .flatMap(userId -> payoutAccess.mayRequest(userId, organizationId, null))
                            .filter(Boolean::booleanValue)
                            .map(allowed -> organizationId);
                })).switchIfEmpty(Mono.error(() -> TenantBoundary.refuse(ErrorCode.BANK_ACCOUNT_UNKNOWN, "bank account")));
    }

    /** Whether the caller may manage the organization's payout accounts, without raising a refusal. */
    public Mono<Boolean> may(String organizationId) {
        return require(organizationId).map(id -> true).onErrorReturn(false);
    }
}
