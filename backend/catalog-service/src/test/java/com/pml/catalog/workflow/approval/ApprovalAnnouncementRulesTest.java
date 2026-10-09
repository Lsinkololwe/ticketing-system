package com.pml.catalog.workflow.approval;

import com.pml.catalog.workflow.approval.ApprovalRules.ApprovalBlocker;
import com.pml.shared.constants.EventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What blocks an approval, and which message each review outcome sends. Pure values. */
@Tag("L1")
@Tag("ET-ADM-001")
@DisplayName("Approval blockers and review announcements")
class ApprovalAnnouncementRulesTest {

    @Test
    @DisplayName("Each missing piece is one blocker, in a fixed order, and a complete event has none")
    void blockers() {
        assertThat(ApprovalRules.approvalBlockers(false, null, 0))
                .containsExactly(ApprovalBlocker.NO_PUBLISHED_TIER, ApprovalBlocker.NO_LOCATION, ApprovalBlocker.NO_CAPACITY);
        assertThat(ApprovalRules.approvalBlockers(true, " ", 10)).containsExactly(ApprovalBlocker.NO_LOCATION);
        assertThat(ApprovalRules.approvalBlockers(true, "venue-1", -1)).containsExactly(ApprovalBlocker.NO_CAPACITY);
        assertThat(ApprovalRules.approvalBlockers(true, "venue-1", 1)).isEmpty();
    }

    @Test
    @DisplayName("The approval refusal names exactly what the blockers list")
    void refusalMatchesBlockers() {
        assertThat(ApprovalRules.approvalPreconditionRefusal(false, null, 5)).hasValueSatisfying(refusal ->
                assertThat(refusal.message()).isEqualTo("the event cannot be approved: it is missing a published ticket tier, a location"));
        assertThat(ApprovalRules.approvalPreconditionRefusal(true, "venue-1", 5)).isEmpty();
    }

    @Test
    @DisplayName("Joining the queue tells administrators; each decision tells the organizer; other statuses tell nobody")
    void announcementTemplates() {
        assertThat(ApprovalRules.announcementTemplate(EventStatus.PENDING_APPROVAL)).isEqualTo("admin.event-pending");
        assertThat(ApprovalRules.announcementTemplate(EventStatus.APPROVED)).isEqualTo("event.approved");
        assertThat(ApprovalRules.announcementTemplate(EventStatus.REJECTED)).isEqualTo("event.rejected");
        assertThat(ApprovalRules.announcementTemplate(EventStatus.CHANGES_REQUESTED)).isEqualTo("event.changes-requested");
        for (EventStatus quiet : new EventStatus[]{EventStatus.DRAFT, EventStatus.PUBLISHED, EventStatus.CANCELLED, EventStatus.COMPLETED}) {
            assertThat(ApprovalRules.announcementTemplate(quiet)).as(quiet.name()).isNull();
        }
        assertThat(ApprovalRules.announcementTemplate(null)).isNull();
    }

    @Test
    @DisplayName("A resubmission is a new announcement; a retry of the same one is not")
    void announcementKeys() {
        String first = ApprovalRules.announcementKey("event-1", 1, EventStatus.PENDING_APPROVAL);
        String resubmitted = ApprovalRules.announcementKey("event-1", 2, EventStatus.PENDING_APPROVAL);

        assertThat(first).isEqualTo("event-1:1:PENDING_APPROVAL");
        assertThat(resubmitted).isNotEqualTo(first);
        assertThat(ApprovalRules.announcementKey("event-1", 1, EventStatus.PENDING_APPROVAL)).isEqualTo(first);
        assertThat(ApprovalRules.announcementKey("event-1", 1, EventStatus.APPROVED)).isNotEqualTo(first);
    }
}
