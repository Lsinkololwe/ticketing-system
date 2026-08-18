package com.pml.shared.config;

import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ET-PLT-001 R3 — the platform clock, and the auditing that must follow it.
 *
 * <p>{@link ApplicationContextRunner} is Spring Boot's own way to test an auto-configuration:
 * it builds the context exactly as an application would, applies the real conditions, and
 * needs no container and no running service. That matters here because these beans have to be
 * correct in five services, and asserting the contribution once beats asserting it five times.
 */
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R3 · one clock, injected, and auditing follows it")
class PlatformClockAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PlatformClockAutoConfiguration.class,
                    PlatformAuditingAutoConfiguration.class));

    @Test
    @DisplayName("contributes exactly one Clock, and it is UTC")
    void contributesOneUtcClock() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class).getZone())
                    .as("""
                        systemDefaultZone() would make an instant mean one thing on a laptop and \
                        another in the cluster; storage and comparison never leave UTC""")
                    .isEqualTo(ZoneOffset.UTC);
        });
    }

    @Test
    @DisplayName("a test may replace it — which is the entire reason it is a bean")
    void isOverridable() {
        Instant frozen = Instant.parse("2026-03-01T18:00:00Z");

        runner.withUserConfiguration(FrozenClockConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context.getBean(Clock.class).instant())
                    .as("@ConditionalOnMissingBean must yield to the application's own definition")
                    .isEqualTo(frozen);
        });
    }

    @Test
    @DisplayName("auditing reads the platform clock, so @CreatedDate freezes when the clock does")
    void auditingFollowsTheClock() {
        Instant frozen = Instant.parse("2026-03-01T18:00:00Z");

        runner.withUserConfiguration(FrozenClockConfiguration.class).run(context -> {
            DateTimeProvider provider = context.getBean(DateTimeProvider.class);

            assertThat(provider.getNow()).isPresent();
            assertThat(Instant.from(provider.getNow().orElseThrow()))
                    .as("""
                        @CreatedDate is stamped by Spring Data, not by application code — so \
                        removing inline now() from services is not enough on its own. If this \
                        reads the wall clock, a frozen-clock test writes a document with a real \
                        timestamp and nothing fails.""")
                    .isEqualTo(frozen);
        });
    }

    @Test
    @DisplayName("the provider bean is named auditingDateTimeProvider, which is what @EnableReactiveMongoAuditing refers to")
    void providerIsNamedForItsReference() {
        runner.run(context -> assertThat(context).hasBean("auditingDateTimeProvider"));
    }

    @Test
    @DisplayName("advancing the clock advances auditing with it")
    void auditingTracksTheClock() {
        runner.withUserConfiguration(FrozenClockConfiguration.class).run(context -> {
            TestClock clock = (TestClock) context.getBean(Clock.class);
            DateTimeProvider provider = context.getBean(DateTimeProvider.class);

            Instant before = Instant.from(provider.getNow().orElseThrow());
            clock.advance(java.time.Duration.ofMinutes(10));
            Instant after = Instant.from(provider.getNow().orElseThrow());

            assertThat(after).isEqualTo(before.plusSeconds(600));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class FrozenClockConfiguration {
        @Bean
        Clock clock() {
            return TestClock.frozenAt("2026-03-01T18:00:00Z");
        }
    }
}
