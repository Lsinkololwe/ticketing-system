package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query Resolver for Bank Account Operations.
 *
 * <h2>Business Intent</h2>
 * Provides read-only queries for organizer bank account data. Bank accounts
 * are required for organizers to receive payouts from ticket sales.
 *
 * <h2>Architecture</h2>
 * This resolver delegates all business logic to {@link BankAccountService},
 * following the Controller → Service → Repository layered architecture pattern.
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Uses OrganizationSecurityService for multi-tenant isolation</li>
 *   <li>Organization membership validated via Identity Service, not simple userId equality</li>
 * </ul>
 *
 * @see BankAccountService
 * @author Booking Service Team
 * @since 1.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class BankAccountQueryResolver {

    private final BankAccountService bankAccountService;

    /**
     * Get all bank accounts for an organizer.
     * Schema: bankAccountsByOrganizer(organizerId: String!): [BankAccount!]!
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService to validate
     * that the requesting user is either the organizer or a team member with access.</p>
     *
     * @param organizerId The organizer's unique identifier
     * @return Flux of bank accounts belonging to the organizer
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @organizationSecurityService.isOrganizerOrTeamMember(#organizerId, authentication)")
    public Flux<BankAccount> bankAccountsByOrganizer(@InputArgument String organizerId) {
        log.debug("GraphQL query: bankAccountsByOrganizer(organizerId={})", organizerId);
        return bankAccountService.findByOrganizerId(organizerId);
    }

    /**
     * Get a bank account by ID.
     * Schema: bankAccount(id: ID!): BankAccount
     *
     * @param id The bank account's unique identifier
     * @return Mono containing the bank account or empty if not found
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @bankAccountSecurityService.isBankAccountOwner(#id, authentication)")
    public Mono<BankAccount> bankAccount(@InputArgument String id) {
        log.debug("GraphQL query: bankAccount(id={})", id);
        return bankAccountService.findById(id);
    }

    /**
     * Get the default bank account for an organizer.
     * Schema: defaultBankAccount(organizerId: String!): BankAccount
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService to validate
     * that the requesting user is either the organizer or a team member with access.</p>
     *
     * @param organizerId The organizer's unique identifier
     * @return Mono containing the default bank account or empty if none set
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @organizationSecurityService.isOrganizerOrTeamMember(#organizerId, authentication)")
    public Mono<BankAccount> defaultBankAccount(@InputArgument String organizerId) {
        log.debug("GraphQL query: defaultBankAccount(organizerId={})", organizerId);
        return bankAccountService.findDefaultByOrganizerId(organizerId);
    }
}
