package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.StockImagePurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The {@code CompleteStockImageUploadInput} GraphQL type. */
public record CompleteStockImageUploadInput(
        @NotBlank @Size(max = 2048) String uploadToken,
        @NotNull StockImagePurpose purpose,
        @Size(max = 60) String categoryCode,
        @Size(max = 120) String title,
        @Size(max = 200) String altText
) {
}
