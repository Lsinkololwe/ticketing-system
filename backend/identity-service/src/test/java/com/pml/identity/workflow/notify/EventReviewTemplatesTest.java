package com.pml.identity.workflow.notify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The messages an event review sends: registered, worded without personal data, and not usable for anything else. Pure values. */
@Tag("L1")
@Tag("ET-NTF-002")
@DisplayName("Event review templates are registered and confined to event reviews")
class EventReviewTemplatesTest {

    private static final List<String> REVIEW_TEMPLATES = List.of(
            "admin.event-pending", "event.approved", "event.rejected", "event.changes-requested");

    @Test
    @DisplayName("Each review template renders a title and body with no placeholder in it")
    void templatesRender() {
        for (String template : REVIEW_TEMPLATES) {
            NotificationRules.Message message = NotificationRules.render(template);
            assertThat(message.title()).as(template).isNotBlank();
            assertThat(message.body()).as(template).isNotBlank().doesNotContain("{", "%s");
        }
    }

    @Test
    @DisplayName("Only the four review templates count as review templates")
    void reviewTemplatesAreClosed() {
        assertThat(REVIEW_TEMPLATES).allMatch(NotificationRules::isEventReview);
        for (String other : List.of("event.reminder.24h", "finance.refund-waiting", "team.invitation", "event.unknown", "")) {
            assertThat(NotificationRules.isEventReview(other)).as(other).isFalse();
        }
        assertThat(NotificationRules.isEventReview(null)).isFalse();
    }

    @Test
    @DisplayName("Adding the review templates kept every earlier template")
    void earlierTemplatesSurvive() {
        for (String earlier : List.of("team.invitation", "team.accepted", "document.approved", "document.rejected",
                "ownership.transfer-requested", "ownership.confirmed", "event.reminder.24h", "event.reminder.1h",
                "finance.chargeback-undecided", "finance.refund-waiting")) {
            assertThat(NotificationRules.registered(earlier)).as(earlier).isTrue();
        }
    }
}
