package com.pml.identity.migration;

import com.pml.identity.config.IdentityIndexInitializer;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.persistence.IndexEnsurer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.mongodb.client.model.UpdateOptions;

/**
 * Creates {@code uniq_verified_contact} on {@code identity_contacts} once the data can satisfy it
 * (ET-IDN-004). Runs after {@code contacts-backfill}.
 *
 * <p>If a verified contact still belongs to more than one account, the step does not build the
 * index and does not merge anything. It writes one {@code CONTACT_DUPLICATE_REPORT} row per
 * duplicate into {@code identity_account_events} (account ids and the type only - the contact key
 * is not copied, so the report holds no personal data) and fails with a message that says how many.
 * An operator resolves them by merging the accounts, then clears this step's ledger row.</p>
 *
 * <p>Idempotent: report rows have a deterministic id, and an index that already exists identically
 * is reported as present.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ContactsUniqueIndexMigrationService {

    static final String REPORT_KIND = "CONTACT_DUPLICATE_REPORT";
    private static final int REPORT_LIMIT = 1000;

    private final ReactiveMongoTemplate mongo;
    private final Clock clock;

    public Mono<String> migrate() {
        return duplicateGroups().flatMap(groups -> {
            if (!groups.isEmpty()) {
                return report(groups).then(Mono.<String>error(new IllegalStateException(
                        "contacts-unique-index refused: %d verified contact(s) belong to more than one account%s. "
                                .formatted(groups.size(), groups.size() >= REPORT_LIMIT ? " (at least; report capped)" : "")
                                + "Nothing was merged. See " + IdentityCollections.ACCOUNT_EVENTS + " kind "
                                + REPORT_KIND + ", merge the accounts, then clear this step's ledger row.")));
            }
            return new IndexEnsurer(mongo)
                    .ensure(List.of(IdentityIndexInitializer.deferred(IdentityIndexInitializer.UNIQUE_VERIFIED_CONTACT)))
                    .flatMap(report -> {
                        if (!report.isClean()) {
                            return Mono.<String>error(new IllegalStateException(
                                    "contacts-unique-index could not create the index: " + report));
                        }
                        return Mono.just("uniq_verified_contact " + report.outcomes().get(0).status());
                    });
        });
    }

    private record Group(String type, int accounts, List<String> accountIds) {
    }

    private Mono<List<Group>> duplicateGroups() {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("verifiedAt", new Document("$exists", true)).append("releasedAt", null)),
                new Document("$group", new Document("_id", new Document("type", "$type").append("valueHash", "$valueHash"))
                        .append("n", new Document("$sum", 1))
                        .append("accountIds", new Document("$addToSet", "$accountId"))),
                new Document("$match", new Document("n", new Document("$gt", 1))),
                new Document("$limit", REPORT_LIMIT));
        return mongo.getCollection(IdentityCollections.CONTACTS)
                .flatMapMany(contacts -> Flux.from(contacts.aggregate(pipeline)))
                .map(doc -> {
                    Document id = doc.get("_id", Document.class);
                    @SuppressWarnings("unchecked")
                    List<String> accountIds = (List<String>) doc.get("accountIds");
                    return new Group(id.getString("type"), accountIds.size(), accountIds);
                })
                .collectList();
    }

    private Mono<Void> report(List<Group> groups) {
        Date now = Date.from(clock.instant());
        return mongo.getCollection(IdentityCollections.ACCOUNT_EVENTS).flatMap(events -> {
            List<Mono<?>> writes = new ArrayList<>();
            for (Group group : groups) {
                // One row per (account, duplicate): keyed so a re-run replaces rather than repeats.
                for (String accountId : group.accountIds()) {
                    String eventId = REPORT_KIND + ":" + accountId + ":" + group.type() + ":"
                            + Integer.toHexString(group.accountIds().stream().sorted().toList().hashCode());
                    writes.add(Mono.from(events.updateOne(new Document("_id", eventId),
                            new Document("$set", new Document("_class", "account_events")
                                    .append("accountId", accountId)
                                    .append("kind", REPORT_KIND)
                                    .append("at", now)
                                    .append("data", new Document("type", group.type())
                                            .append("sharedWith", group.accounts() - 1))),
                            new UpdateOptions().upsert(true))));
                }
            }
            return Flux.concat(writes).then();
        }).doOnSuccess(v -> log.warn("Contacts unique index refused: {} duplicate verified contact(s); report written", groups.size()));
    }
}
