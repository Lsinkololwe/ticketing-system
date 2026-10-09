package com.pml.booking.web.graphql.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Report Export DTO
 *
 * Business Intent: Response for report export operations with download URL.
 */
public record ReportExport(
        String downloadUrl,
        Instant expiresAt,
        ExportFormat format,
        Instant generatedAt,
        String fileName
) {
    /**
     * The export that was produced.
     *
     * <p>There is no failed counterpart: an export that could not be produced
     * raises a refusal, so every instance of this record describes a file that
     * exists. A {@code success: false} variant would have made the download URL
     * nullable on a type whose only purpose is to carry one.</p>
     */
    public static ReportExport of(String downloadUrl, ExportFormat format, String fileName, Instant now) {
        return new ReportExport(
                downloadUrl,
                now.plus(Duration.ofHours(24)), // URL expires in 24 hours
                format,
                now,
                fileName);
    }

    /**
     * Factory method for failed export.
     */
}
