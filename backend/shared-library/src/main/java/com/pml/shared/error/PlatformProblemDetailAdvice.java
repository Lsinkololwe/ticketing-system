package com.pml.shared.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * RFC 9457 problem documents for the REST surface.
 *
 * <h2>One vocabulary, two transports</h2>
 * The REST surface is small but real — presigned-URL uploads, the internal OTP
 * API, webhook receipt, service-to-service lookups. A caller that meets
 * {@code TICKET_UNKNOWN} over GraphQL and {@code TICKET_NOT_FOUND} over REST is
 * dealing with two platforms, and the client ends up with two error maps of
 * which one is always the stale one.
 *
 * <p>Parity here is structural rather than promised: this advice resolves the
 * exception through <b>the same {@link RefusalTranslator} chain</b> the GraphQL
 * handler uses, so a code can only diverge if someone registers a translator
 * for one transport and not the other. {@code RestGraphQlParityTest} asserts it
 * anyway, over every exception the services declare.</p>
 *
 * <h2>The status code is derived, never chosen here</h2>
 * Mapping each exception to a status by hand is how the two surfaces drift: the
 * same refusal becomes 400 in one controller and 409 in another, and the code in
 * the body stops agreeing with the status in the header. The status comes from
 * the registry's {@link ErrorClassification}, so it is decided once per code.
 *
 * <h2>What a defect discloses: nothing</h2>
 * Same rule as GraphQL, and the reason is the same — {@code detail} is a
 * constant and the throwable is logged against the correlation id. A REST body
 * is if anything more tempting to fill in, because {@code ProblemDetail} has a
 * {@code detail} field that reads like an invitation.
 */
@Slf4j
@RestControllerAdvice
public class PlatformProblemDetailAdvice {

    /** Where the problem type URIs live. Stable, and per registry code. */
    private static final String TYPE_PREFIX = "https://errors.myticket.zm/";

    private final List<RefusalTranslator> translators;

    public PlatformProblemDetailAdvice(List<RefusalTranslator> translators) {
        this.translators = translators == null ? List.of() : List.copyOf(translators);
    }

    @ExceptionHandler(Throwable.class)
    public ProblemDetail handle(Throwable thrown, ServerWebExchange exchange) {
        Throwable exception = RefusalResolution.unwrap(thrown);
        String correlationId = UUID.randomUUID().toString();
        String path = exchange == null ? null : exchange.getRequest().getPath().value();

        return RefusalResolution.resolve(exception, translators)
                .map(refusal -> refusalProblem(refusal, correlationId, path))
                .orElseGet(() -> defectProblem(exception, correlationId, path));
    }

    private ProblemDetail refusalProblem(DomainRefusal refusal, String correlationId, String path) {
        log.info("[{}] refusal {} at {}", correlationId, refusal.errorCode(), path);

        ProblemDetail problem = ProblemDetail.forStatus(statusOf(refusal.classification()));
        problem.setType(URI.create(TYPE_PREFIX + refusal.errorCode().name()));
        problem.setTitle(refusal.errorCode().name());
        // The code, not refusal.getMessage() — the developer message is built
        // from data at the throw site and stays in the log line above.
        problem.setDetail(GraphQlErrors.refusalMessage(refusal.errorCode()));

        problem.setProperty(GraphQlErrors.ERROR_CODE, refusal.errorCode().name());
        problem.setProperty(GraphQlErrors.CLASSIFICATION, refusal.classification().name());
        problem.setProperty(GraphQlErrors.RETRYABLE, refusal.retryable());
        problem.setProperty(GraphQlErrors.CORRELATION_ID, correlationId);
        refusal.details().forEach(problem::setProperty);
        return problem;
    }

    private ProblemDetail defectProblem(Throwable exception, String correlationId, String path) {
        log.error("[{}] defect at {}", correlationId, path, exception);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create(TYPE_PREFIX + ErrorCode.INTERNAL_ERROR.name()));
        problem.setTitle(ErrorCode.INTERNAL_ERROR.name());
        problem.setDetail(GraphQlErrors.defectMessage());

        GraphQlErrors.forDefect(exception, correlationId).forEach(problem::setProperty);
        return problem;
    }

    /**
     * The HTTP status for a classification.
     *
     * <p>{@code UNAVAILABLE} is 503 rather than 500 because the two mean
     * different things to everything between the caller and here: a load
     * balancer retries a 503 and gives up on a 500, and that matches
     * {@code retryable} on those rows.</p>
     */
    private HttpStatus statusOf(ErrorClassification classification) {
        return switch (classification) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FAILED_PRECONDITION -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            // UNKNOWN means a registry row was added without choosing a family.
            // 500 is the honest answer: the platform cannot say what went wrong
            // because it has not decided. Listed rather than left to a `default`
            // so that adding a family to ErrorClassification fails this build
            // instead of silently becoming a 500.
            case INTERNAL, UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }


}
