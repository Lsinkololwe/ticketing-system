package com.pml.catalog.migration;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.service.EventTierMirror;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Gives every event written before sales totals existed its totals: {@code soldTickets} and
 * {@code availableTickets} are recomputed from the tiers (which always carried the truth), and
 * {@code grossSales} and {@code commissionAmount} start at zero, because what was paid before booking
 * reported amounts is in booking's ledger and not here.
 *
 * <p>Idempotent: only events without {@code grossSales} are touched, and the step is recorded in the
 * ledger so it runs once.
 */
public final class EventSalesTotalsBackfill {

    private final ReactiveMongoTemplate mongo;
    private final EventTierMirror mirror;

    public EventSalesTotalsBackfill(ReactiveMongoTemplate mongo, EventTierMirror mirror) {
        this.mongo = mongo;
        this.mirror = mirror;
    }

    /** @return a line for the ledger: how many events were brought up to date */
    public Mono<String> run() {
        Query missing = Query.query(Criteria.where("grossSales").exists(false));
        return mongo.find(missing, Event.class)
                .concatMap(event -> mirror.refresh(event.getId()))
                .count()
                .flatMap(refreshed -> mongo.updateMulti(Query.query(Criteria.where("grossSales").exists(false)),
                                new Update().set("grossSales", BigDecimal.ZERO).set("commissionAmount", BigDecimal.ZERO),
                                CatalogCollections.EVENTS)
                        .map(result -> refreshed + " events refreshed, " + result.getModifiedCount() + " given zero totals"));
    }
}
