package com.pml.catalog.web.graphql.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A venue as the organizer types it. The city must name one of the platform's seeded cities;
 * the venue is recorded under that city, never under the typed text.
 */
public record EventLocationInput(
        @NotBlank @Size(max = 300) String name,
        @NotBlank @Size(max = 500) String address,
        @NotBlank @Size(max = 200) String city,
        @Size(max = 200) String province,
        @NotBlank @Size(max = 100) String country,
        @Size(max = 20) String postalCode,
        @Valid CreateCoordinatesInput coordinates,
        @Size(max = 2_000) String description
) {
}
