package com.pml.identity.account;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.migration.ContactsUniqueIndexMigrationService;
import com.pml.identity.security.ContactCrypto;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.security.FieldEncryptionService;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * What the account tests share: a database on the platform's MongoDB replica set (transactions are
 * real), the unique contact index built as the migration builds it, the contact hasher and crypto,
 * and a proof store standing in for the engine's Redis one.
 */
public final class AccountFixtures {

    public static final String OUTBOX = "identity_outbox";
    public static final ContactHasher HASHER = new ContactHasher("test-contact-hash-key-not-a-secret");
    public static final ContactCrypto CRYPTO =
            new ContactCrypto(new FieldEncryptionService(FieldEncryptionService.generateKey(), "k7"));

    public final MongoClient client;
    public final ReactiveMongoTemplate template;
    public final TransactionalOperator transaction;
    public final Outbox outbox;
    public final FakeProofs proofs = new FakeProofs();

    /** Against the shared replica set; tests that declare layer 2 pass its connection string themselves. */
    public AccountFixtures(String database, Clock clock) {
        this(MongoReplicaSet.connectionString(), database, clock);
    }

    public AccountFixtures(String connectionString, String database, Clock clock) {
        this.client = MongoClients.create(connectionString);
        SimpleReactiveMongoDatabaseFactory factory = new SimpleReactiveMongoDatabaseFactory(client, database);
        this.template = new ReactiveMongoTemplate(factory);
        this.transaction = TransactionalOperator.create(new ReactiveMongoTransactionManager(factory));
        this.outbox = new Outbox(template, OUTBOX, clock);
        // collections must exist before the first transaction touches them
        for (String collection : List.of("identity_users", "identity_contacts", "identity_consents",
                "identity_account_events", "identity_audit_logs", OUTBOX)) {
            template.collectionExists(collection)
                    .flatMap(exists -> exists ? Mono.empty() : template.createCollection(collection).then())
                    .block();
        }
        new ContactsUniqueIndexMigrationService(template, clock).migrate().block();
    }

    public void reset() {
        for (String collection : List.of("identity_users", "identity_contacts", "identity_consents",
                "identity_account_events", "identity_audit_logs", OUTBOX)) {
            template.remove(new Query(), collection).block();
        }
        proofs.clear();
    }

    public void close() {
        client.close();
    }

    public long count(String collection) {
        return template.count(new Query(), collection).block();
    }

    public List<Document> all(String collection) {
        return template.findAll(Document.class, collection).collectList().block();
    }

    /** A verified proof of a contact, as the engine would leave it in Redis. */
    public ProofRecord proofFor(ContactType type, String normalized, String proofId) {
        ContactHasher.Normalized contact = HASHER.normalize(normalized, type, "ZM", null).orElseThrow();
        ProofRecord proof = new ProofRecord(proofId, contact.key(), type, CRYPTO.encrypt(contact.value()).block(),
                contact.masked(), "CONSUMED", null);
        proofs.put(proof);
        return proof;
    }

    public static final class FakeProofs implements ProofLookup {
        private final Map<String, ProofRecord> store = new ConcurrentHashMap<>();

        public void put(ProofRecord proof) {
            store.put(proof.proofId(), proof);
        }

        public void clear() {
            store.clear();
        }

        @Override
        public Mono<ProofRecord> find(String proofId) {
            return Mono.justOrEmpty(store.get(proofId));
        }
    }
}
