package com.pml.shared.event;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * Publishes an envelope to RabbitMQ and waits for the broker's confirm.
 *
 * <h2>Why the confirm is awaited</h2>
 * A publish that returns once the bytes are written says nothing about whether the broker took the
 * message. The outbox marks a row sent when this completes, so only a confirmed publish may complete
 * it: an unconfirmed one that later fails would be a message the platform believes it delivered.
 *
 * <h2>Why the wait is on {@code boundedElastic}</h2>
 * The client's confirm wait is blocking. It is contained here, on the scheduler the reactive stack
 * reserves for blocking adapters, rather than on a Netty worker or the drain's scheduler thread.
 *
 * <h2>Persistent, identified, typed</h2>
 * Messages are persistent so a broker restart keeps them, carry the envelope's {@code eventId} as the
 * AMQP message id for tracing, and carry the wire name and ordering key as headers a consumer or an
 * operator can read without parsing the body.
 */
public class AmqpEventTransport implements EventTransport {

    /** The wire name, as a header, for tooling that reads headers rather than routing keys. */
    public static final String EVENT_TYPE_HEADER = "eventType";

    /** The key events of one aggregate are ordered on. */
    public static final String ORDERING_KEY_HEADER = "orderingKey";

    private final RabbitTemplate template;
    private final MessageConverter converter;
    private final Duration confirmTimeout;

    public AmqpEventTransport(RabbitTemplate template, MessageConverter converter, Duration confirmTimeout) {
        this.template = template;
        this.converter = converter;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public Mono<Void> send(String exchange, String routingKey, EventEnvelope envelope, String orderingKey) {
        return Mono.<Void>fromRunnable(() -> {
                    Message message = MessageBuilder.fromMessage(
                                    converter.toMessage(envelope, new org.springframework.amqp.core.MessageProperties()))
                            .setMessageId(envelope.eventId())
                            .setType(envelope.eventType())
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                            .setHeader(EVENT_TYPE_HEADER, envelope.eventType())
                            .setHeader(ORDERING_KEY_HEADER, orderingKey)
                            .build();
                    // invoke() pins one channel for the callback, so the confirm waited for is this publish's.
                    template.invoke(operations -> {
                        operations.send(exchange, routingKey, message);
                        operations.waitForConfirmsOrDie(confirmTimeout.toMillis());
                        return null;
                    });
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(failure -> failure instanceof EventBridge.EventPublicationFailed
                        ? failure
                        : new EventBridge.EventPublicationFailed(exchange, envelope, failure));
    }
}
