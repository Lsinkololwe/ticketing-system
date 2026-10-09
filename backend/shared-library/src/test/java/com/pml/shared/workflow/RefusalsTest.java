package com.pml.shared.workflow;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-PLT-015")
@DisplayName("A refusal keeps its ErrorCode through a workflow and back")
class RefusalsTest {

    static final class EventLocked extends RuntimeException {
        EventLocked() {
            super("event e-1 is PUBLISHED");
        }
    }

    @Test
    @DisplayName("a service exception its translator knows becomes a coded, non-retryable failure")
    void translatorMapsTheServicesOwnException() {
        RuntimeException raised = Refusals.forActivity(new EventLocked(), error -> error instanceof EventLocked
                ? Optional.of(new TranslatedRefusal(ErrorCode.EVENT_STATE_INVALID, "locked"))
                : Optional.empty());

        assertThat(raised).isInstanceOf(ApplicationFailure.class);
        ApplicationFailure failure = (ApplicationFailure) raised;
        assertThat(failure.isNonRetryable()).isTrue();
        assertThat(failure.getType()).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name());
        assertThat(failure.getOriginalMessage())
                .as("the history keeps the exception's own words; the code leads them")
                .isEqualTo("EVENT_STATE_INVALID: event e-1 is PUBLISHED");
    }

    @Test
    @DisplayName("without a translator the same exception stays an ordinary, retryable fault")
    void untranslatedIsNotARefusal() {
        assertThat(Refusals.forActivity(new EventLocked())).isInstanceOf(EventLocked.class);
    }

    @Test
    @DisplayName("the client side reads the code back even when only the message survived")
    void codeSurvivesAWrapper() {
        ApplicationFailure wrapped = ApplicationFailure.newNonRetryableFailure(
                "EVENT_STATE_INVALID: locked", "io.temporal.SomeWrapper");
        Throwable back = Refusals.fromTemporal(wrapped, ErrorCode.EVENT_STATE_INVALID);
        assertThat(back).isInstanceOf(TranslatedRefusal.class);
        assertThat(((TranslatedRefusal) back).errorCode()).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(back.getMessage()).isEqualTo("locked");
    }
}
