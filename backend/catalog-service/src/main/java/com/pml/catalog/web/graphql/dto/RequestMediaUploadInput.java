package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The {@code RequestMediaUploadInput} GraphQL type: what the file will be, before any byte is sent. */
public record RequestMediaUploadInput(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        @Min(1) @Max(com.pml.catalog.service.MediaRules.MAX_UPLOAD_BYTES) int sizeBytes
) {
}
