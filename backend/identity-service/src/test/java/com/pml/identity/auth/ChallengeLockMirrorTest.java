package com.pml.identity.auth;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.auth.challenge.ChallengeKeys;
import com.pml.identity.auth.challenge.MongoLockMirror;
import com.pml.identity.config.IdentityChallengeProperties;
import com.pml.identity.config.MongoSchemaValidationConfig;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.identity.repository.AccountEventRepository;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A contact lock is mirrored to MongoDB (key only) and survives the loss of Redis (ET-IDN-001-R3). */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001-R3 · a lock is mirrored to identity_account_events and restored if Redis loses it")
class ChallengeLockMirrorTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static AccountEventRepository events;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_lock_mirror"));
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties())
                .applySchemaValidation();
        events = new ReactiveMongoRepositoryFactory(template).getRepository(AccountEventRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("exhausting the attempts writes one OTP_LOCK event holding the contact key and no contact; a lost Redis lock is restored")
    void mirroredAndRestored() {
        template.remove(new Query(), IdentityCollections.ACCOUNT_EVENTS).block();
        IdentityChallengeProperties properties = new IdentityChallengeProperties();
        var engine = new AuthEngine(null, new MongoLockMirror(events, properties));
        String phone = AuthEngine.randomPhone();
        var issued = engine.issue(phone);
        String contactKey = engine.hasher.normalize(phone, null, null, null).orElseThrow().key();

        for (int i = 0; i < 5; i++) {
            Refusals.of(engine.challenges.verify(issued.challengeId(), "11111" + i));
        }

        List<AccountEvent> written = events.findAll().collectList().block();
        assertThat(written).hasSize(1);
        AccountEvent event = written.get(0);
        assertThat(event.getKind()).isEqualTo("OTP_LOCK");
        assertThat(event.getAccountId()).isNull();
        assertThat(event.getData()).containsEntry("contactKey", contactKey);
        assertThat(event.toString()).doesNotContain(phone.substring(1));

        // Redis loses the lock (flush, failover): the mirror puts it back.
        engine.redis.delete(ChallengeKeys.lock(contactKey)).block();
        engine.clock.advance(Duration.ofMinutes(1));
        assertThat(engine.challenges.restoreLocks().block()).isEqualTo(1L);
        Refusals.of(ErrorCode.OTP_LOCKED, engine.challenges.issue(engine.command(phone)));

        // Restoring again changes nothing, and after the lock has run out nothing is restored.
        assertThat(engine.challenges.restoreLocks().block()).isZero();
        engine.clock.advance(Duration.ofMinutes(16));
        assertThat(engine.challenges.restoreLocks().block()).isZero();
    }
}
