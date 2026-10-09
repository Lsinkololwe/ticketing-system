package com.pml.identity.migration;

import com.pml.identity.persistence.IdentityCollections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Gives every pre-existing account the ET-IDN-004 fields (CONTRACT 2 and 8). Old fields stay.
 *
 * <ul>
 *   <li>{@code status}: SUSPENDED when {@code active == false}, {@code locked == true} or
 *       {@code accountStatus} is INACTIVE, LOCKED or SUSPENDED; PROVISIONING for PENDING_VERIFICATION;
 *       otherwise ACTIVE. PENDING_DELETION stays ACTIVE and gets {@code pendingKind =
 *       DELETION_REQUESTED} and {@code pendingSince}.</li>
 *   <li>{@code keycloakUserId = _id} and {@code provisionedAt = createdAt}.</li>
 *   <li>Orphans (an {@code _id} that is not a Keycloak UUID): {@code status = PROVISIONING}, no
 *       {@code keycloakUserId}, no {@code provisionedAt}.</li>
 *   <li>The placeholder names {@code Phone} / {@code User} that phone sign-up wrote are removed.</li>
 * </ul>
 *
 * <p>Idempotent: only documents with no {@code status} are touched, and the placeholder-name
 * filter matches nothing once the names are gone. Each document is updated by one atomic
 * pipeline update, so a crash between documents leaves a mix that the next run completes.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class UserAccountFieldsBackfillMigrationService {

    private static final Pattern UUID = AccountPreflightReportMigrationService.UUID;

    private final ReactiveMongoTemplate mongo;

    public record Result(long orphans, long accounts, long placeholderNames) {
        @Override
        public String toString() {
            return "orphans=%d accounts=%d placeholderNamesRemoved=%d".formatted(orphans, accounts, placeholderNames);
        }
    }

    public Mono<Result> migrate() {
        return mongo.getCollection(IdentityCollections.USERS).flatMap(users -> {
            Document noStatus = new Document("status", new Document("$exists", false));

            // 1. Orphans first: they must not be given a keycloakUserId equal to a made-up id.
            Document orphanFilter = new Document(noStatus)
                    .append("_id", new Document("$not", UUID));
            Document orphanUpdate = new Document("$set", new Document("status", "PROVISIONING"));

            // 2. Everything else, in one pipeline update per document.
            Document pendingDeletion = new Document("$eq", List.of("$accountStatus", "PENDING_DELETION"));
            Document accountStages = new Document("$set", new Document()
                    .append("keycloakUserId", "$_id")
                    .append("provisionedAt", new Document("$cond", List.of(
                            new Document("$eq", List.of("$accountStatus", "PENDING_VERIFICATION")),
                            "$$REMOVE", "$createdAt")))
                    .append("status", new Document("$switch", new Document()
                            .append("branches", List.of(
                                    new Document("case", new Document("$or", List.of(
                                            new Document("$eq", List.of("$active", false)),
                                            new Document("$eq", List.of("$locked", true)),
                                            new Document("$in", List.of("$accountStatus",
                                                    List.of("INACTIVE", "LOCKED", "SUSPENDED"))))))
                                            .append("then", "SUSPENDED"),
                                    new Document("case", new Document("$eq",
                                            List.of("$accountStatus", "PENDING_VERIFICATION")))
                                            .append("then", "PROVISIONING")))
                            .append("default", "ACTIVE")))
                    .append("pendingKind", new Document("$cond", List.of(pendingDeletion, "DELETION_REQUESTED", "$$REMOVE")))
                    .append("pendingSince", new Document("$cond", List.of(pendingDeletion,
                            new Document("$ifNull", List.of("$updatedAt", "$createdAt")), "$$REMOVE"))));

            // 3. The names phone sign-up invented. Only the pair, so a real "User" is left alone.
            Document placeholderNames = new Document("firstName", "Phone").append("lastName", "User");
            Document unsetNames = new Document("$unset", new Document("firstName", "").append("lastName", ""));

            return Mono.from(users.updateMany(orphanFilter, orphanUpdate))
                    .flatMap(orphans -> Mono.from(users.updateMany(noStatus, List.of(accountStages)))
                            .flatMap(accounts -> Mono.from(users.updateMany(placeholderNames, unsetNames))
                                    .map(names -> new Result(orphans.getModifiedCount(),
                                            accounts.getModifiedCount(), names.getModifiedCount()))));
        }).doOnNext(result -> log.info("Account fields backfill: {}", result));
    }
}
