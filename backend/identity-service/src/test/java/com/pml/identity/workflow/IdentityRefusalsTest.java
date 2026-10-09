package com.pml.identity.workflow;

import com.pml.shared.workflow.Refusals;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-PLT-015")
@DisplayName("ET-PLT-015 · identity's refusals keep their ErrorCode through a workflow and back")
class IdentityRefusalsTest {

    @Test
    @DisplayName("a refusal is non-retryable, typed by its code, and leads its message with the code")
    void refusalShape() {
        ApplicationFailure failure = Refusals.refusal(ErrorCode.ORGANIZATION_STATE_INVALID, "not reviewable");
        assertThat(failure.isNonRetryable()).isTrue();
        assertThat(failure.getType()).isEqualTo("ORGANIZATION_STATE_INVALID");
        assertThat(failure.getOriginalMessage()).isEqualTo("ORGANIZATION_STATE_INVALID: not reviewable");
    }

    @Test
    @DisplayName("a validator's refusal, whose type the SDK replaces, still yields its code from the message")
    void codeSurvivesAWrapper() {
        ApplicationFailure wrapped = ApplicationFailure.newNonRetryableFailure(
                "TRANSFER_NOT_PENDING: the transfer is CANCELLED", "io.temporal.internal.worker.WorkflowExecutionException");
        assertThat(Refusals.typeOf(wrapped, null)).isEqualTo("TRANSFER_NOT_PENDING");
        assertThat(Refusals.fromTemporal(wrapped, ErrorCode.ORGANIZATION_STATE_INVALID))
                .isInstanceOf(TranslatedRefusal.class)
                .satisfies(error -> assertThat(((DomainRefusal) error).errorCode()).isEqualTo(ErrorCode.TRANSFER_NOT_PENDING))
                .hasMessage("the transfer is CANCELLED");
    }

    @Test
    @DisplayName("an activity's business-rule failure is not retried; a platform refusal keeps its retryability")
    void forActivity() {
        RuntimeException rule = Refusals.forActivity(new IllegalStateException("Only the owner can initiate a transfer"));
        assertThat(rule).isInstanceOf(ApplicationFailure.class);
        assertThat(((ApplicationFailure) rule).isNonRetryable()).isTrue();

        RuntimeException refused = Refusals.forActivity(new TranslatedRefusal(ErrorCode.DOCUMENT_REQUIRED, "missing"));
        assertThat(((ApplicationFailure) refused).isNonRetryable()).isTrue();
        assertThat(((ApplicationFailure) refused).getType()).isEqualTo("DOCUMENT_REQUIRED");

        RuntimeException transient_ = Refusals.forActivity(new RuntimeException("connection reset"));
        assertThat(transient_).isNotInstanceOf(ApplicationFailure.class);
    }

    @Test
    @DisplayName("an IllegalArgumentException crosses back as itself, so GraphQL maps it as before")
    void illegalArgumentRoundTrips() {
        ApplicationFailure failure = (ApplicationFailure) Refusals.forActivity(new IllegalArgumentException("Invalid transfer token"));
        assertThat(Refusals.fromTemporal(failure, ErrorCode.TRANSFER_NOT_PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid transfer token");
    }
}
