package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The {@code EventFaqInput} GraphQL type. */
public record EventFaqInput(
        @NotBlank @Size(max = 200) String question,
        @NotBlank @Size(max = 2_000) String answer
) {
}
