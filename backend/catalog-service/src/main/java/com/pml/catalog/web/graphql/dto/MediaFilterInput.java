package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.Size;

/** The {@code MediaFilterInput} GraphQL type. */
public record MediaFilterInput(@Size(max = 64) String eventId, @Size(max = 100) String search) {
}
