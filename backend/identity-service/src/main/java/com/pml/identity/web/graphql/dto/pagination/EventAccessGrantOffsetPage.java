package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.EventAccessGrant;

import java.util.List;

/**
 * Offset-based pagination result for EventAccessGrants.
 *
 * Schema definition:
 * type EventAccessGrantOffsetPage {
 *     content: [EventAccessGrant!]!
 *     pageInfo: PageInfo!
 * }
 */
public record EventAccessGrantOffsetPage(
        List<EventAccessGrant> content,
        PageInfo pageInfo
) {
    public static EventAccessGrantOffsetPage empty() {
        return new EventAccessGrantOffsetPage(List.of(), PageInfo.empty());
    }
}
