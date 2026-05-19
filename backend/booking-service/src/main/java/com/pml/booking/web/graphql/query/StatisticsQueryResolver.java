package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.service.FinancialReportService;
import com.pml.booking.web.graphql.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query Resolver for Transaction Statistics and Financial Reports
 *
 * Business Intent: Provides aggregated statistics for financial transactions
 * and comprehensive financial reports for dashboards and business intelligence.
 * Access is restricted to ADMIN and FINANCE roles.
 *
 * NOTE: Ticket statistics are handled by TicketQueryResolver.
 * Payout request statistics are handled by PayoutRequestQueryResolver.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class StatisticsQueryResolver {

    private final FinancialReportService financialReportService;

    /**
     * Get comprehensive transaction statistics with optional filtering.
     * Schema: transactionStats(eventId: ID, organizerId: ID): TransactionStats!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<TransactionStats> transactionStats(
            @InputArgument String eventId,
            @InputArgument String organizerId
    ) {
        log.debug("GraphQL query: transactionStats(eventId={}, organizerId={})", eventId, organizerId);
        return financialReportService.getTransactionStats(eventId, organizerId);
    }

    /**
     * Generate a financial report for the specified period and filters.
     * Schema: financialReport(filter: FinancialReportFilterInput!): FinancialReport!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<FinancialReport> financialReport(@InputArgument FinancialReportFilterInput filter) {
        log.debug("GraphQL query: financialReport(startDate={}, endDate={})",
                filter.startDate(), filter.endDate());
        return financialReportService.generateFinancialReport(filter);
    }

    /**
     * Export a financial report in the specified format.
     * Schema: exportFinancialReport(filter: FinancialReportFilterInput!, format: ExportFormat!): ReportExport!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<ReportExport> exportFinancialReport(
            @InputArgument FinancialReportFilterInput filter,
            @InputArgument ExportFormat format
    ) {
        log.debug("GraphQL query: exportFinancialReport(format={})", format);
        return financialReportService.exportFinancialReport(filter, format);
    }

    /**
     * Export a sales report for a specific event.
     * Schema: exportSalesReport(eventId: ID!, format: ExportFormat!): ReportExport!
     *
     * <p>OWASP A01:2021 Compliance: Uses EventSecurityService to validate
     * that the requesting user is the event organizer or has admin/finance role.</p>
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<ReportExport> exportSalesReport(
            @InputArgument String eventId,
            @InputArgument ExportFormat format
    ) {
        log.debug("GraphQL query: exportSalesReport(eventId={}, format={})", eventId, format);
        return financialReportService.exportSalesReport(eventId, format);
    }
}
