package com.pml.catalog.web.graphql.dto;

import jakarta.validation.constraints.Size;

import com.pml.catalog.domain.enums.MediaStatus;

/** The {@code MediaModerationFilterInput} GraphQL type. */
public record MediaModerationFilterInput(MediaStatus status, @Size(max = 64) String organizationId, @Size(max = 64) String eventId, @Size(max = 100) String search) {
}
