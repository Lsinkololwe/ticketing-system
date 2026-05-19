package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.Event;

import java.util.List;
import java.util.Map;

public record DuplicateEventMutationResponse(
        boolean success,
        String message,
        Event data,
        List<String> errors,
        Map<String, Object> metadata
) {}
