package com.pml.identity.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.account.ContactChangeService;
import com.pml.identity.account.ContactChangeService.ContactChangeRequested;
import com.pml.identity.account.ContactChangeService.ContactChangeResult;
import com.pml.identity.account.ContactChangeService.ContactCodeSent;
import com.pml.identity.web.graphql.dto.ConfirmContactAddInput;
import com.pml.identity.web.graphql.dto.ConfirmContactChangeInput;
import com.pml.identity.web.graphql.dto.ConfirmContactRemovalInput;
import com.pml.identity.web.graphql.dto.RequestContactAddInput;
import com.pml.identity.web.graphql.dto.ResendContactCodeInput;
import com.pml.identity.web.graphql.dto.RequestContactChangeInput;
import com.pml.identity.web.graphql.dto.SetPrimaryContactInput;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;
import com.pml.shared.security.revocation.FailClosedOnRevocation;

/**
 * A buyer's own contacts (ET-IDN-004 R3, R5). There is no account argument anywhere: the account is the
 * token's own, through {@code AccountIdentity}, so one account cannot name another's contact. Nothing
 * returned or logged carries a raw contact or a code.
 */
@DgsComponent
@Validated
@RequiredArgsConstructor
public class ContactMutationResolver {

    private final ContactChangeService contacts;

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<ContactCodeSent> requestContactAdd(@Valid @InputArgument RequestContactAddInput input) {
        return account().flatMap(id -> contacts.requestAdd(id, input.type(), input.value(), input.regionHint()));
    }

    @DgsMutation
    @FailClosedOnRevocation("account.confirmContactAdd")
    @PreAuthorize("isAuthenticated()")
    public Mono<ContactChangeResult> confirmContactAdd(@Valid @InputArgument ConfirmContactAddInput input) {
        return account().flatMap(id -> contacts.confirmAdd(id, input.challengeId(), input.code()));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<ContactChangeRequested> requestContactChange(@Valid @InputArgument RequestContactChangeInput input) {
        return account().flatMap(id -> contacts.requestChange(id, input.contactId(), input.type(), input.value(), input.regionHint()));
    }

    @DgsMutation
    @FailClosedOnRevocation("account.confirmContactChange")
    @PreAuthorize("isAuthenticated()")
    public Mono<ContactChangeResult> confirmContactChange(@Valid @InputArgument ConfirmContactChangeInput input) {
        return account().flatMap(id -> contacts.confirmChange(id, input.changeId(), input.newContactCode(), input.currentContactCode()));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> cancelContactChange(@InputArgument String changeId) {
        return account().flatMap(id -> contacts.cancelChange(id, changeId));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<ContactCodeSent> requestContactRemoval(@InputArgument String contactId) {
        return account().flatMap(id -> contacts.requestRemoval(id, contactId));
    }

    @DgsMutation
    @FailClosedOnRevocation("account.confirmContactRemoval")
    @PreAuthorize("isAuthenticated()")
    public Mono<ContactChangeResult> confirmContactRemoval(@Valid @InputArgument ConfirmContactRemovalInput input) {
        return account().flatMap(id -> contacts.confirmRemoval(id, input.challengeId(), input.code()));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<ContactCodeSent> requestPrimaryContact(@InputArgument String contactId) {
        return account().flatMap(id -> contacts.requestPrimary(id, contactId));
    }

    @DgsMutation
    @FailClosedOnRevocation("account.setPrimaryContact")
    @PreAuthorize("isAuthenticated()")
    public Mono<ContactChangeResult> setPrimaryContact(@Valid @InputArgument SetPrimaryContactInput input) {
        return account().flatMap(id -> contacts.setPrimary(id, input.contactId(), input.challengeId(), input.code()));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<ContactCodeSent> resendContactCode(@Valid @InputArgument ResendContactCodeInput input) {
        return account().flatMap(id -> contacts.resend(id, input.challengeId(), input.changeId(),
                input.target() == null ? null : input.target().name()));
    }

    /** The token's own account: the {@code accountId} claim, else {@code sub}. */
    static Mono<String> account() {
        return SecurityContextUtils.getCurrentUserId()
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_AUTHENTICATED, "sign in")));
    }
}
