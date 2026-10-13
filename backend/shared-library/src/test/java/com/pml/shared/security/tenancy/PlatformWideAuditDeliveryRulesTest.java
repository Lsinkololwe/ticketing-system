package com.pml.shared.security.tenancy;

import io.temporal.activity.ActivityOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How hard delivery tries, and what may become part of a workflow id. OWASP A04:2021 · A09:2021. */
@Tag("L1")
@Tag("ET-PLT-007")
@Tag("ET-PLT-015")
@Tag("OWASP")
@DisplayName("audit delivery retries without a limit and with a capped backoff, and is addressed only by a record id")
class PlatformWideAuditDeliveryRulesTest {

    @Test
    @DisplayName("the retry has no attempt limit, starts at five seconds and never waits more than fifteen minutes")
    void unboundedWithCappedBackoff() {
        var retry = PlatformWideAuditDeliveryRules.activityOptions().getRetryOptions();

        assertThat(retry.getMaximumAttempts()).as("zero is no limit").isZero();
        assertThat(retry.getInitialInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(retry.getBackoffCoefficient()).isEqualTo(2.0);
        assertThat(retry.getMaximumInterval()).isEqualTo(Duration.ofMinutes(15));
        ActivityOptions options = PlatformWideAuditDeliveryRules.activityOptions();
        assertThat(options.getStartToCloseTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("the workflow id is audit-delivery/ and a UUID; an actor, a reason or any text is refused")
    void idIsOpaque() {
        String id = UUID.randomUUID().toString();
        assertThat(PlatformWideAuditDeliveryRules.workflowId(id)).isEqualTo("audit-delivery/" + id);

        for (String notAnId : new String[]{null, "", "finance-1", "buyer@example.com", "+260971234567",
                "BANK_ACCOUNT_MANAGE", id.toUpperCase(), id + "/x", "audit-delivery/" + id}) {
            assertThatThrownBy(() -> PlatformWideAuditDeliveryRules.workflowId(notAnId)).as(String.valueOf(notAnId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
