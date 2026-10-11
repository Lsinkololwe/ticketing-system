package com.pml.catalog.security;

import com.pml.shared.security.tenancy.PlatformWideAccess;
import reactor.core.publisher.Mono;

/** Facts about the signed-in caller that a field resolver needs and a role annotation cannot express. */
public final class Callers {

    private Callers() {
    }

    /**
     * Whether the caller holds a platform-wide administrator role; false when nobody is signed in.
     *
     * <p>A capability gate, not a read across organizations, so it is answered by
     * {@link PlatformWideAccess#holdsPlatformRole()} without an audit row.
     */
    public static Mono<Boolean> platformAdministrator() {
        return PlatformWideAccess.holdsPlatformRole();
    }
}
