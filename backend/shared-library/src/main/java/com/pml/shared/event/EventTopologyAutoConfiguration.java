package com.pml.shared.event;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Declares the platform's {@link EventTopology} on the broker.
 *
 * <p>Every service declares all of it. A declaration is idempotent, so the first service to connect
 * creates the exchanges and queues and the rest find them in place; what is never acceptable is a
 * message published before its subscription's queue exists, because the exchange would accept it and
 * drop it. {@code platform.events.declare-topology=false} switches this off for a deployment whose
 * broker topology is provisioned by other means.</p>
 */
@AutoConfiguration(after = org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitAdmin.class)
@ConditionalOnProperty(prefix = "platform.events", name = "declare-topology", havingValue = "true", matchIfMissing = true)
public class EventTopologyAutoConfiguration {

    @Bean
    @ConditionalOnBean(org.springframework.amqp.rabbit.connection.ConnectionFactory.class)
    @ConditionalOnMissingBean(name = "platformEventTopology")
    public Declarables platformEventTopology() {
        List<Declarable> declared = new ArrayList<>();

        for (String exchange : EventTopology.EXCHANGES) {
            declared.add(new TopicExchange(exchange, true, false));
            declared.add(new DirectExchange(EventTopology.deadLetterExchange(exchange), true, false));
        }

        for (EventTopology.Subscription subscription : EventTopology.SUBSCRIPTIONS) {
            TopicExchange exchange = new TopicExchange(subscription.exchange(), true, false);
            DirectExchange deadLetters = new DirectExchange(subscription.deadLetterExchange(), true, false);

            QueueBuilder queue = QueueBuilder.durable(subscription.queue())
                    .quorum()
                    .withArgument("x-dead-letter-exchange", subscription.deadLetterExchange())
                    .withArgument("x-dead-letter-routing-key", subscription.queue())
                    .withArgument("x-message-ttl", EventTopology.MESSAGE_TTL_MILLIS);
            if (subscription.ordered()) {
                queue.withArgument("x-single-active-consumer", true);
            }
            Queue main = queue.build();
            Queue deadLetterQueue = QueueBuilder.durable(subscription.deadLetterQueue())
                    .withArgument("x-max-length", EventTopology.DEAD_LETTER_LIMIT)
                    .withArgument("x-overflow", "drop-head")
                    .build();

            declared.add(main);
            declared.add(deadLetterQueue);
            for (String wireName : subscription.wireNames()) {
                declared.add(BindingBuilder.bind(main).to(exchange).with(wireName));
            }
            Binding toDeadLetters = BindingBuilder.bind(deadLetterQueue).to(deadLetters).with(subscription.queue());
            declared.add(toDeadLetters);
        }
        return new Declarables(declared);
    }
}
