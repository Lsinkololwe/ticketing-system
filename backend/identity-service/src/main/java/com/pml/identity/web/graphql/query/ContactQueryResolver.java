package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.pml.identity.account.ContactChangeService;
import com.pml.identity.account.ContactChangeService.MyContacts;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/** The caller's own contacts, masked, and the change waiting for its codes. */
@DgsComponent
@RequiredArgsConstructor
public class ContactQueryResolver {

    private final ContactChangeService contacts;

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<MyContacts> myContacts() {
        return SecurityContextUtils.getCurrentUserId()
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_AUTHENTICATED, "sign in")))
                .flatMap(contacts::myContacts);
    }
}
