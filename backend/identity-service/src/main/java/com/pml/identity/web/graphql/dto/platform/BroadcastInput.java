package com.pml.identity.web.graphql.dto.platform;

import com.pml.identity.domain.enums.AlertSeverity;
import com.pml.identity.domain.enums.AnnouncementSegment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** An announcement to publish; {@code startsAt} null means now, a future one schedules it. */
public record BroadcastInput(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String message,
        AnnouncementSegment segment,
        AlertSeverity severity,
        Instant startsAt,
        Instant endsAt
) {}
