package com.pml.shared.exception;

import lombok.Getter;
import org.bson.Document;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when MongoDB document validation fails.
 * <p>
 * This exception captures detailed validation failure information including:
 * <ul>
 *     <li>The collection name where validation failed</li>
 *     <li>The document that failed validation (sanitized)</li>
 *     <li>List of specific validation errors from MongoDB</li>
 * </ul>
 * </p>
 * <p>
 * Used in conjunction with {@link com.pml.shared.exception.MongoValidationErrorHandler}
 * to provide user-friendly error messages in GraphQL responses.
 * </p>
 *
 * @see com.pml.shared.exception.MongoValidationErrorHandler
 * @since 1.0.0
 */
@Getter
public class MongoSchemaValidationException extends RuntimeException {

    /**
     * The name of the MongoDB collection where validation failed.
     */
    private final String collectionName;

    /**
     * The document that failed validation (may be sanitized to remove sensitive data).
     */
    private final Document failedDocument;

    /**
     * List of validation error messages extracted from MongoDB's error response.
     * Examples: "Missing required field: organizationId", "Invalid email format"
     */
    private final List<String> validationErrors;

    /**
     * Original MongoDB error code (e.g., 121 for document validation failure).
     */
    private final Integer errorCode;

    /**
     * Constructs a new MongoSchemaValidationException.
     *
     * @param message Human-readable error message
     * @param collectionName MongoDB collection name
     * @param failedDocument The document that failed validation
     * @param validationErrors List of specific validation failures
     * @param errorCode MongoDB error code
     */
    public MongoSchemaValidationException(
            String message,
            String collectionName,
            Document failedDocument,
            List<String> validationErrors,
            Integer errorCode) {
        super(message);
        this.collectionName = collectionName;
        this.failedDocument = failedDocument;
        this.validationErrors = validationErrors != null ? validationErrors : Collections.emptyList();
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new MongoSchemaValidationException with a cause.
     *
     * @param message Human-readable error message
     * @param collectionName MongoDB collection name
     * @param failedDocument The document that failed validation
     * @param validationErrors List of specific validation failures
     * @param errorCode MongoDB error code
     * @param cause The underlying exception
     */
    public MongoSchemaValidationException(
            String message,
            String collectionName,
            Document failedDocument,
            List<String> validationErrors,
            Integer errorCode,
            Throwable cause) {
        super(message, cause);
        this.collectionName = collectionName;
        this.failedDocument = failedDocument;
        this.validationErrors = validationErrors != null ? validationErrors : Collections.emptyList();
        this.errorCode = errorCode;
    }

    /**
     * Returns a summary of validation errors as a single string.
     *
     * @return Comma-separated validation errors, or "Unknown validation error"
     */
    public String getValidationErrorsSummary() {
        if (validationErrors.isEmpty()) {
            return "Unknown validation error";
        }
        return String.join(", ", validationErrors);
    }

    @Override
    public String toString() {
        return String.format(
                "MongoSchemaValidationException[collection=%s, errorCode=%d, errors=%s]",
                collectionName,
                errorCode,
                getValidationErrorsSummary()
        );
    }
}
