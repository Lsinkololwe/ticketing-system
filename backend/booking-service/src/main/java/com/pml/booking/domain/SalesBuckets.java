package com.pml.booking.domain;

import com.pml.shared.constants.PlatformTime;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * The time buckets a sales series is cut into, in Lusaka time.
 *
 * <p>A day is the day the buyer lived, not the UTC day: a ticket bought at 23:30 in Lusaka belongs to
 * that evening, not to tomorrow. Series are generated with every bucket in range present, so a chart
 * draws a quiet day as a zero and not as a gap.
 */
public final class SalesBuckets {

    public enum Bucket {
        HOUR("hour", Duration.ofDays(7)),
        DAY("day", Duration.ofDays(366)),
        WEEK("week", Duration.ofDays(731));

        private final String unit;
        private final Duration maxRange;

        Bucket(String unit, Duration maxRange) {
            this.unit = unit;
            this.maxRange = maxRange;
        }

        /** The unit name MongoDB's {@code $dateTrunc} takes. */
        public String unit() {
            return unit;
        }

        /** The longest span a series of this granularity may cover. */
        public Duration maxRange() {
            return maxRange;
        }
    }

    private SalesBuckets() {
    }

    /** The start of the bucket containing {@code at}. Weeks start on Monday. */
    public static ZonedDateTime floor(Instant at, Bucket bucket) {
        ZonedDateTime local = PlatformTime.atZone(at);
        return switch (bucket) {
            case HOUR -> local.truncatedTo(ChronoUnit.HOURS);
            case DAY -> local.truncatedTo(ChronoUnit.DAYS);
            case WEEK -> local.truncatedTo(ChronoUnit.DAYS).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        };
    }

    public static ZonedDateTime next(ZonedDateTime start, Bucket bucket) {
        return switch (bucket) {
            case HOUR -> start.plusHours(1);
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
        };
    }

    /** Every bucket start from the one containing {@code from} up to, and excluding, the one after {@code to}. */
    public static List<ZonedDateTime> between(Instant from, Instant to, Bucket bucket) {
        List<ZonedDateTime> starts = new ArrayList<>();
        ZonedDateTime cursor = floor(from, bucket);
        while (!cursor.toInstant().isAfter(to)) {
            starts.add(cursor);
            cursor = next(cursor, bucket);
        }
        return starts;
    }
}
