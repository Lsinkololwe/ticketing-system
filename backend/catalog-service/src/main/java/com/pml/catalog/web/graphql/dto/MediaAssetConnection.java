package com.pml.catalog.web.graphql.dto;

import java.util.List;

public record MediaAssetConnection(List<MediaAssetEdge> edges, PageInfo pageInfo) {
}
