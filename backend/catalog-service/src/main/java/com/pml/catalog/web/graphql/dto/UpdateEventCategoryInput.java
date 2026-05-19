package com.pml.catalog.web.graphql.dto;

public record UpdateEventCategoryInput(
        String name,
        String code,
        String description,
        Boolean isActive
) {}
