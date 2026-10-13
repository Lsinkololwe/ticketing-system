package com.pml.shared.security.tenancy;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * How hard delivery tries. Identity may be down for minutes or for a day, and a platform-wide
 * reach must still arrive, so the retry has no attempt limit; the interval doubles from five seconds
 * to a fifteen-minute ceiling, which keeps a long outage from becoming a storm of attempts while
 * still delivering within fifteen minutes of identity coming back.
 */
public final class PlatformWideAuditDeliveryRules {

    public static final Duration INITIAL_INTERVAL = Duration.ofSeconds(5);
    public static final Duration MAXIMUM_INTERVAL = Duration.ofMinutes(15);
    public static final double BACKOFF = 2.0;

    /** One attempt: a bit more than the forwarder's own bound on identity's answer. */
    public static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(30);

    /** Marks the shape of this workflow so a later step can be added without breaking running executions. */
    public static final String STEPS_VERSION = "platform-wide-audit-delivery-steps";

    private static final Pattern RECORD_ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private PlatformWideAuditDeliveryRules() {
    }

    public static ActivityOptions activityOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(ATTEMPT_TIMEOUT)
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(INITIAL_INTERVAL)
                        .setBackoffCoefficient(BACKOFF)
                        .setMaximumInterval(MAXIMUM_INTERVAL)
                        // zero means no limit
                        .setMaximumAttempts(0)
                        .build())
                .build();
    }

    /**
     * {@code audit-delivery/{recordId}}. Only a random record id is accepted, so an actor, a
     * reason or any text can never become part of a workflow id.
     */
    public static String workflowId(String recordId) {
        if (recordId == null || !RECORD_ID.matcher(recordId).matches()) {
            throw new IllegalArgumentException("an audit delivery is addressed by its record id");
        }
        return "audit-delivery/" + recordId;
    }
}
