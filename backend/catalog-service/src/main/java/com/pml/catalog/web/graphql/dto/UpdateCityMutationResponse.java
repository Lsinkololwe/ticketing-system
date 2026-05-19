package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.City;

import java.util.List;
import java.util.Map;

public record UpdateCityMutationResponse(
        boolean success,
        String message,
        City data,
        List<String> errors,
        Map<String, Object> metadata
) {}
