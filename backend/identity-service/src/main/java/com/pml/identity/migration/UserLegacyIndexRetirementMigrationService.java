package com.pml.identity.migration;

import com.pml.identity.persistence.IdentityCollections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Drops two {@code identity_users} indexes the account model cannot live with (ET-IDN-004).
 *
 * <ul>
 *   <li>{@code email}: a PLAIN unique index on {@code email}. It treats an absent address as a
 *       value, so the second account without an email (every phone-only account) is refused. The
 *       partial unique {@code idx_email} stays; it excludes accounts that have no address.</li>
 *   <li>{@code idx_userType_accountStatus}: indexes {@code userType}, a field no document has.</li>
 * </ul>
 *
 * Idempotent: an index that is already gone is skipped. The partial {@code idx_email} is never
 * touched, even though it shares its key with the plain one.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UserLegacyIndexRetirementMigrationService {

    private final ReactiveMongoTemplate mongo;

    public Mono<String> migrate() {
        return mongo.getMongoDatabase()
                .flatMap(db -> Flux.from(db.getCollection(IdentityCollections.USERS).listIndexes())
                        .collectList()
                        .onErrorReturn(List.of())
                        .flatMapMany(indexes -> Flux.fromIterable(indexes)
                                .filter(UserLegacyIndexRetirementMigrationService::isRetired)
                                .map(index -> index.getString("name")))
                        .concatMap(name -> Mono.from(db.getCollection(IdentityCollections.USERS).dropIndex(name))
                                .thenReturn(name))
                        .collectList())
                .map(dropped -> {
                    log.info("Dropped {} legacy identity_users index(es): {}", dropped.size(), dropped);
                    return dropped.isEmpty() ? "nothing to drop" : "dropped " + dropped;
                });
    }

    private static boolean isRetired(Document index) {
        String name = index.getString("name");
        if ("idx_userType_accountStatus".equals(name)) {
            return true;
        }
        // The plain unique one, recognised by name AND by having no partial filter, so a partial
        // index that someone named `email` is not mistaken for it.
        return "email".equals(name)
                && new Document("email", 1).equals(normalise(index.get("key", Document.class)))
                && Boolean.TRUE.equals(index.getBoolean("unique"))
                && index.get("partialFilterExpression") == null;
    }

    private static Document normalise(Document key) {
        Document out = new Document();
        if (key != null) {
            key.forEach((field, direction) -> out.append(field, ((Number) direction).intValue()));
        }
        return out;
    }
}
