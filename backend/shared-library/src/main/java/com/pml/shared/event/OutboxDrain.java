package com.pml.shared.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Turns the outbox.
 *
 * <h2>The half that publishes</h2>
 * {@link Outbox} stages an envelope inside the business transaction and knows how to claim,
 * publish and mark rows. {@link EventBridge} knows how to hand one envelope to the bus. This is
 * the caller that connects them. Without it, a publisher sends directly and carries the window
 * between commit and publish that the outbox exists to close:
 *
 * <pre>{@code
 * boolean sent = streamBridge.send("userOutput-out-0", event);
 * if (!sent) log.warn("Failed to publish ...");     // the write is durable, the message is gone
 * }</pre>
 *
 * <p>Nothing retries that, because nothing recorded that it should. This class is what makes the
 * row a promise rather than a note.</p>
 *
 * <h2>Why a poll rather than a change stream</h2>
 * A tailable cursor on the outbox would publish sooner, and would also be a second thing that can
 * be down. A poll re-reads the truth every interval: if the drain was dead for an hour, the first
 * pass after it restarts finds every row waiting, in {@code stagedAt} order. Nothing needs to have
 * been listening at the moment of the write, which is the property that makes the outbox
 * restartable rather than merely asynchronous.
 *
 * <h2>Bounded, and safe to overlap</h2>
 * Each pass takes at most {@link #BATCH} rows. {@code fixedDelay} measures from the completion of
 * the <em>method</em>, which returns immediately here, so two passes can overlap — and that is
 * safe rather than merely tolerable: {@link Outbox} claims each row with a conditional
 * {@code findAndModify} that matches only while it is {@code PENDING}, so exactly one drain wins
 * any given row. Overlapping passes contend; they do not duplicate.
 */
@Slf4j
public class OutboxDrain {

    /**
     * How many rows one pass will publish.
     *
     * <p>Bounded so an accumulated backlog does not produce a single pass that runs for minutes
     * holding claims. The backlog drains over several passes instead, and {@code pendingCount}
     * stays a signal that moves rather than a number that only changes when a long pass finishes.
     * </p>
     */
    static final int BATCH = 100;

    /**
     * How long a claim may sit before another drain may take the row.
     *
     * <p>Longer than any plausible publish, because reclaiming a row that is still being published
     * is how one message becomes two. The bus is at-least-once regardless and every consumer
     * deduplicates on {@code eventId} — but a duplicate the platform caused itself, on a timer it
     * chose, is a different thing from one the bus caused.</p>
     */
    static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);

    private final Outbox outbox;
    private final EventBridge bridge;

    public OutboxDrain(Outbox outbox, EventBridge bridge) {
        this.outbox = outbox;
        this.bridge = bridge;
    }

    @Scheduled(fixedDelayString = "${platform.outbox.drain-interval:PT2S}")
    public void drain() {
        // Subscribed, not blocked. A block() here runs on the scheduler's thread and, in a WebFlux
        // service, eventually on a Netty worker — where one stalled worker stalls every request it
        // is carrying. Nothing is waiting on this method's return.
        publishPending()
                // subscribe(onNext, onError): a dropped error here is a drain that silently stops
                // publishing, which is this class's own failure mode wearing the disguise of a
                // healthy scheduler.
                .subscribe(published -> {
                    if (published > 0) {
                        log.debug("Outbox drain published {} envelope(s)", published);
                    }
                }, failed -> log.error("Outbox drain pass failed, retrying next interval: {}",
                        failed.getMessage()));
    }

    /**
     * Reclaims abandoned claims, then publishes a batch.
     *
     * <p>Reclaim first, deliberately. A row stuck in {@code PUBLISHING} from a process that died
     * is invisible to the claim filter, which matches only {@code PENDING} — so without this it is
     * not lost and never sent either, and its status says somebody is dealing with it. That is the
     * harder failure to notice, which is why it runs on every pass rather than on a slower timer of
     * its own.</p>
     *
     * @return how many envelopes reached the bus
     */
    Mono<Long> publishPending() {
        return outbox.reclaimStale(CLAIM_TIMEOUT)
                .doOnNext(reclaimed -> {
                    if (reclaimed > 0) {
                        // Worth an INFO: a nonzero count means a drain died mid-publish, which is
                        // not visible anywhere else.
                        log.info("Outbox drain reclaimed {} abandoned claim(s)", reclaimed);
                    }
                })
                .then(outbox.drain(bridge::publish, BATCH));
    }

    /** The backlog, for the metric and the alert. */
    public Mono<Long> pendingCount() {
        return outbox.pendingCount();
    }
}
