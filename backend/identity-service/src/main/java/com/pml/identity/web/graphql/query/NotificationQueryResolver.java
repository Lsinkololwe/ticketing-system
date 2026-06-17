package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.NotificationStatus;
import com.pml.identity.domain.enums.NotificationType;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.model.NotificationPreferences;
import com.pml.identity.service.NotificationPreferencesService;
import com.pml.identity.service.NotificationService;
import com.pml.identity.web.graphql.dto.pagination.*;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * GraphQL Query Resolver for Notification operations.
 * Handles notification-related queries with both offset and cursor pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class NotificationQueryResolver {

    private final NotificationService notificationService;
    private final NotificationPreferencesService preferencesService;

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get my notifications with offset pagination.
     * Schema: myNotificationsOffsetPagination(type: NotificationType, status: NotificationStatus, pagination: OffsetPaginationInput): NotificationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<NotificationOffsetPage> myNotificationsOffsetPagination(
            @InputArgument NotificationType type,
            @InputArgument NotificationStatus status,
            @InputArgument OffsetPaginationInput pagination
    ) {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myNotificationsOffsetPagination(type={}, status={})", type, status))
                .flatMap(userId -> {
                    Flux<Notification> notificationFlux = notificationService.findByUserId(userId)
                            .filter(notification -> {
                                if (type != null && notification.getType() != type) {
                                    return false;
                                }
                                if (status != null && notification.getStatus() != status) {
                                    return false;
                                }
                                return true;
                            });

                    return buildOffsetPage(notificationFlux, pagination);
                })
                .defaultIfEmpty(NotificationOffsetPage.empty());
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Get my notifications with cursor pagination (mobile/infinite scroll).
     * Schema: myNotificationsCursorPagination(type: NotificationType, status: NotificationStatus, pagination: CursorPaginationInput): NotificationConnection!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<NotificationConnection> myNotificationsCursorPagination(
            @InputArgument NotificationType type,
            @InputArgument NotificationStatus status,
            @InputArgument CursorPaginationInput pagination
    ) {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myNotificationsCursorPagination(type={}, status={})", type, status))
                .flatMap(userId -> {
                    Flux<Notification> notificationFlux = notificationService.findByUserId(userId)
                            .filter(notification -> {
                                if (type != null && notification.getType() != type) {
                                    return false;
                                }
                                if (status != null && notification.getStatus() != status) {
                                    return false;
                                }
                                return true;
                            });

                    return buildCursorConnection(notificationFlux, pagination);
                })
                .defaultIfEmpty(NotificationConnection.empty());
    }

    // ========================================================================
    // COUNT QUERIES
    // ========================================================================

    /**
     * Get unread notification count.
     * Schema: unreadNotificationCount: Int!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Integer> unreadNotificationCount() {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: unreadNotificationCount (userId={})", userId))
                .flatMap(userId -> notificationService.countUnread(userId).map(Long::intValue))
                .defaultIfEmpty(0);
    }

    // ========================================================================
    // PREFERENCES QUERIES
    // ========================================================================

    /**
     * Get my notification preferences.
     * Schema: myNotificationPreferences: NotificationPreferences
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<NotificationPreferences> myNotificationPreferences() {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myNotificationPreferences (userId={})", userId))
                .flatMap(preferencesService::getOrCreateDefault);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build NotificationOffsetPage from a Flux of notifications.
     */
    private Mono<NotificationOffsetPage> buildOffsetPage(Flux<Notification> notificationFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return notificationFlux.collectList()
                .map(allNotifications -> {
                    int totalCount = allNotifications.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 0;

                    List<Notification> paginatedNotifications = allNotifications.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    PageInfo pageInfo = PageInfo.forOffset(
                            totalCount,
                            limit,
                            p.page(),
                            totalPages,
                            hasNextPage,
                            hasPreviousPage
                    );

                    return new NotificationOffsetPage(paginatedNotifications, pageInfo);
                });
    }

    /**
     * Build NotificationConnection from a Flux of notifications.
     */
    private Mono<NotificationConnection> buildCursorConnection(Flux<Notification> notificationFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : CursorPaginationInput.defaults();
        int limit = p.getLimit();

        return notificationFlux.collectList()
                .map(allNotifications -> {
                    int totalCount = allNotifications.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allNotifications.size(); i++) {
                            if (allNotifications.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of notifications
                    List<Notification> pageNotifications = allNotifications.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageNotifications.isEmpty()) {
                        return NotificationConnection.empty();
                    }

                    // Build edges
                    List<NotificationEdge> edges = pageNotifications.stream()
                            .map(NotificationEdge::of)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.forCursor(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new NotificationConnection(edges, pageInfo, totalCount);
                });
    }
}
