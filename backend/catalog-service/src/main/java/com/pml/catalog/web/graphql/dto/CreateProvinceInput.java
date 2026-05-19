package com.pml.catalog.web.graphql.dto;

public record CreateProvinceInput(
        String name,
        String code,
        String country
) {}
