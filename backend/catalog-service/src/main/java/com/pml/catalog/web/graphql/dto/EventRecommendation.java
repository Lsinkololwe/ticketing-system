package com.pml.catalog.web.graphql.dto;

import com.pml.catalog.domain.enums.RecommendationReason;
import com.pml.catalog.domain.model.Event;

/** The {@code EventRecommendation} GraphQL type. */
public record EventRecommendation(Event event, RecommendationReason reason, String basedOnEventId) {
}
