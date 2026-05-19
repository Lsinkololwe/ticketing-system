package com.pml.booking.service;

import com.pml.booking.web.graphql.dto.*;
import reactor.core.publisher.Mono;

/**
 * Financial Report Service Interface
 *
 * Business Intent: Generates financial reports and statistics for
 * admin dashboards and organizer insights.
 */
public interface FinancialReportService {

    /**
     * Get transaction statistics with optional filtering.
     *
     * @param eventId Optional event filter
     * @param organizerId Optional organizer filter
     * @return Aggregated transaction statistics
     */
    Mono<TransactionStats> getTransactionStats(String eventId, String organizerId);

    /**
     * Generate a financial report for the specified period.
     *
     * @param filter Report filter criteria
     * @return Financial report with aggregated data
     */
    Mono<FinancialReport> generateFinancialReport(FinancialReportFilterInput filter);

    /**
     * Export a financial report in the specified format.
     *
     * @param filter Report filter criteria
     * @param format Export format
     * @return Export result with download URL
     */
    Mono<ReportExport> exportFinancialReport(FinancialReportFilterInput filter, ExportFormat format);

    /**
     * Export sales report for a specific event.
     *
     * @param eventId Event ID
     * @param format Export format
     * @return Export result with download URL
     */
    Mono<ReportExport> exportSalesReport(String eventId, ExportFormat format);
}
