package com.pml.shared.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * Real threads, released together, against real infrastructure.
 *
 * <h2>Why a start gate</h2>
 * ET-PLT-006 R5 requires the platform's concurrency guarantees to be proven under
 * <em>contention</em>. Submitting 200 tasks to a pool does not produce contention — the
 * first finishes long before the last begins, and a read-modify-write implementation
 * sails through. Every caller therefore blocks on one latch and is released in the same
 * instant, so the writes genuinely collide.
 *
 * <p>Virtual threads, so 200 concurrent callers cost 200 continuations rather than 200
 * platform threads, and the number can rise to the on-sale peak this platform is sized for
 * (D-16: 5,000 reservations/minute against a single event) without the harness itself
 * becoming the bottleneck being measured.
 *
 * <h2>Repeat, or it is luck</h2>
 * R5's last box: <em>"repeated enough times to be meaningful rather than lucky"</em>. A
 * concurrency test that passed once has told you nothing, and a flaky one is worse than
 * none — it trains everyone to re-run until green, which is exactly how a real oversell
 * reaches production. {@link #repeat} exists so that is one call rather than a loop
 * somebody forgets to write.
 */
public final class Concurrency {

    private Concurrency() {
    }

    /**
     * What happened when everyone went at once.
     *
     * @param successes values returned by callers that completed
     * @param failures  what the rest threw — refusals are expected outcomes here, not errors
     */
    public record Outcome<T>(List<T> successes, List<Throwable> failures) {

        public int successCount() {
            return successes.size();
        }

        public int failureCount() {
            return failures.size();
        }

        /** Every failure whose type or message names the given refusal. */
        public List<Throwable> failuresMatching(String fragment) {
            return failures.stream()
                    .filter(t -> describe(t).contains(fragment))
                    .toList();
        }

        public long countFailuresMatching(String fragment) {
            return failuresMatching(fragment).size();
        }

        private static String describe(Throwable t) {
            return t.getClass().getSimpleName() + " " + String.valueOf(t.getMessage());
        }
    }

    /**
     * Runs {@code callers} copies of {@code action} simultaneously and gathers every outcome.
     *
     * <p>Failures are collected rather than thrown: in every scenario R5 names, most callers
     * are <em>supposed</em> to fail — 150 of the 200 reservations must be refused — so a
     * refusal is data, not an error.
     */
    public static <T> Outcome<T> inParallel(int callers, IntFunction<T> action) {
        List<T> successes = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(callers);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < callers; i++) {
                final int caller = i;
                pool.submit(() -> {
                    try {
                        startGate.await();
                        successes.add(action.apply(caller));
                    } catch (Throwable failure) {
                        failures.add(failure);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            startGate.countDown();          // everyone goes, now
            if (!finished.await(2, TimeUnit.MINUTES)) {
                throw new AssertionError(
                        "%d of %d callers had not finished after 2 minutes"
                                .formatted(finished.getCount(), callers));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while awaiting parallel callers", e);
        }

        return new Outcome<>(List.copyOf(successes), List.copyOf(failures));
    }

    /**
     * Runs a scenario repeatedly, so a pass means something.
     *
     * <p>Reports which iteration failed — a contention bug that shows up on run 14 of 20 is
     * the interesting kind, and "it failed sometimes" is not a bug report.
     */
    public static void repeat(int times, Runnable scenario) {
        for (int run = 1; run <= times; run++) {
            try {
                scenario.run();
            } catch (Throwable failure) {
                throw new AssertionError(
                        "contention scenario failed on run %d of %d".formatted(run, times), failure);
            }
        }
    }
}
