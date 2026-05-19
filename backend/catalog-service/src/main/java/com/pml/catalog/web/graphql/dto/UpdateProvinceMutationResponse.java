package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.Province;

import java.util.List;
import java.util.Map;

public record UpdateProvinceMutationResponse(
        boolean success,
        String message,
        Province data,
        List<String> errors,
        Map<String, Object> metadata
) {}
