package com.pml.shared.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.Clock;
import java.util.Optional;

/**
 * Points Spring Data's auditing at the platform {@link Clock}.
 *
 * <h2>Why this is not optional</h2>
 * {@code @CreatedDate} and {@code @LastModifiedDate} are populated by Spring Data, not by
 * application code — so however carefully ET-PLT-001 R3 removes inline {@code now()} calls
 * from services, audit fields would still be stamped from the wall clock unless the auditing
 * infrastructure is pointed at the same bean.
 *
 * <p>That gap is easy to miss and expensive to find: a frozen-clock test would drive a
 * reservation across its ten-minute boundary correctly, and the document it wrote would carry
 * a {@code createdAt} from real time. ET-PLT-002 T6's frozen-clock audit-field test is what
 * catches it, and this class is what makes that test pass.
 *
 * <h2>Conditional on Spring Data being present</h2>
 * {@code spring-boot-starter-data-mongodb-reactive} is {@code provided} in this library, and
 * {@code api-gateway} does not use it. A separate auto-configuration guarded by
 * {@link ConditionalOnClass} keeps the {@link Clock} bean itself available everywhere —
 * putting both beans in one class would mean the gateway needs Spring Data on its classpath
 * to get a clock.
 */
@AutoConfiguration
@AutoConfigureAfter(PlatformClockAutoConfiguration.class)
@ConditionalOnClass(DateTimeProvider.class)
public class PlatformAuditingAutoConfiguration {

    /**
     * Named {@code auditingDateTimeProvider} because that is the name
     * {@code @EnableReactiveMongoAuditing(dateTimeProviderRef = "auditingDateTimeProvider")}
     * refers to. A provider bean nothing references is the failure this naming avoids: it
     * exists, it is correct, and Spring Data goes on using the wall clock beside it.
     */
    @Bean
    @ConditionalOnMissingBean(name = "auditingDateTimeProvider")
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
