package com.pml.shared.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * The broker connection the platform's events use: the transport the outbox publishes through and
 * the listener containers that consumers are bound to.
 *
 * <h2>Two listener shapes, chosen by the subscription</h2>
 * A subscription of commutative events is consumed by several threads with a prefetch window, because
 * its messages have no order to keep. A subscription that carries an ordered event is consumed by one
 * thread, one message at a time, because a window of unacknowledged messages is exactly how a later
 * message gets handled while an earlier one is still being retried.
 *
 * <h2>A rejected message is dead-lettered, never requeued</h2>
 * {@code defaultRequeueRejected=false}: a message the consumer gave up on goes to the dead-letter
 * queue with its reason, where an operator can replay it. Requeueing it would hand the same poison
 * message straight back to the same consumer, forever.
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(ConnectionFactory.class)
public class EventTransportAutoConfiguration {

    /** Name of the container factory for a subscription that carries ordered events. */
    public static final String ORDERED_LISTENER_FACTORY = "orderedEventListenerContainerFactory";

    /** Name of the container factory for a subscription of commutative events. */
    public static final String CONCURRENT_LISTENER_FACTORY = "eventListenerContainerFactory";

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter eventMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        // The listener's parameter type decides what the body is read as, not a type header a
        // publisher wrote, so a producer on another build cannot name a class the consumer will load.
        converter.setTypePrecedence(org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean(EventTransport.class)
    public EventTransport eventTransport(RabbitTemplate template, MessageConverter converter,
                                         org.springframework.core.env.Environment environment) {
        Duration timeout = environment.getProperty("platform.events.confirm-timeout", Duration.class,
                Duration.ofSeconds(10));
        return new AmqpEventTransport(template, converter, timeout);
    }

    @Bean(name = CONCURRENT_LISTENER_FACTORY)
    @ConditionalOnMissingBean(name = CONCURRENT_LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory eventListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                             MessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean(name = ORDERED_LISTENER_FACTORY)
    @ConditionalOnMissingBean(name = ORDERED_LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory orderedEventListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
