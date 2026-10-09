package com.pml.identity.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.identity.auth.challenge.ChallengeService;
import com.pml.identity.auth.challenge.LockMirror;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.delivery.CapturingProvider;
import com.pml.identity.auth.delivery.CodeDeliveryProvider;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.auth.limits.LimitService;
import com.pml.identity.auth.proof.LoginHandleService;
import com.pml.identity.auth.proof.ProofService;
import com.pml.identity.config.IdentityChallengeProperties;
import com.pml.identity.config.IdentityLimitsProperties;
import com.pml.identity.config.IdentityLoginHandleProperties;
import com.pml.identity.config.IdentityProofProperties;
import com.pml.identity.security.ContactCrypto;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.security.FieldEncryptionService;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CopyOnWriteArrayList;

/** The challenge engine wired by hand against a real Redis, a frozen clock and captured delivery. */
public final class AuthEngine {

    public static final Instant START = Instant.parse("2026-10-04T08:00:00Z");
    public static final String AES_KEY = FieldEncryptionService.generateKey();

    private static LettuceConnectionFactory factory;
    private static ReactiveStringRedisTemplate sharedRedis;

    public final ReactiveStringRedisTemplate redis;
    public final TestClock clock = TestClock.frozenAt(START);
    public final CapturedMessages captured = new CapturedMessages();
    public final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    public final IdentityChallengeProperties challengeProperties = new IdentityChallengeProperties();
    public final IdentityLimitsProperties limitProperties = new IdentityLimitsProperties();
    public final IdentityProofProperties proofProperties = new IdentityProofProperties();
    public final IdentityLoginHandleProperties handleProperties = new IdentityLoginHandleProperties();
    public final ContactHasher hasher = new ContactHasher("test-contact-hash-key");
    public final RecordingMirror mirror = new RecordingMirror();
    public final ObjectMapper json = new ObjectMapper();
    public final ProofService proofs;
    public final LoginHandleService handles;
    public final LimitService limits;
    public final DeliveryOrchestrator delivery;
    public final ChallengeService challenges;

    public AuthEngine() {
        this(null, null);
    }

    public AuthEngine(List<CodeDeliveryProvider> deliveryProviders, LockMirror lockMirror) {
        this.redis = redis();
        challengeProperties.setHmacPepper("test-challenge-pepper-not-a-secret");
        List<CodeDeliveryProvider> providerList = deliveryProviders != null ? deliveryProviders
                : List.of(new CapturingProvider(captured, clock));
        this.limits = new LimitService(redis, limitProperties, meters);
        this.delivery = new DeliveryOrchestrator(providerList, meters);
        this.proofs = new ProofService(redis, proofProperties);
        this.handles = new LoginHandleService(redis, handleProperties, json);
        ContactCrypto crypto = new ContactCrypto(new FieldEncryptionService(AES_KEY));
        this.challenges = new ChallengeService(redis, challengeProperties, limitProperties, hasher, crypto, limits,
                delivery, lockMirror != null ? lockMirror : mirror, clock, meters);
    }

    private static synchronized ReactiveStringRedisTemplate redis() {
        if (sharedRedis == null) {
            factory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
            factory.afterPropertiesSet();
            sharedRedis = new ReactiveStringRedisTemplate(factory);
        }
        return sharedRedis;
    }

    /** A fresh Zambian mobile number (digits after +26097 are random), in the form a person types. */
    public static String randomPhone() {
        return "+26097" + "%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000));
    }

    public static String randomEmail() {
        return "person." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    public static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10.%d.%d.%d".formatted(r.nextInt(256), r.nextInt(256), r.nextInt(1, 255));
    }

    public ChallengeService.IssueCommand command(String contact) {
        return new ChallengeService.IssueCommand(contact, null, null, randomIp(), null, null);
    }

    public ChallengeService.Issued issue(String contact) {
        return challenges.issue(command(contact)).block();
    }

    /** The code last delivered to the (normalised) recipient. */
    public String codeFor(String normalized) {
        return captured.lastCodeTo(normalized).orElseThrow();
    }

    /** Every key currently in Redis. */
    public List<String> allKeys() {
        return redis.keys("*").collectList().block();
    }

    /** In-memory {@link LockMirror}. */
    public static final class RecordingMirror implements LockMirror {
        public final List<String> contactKeys = new CopyOnWriteArrayList<>();

        @Override
        public Mono<Void> record(String contactKey, Instant lockedUntil, Instant at) {
            contactKeys.add(contactKey);
            return Mono.empty();
        }

        @Override
        public Flux<ActiveLock> active(Instant now) {
            return Flux.empty();
        }
    }

    /** A delivery provider that always fails, as a provider outage does. */
    public static CodeDeliveryProvider failingProvider() {
        return new CodeDeliveryProvider() {
            @Override
            public boolean supports(com.pml.identity.auth.delivery.DeliveryChannel channel) {
                return true;
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public Mono<Void> send(com.pml.identity.auth.delivery.DeliveryChannel channel, String recipient, String code) {
                return Mono.error(new com.pml.identity.auth.delivery.DeliveryFailedException("stub outage"));
            }
        };
    }

    public static <T> List<T> listOf(Flux<T> flux) {
        return new ArrayList<>(flux.collectList().block());
    }
}
