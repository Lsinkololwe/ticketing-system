package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.StockImagePurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The {@code UploadStockImageInput} GraphQL type. */
public record UploadStockImageInput(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank String contentType,
        @NotBlank @Size(max = com.pml.catalog.service.MediaRules.MAX_BASE64_CHARS) String contentBase64,
        @NotNull StockImagePurpose purpose,
        @Size(max = 60) String categoryCode,
        @Size(max = 120) String title,
        @Size(max = 200) String altText
) {
}
