package com.pml.identity.web.graphql.dto.pagination;

import com.pml.identity.domain.model.Notification;

import java.util.List;

/**
 * Offset-based pagination result for Notifications.
 *
 * Schema definition:
 * type NotificationOffsetPage {
 *     content: [Notification!]!
 *     pageInfo: PageInfo!
 * }
 */
public record NotificationOffsetPage(
        List<Notification> content,
        PageInfo pageInfo
) {
    public static NotificationOffsetPage empty() {
        return new NotificationOffsetPage(List.of(), PageInfo.empty());
    }
}
