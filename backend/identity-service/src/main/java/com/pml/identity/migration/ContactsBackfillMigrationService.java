package com.pml.identity.migration;

import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.WriteModel;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.identity.security.ContactCrypto;
import com.pml.identity.security.ContactHasher;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds {@code identity_contacts} from the contact fields the accounts already carry (ET-IDN-004).
 *
 * <ul>
 *   <li>An email contact for every account with a valid, non-placeholder email (the made-up
 *       {@code @phone.local} addresses are skipped), lower-cased. Verified when {@code emailVerified}.</li>
 *   <li>A WHATSAPP contact for every account with a parseable phone number. Verified only when
 *       {@code phoneVerified}.</li>
 *   <li>Each stores the contact key, the encrypted value and the mask - never the plain value.</li>
 * </ul>
 *
 * <h2>Resumable and idempotent</h2>
 * A contact's {@code _id} is derived from (account, type, contact key), written with
 * {@code $setOnInsert}: a second run, or a run that resumes after a crash, creates nothing that
 * exists and overwrites nothing. Accounts are read in {@code _id} order in batches of
 * {@code batchSize}.
 *
 * <h2>Duplicates are copied, not resolved</h2>
 * Two accounts that verified the same contact each get a contact row. The unique index step
 * ({@code contacts-unique-index}) reports them; nothing here merges accounts.
 *
 * <h2>Dry run</h2>
 * With {@code dryRun} the same scan runs and counts what would be written; nothing is written.
 */
@Slf4j
@Service
public class ContactsBackfillMigrationService {

    private final ReactiveMongoTemplate mongo;
    private final ContactHasher hasher;
    private final ContactCrypto crypto;
    private final Clock clock;
    private final int batchSize;
    private final boolean dryRun;

    @Autowired
    public ContactsBackfillMigrationService(
            ReactiveMongoTemplate mongo, ContactHasher hasher, ContactCrypto crypto, Clock clock,
            @Value("${identity.migrations.contacts-backfill-batch-size:500}") int batchSize,
            @Value("${identity.migrations.contacts-backfill-dry-run:false}") boolean dryRun) {
        this.mongo = mongo;
        this.hasher = hasher;
        this.crypto = crypto;
        this.clock = clock;
        this.batchSize = Math.max(1, batchSize);
        this.dryRun = dryRun;
    }

    /** Counts only. {@code written} is what a dry run reports as "would write". */
    public record Result(boolean dryRun, long accountsScanned, long written, long alreadyPresent,
                         long skippedPlaceholder, long skippedInvalid, long verified, long primaryLinked) {
        @Override
        public String toString() {
            return "%saccountsScanned=%d written=%d alreadyPresent=%d verified=%d skippedPlaceholder=%d skippedInvalid=%d primaryLinked=%d"
                    .formatted(dryRun ? "DRY-RUN " : "", accountsScanned, written, alreadyPresent, verified,
                            skippedPlaceholder, skippedInvalid, primaryLinked);
        }
    }

    public Mono<Result> migrate() {
        return migrate(dryRun);
    }

    public Mono<Result> migrate(boolean dryRun) {
        Counters counters = new Counters();
        return mongo.getCollection(IdentityCollections.USERS)
                .flatMap(users -> mongo.getCollection(IdentityCollections.CONTACTS)
                        .flatMap(contacts -> Flux.from(users.find()
                                        .projection(new Document("email", 1).append("emailVerified", 1)
                                                .append("phoneNumber", 1).append("phoneVerified", 1)
                                                .append("createdAt", 1).append("updatedAt", 1)
                                                .append("primaryContactId", 1))
                                        .sort(new Document("_id", 1))
                                        .batchSize(batchSize))
                                .buffer(batchSize)
                                .concatMap(batch -> processBatch(batch, users, contacts, counters, dryRun))
                                .then()))
                .then(Mono.fromSupplier(() -> counters.result(dryRun)))
                .doOnNext(result -> log.info("Contacts backfill: {}", result));
    }

    private Mono<Void> processBatch(List<Document> batch,
                                    com.mongodb.reactivestreams.client.MongoCollection<Document> users,
                                    com.mongodb.reactivestreams.client.MongoCollection<Document> contacts,
                                    Counters counters, boolean dryRun) {
        return Flux.fromIterable(batch)
                .concatMap(user -> contactsOf(user, counters, dryRun))
                .collectList()
                .flatMap(candidates -> {
                    counters.scanned.addAndGet(batch.size());
                    if (candidates.isEmpty()) {
                        return Mono.<Void>empty();
                    }
                    if (dryRun) {
                        counters.written.addAndGet(candidates.size());
                        candidates.stream().filter(c -> c.verifiedAt != null).forEach(c -> counters.verified.incrementAndGet());
                        return Mono.<Void>empty();
                    }
                    List<WriteModel<Document>> contactWrites = new ArrayList<>();
                    List<WriteModel<Document>> userLinks = new ArrayList<>();
                    for (Candidate c : candidates) {
                        contactWrites.add(new UpdateOneModel<>(new Document("_id", c.id),
                                new Document("$setOnInsert", c.toDocument()), new UpdateOptions().upsert(true)));
                        if (c.primary) {
                            userLinks.add(new UpdateOneModel<>(
                                    new Document("_id", c.accountId).append("primaryContactId", new Document("$exists", false)),
                                    new Document("$set", new Document("primaryContactId", c.id))));
                        }
                    }
                    return Mono.from(contacts.bulkWrite(contactWrites, new BulkWriteOptions().ordered(false)))
                            .doOnNext(result -> {
                                counters.written.addAndGet(result.getUpserts().size());
                                counters.alreadyPresent.addAndGet(candidates.size() - result.getUpserts().size());
                                // Count verified among the ones actually written.
                                result.getUpserts().forEach(upsert -> {
                                    if (candidates.get(upsert.getIndex()).verifiedAt != null) {
                                        counters.verified.incrementAndGet();
                                    }
                                });
                            })
                            .then(userLinks.isEmpty() ? Mono.<Void>empty()
                                    : Mono.from(users.bulkWrite(userLinks, new BulkWriteOptions().ordered(false)))
                                    .doOnNext(r -> counters.primaryLinked.addAndGet(r.getModifiedCount()))
                                    .then());
                });
    }

