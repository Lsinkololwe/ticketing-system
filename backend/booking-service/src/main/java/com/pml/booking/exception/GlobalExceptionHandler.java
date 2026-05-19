package com.pml.booking.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Global Exception Handler for Booking Service
 *
 * Business Intent: Provides consistent error responses across all REST and GraphQL
 * endpoints. Ensures sensitive error details are not exposed while providing
 * meaningful error messages for debugging and user feedback.
 *
 * Error Categories:
 * - Business Errors: Invalid operations (e.g., ticket already used)
 * - Validation Errors: Invalid input data
 * - Security Errors: Unauthorized/forbidden access
 * - System Errors: Internal server errors
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==========================================
    // REST Exception Handlers
    // ==========================================

    @ExceptionHandler(TicketNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTicketNotFound(TicketNotFoundException ex) {
        log.warn("Ticket not found: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "TICKET_NOT_FOUND",
                        ex.getMessage(),
                        HttpStatus.NOT_FOUND.value()
                )));
    }

    @ExceptionHandler(TicketAlreadyUsedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTicketAlreadyUsed(TicketAlreadyUsedException ex) {
        log.warn("Ticket already used: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "TICKET_ALREADY_USED",
                        ex.getMessage(),
                        HttpStatus.CONFLICT.value()
                )));
    }

    @ExceptionHandler(RefundNotAllowedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRefundNotAllowed(RefundNotAllowedException ex) {
        log.warn("Refund not allowed: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "REFUND_NOT_ALLOWED",
                        ex.getMessage(),
                        HttpStatus.BAD_REQUEST.value()
                )));
    }

    @ExceptionHandler(PaymentFailedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handlePaymentFailed(PaymentFailedException ex) {
        log.error("Payment failed: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ErrorResponse(
                        "PAYMENT_FAILED",
                        ex.getMessage(),
                        HttpStatus.PAYMENT_REQUIRED.value()
                )));
    }

    @ExceptionHandler(InsufficientEscrowBalanceException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInsufficientBalance(InsufficientEscrowBalanceException ex) {
        log.warn("Insufficient escrow balance: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INSUFFICIENT_BALANCE",
                        ex.getMessage(),
                        HttpStatus.BAD_REQUEST.value()
                )));
    }

    // ==========================================
    // Financial Engine Exception Handlers
    // ==========================================

    @ExceptionHandler(UnbalancedJournalEntryException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnbalancedJournalEntry(UnbalancedJournalEntryException ex) {
        log.error("Journal entry not balanced: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "UNBALANCED_JOURNAL_ENTRY",
                        ex.getMessage(),
                        HttpStatus.BAD_REQUEST.value(),
                        Map.of(
                                "entryNumber", ex.getEntryNumber() != null ? ex.getEntryNumber() : "",
                                "totalDebits", ex.getTotalDebits() != null ? ex.getTotalDebits().toString() : "",
                                "totalCredits", ex.getTotalCredits() != null ? ex.getTotalCredits().toString() : "",
                                "variance", ex.getVariance() != null ? ex.getVariance().toString() : ""
                        )
                )));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccountNotFound(AccountNotFoundException ex) {
        log.warn("Account not found: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "ACCOUNT_NOT_FOUND",
                        ex.getMessage(),
                        HttpStatus.NOT_FOUND.value(),
                        Map.of("accountCode", ex.getAccountCode() != null ? ex.getAccountCode() : "")
                )));
    }

    @ExceptionHandler(InactiveAccountException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInactiveAccount(InactiveAccountException ex) {
        log.warn("Inactive account: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INACTIVE_ACCOUNT",
                        ex.getMessage(),
                        HttpStatus.BAD_REQUEST.value(),
                        Map.of(
                                "accountCode", ex.getAccountCode() != null ? ex.getAccountCode() : "",
                                "accountName", ex.getAccountName() != null ? ex.getAccountName() : ""
                        )
                )));
    }

    @ExceptionHandler(ReconciliationDiscrepancyException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleReconciliationDiscrepancy(ReconciliationDiscrepancyException ex) {
        log.warn("Reconciliation discrepancy: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "RECONCILIATION_DISCREPANCY",
                        ex.getMessage(),
                        HttpStatus.CONFLICT.value(),
                        Map.of(
                                "runId", ex.getRunId() != null ? ex.getRunId() : "",
                                "reconciliationType", ex.getReconciliationType() != null ? ex.getReconciliationType().name() : "",
                                "variance", ex.getVariance() != null ? ex.getVariance().toString() : "",
                                "unmatchedCount", ex.getUnmatchedCount() != null ? ex.getUnmatchedCount().toString() : ""
                        )
                )));
    }

    @ExceptionHandler(ChargebackProcessingException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleChargebackProcessing(ChargebackProcessingException ex) {
        log.error("Chargeback processing error: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "CHARGEBACK_PROCESSING_ERROR",
                        ex.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        Map.of(
                                "chargebackId", ex.getChargebackId() != null ? ex.getChargebackId() : "",
                                "currentStatus", ex.getCurrentStatus() != null ? ex.getCurrentStatus().name() : "",
                                "operation", ex.getOperation() != null ? ex.getOperation() : ""
                        )
                )));
    }

    @ExceptionHandler(InsufficientRecoveryFundsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInsufficientRecoveryFunds(InsufficientRecoveryFundsException ex) {
        log.warn("Insufficient recovery funds: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INSUFFICIENT_RECOVERY_FUNDS",
                        ex.getMessage(),
                        HttpStatus.BAD_REQUEST.value(),
                        Map.of(
                                "chargebackId", ex.getChargebackId() != null ? ex.getChargebackId() : "",
                                "amountRequired", ex.getAmountRequired() != null ? ex.getAmountRequired().toString() : "",
                                "amountRecovered", ex.getAmountRecovered() != null ? ex.getAmountRecovered().toString() : "",
                                "shortfall", ex.getShortfall() != null ? ex.getShortfall().toString() : ""
                        )
                )));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(
                        "ACCESS_DENIED",
                        "You do not have permission to perform this action",
                        HttpStatus.FORBIDDEN.value()
                )));
    }

    @ExceptionHandler(AuthenticationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        "UNAUTHORIZED",
                        "Authentication required",
                        HttpStatus.UNAUTHORIZED.value()
                )));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INVALID_ARGUMENT",
                        ex.getMessage(),
                        HttpStatus.BAD_REQUEST.value()
                )));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalState(IllegalStateException ex) {
        log.warn("Invalid state: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "INVALID_STATE",
                        ex.getMessage(),
                        HttpStatus.CONFLICT.value()
                )));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred. Please try again later.",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                )));
    }

    /**
     * Standard error response structure for REST endpoints.
     */
    public record ErrorResponse(
            String code,
            String message,
            int status,
            Instant timestamp,
            Map<String, Object> details
    ) {
        public ErrorResponse(String code, String message, int status) {
            this(code, message, status, Instant.now(), Map.of());
        }

        public ErrorResponse(String code, String message, int status, Map<String, Object> details) {
            this(code, message, status, Instant.now(), details);
        }
    }
}
