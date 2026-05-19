package com.pml.booking.web.graphql.exception;

import com.pml.booking.exception.*;
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

/**
 * GraphQL Exception Resolver for Booking Service
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

        if (ex instanceof TicketNotFoundException e) {
            log.warn("Ticket not found at {}: {}", fieldPath, e.getMessage());
            return buildError(ex, ErrorType.NOT_FOUND, "TICKET_NOT_FOUND", env);
        }

        if (ex instanceof TicketAlreadyUsedException e) {
            log.warn("Ticket already used at {}: {}", fieldPath, e.getMessage());
            return buildError(ex, ErrorType.BAD_REQUEST, "TICKET_ALREADY_USED", env);
        }

        if (ex instanceof RefundNotAllowedException e) {
            log.warn("Refund not allowed at {}: {}", fieldPath, e.getMessage());
            return buildError(ex, ErrorType.BAD_REQUEST, "REFUND_NOT_ALLOWED", env);
        }

        if (ex instanceof PaymentFailedException e) {
            log.error("Payment failed at {}: {}", fieldPath, e.getMessage());
            return buildError(ex, ErrorType.BAD_REQUEST, "PAYMENT_FAILED", env);
        }

        if (ex instanceof InsufficientEscrowBalanceException e) {
            log.warn("Insufficient escrow balance at {}: {}", fieldPath, e.getMessage());
            return buildError(ex, ErrorType.BAD_REQUEST, "INSUFFICIENT_BALANCE", env);
        }

        if (ex instanceof DoubleBookingException e) {
            log.warn("Double booking attempt at {}: {}", fieldPath, e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message("This ticket has already been booked. Please try another ticket.")
                    .extensions(java.util.Map.of(
                            "code", "DOUBLE_BOOKING",
                            "ticketId", e.getTicketId() != null ? e.getTicketId() : "",
                            "eventId", e.getEventId() != null ? e.getEventId() : ""
                    ))
                    .build();
        }

        if (ex instanceof OptimisticLockingFailureException e) {
            log.warn("Concurrent modification detected at {}: {}", fieldPath, e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message("The resource was modified by another request. Please refresh and try again.")
                    .extensions(java.util.Map.of("code", "CONCURRENT_MODIFICATION"))
                    .build();
        }

        if (ex instanceof AccessDeniedException) {
            log.warn("Access denied at {}", fieldPath);
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.FORBIDDEN)
                    .message("You do not have permission to perform this action")
                    .extensions(java.util.Map.of("code", "ACCESS_DENIED"))
                    .build();
        }

        if (ex instanceof AuthenticationException) {
            log.warn("Authentication required at {}", fieldPath);
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.UNAUTHORIZED)
                    .message("Authentication required")
                    .extensions(java.util.Map.of("code", "UNAUTHORIZED"))
                    .build();
        }

        if (ex instanceof IllegalArgumentException e) {
            log.warn("Invalid argument at {}: {}", fieldPath, e.getMessage());
            return buildError(ex, ErrorType.BAD_REQUEST, "INVALID_ARGUMENT", env);
        }

        if (ex instanceof IllegalStateException e) {
            log.warn("Invalid state at {}: {}", fieldPath, e.getMessage());
            return buildError(ex, ErrorType.BAD_REQUEST, "INVALID_STATE", env);
        }

        // Log unexpected errors with full stack trace
        log.error("Unexpected error at {}", fieldPath, ex);
        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.INTERNAL_ERROR)
                .message("An unexpected error occurred. Please try again later.")
                .extensions(java.util.Map.of("code", "INTERNAL_ERROR"))
                .build();
    }

    private GraphQLError buildError(Throwable ex, ErrorType errorType, String code, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(errorType)
                .message(ex.getMessage())
                .extensions(java.util.Map.of("code", code))
                .build();
    }
}
