package com.pml.catalog.web.graphql.dto;

public record UpdateProvinceInput(
        String name,
        String code,
        String country,
        Boolean isActive
) {}
