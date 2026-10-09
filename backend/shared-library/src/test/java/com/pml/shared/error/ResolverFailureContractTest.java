package com.pml.shared.error;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error contract holds through a <b>real GraphQL execution</b>, not just when the handler is
 * called directly.
 *
 * <h2>The gap this guards</h2>
 * Every other error-contract test in this corpus hand-builds a
 * {@code DataFetcherExceptionHandlerParameters} and invokes
 * {@link PlatformDataFetcherExceptionHandler} itself. That proves the handler's logic and nothing
 * about whether an exception thrown from a resolver ever reaches it — which is the only thing a
 * client experiences.
 *
 * <p>The distinction is not academic here. graphql-java installs its own
 * {@code SimpleDataFetcherExceptionHandler} unless one is supplied, and that default puts the
 * exception's <b>message</b> into the error it returns. So the failure mode of a mis-wiring is not
 * a stack trace in the logs or a bean that will not construct — it is the platform quietly
 * answering with exactly the internals leak the contract exists to prevent, while every unit test
 * stays green.</p>
 *
 * <p>So the probe is: throw a
 * {@link NullPointerException} <em>from a resolver</em> and assert the response carries none of the
 * class name, the message, or a stack frame.</p>
 *
 * <h2>graphql-java rather than a Spring context</h2>
 * This asserts the execution path. {@code ErrorContractWiringTest} asserts that Spring hands DGS
 * this handler rather than another. Two different claims, and a single test that needed a full
 * application context to make either would be skipped in CI and stop meaning anything.
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005 R2/R4 · the contract holds through a real execution")
class ResolverFailureContractTest {

    private static final String SDL = """
            type Query {
              refuses: String
              defects: String
              works: String
            }
            """;

    /** A refusal, the way a service raises one. */
    private static final class TierSoldOut extends DomainRefusal {
        private TierSoldOut() {
            super(ErrorCode.TIER_SOLD_OUT, "tier 4f2a has 0 of 50 remaining");
        }
    }

    /**
     * The message below is the one that must never reach a client.
     *
     * <p>Written to look like the kind of message that actually leaks — a connection string with a
     * host, a database name and a credential — rather than a placeholder, so the assertions below
     * are testing against something whose disclosure would matter.</p>
     */
    private static final String SECRET_DETAIL =
            "mongodb://appuser:s3cr3t@mongo-prod-01.internal:27017/ticketing failed";

    private static GraphQL platform() {
        TypeDefinitionRegistry registry = new SchemaParser().parse(SDL);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("refuses", environment -> {
                            throw new TierSoldOut();
                        })
                        .dataFetcher("defects", environment -> {
                            throw new NullPointerException(SECRET_DETAIL);
                        })
                        .dataFetcher("works", environment -> "ok"))
                .build();

        return GraphQL
                .newGraphQL(new SchemaGenerator().makeExecutableSchema(registry, wiring))
                .defaultDataFetcherExceptionHandler(new PlatformDataFetcherExceptionHandler(
                        List.of(new PlatformRefusalTranslator())))
                .build();
    }

    private static GraphQLError executeAndTakeError(String query) {
        ExecutionResult result = platform().execute(ExecutionInput.newExecutionInput(query).build());
        assertThat(result.getErrors())
                .as("the probe must actually fail, or the assertions below test nothing")
                .hasSize(1);
        return result.getErrors().get(0);
    }

    @Test
    @DisplayName("a refusal thrown from a resolver arrives with its registry code")
    void aRefusalKeepsItsCode() {
        GraphQLError error = executeAndTakeError("{ refuses }");
        Map<String, Object> extensions = error.getExtensions();

        assertThat(extensions).containsEntry(GraphQlErrors.ERROR_CODE, "TIER_SOLD_OUT");
        assertThat(extensions)
                .as("R3 — a sold-out tier will not become available by asking again")
                .containsEntry(GraphQlErrors.RETRYABLE, false);
        assertThat(extensions).containsKey(GraphQlErrors.CORRELATION_ID);
    }

    @Test
    @DisplayName("the refusal's developer message does not travel with it")
    void theDeveloperMessageStaysOnTheServer() {
        GraphQLError error = executeAndTakeError("{ refuses }");

        // The throw site assembled "tier 4f2a has 0 of 50 remaining" from live data. Useful in a
        // log; it discloses inventory to a caller who asked only whether they could buy.
        assertThat(error.getMessage()).doesNotContain("4f2a", "0 of 50");
    }

    @Test
    @DisplayName("a NullPointerException from a resolver leaks nothing — R4's own probe")
    void aDefectLeaksNothing() {
        GraphQLError error = executeAndTakeError("{ defects }");
        String whole = error.getMessage() + " " + error.getExtensions();

        assertThat(whole)
                .as("the exception message is the leak R4 names first")
                .doesNotContain(SECRET_DETAIL)
                .doesNotContain("s3cr3t", "mongo-prod-01.internal");
        assertThat(whole)
                .as("nor the class name")
                .doesNotContain("NullPointerException");
        assertThat(whole)
                .as("nor a stack frame")
                .doesNotContain(".java:", "com.pml.shared.error.ResolverFailureContractTest");

        assertThat(error.getExtensions())
                .containsEntry(GraphQlErrors.ERROR_CODE, "INTERNAL_ERROR")
                .containsEntry(GraphQlErrors.RETRYABLE, true)
                .containsKey(GraphQlErrors.CORRELATION_ID);
    }

    @Test
    @DisplayName("a defect and a refusal are told apart, so this is not passing by refusing both")
    void theTwoPathsAreDistinct() {
        // Without this, an implementation that classified everything as INTERNAL_ERROR would pass
        // aDefectLeaksNothing perfectly — and one that leaked everything would pass
        // aRefusalKeepsItsCode. Each test above is only meaningful given the other outcome exists.
        assertThat(executeAndTakeError("{ refuses }").getExtensions())
                .containsEntry(GraphQlErrors.ERROR_CODE, "TIER_SOLD_OUT");
        assertThat(executeAndTakeError("{ defects }").getExtensions())
                .containsEntry(GraphQlErrors.ERROR_CODE, "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("a successful field is untouched by any of this")
    void theHappyPathStillWorks() {
        ExecutionResult result = platform()
                .execute(ExecutionInput.newExecutionInput("{ works }").build());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.<Map<String, Object>>getData()).containsEntry("works", "ok");
    }
}
