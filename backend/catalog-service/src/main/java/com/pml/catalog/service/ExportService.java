package com.pml.catalog.service;

import com.pml.catalog.domain.enums.ExportFormat;
import com.pml.catalog.dto.EventFilterInput;
import com.pml.catalog.dto.ReportExportDto;
import reactor.core.publisher.Mono;

/**
 * Service for exporting event data in various formats.
 * Supports CSV, PDF, Excel, and JSON export formats.
 */
public interface ExportService {

    /**
     * Export data for a single event.
     *
     * @param eventId the event ID to export
     * @param format the desired export format
     * @return export result with download URL
     */
    Mono<ReportExportDto> exportEventData(String eventId, ExportFormat format);

    /**
     * Export multiple events based on filter criteria.
     *
     * @param filter the filter criteria for selecting events
     * @param format the desired export format
     * @return export result with download URL
     */
    Mono<ReportExportDto> exportEventsReport(EventFilterInput filter, ExportFormat format);
}
