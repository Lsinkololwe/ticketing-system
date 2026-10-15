package com.pml.shared.event;

import reactor.core.publisher.Mono;

/**
 * The one hop from the outbox to the broker.
 *
 * <p>An interface so the drain and {@link EventBridge} can be exercised without a broker, and so the
 * only class that knows which broker the platform uses is the implementation behind it. The
 * production implementation is {@link AmqpEventTransport}.</p>
 */
@FunctionalInterface
public interface EventTransport {

    /**
     * Hands one envelope to the broker and completes only when the broker has accepted it.
     *
     * <p>Completing early is the failure to avoid: the outbox marks the row sent when this
     * completes, so a send that returned before the broker confirmed it would lose the message
     * on a broker refusal and leave nothing owed.</p>
     *
     * @param exchange   the topic exchange of the publishing service
     * @param routingKey the event's wire name, which the subscriptions bind on
     * @param envelope   the message body
     * @param orderingKey the aggregate id events of one aggregate are ordered on, or the event's own id
     *                    when the event type is commutative
     */
    Mono<Void> send(String exchange, String routingKey, EventEnvelope envelope, String orderingKey);
}
