package com.pml.booking.web.graphql.dto;

import java.time.LocalDateTime;

/**
 * Report Export DTO
 *
 * Business Intent: Response for report export operations with download URL.
 */
public record ReportExport(
        boolean success,
        String downloadUrl,
        LocalDateTime expiresAt,
        ExportFormat format,
        LocalDateTime generatedAt,
        String fileName,
        String errorMessage
) {
    /**
     * Factory method for successful export.
     */
    public static ReportExport success(String downloadUrl, ExportFormat format, String fileName) {
        return new ReportExport(
                true,
                downloadUrl,
                LocalDateTime.now().plusHours(24), // URL expires in 24 hours
                format,
                LocalDateTime.now(),
                fileName,
                null
        );
    }

    /**
     * Factory method for failed export.
     */
    public static ReportExport error(String errorMessage, ExportFormat format) {
        return new ReportExport(
                false,
                null,
                null,
                format,
                LocalDateTime.now(),
                null,
                errorMessage
        );
    }
}
