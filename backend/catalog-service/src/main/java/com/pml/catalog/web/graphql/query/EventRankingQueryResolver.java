package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.service.EventRanking;
import com.pml.catalog.web.graphql.dto.EventRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/** What is selling now, and what a buyer might like. */
@DgsComponent
@RequiredArgsConstructor
public class EventRankingQueryResolver {

    private final EventRanking ranking;

    @DgsQuery
    public Flux<Event> trendingEvents(@InputArgument Integer first) {
        return ranking.trending(first == null ? 10 : first);
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ORGANIZER', 'ADMIN')")
    public Mono<List<EventRecommendation>> recommendedEvents(@InputArgument List<String> basedOnEventIds,
                                                             @InputArgument Integer first) {
        return ranking.recommended(basedOnEventIds, first == null ? 10 : first);
    }
}
