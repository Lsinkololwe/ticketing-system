package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.EventCategory;

import java.util.List;
import java.util.Map;

public record UpdateEventCategoryMutationResponse(
        boolean success,
        String message,
        EventCategory data,
        List<String> errors,
        Map<String, Object> metadata
) {}
