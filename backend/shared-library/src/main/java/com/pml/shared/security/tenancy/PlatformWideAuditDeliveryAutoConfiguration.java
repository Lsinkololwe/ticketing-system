package com.pml.shared.security.tenancy;

import com.pml.shared.config.MongoSchemaValidationConfig;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.infrastructure.temporal.TemporalGatewayAutoConfiguration;
import com.pml.shared.security.InternalServiceClientAutoConfiguration;
import com.pml.shared.security.InternalServiceWebClients;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.time.Clock;
import java.util.Map;

/**
 * In a service that names identity with {@code platform.tenancy.identity-url} and defines a
 * {@link PlatformWideAuditOutbox} and a {@link PlatformWideAuditDelivery}, every platform-wide
 * reach is kept locally first and carried to identity's audit trail until identity confirms it.
 *
 * <p>The service supplies the two things that are its own: the collection the rows live in and the
 * task queue its deliveries run on. Everything else (the sink, the forwarder, the start-up
 * re-drive, the metrics and the collection's validator) is assembled here, so the three services
 * cannot drift apart in how they deliver.
 *
 * <p>Identity does not set the property and defines its own sink that writes the row directly, so
 * this never makes identity call itself. A service that defines its own sink keeps it.
 */
@AutoConfiguration(after = {InternalServiceClientAutoConfiguration.class, TemporalGatewayAutoConfiguration.class},
        before = TenantScopeAutoConfiguration.class)
@ConditionalOnProperty(prefix = "platform.tenancy", name = "identity-url")
@EnableConfigurationProperties(TenancyProperties.class)
public class PlatformWideAuditDeliveryAutoConfiguration {

    private static final String OUTBOX_SCHEMA = "platform-wide-audit-outbox-schema.json";

    @Bean
    @ConditionalOnBean({PlatformWideAuditOutbox.class, PlatformWideAuditDelivery.class})
    @ConditionalOnMissingBean
    public PlatformWideAuditMetrics platformWideAuditMetrics(PlatformWideAuditOutbox outbox, Environment environment,
                                                             ObjectProvider<MeterRegistry> meters,
                                                             ObjectProvider<Clock> clock) {
        return new PlatformWideAuditMetrics(environment.getProperty("spring.application.name", "unknown"), outbox,
                meters.getIfAvailable(), clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({PlatformWideAuditOutbox.class, InternalServiceWebClients.class})
    @ConditionalOnMissingBean
    public PlatformWideAuditForwarder platformWideAuditForwarder(PlatformWideAuditOutbox outbox,
                                                                 InternalServiceWebClients clients,
                                                                 TenancyProperties properties,
                                                                 PlatformWideAuditMetrics metrics) {
        return new PlatformWideAuditForwarder(outbox, clients.to(properties.identityUrl()), metrics);
    }

    @Bean
    @ConditionalOnBean({PlatformWideAuditOutbox.class, PlatformWideAuditDelivery.class})
    @ConditionalOnMissingBean(PlatformWideAuditSink.class)
    public PlatformWideAuditSink platformWideAuditSink(PlatformWideAuditOutbox outbox,
                                                       PlatformWideAuditDelivery delivery,
                                                       PlatformWideAuditMetrics metrics) {
        return new OutboxPlatformWideAuditSink(outbox, delivery, metrics);
    }

    @Bean
    @ConditionalOnBean({PlatformWideAuditOutbox.class, PlatformWideAuditDelivery.class})
    @ConditionalOnMissingBean
    public PlatformWideAuditDrain platformWideAuditDrain(PlatformWideAuditOutbox outbox,
                                                         PlatformWideAuditDelivery delivery,
                                                         PlatformWideAuditMetrics metrics) {
        return new PlatformWideAuditDrain(outbox, delivery, metrics);
    }

    /**
     * Applies the outbox collection's validator at start-up, through the same runner every service
     * uses for its own collections. The schema allows exactly the fields of a record and its
     * delivery state, so a row can never carry anything else.
     */
    @Bean
    @ConditionalOnBean(PlatformWideAuditOutbox.class)
    public MongoSchemaValidationConfig platformWideAuditOutboxSchema(PlatformWideAuditOutbox outbox,
                                                                     ReactiveMongoTemplate mongo,
                                                                     ObjectProvider<MongoSchemaValidationProperties> properties) {
        String collection = outbox.collection();
        return new MongoSchemaValidationConfig(mongo, new DefaultResourceLoader(),
                properties.getIfAvailable(MongoSchemaValidationProperties::new)) {
            @Override
            protected Map<String, String> getSchemaDefinitions() {
                return Map.of(collection, OUTBOX_SCHEMA);
            }
        };
    }
}
