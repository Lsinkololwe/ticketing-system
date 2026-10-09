package com.pml.catalog.web.graphql.dto;

import java.util.List;

public record StockImageConnection(List<StockImageEdge> edges, PageInfo pageInfo) {
}
