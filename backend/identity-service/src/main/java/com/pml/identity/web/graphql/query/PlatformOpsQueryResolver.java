package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.AlertSeverity;
import com.pml.identity.domain.enums.AlertStatus;
import com.pml.identity.domain.model.SystemAlert;
import com.pml.identity.domain.model.SystemAnnouncement;
import com.pml.identity.platform.AnnouncementRules;
import com.pml.identity.platform.PlatformOpsService;
import com.pml.identity.platform.ServiceHealth;
import com.pml.identity.platform.ServiceHealthService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;

/** Operational reads: service health, alerts, and the announcements administrators publish. */
@DgsComponent
@RequiredArgsConstructor
public class PlatformOpsQueryResolver {

    private final ServiceHealthService health;
    private final PlatformOpsService ops;

    /** Probes every dependency now and returns what each answered. Also opens or closes the matching alerts. */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Flux<ServiceHealth> serviceHealth() {
        return health.check();
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Flux<SystemAlert> systemAlerts(@InputArgument AlertStatus status, @InputArgument AlertSeverity severity) {
        return ops.alerts(status, severity);
    }

    /** Every announcement, newest first (administrators). */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Flux<SystemAnnouncement> systemAnnouncements() {
        return ops.all();
    }

    /** What the caller should see now: live announcements for the segments they belong to. */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<SystemAnnouncement> myAnnouncements() {
        return SecurityContextUtils.getAuthenticationContext()
                .flatMapMany(context -> ops.activeFor(AnnouncementRules.segmentsOf(
                        context.isOrganizer(), context.isAdmin() || context.hasRole("FINANCE")
                                || context.hasRole("FINANCE_LEAD") || context.hasRole("SUPER_ADMIN"))));
    }
}
