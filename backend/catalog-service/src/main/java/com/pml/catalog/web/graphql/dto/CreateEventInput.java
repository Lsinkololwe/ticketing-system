package com.pml.catalog.web.graphql.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record CreateEventInput(
        String title,
        String description,
        String categoryId,
        LocalDateTime eventDateTime,
        LocalDateTime endDateTime,
        int totalCapacity,
        Map<String, Object> additionalInfo
) {}
