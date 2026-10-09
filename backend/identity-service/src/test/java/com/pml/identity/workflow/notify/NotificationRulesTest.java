package com.pml.identity.workflow.notify;

import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.workflow.notify.NotificationWorkflow.Attempt;
import com.pml.identity.workflow.reminder.ReminderRules.Offset;
import io.temporal.common.RetryOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-NTF-001")
@DisplayName("ET-NTF-001-R2/R5/R6/R7 · notification rules: the chain, the budget, the key, the templates")
class NotificationRulesTest {

    @Test
    @DisplayName("R5 · WhatsApp, then email; the old WhatsApp-then-SMS chain survives only for executions already running")
    void chain() {
        assertThat(NotificationRules.CHAIN).containsExactly(NotificationChannel.WHATSAPP, NotificationChannel.EMAIL);
        assertThat(NotificationRules.LEGACY_CHAIN).containsExactly(NotificationChannel.WHATSAPP, NotificationChannel.SMS);
        assertThat(NotificationRules.channelName(NotificationChannel.WHATSAPP)).isEqualTo("whatsapp");
        assertThat(NotificationRules.channelName(NotificationChannel.EMAIL)).isEqualTo("email");
    }

    @Test
    @DisplayName("the channels for a recipient are the ones they can be reached on, their preferred one first")
    void chainFor() {
        var both = List.of(ContactType.WHATSAPP, ContactType.EMAIL);
        assertThat(NotificationRules.chainFor(null, both))
                .containsExactly(NotificationChannel.WHATSAPP, NotificationChannel.EMAIL);
        assertThat(NotificationRules.chainFor(ContactType.EMAIL, both))
                .containsExactly(NotificationChannel.EMAIL, NotificationChannel.WHATSAPP);
        assertThat(NotificationRules.chainFor(ContactType.WHATSAPP, List.of(ContactType.EMAIL)))
                .as("a preference for a channel with no verified contact is ignored")
                .containsExactly(NotificationChannel.EMAIL);
        assertThat(NotificationRules.chainFor(null, List.of(ContactType.EMAIL)))
                .as("an email-only account is not offered WhatsApp")
                .containsExactly(NotificationChannel.EMAIL);
        assertThat(NotificationRules.chainFor(ContactType.EMAIL, List.of()))
                .as("reachable nowhere: the default chain, so the notification fails visibly")
                .isEqualTo(NotificationRules.CHAIN);
        assertThat(NotificationRules.contactTypeOf(NotificationChannel.SMS)).as("SMS is not addressed by a contact").isNull();
    }

    @Test
    @DisplayName("R6 · three attempts per channel from PT30S; a missing destination is not retried")
    void sendBudget() {
        RetryOptions retry = NotificationRules.sendOptions().getRetryOptions();
        assertThat(retry.getMaximumAttempts()).isEqualTo(3);
        assertThat(retry.getInitialInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(retry.getDoNotRetry()).containsExactly(NotificationRules.NO_DESTINATION);
    }

    @Test
    @DisplayName("R7 · a key is the template and its discriminator, and names one notification row")
    void keys() {
        assertThat(NotificationRules.key("team.accepted", "inv-1")).isEqualTo("team.accepted:inv-1");
        assertThat(NotificationRules.notificationId("team.accepted:inv-1")).isEqualTo("notify:team.accepted:inv-1");
        assertThatThrownBy(() -> NotificationRules.key("team.accepted", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("R2 · every template this service requests is registered; an unregistered one is refused")
    void templates() {
        for (String key : List.of("team.invitation", "team.accepted", "document.approved", "document.rejected",
                "ownership.transfer-requested", "ownership.confirmed")) {
            assertThat(NotificationRules.registered(key)).as(key).isTrue();
        }
        for (Offset offset : Offset.values()) {
            assertThat(NotificationRules.registered(offset.templateKey())).as(offset.name()).isTrue();
        }
        for (String key : List.of("finance.chargeback-undecided", "finance.refund-waiting")) {
            assertThat(NotificationRules.isFinanceEscalation(key)).as(key).isTrue();
        }
        assertThat(NotificationRules.isFinanceEscalation("team.invitation")).isFalse();
        assertThatThrownBy(() -> NotificationRules.render("payout.unheard-of")).isInstanceOf(IllegalArgumentException.class);
        assertThat(NotificationRules.render("team.invitation").body()).doesNotContain("{").doesNotContain("null");
    }

    @Test
    @DisplayName("R6 · an exhausted chain records every channel's last failure")
    void exhausted() {
        String reason = NotificationRules.exhausted(List.of(
                new Attempt(NotificationChannel.WHATSAPP, "FAILED", NotificationRules.CHANNEL_UNAVAILABLE),
                new Attempt(NotificationChannel.EMAIL, "FAILED", NotificationRules.NO_DESTINATION)));
        assertThat(reason).contains("WHATSAPP=CHANNEL_UNAVAILABLE").contains("EMAIL=NO_DESTINATION");
        assertThat(NotificationRules.exhausted(List.of())).isEqualTo("no channel was attempted");
    }
}
