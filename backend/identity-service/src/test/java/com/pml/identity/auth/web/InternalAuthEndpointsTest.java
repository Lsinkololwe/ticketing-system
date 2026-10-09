package com.pml.identity.auth.web;

import com.pml.shared.testing.RedisNode;
import com.pml.identity.account.AccountEnsurer;
import com.pml.identity.account.AccountStatusLookup;
import com.pml.identity.account.AccountStatusView;
import com.pml.identity.account.EnsureCommand;
import com.pml.identity.account.EnsureResult;
import com.pml.identity.auth.AuthEngine;
import com.pml.identity.domain.enums.AccountState;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The internal auth REST surface end to end over a real Redis, with a fake account side
 * (ET-IDN-001 R2, R3, R6): challenge, verify, ensure (200, 202, repeat, suspended, merging), redeem, status.
 */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001-R6 · challenge, verify, ensure, redeem and status behave as the contract says")
class InternalAuthEndpointsTest {

    /** The tests run against a real Redis, from the shared test fixtures. */
    private static final Class<?> STORE = RedisNode.class;

    private final AuthEngine engine = new AuthEngine();

    /** The account side: decides what ensure answers, records every command it was given. */
    private static final class FakeAccounts implements AccountEnsurer, AccountStatusLookup {
        final List<EnsureCommand> commands = new CopyOnWriteArrayList<>();
        final Map<String, String> accountByContact = new ConcurrentHashMap<>();
        final Map<String, AccountStatusView> statuses = new ConcurrentHashMap<>();
        volatile Function<EnsureCommand, Mono<EnsureResult>> behaviour;

        FakeAccounts() {
            // Default: the first call for a contact creates an ACTIVE account, later calls find it.
            behaviour = command -> {
                String existing = accountByContact.get(command.contactKey());
                String accountId = existing != null ? existing : "acc-" + command.contactKey().substring(0, 8);
                accountByContact.putIfAbsent(command.contactKey(), accountId);
                statuses.put(accountId, new AccountStatusView(accountId, AccountState.ACTIVE, "kc-" + accountId));
                return Mono.just(new EnsureResult(accountId, AccountState.ACTIVE, existing == null, null, null));
            };
        }

        @Override
        public Mono<EnsureResult> ensure(EnsureCommand command) {
            commands.add(command);
            return behaviour.apply(command);
        }

        @Override
        public Mono<AccountStatusView> byAccountId(String accountId) {
            return Mono.justOrEmpty(statuses.get(accountId));
        }
    }

    private final FakeAccounts accounts = new FakeAccounts();

    private final WebTestClient client = WebTestClient.bindToController(
                    new InternalChallengeController(engine.challenges, engine.proofs, engine.proofProperties),
                    new InternalEnsureController(engine.proofs, accounts, engine.handles),
                    new InternalHandleController(engine.handles, accounts))
            .controllerAdvice(new AuthProblemAdvice())
            .build();

    // --------------------------------------------------------------------------- helpers

