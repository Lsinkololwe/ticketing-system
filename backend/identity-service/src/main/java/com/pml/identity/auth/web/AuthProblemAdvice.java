package com.pml.identity.auth.web;

import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.GraphQlErrors;
import com.pml.shared.error.RefusalResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * RFC 9457 {@code application/problem+json} for the internal auth API, with the extensions
 * {@code errorCode}, {@code retryable} and where relevant {@code retryAfterSeconds},
 * {@code attemptsRemaining}, {@code lockedUntil} (CONTRACT 4).
 *
 * <p>HTTP statuses are chosen per code here, as the contract fixes them (423 for a locked contact,
 * 429 for throttling, 410 for an expired code) and the generic classification mapping cannot say.
 * Scoped to this API and ahead of the platform advice. {@code detail} is the code, never an
 * exception message, so no contact value or code can reach a response.</p>
 */
@RestControllerAdvice(basePackages = "com.pml.identity.auth.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthProblemAdvice {

    private static final Logger log = LoggerFactory.getLogger(AuthProblemAdvice.class);
    private static final String TYPE_PREFIX = "https://errors.myticket.zm/";

    static final Map<ErrorCode, HttpStatus> STATUS = new EnumMap<>(ErrorCode.class);

    static {
        STATUS.put(ErrorCode.CONTACT_INVALID, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.PHONE_NUMBER_INVALID, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.COMMAND_NOT_WELL_FORMED, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.OTP_INVALID, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.PROOF_INVALID, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.LOGIN_HANDLE_INVALID, HttpStatus.BAD_REQUEST);
        STATUS.put(ErrorCode.OTP_EXPIRED, HttpStatus.GONE);
        STATUS.put(ErrorCode.OTP_LOCKED, HttpStatus.LOCKED);
        STATUS.put(ErrorCode.OTP_ATTEMPTS_EXHAUSTED, HttpStatus.LOCKED);
        STATUS.put(ErrorCode.OTP_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS);
        STATUS.put(ErrorCode.OTP_COOLDOWN_ACTIVE, HttpStatus.TOO_MANY_REQUESTS);
        STATUS.put(ErrorCode.OTP_DELIVERY_FAILED, HttpStatus.SERVICE_UNAVAILABLE);
        STATUS.put(ErrorCode.NOTIFICATION_CHANNEL_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        STATUS.put(ErrorCode.ACCOUNT_SUSPENDED, HttpStatus.FORBIDDEN);
        STATUS.put(ErrorCode.ACCOUNT_MERGING, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.CONTACT_ALREADY_CLAIMED, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.ACCOUNT_NOT_ACTIVE, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.USER_UNKNOWN, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetail> handle(Throwable thrown, ServerWebExchange exchange) {
        Throwable exception = RefusalResolution.unwrap(thrown);
        String correlationId = UUID.randomUUID().toString();
        String path = exchange.getRequest().getPath().value();

        if (exception instanceof DomainRefusal refusal) {
            log.info("[{}] refusal {} at {}", correlationId, refusal.errorCode(), path);
            return problem(refusal.errorCode(), statusOf(refusal), refusal.details(), correlationId);
        }
        if (isMalformedRequest(thrown)) {
            log.info("[{}] malformed request at {}", correlationId, path);
            return problem(ErrorCode.COMMAND_NOT_WELL_FORMED, HttpStatus.BAD_REQUEST, Map.of(), correlationId);
        }
        // The throwable is logged against the correlation id; none of it reaches the caller.
        log.error("[{}] defect at {}", correlationId, path, exception);
        return problem(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, Map.of(), correlationId);
    }

    /** A body that cannot be read, or a 4xx raised by the web layer itself; looks through wrapping causes. */
    private static boolean isMalformedRequest(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ServerWebInputException
                    || t instanceof org.springframework.core.codec.DecodingException
                    || (t instanceof ResponseStatusException rse && rse.getStatusCode().is4xxClientError())) {
                return true;
            }
        }
        return false;
    }

    private static HttpStatus statusOf(DomainRefusal refusal) {
        HttpStatus status = STATUS.get(refusal.errorCode());
        if (status != null) {
            return status;
        }
        return switch (refusal.classification()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FAILED_PRECONDITION -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL, UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode code, HttpStatus status,
                                                          Map<String, Object> details, String correlationId) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + code.name()));
        problem.setTitle(code.name());
        problem.setDetail(code == ErrorCode.INTERNAL_ERROR ? GraphQlErrors.defectMessage() : GraphQlErrors.refusalMessage(code));
        problem.setProperty(GraphQlErrors.ERROR_CODE, code.name());
        problem.setProperty(GraphQlErrors.RETRYABLE, code.retryable());
        problem.setProperty(GraphQlErrors.CORRELATION_ID, correlationId);
        details.forEach((key, value) -> {
            if (problem.getProperties() == null || !problem.getProperties().containsKey(key)) {
                problem.setProperty(key, value);
            }
        });

        ResponseEntity.BodyBuilder response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        Object retryAfter = details.get("retryAfterSeconds");
        if (retryAfter instanceof Number seconds) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds.longValue()));
        }
        return response.body(problem);
    }
}
