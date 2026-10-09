package com.pml.shared.error;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The single exit every GraphQL failure takes.
 *
 * <h2>Why one handler rather than one per exception type</h2>
 * A handler per type covers the types somebody remembered. The failure this
 * exists to prevent is the one nobody anticipated — an NPE from a resolver, a
 * driver timeout, a serialisation error — and those are exactly the exceptions
 * that carry the most revealing messages. So the default path is the safe one
 * and refusals are the special case, rather than the other way round.
 *
 * <h2>Two outcomes, and only two</h2>
 * <ul>
 *   <li>A {@link DomainRefusal} — the platform declining on purpose. Its
 *       registry code, classification, {@code retryable} and chosen details go
 *       to the client, because every one of them was picked deliberately.</li>
 *   <li>Anything else — a defect. The client gets {@code INTERNAL_ERROR}, a
 *       constant message and a correlation id. The exception is logged in full
 *       against that id.</li>
 * </ul>
 *
 * <h2>The correlation id is what makes the silence acceptable</h2>
 * Withholding detail only works if someone entitled to it can still get it.
 * Every defect is logged at ERROR with its correlation id and the whole
 * throwable; the user sees the id and quotes it. Without that this would not be
 * "log it instead of returning it", it would be "lose it".
 */
@Slf4j
public class PlatformDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    private final List<RefusalTranslator> translators;

    /** No translators: every non-refusal is a defect. */
    public PlatformDataFetcherExceptionHandler() {
        this(List.of());
    }

    public PlatformDataFetcherExceptionHandler(List<RefusalTranslator> translators) {
        this.translators = translators == null ? List.of() : List.copyOf(translators);
    }

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters parameters) {

        Throwable exception = RefusalResolution.unwrap(parameters.getException());
        String correlationId = UUID.randomUUID().toString();

        GraphQLError error = RefusalResolution.resolve(exception, translators)
                .map(refusal -> refusalError(refusal, parameters, correlationId))
                .orElseGet(() -> defectError(exception, parameters, correlationId));

        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult().error(error).build());
    }


    private GraphQLError refusalError(DomainRefusal refusal,
                                      DataFetcherExceptionHandlerParameters parameters,
                                      String correlationId) {
        // Logged at INFO, not ERROR: a sold-out tier is the platform working.
        // Logging refusals as errors is how a dashboard fills with noise until
        // nobody looks at it, and the real defect arrives unnoticed.
        log.info("[{}] refusal {} at {}", correlationId, refusal.errorCode(),
                describePath(parameters));

        GraphQLError.Builder<?> error = GraphQLError.newError()
                // The code, not refusal.getMessage(). The developer message is
                // assembled from data at the throw site and stays in the log
                // line above; see DomainRefusal's note on the boundary.
                .message(GraphQlErrors.refusalMessage(refusal.errorCode()))
                .errorType(toErrorType(refusal.classification()))
                .extensions(GraphQlErrors.forRefusal(refusal, correlationId));

        return locate(error, parameters).build();
    }

    private GraphQLError defectError(Throwable exception,
                                     DataFetcherExceptionHandlerParameters parameters,
                                     String correlationId) {
        // The whole throwable, on this side of the boundary only. This line is
        // the reason the client can be told nothing.
        log.error("[{}] defect at {}", correlationId, describePath(parameters), exception);

        GraphQLError.Builder<?> error = GraphQLError.newError()
                // Constant. A message derived from the cause is the same leak in
                // different clothing, and it survives every attempt to strip the
                // cause itself.
                .message(GraphQlErrors.defectMessage())
                .errorType(ErrorType.DataFetchingException)
                .extensions(GraphQlErrors.forDefect(exception, correlationId));

        return locate(error, parameters).build();
    }

    /**
     * The field path, for the log, or a placeholder.
     *
     * <p>Separate from {@link #locate} because logging happens first and must be
     * just as safe: {@code getPath()} dereferences the environment, so calling it
     * from a log statement throws exactly where the guard was supposed to be.</p>
     */
    private String describePath(DataFetcherExceptionHandlerParameters parameters) {
        try {
            return parameters.getDataFetchingEnvironment() == null
                    ? "<no path>"
                    : String.valueOf(parameters.getPath());
        } catch (RuntimeException unavailable) {
            return "<no path>";
        }
    }

    /**
     * Attaches path and source location when they are available.
     *
     * <p>Both are derived from the {@code DataFetchingEnvironment}, and
     * graphql-java dereferences it without a null check. An exception handler
     * that throws is the worst failure mode available — the original exception
     * is lost and whatever the framework does next is outside this contract
     * entirely — so the location is treated as a nicety and never as a
     * requirement.</p>
     */
    private GraphQLError.Builder<?> locate(GraphQLError.Builder<?> error,
                                           DataFetcherExceptionHandlerParameters parameters) {
        try {
            if (parameters.getDataFetchingEnvironment() != null) {
                error.path(parameters.getPath()).location(parameters.getSourceLocation());
            }
        } catch (RuntimeException locationUnavailable) {
            // Deliberately swallowed. Losing the path costs a little context in
            // the client's error array; letting this propagate costs the entire
            // error contract.
            log.debug("no source location available for this failure", locationUnavailable);
        }
        return error;
    }


    /** graphql-java's coarse type, for clients that read it instead of extensions. */
    private ErrorType toErrorType(ErrorClassification classification) {
        return switch (classification) {
            case BAD_REQUEST, FAILED_PRECONDITION -> ErrorType.ValidationError;
            case UNAUTHENTICATED, PERMISSION_DENIED -> ErrorType.ExecutionAborted;
            default -> ErrorType.DataFetchingException;
        };
    }
}
