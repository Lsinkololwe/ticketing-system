package com.pml.identity.web.graphql.mutation;

import org.springframework.validation.annotation.Validated;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.AccountLifecycleService;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * What a signed-in person does to their own account. The subject always comes from the token;
 * no argument names the account, so there is nothing to point at somebody else's.
 */
@DgsComponent
@Validated
@RequiredArgsConstructor
public class AccountSelfMutationResolver {

    private final AccountLifecycleService lifecycle;

    /** Asks for the account to be deleted after the grace period; cancellable until then. */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("account.requestDeletion")
    public Mono<User> requestAccountDeletion(@InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(subject -> lifecycle.requestDeletion(subject, reason));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("account.cancelDeletion")
    public Mono<User> cancelAccountDeletion() {
        return SecurityContextUtils.requireCurrentUserId().flatMap(lifecycle::cancelDeletion);
    }

    /** Ends one of the caller's own sessions. */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("account.revokeSession")
    public Mono<Boolean> revokeSession(@InputArgument String sessionId) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(subject -> lifecycle.revokeSession(subject, sessionId));
    }
}
