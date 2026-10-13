package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.AccountLifecycleService;
import com.pml.identity.service.AdminReadService;
import com.pml.identity.service.GrowthBuckets;
import com.pml.identity.web.graphql.dto.pagination.OffsetPaginationInput;
import com.pml.identity.web.graphql.dto.pagination.OffsetSlice;
import com.pml.identity.web.graphql.dto.pagination.UserOffsetPage;
import com.pml.identity.web.graphql.dto.platform.AuditLogEntryOffsetPage;
import com.pml.identity.web.graphql.dto.platform.AuditLogFilterInput;
import com.pml.identity.web.graphql.dto.platform.GrowthPoint;
import com.pml.shared.constants.UserType;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.identity.platform.FinanceAuditView;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.StaffRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Account reads: the caller's own sessions, and the administrator's staff list, audit trail and growth series. */
@DgsComponent
@RequiredArgsConstructor
public class AccountAdminQueryResolver {

    private final AccountLifecycleService lifecycle;
    private final AdminReadService reads;

    /** The caller's live sessions (the one making this request is marked {@code current}). */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<AccountLifecycleService.Session> mySessions() {
        return SecurityContextUtils.requireCurrentUserId().flatMapMany(subject ->
                SecurityContextUtils.getClaim("sid").defaultIfEmpty("")
                        .flatMapMany(sid -> lifecycle.sessions(subject, sid)));
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<UserOffsetPage> staffAccounts(@InputArgument String search, @InputArgument UserType role,
                                              @InputArgument OffsetPaginationInput pagination) {
        return reads.staff(search, role).map((List<User> all) -> {
            OffsetSlice<User> slice = OffsetSlice.of(all, pagination);
            return new UserOffsetPage(slice.content(), slice.pageInfo());
        });
    }

    /**
     * The audit trail. Administrators and super administrators read all of it. A finance officer
     * reads the money view only: platform-wide reaches whose reason is about money, by any actor,
     * and nothing else (see {@link FinanceAuditView}). Which of the two a caller gets is decided
     * here from their role, never from the filter they send.
     *
     * <p>Each read is itself a platform-wide reach and leaves one row (who read, in which role, when)
     * written before the page is returned. An administrator's read is {@code AUDIT_TRAIL_READ}; a
     * finance officer's is its own reason, {@code FINANCE_AUDIT_VIEW_READ}, so the trail shows apart
     * who looked at the whole record and who looked at the money view. Anyone else is refused and
     * leaves no row. A caller who holds both an administrator and a finance role reads everything.
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'FINANCE')")
    public Mono<AuditLogEntryOffsetPage> auditLogs(@InputArgument AuditLogFilterInput filter,
                                                   @InputArgument OffsetPaginationInput pagination) {
        return readsWholeTrail().flatMap(administrator -> administrator
                ? PlatformWideAccess.platformWide(PlatformWideAccess.Reason.AUDIT_TRAIL_READ, "auditLogs")
                        .then(Mono.defer(() -> reads.auditLogs(filter, pagination)))
                : PlatformWideAccess.requireStaff(PlatformWideAccess.Reason.FINANCE_AUDIT_VIEW_READ, "auditLogs",
                                Set.of(StaffRole.FINANCE))
                        .then(Mono.defer(() -> reads.financeAuditLogs(filter, pagination))));
    }

    /** Whether the caller holds an administrator role, which is what decides the whole trail over the money view. */
    private static Mono<Boolean> readsWholeTrail() {
        Set<String> administrators = Set.of(StaffRole.ADMIN.authority(), StaffRole.SUPER_ADMIN.authority());
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication() != null && context.getAuthentication().getAuthorities().stream()
                        .anyMatch(granted -> administrators.contains(granted.getAuthority())))
                .defaultIfEmpty(false);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<List<GrowthPoint>> userGrowthSeries(@InputArgument Instant from, @InputArgument Instant to,
                                                    @InputArgument GrowthBuckets.Bucket bucket,
                                                    @InputArgument UserType role) {
        return reads.userGrowth(from, to, bucket == null ? GrowthBuckets.Bucket.DAY : bucket, role);
    }
}
