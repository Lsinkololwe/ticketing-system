package com.pml.booking.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pml.shared.event.EventEnvelope;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * DEV ONLY (local e2e stack). The Spring Cloud Azure binder builds its consumer from
 * {@code <namespace>.servicebus.windows.net} and ignores the connection string, so it cannot reach the local
 * Service Bus emulator. When {@code e2e.servicebus.connection-string} is set (env
 * {@code E2E_SERVICEBUS_CONNECTION_STRING}) this class reads {@code catalog-events/booking-sub} straight through the
 * Service Bus SDK and hands each envelope to the same {@code catalogEventConsumer} the binder would call. The e2e start
 * script also blanks the binder's function definition so the two never run together. Without the property, nothing here loads.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "e2e.servicebus.connection-string")
class DevEmulatorCatalogConsumer implements InitializingBean, DisposableBean {

    private final Consumer<Message<EventEnvelope>> consumer;
    private final String connectionString;
    private ServiceBusProcessorClient client;

    DevEmulatorCatalogConsumer(@Qualifier("catalogEventConsumer") Consumer<Message<EventEnvelope>> consumer,
                               @Value("${e2e.servicebus.connection-string}") String connectionString) {
        this.consumer = consumer;
        this.connectionString = connectionString;
    }

    @Override
    public void afterPropertiesSet() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        client = new ServiceBusClientBuilder().connectionString(connectionString).sessionProcessor()
                .topicName("catalog-events").subscriptionName("booking-sub")
                .maxConcurrentSessions(5)
                .processMessage(ctx -> {
                    try {
                        EventEnvelope envelope = mapper.readValue(ctx.getMessage().getBody().toBytes(), EventEnvelope.class);
                        consumer.accept(MessageBuilder.withPayload(envelope).build());
                    } catch (Exception e) {
                        log.error("e2e emulator consumer failed: {}", e.getMessage());
                        throw new IllegalStateException(e);
                    }
                })
                .processError(ctx -> log.warn("e2e emulator consumer error: {}", ctx.getException().getMessage()))
                .buildProcessorClient();
        client.start();
        log.info("e2e: catalog-events/booking-sub consumed from the Service Bus emulator");
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }
}
