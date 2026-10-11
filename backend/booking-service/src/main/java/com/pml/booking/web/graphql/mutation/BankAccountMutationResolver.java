package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.pml.booking.security.TenantReads;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.web.graphql.dto.CreateBankAccountInput;
import com.pml.booking.web.graphql.dto.UpdateBankAccountInput;
import com.pml.booking.service.BankAccountService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.exception.AccountNotFoundException;

/**
 * GraphQL Mutation Resolver for Bank Account Operations.
 *
 * <h2>Business Intent</h2>
 * Handles organizer bank account lifecycle including creation, updates,
 * verification, and deletion. Bank accounts are required for organizers
 * to receive payouts from ticket sales.
 *
 * <h2>Architecture</h2>
 * This resolver delegates all business logic to {@link BankAccountService},
 * following the Controller → Service → Repository layered architecture pattern.
 *
 * @see BankAccountService
 * @author Booking Service Team
 * @since 1.0
 */
@Slf4j

@DgsComponent
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class BankAccountMutationResolver {

    private final BankAccountService bankAccountService;
    private final TenantReads tenantReads;
    private final com.pml.booking.workflow.bank.BankVerificationProcess bankVerificationProcess;
    private final com.pml.booking.security.BankAccountAccess access;

    /**
     * Create a new bank account for an organizer.
     * Schema: createBankAccount(input: CreateBankAccountInput!): CreateBankAccountMutationResponse!
     *
     * <h2>OWASP Compliance</h2>
     * <ul>
     *   <li>A01:2021 - Broken Access Control: organizerId extracted from JWT, not client input</li>
     * </ul>
     *
     * @param input The bank account creation input
     * @return CreateBankAccountMutationResponse with success status and created account
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<BankAccount> createBankAccount(
            @Valid @InputArgument CreateBankAccountInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(organizerId -> log.info("Creating bank account for organizer: {}", organizerId))
                .flatMap(organizerId -> bankAccountService.create(input, organizerId));
    }

    /**
     * Update an existing bank account.
     * Schema: updateBankAccount(id: ID!, input: UpdateBankAccountInput!): UpdateBankAccountMutationResponse!
     *
     * @param id The bank account ID to update
     * @param input The update input with new values
     * @return UpdateBankAccountMutationResponse with success status and updated account
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<BankAccount> updateBankAccount(
            @InputArgument String id,
            @Valid @InputArgument UpdateBankAccountInput input
    ) {
        log.info("Updating bank account: {}", id);

        return managed(id).then(Mono.defer(() -> bankAccountService.update(id, input)))
                .switchIfEmpty(Mono.error(new IllegalStateException("Bank account not found")));
    }

    /**
     * Delete (soft-delete) a bank account.
     * Schema: deleteBankAccount(id: ID!): DeleteBankAccountMutationResponse!
     *
     * @param id The bank account ID to delete
     * @return DeleteBankAccountMutationResponse with success status
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<String> deleteBankAccount(
            @InputArgument String id
    ) {
        log.info("Deleting bank account: {}", id);

        return managed(id).then(Mono.defer(() -> bankAccountService.delete(id)))
                .flatMap(deleted -> deleted
                        ? Mono.just(id)
                        : Mono.error(new AccountNotFoundException(
                                "bank account not found: " + id)));
    }

    /**
     * Set a bank account as the default for payouts.
     * Schema: setDefaultBankAccount(id: ID!): UpdateBankAccountMutationResponse!
     *
     * <h2>OWASP Compliance</h2>
     * <ul>
     *   <li>A01:2021 - Broken Access Control: organizerId extracted from JWT, not client input</li>
     * </ul>
     *
     * @param id The bank account ID to set as default
     * @return UpdateBankAccountMutationResponse with success status and updated account
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<BankAccount> setDefaultBankAccount(
            @InputArgument String id
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(organizerId -> log.info("Setting default bank account: {} for organizer: {}", id, organizerId))
                .flatMap(organizerId -> managed(id).then(Mono.defer(() -> bankAccountService.setAsDefault(id, organizerId)))
                        .switchIfEmpty(Mono.error(new IllegalStateException("Bank account not found"))));
    }

    /**
     * Verify a bank account (admin operation).
     * Schema: verifyBankAccount(id: ID!): VerifyBankAccountMutationResponse!
     *
     * <h2>OWASP Compliance</h2>
     * <ul>
     *   <li>A01:2021 - Broken Access Control: verifiedBy extracted from JWT, not client input</li>
     * </ul>
     *
     * @param id The bank account ID to verify
     * @return VerifyBankAccountMutationResponse with success status and verified account
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<BankAccount> verifyBankAccount(
            @InputArgument String id
    ) {
        // An account is verified by the micro-deposit its owner confirms; a platform
        // role starts that verification on the owner's behalf and cannot mark it verified by hand.
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorId -> log.info("Starting verification of bank account: {} by: {}", id, actorId))
                .flatMap(actorId -> bankVerificationProcess.start(id, null));
    }

    /** Sends the micro-deposit to the caller's own account. */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<BankAccount> startBankVerification(@InputArgument String id) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> managed(id).then(Mono.defer(() -> bankVerificationProcess.start(id, null))));
    }

    /** The owner confirms the amount that arrived; three wrong answers lock it for a day. */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<BankAccount> confirmBankVerification(@InputArgument String id, @InputArgument java.math.BigDecimal amount) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actorId -> managed(id)
                        .then(Mono.defer(() -> bankVerificationProcess.confirmAsMember(id, actorId, amount))));
    }

    /**
     * The account, once the caller may manage its organization's payout accounts. An account of another
     * organization, and one the caller may see but not manage, read as an account that does not exist.
     */
    private Mono<com.pml.booking.domain.model.BankAccount> managed(String id) {
        return tenantReads.bankAccountForCaller(id)
                .flatMap(account -> access.require(account.getOrganizationId()).thenReturn(account));
    }
}
