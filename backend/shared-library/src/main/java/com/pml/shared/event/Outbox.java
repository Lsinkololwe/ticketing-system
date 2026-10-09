package com.pml.shared.event;

import com.mongodb.client.model.ReturnDocument;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * The staging half of the transactional outbox: the row and the intent to publish commit together.
 *
 * <h2>The failure this removes</h2>
 * Publishing after a commit — from an {@code AFTER_COMMIT} listener, say — leaves a window with
 * no owner. The document is durable, the message is not sent, and the process can die in
 * between. What the platform does today at three such sites is
 * {@code if (!sent) log.warn(...)}: the write survives, the message is gone, and a WARN line is
 * the only evidence. Nothing retries it because nothing recorded that it should.
 *
 * <p>Staging the envelope <em>inside the business transaction</em> makes the two atomic. Either
 * the ticket and the row both exist, or neither does. Delivery then becomes a separate,
 * restartable problem rather than a moment that must not be interrupted.</p>
 *
 * <h2>No {@code @Document}, deliberately</h2>
 * Shared-library carries no Spring stereotypes, and the three outbox collections are
 * per service ({@code catalog_outbox}, {@code booking_outbox}, {@code identity_outbox}). This
 * works in raw {@link Document}s against a collection name the service supplies, so one engine
 * serves all three without shared-library owning a document.
 *
 * <h2>Exactly once is not what this promises</h2>
 * The bus is at-least-once and a crash between publishing and marking the row sent will deliver
 * twice. That is why every consumer deduplicates on {@code eventId}. What the outbox guarantees
 * is the other half — that a committed write is never silently unaccompanied by its message.
 */
@Slf4j
public final class Outbox {

    /** A staged envelope waiting to be published. */
    public static final String PENDING = "PENDING";

    /** Claimed by a drain that is publishing it now. */
    public static final String PUBLISHING = "PUBLISHING";

    /** Handed to the bus. */
    public static final String SENT = "SENT";

    private final ReactiveMongoTemplate mongoTemplate;
    private final String collection;
    private final Clock clock;

    public Outbox(ReactiveMongoTemplate mongoTemplate, String collection, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.collection = collection;
        this.clock = clock;
    }

    /**
     * Stages an envelope. Call this <b>inside</b> the transaction that writes the document.
     *
     * <p>{@code _id} is the {@code eventId}, so staging the same envelope twice is a duplicate
     * key rather than two messages.</p>
     */
    public Mono<Void> stage(EventEnvelope envelope) {
        Document row = new Document("_id", envelope.eventId())
                .append("eventType", envelope.eventType())
                .append("schemaVersion", envelope.schemaVersion())
                .append("occurredAt", java.util.Date.from(envelope.occurredAt()))
                .append("correlationId", envelope.correlationId())
                .append("causationId", envelope.causationId())
                .append("sourceService", envelope.sourceService())
                .append("payload", new Document(envelope.payload()))
                .append("status", PENDING)
                .append("attempts", 0)
                .append("stagedAt", java.util.Date.from(clock.instant()));

        return mongoTemplate.insert(row, collection).then();
    }

    /**
     * Publishes staged rows, one at a time, and marks each sent only once the sink accepts it.
     *
     * <p>Each row is <b>claimed</b> with a conditional {@code findAndModify} from
     * {@code PENDING} to {@code PUBLISHING}, so two instances draining concurrently cannot both
     * take the same row. A plain "find pending, then update" would let both read it before
     * either wrote, and the message would go out twice for no reason the bus could explain.</p>
     *
     * @param sink what hands the envelope to the bus; its Mono completes when the bus has it
     * @return how many rows were published
     */
    public Mono<Long> drain(Function<EventEnvelope, Mono<Void>> sink, int batchSize) {
        return Flux.range(0, batchSize)
                .concatMap(attempt -> claimOne()
                        .flatMap(row -> publish(row, sink)))
                // An empty claim means nothing is pending: stop rather than spinning through
                // the rest of the batch.
                .takeWhile(published -> published)
                .count();
    }

    private Mono<Document> claimOne() {
        Query pending = Query.query(Criteria.where("status").is(PENDING))
                .with(org.springframework.data.domain.Sort.by("stagedAt"));

        Update claim = new Update()
                .set("status", PUBLISHING)
                .set("claimedAt", java.util.Date.from(clock.instant()))
                .inc("attempts", 1);

        return mongoTemplate.findAndModify(pending, claim,
                FindAndModifyOptions.options().returnNew(true), Document.class, collection);
    }

    private Mono<Boolean> publish(Document row, Function<EventEnvelope, Mono<Void>> sink) {
        EventEnvelope envelope = toEnvelope(row);

        return sink.apply(envelope)
                .then(markSent(row.getString("_id")))
                .thenReturn(true)
                .onErrorResume(error -> {
                    // Back to PENDING, not dropped. The row is the record that this message is
                    // owed; releasing the claim is what makes the next drain pick it up rather
                    // than leaving it stuck in PUBLISHING forever.
                    log.warn("Outbox publish failed for {} ({}), releasing the claim: {}",
                            row.getString("_id"), envelope.eventType(), error.getMessage(), error);
                    return release(row.getString("_id")).thenReturn(false);
                });
    }

    private Mono<Void> markSent(String eventId) {
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(eventId)),
                new Update().set("status", SENT).set("sentAt", java.util.Date.from(clock.instant())),
                collection).then();
    }

    private Mono<Void> release(String eventId) {
        return mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(eventId)),
                new Update().set("status", PENDING).unset("claimedAt"),
                collection).then();
    }

    /**
     * Returns rows stuck in {@code PUBLISHING} to {@code PENDING}.
     *
     * <p>A process that dies mid-publish leaves its claim behind, and nothing else will touch
     * that row — the claim filter only matches {@code PENDING}. Without this the message is not
     * lost but is never sent either, which is the harder failure to notice: the row is right
     * there, and its status says somebody is dealing with it.</p>
     */
    public Mono<Long> reclaimStale(Duration olderThan) {
        Instant cutoff = clock.instant().minus(olderThan);
        return mongoTemplate.updateMulti(
                        Query.query(Criteria.where("status").is(PUBLISHING)
                                .and("claimedAt").lt(java.util.Date.from(cutoff))),
                        new Update().set("status", PENDING).unset("claimedAt"),
                        collection)
                .map(result -> result.getModifiedCount())
                .doOnNext(reclaimed -> {
                    if (reclaimed > 0) {
                        log.warn("Reclaimed {} outbox row(s) abandoned mid-publish", reclaimed);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private static EventEnvelope toEnvelope(Document row) {
        return new EventEnvelope(
                row.getString("_id"),
                row.getString("eventType"),
                row.getInteger("schemaVersion", 1),
                row.getDate("occurredAt").toInstant(),
                row.getString("correlationId"),
                row.getString("causationId"),
                row.getString("sourceService"),
                (java.util.Map<String, Object>) (java.util.Map<?, ?>) row.get("payload", Document.class));
    }

    /** For the drain scheduler and for tests — how much is waiting. */
    public Mono<Long> pendingCount() {
        return mongoTemplate.count(Query.query(Criteria.where("status").is(PENDING)), collection);
    }

    static List<String> statuses() {
        return List.of(PENDING, PUBLISHING, SENT);
    }

    static ReturnDocument returnNew() {
        return ReturnDocument.AFTER;
    }
}
