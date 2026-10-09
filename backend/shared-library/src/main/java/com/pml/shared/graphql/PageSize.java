package com.pml.shared.graphql;

/**
 * The one place a requested page size is checked.
 *
 * <h2>Refused, never clamped</h2>
 * A request above the maximum is refused with {@code PAGE_SIZE_EXCEEDED}, and
 * <b>not</b> quietly reduced. The reason is what a client does next.
 *
 * <p>A great many pagination loops terminate on "I asked for N and got fewer than N, so that was
 * the last page". Clamping breaks exactly that: a client asks for 500, receives 100, concludes it
 * has read everything, and stops — having seen a fifth of the data with no error anywhere. The
 * server behaved defensively, the client behaved reasonably, and the result is silent data loss
 * at the only layer that could have noticed.</p>
 *
 * <p>Refusing costs the client one visible error and one code change. Clamping costs a
 * correctness bug that surfaces as "the report is missing rows" months later.</p>
 *
 * <h2>Why the limit exists at all</h2>
 * An unbounded list field is how one client takes the platform down at on-sale: a single query
 * for every ticket of a headline event, served from the same connection pool as five thousand
 * reservations.
 */
public final class PageSize {

    /** 100 for offset pages and for {@code first}/{@code last} on connections. */
    public static final int MAX = 100;

    /** Used where a caller supplies no size. */
    public static final int DEFAULT = 20;

    private PageSize() {
    }

    /**
     * Returns the size to use, or refuses.
     *
     * @param requested the client's value; null means "no preference" and yields {@link #DEFAULT}
     * @throws PageSizeExceeded if above {@link #MAX}
     * @throws IllegalArgumentException if zero or negative — a page of nothing is a bug in the
     *         caller, not a request to be honoured
     */
    public static int require(Integer requested) {
        return require(requested, MAX, DEFAULT);
    }

    /**
     * The same rule against a smaller ceiling.
     *
     * <p>Some fields are bounded well below {@link #MAX} — a dashboard's "recent activity" widget
     * has no business returning a hundred rows. Those still <b>refuse</b> rather than reduce: the
     * ceiling differs, the reason it must be visible does not. A caller silently given 50 of the
     * 100 it asked for is in exactly the position this class exists to prevent, and the smaller
     * the ceiling the more likely it is to be hit.</p>
     *
     * @param ceiling the maximum this particular field allows
     * @param fallback what to use when the caller expresses no preference
     */
    public static int require(Integer requested, int ceiling, int fallback) {
        if (requested == null) {
            return fallback;
        }
        if (requested > ceiling) {
            throw new PageSizeExceeded(requested, ceiling);
        }
        if (requested < 1) {
            throw new IllegalArgumentException(
                    "page size must be at least 1, got " + requested);
        }
        return requested;
    }

    /**
     * Raised when a client asks for more than {@link #MAX} rows.
     *
     * <p>The message carries both numbers because the client has to change its request, and
     * "too large" without the ceiling means guessing.</p>
     */
    public static class PageSizeExceeded extends RuntimeException {

        /** The registry code this refusal carries; named here so callers can match on it. */
        public static final String CODE = "PAGE_SIZE_EXCEEDED";

        private final int requested;
        private final int ceiling;

        public PageSizeExceeded(int requested) {
            this(requested, MAX);
        }

        public PageSizeExceeded(int requested, int ceiling) {
            super("requested page size %d exceeds the maximum of %d".formatted(requested, ceiling));
            this.requested = requested;
            this.ceiling = ceiling;
        }

        public int requested() {
            return requested;
        }

        /** The limit that applied, which may be below {@link #MAX} for a narrower field. */
        public int ceiling() {
            return ceiling;
        }

        public String code() {
            return CODE;
        }
    }
}
