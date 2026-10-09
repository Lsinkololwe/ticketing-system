package com.pml.shared.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bound with Spring Boot's own {@link Binder}: the same rules the application applies, no context. */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("The consumer retry budget each service states is the one it runs with")
class RetryBudgetBindingTest {

    private static RetryBudget bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("platform.events.consumer", RetryBudget.class);
    }

    @Test
    @DisplayName("platform.events.consumer binds all four numbers, under the names application.yml uses")
    void bindsTheStatedBudget() {
        assertThat(bind(Map.of(
                "platform.events.consumer.max-attempts", "5",
                "platform.events.consumer.initial-backoff", "250ms",
                "platform.events.consumer.max-backoff", "30s",
                "platform.events.consumer.backoff-multiplier", "3.0")))
                .isEqualTo(new RetryBudget(5, Duration.ofMillis(250), Duration.ofSeconds(30), 3.0));
    }

    @Test
    @DisplayName("unstated, it is the platform default")
    void defaultsToTheDeclaredDefault() {
        assertThat(bind(Map.of())).isEqualTo(RetryBudget.DEFAULT);
    }

    @Test
    @DisplayName("a budget that makes no sense refuses to bind — the service does not start")
    void nonsenseRefusesToBind() {
        assertThatThrownBy(() -> bind(Map.of("platform.events.consumer.max-attempts", "0")))
                .isInstanceOf(BindException.class);
    }
}
