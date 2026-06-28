package com.pml.identity.graphql.exception;

import com.netflix.graphql.dgs.exceptions.DgsException;
import com.netflix.graphql.types.errors.ErrorType;
import com.netflix.graphql.types.errors.TypedGraphQLError;
import com.pml.shared.exception.MongoSchemaValidationException;
import com.pml.shared.exception.TenantIsolationException;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * GraphQL exception handler for MongoDB validation errors in Identity Service.
 * <p>
 * Converts domain exceptions to properly formatted GraphQL errors with:
 * <ul>
 *     <li>Appropriate error type (BAD_REQUEST, PERMISSION_DENIED, INTERNAL)</li>
 *     <li>User-friendly error messages</li>
 *     <li>Additional context in extensions field</li>
 * </ul>
 * </p>
 * <p>
 * Handled exceptions:
 * <ul>
 *     <li>{@link MongoSchemaValidationException}: Document validation failures</li>
 *     <li>{@link TenantIsolationException}: Tenant isolation violations</li>
 * </ul>
 * </p>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class MongoValidationExceptionHandler implements DataFetcherExceptionHandler {

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {

        Throwable exception = handlerParameters.getException();

        // Unwrap DgsException if present
        if (exception instanceof DgsException dgsException) {
            if (dgsException.getCause() != null) {
                exception = dgsException.getCause();
            }
        }

        // Handle MongoSchemaValidationException
        if (exception instanceof MongoSchemaValidationException validationEx) {
            return handleMongoValidation(validationEx, handlerParameters);
        }

        // Handle TenantIsolationException
        if (exception instanceof TenantIsolationException tenantEx) {
            return handleTenantIsolation(tenantEx, handlerParameters);
        }

        // Unhandled exception: log the FULL stack trace so it is traceable on the
        // server, then return a generic message to the client (no internal leakage).
        log.error("Unhandled exception in GraphQL field '{}' at path {}: {}",
                handlerParameters.getField() != null
                        ? handlerParameters.getField().getName() : "unknown",
                handlerParameters.getPath(),
                exception.toString(),
                exception);

        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult()
                        .error(TypedGraphQLError.newInternalErrorBuilder()
                                .message("An unexpected error occurred")
                                .path(handlerParameters.getPath())
                                .build())
                        .build()
        );
    }

    /**
     * Handles MongoDB schema validation exceptions.
     * <p>
     * Returns a BAD_REQUEST error with validation details in extensions.
     * </p>
     */
    private CompletableFuture<DataFetcherExceptionHandlerResult> handleMongoValidation(
            MongoSchemaValidationException ex,
            DataFetcherExceptionHandlerParameters params) {

        log.warn("MongoDB validation failed for collection {}: {}",
                ex.getCollectionName(),
                ex.getValidationErrorsSummary());

        TypedGraphQLError error = TypedGraphQLError.newBuilder()
                .message("Validation failed: " + ex.getValidationErrorsSummary())
                .errorType(ErrorType.BAD_REQUEST)
                .path(params.getPath())
                .extensions(Map.of(
                        "errorType", "VALIDATION_ERROR",
                        "collection", ex.getCollectionName(),
                        "validationErrors", ex.getValidationErrors(),
                        "errorCode", ex.getErrorCode() != null ? ex.getErrorCode() : 0
                ))
                .build();

        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult()
                        .error(error)
                        .build()
        );
    }

    /**
     * Handles tenant isolation exceptions.
     * <p>
     * Returns a PERMISSION_DENIED error (OWASP A01 - Broken Access Control).
     * </p>
     */
    private CompletableFuture<DataFetcherExceptionHandlerResult> handleTenantIsolation(
            TenantIsolationException ex,
            DataFetcherExceptionHandlerParameters params) {

        log.error("Tenant isolation violation: {}", ex.getMessage());

        TypedGraphQLError error = TypedGraphQLError.newBuilder()
                .message("Access denied: " + ex.getMessage())
                .errorType(ErrorType.PERMISSION_DENIED)
                .path(params.getPath())
                .extensions(Map.of(
                        "errorType", "TENANT_ISOLATION_VIOLATION",
                        "securityIncident", true
                ))
                .build();

        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult()
                        .error(error)
                        .build()
        );
    }
}
