package com.pml.identity.workflow.contactchange;

import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.AccountGuard;
import com.pml.identity.account.AccountStates;
import com.pml.identity.account.ContactChangeKind;
import com.pml.identity.account.ContactChangeMarker;
import com.pml.identity.account.ContactChangeSteps;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.delivery.CapturingProvider;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.config.IdentityContactProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.TemporalTestEnvironments;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflow.ChangeCommand;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The marker a contact change sets on an account, and what happens to one whose change died, on the
 * in-process Temporal server with the real MongoDB steps and a Keycloak that remembers what it was told.
 *
 * <p>A change that is killed from outside (terminated, timed out, lost) cannot clear its own marker, and
 * can leave a contact claimed but never committed and Keycloak's email already written. The properties:
 * a live change keeps refusing a second one; a closed one, or one past its expiry, is reclaimed at the
 * next attempt with the account exactly as it was before the abandoned change; of attempts racing for one
 * leftover exactly one takes it; a marker from before owners were recorded is judged by what the server
 * says is running and by its age; and none of this scans anything or touches sign-in.</p>
 */
@Tag("L3")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001 · a contact-change marker whose change died clears itself at the next attempt")
class ContactChangeMarkerTest {

    private static final String OLD_EMAIL = "old.person@example.com";
    private static final String NEW_EMAIL = "new.person@example.com";
    private static final String OTHER_EMAIL = "other.person@example.com";

    private static final ContactChangeWorkflowTest.MovableClock CLOCK = new ContactChangeWorkflowTest.MovableClock();
    private static AccountFixtures db;

