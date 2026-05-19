package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.web.graphql.dto.CreateBankAccountInput;
import com.pml.booking.web.graphql.dto.CreateBankAccountMutationResponse;
import com.pml.booking.web.graphql.dto.DeleteBankAccountMutationResponse;
import com.pml.booking.web.graphql.dto.UpdateBankAccountInput;
import com.pml.booking.web.graphql.dto.UpdateBankAccountMutationResponse;
import com.pml.booking.web.graphql.dto.VerifyBankAccountMutationResponse;
import com.pml.booking.service.BankAccountService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;

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
@RequiredArgsConstructor
public class BankAccountMutationResolver {

    private final BankAccountService bankAccountService;

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
    public Mono<CreateBankAccountMutationResponse> createBankAccount(
            @InputArgument CreateBankAccountInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(organizerId -> log.info("Creating bank account for organizer: {}", organizerId))
                .flatMap(organizerId -> bankAccountService.create(input, organizerId)
                        .map(account -> new CreateBankAccountMutationResponse(
                                true,
                                "Bank account created successfully",
                                account,
                                List.of(),
                                null
                        )))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(new CreateBankAccountMutationResponse(
                                false, "Authentication required", null, List.of("Please log in"), null)))
                .onErrorResume(e -> {
                    log.error("Create bank account failed: {}", e.getMessage());
                    return Mono.just(new CreateBankAccountMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
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
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @bankAccountSecurityService.isBankAccountOwner(#id, authentication)")
    public Mono<UpdateBankAccountMutationResponse> updateBankAccount(
            @InputArgument String id,
            @InputArgument UpdateBankAccountInput input
    ) {
        log.info("Updating bank account: {}", id);

        return bankAccountService.update(id, input)
                .map(account -> new UpdateBankAccountMutationResponse(
                        true,
                        "Bank account updated successfully",
                        account,
                        List.of(),
                        null
                ))
                .switchIfEmpty(Mono.just(new UpdateBankAccountMutationResponse(
                        false,
                        "Bank account not found",
                        null,
                        List.of("Bank account not found"),
                        null
                )))
                .onErrorResume(e -> {
                    log.error("Update bank account failed: {}", e.getMessage());
                    return Mono.just(new UpdateBankAccountMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
    }

    /**
     * Delete (soft-delete) a bank account.
     * Schema: deleteBankAccount(id: ID!): DeleteBankAccountMutationResponse!
     *
     * @param id The bank account ID to delete
     * @return DeleteBankAccountMutationResponse with success status
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @bankAccountSecurityService.isBankAccountOwner(#id, authentication)")
    public Mono<DeleteBankAccountMutationResponse> deleteBankAccount(
            @InputArgument String id
    ) {
        log.info("Deleting bank account: {}", id);

        return bankAccountService.delete(id)
                .map(deleted -> {
                    if (deleted) {
                        return new DeleteBankAccountMutationResponse(
                                true,
                                "Bank account deleted successfully",
                                List.of(),
                                null
                        );
                    } else {
                        return new DeleteBankAccountMutationResponse(
                                false,
                                "Bank account not found",
                                List.of("Bank account not found"),
                                null
                        );
                    }
                })
                .onErrorResume(e -> {
                    log.error("Delete bank account failed: {}", e.getMessage());
                    return Mono.just(new DeleteBankAccountMutationResponse(
                            false,
                            e.getMessage(),
                            List.of(e.getMessage()),
                            null
                    ));
                });
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
    public Mono<UpdateBankAccountMutationResponse> setDefaultBankAccount(
            @InputArgument String id
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(organizerId -> log.info("Setting default bank account: {} for organizer: {}", id, organizerId))
                .flatMap(organizerId -> bankAccountService.setAsDefault(id, organizerId)
                        .map(account -> new UpdateBankAccountMutationResponse(
                                true,
                                "Default bank account updated successfully",
                                account,
                                List.of(),
                                null
                        ))
                        .switchIfEmpty(Mono.just(new UpdateBankAccountMutationResponse(
                                false,
                                "Bank account not found",
                                null,
                                List.of("Bank account not found"),
                                null
                        ))))
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(new UpdateBankAccountMutationResponse(
                                false, "Authentication required", null, List.of("Please log in"), null)))
                .onErrorResume(e -> {
                    log.error("Set default bank account failed: {}", e.getMessage());
                    return Mono.just(new UpdateBankAccountMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
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
    public Mono<VerifyBankAccountMutationResponse> verifyBankAccount(
            @InputArgument String id
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(verifiedBy -> log.info("Verifying bank account: {} by: {}", id, verifiedBy))
                .flatMap(verifiedBy -> bankAccountService.verify(id, verifiedBy)
                        .map(account -> new VerifyBankAccountMutationResponse(
                                true,
                                "Bank account verified successfully",
                                account,
                                List.of(),
                                null
                        ))
                        .switchIfEmpty(Mono.just(new VerifyBankAccountMutationResponse(
                                false,
                                "Bank account not found",
                                null,
                                List.of("Bank account not found"),
                                null
                        ))))
                .onErrorResume(e -> {
                    log.error("Verify bank account failed: {}", e.getMessage());
                    return Mono.just(new VerifyBankAccountMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
    }
}
