package com.pml.identity.workflow.contactchange;

import com.pml.identity.infrastructure.temporal.WorkflowIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004-R5 · the contact-change process constants: id, expiry, attempt budget")
class ContactChangeRulesTest {

    @Test
    @DisplayName("one open change per account: the workflow id is contact-change/{accountId}")
    void workflowId() {
        assertThat(WorkflowIds.contactChange("0b8f1c2e-1111-4222-8333-444455556666"))
                .isEqualTo("contact-change/0b8f1c2e-1111-4222-8333-444455556666");
        assertThatThrownBy(() -> WorkflowIds.contactChange(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a change waits 48 hours for its codes and tolerates five wrong ones")
    void budgets() {
        assertThat(ContactChangeRules.EXPIRY).isEqualTo(Duration.ofHours(48));
        assertThat(ContactChangeRules.attemptsRemaining(0)).isEqualTo(5);
        assertThat(ContactChangeRules.attemptsRemaining(3)).isEqualTo(2);
        assertThat(ContactChangeRules.attemptsRemaining(5)).isZero();
        assertThat(ContactChangeRules.attemptsRemaining(9)).isZero();
    }

    @Test
    @DisplayName("the step sequence is versioned from the first release")
    void versioned() {
        assertThat(ContactChangeRules.STEPS).isEqualTo("contact-change-steps");
    }
}
