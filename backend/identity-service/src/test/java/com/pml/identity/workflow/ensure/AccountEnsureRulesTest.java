package com.pml.identity.workflow.ensure;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · ensure activities wait for Keycloak and Mongo instead of giving up")
class AccountEnsureRulesTest {

    @Test
    @DisplayName("retries are unlimited, backed off, capped at a minute, on the account queue")
    void retryPolicy() {
        ActivityOptions options = AccountEnsureRules.options();

        assertThat(options.getTaskQueue()).isEqualTo(TaskQueues.ACCOUNT);
        assertThat(options.getStartToCloseTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.getRetryOptions().getMaximumAttempts())
                .as("0 means unlimited: an outage is waited out, an account is never abandoned")
                .isZero();
        assertThat(options.getRetryOptions().getInitialInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(options.getRetryOptions().getBackoffCoefficient()).isEqualTo(2.0);
        assertThat(options.getRetryOptions().getMaximumInterval()).isEqualTo(Duration.ofMinutes(1));
    }
}
