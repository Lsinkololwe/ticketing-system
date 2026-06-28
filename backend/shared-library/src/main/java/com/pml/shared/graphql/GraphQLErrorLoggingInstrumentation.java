package com.pml.shared.graphql;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Logs every error present on a completed GraphQL {@link ExecutionResult}.
 *
 * <p><b>Why this exists:</b> a {@code DataFetcherExceptionHandler} only sees
 * exceptions thrown <i>while fetching</i>. Errors raised in graphql-java's later
 * value-completion phase — scalar coercing/{@code SerializationError},
 * validation errors, etc. — are added straight to the result's {@code errors}
 * list and never reach the handler. Those would otherwise vanish from the server
 * logs (the client gets a generic {@code INTERNAL} error with no trace). This
 * instrumentation closes that gap by logging all result errors, including the
 * underlying throwable + stack trace when one is available.</p>
 *
 * <p>Registered automatically in every service via the {@code com.pml.shared}
 * component scan; DGS / Spring for GraphQL picks up {@code Instrumentation} beans.</p>
 */
@Slf4j
@Component
public class GraphQLErrorLoggingInstrumentation extends SimplePerformantInstrumentation {

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(
            ExecutionResult executionResult,
            InstrumentationExecutionParameters parameters,
            InstrumentationState state) {

        List<GraphQLError> errors = executionResult.getErrors();
        if (errors != null && !errors.isEmpty()) {
            String operation = parameters != null ? parameters.getOperation() : null;
            for (GraphQLError error : errors) {
                // Data-fetcher exceptions are already logged with a stack trace by
                // the per-service DataFetcherExceptionHandler. This catches the rest
                // (serialization/coercing, validation, etc.) that bypass it — those
                // carry no throwable, so we log path/type/message for traceability.
                if (error instanceof Throwable throwable) {
                    log.error("GraphQL error [op={}] path={} type={}: {}",
                            operation, error.getPath(), error.getErrorType(), error.getMessage(), throwable);
                } else {
                    log.error("GraphQL error [op={}] path={} type={}: {}",
                            operation, error.getPath(), error.getErrorType(), error.getMessage());
                }
            }
        }
        return CompletableFuture.completedFuture(executionResult);
    }
}
