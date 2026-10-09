package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.Size;

import com.pml.catalog.domain.enums.StockImagePurpose;

/** The {@code StockImageFilterInput} GraphQL type. */
public record StockImageFilterInput(StockImagePurpose purpose, @Size(max = 64) String categoryCode, Boolean includeInactive) {
}
