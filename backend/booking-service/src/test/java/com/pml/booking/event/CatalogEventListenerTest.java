package com.pml.booking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pml.booking.infrastructure.messaging.CatalogConsumerConfig;
import com.pml.booking.infrastructure.messaging.CatalogEventListener;
import com.pml.booking.workflow.finance.EventFinanceProcess;
import com.pml.shared.event.AmqpEventTransport;
import com.pml.shared.event.ConsumerDispatch;
import com.pml.shared.event.ConsumerGuard;
import com.pml.shared.event.DeadLetter;
import com.pml.shared.event.DeadLetterDepth;
import com.pml.shared.event.EventBridge;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.event.EventTopology;
import com.pml.shared.event.EventTopologyAutoConfiguration;
import com.pml.shared.event.EventTransportAutoConfiguration;
import com.pml.shared.event.EventType;
import com.pml.shared.event.RetryBudget;
import com.pml.shared.testing.RabbitNode;
import com.pml.shared.testing.RedisNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Booking's catalog subscription on a real RabbitMQ and a real Redis: the listener bound by the
 * platform's container factory, the guard, the retry budget and the dead-letter route, driven by
 * envelopes published through the same transport the outbox uses.
 *
 * <h2>What this proves that the dispatch test cannot</h2>
 * {@code CatalogConsumerIdempotencyTest} proves the guard against Redis with the delivery called
 * directly. Here the delivery comes from the broker: the acknowledgement is real, a redelivery is a
 * second message, and a message the consumer gives up on is rejected into the dead-letter queue
 * rather than lost or retried forever.
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003 · booking's catalog listener, on a real broker: handled once, acknowledged, dead-lettered when it gives up")
class CatalogEventListenerTest {

    private static final String QUEUE = "catalog-events.booking-sub";

    private static AnnotationConfigApplicationContext context;
    private static CachingConnectionFactory connections;
    private static RabbitTemplate template;
    private static EventBridge bridge;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;

    /** What booking's finance process was asked to do, in order. */
    private static final List<String> handled = new CopyOnWriteArrayList<>();
    private static final List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();
    private static volatile boolean failTheWork;

    @BeforeAll
    static void startTheListener() {
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);

        connections = new CachingConnectionFactory(RabbitNode.host(), RabbitNode.port());
        connections.setUsername(RabbitNode.USERNAME);
        connections.setPassword(RabbitNode.PASSWORD);
        connections.setVirtualHost(RabbitNode.newVirtualHost());
        connections.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);

        context = new AnnotationConfigApplicationContext();
        context.registerBean("connectionFactory", ConnectionFactory.class, () -> connections);
        context.register(ListenerConfig.class);
        context.refresh();

        Jackson2JsonMessageConverter converter = context.getBean(Jackson2JsonMessageConverter.class);
        template = new RabbitTemplate(connections);
        template.setMessageConverter(converter);
        bridge = new EventBridge(new AmqpEventTransport(template, converter, Duration.ofSeconds(10)),
                EventTopology.CATALOG_EVENTS);
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
        if (redisFactory != null) {
            redisFactory.destroy();
        }
    }

    @BeforeEach
    void reset() {
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
        handled.clear();
        deadLetters.clear();
        failTheWork = false;
    }

    @Test
    @DisplayName("a published event is handled once and the message is acknowledged")
    void handledAndAcknowledged() {
        EventEnvelope envelope = published("ev-handled");

        bridge.publish(envelope).block(Duration.ofSeconds(10));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(handled).containsExactly(envelope.eventId()));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(depth(QUEUE)).as("acknowledged, so nothing is left on the queue").isZero());
        assertThat(depth(QUEUE + ".dlq")).isZero();
    }

    @Test
    @DisplayName("the same message delivered twice does the work once")
    void redeliveryIsRecognised() {
        EventEnvelope envelope = published("ev-twice");

        bridge.publish(envelope).block(Duration.ofSeconds(10));
        bridge.publish(envelope).block(Duration.ofSeconds(10));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(depth(QUEUE)).isZero());
        assertThat(handled).as("the second delivery is a duplicate, acknowledged and not run").hasSize(1);
        assertThat(deadLetters).isEmpty();
    }

    @Test
    @DisplayName("work that keeps failing is dead-lettered to the queue's DLQ, not retried forever")
    void failingWorkIsDeadLettered() {
        failTheWork = true;
        EventEnvelope envelope = published("ev-poison");

        bridge.publish(envelope).block(Duration.ofSeconds(10));

        Message dead = template.receive(QUEUE + ".dlq", 30_000);
        assertThat(dead).as("the give-up reaches the dead-letter queue for an operator").isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(envelope.eventId());
        assertThat(deadLetters).as("DeadLetterDepth counted the give-up").hasSize(1);
        assertThat(depth(QUEUE)).as("and the live queue is not blocked behind it").isZero();
    }

    // ------------------------------------------------------------------ helpers

    private static int depth(String queue) {
        return new RabbitAdmin(connections).getQueueInfo(queue).getMessageCount();
    }

    private static EventEnvelope published(String eventId) {
        return EventEnvelopes.of(EventType.CATALOG_EVENT_PUBLISHED, Instant.parse("2026-10-14T10:00:00Z"), eventId,
                Map.of("eventId", eventId, "organizationId", "org-1", "startsAt", "2026-10-30T18:00:00Z"));
    }

    @Configuration
    @EnableRabbit
    static class ListenerConfig {

        @Bean
        RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }

        @Bean
        Declarables platformEventTopology() {
            return new EventTopologyAutoConfiguration().platformEventTopology();
        }

        @Bean
        Jackson2JsonMessageConverter eventConverter() {
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
            converter.setTypePrecedence(
                    org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
            return converter;
        }

        @Bean(name = EventTransportAutoConfiguration.ORDERED_LISTENER_FACTORY)
        SimpleRabbitListenerContainerFactory orderedFactory(ConnectionFactory connectionFactory,
                                                            MessageConverter converter) {
            return new EventTransportAutoConfiguration()
                    .orderedEventListenerContainerFactory(connectionFactory, converter);
        }

        @Bean
        EventFinanceProcess eventFinance() {
            EventFinanceProcess process = org.mockito.Mockito.mock(EventFinanceProcess.class);
            org.mockito.Mockito.when(process.deliver(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
                EventEnvelope envelope = call.getArgument(0);
                if (failTheWork) {
                    return Mono.<Void>error(new IllegalStateException("the escrow store is unreachable"));
                }
                handled.add(envelope.eventId());
                return Mono.<Void>empty();
            });
            return process;
        }

        @Bean
        ConsumerDispatch dispatch() {
            ConsumerGuard guard = new ConsumerGuard(redis,
                    envelope -> Mono.just(handled.contains(envelope.eventId())),
                    CatalogConsumerConfig.CONSUMER);
            // Two attempts a few hundred milliseconds apart: a failing consumer reaches its dead letter
            // while the test is still waiting for it.
            RetryBudget budget = new RetryBudget(2, Duration.ofMillis(100), Duration.ofMillis(300), 2.0);
            return new ConsumerDispatch(guard, budget,
                    letter -> {
                        deadLetters.add(letter);
                        return Mono.empty();
                    },
                    new DeadLetterDepth(new SimpleMeterRegistry()),
                    CatalogConsumerConfig.CONSUMER, CatalogConsumerConfig.SUBSCRIPTION);
        }

        @Bean
        CatalogEventListener listener(EventFinanceProcess eventFinance, ConsumerDispatch dispatch) {
            return new CatalogEventListener(eventFinance, dispatch);
        }
    }
}
