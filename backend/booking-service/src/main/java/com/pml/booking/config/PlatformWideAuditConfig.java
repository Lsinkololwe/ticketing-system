package com.pml.booking.config;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.persistence.BookingCollections;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.security.tenancy.PlatformWideAuditDelivery;
import com.pml.shared.security.tenancy.PlatformWideAuditOutbox;
import com.pml.shared.security.tenancy.TemporalPlatformWideAuditDelivery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.time.Clock;

/**
 * Where this service keeps platform-wide reaches until identity has confirmed them, and which
 * queue carries them there. The sink, the forwarder and the start-up re-drive are assembled from
 * these two by the shared library.
 */
@Configuration
public class PlatformWideAuditConfig {

    @Bean
    PlatformWideAuditOutbox platformWideAuditOutbox(ReactiveMongoTemplate mongo, Clock clock) {
        return new PlatformWideAuditOutbox(mongo, BookingCollections.PLATFORM_WIDE_AUDIT_OUTBOX, clock);
    }

    @Bean
    PlatformWideAuditDelivery platformWideAuditDelivery(TemporalGateway temporal) {
        return new TemporalPlatformWideAuditDelivery(temporal, TaskQueues.AUDIT);
    }
}
