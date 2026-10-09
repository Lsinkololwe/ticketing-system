package com.pml.gateway.config;

import com.pml.gateway.controller.FallbackController;
import com.pml.gateway.controller.HealthController;
import com.pml.shared.config.PlatformClockAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway's two response timestamps come from the injected {@link Clock}.
 *
 * <h2>Why the wiring is tested</h2>
 * The controllers take the clock as a constructor argument. A missing bean is not a compile
 * error — it is a context that fails to refresh at startup, in the one module that sits in front of
 * everything else.
 *
 * <p>The {@link Clock} arrives from `shared-library` via
 * {@link PlatformClockAutoConfiguration}, registered in
 * `AutoConfiguration.imports`. That is two artifacts and an imports file away from these
 * controllers, so "it will be there" is an assumption with three places to break.</p>
 *
 * <h2>ApplicationContextRunner rather than @SpringBootTest</h2>
 * The full gateway context wants Redis for the rate limiter and a route set pointing at services
 * that are not running. None of that bears on whether a clock is injectable, and a test that needs
 * infrastructure to assert a wiring fact gets skipped in CI and stops meaning anything.
 */
@Tag("L3")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001 R3 · the gateway's timestamps come from the injected Clock")
class GatewayClockWiringTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformClockAutoConfiguration.class))
            .withUserConfiguration(GatewayControllers.class)
            .withPropertyValues("spring.application.name=api-gateway");

    @Test
    @DisplayName("both controllers construct against the platform clock")
    void bothControllersConstruct() {
        contexts.run(context -> {
            assertThat(context)
                    .as("a missing Clock bean fails at startup, not at compile time — which is why "
                            + "this assertion exists at all")
                    .hasNotFailed();
            assertThat(context).hasSingleBean(HealthController.class);
            assertThat(context).hasSingleBean(FallbackController.class);
            assertThat(context).hasSingleBean(Clock.class);
        });
    }

    @Test
    @DisplayName("the platform clock is UTC, so the gateway's stamp matches the services'")
    void theClockIsUtc() {
        contexts.run(context -> assertThat(context.getBean(Clock.class).getZone())
                .as("the timestamp these controllers emit is read beside service logs during an "
                        + "outage; a gateway on host-local time and services on UTC disagree by "
                        + "the host's offset with nothing in either to say which is which")
                .isEqualTo(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("a test may override the clock, and the controllers then see frozen time")
    void aFrozenClockWins() {
        Instant frozen = Instant.parse("2026-09-02T00:00:00Z");

        contexts.withBean(Clock.class, () -> Clock.fixed(frozen, ZoneOffset.UTC))
                .run(context -> {
                    // The point of injecting a Clock is that a test can replace it. If
                    // @ConditionalOnMissingBean were dropped from the auto-configuration this
                    // still constructs, still passes a naive smoke test, and quietly reverts every
                    // frozen-time test in the corpus to wall time.
                    assertThat(context.getBean(Clock.class).instant())
                            .as("an overridden clock must win over the auto-configured one")
                            .isEqualTo(frozen);
                    assertThat(context).hasSingleBean(Clock.class);
                });
    }

    @Configuration
    static class GatewayControllers {

        @Bean
        HealthController healthController(Clock clock) {
            return new HealthController(clock);
        }

        @Bean
        FallbackController fallbackController(Clock clock) {
            return new FallbackController(clock);
        }
    }
}
