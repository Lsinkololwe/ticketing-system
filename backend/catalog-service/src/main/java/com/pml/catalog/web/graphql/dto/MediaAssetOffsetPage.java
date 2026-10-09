package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.MediaAsset;

import java.util.List;

public record MediaAssetOffsetPage(
        List<MediaAsset> content,
        int pageNumber,
        int pageSize,
        int totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
