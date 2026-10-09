package com.pml.identity.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Whether an instant falls inside a user's nightly quiet period.
 *
 * <p>The period is read in the user's zone, or the platform's when they set none. An end earlier
 * than the start means the period crosses midnight: 22:00–07:00 covers 23:30 and 06:59, not 12:00.
 */
public final class QuietHours {

    public static final ZoneId PLATFORM_ZONE = ZoneId.of("Africa/Lusaka");

    private QuietHours() {
    }

    public static boolean isZone(String zone) {
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    public static boolean contains(String start, String end, String zone, Instant now) {
        if (start == null || end == null) {
            return false;
        }
        ZoneId id = zone != null && isZone(zone) ? ZoneId.of(zone) : PLATFORM_ZONE;
        LocalTime time = now.atZone(id).toLocalTime();
        LocalTime from = LocalTime.parse(start);
        LocalTime to = LocalTime.parse(end);
        if (from.equals(to)) {
            return false;
        }
        return from.isBefore(to)
                ? !time.isBefore(from) && time.isBefore(to)
                : !time.isBefore(from) || time.isBefore(to);
    }
}
