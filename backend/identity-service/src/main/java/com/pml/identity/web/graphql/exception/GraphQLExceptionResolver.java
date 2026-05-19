package com.pml.identity.web.graphql.exception;

import com.pml.identity.exception.*;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GraphQL Exception Resolver for Identity Service
 *
 * Business Intent: Translates application exceptions into GraphQL-compliant
 * error responses with appropriate error types and messages. Ensures sensitive
 * details are not exposed while providing actionable error information.
 *
 * Error Type Mapping:
 * - NOT_FOUND: Resource does not exist
 * - BAD_REQUEST: Invalid input or business rule violation
 * - UNAUTHORIZED: Authentication required
 * - FORBIDDEN: Insufficient permissions
 * - INTERNAL_ERROR: Unexpected server errors
 */
@Slf4j
@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        String fieldPath = env.getExecutionStepInfo().getPath().toString();

        if (ex instanceof UserNotFoundException e) {
            log.warn("User not found at {}: {}", fieldPath, e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.NOT_FOUND)
                    .message(e.getMessage())
                    .extensions(Map.of(
                            "code", "USER_NOT_FOUND",
                            "userId", e.getUserId() != null ? e.getUserId() : ""
                    ))
                    .build();
        }

        if (ex instanceof OrganizationNotFoundException e) {
            log.warn("Organization not found at {}: {}", fieldPath, e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.NOT_FOUND)
                    .message(e.getMessage())
                    .extensions(Map.of(
                            "code", "ORGANIZATION_NOT_FOUND",
                            "organizationId", e.getOrganizationId() != null ? e.getOrganizationId() : ""
                    ))
                    .build();
        }

        if (ex instanceof DuplicateResourceException e) {
            log.warn("Duplicate resource at {}: {}", fieldPath, e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(e.getMessage())
                    .extensions(Map.of(
                            "code", "DUPLICATE_RESOURCE",
                            "resourceType", e.getResourceType() != null ? e.getResourceType() : "",
                            "fieldName", e.getFieldName() != null ? e.getFieldName() : "",
                            "fieldValue", e.getFieldValue() != null ? e.getFieldValue() : ""
                    ))
                    .build();
        }

        if (ex instanceof InvalidInvitationException e) {
            log.warn("Invalid invitation at {}: {}", fieldPath, e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(e.getMessage())
                    .extensions(Map.of(
                            "code", "INVALID_INVITATION",
                            "invitationId", e.getInvitationId() != null ? e.getInvitationId() : "",
                            "reason", e.getReason() != null ? e.getReason() : ""
                    ))
                    .build();
        }

        if (ex instanceof OptimisticLockingFailureException e) {
            log.warn("Concurrent modification detected at {}: {}", fieldPath, e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message("The resource was modified by another request. Please refresh and try again.")
                    .extensions(Map.of("code", "CONCURRENT_MODIFICATION"))
                    .build();
        }

        if (ex instanceof AccessDeniedException) {
            log.warn("Access denied at {}", fieldPath);
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.FORBIDDEN)
                    .message("You do not have permission to perform this action")
                    .extensions(Map.of("code", "ACCESS_DENIED"))
                    .build();
        }

        if (ex instanceof AuthenticationException) {
            log.warn("Authentication required at {}", fieldPath);
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.UNAUTHORIZED)
                    .message("Authentication required")
                    .extensions(Map.of("code", "UNAUTHORIZED"))
                    .build();
        }

        if (ex instanceof IllegalArgumentException e) {
            log.warn("Invalid argument at {}: {}", fieldPath, e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(e.getMessage())
                    .extensions(Map.of("code", "INVALID_ARGUMENT"))
                    .build();
        }

        if (ex instanceof IllegalStateException e) {
            log.warn("Invalid state at {}: {}", fieldPath, e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(e.getMessage())
                    .extensions(Map.of("code", "INVALID_STATE"))
                    .build();
        }

        // Log unexpected errors with full stack trace
        log.error("Unexpected error at {}", fieldPath, ex);
        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.INTERNAL_ERROR)
                .message("An unexpected error occurred. Please try again later.")
                .extensions(Map.of("code", "INTERNAL_ERROR"))
                .build();
    }
}
