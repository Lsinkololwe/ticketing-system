package com.pml.shared.constants;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The zone the platform means when it says "today".
 *
 * <h2>Why this class has to exist for the {@code Instant} migration to be honest</h2>
 * {@code LocalDateTime} looks like it needs no zone, and that is the problem: it silently uses
 * the JVM's default wherever the question comes up. On a laptop in Lusaka that is right by
 * accident; in CI and on a cloud host running UTC it is two hours out, so an event at 00:30
 * local belongs to the previous day and a "happening today" list quietly omits it.
 *
 * <p>{@link Instant} has no calendar at all — no {@code toLocalDate}, no {@code format}, no
 * {@code plusHours} — which forces the zone to be named at every boundary. That is not friction
 * to work around; it is the defect surfacing. Every conversion goes through here so the answer
 * is the same one everywhere.</p>
 *
 * <h2>Africa/Lusaka, not a fixed offset</h2>
 * Zambia is UTC+2 year-round with no daylight saving, so {@code ZoneOffset.ofHours(2)} would
 * behave identically today. The named zone is used anyway: an offset is a fact about now, a
 * zone is a rule, and if the rule ever changes the tz database carries the change while a
 * hard-coded offset silently does not.
 */
public final class PlatformTime {

    /** The platform's civil time zone. Business days, "today" and rendered times use this. */
    public static final ZoneId ZONE = ZoneId.of("Africa/Lusaka");

    private PlatformTime() {
    }

    /** The civil date this instant falls on, in the platform's zone. */
    public static LocalDate dateAt(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE).toLocalDate();
    }

    /** The instant as civil time, for rendering or for calendar arithmetic. */
    public static ZonedDateTime atZone(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE);
    }

    /** Renders an instant with the given pattern, in the platform's zone. */
    public static String format(Instant instant, DateTimeFormatter formatter) {
        return instant == null ? null : formatter.format(atZone(instant));
    }

    /**
     * Parses civil time that carries no offset — {@code 2026-08-18T10:30:00} — as the platform's
     * zone.
     *
     * <p>A client sending a wall-clock time means a time in Zambia; interpreting it as UTC moves
     * every event two hours earlier, which reads as an off-by-two bug in the display layer
     * rather than as a parsing decision made here.</p>
     */
    public static Instant parseLocal(CharSequence text, DateTimeFormatter formatter) {
        return java.time.LocalDateTime.parse(text, formatter).atZone(ZONE).toInstant();
    }
}
