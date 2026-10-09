package com.pml.shared.error;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one exit every GraphQL failure takes.
 *
 * <h2>The case worth the most</h2>
 * A refusal raised inside a reactive chain arrives wrapped in a
 * {@code CompletionException}. A handler that checks {@code instanceof
 * DomainRefusal} on the outer throwable classifies every one of them as a
 * defect — which is safe, and useless: the client is told `INTERNAL_ERROR` and
 * "the request could not be completed" when the truth was "this tier is sold
 * out". Every refusal on the platform travels through a {@code Mono}, so
 * getting this wrong would silently disable the entire error contract while
 * every leak test still passed.
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R2 · refusals stay legible, defects say nothing")
class PlatformDataFetcherExceptionHandlerTest {

    private final PlatformDataFetcherExceptionHandler handler =
            new PlatformDataFetcherExceptionHandler();

    /** A concrete refusal — the platform declining on purpose. */
    private static final class TierSoldOut extends DomainRefusal {
        TierSoldOut() {
            super(ErrorCode.TIER_SOLD_OUT, "tier 77 has no remaining capacity",
                    Map.of("tierId", "tier-77"));
        }
    }

    private GraphQLError handle(Throwable thrown) {
        DataFetcherExceptionHandlerParameters parameters =
                DataFetcherExceptionHandlerParameters.newExceptionParameters()
                        .exception(thrown)
                        .build();

        DataFetcherExceptionHandlerResult result =
                handler.handleException(parameters).join();

        assertThat(result.getErrors()).hasSize(1);
        return result.getErrors().get(0);
    }

    @Test
    @DisplayName("a refusal reaches the client with its code, classification and retryability")
    void refusalKeepsItsContract() {
        GraphQLError error = handle(new TierSoldOut());

        assertThat(error.getExtensions())
                .containsEntry(GraphQlErrors.ERROR_CODE, ErrorCode.TIER_SOLD_OUT.name())
                .containsEntry(GraphQlErrors.CLASSIFICATION,
                        ErrorCode.TIER_SOLD_OUT.classification().name())
                .containsEntry(GraphQlErrors.RETRYABLE, ErrorCode.TIER_SOLD_OUT.retryable())
                .containsEntry("tierId", "tier-77");
        assertThat(error.getExtensions()).containsKey(GraphQlErrors.CORRELATION_ID);
    }

    @Test
    @DisplayName("a refusal wrapped by a reactive chain is still a refusal")
    void refusalSurvivesCompletionExceptionWrapping() {
        // The case that would silently disable the whole contract: every refusal
        // on this platform is raised inside a Mono, so it arrives wrapped.
        GraphQLError error = handle(
                new CompletionException(new RuntimeException(new TierSoldOut())));

        assertThat(error.getExtensions())
                .as("""
                    unwrapped to the refusal, or every sold-out tier on the platform \
                    reports INTERNAL_ERROR and the client can say nothing useful""")
                .containsEntry(GraphQlErrors.ERROR_CODE, ErrorCode.TIER_SOLD_OUT.name());
    }

    @Test
    @DisplayName("a defect reveals no message, class name, package or frame")
    void defectRevealsNothing() {
        RuntimeException defect = new NullPointerException(
                "Cannot invoke \"com.pml.booking.domain.model.Ticket.getOwnerId()\" "
                        + "because \"ticket\" is null");

        GraphQLError error = handle(defect);
        String rendered = error.getMessage() + error.getExtensions();

        assertThat(rendered)
                .doesNotContain(defect.getMessage())
                .doesNotContain("NullPointerException")
                .doesNotContain("com.pml");
        assertThat(error.getExtensions())
                .containsEntry(GraphQlErrors.ERROR_CODE, ErrorCode.INTERNAL_ERROR.name());
    }

    @Test
    @DisplayName("two different defects are indistinguishable apart from the correlation id")
    void defectsCannotBeToldApart() {
        Map<String, Object> first = handle(new IllegalStateException("mongo at 10.0.4.12")).getExtensions();
        Map<String, Object> second = handle(new SecurityException("user 8f21c not admin")).getExtensions();

        assertThat(first.get(GraphQlErrors.ERROR_CODE)).isEqualTo(second.get(GraphQlErrors.ERROR_CODE));
        assertThat(first.get(GraphQlErrors.CLASSIFICATION)).isEqualTo(second.get(GraphQlErrors.CLASSIFICATION));
        assertThat(first.get(GraphQlErrors.RETRYABLE)).isEqualTo(second.get(GraphQlErrors.RETRYABLE));

        // The ids differ, and must: they are what makes each one findable in the
        // log without telling the caller anything about it.
        assertThat(first.get(GraphQlErrors.CORRELATION_ID))
                .isNotEqualTo(second.get(GraphQlErrors.CORRELATION_ID));
    }

    @Test
    @DisplayName("the defect message is the constant, not the cause")
    void defectMessageIsConstant() {
        assertThat(handle(new RuntimeException("anything at all")).getMessage())
                .isEqualTo(GraphQlErrors.defectMessage());
    }
}
