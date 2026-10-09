package com.pml.shared.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Removes a collection whose document class no longer exists.
 *
 * <h2>Different from {@link RedundantSourceDrop}, and the difference matters</h2>
 * That one settles a <em>pair</em>: a source and the registry collection it should have become,
 * dropped only when the target already holds everything. This one has no target. The collection
 * is going because nothing in the platform reads it any more — the {@code @Document} class and
 * its repository have been deleted — so there is no second copy to compare against and the only
 * safe posture is to say how much is being destroyed before destroying it.
 *
 * <h2>The count is logged at WARN before the drop, always</h2>
 * A drop reported as "done" tells an operator nothing about what it cost. If the expectation was
 * an empty leftover and the log says 4,000 documents, that is the moment to find out — while the
 * backup is still current — rather than from the absence of data later.
 *
 * <h2>Why this can be automatic when the redundancy check cannot</h2>
 * {@code RedundantSourceDrop} refuses without proof because a source collection is still mapped
 * and still read; getting it wrong loses live data. Here the mapping is gone, so the collection
 * is already unreachable from the application: dropping it changes nothing the code can observe,
 * and keeping it means a registry that is closed on paper and not on disk.
 */
@Slf4j
public final class RetiredCollectionDrop {

    private final ReactiveMongoTemplate mongoTemplate;

    public RetiredCollectionDrop(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Mono<String> drop(List<String> collections) {
        return Flux.fromIterable(collections)
                .concatMap(this::dropOne)
                .collectList()
                .map(reports -> String.join("; ", reports));
    }

    private Mono<String> dropOne(String collection) {
        return mongoTemplate.getMongoDatabase()
                .flatMapMany(database -> database.listCollectionNames())
                .any(collection::equals)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.just(collection + ": already absent");
                    }
                    return mongoTemplate.getMongoDatabase()
                            .flatMap(database -> Mono.from(
                                    database.getCollection(collection).countDocuments()))
                            .flatMap(count -> {
                                log.warn("Dropping retired collection {} holding {} document(s) — "
                                        + "its @Document class and repository no longer exist",
                                        collection, count);
                                return mongoTemplate.getMongoDatabase()
                                        .flatMap(database -> Mono.from(
                                                database.getCollection(collection).drop()))
                                        .thenReturn("%s: dropped, %d document(s)"
                                                .formatted(collection, count));
                            });
                });
    }
}
