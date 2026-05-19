package com.pml.catalog.web.graphql.dto;

public record CreateCityInput(
        String name,
        String code,
        String provinceId,
        String country
) {}
