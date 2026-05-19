package com.pml.catalog.web.graphql.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record UpdateEventInput(
        String title,
        String description,
        String categoryId,
        LocalDateTime eventDateTime,
        LocalDateTime endDateTime,
        Integer totalCapacity,
        List<String> tags,
        Map<String, Object> additionalInfo
) {}
