package com.pml.shared.event;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where a service stages its envelopes and where the drain sends them.
 *
 * <p>Both are {@code @NotBlank} and neither has a default. The reasoning is the same
 * as for all externalised configuration: a missing value should fail startup naming the property, not start a service whose
 * outbox writes somewhere nothing reads.</p>
 *
 * @param collection the per-service outbox collection — {@code catalog_outbox},
 *                   {@code booking_outbox} or {@code identity_outbox}
 * @param binding    the Spring Cloud Stream output binding the drain publishes to
 */
@Validated
@ConfigurationProperties(prefix = "platform.outbox")
public record OutboxProperties(@NotBlank String collection, @NotBlank String binding,
                               java.time.Duration drainInterval) {

    /** The drain reads this through its @Scheduled expression; the field documents the default. */
    public OutboxProperties {
        if (drainInterval == null) {
            drainInterval = java.time.Duration.ofSeconds(2);
        }
    }
}