    private TestWorkflowEnvironment env;
    private FakeKeycloak keycloak;
    private ContactChangeWorkflowTest.FaultyActivities activities;
    private ContactChangeProcess process;
    private ContactChangeMarker marker;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures("identity_contact_change_marker", CLOCK);
    }

    @AfterAll
    static void disconnect() {
        db.close();
    }

    @BeforeEach
    void start() {
        db.reset();
        keycloak = new FakeKeycloak();
        IdentityContactProperties properties = new IdentityContactProperties();
        properties.setQuarantine(Duration.ofDays(30));
        DeliveryOrchestrator delivery = new DeliveryOrchestrator(
                List.of(new CapturingProvider(new CapturedMessages(), CLOCK)), new SimpleMeterRegistry());
        ContactChangeSteps steps = new ContactChangeSteps(db.template, db.transaction, db.outbox, db.proofs, keycloak, keycloak,
                AccountFixtures.CRYPTO, delivery, properties, CLOCK, (accountId, trigger, actor) -> reactor.core.publisher.Mono.empty());
        activities = new ContactChangeWorkflowTest.FaultyActivities(new ContactChangeActivitiesImpl(steps));
        env = TemporalTestEnvironments.newInstance();
        Worker worker = env.newWorker(TaskQueues.ACCOUNT);
        worker.registerWorkflowImplementationTypes(ContactChangeWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
        process = new ContactChangeProcess(new TemporalGateway(env.getWorkflowClient()));
        marker = new ContactChangeMarker(steps, process, db.template, CLOCK);
    }

    @AfterEach
    void stop() {
        env.close();
    }

    // ---------------------------------------------------------------------------------- fixtures

    private String account() {
        return account(OLD_EMAIL);
    }

    /** An ACTIVE account with one verified, primary email contact and a Keycloak user holding that email. */
    private String account(String email) {
        String accountId = UUID.randomUUID().toString();
        String keycloakUserId = keycloak.preexisting(accountId);
        keycloak.emails.put(keycloakUserId, email);
        ProofRecord proof = db.proofFor(ContactType.EMAIL, email, "seed-" + accountId);
        Contact contact = Contact.builder().id("c-" + accountId).accountId(accountId).type(ContactType.EMAIL)
                .valueHash(proof.contactKey()).valueEncrypted(proof.valueEncrypted()).valueMasked(proof.valueMasked())
                .verifiedAt(CLOCK.instant()).primary(true).source("OTP").createdAt(CLOCK.instant()).build();
        User user = User.builder().id(accountId).username(accountId).keycloakUserId(keycloakUserId)
                .primaryContactId(contact.getId()).emailVerified(true).createdAt(CLOCK.instant()).build();
        AccountStates.apply(user, AccountState.ACTIVE);
        db.template.insert(user).block();
        db.template.insert(contact).block();
        return accountId;
    }

    private ProofRecord proof(String email) {
        return db.proofFor(ContactType.EMAIL, email, "proof-" + UUID.randomUUID());
    }

    private ChangeCommand change(String accountId, String contactId, ProofRecord target) {
        return new ChangeCommand(UUID.randomUUID().toString(), accountId, ContactChangeKind.CHANGE, contactId, target.type(),
                target.contactKey(), target.valueMasked(), null, "n-ch", "c-ch");
    }

    private User user(String accountId) {
        return db.template.findById(accountId, User.class).block();
    }

    private Document userDocument(String accountId) {
        return db.all("identity_users").stream().filter(d -> accountId.equals(d.getString("_id"))).findFirst().orElseThrow();
    }

    private String runId(String accountId) {
        return env.getWorkflowClient().newUntypedWorkflowStub(WorkflowIds.contactChange(accountId))
                .describe().getExecution().getRunId();
    }

    private void terminate(String accountId) {
        env.getWorkflowClient().newUntypedWorkflowStub(WorkflowIds.contactChange(accountId)).terminate("an operator stopped it");
    }

    private static ErrorCode refusal(Runnable action) {
        try {
            action.run();
        } catch (DomainRefusal refusal) {
            return refusal.errorCode();
        }
        throw new AssertionError("expected a refusal");
    }

    /** Everything about the account that a person could see or that Keycloak holds, without the clock-stamped field. */
    private Map<String, Object> snapshot(String accountId) {
        Map<String, Object> state = new LinkedHashMap<>();
        Document document = new Document(userDocument(accountId));
        document.remove("updatedAt");
        state.put("user", document);
        List<String> contacts = new ArrayList<>();
        for (Document contact : db.all("identity_contacts")) {
            if (accountId.equals(contact.getString("accountId"))) {
                contacts.add(contact.getString("_id") + "|" + contact.getBoolean("primary") + "|" + contact.get("releasedAt")
                        + "|" + contact.getString("valueHash"));
            }
        }
        state.put("contacts", contacts);
        state.put("keycloakEmail", keycloak.emails.get(user(accountId).getKeycloakUserId()));
        return state;
    }

    /**
     * Starts a change of the account's email and takes it past the claim and the Keycloak write, then holds it
     * there (the commit keeps failing), so killing it leaves the worst a dead change can leave. Asserts the
     * leftover exists before anything is said about clearing it.
     */
    private String changeLeftHalfDone(String accountId, String target) {
        ProofRecord proof = proof(target);
        activities.failCommit.set(1_000_000);
        process.start(change(accountId, "c-" + accountId, proof)).block();
        process.awaitOpen(accountId, Duration.ofSeconds(10)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, proof.proofId()).block();
        String keycloakUserId = user(accountId).getKeycloakUserId();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() ->
                user(accountId).getPendingContactId() != null && target.equals(keycloak.emails.get(keycloakUserId)));
        assertThat(db.all("identity_contacts").stream().filter(c -> accountId.equals(c.getString("accountId"))).toList())
                .as("the claimed contact exists: this test would pass against a change that never got that far")
                .hasSize(2);
        return keycloakUserId;
    }

    // ---------------------------------------------------------------------------------- a live change

    @Test
    @DisplayName("a running change records its run and the latest moment it could be open, and a second attempt is refused with the marker untouched")
    void liveChangeKeepsItsMarker() {
        String accountId = account();
        process.start(change(accountId, "c-" + accountId, proof(NEW_EMAIL))).block();
        process.awaitOpen(accountId, Duration.ofSeconds(10)).block();

        User marked = user(accountId);
        assertThat(marked.getPendingKind()).isEqualTo(PendingKind.CHANGING);
        assertThat(marked.getPendingOwnerRunId()).isEqualTo(runId(accountId));
        assertThat(marked.getPendingExpiresAt()).isEqualTo(CLOCK.instant().plus(ContactChangeRules.MAX_LIFETIME));
        assertThat(process.isOpen(accountId, marked.getPendingOwnerRunId()).block()).isTrue();

        assertThat(refusal(() -> marker.release(marked).block())).isEqualTo(ErrorCode.CONTACT_CHANGE_IN_PROGRESS);

        User after = user(accountId);
        assertThat(after.getPendingKind()).isEqualTo(PendingKind.CHANGING);
        assertThat(after.getPendingOwnerRunId()).isEqualTo(marked.getPendingOwnerRunId());
        assertThat(process.status(accountId).block()).isPresent();
    }

    @Test
    @DisplayName("a change that ends in the ordinary way leaves no owner, expiry or claim on the account")
    void completedChangeLeavesNothingOnTheAccount() {
        String accountId = account();
        ProofRecord target = proof(NEW_EMAIL);
        process.start(change(accountId, "c-" + accountId, target)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();

        assertThat(process.await(accountId, Duration.ofMinutes(5)).block().orElseThrow().status()).isEqualTo("COMPLETED");

        Document stored = userDocument(accountId);
        assertThat(stored).doesNotContainKeys("pendingKind", "pendingSince", "pendingOwnerRunId", "pendingExpiresAt", "pendingContactId");
    }

    // ---------------------------------------------------------------------------------- a dead change

    @Test
    @DisplayName("a terminated change is reclaimed at the next attempt: marker gone, claimed contact gone, Keycloak's email back, the account exactly as before")
    void terminatedChangeIsReclaimedWithTheAccountAsBefore() {
        String accountId = account();
        Map<String, Object> before = snapshot(accountId);
        changeLeftHalfDone(accountId, NEW_EMAIL);
        User leftover = user(accountId);
        assertThat(process.isOpen(accountId, leftover.getPendingOwnerRunId()).block()).isTrue();
        terminate(accountId);
        assertThat(process.isOpen(accountId, leftover.getPendingOwnerRunId()).block()).as("the server now says it is closed").isFalse();
        assertThat(process.status(accountId).block()).as("a dead change is not shown as a pending one").isEmpty();

        User released = marker.release(leftover).block();

        assertThat(released.getPendingKind()).isNull();
        assertThat(snapshot(accountId)).isEqualTo(before);
    }

    @Test
    @DisplayName("after a reclaim the account takes a new change and finishes it: the dead change's address is free to be claimed again")
    void accountChangesAgainAfterReclaim() {
        String accountId = account();
        changeLeftHalfDone(accountId, NEW_EMAIL);
        terminate(accountId);
        marker.release(user(accountId)).block();
        activities.failCommit.set(0);

        ProofRecord target = proof(NEW_EMAIL);
        process.start(change(accountId, "c-" + accountId, target)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();

        assertThat(process.await(accountId, Duration.ofMinutes(5)).block().orElseThrow().status()).isEqualTo("COMPLETED");
        List<Document> active = db.all("identity_contacts").stream()
                .filter(c -> accountId.equals(c.getString("accountId")) && c.get("releasedAt") == null).toList();
        assertThat(active).singleElement().satisfies(c -> assertThat(c.getBoolean("primary")).isTrue());
        assertThat(keycloak.emails.get(user(accountId).getKeycloakUserId())).isEqualTo(NEW_EMAIL);
    }

    @Test
    @DisplayName("a marker past its expiry is cleared even if the server still lists the execution open")
    void expiredMarkerIsCleared() {
        String accountId = account();
        Map<String, Object> before = snapshot(accountId);
        process.start(change(accountId, "c-" + accountId, proof(NEW_EMAIL))).block();
        process.awaitOpen(accountId, Duration.ofSeconds(10)).block();
        User marked = user(accountId);
        assertThat(process.isOpen(accountId, marked.getPendingOwnerRunId()).block()).isTrue();

        CLOCK.advance(ContactChangeRules.MAX_LIFETIME);
        try {
            User released = marker.release(marked).block();

            assertThat(released.getPendingKind()).isNull();
            assertThat(snapshot(accountId)).isEqualTo(before);
        } finally {
            CLOCK.advance(ContactChangeRules.MAX_LIFETIME.negated());
        }
    }

    @Test
    @DisplayName("one instant before its expiry a running change is still live")
    void notYetExpiredMarkerIsLive() {
        String accountId = account();
        process.start(change(accountId, "c-" + accountId, proof(NEW_EMAIL))).block();
        process.awaitOpen(accountId, Duration.ofSeconds(10)).block();
        User marked = user(accountId);

        CLOCK.advance(ContactChangeRules.MAX_LIFETIME.minusMillis(1));
        try {
            assertThat(refusal(() -> marker.release(marked).block())).isEqualTo(ErrorCode.CONTACT_CHANGE_IN_PROGRESS);
        } finally {
            CLOCK.advance(ContactChangeRules.MAX_LIFETIME.minusMillis(1).negated());
        }
    }

    @Test
    @DisplayName("of eight attempts racing for one leftover exactly one takes it and the rest are told a change is in progress")
    void exactlyOneAttemptTakesTheLeftover() throws Exception {
        String accountId = account();
        changeLeftHalfDone(accountId, NEW_EMAIL);
        terminate(accountId);
        User leftover = user(accountId);
        assertThat(leftover.getPendingKind()).isEqualTo(PendingKind.CHANGING);

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<ErrorCode>> outcomes = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            outcomes.add(pool.submit(() -> {
                gate.await();
                try {
                    marker.release(leftover).block();
                    return null;
                } catch (DomainRefusal refused) {
                    return refused.errorCode();
                }
            }));
        }
        gate.countDown();
        List<ErrorCode> refusals = new ArrayList<>();
        int took = 0;
        for (Future<ErrorCode> outcome : outcomes) {
            ErrorCode code = outcome.get(60, TimeUnit.SECONDS);
            if (code == null) {
                took++;
            } else {
                refusals.add(code);
            }
        }
        pool.shutdown();

        assertThat(took).as("attempts that took the marker").isEqualTo(1);
        assertThat(refusals).hasSize(attempts - 1).containsOnly(ErrorCode.CONTACT_CHANGE_IN_PROGRESS);
        assertThat(user(accountId).getPendingKind()).isNull();
    }

    // ---------------------------------------------------------------------------------- a marker from before owners

    private void legacyMarker(String accountId, java.time.Instant since) {
        Update update = new Update().set("pendingKind", PendingKind.CHANGING).unset("pendingOwnerRunId")
                .unset("pendingExpiresAt").unset("pendingContactId");
        if (since == null) {
            update.unset("pendingSince");
        } else {
            update.set("pendingSince", since);
        }
        db.template.updateFirst(Query.query(Criteria.where("_id").is(accountId)), update, User.class).block();
    }

    @Test
    @DisplayName("an ownerless marker with nothing running for the account is cleared, with or without a timestamp")
    void legacyMarkerWithNothingRunningIsCleared() {
        String withTime = account("legacy.a@example.com");
        legacyMarker(withTime, CLOCK.instant());
        String withoutTime = account("legacy.b@example.com");
        legacyMarker(withoutTime, null);

        assertThat(marker.release(user(withTime)).block().getPendingKind()).isNull();
        assertThat(marker.release(user(withoutTime)).block().getPendingKind()).isNull();
    }

    @Test
    @DisplayName("an ownerless marker is live while a change of that account is running, and stale once it is older than the longest a change may live")
    void legacyMarkerIsJudgedByTheServerAndByAge() {
        String accountId = account();
        process.start(change(accountId, "c-" + accountId, proof(NEW_EMAIL))).block();
        process.awaitOpen(accountId, Duration.ofSeconds(10)).block();
        legacyMarker(accountId, CLOCK.instant());
        User legacy = user(accountId);
        assertThat(legacy.getPendingOwnerRunId()).isNull();

        assertThat(refusal(() -> marker.release(legacy).block())).isEqualTo(ErrorCode.CONTACT_CHANGE_IN_PROGRESS);

        legacyMarker(accountId, CLOCK.instant().minus(ContactChangeRules.MAX_LIFETIME).minusSeconds(1));
        User old = user(accountId);
        assertThat(marker.release(old).block().getPendingKind()).isNull();
    }

    @Test
    @DisplayName("a change started over a leftover takes the account over: the dead change's claim is removed and the new change completes cleanly")
    void newChangeTakesOverALeftover() {
        String accountId = account();
        changeLeftHalfDone(accountId, NEW_EMAIL);
        terminate(accountId);
        activities.failCommit.set(0);

        ProofRecord target = proof(OTHER_EMAIL);
        process.start(change(accountId, "c-" + accountId, target)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();

        assertThat(process.await(accountId, Duration.ofMinutes(5)).block().orElseThrow().status()).isEqualTo("COMPLETED");
        List<Document> contacts = db.all("identity_contacts").stream().filter(c -> accountId.equals(c.getString("accountId"))).toList();
        assertThat(contacts).as("the dead change's claim is gone: old (released) and the new one").hasSize(2);
        assertThat(contacts.stream().filter(c -> c.get("releasedAt") == null)).singleElement()
                .satisfies(c -> assertThat(c.getString("valueHash")).isEqualTo(target.contactKey()));
        assertThat(keycloak.emails.get(user(accountId).getKeycloakUserId())).isEqualTo(OTHER_EMAIL);
        assertThat(userDocument(accountId)).doesNotContainKeys("pendingKind", "pendingContactId", "pendingOwnerRunId");
    }

    @Test
    @DisplayName("a change that is refused after it claimed its contact removes the claim and puts Keycloak's email back")
    void refusedChangeLeavesTheAccountAsItWas() {
        String accountId = account();
        Map<String, Object> before = snapshot(accountId);
        ProofRecord target = proof(NEW_EMAIL);
        process.start(change(accountId, "no-such-contact", target)).block();
        process.authorise(accountId).block();
        process.acceptNew(accountId, target.proofId()).block();

        assertThat(refusal(() -> process.await(accountId, Duration.ofMinutes(5)).block())).isEqualTo(ErrorCode.CONTACT_UNKNOWN);

        assertThat(snapshot(accountId)).isEqualTo(before);
    }

    // ---------------------------------------------------------------------------------- what it does not touch

    @Test
    @DisplayName("a stuck marker never refuses sign-in: only an account being merged is refused there")
    void aStuckMarkerDoesNotBlockSignIn() {
        String accountId = account();
        legacyMarker(accountId, CLOCK.instant().minus(Duration.ofDays(400)));
        User stuck = user(accountId);

        assertThat(stuck.getPendingKind()).isEqualTo(PendingKind.CHANGING);
        assertThat(AccountGuard.refusal(stuck)).isEmpty();
        assertThat(AccountGuard.isActive(stuck)).isTrue();
    }
}
