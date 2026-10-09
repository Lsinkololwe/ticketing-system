package com.pml.shared.event;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Makes the consumer {@link RetryBudget} a bean, bound from {@code platform.events.consumer}. */
@AutoConfiguration
@EnableConfigurationProperties(RetryBudget.class)
public class ConsumerRetryAutoConfiguration {
}
