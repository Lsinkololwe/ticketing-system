package com.pml.shared.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
/**
 * Exports dead-letter depth per subscription.
 *
 * <h2>Two meters, because they answer different questions</h2>
 * <ul>
 *   <li>{@code platform.deadletter.produced} — a <b>counter</b>, incremented as this process
 *       gives up on a message. It is monotonic, so it survives a scrape gap and answers
 *       "did anything start failing at 14:05".</li>
 *   <li>{@code platform.deadletter.depth} — a <b>gauge</b> of how many messages are sitting in
 *       the subscription's dead-letter queue right now. That is what the alert fires on: a counter
 *       that rose an hour ago and was drained needs no one woken up, and a depth that is still
 *       above zero does.</li>
 * </ul>
 *
 * <h2>The gauge is fed from the broker, not from this process</h2>
 * Depth is a broker fact. A process that counted only its own dead letters would read zero after
 * a restart while the queue was full, and would miss every message dead-lettered by a sibling
 * replica — the alert would be quietest exactly when the platform was worst. So the gauge takes
 * a supplier and the service wires the Service Bus management client to it. Until one is
 * registered a subscription has no depth gauge at all, which is visibly absent rather than
 * confidently wrong.
 */
public final class DeadLetterDepth {

    public static final String PRODUCED_METRIC = "platform.deadletter.produced";
    public static final String DEPTH_METRIC = "platform.deadletter.depth";

    private final MeterRegistry registry;
    private final Map<String, Counter> produced = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> observed = new ConcurrentHashMap<>();

    public DeadLetterDepth(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Called as a message is dead-lettered, tagged so one bad consumer is distinguishable. */
    public void produced(DeadLetter letter) {
        produced.computeIfAbsent(
                        letter.subscription() + "|" + letter.consumerName() + "|" + letter.reason(),
                        key -> Counter.builder(PRODUCED_METRIC)
                                .description("Messages this process gave up on and dead-lettered")
                                .tags(Tags.of(
                                        "subscription", letter.subscription(),
                                        "consumer", letter.consumerName(),
                                        "reason", letter.reason()))
                                .register(registry))
                .increment();
    }

    /**
     * Records a depth reading for a subscription that is polled rather than scraped.
     *
     * <p>Registers the gauge on first call, so a subscription that has never been polled has no
     * gauge — see the class note on absent versus wrong.</p>
     */
    public void observedDepth(String subscription, long depth) {
        observed.computeIfAbsent(subscription, key -> {
            AtomicLong holder = new AtomicLong();
            registry.gauge(DEPTH_METRIC, Tags.of("subscription", key), holder, AtomicLong::doubleValue);
            return holder;
        }).set(depth);
    }
}
