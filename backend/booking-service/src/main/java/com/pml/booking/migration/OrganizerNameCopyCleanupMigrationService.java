package com.pml.booking.migration;

import com.pml.booking.persistence.BookingCollections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Drops the organizer name copied onto escrow accounts and payout requests.
 *
 * <p>The organization's name is identity's; these records now carry only its id and a client reads
 * the name through the federated {@code organization} reference, so a copy here could only go stale.
 * Runs before the collection validators tighten, which is why the property can leave them.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class OrganizerNameCopyCleanupMigrationService {

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    /** @return how many documents lost the field, across both collections */
    public Mono<Long> migrate() {
        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();
        Document filter = new Document("organizerName", new Document("$exists", true));
        Document unset = new Document("$unset", new Document("organizerName", ""));
        return mongo.getMongoDatabase()
                .flatMapMany(db -> Flux.fromIterable(List.of(BookingCollections.ESCROW_ACCOUNTS, BookingCollections.PAYOUT_REQUESTS))
                        .concatMap(name -> Mono.from(db.getCollection(name).updateMany(filter, unset))
                                .map(result -> result.getModifiedCount())
                                .defaultIfEmpty(0L)))
                .reduce(0L, Long::sum)
                .doOnNext(n -> log.info("Organizer name copy cleanup: removed organizerName from {} document(s)", n));
    }
}
