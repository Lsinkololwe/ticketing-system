package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.service.OrganizerEventFeed;
import com.pml.catalog.util.KeysetCursor;
import com.pml.catalog.web.graphql.dto.CursorPaginationInput;
import com.pml.catalog.web.graphql.dto.EventConnection;
import com.pml.catalog.web.graphql.dto.EventEdge;
import com.pml.catalog.web.graphql.dto.PageInfo;
import com.pml.catalog.web.graphql.query.OrganizerEventQueryResolver.OrganizerEventFilterInput;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;

/** The organizer's event list with a cursor, for lists that grow while someone is reading them. */
@DgsComponent
@RequiredArgsConstructor
public class OrganizerEventConnectionResolver {

    private final OrganizerEventFeed feed;

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<EventConnection> myEventsConnection(@InputArgument OrganizerEventFilterInput filter,
                                                    @InputArgument CursorPaginationInput pagination) {
        CursorPaginationInput page = pagination != null ? pagination : new CursorPaginationInput();
        if (page.isBackward()) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("pagination.before",
                    "this list pages forward only"))));
        }
        return feed.mine(filter, page.getAfter(), page.getLimit()).map(result -> {
            List<EventEdge> edges = result.events().stream()
                    .map(event -> new EventEdge(KeysetCursor.encode(event.getCreatedAt(), event.getId()), event))
                    .toList();
            EventConnection connection = new EventConnection();
            connection.setEdges(edges);
            connection.setPageInfo(PageInfo.builder()
                    .hasNextPage(result.hasNext())
                    .hasPreviousPage(result.hasPrevious())
                    .startCursor(edges.isEmpty() ? null : edges.get(0).getCursor())
                    .endCursor(edges.isEmpty() ? null : edges.get(edges.size() - 1).getCursor())
                    .build());
            return connection;
        });
    }
}
