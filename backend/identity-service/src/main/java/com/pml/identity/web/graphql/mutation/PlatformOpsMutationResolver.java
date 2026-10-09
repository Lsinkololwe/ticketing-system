package com.pml.identity.web.graphql.mutation;

import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.SystemAlert;
import com.pml.identity.domain.model.SystemAnnouncement;
import com.pml.identity.platform.PlatformOpsService;
import com.pml.identity.web.graphql.dto.platform.BroadcastInput;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/** Acknowledging alerts and publishing announcements; administrators only, audited. */
@DgsComponent
@Validated
@RequiredArgsConstructor
public class PlatformOpsMutationResolver {

    private final PlatformOpsService ops;

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.acknowledgeAlert")
    public Mono<SystemAlert> acknowledgeAlert(@InputArgument String id) {
        return actor().flatMap(adminId -> ops.acknowledge(id, adminId));
    }

    /** Publishes an announcement now, or schedules it with a future {@code startsAt}. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.broadcastNotification")
    public Mono<SystemAnnouncement> broadcastNotification(@Valid @InputArgument BroadcastInput input) {
        return actor().flatMap(adminId -> ops.broadcast(input.title(), input.message(), input.segment(),
                input.severity(), input.startsAt(), input.endsAt(), adminId));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.cancelAnnouncement")
    public Mono<SystemAnnouncement> cancelAnnouncement(@InputArgument String id) {
        return actor().flatMap(adminId -> ops.cancel(id, adminId));
    }

    private static Mono<String> actor() {
        return SecurityContextUtils.getCurrentUserId().defaultIfEmpty("system");
    }
}
