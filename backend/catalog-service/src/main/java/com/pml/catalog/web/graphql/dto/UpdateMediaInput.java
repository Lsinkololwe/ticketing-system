package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.Size;

/** The {@code UpdateMediaInput} GraphQL type; a null field leaves the stored value unchanged. */
public record UpdateMediaInput(
        @Size(max = 120) String title,
        @Size(max = 200) String altText,
        String eventId
) {
}
