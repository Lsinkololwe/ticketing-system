package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The {@code UploadMediaInput} GraphQL type; the picture travels as base64 and is checked by its bytes. */
public record UploadMediaInput(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank String contentType,
        @NotBlank @Size(max = com.pml.catalog.service.MediaRules.MAX_BASE64_CHARS) String contentBase64,
        @Size(max = 120) String title,
        @Size(max = 200) String altText,
        String eventId
) {
}
