package com.pml.shared.security.tenancy;

import com.pml.shared.config.MongoSchemaValidationConfig;
import com.pml.shared.security.InternalServiceWebClients;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * What a service gets by naming identity and keeping an outbox: the local-first sink and everything
 * that serves it, assembled in one place so the three services cannot differ.
 */
@Tag("L3")
@Tag("ET-PLT-007")
@Tag("ET-PLT-009")
@DisplayName("a service that names identity and keeps an outbox gets the local-first sink; identity and others keep theirs")
class PlatformWideAuditDeliveryAutoConfigurationTest {

    private final ApplicationContextRunner unnamed = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformWideAuditDeliveryAutoConfiguration.class,
                    TenantScopeAutoConfiguration.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ReactiveMongoTemplate.class, () -> mock(ReactiveMongoTemplate.class))
            .withBean(InternalServiceWebClients.class, () -> InternalServiceWebClients.unauthenticated(WebClient.builder()))
            .withPropertyValues("spring.application.name=booking-service");

    private final ApplicationContextRunner runner = unnamed
            .withPropertyValues("platform.tenancy.identity-url=http://identity:8083");

    private ApplicationContextRunner withOutbox(ApplicationContextRunner base) {
        return base
                .withBean(PlatformWideAuditOutbox.class, () -> new PlatformWideAuditOutbox(
                        mock(ReactiveMongoTemplate.class), "booking_platform_wide_audit_outbox", Clock.systemUTC()))
                .withBean(PlatformWideAuditDelivery.class, () -> recordId -> { });
    }

    @Test
    @DisplayName("the sink, forwarder, re-drive, metrics and the collection's validator are assembled, and the access class uses the sink")
    void assembled() {
        withOutbox(runner).run(context -> {
            assertThat(context).hasSingleBean(PlatformWideAuditSink.class);
            assertThat(context.getBean(PlatformWideAuditSink.class)).isInstanceOf(OutboxPlatformWideAuditSink.class);
            assertThat(context).hasSingleBean(PlatformWideAuditForwarder.class);
            assertThat(context).hasSingleBean(PlatformWideAuditDrain.class);
            assertThat(context).hasSingleBean(PlatformWideAuditMetrics.class);
            assertThat(context).hasSingleBean(MongoSchemaValidationConfig.class);
            assertThat(context).hasSingleBean(PlatformWideAccess.class);
        });
    }

    @Test
    @DisplayName("without an outbox the service is left on the log-only default")
    void noOutboxNoDelivery() {
        runner.run(context -> {
            assertThat(context.getBean(PlatformWideAuditSink.class)).isInstanceOf(LogOnlyPlatformWideAuditSink.class);
            assertThat(context).doesNotHaveBean(PlatformWideAuditForwarder.class)
                    .doesNotHaveBean(PlatformWideAuditDrain.class);
        });
    }

    @Test
    @DisplayName("a service that does not name identity (identity itself) is never wired to call it")
    void identityIsNotWiredToItself() {
        withOutbox(unnamed)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PlatformWideAuditForwarder.class)
                            .doesNotHaveBean(PlatformWideAuditDrain.class)
                            .doesNotHaveBean(OutboxPlatformWideAuditSink.class);
                });
    }

    @Test
    @DisplayName("a service that defines its own sink keeps it")
    void ownSinkWins() {
        PlatformWideAuditSink own = record -> Mono.empty();
        withOutbox(runner).withBean("ownSink", PlatformWideAuditSink.class, () -> own)
                .run(context -> assertThat(context.getBean(PlatformWideAuditSink.class)).isSameAs(own));
    }
}
