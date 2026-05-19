package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.service.JournalService;
import com.pml.booking.web.graphql.dto.JournalEntryFilterInput;
import com.pml.booking.web.graphql.dto.JournalEntryOffsetPage;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Journal Entry Operations.
 *
 * <p>Provides read access to the double-entry ledger journal entries.
 * Each journal entry contains balanced debits and credits affecting
 * accounts in the Chart of Accounts.</p>
 *
 * <h2>Journal Entry Lifecycle</h2>
 * <ul>
 *   <li>DRAFT: Entry created but not yet finalized</li>
 *   <li>POSTED: Entry finalized and affects account balances</li>
 *   <li>REVERSED: Entry has been reversed by another entry</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class JournalEntryQueryResolver {

    private final JournalService journalService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get a journal entry by ID.
     * Schema: journalEntry(id: ID!): JournalEntry
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<JournalEntry> journalEntry(@InputArgument String id) {
        log.debug("GraphQL query: journalEntry(id={})", id);
        Objects.requireNonNull(id, "Journal entry ID is required");
        return journalService.findById(id);
    }

    /**
     * Get a journal entry by entry number.
     * Schema: journalEntryByNumber(entryNumber: String!): JournalEntry
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<JournalEntry> journalEntryByNumber(@InputArgument String entryNumber) {
        log.debug("GraphQL query: journalEntryByNumber({})", entryNumber);
        Objects.requireNonNull(entryNumber, "Entry number is required");
        return journalService.findByEntryNumber(entryNumber);
    }

    /**
     * Get journal entries by correlation ID.
     * Schema: journalEntriesByCorrelationId(correlationId: String!): [JournalEntry!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<JournalEntry>> journalEntriesByCorrelationId(@InputArgument String correlationId) {
        log.debug("GraphQL query: journalEntriesByCorrelationId({})", correlationId);
        Objects.requireNonNull(correlationId, "Correlation ID is required");
        return journalService.findByCorrelationId(correlationId).collectList();
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES
    // ========================================================================

    /**
     * Get journal entries with filtering and offset pagination.
     * Schema: journalEntriesOffsetPagination(filter: JournalEntryFilterInput, pagination: OffsetPaginationInput): JournalEntryOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<JournalEntryOffsetPage> journalEntriesOffsetPagination(
            @InputArgument JournalEntryFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: journalEntriesOffsetPagination");

        Flux<JournalEntry> entryFlux = getFilteredEntries(filter);
        return entryFlux.collectList()
                .map(entries -> buildOffsetPage(entries, pagination));
    }

    /**
     * Get journal entries affecting a specific account.
     * Schema: journalEntriesByAccountCode(accountCode: String!, pagination: OffsetPaginationInput): JournalEntryOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<JournalEntryOffsetPage> journalEntriesByAccountCode(
            @InputArgument String accountCode,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: journalEntriesByAccountCode({})", accountCode);
        Objects.requireNonNull(accountCode, "Account code is required");

        return journalService.findByAccountCode(accountCode)
                .collectList()
                .map(entries -> buildOffsetPage(entries, pagination));
    }

    /**
     * Get pending (draft) journal entries.
     * Schema: pendingJournalEntriesOffsetPagination(pagination: OffsetPaginationInput): JournalEntryOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<JournalEntryOffsetPage> pendingJournalEntriesOffsetPagination(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: pendingJournalEntriesOffsetPagination");

        return journalService.findByStatus(JournalEntryStatus.DRAFT)
                .collectList()
                .map(entries -> buildOffsetPage(entries, pagination));
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get filtered journal entries based on filter input.
     */
    private Flux<JournalEntry> getFilteredEntries(JournalEntryFilterInput filter) {
        if (filter == null || !filter.hasFilters()) {
            return journalService.findAll();
        }

        // Apply filters based on available criteria
        if (filter.correlationId() != null) {
            return journalService.findByCorrelationId(filter.correlationId());
        }

        if (filter.accountCode() != null) {
            return journalService.findByAccountCode(filter.accountCode());
        }

        if (filter.status() != null) {
            return journalService.findByStatus(filter.status());
        }

        if (filter.startDate() != null && filter.endDate() != null) {
            return journalService.findByDateRange(
                    filter.startDate().toLocalDate(),
                    filter.endDate().toLocalDate()
            );
        }

        return journalService.findAll();
    }

    /**
     * Build JournalEntryOffsetPage from a list of entries.
     */
    private JournalEntryOffsetPage buildOffsetPage(
            List<JournalEntry> allEntries,
            OffsetPaginationInput pagination
    ) {
        OffsetPaginationInput p = pagination != null ? pagination : new OffsetPaginationInput(1, 20);
        int limit = p.getLimit();
        int offset = p.getOffset();

        int totalCount = allEntries.size();
        int totalPages = (int) Math.ceil((double) totalCount / limit);
        boolean hasNextPage = (offset + limit) < totalCount;
        boolean hasPreviousPage = p.page() > 1;

        List<JournalEntry> paginatedData = allEntries.stream()
                .skip(offset)
                .limit(limit)
                .toList();

        PaginationInfo paginationInfo = new PaginationInfo(
                totalCount,
                limit,
                p.page(),
                totalPages,
                hasNextPage,
                hasPreviousPage
        );

        return new JournalEntryOffsetPage(paginatedData, paginationInfo);
    }
}
