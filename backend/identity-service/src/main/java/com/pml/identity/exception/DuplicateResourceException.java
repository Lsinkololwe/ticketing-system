package com.pml.identity.exception;

/**
 * Exception thrown when attempting to create a duplicate resource.
 */
public class DuplicateResourceException extends RuntimeException {

    private final String resourceType;
    private final String fieldName;
    private final String fieldValue;

    public DuplicateResourceException(String resourceType, String fieldName, String fieldValue) {
        super(String.format("%s with %s '%s' already exists", resourceType, fieldName, fieldValue));
        this.resourceType = resourceType;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }
}
