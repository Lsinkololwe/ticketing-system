package com.pml.identity.account;

import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.tenancy.StaffRole;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * Who may hand out platform staff roles (ADMIN, SUPER_ADMIN, FINANCE, FINANCE_LEAD).
 *
 * <p>An administrator can manage buyers and organizers, but a staff role gives access to money and to
 * every account, so only a super administrator may create a staff user, grant a staff role, replace a
 * person's roles when staff roles are involved, or take one away. The check is made here on the
 * server from the caller's token, so a hand-made request from an administrator's session is refused
 * the same as a click in the console.</p>
 */
public final class StaffRolePolicy {

    /** The authority Spring Security derives from the super administrator role. */
    private static final String SUPER_ADMIN_AUTHORITY = StaffRole.SUPER_ADMIN.authority();

    private StaffRolePolicy() {
    }

    /** Completes when none of the roles is a staff role, or the caller is a super administrator. */
    public static Mono<Void> requireSuperAdminToTouch(Collection<UserType> roles) {
        boolean touchesStaffRole = roles != null && roles.stream().anyMatch(role -> role != null && role.isStaffRole());
        if (!touchesStaffRole) {
            return Mono.empty();
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication -> authentication != null && authentication.getAuthorities().stream()
                        .anyMatch(authority -> SUPER_ADMIN_AUTHORITY.equals(authority.getAuthority())))
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                        "only a super administrator may create or change staff roles")))
                .then();
    }
}
