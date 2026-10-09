package com.pml.identity.account;

import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.delivery.CapturingProvider;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.config.IdentityAccountRepairProperties;
import com.pml.identity.config.IdentityContactProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.workflow.TemporalTestEnvironments;
import com.pml.identity.workflow.contactchange.ContactChangeActivitiesImpl;
import com.pml.identity.workflow.contactchange.ContactChangeProcess;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflow.ChangeCommand;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflowImpl;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L3")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004-R5 · repair D8 (CHANGING): an orphaned marker past 48 hours is cleared, a live change and a fresh marker are not")
class AccountRepairTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final Clock CLOCK = TestClock.frozenAt(NOW);
    private static AccountFixtures db;

    private TestWorkflowEnvironment env;
    private ContactChangeProcess process;
    private AccountRepair repair;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures("identity_account_repair", CLOCK);
    }

    @AfterAll
    static void disconnect() {
        db.close();
    }

    @BeforeEach
    void start() {
        db.reset();
        FakeKeycloak keycloak = new FakeKeycloak();
        DeliveryOrchestrator delivery = new DeliveryOrchestrator(List.of(new CapturingProvider(new CapturedMessages(), CLOCK)), new SimpleMeterRegistry());
        ContactChangeSteps steps = new ContactChangeSteps(db.template, db.transaction, db.outbox, db.proofs, keycloak, keycloak,
                AccountFixtures.CRYPTO, delivery, new IdentityContactProperties(), CLOCK);
        env = TemporalTestEnvironments.newInstance();
        Worker worker = env.newWorker(TaskQueues.ACCOUNT);
        worker.registerWorkflowImplementationTypes(ContactChangeWorkflowImpl.class);
        worker.registerActivitiesImplementations(new ContactChangeActivitiesImpl(steps));
        env.start();
        process = new ContactChangeProcess(new TemporalGateway(env.getWorkflowClient()));
        repair = new AccountRepair(db.template, process, new IdentityAccountRepairProperties(), keycloak, keycloak,
                new AccountProvisioning(db.template, db.transaction, db.outbox, db.proofs, keycloak, AccountFixtures.CRYPTO, CLOCK),
                AccountFixtures.CRYPTO, new SimpleMeterRegistry(), CLOCK);
    }

    @AfterEach
    void stop() {
        env.close();
    }

    private String account(PendingKind marker, Instant since) {
        String accountId = UUID.randomUUID().toString();
        ProofRecord proof = db.proofFor(ContactType.EMAIL, "repair-" + accountId.substring(0, 8) + "@example.com", "p-" + accountId);
        Contact contact = Contact.builder().id("c-" + accountId).accountId(accountId).type(ContactType.EMAIL).valueHash(proof.contactKey())
                .valueEncrypted(proof.valueEncrypted()).valueMasked(proof.valueMasked()).verifiedAt(NOW).primary(true).createdAt(NOW).build();
        User user = User.builder().id(accountId).username(accountId).pendingKind(marker).pendingSince(since).createdAt(NOW).build();
        AccountStates.apply(user, AccountState.ACTIVE);
        db.template.insert(user).block();
        db.template.insert(contact).block();
        return accountId;
    }

    private PendingKind marker(String accountId) {
        return db.template.findById(accountId, User.class).block().getPendingKind();
    }

    @Test
    @DisplayName("a CHANGING marker older than the maximum age with no open workflow is cleared; a fresh one, a MERGING one and an unmarked account are left")
    void clearsOnlyOrphanedChangingMarkers() {
        String orphan = account(PendingKind.CHANGING, NOW.minus(Duration.ofHours(49)));
        String fresh = account(PendingKind.CHANGING, NOW.minus(Duration.ofHours(1)));
        String merging = account(PendingKind.MERGING, NOW.minus(Duration.ofHours(49)));
        String plain = account(null, null);

        assertThat(repair.clearStaleChanging().block()).isEqualTo(1);

        assertThat(marker(orphan)).isNull();
        assertThat(marker(fresh)).isEqualTo(PendingKind.CHANGING);
        assertThat(marker(merging)).isEqualTo(PendingKind.MERGING);
        assertThat(marker(plain)).isNull();
        assertThat(repair.clearStaleChanging().block()).as("a second run changes nothing").isZero();
    }

    @Test
    @DisplayName("an old marker whose ContactChangeWorkflow is still open is not cleared: the workflow is waiting and a second change must not start")
    void leavesALiveChangeAlone() {
        String accountId = account(null, null);
        ProofRecord target = db.proofFor(ContactType.EMAIL, "target@example.com", "p-target");
        process.start(new ChangeCommand(UUID.randomUUID().toString(), accountId, ContactChangeKind.CHANGE, "c-" + accountId,
                ContactType.EMAIL, target.contactKey(), target.valueMasked(), null, "n", "c")).block();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> marker(accountId) == PendingKind.CHANGING);
        db.template.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().set("pendingSince", NOW.minus(Duration.ofHours(60))), User.class).block();

        assertThat(repair.clearStaleChanging().block()).isZero();

        assertThat(marker(accountId)).isEqualTo(PendingKind.CHANGING);
    }
}
