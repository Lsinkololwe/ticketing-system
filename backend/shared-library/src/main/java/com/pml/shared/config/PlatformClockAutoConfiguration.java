package com.pml.shared.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * The platform's single source of "now".
 *
 * <h2>No inline clock reads</h2>
 * Every timestamp in the platform comes from this bean. No production code calls
 * {@code Instant.now()}, {@code LocalDateTime.now()} or {@code System.currentTimeMillis()}
 * directly, because a class that reads the wall clock cannot be tested against a boundary —
 * and this platform is defined by its boundaries. A reservation is live at 9:59 and expired
 * at 10:01; an OTP is valid at 4:59 and dead at 5:01; a sales window opens at
 * {@code salesStartAt} and not a second before. Each of those is specified on <em>both</em>
 * sides, and none of them is testable against a clock the test cannot move.
 *
 * <p>{@link com.pml.shared.testing.TestClock} in the test harness is what replaces this
 * in a test, which is the whole reason the bean exists rather than a static call.
 *
 * <h2>Why an auto-configuration rather than a class per service</h2>
 * The alternative is a {@code PlatformConfig} in each service. Five copies of
 * {@code Clock.systemUTC()} is five things to keep in step, and the failure mode is silent:
 * one service on a different clock source produces timestamps that look fine and order
 * wrongly against another service's. Spring Boot's native mechanism for "a library
 * contributes a bean unless the application says otherwise" is an
 * {@link AutoConfiguration}, and {@code shared-library} already publishes five of them.
 * This is the sixth.
 *
 * <h2>UTC, always</h2>
 * {@code systemUTC()}, never {@code systemDefaultZone()}. The zone a JVM happens to be
 * started in is not a business fact, and an instant that means one thing on a developer's
 * laptop and another in the cluster is the kind of defect that surfaces as a reservation
 * expiring two hours early. Presentation converts to Africa/Lusaka; storage and comparison
 * never leave UTC.
 */
@AutoConfiguration
public class PlatformClockAutoConfiguration {

    /**
     * {@code @ConditionalOnMissingBean} so a test — or a service with a genuine reason —
     * can supply its own without editing this class.
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock platformClock() {
        return Clock.systemUTC();
    }
}
