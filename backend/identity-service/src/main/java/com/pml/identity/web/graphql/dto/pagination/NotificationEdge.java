package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.Notification;

/**
 * Edge wrapper for Notification in cursor-based pagination.
 *
 * Schema definition:
 * type NotificationEdge {
 *     cursor: String!
 *     node: Notification!
 * }
 */
public record NotificationEdge(
        String cursor,
        Notification node
) {
    public static NotificationEdge of(Notification notification) {
        return new NotificationEdge(notification.getId(), notification);
    }
}
