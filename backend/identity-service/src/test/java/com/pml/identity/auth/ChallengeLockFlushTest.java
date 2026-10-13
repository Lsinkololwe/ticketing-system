package com.pml.identity.auth;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.auth.challenge.ChallengeKeys;
import com.pml.identity.auth.challenge.MongoLockMirror;
import com.pml.identity.auth.delivery.LogWatch;
import com.pml.identity.config.IdentityChallengeProperties;
import com.pml.identity.config.MongoSchemaValidationConfig;
import com.pml.identity.repository.AccountEventRepository;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A contact that was locked for guessing stays locked when Redis is wiped while the service keeps
 * running: the next code request and the next code attempt both consult the durable copy, put the
 * lock back and are refused until the same moment as before.
 */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("a contact lock survives a Redis flush while the service is running")
class ChallengeLockFlushTest {

    private static MongoClient client;
    private static AccountEventRepository events;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        ReactiveMongoTemplate template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_lock_flush"));
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties())
                .applySchemaValidation();
        events = new ReactiveMongoRepositoryFactory(template).getRepository(AccountEventRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private final AuthEngine engine = new AuthEngine(null, new MongoLockMirror(events, new IdentityChallengeProperties()));

    private void flushRedis() {
        engine.redis.execute(connection -> connection.serverCommands().flushAll()).blockLast();
    }

    /** Issues a code and burns the five attempts; returns the challenge id and the lock's end time. */
    private record Locked(String challengeId, String lockedUntil) {
    }

    private Locked lock(String contact) {
        var issued = engine.issue(contact);
        TranslatedRefusal last = null;
        for (int i = 0; i < 5; i++) {
            last = Refusals.of(engine.challenges.verify(issued.challengeId(), "11111" + i));
        }
        assertThat(last.errorCode()).isEqualTo(ErrorCode.OTP_LOCKED);
        return new Locked(issued.challengeId(), (String) last.details().get("lockedUntil"));
    }

    @Test
    @DisplayName("after a flush the next code request is refused as locked, until the same time, and released at 15:01")
    void issueAfterFlush() {
        String phone = AuthEngine.randomPhone();
        Locked locked = lock(phone);

        flushRedis();
        String contactKey = engine.hasher.normalize(phone, null, null, null).orElseThrow().key();
        assertThat(engine.redis.hasKey(ChallengeKeys.lock(contactKey)).block()).isFalse();

        TranslatedRefusal refused = Refusals.of(ErrorCode.OTP_LOCKED, engine.challenges.issue(engine.command(phone)));
        assertThat(refused.details().get("lockedUntil")).isEqualTo(locked.lockedUntil());

        engine.clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        Refusals.of(ErrorCode.OTP_LOCKED, engine.challenges.issue(engine.command(phone)));

        engine.clock.advance(Duration.ofSeconds(2));
        assertThat(engine.challenges.issue(engine.command(phone)).block().challengeId()).isNotBlank();
    }

    @Test
    @DisplayName("after a flush an attempt with the old challenge id is refused as locked, not as expired")
    void verifyAfterFlush() {
        String phone = AuthEngine.randomPhone();
        Locked locked = lock(phone);

        flushRedis();

        TranslatedRefusal refused = Refusals.of(ErrorCode.OTP_LOCKED, engine.challenges.verify(locked.challengeId(), "000000"));
        assertThat(refused.details().get("lockedUntil")).isEqualTo(locked.lockedUntil());

        engine.clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        Refusals.of(ErrorCode.OTP_EXPIRED, engine.challenges.verify(locked.challengeId(), "000000"));
    }

    @Test
    @DisplayName("a contact that was never locked is not affected by a flush")
    void unlockedContactIsUntouched() {
        String phone = AuthEngine.randomPhone();
        flushRedis();
        assertThat(engine.issue(phone).challengeId()).isNotBlank();
    }

    @Test
    @DisplayName("when the durable copy cannot be written the lock in Redis still applies, one counter and one warning by class name only")
    void failedMirrorWriteIsCountedAndLogged() {
        var mirror = new AuthEngine.RecordingMirror();
        mirror.failWrites = true;
        AuthEngine failing = new AuthEngine(null, mirror);
        String phone = AuthEngine.randomPhone();

        try (LogWatch logs = new LogWatch()) {
            var issued = failing.issue(phone);
            for (int i = 0; i < 5; i++) {
                Refusals.of(failing.challenges.verify(issued.challengeId(), "22222" + i));
            }

            Refusals.of(ErrorCode.OTP_LOCKED, failing.challenges.issue(failing.command(phone)));
            assertThat(failing.meters.counter("identity.otp.lock.mirror", "outcome", "write_failed").count())
                    .isEqualTo(1.0);
            assertThat(logs.lines()).anyMatch(line -> line.contains("Could not mirror a contact lock: IllegalStateException"));
            logs.assertNoneContains(phone, phone.substring(1), "mirror down");
        }
    }
}
