package com.pml.identity.platform;

import java.time.Instant;

/** One probe's answer. {@code detail} is a category such as "timeout", never an address. */
public record ServiceHealth(String name, HealthRules.Status status, long latencyMillis, Instant checkedAt, String detail) {
}
