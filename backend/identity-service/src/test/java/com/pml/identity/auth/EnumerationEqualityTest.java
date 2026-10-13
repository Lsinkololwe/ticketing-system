package com.pml.identity.auth;

import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.AccountProvisioning;
import com.pml.identity.account.AccountService;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.auth.challenge.ChallengeService;
import com.pml.identity.auth.web.AuthProblemAdvice;
import com.pml.identity.auth.web.InternalChallengeController;
import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.security.revocation.MongoRevocationStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asking for a code for a contact that already has an account looks exactly like asking for one that
 * has none, so the request form cannot be used to find out who is registered. The challenge engine
 * has no way to ask whether an account exists; these tests prove the status, the body shape and the
 * latency band against real accounts, and pin the absence of that dependency.
 */
@Tag("L2")
@Tag("ET-IDN-001")
@DisplayName("a code request for a registered contact and one for an unknown contact are indistinguishable")
class EnumerationEqualityTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private static AccountFixtures db;

    /** The tests run against a real Redis, from the shared test fixtures. */
    private static final Class<?> STORE = RedisNode.class;

    private final AuthEngine engine = new AuthEngine();
    private final FakeKeycloak keycloak = new FakeKeycloak();

    @BeforeAll
    static void connect() {
        db = new AccountFixtures(MongoReplicaSet.connectionString(), "identity_enumeration", TestClock.frozenAt(NOW));
    }

    @AfterAll
    static void disconnect() {
        db.close();
    }

    private final WebTestClient client = WebTestClient.bindToController(
                    new InternalChallengeController(engine.challenges, engine.proofs, engine.proofProperties))
            .controllerAdvice(new AuthProblemAdvice())
            .build();

    /** An ACTIVE account that holds one verified WhatsApp contact and one verified email contact. */
    private record Registered(String accountId, String phone, String email) {
    }

    private Registered registerActiveAccount() {
        AccountProvisioning provisioning = new AccountProvisioning(db.template, db.transaction, db.outbox, db.proofs,
                keycloak, AccountFixtures.CRYPTO, engine.clock);
        String phone = AuthEngine.randomPhone();
        String email = AuthEngine.randomEmail();
        ContactHasher.Normalized phoneContact = engine.hasher.normalize(phone, ContactType.WHATSAPP, null, null).orElseThrow();
        ContactHasher.Normalized emailContact = engine.hasher.normalize(email, ContactType.EMAIL, null, null).orElseThrow();
        String proofId = "proof-" + UUID.randomUUID();
        db.proofs.put(new ProofRecord(proofId, phoneContact.key(), ContactType.WHATSAPP,
                AccountFixtures.CRYPTO.encrypt(phoneContact.value()).block(), phoneContact.masked(), "CONSUMED", null));
        String accountId = provisioning.claim(proofId, phoneContact.key(), ContactType.WHATSAPP).block().accountId();
        String keycloakUserId = provisioning.createKeycloakUser(accountId).block();
        provisioning.activate(accountId, keycloakUserId, "web", null, List.of()).block();
        db.template.insert(Contact.builder().id(UUID.randomUUID().toString()).accountId(accountId).type(ContactType.EMAIL)
                .valueHash(emailContact.key()).valueEncrypted(AccountFixtures.CRYPTO.encrypt(emailContact.value()).block())
                .valueMasked(emailContact.masked()).verifiedAt(engine.clock.instant()).primary(false).source("OTP")
                .createdAt(engine.clock.instant()).build()).block();
        assertThat(db.template.findById(accountId, User.class).block().getStatus()).isEqualTo(AccountState.ACTIVE);
        return new Registered(accountId, phone, email);
    }

    private Map<String, Object> challengeBody(String contact, int expectedStatus) {
        return client.post().uri("/api/internal/auth/challenges").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("contact", Map.of("value", contact), "clientIp", AuthEngine.randomIp()))
                .exchange().expectStatus().isEqualTo(expectedStatus)
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() { })
                .returnResult().getResponseBody();
    }

    /** Everything about a response that is not specific to one contact. */
    private static Map<String, Object> comparable(Map<String, Object> body) {
        Map<String, Object> copy = new TreeMap<>(body);
        copy.remove("challengeId");
        copy.remove("maskedContact");
        return copy;
    }

    private static long medianMillis(List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2) / 1_000_000;
    }

    @Test
    @DisplayName("the challenge service takes no account, user or contact repository")
    void noAccountLookup() {
        for (Constructor<?> constructor : ChallengeService.class.getConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertThat(parameter.getName().toLowerCase()).doesNotContain("repository", "account", "userservice");
            }
        }
    }

    @Test
    @DisplayName("same response shape and a latency within the same band for phone and email, known and unknown")
    void sameShapeSameBand() {
        // "Known" contacts have already completed a sign-in (proved and consumed), as a registered buyer has.
        List<String> known = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String contact = i % 2 == 0 ? AuthEngine.randomPhone() : AuthEngine.randomEmail();
            var issued = engine.issue(contact);
            var verified = engine.challenges.verify(issued.challengeId(), engine.codeFor(normalised(contact))).block();
            engine.proofs.create(verified.contactKey(), verified.type(), verified.valueEncrypted(), verified.valueMasked()).block();
            known.add(contact);
            engine.clock.advance(Duration.ofSeconds(61));
            unknown.add(i % 2 == 0 ? AuthEngine.randomPhone() : AuthEngine.randomEmail());
        }

        List<Long> knownNanos = new ArrayList<>();
        List<Long> unknownNanos = new ArrayList<>();
        TreeSet<String> knownShapes = new TreeSet<>();
        TreeSet<String> unknownShapes = new TreeSet<>();
        for (int i = 0; i < known.size(); i++) {
            long t0 = System.nanoTime();
            var a = engine.challenges.issue(engine.command(known.get(i))).block();
            knownNanos.add(System.nanoTime() - t0);
            long t1 = System.nanoTime();
            var b = engine.challenges.issue(engine.command(unknown.get(i))).block();
            unknownNanos.add(System.nanoTime() - t1);
            knownShapes.add(shape(a));
            unknownShapes.add(shape(b));
        }

        assertThat(knownShapes).as("same status/shape per contact type").isEqualTo(unknownShapes);
        Collections.sort(knownNanos);
        Collections.sort(unknownNanos);
        long medianKnown = knownNanos.get(knownNanos.size() / 2) / 1_000_000;
        long medianUnknown = unknownNanos.get(unknownNanos.size() / 2) / 1_000_000;
        assertThat(Math.abs(medianKnown - medianUnknown)).as("median latency difference in ms").isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("a registered WhatsApp number and an unregistered one get the same status and the same body keys and values")
    void registeredAndUnregisteredPhoneAreIdentical() {
        Registered known = registerActiveAccount();

        Map<String, Object> registered = challengeBody(known.phone(), 202);
        Map<String, Object> unregistered = challengeBody(AuthEngine.randomPhone(), 202);

        assertThat(registered.keySet()).containsExactlyInAnyOrderElementsOf(unregistered.keySet());
        assertThat(comparable(registered)).isEqualTo(comparable(unregistered));
        assertThat(registered.get("contactType")).isEqualTo("WHATSAPP");
    }

    @Test
    @DisplayName("a registered email and an unregistered one get the same status and the same body keys and values")
    void registeredAndUnregisteredEmailAreIdentical() {
        Registered known = registerActiveAccount();

        Map<String, Object> registered = challengeBody(known.email(), 202);
        Map<String, Object> unregistered = challengeBody(AuthEngine.randomEmail(), 202);

        assertThat(registered.keySet()).containsExactlyInAnyOrderElementsOf(unregistered.keySet());
        assertThat(comparable(registered)).isEqualTo(comparable(unregistered));
        assertThat(registered.get("contactType")).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("registered and unregistered contacts, phone and email, answer within the same latency band")
    void registeredAndUnregisteredLatencyBand() {
        List<Registered> accounts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            accounts.add(registerActiveAccount());
        }
        List<Long> registeredNanos = new ArrayList<>();
        List<Long> unregisteredNanos = new ArrayList<>();
        for (Registered account : accounts) {
            for (boolean phone : new boolean[]{true, false}) {
                long start = System.nanoTime();
                challengeBody(phone ? account.phone() : account.email(), 202);
                registeredNanos.add(System.nanoTime() - start);
                start = System.nanoTime();
                challengeBody(phone ? AuthEngine.randomPhone() : AuthEngine.randomEmail(), 202);
                unregisteredNanos.add(System.nanoTime() - start);
            }
        }

        assertThat(Math.abs(medianMillis(registeredNanos) - medianMillis(unregisteredNanos)))
                .as("median latency difference in ms").isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("the contact of a suspended account gets the same answer as an unknown contact, so suspension is not revealed")
    void suspendedAccountContactAnswersLikeAnyOther() {
        Registered known = registerActiveAccount();
        org.springframework.beans.factory.support.DefaultListableBeanFactory beans =
                new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        AccountService accounts = new AccountService(db.template, db.transaction, db.outbox, keycloak,
                beans.getBeanProvider(MongoRevocationStore.class), new KeycloakProperties(), engine.clock);
        accounts.suspend(known.accountId(), "chargeback abuse", "admin-1").block();
        assertThat(db.template.findById(known.accountId(), User.class).block().getStatus())
                .isEqualTo(AccountState.SUSPENDED);

        Map<String, Object> suspendedPhone = challengeBody(known.phone(), 202);
        Map<String, Object> suspendedEmail = challengeBody(known.email(), 202);
        Map<String, Object> unknownPhone = challengeBody(AuthEngine.randomPhone(), 202);
        Map<String, Object> unknownEmail = challengeBody(AuthEngine.randomEmail(), 202);

        assertThat(comparable(suspendedPhone)).isEqualTo(comparable(unknownPhone));
        assertThat(comparable(suspendedEmail)).isEqualTo(comparable(unknownEmail));
        assertThat(suspendedPhone.keySet()).containsExactlyInAnyOrderElementsOf(unknownPhone.keySet());
        assertThat(engine.captured.countTo(known.phone())).as("a code really was sent").isEqualTo(1);
    }

    private String normalised(String contact) {
        return engine.hasher.normalize(contact, null, null, null).orElseThrow().value();
    }

    private String shape(ChallengeService.Issued issued) {
        JsonNode node = engine.json.valueToTree(issued);
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return issued.contactType() + ":" + issued.channel() + ":" + issued.expiresInSeconds() + ":"
                + issued.resendAfterSeconds() + ":" + new TreeSet<>(fields);
    }
}
