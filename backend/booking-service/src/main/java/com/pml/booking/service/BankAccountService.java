package com.pml.booking.service;

import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.web.graphql.dto.CreateBankAccountInput;
import com.pml.booking.web.graphql.dto.UpdateBankAccountInput;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for managing organizer bank accounts.
 *
 * <h2>Business Context</h2>
 * Bank accounts are required for organizers to receive payouts from ticket sales.
 * Each organizer can have multiple bank accounts but only one can be marked as default.
 * The default account receives automatic payouts when payout thresholds are met.
 *
 * <h2>Primary Users</h2>
 * <ul>
 *   <li><b>Organizers</b> - Manage their own bank accounts for receiving payouts</li>
 *   <li><b>Finance Team</b> - View and verify bank account details during payout processing</li>
 *   <li><b>Admin</b> - Full access for support and compliance purposes</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 * <ul>
 *   <li>Query resolvers use read methods for displaying account information</li>
 *   <li>Mutation resolvers use write methods for account management</li>
 *   <li>PayoutRequestFieldResolver uses findById for resolving bankAccount field</li>
 * </ul>
 *
 * @author Booking Service Team
 * @since 1.0
 */
public interface BankAccountService {

    /**
     * Retrieves all bank accounts belonging to an organizer.
     * Used in organizer dashboard to display linked payout accounts.
     *
     * @param organizerId The organizer's unique identifier
     * @return Flux of bank accounts for the organizer
     */
    Flux<BankAccount> findByOrganizerId(String organizerId);

    /**
     * Retrieves a bank account by its unique identifier.
     * Used by PayoutRequestFieldResolver to resolve the bankAccount field.
     *
     * @param id The bank account ID
     * @return Mono containing the bank account or empty if not found
     */
    Mono<BankAccount> findById(String id);

    /**
     * Retrieves the default bank account for automatic payouts.
     * Returns empty if no default is set (organizer must select one).
     *
     * @param organizerId The organizer's unique identifier
     * @return Mono containing the default bank account or empty
     */
    Mono<BankAccount> findDefaultByOrganizerId(String organizerId);

    /**
     * Creates a new bank account for an organizer.
     * If this is the first account, it is automatically set as default.
     * If isDefault is true, unsets any previously default account.
     *
     * @param input The bank account creation input
     * @param organizerId The organizer's unique identifier
     * @return Mono containing the created bank account
     */
    Mono<BankAccount> create(CreateBankAccountInput input, String organizerId);

    /**
     * Updates bank account details (account holder name, bank details, etc.).
     * Note: Some fields like account numbers may have restrictions on updates
     * depending on verification status.
     *
     * @param id The bank account ID to update
     * @param input The update input containing new values
     * @return Mono containing the updated bank account
     */
    Mono<BankAccount> update(String id, UpdateBankAccountInput input);

    /**
     * Sets a bank account as the default for payouts.
     * Unsets any previously default account for the same organizer.
     *
     * @param id The bank account ID to set as default
     * @param organizerId The organizer's unique identifier (for validation)
     * @return Mono containing the updated bank account
     */
    Mono<BankAccount> setAsDefault(String id, String organizerId);

    /**
     * Soft deletes a bank account by setting its status to DELETED.
     * Cannot delete if it is currently processing a payout.
     *
     * @param id The bank account ID to delete
     * @return Mono containing true if deleted successfully
     */
    Mono<Boolean> delete(String id);

    /**
     * Verifies a bank account (admin operation).
     * Sets the verification status and records who verified it.
     *
     * @param id The bank account ID to verify
     * @param verifiedBy The ID of the admin user who verified
     * @return Mono containing the verified bank account
     */
    Mono<BankAccount> verify(String id, String verifiedBy);
}
