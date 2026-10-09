package com.pml.identity.platform;

import com.pml.identity.domain.enums.AnnouncementSegment;
import com.pml.identity.domain.model.SystemAnnouncement;
import com.pml.shared.error.DomainRefusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-ADM-005")
@DisplayName("ET-ADM-005-R9 · announcements: who a segment reaches, when one is live, what is valid")
class AnnouncementRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    @Test
    void segmentsOfACaller() {
        assertThat(AnnouncementRules.segmentsOf(false, false))
                .containsExactlyInAnyOrder(AnnouncementSegment.ALL, AnnouncementSegment.BUYERS);
        assertThat(AnnouncementRules.segmentsOf(true, false))
                .as("an organizer buys tickets too")
                .containsExactlyInAnyOrder(AnnouncementSegment.ALL, AnnouncementSegment.BUYERS, AnnouncementSegment.ORGANIZERS);
        assertThat(AnnouncementRules.segmentsOf(false, true))
                .containsExactlyInAnyOrder(AnnouncementSegment.ALL, AnnouncementSegment.STAFF);
    }

    @Test
    void liveOnlyInsideItsWindowAndNotWhenCancelled() {
        SystemAnnouncement scheduled = SystemAnnouncement.builder().startsAt(NOW.plusSeconds(60)).build();
        SystemAnnouncement open = SystemAnnouncement.builder().startsAt(NOW.minusSeconds(60)).build();
        SystemAnnouncement ended = SystemAnnouncement.builder().startsAt(NOW.minusSeconds(120)).endsAt(NOW).build();
        SystemAnnouncement cancelled = SystemAnnouncement.builder().startsAt(NOW.minusSeconds(60)).cancelledAt(NOW).build();

        assertThat(scheduled.activeAt(NOW)).isFalse();
        assertThat(open.activeAt(NOW)).isTrue();
        assertThat(ended.activeAt(NOW)).as("the end is exclusive").isFalse();
        assertThat(cancelled.activeAt(NOW)).isFalse();
    }

    @Test
    void validation() {
        AnnouncementRules.validate("Maintenance tonight", "Back by 02:00.", NOW, NOW.plusSeconds(3600));
        assertThatThrownBy(() -> AnnouncementRules.validate(" ", "x", NOW, null)).isInstanceOf(DomainRefusal.class);
        assertThatThrownBy(() -> AnnouncementRules.validate("t", "", NOW, null)).isInstanceOf(DomainRefusal.class);
        assertThatThrownBy(() -> AnnouncementRules.validate("t".repeat(121), "m", NOW, null)).isInstanceOf(DomainRefusal.class);
        assertThatThrownBy(() -> AnnouncementRules.validate("t", "m".repeat(1001), NOW, null)).isInstanceOf(DomainRefusal.class);
        assertThatThrownBy(() -> AnnouncementRules.validate("t", "m", NOW, NOW)).isInstanceOf(DomainRefusal.class);
    }
}