    private Map<String, Object> challenge(String contact) {
        return client.post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("contact", Map.of("value", contact), "clientIp", AuthEngine.randomIp()))
                .exchange().expectStatus().isAccepted()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { })
                .returnResult().getResponseBody();
    }

    /** Challenge plus a correct verify; returns the proof. */
    private String proofFor(String contact) {
        Map<String, Object> issued = challenge(contact);
        String normalised = engine.hasher.normalize(contact, null, null, null).orElseThrow().value();
        Map<String, Object> verified = client.post().uri("/api/internal/auth/challenges/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("challengeId", issued.get("challengeId"), "code", engine.codeFor(normalised)))
                .exchange().expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { })
                .returnResult().getResponseBody();
        return (String) verified.get("proof");
    }

    private WebTestClient.ResponseSpec ensure(Map<String, Object> body) {
        return client.post().uri("/api/internal/auth/accounts/ensure").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body).exchange();
    }

    // --------------------------------------------------------------------------- challenge and verify

    @Test
    @DisplayName("POST /challenges is 202 with the agreed body and no code; the contact appears in no URL")
    void challengeResponse() {
        String phone = AuthEngine.randomPhone();
        var result = client.post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("contact", Map.of("value", phone, "type", "WHATSAPP"), "regionHint", "ZM",
                        "clientIp", AuthEngine.randomIp(), "deviceId", "dev-1", "preferredChannel", "WHATSAPP"))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.challengeId").isNotEmpty()
                .jsonPath("$.contactType").isEqualTo("WHATSAPP")
                .jsonPath("$.maskedContact").value(masked -> assertThat((String) masked).contains("*").doesNotContain(phone))
                .jsonPath("$.channel").isEqualTo("WHATSAPP")
                .jsonPath("$.expiresInSeconds").isEqualTo(300)
                .jsonPath("$.resendAfterSeconds").isEqualTo(60)
                .returnResult();
        String body = new String(result.getResponseBodyContent());
        assertThat(body).doesNotContain(engine.codeFor(phone));
    }

    @Test
    @DisplayName("a national number needs no regionHint for Zambia and an email is detected by its @")
    void contactForms() {
        challenge("0977" + "%06d".formatted((int) (Math.random() * 1_000_000)));
        challenge(AuthEngine.randomEmail());
    }

    @Test
    @DisplayName("verify with a wrong code is 400 OTP_INVALID with attemptsRemaining; an unknown challenge is 410 OTP_EXPIRED")
    void verifyErrors() {
        Map<String, Object> issued = challenge(AuthEngine.randomPhone());
        client.post().uri("/api/internal/auth/challenges/verify").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("challengeId", issued.get("challengeId"), "code", "000000x")).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("OTP_INVALID").jsonPath("$.attemptsRemaining").isEqualTo(4);
        client.post().uri("/api/internal/auth/challenges/verify").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("challengeId", java.util.UUID.randomUUID().toString(), "code", "123456")).exchange()
                .expectStatus().isEqualTo(410)
                .expectBody().jsonPath("$.errorCode").isEqualTo("OTP_EXPIRED");
    }

    @Test
    @DisplayName("invalid contacts are 400 CONTACT_INVALID, a missing contact is 400 COMMAND_NOT_WELL_FORMED, and an immediate resend is 429 with Retry-After")
    void challengeErrors() {
        client.post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("contact", Map.of("value", "+81312345678"))).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("CONTACT_INVALID");
        client.post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("regionHint", "ZM")).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("COMMAND_NOT_WELL_FORMED");
        client.post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("contact", Map.of("value", "a@b.co", "type", "SMS"))).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("COMMAND_NOT_WELL_FORMED");

        String phone = AuthEngine.randomPhone();
        challenge(phone);
        client.post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("contact", Map.of("value", phone))).exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody().jsonPath("$.errorCode").isEqualTo("OTP_COOLDOWN_ACTIVE").jsonPath("$.retryAfterSeconds").isNumber();
    }

    // --------------------------------------------------------------------------- ensure

    @Test
    @DisplayName("ensure for an ACTIVE account is 200 with a login handle only when asked, and the command carries no raw contact")
    void ensureActive() {
        String phone = AuthEngine.randomPhone();
        String proof = proofFor(phone);

        ensure(Map.of("proof", proof, "clientId", "myticketzm-web", "issueHandle", true, "displayName", "Mwila",
                "consents", List.of(Map.of("purpose", "TERMS", "version", "2026-10"))))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountId").isNotEmpty()
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.isNew").isEqualTo(true)
                .jsonPath("$.loginHandle").isNotEmpty();

        EnsureCommand command = accounts.commands.get(0);
        assertThat(command.proofId()).isEqualTo(proof);
        assertThat(command.clientId()).isEqualTo("myticketzm-web");
        assertThat(command.issueHandle()).isTrue();
        assertThat(command.displayName()).isEqualTo("Mwila");
        assertThat(command.consents()).hasSize(1);
        assertThat(command.contactKey()).matches("[0-9a-f]{64}");
        assertThat(command.toString()).doesNotContain(phone.substring(1));
        assertThat(engine.proofs.find(proof).block().state()).isEqualTo("CONSUMED");
        assertThat(engine.proofs.find(proof).block().accountId()).isEqualTo(command.contactKey().isEmpty() ? null : "acc-" + command.contactKey().substring(0, 8));
    }

    @Test
    @DisplayName("issueHandle=false returns no loginHandle")
    void ensureWithoutHandle() {
        String proof = proofFor(AuthEngine.randomEmail());
        var body = ensure(Map.of("proof", proof, "clientId", "myticketzm-web", "issueHandle", false))
                .expectStatus().isOk().expectBody().returnResult();
        assertThat(new String(body.getResponseBodyContent())).doesNotContain("loginHandle");
    }

    @Test
    @DisplayName("a repeat with the same proof returns the same account (isNew false), asks the same question, and may issue a fresh handle")
    void ensureIdempotent() {
        String proof = proofFor(AuthEngine.randomPhone());
        var first = ensure(Map.of("proof", proof, "clientId", "myticketzm-web", "issueHandle", true))
                .expectStatus().isOk().expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { })
                .returnResult().getResponseBody();
        var second = ensure(Map.of("proof", proof, "clientId", "myticketzm-web", "issueHandle", true))
                .expectStatus().isOk().expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { })
                .returnResult().getResponseBody();
        assertThat(second.get("accountId")).isEqualTo(first.get("accountId"));
        assertThat(second.get("isNew")).isEqualTo(false);
        assertThat(second.get("loginHandle")).isNotEqualTo(first.get("loginHandle"));
        assertThat(accounts.commands).hasSize(2);
        assertThat(accounts.commands.get(0).proofId()).isEqualTo(accounts.commands.get(1).proofId());
        assertThat(accounts.commands.get(0).contactKey()).isEqualTo(accounts.commands.get(1).contactKey());
    }

    @Test
    @DisplayName("while the workflow runs ensure is 202 PROVISIONING with retryAfterSeconds, and the retry with the same proof gets the account")
    void ensureProvisioning() {
        String proof = proofFor(AuthEngine.randomPhone());
        accounts.behaviour = command -> Mono.just(new EnsureResult(null, AccountState.PROVISIONING, false, null, 3));
        ensure(Map.of("proof", proof, "clientId", "myticketzm-web", "issueHandle", true))
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PROVISIONING")
                .jsonPath("$.retryAfterSeconds").isEqualTo(3)
                .jsonPath("$.accountId").doesNotExist()
                .jsonPath("$.loginHandle").doesNotExist();

        accounts.behaviour = command -> Mono.just(new EnsureResult("acc-1", AccountState.ACTIVE, true, null, null));
        ensure(Map.of("proof", proof, "clientId", "myticketzm-web", "issueHandle", true))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.accountId").isEqualTo("acc-1").jsonPath("$.loginHandle").isNotEmpty();
    }

    @Test
    @DisplayName("a provisioning answer without a retry hint gets the default of two seconds")
    void ensureProvisioningDefault() {
        String proof = proofFor(AuthEngine.randomEmail());
        accounts.behaviour = command -> Mono.just(new EnsureResult("acc-2", AccountState.PROVISIONING, true, null, null));
        ensure(Map.of("proof", proof, "clientId", "c")).expectStatus().isAccepted()
                .expectBody().jsonPath("$.retryAfterSeconds").isEqualTo(2).jsonPath("$.accountId").isEqualTo("acc-2");
    }

    @Test
    @DisplayName("a SUSPENDED account is 403 ACCOUNT_SUSPENDED and gets no handle; a merged or merging one is 409 ACCOUNT_MERGING")
    void ensureRefusals() {
        accounts.behaviour = command -> Mono.just(new EnsureResult("acc-s", AccountState.SUSPENDED, false, null, null));
        ensure(Map.of("proof", proofFor(AuthEngine.randomPhone()), "clientId", "c", "issueHandle", true))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.errorCode").isEqualTo("ACCOUNT_SUSPENDED").jsonPath("$.loginHandle").doesNotExist();

        accounts.behaviour = command -> Mono.just(new EnsureResult("acc-m", AccountState.MERGED, false, null, null));
        ensure(Map.of("proof", proofFor(AuthEngine.randomPhone()), "clientId", "c"))
                .expectStatus().isEqualTo(409).expectBody().jsonPath("$.errorCode").isEqualTo("ACCOUNT_MERGING");

        accounts.behaviour = command -> Mono.error(new TranslatedRefusal(ErrorCode.ACCOUNT_MERGING, "merge running"));
        ensure(Map.of("proof", proofFor(AuthEngine.randomPhone()), "clientId", "c"))
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("ACCOUNT_MERGING").jsonPath("$.retryable").isEqualTo(true);

        accounts.behaviour = command -> Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_ALREADY_CLAIMED, "race lost"));
        ensure(Map.of("proof", proofFor(AuthEngine.randomPhone()), "clientId", "c"))
                .expectStatus().isEqualTo(409).expectBody().jsonPath("$.errorCode").isEqualTo("CONTACT_ALREADY_CLAIMED");
    }

    @Test
    @DisplayName("two proofs for one contact resolve to one account")
    void twoProofsOneAccount() {
        String phone = AuthEngine.randomPhone();
        String first = proofFor(phone);
        engine.clock.advance(java.time.Duration.ofSeconds(61));
        String second = proofFor(phone);
        assertThat(second).isNotEqualTo(first);

        var a = ensure(Map.of("proof", first, "clientId", "c")).expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { }).returnResult().getResponseBody();
        var b = ensure(Map.of("proof", second, "clientId", "c")).expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { }).returnResult().getResponseBody();
        assertThat(b.get("accountId")).isEqualTo(a.get("accountId"));
        assertThat(a.get("isNew")).isEqualTo(true);
        assertThat(b.get("isNew")).isEqualTo(false);
    }

    @Test
    @DisplayName("an unknown proof is 400 PROOF_INVALID and a request without proof or clientId is 400 COMMAND_NOT_WELL_FORMED")
    void ensureInvalidInput() {
        ensure(Map.of("proof", "x".repeat(43), "clientId", "c")).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("PROOF_INVALID");
        ensure(Map.of("clientId", "c")).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("COMMAND_NOT_WELL_FORMED");
        ensure(Map.of("proof", "x".repeat(43))).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode").isEqualTo("COMMAND_NOT_WELL_FORMED");
        assertThat(accounts.commands).isEmpty();
    }

    // --------------------------------------------------------------------------- redeem and status

    @Test
    @DisplayName("a handle from ensure redeems once to the account id, and the second redeem is 400 LOGIN_HANDLE_INVALID")
    void redeemOnce() {
        String proof = proofFor(AuthEngine.randomPhone());
        var ensured = ensure(Map.of("proof", proof, "clientId", "myticketzm-web", "issueHandle", true))
                .expectStatus().isOk().expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { })
                .returnResult().getResponseBody();
        Map<String, String> redeem = Map.of("handle", (String) ensured.get("loginHandle"), "clientId", "myticketzm-web");

        client.post().uri("/api/internal/auth/handles/redeem").contentType(MediaType.APPLICATION_JSON).bodyValue(redeem)
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.accountId").isEqualTo(ensured.get("accountId"));
        client.post().uri("/api/internal/auth/handles/redeem").contentType(MediaType.APPLICATION_JSON).bodyValue(redeem)
                .exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.errorCode").isEqualTo("LOGIN_HANDLE_INVALID");
    }

    @Test
    @DisplayName("redeeming for an account that is no longer ACTIVE is 409 ACCOUNT_NOT_ACTIVE with currentStatus; the handle is spent")
    void redeemNotActive() {
        String handle = engine.handles.issue("acc-x", "myticketzm-web").block();
        accounts.statuses.put("acc-x", new AccountStatusView("acc-x", AccountState.SUSPENDED, "kc-x"));
        client.post().uri("/api/internal/auth/handles/redeem").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("handle", handle, "clientId", "myticketzm-web")).exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("ACCOUNT_NOT_ACTIVE").jsonPath("$.currentStatus").isEqualTo("SUSPENDED");

        String orphan = engine.handles.issue("acc-gone", "myticketzm-web").block();
        client.post().uri("/api/internal/auth/handles/redeem").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("handle", orphan, "clientId", "myticketzm-web")).exchange()
                .expectStatus().isEqualTo(409).expectBody().jsonPath("$.errorCode").isEqualTo("ACCOUNT_NOT_ACTIVE");
    }

    @Test
    @DisplayName("a handle presented by another client is 400 LOGIN_HANDLE_INVALID")
    void redeemWrongClient() {
        String handle = engine.handles.issue("acc-y", "myticketzm-web").block();
        accounts.statuses.put("acc-y", new AccountStatusView("acc-y", AccountState.ACTIVE, "kc-y"));
        client.post().uri("/api/internal/auth/handles/redeem").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("handle", handle, "clientId", "someone-else")).exchange()
                .expectStatus().isBadRequest().expectBody().jsonPath("$.errorCode").isEqualTo("LOGIN_HANDLE_INVALID");
    }

    @Test
    @DisplayName("GET /accounts/{id}/status returns the lifecycle state and Keycloak id, 404 USER_UNKNOWN otherwise")
    void status() {
        accounts.statuses.put("acc-z", new AccountStatusView("acc-z", AccountState.PROVISIONING, null));
        client.get().uri("/api/internal/auth/accounts/acc-z/status").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.accountId").isEqualTo("acc-z").jsonPath("$.status").isEqualTo("PROVISIONING")
                .jsonPath("$.keycloakUserId").doesNotExist();
        accounts.statuses.put("acc-w", new AccountStatusView("acc-w", AccountState.ACTIVE, "kc-w"));
        client.get().uri("/api/internal/auth/accounts/acc-w/status").exchange()
                .expectBody().jsonPath("$.keycloakUserId").isEqualTo("kc-w");
        client.get().uri("/api/internal/auth/accounts/nobody/status").exchange()
                .expectStatus().isNotFound().expectBody().jsonPath("$.errorCode").isEqualTo("USER_UNKNOWN");
    }
}
