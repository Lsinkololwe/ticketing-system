package com.pml.catalog.workflow;

import com.pml.catalog.error.CatalogRefusalTranslator;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.workflow.Refusal;
import com.pml.catalog.exception.EventNotFoundException;
import com.pml.catalog.exception.InvalidEventStateException;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-CAT-001")
@DisplayName("ET-PLT-005 in catalog · a lifecycle refusal keeps its code through Temporal and back")
class CatalogRefusalsTest {

    /** What catalog's activities pass, so their own exceptions reach the caller as codes. */
    private static final CatalogRefusalTranslator CATALOG = new CatalogRefusalTranslator();

    @Test
    @DisplayName("an illegal transition becomes a non-retryable EVENT_STATE_INVALID, never a retried activity")
    void anIllegalTransitionIsNotRetried() {
        RuntimeException raised = Refusals.forActivity(new InvalidEventStateException("event-1", "DRAFT", "APPROVED"), CATALOG);

        assertThat(raised).isInstanceOfSatisfying(ApplicationFailure.class, failure -> {
            assertThat(failure.getType()).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name());
            assertThat(failure.isNonRetryable()).isTrue();
        });
    }

    @Test
    @DisplayName("a missing event becomes EVENT_UNKNOWN")
    void aMissingEventIsUnknown() {
        RuntimeException raised = Refusals.forActivity(new EventNotFoundException("event-1"), CATALOG);

        assertThat(((ApplicationFailure) raised).getType()).isEqualTo(ErrorCode.EVENT_UNKNOWN.name());
    }

    @Test
    @DisplayName("a domain refusal keeps its own code")
    void aDomainRefusalKeepsItsCode() {
        RuntimeException raised = Refusals.forActivity(new TranslatedRefusal(ErrorCode.EVENT_STATE_INVALID, "tickets are sold"));

        assertThat(((ApplicationFailure) raised).getType()).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name());
    }

    @Test
    @DisplayName("an unrecognised fault stays retryable, because a database blip is not a refusal")
    void anUnknownFaultIsRetried() {
        RuntimeException fault = new RuntimeException("connection reset");

        assertThat(Refusals.forActivity(fault)).isSameAs(fault);
    }

    @Test
    @DisplayName("a validator refusal whose type the SDK replaced is recovered from its message")
    void theCodeSurvivesTheWrapper() {
        ApplicationFailure wrapped = ApplicationFailure.newNonRetryableFailure(
                "EVENT_STATE_INVALID: only a published event is rescheduled",
                "io.temporal.internal.worker.WorkflowExecutionException");

        Throwable translated = Refusals.fromTemporal(wrapped, ErrorCode.RESOURCE_CONFLICT);

        assertThat(translated).isInstanceOfSatisfying(DomainRefusal.class,
                refusal -> assertThat(refusal.errorCode()).isEqualTo(ErrorCode.EVENT_STATE_INVALID));
        assertThat(Refusals.typeOf(wrapped, null)).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name());
        assertThat(Refusals.messageOf(wrapped)).isEqualTo("only a published event is rescheduled");
    }

    @Test
    @DisplayName("a rule's refusal raises exactly its code")
    void aRuleRefusalRaisesItsCode() {
        ApplicationFailure failure = new Refusal(ErrorCode.ACTOR_NOT_PERMITTED, "claimed by reviewer-alice").failure();

        assertThat(failure.getType()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name());
        assertThat(failure.isNonRetryable()).isTrue();
    }
}
