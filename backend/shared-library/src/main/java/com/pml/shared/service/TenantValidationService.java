package com.pml.shared.service;

import com.pml.shared.exception.TenantIsolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

/**
 * Service for validating tenant context in multi-tenant operations.
 * <p>
 * Implements OWASP A01:2021 - Broken Access Control prevention by ensuring
 * that documents always belong to the correct organization/tenant.
 * </p>
 * <p>
 * Usage in service layer:
 * <pre>
 * public Mono&lt;Event&gt; createEvent(CreateEventInput input, String organizationId) {
 *     Event event = mapper.toEntity(input);
 *     event.setOrganizationId(organizationId);
 *
 *     return tenantValidationService.validateTenantContext(event, organizationId)
 *         .flatMap(eventRepository::save);
 * }
 * </pre>
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
public class TenantValidationService {

    /**
     * Validates that a document has the correct organizationId for the current user.
     * <p>
     * Prevents horizontal privilege escalation by ensuring users can only
     * access/modify documents within their organization.
     * </p>
     * <p>
     * This method uses reflection to extract the organizationId field from the document.
     * It supports:
     * <ul>
     *     <li>Field name: organizationId</li>
     *     <li>Getter method: getOrganizationId()</li>
     * </ul>
     * </p>
     *
     * @param document The document to validate (must have organizationId field)
     * @param expectedOrgId The organization ID of the current authenticated user
     * @param <T> The document type
     * @return A Mono emitting the validated document
     * @throws TenantIsolationException if organizationId mismatch or missing
     */
    public <T> Mono<T> validateTenantContext(T document, String expectedOrgId) {
        return Mono.fromCallable(() -> {
            if (document == null) {
                throw new TenantIsolationException("Document cannot be null for tenant validation");
            }

            if (expectedOrgId == null || expectedOrgId.isBlank()) {
                throw new TenantIsolationException("Expected organizationId cannot be null or empty");
            }

            // Extract organizationId from document
            String actualOrgId = extractOrganizationId(document);

            // Validate match
            if (actualOrgId == null || actualOrgId.isBlank()) {
                log.error("SECURITY VIOLATION: Document of type {} missing organizationId field",
                        document.getClass().getSimpleName());
                throw new TenantIsolationException(
                        "Document must have an organizationId for tenant isolation"
                );
            }

            if (!expectedOrgId.equals(actualOrgId)) {
                log.error(
                        "SECURITY VIOLATION: Tenant isolation breach attempt. " +
                        "Expected organizationId: {}, actual: {}, documentType: {}",
                        expectedOrgId, actualOrgId, document.getClass().getSimpleName()
                );
                throw new TenantIsolationException(
                        String.format(
                                "Access denied: Document belongs to organization %s, but user is in organization %s",
                                maskOrgId(actualOrgId),
                                maskOrgId(expectedOrgId)
                        )
                );
            }

            log.debug("Tenant validation passed for document type {} with organizationId {}",
                    document.getClass().getSimpleName(), maskOrgId(expectedOrgId));

            return document;
        });
    }

    /**
     * Validates tenant context for batch operations.
     * <p>
     * Ensures all documents in a list belong to the expected organization.
     * </p>
     *
     * @param documents List of documents to validate
     * @param expectedOrgId The organization ID of the current authenticated user
     * @param <T> The document type
     * @return A Mono emitting the validated list
     */
    public <T> Mono<Iterable<T>> validateTenantContextBatch(
            Iterable<T> documents,
            String expectedOrgId) {

        return Mono.fromCallable(() -> {
            for (T document : documents) {
                // Validate each document synchronously
                validateTenantContext(document, expectedOrgId).block();
            }
            return documents;
        });
    }

    /**
     * Extracts organizationId from a document using reflection.
     * <p>
     * Tries the following methods in order:
     * <ol>
     *     <li>getOrganizationId() method</li>
     *     <li>organizationId field</li>
     * </ol>
     * </p>
     *
     * @param document The document to extract organizationId from
     * @return The organizationId value, or null if not found
     */
    private String extractOrganizationId(Object document) {
        try {
            Class<?> clazz = document.getClass();

            // Try getter method first (preferred)
            try {
                Method getter = clazz.getMethod("getOrganizationId");
                Object result = getter.invoke(document);
                return result != null ? result.toString() : null;
            } catch (NoSuchMethodException e) {
                // Getter not found, try field access
            }

            // Try direct field access
            try {
                var field = clazz.getDeclaredField("organizationId");
                field.setAccessible(true);
                Object result = field.get(document);
                return result != null ? result.toString() : null;
            } catch (NoSuchFieldException e) {
                // Field not found
                log.warn("Class {} does not have organizationId field or getter method",
                        clazz.getSimpleName());
                return null;
            }

        } catch (Exception e) {
            log.error("Failed to extract organizationId from document", e);
            throw new TenantIsolationException(
                    "Failed to validate tenant context: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Masks an organization ID for logging (OWASP A09 - Security Logging).
     * <p>
     * Example: 507f1f77bcf86cd799439011 → 507f***9011
     * </p>
     *
     * @param orgId The organization ID to mask
     * @return Masked organization ID
     */
    private String maskOrgId(String orgId) {
        if (orgId == null || orgId.length() < 8) {
            return "***";
        }
        return orgId.substring(0, 4) + "***" + orgId.substring(orgId.length() - 4);
    }
}
