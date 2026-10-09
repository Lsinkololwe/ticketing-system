package com.pml.identity.platform;

import com.pml.identity.service.GrowthBuckets;
import com.pml.identity.service.GrowthBuckets.Bucket;
import com.pml.identity.web.graphql.dto.platform.GrowthPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-ADM-004")
@DisplayName("ET-ADM-004-R9 · growth buckets are Zambian days, Monday weeks and calendar months, with no gaps")
class GrowthBucketsTest {

    @Test
    @DisplayName("a day starts at local midnight, which is 22:00 UTC the evening before")
    void dayStartsAtLocalMidnight() {
        // 23:30 UTC on 3 Oct is 01:30 on 4 Oct in Lusaka (UTC+2).
        assertThat(GrowthBuckets.start(Instant.parse("2026-10-03T23:30:00Z"), Bucket.DAY).toInstant())
                .isEqualTo(Instant.parse("2026-10-03T22:00:00Z"));
    }

    @Test
    @DisplayName("a week starts on Monday and a month on the first")
    void weekAndMonth() {
        // 2026-10-04 is a Sunday.
        assertThat(GrowthBuckets.start(Instant.parse("2026-10-04T10:00:00Z"), Bucket.WEEK).toInstant())
                .isEqualTo(Instant.parse("2026-09-27T22:00:00Z"));
        assertThat(GrowthBuckets.start(Instant.parse("2026-10-17T10:00:00Z"), Bucket.MONTH).toInstant())
                .isEqualTo(Instant.parse("2026-09-30T22:00:00Z"));
    }

    @Test
    @DisplayName("every bucket in the range is present, empty ones as zero, and the running total carries over")
    void fillsGaps() {
        Instant from = Instant.parse("2026-10-01T10:00:00Z");
        Instant to = Instant.parse("2026-10-04T10:00:00Z");
        Instant oct1 = Instant.parse("2026-09-30T22:00:00Z");
        Instant oct3 = Instant.parse("2026-10-02T22:00:00Z");

        List<GrowthPoint> series = GrowthBuckets.fill(from, to, Bucket.DAY, Map.of(oct1, 2, oct3, 5), 100);

        assertThat(series).extracting(GrowthPoint::newUsers).containsExactly(2, 0, 5, 0);
        assertThat(series).extracting(GrowthPoint::cumulative).containsExactly(102L, 102L, 107L, 107L);
    }
}
