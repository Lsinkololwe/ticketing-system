package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The {@code RunningOrderItemInput} GraphQL type: a venue-local {@code HH:mm} and what happens then. */
public record RunningOrderItemInput(
        @NotBlank @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]", message = "must be a 24-hour HH:mm time") String time,
        @NotBlank @Size(max = 200) String title
) {
}
