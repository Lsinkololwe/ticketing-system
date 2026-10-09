package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.StockImagePurpose;

import java.time.Instant;

/** The {@code StockImage} GraphQL type: a stock {@code MediaAsset} as the library presents it. */
public record StockImage(
        String id,
        String url,
        String title,
        String altText,
        StockImagePurpose purpose,
        String categoryCode,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
