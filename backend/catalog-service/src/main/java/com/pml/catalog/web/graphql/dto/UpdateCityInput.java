package com.pml.catalog.web.graphql.dto;

public record UpdateCityInput(
        String name,
        String code,
        String provinceId,
        String country,
        Boolean isActive
) {}
