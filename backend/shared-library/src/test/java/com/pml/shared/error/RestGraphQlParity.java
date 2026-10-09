package com.pml.shared.error;

import graphql.execution.DataFetcherExceptionHandlerParameters;
import org.springframework.http.ProblemDetail;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared assertion: the two transports answer with the same registry code.
 *
 * <h2>Why this is asserted over the real exception list</h2>
 * "One vocabulary, two transports" is easy to state and easy to break in one
 * place. Checking a handful of representative exceptions proves the ones
 * somebody thought of; the divergence that matters is the exception added later
 * to a service translator by a developer who only tested the surface they were
 * working on.
 *
 * <p>So the check runs over every exception class the service declares — the
 * same reflective scan {@link RefusalCoverage} uses — and compares the code, the
 * classification and {@code retryable} that each transport produces. A REST-only
 * or GraphQL-only mapping fails by name.</p>
 */
public final class RestGraphQlParity {

    private RestGraphQlParity() {
    }

    public static void assertTransportsAgree(RefusalTranslator serviceTranslator,
                                             String basePackage) {
        List<RefusalTranslator> chain =
                List.of(serviceTranslator, new PlatformRefusalTranslator());

        PlatformDataFetcherExceptionHandler graphql =
                new PlatformDataFetcherExceptionHandler(chain);
        PlatformProblemDetailAdvice rest = new PlatformProblemDetailAdvice(chain);

        List<Throwable> samples = RefusalCoverage.declaredExceptionInstances(basePackage);

        assertThat(samples)
                .as("no exception instances built from %s — the scan is looking in the "
                        + "wrong place, so this would agree about nothing", basePackage)
                .isNotEmpty();

        List<String> divergent = new ArrayList<>();

        for (Throwable sample : samples) {
            var graphqlExtensions = graphql
                    .handleException(DataFetcherExceptionHandlerParameters
                            .newExceptionParameters().exception(sample).build())
                    .join()
                    .getErrors()
                    .get(0)
                    .getExtensions();

            ProblemDetail problem = rest.handle(sample, null);
            var restProperties = problem.getProperties();

            for (String key : List.of(GraphQlErrors.ERROR_CODE,
                    GraphQlErrors.CLASSIFICATION,
                    GraphQlErrors.RETRYABLE)) {
                Object overGraphQl = graphqlExtensions.get(key);
                Object overRest = restProperties == null ? null : restProperties.get(key);
                if (overGraphQl == null || !overGraphQl.equals(overRest)) {
                    divergent.add("%s → %s: GraphQL %s, REST %s".formatted(
                            sample.getClass().getSimpleName(), key, overGraphQl, overRest));
                }
            }
        }

        assertThat(divergent)
                .as("""
                    the same exception answers differently depending on the transport. A \
                    client then needs two error maps, and one of them is always the stale \
                    one. Both surfaces resolve through the same RefusalTranslator chain, so \
                    a divergence here means a translator was registered for one and not the \
                    other.""")
                .isEmpty();
    }
}
