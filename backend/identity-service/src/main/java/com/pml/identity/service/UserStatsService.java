package com.pml.identity.service;

import com.pml.identity.domain.enums.AccountStatus;
import com.pml.identity.web.graphql.dto.stats.UserStats;
import com.pml.identity.web.graphql.dto.stats.UserStatusStats;
import com.pml.identity.web.graphql.dto.stats.UserRoleStats;
import com.pml.shared.constants.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Service for computing user statistics using MongoDB aggregation pipelines.
 *
 * This service uses efficient aggregation queries instead of multiple database
 * round-trips, resulting in significantly faster dashboard performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Compute comprehensive user statistics using MongoDB aggregation.
     *
     * Performance: Uses parallel aggregation queries to minimize database round-trips.
     */
    public Mono<UserStats> getUserStats() {
        return Mono.zip(
                getUserTypeCountsAggregation(),
                getUserStatusCountsAggregation(),
                getVerifiedUsersCount(),
                getActiveUsersCount(),
                getNewUsersThisMonthCount(),
                getNewUsersThisWeekCount(),
                getLastMonthUsersCount()
        ).map(tuple -> {
            List<UserRoleStats> typeStats = tuple.getT1();
            List<UserStatusStats> statusStats = tuple.getT2();
            int verifiedUsers = tuple.getT3();
            int activeUsers = tuple.getT4();
            int newUsersThisMonth = tuple.getT5();
            int newUsersThisWeek = tuple.getT6();
            int lastMonthUsers = tuple.getT7();

            // Calculate totals from type stats
            int totalUsers = typeStats.stream().mapToInt(UserRoleStats::getCount).sum();
            int organizers = getCountForType(typeStats, UserType.ORGANIZER);
            int attendees = getCountForType(typeStats, UserType.CUSTOMER);
            int adminUsers = getCountForType(typeStats, UserType.ADMIN) +
                           getCountForType(typeStats, UserType.SUPER_ADMIN);

            // Extract status counts
            int suspendedUsers = getCountForStatus(statusStats, AccountStatus.SUSPENDED);
            int lockedUsers = getCountForStatus(statusStats, AccountStatus.LOCKED);
            int pendingVerificationUsers = getCountForStatus(statusStats, AccountStatus.PENDING_VERIFICATION);

            // Calculate growth rate (month-over-month)
            Double growthRate = lastMonthUsers > 0
                    ? ((double) newUsersThisMonth / lastMonthUsers - 1) * 100
                    : null;

            return UserStats.builder()
                    .totalUsers(totalUsers)
                    .organizers(organizers)
                    .attendees(attendees)
                    .adminUsers(adminUsers)
                    .verifiedUsers(verifiedUsers)
                    .activeUsers(activeUsers)
                    .suspendedUsers(suspendedUsers)
                    .lockedUsers(lockedUsers)
                    .pendingVerificationUsers(pendingVerificationUsers)
                    .newUsersThisMonth(newUsersThisMonth)
                    .newUsersThisWeek(newUsersThisWeek)
                    .growthRate(growthRate != null ? Math.round(growthRate * 100.0) / 100.0 : null)
                    .usersByRole(typeStats)
                    .usersByStatus(statusStats)
                    .build();
        });
    }

    /**
     * Get user counts grouped by role using a single aggregation.
     *
     * MongoDB Aggregation Pipeline:
     * 1. $unwind: Flatten the roles array so each role becomes a separate document
     * 2. $group: Group all roles and count occurrences
     * 3. $sort: Order by count descending
     *
     * Note: Since users can have multiple roles, the same user may be counted
     * multiple times (once for each role they have). This is intentional.
     */
    private Mono<List<UserRoleStats>> getUserTypeCountsAggregation() {
        Aggregation aggregation = newAggregation(
                unwind("roles"),
                group("roles").count().as("count"),
                sort(Sort.Direction.DESC, "count")
        );

        return mongoTemplate.aggregate(aggregation, "users", TypeAggResult.class)
                .collectList()
                .map(results -> {
                    int total = results.stream().mapToInt(TypeAggResult::getCount).sum();

                    // Create stats for all user types, including those with 0 count
                    List<UserRoleStats> stats = new ArrayList<>();
                    for (UserType userType : UserType.values()) {
                        int count = results.stream()
                                .filter(r -> userType.name().equals(r.getId()))
                                .findFirst()
                                .map(TypeAggResult::getCount)
                                .orElse(0);

                        double percentage = total > 0 ? (count * 100.0) / total : 0.0;

                        stats.add(UserRoleStats.builder()
                                .role(userType)
                                .count(count)
                                .percentage(Math.round(percentage * 100.0) / 100.0)
                                .build());
                    }
                    return stats;
                });
    }

    /**
     * Get user counts grouped by account status.
     */
    private Mono<List<UserStatusStats>> getUserStatusCountsAggregation() {
        Aggregation aggregation = newAggregation(
                group("accountStatus").count().as("count"),
                sort(Sort.Direction.DESC, "count")
        );

        return mongoTemplate.aggregate(aggregation, "users", TypeAggResult.class)
                .collectList()
                .map(results -> {
                    int total = results.stream().mapToInt(TypeAggResult::getCount).sum();

                    // Create stats for all account statuses, including those with 0 count
                    List<UserStatusStats> stats = new ArrayList<>();
                    for (AccountStatus status : AccountStatus.values()) {
                        int count = results.stream()
                                .filter(r -> status.name().equals(r.getId()))
                                .findFirst()
                                .map(TypeAggResult::getCount)
                                .orElse(0);

                        double percentage = total > 0 ? (count * 100.0) / total : 0.0;

                        stats.add(UserStatusStats.builder()
                                .status(status)
                                .count(count)
                                .percentage(Math.round(percentage * 100.0) / 100.0)
                                .build());
                    }
                    return stats;
                });
    }

    /**
     * Get count of verified users (email or phone verified).
     */
    private Mono<Integer> getVerifiedUsersCount() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("emailVerified").is(true)
                        .orOperator(Criteria.where("phoneVerified").is(true))),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, "users", CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    /**
     * Get count of active users (active flag is true).
     */
    private Mono<Integer> getActiveUsersCount() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("active").is(true)),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, "users", CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    /**
     * Get count of new users registered this month.
     */
    private Mono<Integer> getNewUsersThisMonthCount() {
        LocalDateTime startOfMonth = LocalDateTime.now()
                .with(TemporalAdjusters.firstDayOfMonth())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        Aggregation aggregation = newAggregation(
                match(Criteria.where("createdAt").gte(startOfMonth)),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, "users", CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    /**
     * Get count of new users registered this week.
     */
    private Mono<Integer> getNewUsersThisWeekCount() {
        LocalDateTime startOfWeek = LocalDateTime.now()
                .with(java.time.DayOfWeek.MONDAY)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        Aggregation aggregation = newAggregation(
                match(Criteria.where("createdAt").gte(startOfWeek)),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, "users", CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    /**
     * Get count of users registered last month (for growth calculation).
     */
    private Mono<Integer> getLastMonthUsersCount() {
        LocalDateTime startOfLastMonth = LocalDateTime.now()
                .minusMonths(1)
                .with(TemporalAdjusters.firstDayOfMonth())
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        LocalDateTime endOfLastMonth = LocalDateTime.now()
                .with(TemporalAdjusters.firstDayOfMonth())
                .minusDays(1)
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        Aggregation aggregation = newAggregation(
                match(Criteria.where("createdAt").gte(startOfLastMonth).lte(endOfLastMonth)),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, "users", CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    private int getCountForType(List<UserRoleStats> stats, UserType userType) {
        return stats.stream()
                .filter(s -> s.getRole() == userType)
                .findFirst()
                .map(UserRoleStats::getCount)
                .orElse(0);
    }

    private int getCountForStatus(List<UserStatusStats> stats, AccountStatus status) {
        return stats.stream()
                .filter(s -> s.getStatus() == status)
                .findFirst()
                .map(UserStatusStats::getCount)
                .orElse(0);
    }

    // ==================== Helper Classes ====================

    @lombok.Data
    private static class TypeAggResult {
        private String id; // The _id field from $group (role name)
        private int count;
    }

    @lombok.Data
    private static class CountResult {
        private int count;
    }
}
