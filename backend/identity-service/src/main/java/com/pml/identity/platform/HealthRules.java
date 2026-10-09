package com.pml.identity.platform;

import java.util.Set;

/** Pure interpretation of probe results; the probe's I/O is {@link ServiceHealthService}. */
public final class HealthRules {

    public enum Status { UP, DOWN }

    private HealthRules() {
    }

    /** An actuator health response is UP only when it says so; any other answer, including no answer, is DOWN. */
    public static Status ofActuator(int httpStatus, String body) {
        return httpStatus == 200 && body != null && body.replace(" ", "").contains("\"status\":\"UP\"")
                ? Status.UP : Status.DOWN;
    }

    /** A short, safe description of a failure: a category, never a URL, host or stack trace. */
    public static String category(Throwable failure) {
        if (failure instanceof java.util.concurrent.TimeoutException) {
            return "timeout";
        }
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof java.net.ConnectException || cause instanceof java.nio.channels.ClosedChannelException) {
                return "connection refused";
            }
            if (cause instanceof java.net.UnknownHostException) {
                return "unreachable";
            }
            cause = cause.getCause();
        }
        return "unavailable";
    }

    /** Alert key for a service being down. */
    public static String alertKey(String service) {
        return "service-down:" + service;
    }

    public static final Set<String> SELF_CHECKS = Set.of("mongodb", "redis", "keycloak", "temporal");
}
