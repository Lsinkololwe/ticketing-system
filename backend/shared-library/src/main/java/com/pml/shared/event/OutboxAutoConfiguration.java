package com.pml.shared.event;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Wires the transactional outbox into a service.
 *
 * <h2>Why this is auto-configuration rather than three copies</h2>
 * Every service needs the same three beans differing only in a collection name and a binding.
 * Written per service, the third copy is where the interval drifts, or the reclaim is left out,
 * and nothing says so — a drain that never reclaims looks identical to one with nothing to
 * reclaim.
 *
 * <h2>Opt-in by property, not by classpath</h2>
 * A service declares its own collection and binding:
 *
 * <pre>{@code
 * platform:
 *   outbox:
 *     collection: identity_outbox
 *     binding: identity-events-out-0
 * }</pre>
 *
 * <p>No default collection is supplied, deliberately. Guessing one from the application name would
 * quietly give a misconfigured service a working outbox pointed at the wrong collection — messages
 * staged where no drain looks, which is the failure the outbox exists to prevent, reached by a
 * different road.</p>
 */
@AutoConfiguration
@ConditionalOnClass({ReactiveMongoTemplate.class, StreamBridge.class})
@ConditionalOnProperty(prefix = "platform.outbox", name = {"collection", "binding"})
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Outbox platformOutbox(ReactiveMongoTemplate mongoTemplate,
                                 OutboxProperties properties,
                                 Clock clock) {
        return new Outbox(mongoTemplate, properties.collection(), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventBridge platformEventBridge(StreamBridge streamBridge, OutboxProperties properties) {
        return new EventBridge(streamBridge, properties.binding());
    }

    /**
     * The drain only exists where an {@link Outbox} does.
     *
     * <p>{@code @ConditionalOnBean} rather than unconditional: a scheduled component polling a
     * collection that no service stages into is a query every two seconds, forever, for nothing —
     * and it would make {@code pendingCount} a metric that is always zero for the wrong reason.</p>
     */
    @Bean
    @ConditionalOnBean({Outbox.class, EventBridge.class})
    @ConditionalOnMissingBean
    public OutboxDrain platformOutboxDrain(Outbox outbox, EventBridge bridge) {
        return new OutboxDrain(outbox, bridge);
    }
}
