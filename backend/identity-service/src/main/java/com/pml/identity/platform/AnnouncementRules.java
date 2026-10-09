package com.pml.identity.platform;

import com.pml.identity.domain.enums.AnnouncementSegment;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Pure rules for announcements: who a segment reaches, and what a valid announcement is. */
public final class AnnouncementRules {

    public static final int TITLE_MAX = 120;
    public static final int MESSAGE_MAX = 1000;

    private AnnouncementRules() {
    }

    /**
     * The segments a caller belongs to. Every signed-in caller is reachable by ALL; ORGANIZERS and
     * STAFF come from roles; BUYERS is everyone who is not staff, because an organizer buys
     * tickets too.
     */
    public static Set<AnnouncementSegment> segmentsOf(boolean organizer, boolean staff) {
        java.util.EnumSet<AnnouncementSegment> segments = java.util.EnumSet.of(AnnouncementSegment.ALL);
        if (organizer) segments.add(AnnouncementSegment.ORGANIZERS);
        if (staff) segments.add(AnnouncementSegment.STAFF);
        if (!staff) segments.add(AnnouncementSegment.BUYERS);
        return segments;
    }

    /** Refuses an announcement that is empty, too long, or whose window runs backwards. */
    public static void validate(String title, String message, Instant startsAt, Instant endsAt) {
        if (title == null || title.isBlank() || title.length() > TITLE_MAX) {
            throw invalid("title 1.." + TITLE_MAX + " characters");
        }
        if (message == null || message.isBlank() || message.length() > MESSAGE_MAX) {
            throw invalid("message 1.." + MESSAGE_MAX + " characters");
        }
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw invalid("endsAt after startsAt");
        }
    }

    private static TranslatedRefusal invalid(String constraint) {
        return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "announcement invalid: " + constraint,
                Map.of("constraint", constraint));
    }
}
