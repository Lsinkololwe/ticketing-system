package com.pml.identity.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.identity.account.AccountStates;
import com.pml.identity.account.ContactService;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * The fields of {@code User} that are computed or protected rather than read off the document.
 *
 * <p>{@code status} is derived because documents written before the account backfill carry only the
 * legacy status. {@code contacts} are the person's own business: only the account's holder or an
 * administrator may read them, and even then only the masked form - the value is never in the schema.
 */
@DgsComponent
@RequiredArgsConstructor
public class UserAccountFields {

    private final ContactService contactService;
    private final com.pml.identity.service.AccountLifecycleService lifecycle;

    @DgsData(parentType = "User", field = "status")
    public AccountState status(DgsDataFetchingEnvironment environment) {
        User user = environment.getSource();
        return AccountStates.of(user);
    }

    @DgsData(parentType = "User", field = "contacts")
    public Mono<List<Contact>> contacts(DgsDataFetchingEnvironment environment) {
        User user = environment.getSource();
        return SecurityContextUtils.getAuthenticationContext()
                .flatMap(context -> {
                    boolean holder = context.getUserId() != null
                            && (context.getUserId().equals(user.getId()) || context.getUserId().equals(user.getKeycloakUserId()));
                    if (!holder && !context.isAdmin()) {
                        return Mono.<List<Contact>>error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                                "contacts are visible to their holder and to administrators"));
                    }
                    return contactService.contactsOf(user.getId()).collectList();
                })
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_AUTHENTICATED, "sign in to see contacts")));
    }

    /** Why an administrator suspended the account. Administrators only; null for anyone else. */
    @DgsData(parentType = "User", field = "suspendReason")
    public Mono<String> suspendReason(DgsDataFetchingEnvironment environment) {
        return reasonForAdmins(environment.getSource(), AccountState.SUSPENDED);
    }

    /** Why the account is locked (a lock is a suspension with a reason). Administrators only. */
    @DgsData(parentType = "User", field = "lockReason")
    public Mono<String> lockReason(DgsDataFetchingEnvironment environment) {
        User user = environment.getSource();
        return user.isLocked() ? reasonForAdmins(user, AccountState.SUSPENDED) : Mono.empty();
    }

    private Mono<String> reasonForAdmins(User user, AccountState required) {
        if (AccountStates.of(user) != required) {
            return Mono.empty();
        }
        return SecurityContextUtils.getAuthenticationContext()
                .filter(SecurityContextUtils.AuthenticationContext::isAdmin)
                .flatMap(context -> lifecycle.suspensionReason(user.getId()));
    }
}
