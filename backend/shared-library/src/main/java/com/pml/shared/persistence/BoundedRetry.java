package com.pml.shared.persistence;

import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Retrying a version conflict, with a limit.
 *
 * <h2>Why bounded is the whole point</h2>
 * <em>An unbounded retry under contention is a livelock that reports as latency.</em>
 * Optimistic locking converts a lost update into an exception, and the obvious
 * response is to retry — but under real contention the retry loses again, and again. Nothing
 * errors and nothing completes: requests pile up, p99 climbs, and the dashboard shows a slow
 * service rather than a stuck one. A bounded retry turns that into a visible failure, which is
 * the outcome someone can act on.
 *
 * <h2>Jitter, not a fixed delay</h2>
 * Two callers that collide and both wait exactly 50ms collide again at 50ms. The jitter is what
 * separates them; without it the retry reproduces the contention it is recovering from.
 *
 * <h2>This is not for the inventory decrement</h2>
 * The inventory's conditional {@code findAndModify} does not raise a version conflict — it either
 * matches and updates, or matches nothing and refuses. A refusal is an answer, not a failure,
 * and retrying it would be retrying "sold out". This class is for read-modify-write on versioned
 * documents where a conflict genuinely means "someone else got there first, read again".
 */
public final class BoundedRetry {

    /** Enough attempts to clear ordinary contention, few enough to fail visibly under real load. */
    public static final int DEFAULT_ATTEMPTS = 3;

    private static final Duration FIRST_BACKOFF = Duration.ofMillis(25);

    private BoundedRetry() {
    }

    /** Retries only version conflicts; every other error propagates untouched. */
    public static <T> Mono<T> onVersionConflict(Mono<T> operation) {
        return onVersionConflict(operation, DEFAULT_ATTEMPTS);
    }

    public static <T> Mono<T> onVersionConflict(Mono<T> operation, int attempts) {
        return operation.retryWhen(Retry
                .backoff(attempts, FIRST_BACKOFF)
                .jitter(0.5)
                .filter(error -> error instanceof OptimisticLockingFailureException)
                // Without this the exhausted retry is wrapped in RetryExhaustedException, and a
                // caller that handles OptimisticLockingFailureException stops recognising it.
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }
}
