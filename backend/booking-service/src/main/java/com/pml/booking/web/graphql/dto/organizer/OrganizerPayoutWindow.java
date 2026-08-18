package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * How much of an organizer's money is withdrawable now, and when the next
 * tranche unlocks.
 *
 * <p>Backs the dashboard's cash-available tile. The tile draws elapsed against
 * total as a two-segment bullet meter, so the server sends both ends of the
 * window rather than a bare percentage.
 *
 * <p>The window is the escrow hold on the organizer's earliest still-locked
 * event account: funds are held until the event has run and the refund window
 * has closed, then the account flips to {@code PAYOUT_ELIGIBLE}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerPayoutWindow {

    /**
     * Sum of balances on accounts already flipped to payout-eligible. This is
     * the figure the organizer can request today.
     */
    @Builder.Default
    private BigDecimal availableNow = BigDecimal.ZERO;

    /**
     * Sum of balances still held in escrow for events that have not cleared.
     * Shown as a caveat under the headline figure so "available" is never read
     * as "everything I have earned".
     */
    @Builder.Default
    private BigDecimal pendingRelease = BigDecimal.ZERO;

    @Builder.Default
    private String currency = "ZMW";

    /**
     * When the earliest still-locked escrow account opened, and when it
     * unlocks. Null when nothing is locked — the client then renders the
     * headline figure with no meter rather than a meter at 100%.
     */
    private LocalDateTime windowOpenedAt;

    private LocalDateTime nextReleaseAt;

    /**
     * Whole days from {@link #windowOpenedAt} to {@link #nextReleaseAt}.
     */
    @Builder.Default
    private Integer windowDaysTotal = 0;

    /**
     * Whole days already elapsed, clamped to {@link #windowDaysTotal} so a
     * clock skew or an overdue release can never paint past the track.
     */
    @Builder.Default
    private Integer daysElapsed = 0;

    public Integer getDaysRemaining() {
        if (windowDaysTotal == null || daysElapsed == null) return 0;
        return Math.max(0, windowDaysTotal - daysElapsed);
    }

    /**
     * Builds the window from the earliest locked account's timestamps.
     *
     * @param openedAt  when the hold started
     * @param releaseAt when the hold ends
     * @param now       evaluation instant, injected so this stays testable
     */
    public static OrganizerPayoutWindowBuilder windowBetween(
            Instant openedAt, Instant releaseAt, Instant now) {
        OrganizerPayoutWindowBuilder builder = OrganizerPayoutWindow.builder();
        if (openedAt == null || releaseAt == null || !releaseAt.isAfter(openedAt)) {
            return builder;
        }
        long total = Duration.between(openedAt, releaseAt).toDays();
        long elapsed = Duration.between(openedAt, now).toDays();
        return builder
                .windowOpenedAt(LocalDateTime.ofInstant(openedAt, ZoneOffset.UTC))
                .nextReleaseAt(LocalDateTime.ofInstant(releaseAt, ZoneOffset.UTC))
                .windowDaysTotal((int) Math.max(0, total))
                .daysElapsed((int) Math.min(Math.max(0, elapsed), Math.max(0, total)));
    }

    public static OrganizerPayoutWindow empty() {
        return OrganizerPayoutWindow.builder().build();
    }
}
