package com.pml.catalog.dto;

import com.pml.catalog.domain.enums.ExportFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime expiresAt;
    private ExportFormat format;
    private LocalDateTime generatedAt;
    private String fileName;
    private String errorMessage;

    /**
     * Create a successful export result.
     */
    public static ReportExportDto success(String downloadUrl, String fileName, ExportFormat format, LocalDateTime expiresAt) {
        return ReportExportDto.builder()
                .success(true)
                .downloadUrl(downloadUrl)
                .fileName(fileName)
                .format(format)
                .generatedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Create a failed export result.
     */
    public static ReportExportDto error(String errorMessage, ExportFormat format) {
        return ReportExportDto.builder()
                .success(false)
                .errorMessage(errorMessage)
                .format(format)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
