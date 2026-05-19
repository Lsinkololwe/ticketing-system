package com.pml.identity.web.graphql.dto.pagination;

import java.util.List;

/**
 * Cursor-based pagination connection for Notifications.
 * Follows Relay Connection Specification.
 *
 * Schema definition:
 * type NotificationConnection {
 *     edges: [NotificationEdge!]!
 *     pageInfo: PageInfo!
 *     totalCount: Int
 * }
 */
public record NotificationConnection(
        List<NotificationEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static NotificationConnection empty() {
        return new NotificationConnection(List.of(), PageInfo.empty(), 0);
    }
}
