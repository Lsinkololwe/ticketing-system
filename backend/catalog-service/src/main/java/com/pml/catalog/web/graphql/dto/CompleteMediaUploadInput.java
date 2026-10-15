package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The {@code CompleteMediaUploadInput} GraphQL type. */
public record CompleteMediaUploadInput(
        @NotBlank @Size(max = 2048) String uploadToken,
        @Size(max = 120) String title,
        @Size(max = 200) String altText,
        @Size(max = 64) String eventId
) {
}
