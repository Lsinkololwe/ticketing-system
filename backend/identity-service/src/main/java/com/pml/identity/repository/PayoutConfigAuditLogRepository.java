package com.pml.identity.repository;

import com.pml.identity.domain.model.PayoutConfigAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Repository for payout configuration audit logs.
 *
 * SECURITY FEATURES:
 * - Indexed queries for performance
 * - Reactive (non-blocking) API
 * - PCI-DSS compliant audit trail storage
 */
@Repository
public interface PayoutConfigAuditLogRepository extends ReactiveMongoRepository<PayoutConfigAuditLog, String> {

    /**
     * Find audit logs for an organization (most recent first).
     *
     * @param organizationId the organization ID
     * @param pageable pagination
     * @return audit logs
     */
    Flux<PayoutConfigAuditLog> findByOrganizationIdOrderByTimestampDesc(
        String organizationId,
        Pageable pageable
    );

    /**
     * Find audit logs for a user (security monitoring).
     *
     * @param userId the user ID
     * @param pageable pagination
     * @return audit logs
     */
    Flux<PayoutConfigAuditLog> findByUserIdOrderByTimestampDesc(
        String userId,
        Pageable pageable
    );

    /**
     * Find audit logs by action type (for security analysis).
     *
     * @param action the action type
     * @param pageable pagination
     * @return audit logs
     */
    Flux<PayoutConfigAuditLog> findByActionOrderByTimestampDesc(
        PayoutConfigAuditLog.AuditAction action,
        Pageable pageable
    );

    /**
     * Find failed operations (security incidents).
     *
     * @param success false to find failures
     * @param pageable pagination
     * @return audit logs
     */
    Flux<PayoutConfigAuditLog> findBySuccessOrderByTimestampDesc(
        boolean success,
        Pageable pageable
    );

    /**
     * Find audit logs within a time range (compliance reporting).
     *
     * @param organizationId the organization ID
     * @param startTime start of time range
     * @param endTime end of time range
     * @return audit logs
     */
    Flux<PayoutConfigAuditLog> findByOrganizationIdAndTimestampBetweenOrderByTimestampDesc(
        String organizationId,
        Instant startTime,
        Instant endTime
    );

    /**
     * Count audit logs for an organization.
     *
     * @param organizationId the organization ID
     * @return count
     */
    Mono<Long> countByOrganizationId(String organizationId);

    /**
     * Delete old audit logs (data retention policy).
     * NOTE: Check PCI-DSS requirements before deleting (minimum 1 year).
     *
     * @param timestamp delete logs before this timestamp
     * @return deleted count
     */
    Mono<Long> deleteByTimestampBefore(Instant timestamp);
}
