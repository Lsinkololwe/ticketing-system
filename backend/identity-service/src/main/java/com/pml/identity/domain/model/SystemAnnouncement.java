package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.AlertSeverity;
import com.pml.identity.domain.enums.AnnouncementSegment;
import com.pml.identity.persistence.IdentityCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A message from the platform to a segment, shown from {@code startsAt} until {@code endsAt}
 * (open-ended when null). It is read by the clients, not pushed: a scheduled announcement is
 * simply one whose start is in the future.
 */
@Document(collection = IdentityCollections.ANNOUNCEMENTS)
@TypeAlias("announcements")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SystemAnnouncement {

    @Id
    private String id;

    private String title;

    private String message;

    private AnnouncementSegment segment;

    private AlertSeverity severity;

    private Instant startsAt;

    private Instant endsAt;

    private Instant cancelledAt;

    private String cancelledBy;

    private String createdBy;

    private Instant createdAt;

    /** Live at {@code now}: started, not ended, not cancelled. */
    public boolean activeAt(Instant now) {
        return cancelledAt == null
                && !startsAt.isAfter(now)
                && (endsAt == null || endsAt.isAfter(now));
    }
}
