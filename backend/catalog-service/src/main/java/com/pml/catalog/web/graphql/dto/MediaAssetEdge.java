package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.model.MediaAsset;

public record MediaAssetEdge(MediaAsset node, String cursor) {
}
