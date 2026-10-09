package com.pml.identity.repository;

import com.pml.identity.domain.model.PayoutConfigAuditLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

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
     * Count audit logs for an organization.
     *
     * @param organizationId the organization ID
     * @return count
     */
    Mono<Long> countByOrganizationId(String organizationId);
}
