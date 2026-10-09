package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.ExportFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for export report response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportExportDto {

    private boolean success;
    private String downloadUrl;
    private Instant expiresAt;
    private ExportFormat format;
    private Instant generatedAt;
    private String fileName;
    private String errorMessage;

    /**
     * Create a successful export result.
     */
    public static ReportExportDto success(String downloadUrl, String fileName, ExportFormat format,
                                          Instant expiresAt, Instant generatedAt) {
        return ReportExportDto.builder()
                .success(true)
                .downloadUrl(downloadUrl)
                .fileName(fileName)
                .format(format)
                .generatedAt(generatedAt)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Create a failed export result.
     */
    public static ReportExportDto error(String errorMessage, ExportFormat format, Instant generatedAt) {
        return ReportExportDto.builder()
                .success(false)
                .errorMessage(errorMessage)
                .format(format)
                .generatedAt(generatedAt)
                .build();
    }
}
