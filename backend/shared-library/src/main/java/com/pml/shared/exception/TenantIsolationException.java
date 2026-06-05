package com.pml.shared.exception;

/**
 * Exception thrown when tenant isolation validation fails.
 * <p>
 * Indicates a potential security violation where a user attempted to access
 * or modify a resource belonging to a different organization.
 * </p>
 * <p>
 * OWASP A01:2021 - Broken Access Control prevention.
 * </p>
 *
 * @see com.pml.shared.service.TenantValidationService
 * @since 1.0.0
 */
public class TenantIsolationException extends RuntimeException {

    public TenantIsolationException(String message) {
        super(message);
    }

    public TenantIsolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
