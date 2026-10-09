package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** The {@code CheckoutSettingsInput} GraphQL type. */
public record CheckoutSettingsInput(
        @Min(1) @Max(100) Integer maxTicketsPerOrder,
        Boolean collectHolderNames,
        @Size(max = 200) String extraQuestion
) {
}
