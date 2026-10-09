package com.pml.identity.service;

import com.pml.shared.constants.PlatformTime;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Time buckets for the user-growth series, in the platform's own time zone so a day is a Zambian
 * day. Pure: the series is filled so every bucket in the range is present, with zero where nobody
 * joined, because a chart with missing days draws a line between the days either side.
 */
public final class GrowthBuckets {

    public enum Bucket { DAY, WEEK, MONTH }

    private GrowthBuckets() {
    }

    /** The start of the bucket containing {@code instant}; weeks start on Monday. */
    public static ZonedDateTime start(Instant instant, Bucket bucket) {
        ZonedDateTime local = PlatformTime.atZone(instant).toLocalDate().atStartOfDay(PlatformTime.ZONE);
        return switch (bucket) {
            case DAY -> local;
            case WEEK -> local.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> local.withDayOfMonth(1);
        };
    }

    private static ZonedDateTime next(ZonedDateTime start, Bucket bucket) {
        return switch (bucket) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
        };
    }

    /**
     * @param counts       joiners per bucket start, from the database (buckets with none are absent)
     * @param before       accounts that existed before {@code from}; the cumulative starts here
     * @param to           exclusive end of the range
     */
    public static List<com.pml.identity.web.graphql.dto.platform.GrowthPoint> fill(
            Instant from, Instant to, Bucket bucket, Map<Instant, Integer> counts, long before) {
        List<com.pml.identity.web.graphql.dto.platform.GrowthPoint> series = new ArrayList<>();
        long running = before;
        for (ZonedDateTime cursor = start(from, bucket); cursor.toInstant().isBefore(to); cursor = next(cursor, bucket)) {
            int joined = counts.getOrDefault(cursor.toInstant(), 0);
            running += joined;
            series.add(new com.pml.identity.web.graphql.dto.platform.GrowthPoint(cursor.toInstant(), joined, running));
        }
        return series;
    }
}
