package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.enums.ExportFormat;
import com.pml.catalog.dto.EventFilterInput;
import com.pml.catalog.dto.ReportExportDto;
import com.pml.catalog.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query Resolver for Export operations.
 * Admin-only for platform reporting and compliance.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ExportQueryResolver {

    private final ExportService exportService;

    /**
     * Export data for a single event.
     * Used for compliance, legal requests, and detailed reporting.
     *
     * @param eventId the event ID to export
     * @param format the desired export format (CSV, PDF, EXCEL, JSON)
     * @return export result with download URL
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReportExportDto> exportEventData(
            @InputArgument String eventId,
            @InputArgument ExportFormat format) {
        log.info("GraphQL query: exportEventData(eventId={}, format={})", eventId, format);
        return exportService.exportEventData(eventId, format);
    }

    /**
     * Export multiple events based on filter criteria.
     * Used for platform analytics and bulk reporting.
     *
     * @param filter the filter criteria for selecting events
     * @param format the desired export format (CSV, PDF, EXCEL, JSON)
     * @return export result with download URL
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReportExportDto> exportEventsReport(
            @InputArgument EventFilterInput filter,
            @InputArgument ExportFormat format) {
        log.info("GraphQL query: exportEventsReport(filter={}, format={})", filter, format);
        return exportService.exportEventsReport(filter, format);
    }
}
