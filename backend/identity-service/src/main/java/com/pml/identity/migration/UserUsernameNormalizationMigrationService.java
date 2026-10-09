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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes {@code identity_users.username} satisfy the unique {@code idx_username} the registry declares,
 * on a database that was written before the index existed, and then builds the index.
 *
 * <p>Startup index creation refuses on such a database ({@code E11000 duplicate key ... username:
 * "admin"}) because legacy accounts can share a username (the same staff login synced from two
 * Keycloak realms or imports) or carry none. Two repairs, both deterministic and idempotent:
 * <ol>
 *   <li><b>Backfill</b> an absent, null, blank or non-string username from the account's
 *       {@code keycloakUserId}, else its {@code _id} - the convention the sync already uses for
 *       accounts it has no name for.</li>
 *   <li><b>De-duplicate</b>: within a group sharing a username the oldest account
 *       ({@code createdAt}, then {@code _id}) keeps it; every other one becomes
 *       {@code <username>-<first 8 of id>} (more of the id on a clash, base trimmed to the 50-character
 *       limit). The login a person types is Keycloak's, not this field, so nothing is locked out; each
 *       rename is logged with both ids.</li>
 * </ol>
 * Finally {@code idx_username} is ensured; a conflict still standing fails the step loudly.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UserUsernameNormalizationMigrationService {

    static final int MAX_LENGTH = 50;

    private final ReactiveMongoTemplate mongo;

    public record Result(long backfilled, long renamed) {
    }

    public Mono<String> migrate() {
        return backfill()
                .flatMap(backfilled -> deduplicate().map(renamed -> new Result(backfilled, renamed)))
                .flatMap(result -> new IndexEnsurer(mongo)
                        .ensure(List.of(IdentityIndexInitializer.specifications().stream()
                                .filter(spec -> spec.name().equals("idx_username")).findFirst().orElseThrow()))
                        .flatMap(report -> report.isClean()
                                ? Mono.just("backfilled " + result.backfilled() + ", renamed " + result.renamed()
                                        + ", idx_username " + report.outcomes().get(0).status())
                                : Mono.<String>error(new IllegalStateException(
                                        "users-username-normalize could not create idx_username: " + report))));
    }

    private Mono<Long> backfill() {
        Document missing = new Document("$or", List.of(
                new Document("username", null),
                new Document("username", ""),
                new Document("username", new Document("$not", new Document("$type", "string")))));
        return mongo.getCollection(IdentityCollections.USERS)
                .flatMapMany(users -> Flux.from(users.find(missing)))
                .concatMap(user -> {
                    String id = String.valueOf(user.get("_id"));
                    Object link = user.get("keycloakUserId");
                    String name = link instanceof String s && !s.isBlank() ? s : id;
                    if (name.length() > MAX_LENGTH) {
                        name = name.substring(0, MAX_LENGTH);
                    }
                    String chosen = name;
                    return mongo.getCollection(IdentityCollections.USERS)
                            .flatMap(users -> Mono.from(users.updateOne(
                                    new Document("_id", user.get("_id")).append("$or", missing.get("$or")),
                                    new Document("$set", new Document("username", chosen)))))
                            .map(updated -> updated.getModifiedCount());
                })
                .reduce(0L, Long::sum);
    }

    private Mono<Long> deduplicate() {
        return mongo.getCollection(IdentityCollections.USERS)
                .flatMap(users -> Flux.from(users.aggregate(List.of(
                                new Document("$match", new Document("username", new Document("$type", "string"))),
                                new Document("$group", new Document("_id", "$username").append("n", new Document("$sum", 1))),
                                new Document("$match", new Document("n", new Document("$gt", 1)))))).collectList()
                        .flatMap(groups -> groups.isEmpty() ? Mono.just(0L) : rename(groups.stream()
                                .map(group -> group.getString("_id")).toList())));
    }

    private Mono<Long> rename(List<String> duplicated) {
        return mongo.getCollection(IdentityCollections.USERS).flatMap(users ->
                Flux.from(users.find(new Document("username", new Document("$in", duplicated)))).collectList()
                        .zipWith(Flux.from(users.distinct("username", String.class)).collectList())
                        .flatMap(loaded -> {
                            List<Document> docs = loaded.getT1();
                            Map<String, List<Document>> byName = new LinkedHashMap<>();
                            docs.forEach(doc -> byName.computeIfAbsent(doc.getString("username"), k -> new ArrayList<>()).add(doc));
                            // Every username in the collection, not only the shared ones: a derived name must not
                            // land on an account outside the duplicated groups either.
                            Set<String> taken = new HashSet<>(loaded.getT2());
                            List<Document[]> plan = new ArrayList<>();
                            byName.forEach((name, group) -> {
                                group.sort(Comparator
                                        .comparing((Document d) -> d.getDate("createdAt"), Comparator.nullsLast(Comparator.naturalOrder()))
                                        .thenComparing(d -> String.valueOf(d.get("_id"))));
                                for (Document loser : group.subList(1, group.size())) {
                                    String id = String.valueOf(loser.get("_id"));
                                    String fresh = fresh(name, id, taken);
                                    taken.add(fresh);
                                    log.warn("identity_users username '{}' is shared; account {} becomes '{}', account {} keeps it",
                                            name, id, fresh, group.get(0).get("_id"));
                                    plan.add(new Document[]{loser, new Document("username", fresh)});
                                }
                            });
                            return Flux.fromIterable(plan)
                                    .concatMap(step -> Mono.from(users.updateOne(
                                            new Document("_id", step[0].get("_id")).append("username", step[0].getString("username")),
                                            new Document("$set", step[1]))))
                                    .map(result -> result.getModifiedCount()).reduce(0L, Long::sum);
                        }));
    }

    /** {@code <name>-<id prefix>}, within the length limit, growing the prefix until it is unused. */
    static String fresh(String name, String id, Set<String> taken) {
        String clean = id.replace("-", "");
        for (int length = 8; length <= clean.length(); length += 4) {
            String suffix = "-" + clean.substring(0, length);
            String base = name.length() + suffix.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH - suffix.length()) : name;
            String candidate = base + suffix;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return id.length() > MAX_LENGTH ? id.substring(0, MAX_LENGTH) : id;
    }
}
