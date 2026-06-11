package com.pml.identity.service.impl;

import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.repository.AuditLogRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.RoleSyncService;
import com.pml.shared.constants.UserType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of RoleSyncService with resilience patterns
 *
 * Security Features:
 * - Circuit breaker for Keycloak failures (prevents cascading failures)
 * - Retry logic with exponential backoff (handles transient failures)
 * - Idempotent operations (safe to retry without side effects)
 * - Comprehensive audit logging (OWASP compliance)
 * - Error sanitization (prevents information leakage)
 *
 * Resilience Patterns:
 * - Circuit breaker opens after 5 consecutive failures
 * - Retry up to 3 times with exponential backoff
 * - Fallback to partial success if Keycloak unavailable
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleSyncServiceImpl implements RoleSyncService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final AuditLogRepository auditLogRepository;

    private static final String CIRCUIT_BREAKER_NAME = "keycloak";
    private static final String RETRY_NAME = "keycloak";

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "grantOrganizerRoleFallback")
    @Retry(name = RETRY_NAME)
    public Mono<Void> grantOrganizerRole(String userId, String performedBy, String organizationId) {
        log.info("Granting ORGANIZER role to user {} by {}", userId, performedBy);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    // Check if user already has ORGANIZER role (idempotency)
                    if (user.hasRole(UserType.ORGANIZER)) {
                        log.info("User {} already has ORGANIZER role, skipping grant", userId);
                        return createSuccessAudit(AuditLog.AuditAction.ROLE_GRANT, userId, performedBy, organizationId, "ORGANIZER (already exists)")
                                .then(Mono.empty());
                    }

                    // Add role to MongoDB
                    boolean added = user.addRole(UserType.ORGANIZER);
                    if (!added) {
                        return Mono.error(new IllegalStateException("Failed to add ORGANIZER role to user " + userId));
                    }

                    user.setUpdatedAt(Instant.now());

                    // Save to MongoDB first (single source of truth)
                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                // Sync to Keycloak (best effort)
                                return keycloakService.addRoleToUser(userId, UserType.ORGANIZER.name())
                                        .then(createSuccessAudit(
                                                AuditLog.AuditAction.ROLE_GRANT,
                                                userId,
                                                performedBy,
                                                organizationId,
                                                "ORGANIZER"
                                        ))
                                        .onErrorResume(keycloakError -> {
                                            // Log Keycloak failure but don't rollback MongoDB
                                            log.error("Failed to grant role in Keycloak for user {}: {}", userId, keycloakError.getMessage());
                                            return createFailureAudit(
                                                    AuditLog.AuditAction.KEYCLOAK_SYNC_FAILURE,
                                                    userId,
                                                    performedBy,
                                                    organizationId,
                                                    keycloakError.getMessage(),
                                                    "KEYCLOAK_UNAVAILABLE"
                                            );
                                        });
                            });
                })
                .then();
    }

    /**
     * Fallback method when circuit breaker is open (Keycloak unavailable)
     * Grants role in MongoDB only, logs failure for manual reconciliation
     */
    private Mono<Void> grantOrganizerRoleFallback(String userId, String performedBy, String organizationId, Throwable throwable) {
        log.warn("Circuit breaker open for Keycloak, granting role in MongoDB only for user {}: {}", userId, throwable.getMessage());

        return userRepository.findById(userId)
                .flatMap(user -> {
                    if (user.hasRole(UserType.ORGANIZER)) {
                        log.info("User {} already has ORGANIZER role (fallback)", userId);
                        return Mono.empty();
                    }

                    user.addRole(UserType.ORGANIZER);
                    user.setUpdatedAt(Instant.now());

                    return userRepository.save(user)
                            .then(createFailureAudit(
                                    AuditLog.AuditAction.KEYCLOAK_SYNC_FAILURE,
                                    userId,
                                    performedBy,
                                    organizationId,
                                    "Keycloak unavailable - role granted in MongoDB only",
                                    "CIRCUIT_BREAKER_OPEN"
                            ));
                })
                .then();
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "revokeOrganizerRoleFallback")
    @Retry(name = RETRY_NAME)
    public Mono<Void> revokeOrganizerRole(String userId, String performedBy, String organizationId) {
        log.info("Revoking ORGANIZER role from user {} by {}", userId, performedBy);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    // Check if user has ORGANIZER role (idempotency)
                    if (!user.hasRole(UserType.ORGANIZER)) {
                        log.info("User {} does not have ORGANIZER role, skipping revoke", userId);
                        return createSuccessAudit(AuditLog.AuditAction.ROLE_REVOKE, userId, performedBy, organizationId, "ORGANIZER (not present)")
                                .then(Mono.empty());
                    }

                    // Remove role from MongoDB
                    boolean removed = user.removeRole(UserType.ORGANIZER);
                    if (!removed) {
                        return Mono.error(new IllegalStateException("Failed to remove ORGANIZER role from user " + userId));
                    }

                    user.setUpdatedAt(Instant.now());

                    // Save to MongoDB first
                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                // Sync to Keycloak (best effort)
                                return keycloakService.removeRoleFromUser(userId, UserType.ORGANIZER.name())
                                        .then(createSuccessAudit(
                                                AuditLog.AuditAction.ROLE_REVOKE,
                                                userId,
                                                performedBy,
                                                organizationId,
                                                "ORGANIZER"
                                        ))
                                        .onErrorResume(keycloakError -> {
                                            log.error("Failed to revoke role in Keycloak for user {}: {}", userId, keycloakError.getMessage());
                                            return createFailureAudit(
                                                    AuditLog.AuditAction.KEYCLOAK_SYNC_FAILURE,
                                                    userId,
                                                    performedBy,
                                                    organizationId,
                                                    keycloakError.getMessage(),
                                                    "KEYCLOAK_UNAVAILABLE"
                                            );
                                        });
                            });
                })
                .then();
    }

    /**
     * Fallback method for role revocation when circuit breaker is open
     */
    private Mono<Void> revokeOrganizerRoleFallback(String userId, String performedBy, String organizationId, Throwable throwable) {
        log.warn("Circuit breaker open for Keycloak, revoking role in MongoDB only for user {}: {}", userId, throwable.getMessage());

        return userRepository.findById(userId)
                .flatMap(user -> {
                    if (!user.hasRole(UserType.ORGANIZER)) {
                        log.info("User {} does not have ORGANIZER role (fallback)", userId);
                        return Mono.empty();
                    }

                    user.removeRole(UserType.ORGANIZER);
                    user.setUpdatedAt(Instant.now());

                    return userRepository.save(user)
                            .then(createFailureAudit(
                                    AuditLog.AuditAction.KEYCLOAK_SYNC_FAILURE,
                                    userId,
                                    performedBy,
                                    organizationId,
                                    "Keycloak unavailable - role revoked in MongoDB only",
                                    "CIRCUIT_BREAKER_OPEN"
                            ));
                })
                .then();
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "syncUserRolesFallback")
    @Retry(name = RETRY_NAME)
    public Mono<Void> syncUserRoles(String userId, String performedBy) {
        log.info("Syncing all roles for user {} by {}", userId, performedBy);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    Set<UserType> roles = user.getRoles();
                    if (roles == null || roles.isEmpty()) {
                        roles = EnumSet.of(UserType.CUSTOMER);
                    }

                    // Sync to Keycloak
                    return keycloakService.syncUserRoles(userId, roles)
                            .then(createSuccessAudit(
                                    AuditLog.AuditAction.KEYCLOAK_SYNC_SUCCESS,
                                    userId,
                                    performedBy,
                                    null,
                                    "Synced " + roles.size() + " roles"
                            ))
                            .onErrorResume(keycloakError -> {
                                log.error("Failed to sync roles to Keycloak for user {}: {}", userId, keycloakError.getMessage());
                                return createFailureAudit(
                                        AuditLog.AuditAction.KEYCLOAK_SYNC_FAILURE,
                                        userId,
                                        performedBy,
                                        null,
                                        keycloakError.getMessage(),
                                        "KEYCLOAK_SYNC_ERROR"
                                );
                            });
                })
                .then();
    }

    /**
     * Fallback method for full role sync when circuit breaker is open
     */
    private Mono<Void> syncUserRolesFallback(String userId, String performedBy, Throwable throwable) {
        log.warn("Circuit breaker open for Keycloak, cannot sync roles for user {}: {}", userId, throwable.getMessage());

        return createFailureAudit(
                AuditLog.AuditAction.KEYCLOAK_SYNC_FAILURE,
                userId,
                performedBy,
                null,
                "Keycloak unavailable - manual sync required",
                "CIRCUIT_BREAKER_OPEN"
        ).then();
    }

    @Override
    public Mono<Boolean> isKeycloakHealthy() {
        return keycloakService.countUsers()
                .map(count -> true)
                .onErrorReturn(false)
                .timeout(java.time.Duration.ofSeconds(5))
                .onErrorReturn(false);
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private Mono<Void> createSuccessAudit(AuditLog.AuditAction action, String userId, String performedBy, String organizationId, String details) {
        Map<String, String> metadata = new HashMap<>();
        if (organizationId != null) {
            metadata.put("organizationId", organizationId);
        }
        metadata.put("details", details);

        AuditLog auditLog = AuditLog.builder()
                .action(action)
                .userId(userId)
                .performedBy(performedBy)
                .resourceId(organizationId)
                .resourceType("Organization")
                .status(AuditLog.AuditStatus.SUCCESS)
                .timestamp(Instant.now())
                .metadata(metadata)
                .build();

        return auditLogRepository.save(auditLog)
                .doOnSuccess(log -> logAuditEvent(auditLog))
                .then();
    }

    private Mono<Void> createFailureAudit(AuditLog.AuditAction action, String userId, String performedBy, String organizationId, String errorMessage, String errorCode) {
        Map<String, String> metadata = new HashMap<>();
        if (organizationId != null) {
            metadata.put("organizationId", organizationId);
        }

        AuditLog auditLog = AuditLog.failure(action, userId, performedBy, errorMessage, errorCode);
        auditLog.setResourceId(organizationId);
        auditLog.setResourceType("Organization");
        auditLog.setMetadata(metadata);

        return auditLogRepository.save(auditLog)
                .doOnSuccess(log -> logAuditEvent(auditLog))
                .then();
    }

    /**
     * Log audit event to application logs (OWASP logging format)
     */
    private void logAuditEvent(AuditLog auditLog) {
        String logMessage = String.format(
                "SECURITY_EVENT: %s | User: %s | PerformedBy: %s | Status: %s | Time: %s",
                auditLog.getAction().getCode(),
                auditLog.getUserId(),
                auditLog.getPerformedBy(),
                auditLog.getStatus(),
                auditLog.getTimestamp()
        );

        if (auditLog.getStatus() == AuditLog.AuditStatus.SUCCESS) {
            log.info(logMessage);
        } else {
            log.error("{} | Error: {} | ErrorCode: {}", logMessage, auditLog.getErrorMessage(), auditLog.getErrorCode());
        }
    }
}
