package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.AccountStatus;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.UserService;
import com.pml.identity.service.UserStatsService;
import com.pml.identity.web.graphql.dto.pagination.*;
import com.pml.identity.web.graphql.dto.stats.UserStats;
import com.pml.shared.constants.UserType;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for User operations.
 * Handles user-related queries with both offset and cursor pagination.
 *
 * Uses DGS annotations for proper integration with Apollo Federation.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class UserQueryResolver {

    private final UserService userService;
    private final UserStatsService userStatsService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get the currently authenticated user.
     * Schema: me: User
     */
    @DgsQuery
    public Mono<User> me() {
        return SecurityContextUtils.getCurrentUserEmail()
                .doOnNext(email -> log.debug("GraphQL query: me (email={})", email))
                .flatMap(userService::findByEmail);
    }

    /**
     * Get a user by ID.
     * Schema: user(id: ID!): User
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<User> user(@InputArgument String id) {
        log.debug("GraphQL query: user(id={})", id);
        Objects.requireNonNull(id, "User ID is required");
        return userService.findById(id);
    }

    /**
     * Get a user by email.
     * Schema: userByEmail(email: String!): User
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> userByEmail(@InputArgument String email) {
        log.debug("GraphQL query: userByEmail(email={})", email);
        Objects.requireNonNull(email, "Email is required");
        return userService.findByEmail(email);
    }

    /**
     * Get a user by phone number.
     * Schema: userByPhone(phoneNumber: String!): User
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> userByPhone(@InputArgument String phoneNumber) {
        log.debug("GraphQL query: userByPhone(phoneNumber={})", phoneNumber);
        Objects.requireNonNull(phoneNumber, "Phone number is required");
        return userService.findByPhoneNumber(phoneNumber);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Search users with offset pagination (admin only).
     * Schema: usersOffsetPagination(search: String, role: UserType, accountStatus: AccountStatus, pagination: OffsetPaginationInput): UserOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<UserOffsetPage> usersOffsetPagination(
            @InputArgument String search,
            @InputArgument UserType role,
            @InputArgument AccountStatus accountStatus,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: usersOffsetPagination(search={}, role={}, accountStatus={})",
                search, role, accountStatus);

        Flux<User> userFlux = applyFilters(userService.findAll(), search, role, accountStatus);
        return buildOffsetPage(userFlux, pagination);
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Search users with cursor pagination (admin only, mobile/infinite scroll).
     * Schema: usersCursorPagination(search: String, role: UserType, accountStatus: AccountStatus, pagination: CursorPaginationInput): UserConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<UserConnection> usersCursorPagination(
            @InputArgument String search,
            @InputArgument UserType role,
            @InputArgument AccountStatus accountStatus,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: usersCursorPagination(search={}, role={}, accountStatus={})",
                search, role, accountStatus);

        Flux<User> userFlux = applyFilters(userService.findAll(), search, role, accountStatus);
        return buildCursorConnection(userFlux, pagination);
    }

    // ========================================================================
    // ROLE-BASED QUERIES (Multi-role support)
    // ========================================================================

    /**
     * Find all users who have a specific role.
     * Schema: usersByRole(role: UserType!, activeOnly: Boolean, pagination: OffsetPaginationInput): UserOffsetPage
     *
     * OWASP Compliance:
     * - A01:2021 Broken Access Control: Admin-only access enforced
     * - A04:2021 Insecure Design: Uses MongoDB query, not in-memory filtering
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<UserOffsetPage> usersByRole(
            @InputArgument UserType role,
            @InputArgument Boolean activeOnly,
            @InputArgument OffsetPaginationInput pagination
    ) {
        Objects.requireNonNull(role, "Role is required");
        log.debug("GraphQL query: usersByRole(role={}, activeOnly={})", role, activeOnly);

        Flux<User> userFlux = userService.findByRole(role);

        // Apply active filter if specified
        if (Boolean.TRUE.equals(activeOnly)) {
            userFlux = userFlux.filter(User::isActive);
        }

        return buildOffsetPage(userFlux, pagination);
    }

    // ========================================================================
    // STATISTICS QUERIES
    // ========================================================================

    /**
     * Get comprehensive user statistics using MongoDB aggregation.
     * Schema: userStats: UserStats
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<UserStats> userStats() {
        log.debug("GraphQL query: userStats");
        return userStatsService.getUserStats();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Apply filters to a Flux of users.
     *
     * <p>Multi-role support: The userType filter now checks if the user has the specified role,
     * not if it's their only or primary role.</p>
     */
    private Flux<User> applyFilters(Flux<User> users, String search, UserType role, AccountStatus accountStatus) {
        return users.filter(user -> {
            // Filter by search term (name or email)
            if (search != null && !search.isBlank()) {
                String searchLower = search.toLowerCase();
                boolean matchesSearch = (user.getFirstName() != null && user.getFirstName().toLowerCase().contains(searchLower))
                        || (user.getLastName() != null && user.getLastName().toLowerCase().contains(searchLower))
                        || (user.getEmail() != null && user.getEmail().toLowerCase().contains(searchLower))
                        || (user.getUsername() != null && user.getUsername().toLowerCase().contains(searchLower));
                if (!matchesSearch) {
                    return false;
                }
            }

            // Filter by role (multi-role: check if user HAS the role)
            if (role != null && !user.hasRole(role)) {
                return false;
            }

            // Filter by account status
            if (accountStatus != null && user.getAccountStatus() != accountStatus) {
                return false;
            }

            return true;
        });
    }

    /**
     * Build UserOffsetPage from a Flux of users.
     */
    private Mono<UserOffsetPage> buildOffsetPage(Flux<User> userFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return userFlux.collectList()
                .map(allUsers -> {
                    int totalCount = allUsers.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 0;

                    List<User> paginatedUsers = allUsers.stream()
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

                    return new UserOffsetPage(paginatedUsers, pageInfo);
                });
    }

    /**
     * Build UserConnection from a Flux of users.
     */
    private Mono<UserConnection> buildCursorConnection(Flux<User> userFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : CursorPaginationInput.defaults();
        int limit = p.getLimit();

        return userFlux.collectList()
                .map(allUsers -> {
                    int totalCount = allUsers.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allUsers.size(); i++) {
                            if (allUsers.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of users
                    List<User> pageUsers = allUsers.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageUsers.isEmpty()) {
                        return UserConnection.empty();
                    }

                    // Build edges
                    List<UserEdge> edges = pageUsers.stream()
                            .map(UserEdge::of)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.forCursor(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new UserConnection(edges, pageInfo, totalCount);
                });
    }
}
