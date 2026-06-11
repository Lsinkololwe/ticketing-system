package com.pml.identity.repository;

import com.pml.identity.domain.model.AuditLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Repository for audit logs
 */
@Repository
public interface AuditLogRepository extends ReactiveMongoRepository<AuditLog, String> {

    /**
     * Find audit logs for a specific user
     */
    Flux<AuditLog> findByUserIdOrderByTimestampDesc(String userId);

    /**
     * Find audit logs by action type
     */
    Flux<AuditLog> findByActionOrderByTimestampDesc(AuditLog.AuditAction action);

    /**
     * Find audit logs by status
     */
    Flux<AuditLog> findByStatusOrderByTimestampDesc(AuditLog.AuditStatus status);

    /**
     * Find audit logs within a time range
     */
    Flux<AuditLog> findByTimestampBetweenOrderByTimestampDesc(Instant startTime, Instant endTime);

    /**
     * Find audit logs for a specific user within a time range
     */
    Flux<AuditLog> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
            String userId, Instant startTime, Instant endTime);

    /**
     * Count failed audit logs for a user (for security monitoring)
     */
    Mono<Long> countByUserIdAndStatusAndTimestampAfter(
            String userId, AuditLog.AuditStatus status, Instant since);
}
