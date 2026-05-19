package com.pml.catalog.web.graphql.dto;

import java.util.List;
import java.util.Map;

public record DeleteProvinceMutationResponse(
        boolean success,
        String message,
        Boolean data,
        List<String> errors,
        Map<String, Object> metadata
) {}
