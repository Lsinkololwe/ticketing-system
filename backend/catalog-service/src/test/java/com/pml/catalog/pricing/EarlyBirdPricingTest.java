package com.pml.catalog.pricing;

import com.pml.catalog.domain.model.TicketTier;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a tier costs, at the two minutes that decide it.
 *
 * <h2>Why this test could not be written until today</h2>
 * {@code getCurrentPrice()} read {@code Instant.now()} directly. The only ways to assert what it
 * returns the minute before an early-bird window closes were to wait for a real window to close or
 * to trust the comparison by reading it. So the method that decides the advertised price of every
 * ticket on the platform had <b>no test at all</b>. An
 * {@code Instant.now()} next to a deadline is not only a lint violation, it is the reason the
 * boundary case is untested.
 *
 * <h2>Both bounds, deliberately</h2>
 * One assertion either side of the deadline. "Returns the early-bird price during the window"
 * passes against an implementation that never charges full price; "returns full price after"
 * passes against one that never discounts. Only the pair pins the behaviour, and the pair is what
 * an off-by-one in the comparison breaks.
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001 R3 · early-bird pricing, at the boundary")
class EarlyBirdPricingTest {

    private static final Instant WINDOW_CLOSES = Instant.parse("2026-09-10T23:59:59Z");
    private static final BigDecimal FULL = new BigDecimal("200.00");
    private static final BigDecimal EARLY = new BigDecimal("150.00");

    private static TicketTier tier() {
        return TicketTier.builder()
                .price(FULL)
                .earlyBirdPrice(EARLY)
                .earlyBirdEndsAt(WINDOW_CLOSES)
                .build();
    }

    @Test
    @DisplayName("a minute before the window closes, the early-bird price applies")
    void justBeforeTheWindowCloses() {
        Instant now = TestClock.frozenAt(WINDOW_CLOSES).justBefore(WINDOW_CLOSES).instant();

        assertThat(tier().getCurrentPrice(now)).isEqualByComparingTo(EARLY);
        assertThat(tier().isEarlyBirdActive(now)).isTrue();
    }

    @Test
    @DisplayName("a minute after, it does not")
    void justAfterTheWindowCloses() {
        Instant now = TestClock.frozenAt(WINDOW_CLOSES).justAfter(WINDOW_CLOSES).instant();

        assertThat(tier().getCurrentPrice(now)).isEqualByComparingTo(FULL);
        assertThat(tier().isEarlyBirdActive(now)).isFalse();
    }

    @Test
    @DisplayName("at the instant itself the window is over — isBefore is exclusive")
    void atTheInstantItself() {
        // Worth pinning rather than leaving to whoever next reads the comparison. `isBefore` is
        // exclusive, so a tier whose window "ends at 23:59:59" is already full price at 23:59:59.
        // That is a defensible reading of a deadline; the point is that it is now a decision the
        // test records rather than an accident of which operator was typed.
        assertThat(tier().getCurrentPrice(WINDOW_CLOSES)).isEqualByComparingTo(FULL);
    }

    @Test
    @DisplayName("a tier with no early-bird configured is simply full price, always")
    void noEarlyBirdConfigured() {
        TicketTier plain = TicketTier.builder().price(FULL).build();
        Instant during = WINDOW_CLOSES.minusSeconds(86_400);

        // Both nulls, separately: a half-configured tier (a price with no deadline, or a deadline
        // with no price) must not discount, and must not throw either — an organiser filling one
        // field and saving is an ordinary thing to do.
        assertThat(plain.getCurrentPrice(during)).isEqualByComparingTo(FULL);
        assertThat(plain.isEarlyBirdActive(during)).isFalse();

        TicketTier priceOnly = TicketTier.builder().price(FULL).earlyBirdPrice(EARLY).build();
        assertThat(priceOnly.getCurrentPrice(during)).isEqualByComparingTo(FULL);

        TicketTier deadlineOnly = TicketTier.builder()
                .price(FULL).earlyBirdEndsAt(WINDOW_CLOSES).build();
        assertThat(deadlineOnly.getCurrentPrice(during)).isEqualByComparingTo(FULL);
    }

    @Test
    @DisplayName("savings and discount percentage move with the window, not with the wall clock")
    void savingsFollowTheSameBoundary() {
        TicketTier withOriginal = TicketTier.builder()
                .price(FULL)
                .originalPrice(FULL)
                .earlyBirdPrice(EARLY)
                .earlyBirdEndsAt(WINDOW_CLOSES)
                .build();

        Instant during = WINDOW_CLOSES.minusSeconds(60);
        Instant after = WINDOW_CLOSES.plusSeconds(60);

        assertThat(withOriginal.getSavings(during)).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(withOriginal.getDiscountPercentage(during)).isEqualTo(25);

        assertThat(withOriginal.getSavings(after)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(withOriginal.getDiscountPercentage(after)).isEqualTo(0);
    }
}
