package com.pml.catalog.workflow.approval;

import java.time.Duration;

/**
 * The SLA clock: time spent waiting on the platform, excluding time spent waiting on
 * the applicant.
 *
 * <p>It runs from submission, stops while changes are requested and resumes on resubmission. The
 * elapsed time is carried as a sum rather than recomputed from timestamps, so any number of
 * change-request rounds costs one number of state.
 */
public final class SlaClock {

    private long accumulatedMillis;
    private long runningSinceMillis;
    private boolean running;

    private SlaClock(long accumulatedMillis, boolean running, long nowMillis) {
        this.accumulatedMillis = Math.max(0L, accumulatedMillis);
        this.running = running;
        this.runningSinceMillis = nowMillis;
    }

    /** A clock started by a submission at {@code nowMillis}. */
    public static SlaClock startedAt(long nowMillis) {
        return new SlaClock(0L, true, nowMillis);
    }

    /** A clock resumed from what is already known: the elapsed time, and whether it is counting. */
    public static SlaClock of(long elapsedMillis, boolean running, long nowMillis) {
        return new SlaClock(elapsedMillis, running, nowMillis);
    }

    public long elapsed(long nowMillis) {
        return running ? accumulatedMillis + Math.max(0L, nowMillis - runningSinceMillis) : accumulatedMillis;
    }

    public void pause(long nowMillis) {
        if (running) {
            accumulatedMillis = elapsed(nowMillis);
            running = false;
        }
    }

    public void resume(long nowMillis) {
        if (!running) {
            runningSinceMillis = nowMillis;
            running = true;
        }
    }

    public boolean running() {
        return running;
    }

    /** What is left of {@code sla} at {@code nowMillis}; zero once breached. */
    public long remaining(Duration sla, long nowMillis) {
        return Math.max(0L, sla.toMillis() - elapsed(nowMillis));
    }
}
