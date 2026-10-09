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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

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

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<AuditLogEntryOffsetPage> auditLogs(@InputArgument AuditLogFilterInput filter,
                                                   @InputArgument OffsetPaginationInput pagination) {
        return reads.auditLogs(filter, pagination);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<List<GrowthPoint>> userGrowthSeries(@InputArgument Instant from, @InputArgument Instant to,
                                                    @InputArgument GrowthBuckets.Bucket bucket,
                                                    @InputArgument UserType role) {
        return reads.userGrowth(from, to, bucket == null ? GrowthBuckets.Bucket.DAY : bucket, role);
    }
}
