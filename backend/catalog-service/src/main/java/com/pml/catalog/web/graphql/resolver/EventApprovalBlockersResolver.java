package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.service.EventReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * {@code Event.approvalBlockers}: what an event still lacks before a reviewer can approve it. Only
 * platform administrators review events, so only they may read it.
 */
@DgsComponent
public class EventApprovalBlockersResolver {

    private final EventReviewService reviews;

    public EventApprovalBlockersResolver(EventReviewService reviews) {
        this.reviews = reviews;
    }

    @DgsData(parentType = "Event", field = "approvalBlockers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<List<String>> approvalBlockers(DgsDataFetchingEnvironment env) {
        Event event = env.getSource();
        return reviews.approvalBlockers(event).map(blockers -> blockers.stream().map(Enum::name).toList());
    }
}
