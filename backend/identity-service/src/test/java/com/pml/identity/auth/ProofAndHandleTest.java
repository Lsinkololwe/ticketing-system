package com.pml.identity.auth;

import com.pml.shared.testing.RedisNode;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.auth.proof.LoginHandleService;
import com.pml.identity.auth.proof.ProofService;
import com.pml.identity.domain.enums.ContactType;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The proof and the login handle are short, bound and single-use (ET-IDN-001 R6, CONTRACT 6). */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001-R6 · a proof authorises one ensure; a login handle authorises one sign-in")
class ProofAndHandleTest {

    /** The tests run against a real Redis, from the shared test fixtures. */
    private static final Class<?> STORE = RedisNode.class;

    private final AuthEngine engine = new AuthEngine();
    private final ProofService proofs = engine.proofs;
    private final LoginHandleService handles = engine.handles;

    private String newProof() {
        return proofs.create("k".repeat(64), ContactType.EMAIL, "v1.ciphertext", "j***@example.com").block();
    }

    @Test
    @DisplayName("a proof is 256 random bits, bound to the contact, and expires after two minutes while NEW")
    void proofShapeAndTtl() {
        String a = newProof();
        String b = newProof();
        assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(b);

        ProofRecord record = proofs.find(a).block();
        assertThat(record.contactKey()).isEqualTo("k".repeat(64));
        assertThat(record.type()).isEqualTo(ContactType.EMAIL);
        assertThat(record.valueEncrypted()).isEqualTo("v1.ciphertext");
        assertThat(record.valueMasked()).isEqualTo("j***@example.com");
        assertThat(record.state()).isEqualTo("NEW");
        assertThat(record.accountId()).isNull();
        assertThat(engine.redis.getExpire("proof:" + a).block()).isBetween(Duration.ofSeconds(110), Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("consuming a proof extends it to the ensure hold once, and repeating the consume is idempotent")
    void consumeExtendsOnce() {
        String proof = newProof();
        ProofRecord consumed = proofs.markConsumed(proof).block();
        assertThat(consumed.state()).isEqualTo("CONSUMED");
        Duration afterFirst = engine.redis.getExpire("proof:" + proof).block();
        assertThat(afterFirst).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));

        engine.redis.expire("proof:" + proof, Duration.ofMinutes(10)).block();
        assertThat(proofs.markConsumed(proof).block().state()).isEqualTo("CONSUMED");
        assertThat(engine.redis.getExpire("proof:" + proof).block()).isLessThanOrEqualTo(Duration.ofMinutes(10));

        proofs.recordAccount(proof, "account-9").block();
        assertThat(proofs.find(proof).block().accountId()).isEqualTo("account-9");
        assertThat(engine.redis.getExpire("proof:" + proof).block()).isLessThanOrEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("unknown, expired and malformed proofs are PROOF_INVALID")
    void invalidProofs() {
        Refusals.of(ErrorCode.PROOF_INVALID, proofs.markConsumed("x".repeat(43)));
        Refusals.of(ErrorCode.PROOF_INVALID, proofs.markConsumed("short"));
        Refusals.of(ErrorCode.PROOF_INVALID, proofs.markConsumed(null));
        Refusals.of(ErrorCode.PROOF_INVALID, proofs.markConsumed("../../etc:passwd" + "a".repeat(20)));

        String proof = newProof();
        engine.redis.delete("proof:" + proof).block(); // as if the two minutes had run out
        Refusals.of(ErrorCode.PROOF_INVALID, proofs.markConsumed(proof));
        assertThat(proofs.find(proof).block()).isNull();
    }

    @Test
    @DisplayName("a handle is bound to the account and client, lives 60 s, and redeems exactly once")
    void handleSingleUse() {
        String handle = handles.issue("account-1", "myticketzm-web").block();
        assertThat(handle).hasSize(43);
        assertThat(engine.redis.getExpire("handle:" + handle).block()).isBetween(Duration.ofSeconds(55), Duration.ofSeconds(60));

        var redeemed = handles.redeem(handle, "myticketzm-web").block();
        assertThat(redeemed.accountId()).isEqualTo("account-1");
        assertThat(redeemed.clientId()).isEqualTo("myticketzm-web");
        Refusals.of(ErrorCode.LOGIN_HANDLE_INVALID, handles.redeem(handle, "myticketzm-web"));
    }

    @Test
    @DisplayName("a handle redeemed by another client is refused and is gone for everyone")
    void handleClientMismatch() {
        String handle = handles.issue("account-2", "myticketzm-web").block();
        Refusals.of(ErrorCode.LOGIN_HANDLE_INVALID, handles.redeem(handle, "other-client"));
        Refusals.of(ErrorCode.LOGIN_HANDLE_INVALID, handles.redeem(handle, "myticketzm-web"));
    }

    @Test
    @DisplayName("an expired or malformed handle is LOGIN_HANDLE_INVALID")
    void handleExpired() {
        String handle = handles.issue("account-3", "myticketzm-web").block();
        engine.redis.delete("handle:" + handle).block();
        Refusals.of(ErrorCode.LOGIN_HANDLE_INVALID, handles.redeem(handle, "myticketzm-web"));
        Refusals.of(ErrorCode.LOGIN_HANDLE_INVALID, handles.redeem("nope", "myticketzm-web"));
        Refusals.of(ErrorCode.LOGIN_HANDLE_INVALID, handles.redeem(null, null));
    }

    @Test
    @DisplayName("of 20 parallel redemptions exactly one wins")
    void parallelRedeem() {
        String handle = handles.issue("account-4", "myticketzm-web").block();
        AtomicInteger wins = new AtomicInteger();
        Flux.range(0, 20)
                .flatMap(i -> handles.redeem(handle, "myticketzm-web")
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(r -> wins.incrementAndGet())
                        .onErrorResume(e -> reactor.core.publisher.Mono.empty()), 20)
                .blockLast();
        assertThat(wins.get()).isEqualTo(1);
    }
}