    /** The contact rows one account should have, encrypted. Placeholder and invalid values are counted, not kept. */
    private Flux<Candidate> contactsOf(Document user, Counters counters, boolean dryRun) {
        String accountId = String.valueOf(user.get("_id"));
        Date created = user.get("createdAt") instanceof Date d ? d : null;
        Date updated = user.get("updatedAt") instanceof Date d ? d : created;
        Date now = Date.from(clock.instant());
        Date verifiedStamp = updated != null ? updated : now;
        Date createdStamp = created != null ? created : now;

        List<Candidate> found = new ArrayList<>();

        if (user.get("email") instanceof String email && !email.isBlank()) {
            if (AccountPreflightReportMigrationService.PLACEHOLDER_EMAIL.matcher(email.trim()).matches()) {
                counters.skippedPlaceholder.incrementAndGet();
            } else {
                Optional<ContactHasher.Normalized> n = hasher.normalize(email, ContactType.EMAIL, null, null);
                if (n.isEmpty()) {
                    counters.skippedInvalid.incrementAndGet();
                } else {
                    found.add(new Candidate(accountId, n.get(), Boolean.TRUE.equals(user.getBoolean("emailVerified"))
                            ? verifiedStamp : null, createdStamp));
                }
            }
        }
        if (user.get("phoneNumber") instanceof String phone && !phone.isBlank()) {
            Optional<ContactHasher.Normalized> n = hasher.normalize(phone, ContactType.WHATSAPP, null, null);
            if (n.isEmpty()) {
                counters.skippedInvalid.incrementAndGet();
            } else {
                found.add(new Candidate(accountId, n.get(), Boolean.TRUE.equals(user.getBoolean("phoneVerified"))
                        ? verifiedStamp : null, createdStamp));
            }
        }

        // One primary per account: the verified phone, else the verified email.
        found.stream().filter(c -> c.verifiedAt != null && c.type == ContactType.WHATSAPP).findFirst()
                .or(() -> found.stream().filter(c -> c.verifiedAt != null).findFirst())
                .ifPresent(c -> c.primary = true);

        return Flux.fromIterable(found).concatMap(c -> dryRun
                ? Mono.just(c)
                : crypto.encrypt(c.value).doOnNext(cipher -> c.encrypted = cipher).thenReturn(c));
    }

    private static final class Counters {
        final AtomicLong scanned = new AtomicLong();
        final AtomicLong written = new AtomicLong();
        final AtomicLong alreadyPresent = new AtomicLong();
        final AtomicLong skippedPlaceholder = new AtomicLong();
        final AtomicLong skippedInvalid = new AtomicLong();
        final AtomicLong verified = new AtomicLong();
        final AtomicLong primaryLinked = new AtomicLong();

        Result result(boolean dryRun) {
            return new Result(dryRun, scanned.get(), written.get(), alreadyPresent.get(), skippedPlaceholder.get(),
                    skippedInvalid.get(), verified.get(), primaryLinked.get());
        }
    }

    private static final class Candidate {
        final String id;
        final String accountId;
        final ContactType type;
        final String value;
        final String hash;
        final String masked;
        final Date verifiedAt;
        final Date createdAt;
        boolean primary;
        String encrypted;

        Candidate(String accountId, ContactHasher.Normalized n, Date verifiedAt, Date createdAt) {
            this.accountId = accountId;
            this.type = n.type();
            this.value = n.value();
            this.hash = n.key();
            this.masked = n.masked();
            this.verifiedAt = verifiedAt;
            this.createdAt = createdAt;
            this.id = UUID.nameUUIDFromBytes(("contact|" + accountId + "|" + type + "|" + hash)
                    .getBytes(StandardCharsets.UTF_8)).toString();
        }

        Document toDocument() {
            Document d = new Document("_class", "contacts")
                    .append("accountId", accountId)
                    .append("type", type.name())
                    .append("valueHash", hash)
                    .append("valueEncrypted", encrypted)
                    .append("valueMasked", masked)
                    .append("primary", primary)
                    .append("source", "MIGRATION")
                    .append("createdAt", createdAt);
            if (verifiedAt != null) {
                d.append("verifiedAt", verifiedAt);
            }
            return d;
        }

        @Override
        public String toString() {
            return "Candidate[" + type + ", " + masked + "]";
        }
    }
}
