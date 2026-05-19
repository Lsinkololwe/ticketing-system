package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.domain.model.PlatformAccount;
import com.pml.booking.service.PlatformAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Platform Account Operations.
 *
 * <p>Provides read access to platform-owned accounts:
 * <ul>
 *   <li>OPERATING: Main operational funds</li>
 *   <li>RESERVE: Reserve funds for chargebacks and disputes</li>
 *   <li>TAX_HOLDING: Tax withholding account</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PlatformAccountQueryResolver {

    private final PlatformAccountService platformAccountService;

    // ========================================================================
    // PLATFORM ACCOUNT QUERIES
    // ========================================================================

    /**
     * Get all platform accounts.
     * Schema: platformAccounts: [PlatformAccount!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<PlatformAccount>> platformAccounts() {
        log.debug("GraphQL query: platformAccounts");
        return platformAccountService.getAllAccounts().collectList();
    }

    /**
     * Get a platform account by ID.
     * Schema: platformAccount(id: ID!): PlatformAccount
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PlatformAccount> platformAccount(@InputArgument String id) {
        log.debug("GraphQL query: platformAccount(id={})", id);
        Objects.requireNonNull(id, "Platform account ID is required");
        return platformAccountService.findById(id);
    }

    /**
     * Get a platform account by type.
     * Schema: platformAccountByType(accountType: PlatformAccountType!): PlatformAccount
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PlatformAccount> platformAccountByType(@InputArgument PlatformAccountType accountType) {
        log.debug("GraphQL query: platformAccountByType({})", accountType);
        Objects.requireNonNull(accountType, "Platform account type is required");
        return platformAccountService.getByType(accountType);
    }
}
