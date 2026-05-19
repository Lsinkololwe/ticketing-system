package com.pml.identity.exception;

/**
 * Exception thrown when an organization is not found.
 */
public class OrganizationNotFoundException extends RuntimeException {

    private final String organizationId;

    public OrganizationNotFoundException(String organizationId) {
        super("Organization not found: " + organizationId);
        this.organizationId = organizationId;
    }

    public OrganizationNotFoundException(String message, String organizationId) {
        super(message);
        this.organizationId = organizationId;
    }

    public String getOrganizationId() {
        return organizationId;
    }
}
