package com.pml.identity.migration;

import com.pml.identity.persistence.IdentityCollections;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Drops the retired {@code registrationEventPublished} field from {@code identity_users}.
 *
 * <h2>Why the field is gone</h2>
 * Publication of the registration fact runs through the transactional outbox
 * ({@code com.pml.shared.event.Outbox}): the envelope is staged inside the same
 * transaction as the user write, so its delivery state lives in {@code identity_outbox} — where
 * every other event's delivery state lives — rather than as a boolean flag on the user document
 * itself. {@code User} does not declare the field.
 *
 * <p>{@code additionalProperties: false} on {@code users-schema.json} means a document still
 * carrying it fails validation the next time anything resaves it whole; this migration removes it
 * up front rather than waiting for that write to fail.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UserFieldCleanupMigrationService {

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public Mono<Long> migrate() {
        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();
        Document filter = new Document("registrationEventPublished", new Document("$exists", true));
        Document unset = new Document("$unset", new Document("registrationEventPublished", ""));

        return mongo.getMongoDatabase()
                .flatMap(db -> Mono.from(db.getCollection(IdentityCollections.USERS)
                        .updateMany(filter, unset)))
                .map(result -> result.getModifiedCount())
                .doOnNext(n -> log.info("User field cleanup (F-035): removed registrationEventPublished "
                        + "from {} document(s) — publication now tracked in identity_outbox", n))
                .defaultIfEmpty(0L);
    }
}
