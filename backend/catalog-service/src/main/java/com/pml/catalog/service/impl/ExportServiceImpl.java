package com.pml.catalog.service.impl;

import com.pml.shared.constants.PlatformTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pml.catalog.domain.enums.ExportFormat;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.web.graphql.dto.EventFilterInput;
import com.pml.catalog.web.graphql.dto.ReportExportDto;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.ExportService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Implementation of ExportService.
 * Generates export files in CSV, JSON, PDF, and Excel formats.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final EventRepository eventRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final com.pml.catalog.service.EventAdminFilter adminFilter;
    private final org.springframework.data.mongodb.core.ReactiveMongoTemplate mongoTemplate;

    /** The largest event export; a filter selecting more is refused. */
    static final int MAX_EXPORT_ROWS = 10_000;

    @Value("${catalog.export.base-url:http://localhost:8085/api/exports}")
    private String exportBaseUrl;

    @Value("${catalog.export.directory:${java.io.tmpdir}/exports}")
    private String exportDirectory;

    @Value("${catalog.export.expiry-hours:24}")
    private int expiryHours;

    @Override
    public Mono<ReportExportDto> exportEventData(String eventId, ExportFormat format) {
        log.info("Exporting event data: eventId={}, format={}", eventId, format);

        // Reached only from the ADMIN-only exportEventData query: read the real request scope
        // rather than assuming platform authority, so a future non-admin caller is refused here
        // too instead of exporting a rival's event by guessing its id.
        return CurrentTenantScope.get()
                .flatMap(scope -> TenantGuard.locate(
                        scope,
                        eventRepository.findById(eventId),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(eventId, organizationIds),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + eventId + " (export)"))
                .flatMap(event -> generateExport(List.of(event), format, "event_" + eventId))
                .onErrorResume(e -> {
                    log.error("Error exporting event data: {}", e.getMessage(), e);
                    return Mono.just(ReportExportDto.error(e.getMessage(), format, clock.instant()));
                });
    }

    @Override
    public Mono<ReportExportDto> exportEventsReport(EventFilterInput filter, ExportFormat format) {
        log.info("Exporting events report: filter={}, format={}", filter, format);

        // Build query based on filter
        return findEventsByFilter(filter)
                .collectList()
                .flatMap(events -> {
                    if (events.isEmpty()) {
                        return Mono.just(ReportExportDto.error("No events found matching filter criteria", format, clock.instant()));
                    }
                    String filePrefix = "events_report_" + PlatformTime.format(
                            clock.instant(), DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    return generateExport(events, format, filePrefix);
                })
                // A refusal is the caller's to see as a refusal; only a failure to write the file
                // becomes an error export.
                .onErrorResume(e -> !(e instanceof com.pml.shared.error.DomainRefusal), e -> {
                    log.error("Error exporting events report: {}", e.getMessage(), e);
                    return Mono.just(ReportExportDto.error(e.getMessage(), format, clock.instant()));
                });
    }

    /**
     * The events the filter selects, at most {@link #MAX_EXPORT_ROWS}. A larger selection is refused
     * so the administrator narrows it, rather than receiving a file that silently stops.
     */
    private reactor.core.publisher.Flux<Event> findEventsByFilter(EventFilterInput filter) {
        return adminFilter.query(filter)
                .flatMapMany(query -> mongoTemplate.find(query.limit(MAX_EXPORT_ROWS + 1), Event.class)
                        .collectList()
                        .flatMapMany(found -> found.size() > MAX_EXPORT_ROWS
                                ? reactor.core.publisher.Flux.error(new com.pml.shared.error.ValidationRefusal(List.of(
                                        new com.pml.shared.error.FieldViolation("filter",
                                                "selects more than " + MAX_EXPORT_ROWS + " events; narrow it"))))
                                : reactor.core.publisher.Flux.fromIterable(found)));
    }

    /**
     * Generate export file in the specified format.
     */
    private Mono<ReportExportDto> generateExport(List<Event> events, ExportFormat format, String filePrefix) {
        return Mono.fromCallable(() -> {
            String fileName = filePrefix + getFileExtension(format);
            Path exportPath = Path.of(exportDirectory);

            // Ensure export directory exists
            Files.createDirectories(exportPath);

            Path filePath = exportPath.resolve(fileName);

            // Generate file based on format
            switch (format) {
                case CSV -> generateCsv(events, filePath);
                case JSON -> generateJson(events, filePath);
                case EXCEL -> generateExcel(events, filePath);
                case PDF -> generatePdf(events, filePath);
            }

            String downloadUrl = exportBaseUrl + "/" + fileName;
            Instant expiresAt = clock.instant().plus(Duration.ofHours(expiryHours));

            log.info("Export generated: fileName={}, downloadUrl={}", fileName, downloadUrl);

            return ReportExportDto.success(downloadUrl, fileName, format, expiresAt, clock.instant());
        });
    }

    /**
     * Generate CSV export.
     */
    private void generateCsv(List<Event> events, Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            // Write header
            writer.write("ID,Title,Status,Organizer ID,Category ID,Start Date,End Date,City,Location,Capacity,Created At\n");

            // Write data rows
            for (Event event : events) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%s\n",
                        escapeCsv(event.getId()),
                        escapeCsv(event.getTitle()),
                        event.getStatus() != null ? event.getStatus().name() : "",
                        escapeCsv(event.getOrganizerId()),
                        escapeCsv(event.getCategoryId()),
                        event.getEventDateTime() != null ? event.getEventDateTime().toString() : "",
                        event.getEndDateTime() != null ? event.getEndDateTime().toString() : "",
                        escapeCsv(event.getCityName()),
                        escapeCsv(event.getLocationName()),
                        event.getTotalCapacity(),
                        event.getCreatedAt() != null ? event.getCreatedAt().toString() : ""
                ));
            }
        }
        log.debug("CSV export generated: {}", filePath);
    }

    /**
     * Generate JSON export.
     */
    private void generateJson(List<Event> events, Path filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Create export data structure
        Map<String, Object> exportData = new LinkedHashMap<>();
        exportData.put("exportedAt", clock.instant().toString());
        exportData.put("totalEvents", events.size());
        exportData.put("events", events.stream().map(this::eventToMap).toList());

        mapper.writeValue(filePath.toFile(), exportData);
        log.debug("JSON export generated: {}", filePath);
    }

    /**
     * Generate Excel export (simple CSV-based XLSX placeholder).
     * Note: For production, use Apache POI library.
     */
    private void generateExcel(List<Event> events, Path filePath) throws IOException {
        // For a proper Excel export, you would use Apache POI.
        // This is a placeholder that generates a CSV file with .xlsx extension.
        // In production, add Apache POI dependency and generate proper XLSX.
        log.warn("Excel export using CSV format as placeholder. Add Apache POI for proper XLSX support.");

        // Generate as CSV for now (production would use Apache POI)
        Path csvPath = Path.of(filePath.toString().replace(".xlsx", ".csv"));
        generateCsv(events, csvPath);
        Files.move(csvPath, filePath);
    }

    /**
     * Generate PDF export (simple text-based placeholder).
     * Note: For production, use iText or Apache PDFBox library.
     */
    private void generatePdf(List<Event> events, Path filePath) throws IOException {
        // For a proper PDF export, you would use iText or PDFBox.
        // This is a placeholder that generates a text report.
        // In production, add PDF library dependency and generate proper PDF.
        log.warn("PDF export using text format as placeholder. Add iText/PDFBox for proper PDF support.");

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write("EVENT EXPORT REPORT\n");
            writer.write("=" .repeat(60) + "\n");
            writer.write("Generated: " + clock.instant() + "\n");
            writer.write("Total Events: " + events.size() + "\n");
            writer.write("=" .repeat(60) + "\n\n");

            for (Event event : events) {
                writer.write("Event: " + event.getTitle() + "\n");
                writer.write("-".repeat(40) + "\n");
                writer.write("  ID: " + event.getId() + "\n");
                writer.write("  Status: " + (event.getStatus() != null ? event.getStatus().name() : "N/A") + "\n");
                writer.write("  Organizer: " + event.getOrganizerId() + "\n");
                writer.write("  Start: " + (event.getEventDateTime() != null ? event.getEventDateTime().toString() : "N/A") + "\n");
                writer.write("  End: " + (event.getEndDateTime() != null ? event.getEndDateTime().toString() : "N/A") + "\n");
                writer.write("  City: " + (event.getCityName() != null ? event.getCityName() : "N/A") + "\n");
                writer.write("  Location: " + (event.getLocationName() != null ? event.getLocationName() : "N/A") + "\n");
                writer.write("  Capacity: " + event.getTotalCapacity() + "\n");
                writer.write("\n");
            }
        }
        log.debug("PDF export generated (text format): {}", filePath);
    }

    /**
     * Convert event to map for JSON export.
     */
    private Map<String, Object> eventToMap(Event event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("title", event.getTitle());
        map.put("description", event.getDescription());
        map.put("status", event.getStatus() != null ? event.getStatus().name() : null);
        map.put("organizerId", event.getOrganizerId());
        map.put("organizerName", event.getOrganizerName());
        map.put("categoryId", event.getCategoryId());
        map.put("eventDateTime", event.getEventDateTime() != null ? event.getEventDateTime().toString() : null);
        map.put("endDateTime", event.getEndDateTime() != null ? event.getEndDateTime().toString() : null);
        map.put("cityName", event.getCityName());
        map.put("locationName", event.getLocationName());
        map.put("totalCapacity", event.getTotalCapacity());
        map.put("isFeatured", event.isFeatured());
        map.put("published", event.isPublished());
        map.put("createdAt", event.getCreatedAt() != null ? event.getCreatedAt().toString() : null);
        map.put("updatedAt", event.getUpdatedAt() != null ? event.getUpdatedAt().toString() : null);
        return map;
    }

    /**
     * Get file extension for format.
     */
    private String getFileExtension(ExportFormat format) {
        return switch (format) {
            case CSV -> ".csv";
            case JSON -> ".json";
            case EXCEL -> ".xlsx";
            case PDF -> ".pdf";
        };
    }

    /**
     * Escape CSV field value.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // A cell a spreadsheet would evaluate — =, +, -, @, or a tab or carriage return — is made
        // text with a leading apostrophe, so an organizer's event title cannot run as a formula on
        // the administrator's machine.
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        // If contains comma, newline, or quote, wrap in quotes and escape internal quotes
        if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
