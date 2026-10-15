package com.pml.identity.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.pml.shared.security.RequiresPermission;
import com.pml.shared.security.Permission;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import jakarta.validation.Valid;
import com.pml.identity.security.revocation.AccessRevocationService;
import com.pml.identity.security.revocation.RevocationRecord;
import com.pml.identity.web.graphql.dto.pagination.OffsetPaginationInput;
import com.pml.identity.web.graphql.dto.platform.RevocationRecordOffsetPage;
import com.pml.identity.web.graphql.dto.platform.RevocationRecordView;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.pml.shared.security.revocation.RevocationType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

/**
 * Cutting access on purpose: an administrator revoking a token, a session or a person, a person
 * signing out of their other devices, and the log of what is still revoked.
 *
 * <p>An administrator's revocation must carry a written reason of at least twenty characters;
 * the service refuses a shorter one as a malformed command.</p>
 */
@DgsComponent
@Validated
@RequiredArgsConstructor
public class AccessRevocationResolver {

    private final AccessRevocationService revocations;

    @DgsMutation
    @RequiresPermission(Permission.SESSION_MANAGE)
    @FailClosedOnRevocation("admin.revokeToken")
    public Mono<RevocationRecordView> revokeToken(@InputArgument String jti, @InputArgument String reason) {
        return actor().flatMap(admin -> revocations.revokeManually(RevocationType.TOKEN, jti, reason, admin))
                .map(AccessRevocationResolver::view);
    }

    @DgsMutation
    @RequiresPermission(Permission.SESSION_MANAGE)
    @FailClosedOnRevocation("admin.revokeSessionAccess")
    public Mono<RevocationRecordView> revokeSessionAccess(@InputArgument String sid,
                                                          @InputArgument String reason) {
        return actor().flatMap(admin -> revocations.revokeManually(RevocationType.SESSION, sid, reason, admin))
                .map(AccessRevocationResolver::view);
    }

    @DgsMutation
    @RequiresPermission(Permission.SESSION_MANAGE)
    @FailClosedOnRevocation("admin.revokeUserAccess")
    public Mono<RevocationRecordView> revokeUserAccess(@InputArgument String userId,
                                                       @InputArgument String reason) {
        return actor().flatMap(admin -> revocations.revokeAccountManually(userId, reason, admin))
                .map(AccessRevocationResolver::view);
    }

    /** The subject and the current session come from the token; nothing names another account. */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("account.signOutEverywhere")
    public Mono<Integer> signOutEverywhere() {
        return SecurityContextUtils.getJwt().flatMap(jwt -> revocations
                .signOutOtherSessions(jwt.getSubject(), jwt.getClaimAsString("sid"))
                .map(Long::intValue));
    }

    @DgsQuery
    @RequiresPermission(Permission.SESSION_MANAGE)
    public Mono<RevocationRecordOffsetPage> activeRevocations(@InputArgument RevocationType type,
                                                              @Valid @InputArgument OffsetPaginationInput pagination) {
        return revocations.active(type, pagination);
    }

    private static Mono<String> actor() {
        return SecurityContextUtils.getCurrentUserId().defaultIfEmpty("system");
    }

    private static RevocationRecordView view(RevocationRecord record) {
        return new RevocationRecordView(record.getId(), record.getType(), record.getValue(),
                record.getReason(), record.getTrigger(), record.getRevokedBy(), record.getRevokedAt(),
                record.getExpiresAt());
    }
}
