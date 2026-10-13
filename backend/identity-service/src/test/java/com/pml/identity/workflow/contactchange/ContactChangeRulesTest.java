package com.pml.identity.workflow.contactchange;

import com.pml.identity.infrastructure.temporal.WorkflowIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

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

    private static final Instant NOW = Instant.parse("2026-10-13T08:00:00Z");

    @Test
    @DisplayName("a change may live its 48-hour wait plus a day to finish applying, and no longer")
    void maximumLifetime() {
        assertThat(ContactChangeRules.MAX_LIFETIME).isEqualTo(Duration.ofHours(72));
    }

    @Test
    @DisplayName("a marker with an expiry is expired from that instant, not before")
    void expiryBoundary() {
        Instant expiresAt = NOW.plus(ContactChangeRules.MAX_LIFETIME);

        assertThat(ContactChangeRules.expired(expiresAt, NOW, expiresAt.minusMillis(1))).isFalse();
        assertThat(ContactChangeRules.expired(expiresAt, NOW, expiresAt)).isTrue();
        assertThat(ContactChangeRules.expired(expiresAt, NOW, expiresAt.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("a marker older than owners is aged from its own timestamp, and one with neither is never expired by the clock")
    void legacyMarkerExpiry() {
        Instant since = NOW.minus(Duration.ofDays(10));

        assertThat(ContactChangeRules.expired(null, since, NOW)).isTrue();
        assertThat(ContactChangeRules.expired(null, NOW.minus(ContactChangeRules.MAX_LIFETIME).plusSeconds(1), NOW)).isFalse();
        assertThat(ContactChangeRules.expired(null, NOW.minus(ContactChangeRules.MAX_LIFETIME), NOW)).isTrue();
        assertThat(ContactChangeRules.expired(null, null, NOW)).isFalse();
    }
}
