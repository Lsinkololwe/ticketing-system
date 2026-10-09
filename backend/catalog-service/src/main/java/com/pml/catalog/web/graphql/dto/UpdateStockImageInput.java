package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.StockImagePurpose;
import jakarta.validation.constraints.Size;

/** The {@code UpdateStockImageInput} GraphQL type; a null field leaves the stored value unchanged. */
public record UpdateStockImageInput(
        @Size(max = 120) String title,
        @Size(max = 200) String altText,
        StockImagePurpose purpose,
        @Size(max = 60) String categoryCode,
        Boolean active
) {
}
