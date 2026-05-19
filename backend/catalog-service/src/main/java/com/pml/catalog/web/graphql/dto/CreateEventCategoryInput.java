package com.pml.catalog.web.graphql.dto;

public record CreateEventCategoryInput(
        String name,
        String code,
        String description
) {}
