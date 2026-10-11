package com.pml.shared.security.tenancy;

import java.time.Instant;

/**
 * One platform-wide reach, as it is handed to a {@link PlatformWideAuditSink}.
 *
 * <p>Deliberately small: who, in which role, in which service, why, doing what, and when. There is
 * no field for a request body, a token or an argument value, so a sink cannot be handed one by
 * accident and an audit row cannot become a second copy of the data that was read.
 *
 * @param actorSub  the authenticated subject, or {@code system:...} for a workflow with no caller
 * @param role      the platform-wide authority the reach was made under, e.g. {@code ROLE_ADMIN}
 * @param service   the application name of the service that made the reach
 * @param reason    the fixed reason the call site declared; never caller-supplied
 * @param operation a fixed operation name where the call site has one, otherwise {@code null}
 * @param at        when the reach happened, from the service's {@code Clock}
 */
public record PlatformWideAuditRecord(
        String actorSub,
        String role,
        String service,
        PlatformWideAccess.Reason reason,
        String operation,
        Instant at) {
}
