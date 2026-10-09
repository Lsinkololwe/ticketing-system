package com.pml.identity.workflow.ensure;

import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.AccountProvisioning;
import com.pml.identity.account.ConsentGrant;
import com.pml.identity.account.EnsureCommand;
import com.pml.identity.account.EnsureResult;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.TemporalTestEnvironments;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.testing.TestClock;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateException;
import io.temporal.client.WorkflowUpdateStage;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The account-ensure process end to end on the in-process Temporal server, with the real
 * MongoDB steps and a Keycloak that can be down.
 *
 * <p>The properties: one account per contact however many sign-ins race; an account the process
 * could not finish is completed forward, never deleted; a wait that ends first answers
 * PROVISIONING and the workflow carries on; a refusal reaches the caller with its code; and the
 * history replays.
 */
@Tag("L3")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · AccountEnsureWorkflow finds or creates exactly one account per contact and completes forward")
class AccountEnsureWorkflowTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final Clock CLOCK = TestClock.frozenAt(NOW);
    private static final String PHONE = "+260971234567";

    private static AccountFixtures db;

    private TestWorkflowEnvironment env;
    private FakeKeycloak keycloak;
    private AccountProvisioning provisioning;
    private FaultyActivities activities;
    private AccountEnsureProcess process;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures("identity_ensure_workflow", CLOCK);
    }

    @AfterAll
    static void disconnect() {
        db.close();
    }

    @BeforeEach
    void start() {
        db.reset();
        keycloak = new FakeKeycloak();
        provisioning = new AccountProvisioning(db.template, db.transaction, db.outbox, db.proofs, keycloak,
                AccountFixtures.CRYPTO, CLOCK);
        activities = new FaultyActivities(new AccountEnsureActivitiesImpl(provisioning));
        env = TemporalTestEnvironments.newInstance();
        Worker worker = env.newWorker(TaskQueues.ACCOUNT);
        worker.registerWorkflowImplementationTypes(AccountEnsureWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
        process = new AccountEnsureProcess(new TemporalGateway(env.getWorkflowClient()), db.template, Duration.ofSeconds(20));
    }

    @AfterEach
    void stop() {
        env.close();
    }

    private EnsureCommand command(ProofRecord proof, String proofId) {
        return new EnsureCommand(proofId, proof.contactKey(), proof.type(), "myticketzm-web", false, "Chanda",
                List.of(new ConsentGrant("TERMS", "2026-10")));
    }

    private ProofRecord whatsapp(String proofId) {
        return db.proofFor(ContactType.WHATSAPP, PHONE, proofId);
    }

    private User only() {
        return db.template.findAll(User.class).collectList().block().get(0);
    }

    @Test
    @DisplayName("a new contact: one ACTIVE account, a Keycloak user named by the account id with the CUSTOMER role, consents and an outbox row")
    void happyPath() {
        ProofRecord proof = whatsapp("proof-1");

        EnsureResult result = process.ensure(command(proof, "proof-1")).block();

        assertThat(result.status()).isEqualTo(AccountState.ACTIVE);
        assertThat(result.isNew()).isTrue();
        assertThat(result.loginHandle()).as("the endpoint issues handles, never the workflow").isNull();
        User account = only();
        assertThat(account.getId()).isEqualTo(result.accountId());
        assertThat(account.getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(account.getKeycloakUserId()).isEqualTo(keycloak.user(account.getId()).id());
        assertThat(keycloak.user(account.getId()).roles()).containsExactly("CUSTOMER");
        assertThat(db.count("identity_consents")).isEqualTo(1);
        assertThat(db.count(AccountFixtures.OUTBOX)).isEqualTo(1);
    }

    @Test
    @DisplayName("a known contact with an ACTIVE account is answered from the database: no workflow, no Keycloak call")
    void fastPath() {
        ProofRecord proof = whatsapp("proof-1");
        EnsureResult first = process.ensure(command(proof, "proof-1")).block();
        keycloak.calls.clear();
        int claims = activities.claims.get();

        EnsureResult again = process.ensure(command(proof, "proof-2")).block();

        assertThat(again.accountId()).isEqualTo(first.accountId());
        assertThat(again.status()).isEqualTo(AccountState.ACTIVE);
        assertThat(again.isNew()).isFalse();
        assertThat(activities.claims.get()).as("no activity ran").isEqualTo(claims);
        assertThat(keycloak.calls).isEmpty();
    }

    @Test
    @DisplayName("Keycloak already has the user from an earlier attempt: it is adopted by username and nothing is created twice")
    void keycloakAlreadyHasTheUser() {
        ProofRecord proof = whatsapp("proof-1");
        // a crashed run claimed the contact and made the Keycloak user, then died
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        String leftBehind = keycloak.preexisting(accountId);

        EnsureResult result = process.ensure(command(proof, "proof-1")).block();

        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.status()).isEqualTo(AccountState.ACTIVE);
        assertThat(keycloak.creates.get()).isZero();
        assertThat(only().getKeycloakUserId()).isEqualTo(leftBehind);
        assertThat(db.count("identity_users")).isEqualTo(1);
    }

    @Test
    @DisplayName("Keycloak down then up: the caller is told PROVISIONING, the workflow keeps retrying, and the account is ACTIVE once Keycloak returns")
    void keycloakDownThenUp() {
        ProofRecord proof = whatsapp("proof-1");
        keycloak.down = true;
        AccountEnsureProcess impatient = new AccountEnsureProcess(new TemporalGateway(env.getWorkflowClient()),
                db.template, Duration.ofMillis(700));

        EnsureResult waiting = impatient.ensure(command(proof, "proof-1")).block();

        assertThat(waiting.status()).isEqualTo(AccountState.PROVISIONING);
        assertThat(waiting.retryAfterSeconds()).isEqualTo(2);
        assertThat(only().getStatus()).as("claimed, not finished, and not deleted").isEqualTo(AccountState.PROVISIONING);

        keycloak.down = false;
        env.sleep(Duration.ofMinutes(3));

        assertThat(only().getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(db.count("identity_users")).isEqualTo(1);
        // asking again with the same proof reaches the account without starting anything new
        assertThat(process.ensure(command(proof, "proof-1")).block().status()).isEqualTo(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("a crash right after the claim: the retry finds the account PROVISIONING and finishes it, creating no second account")
    void crashAfterClaim() {
        ProofRecord proof = whatsapp("proof-1");
        activities.failAfterClaim.set(2);

        EnsureResult result = process.ensure(command(proof, "proof-1")).block();

        assertThat(result.status()).isEqualTo(AccountState.ACTIVE);
        assertThat(db.count("identity_users")).isEqualTo(1);
        assertThat(db.count("identity_contacts")).isEqualTo(1);
        assertThat(activities.claims.get()).as("the claim itself was not repeated").isEqualTo(1);
    }

    @Test
    @DisplayName("a run that died holding a PROVISIONING account is completed by the next ensure, never recreated")
    void completedForward() {
        ProofRecord proof = whatsapp("proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        db.proofs.clear();
        whatsapp("proof-2");

        EnsureResult result = process.ensure(command(proof, "proof-2")).block();

        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.status()).isEqualTo(AccountState.ACTIVE);
        assertThat(result.isNew()).as("this call did not create it").isFalse();
        assertThat(db.count("identity_users")).isEqualTo(1);
    }

    @Test
    @DisplayName("two simultaneous sign-ins with one contact meet one execution and make one account")
    void simultaneousStartsMakeOneAccount() throws Exception {
        for (int round = 0; round < 3; round++) {
            db.reset();
            keycloak.byUsername.clear();
            keycloak.creates.set(0);
            ProofRecord proof = whatsapp("proof-a");
            whatsapp("proof-b");
            // different proofs, same contact: the race the workflow id exists to settle
            CompletableFuture<EnsureResult> first = process.ensure(command(proof, "proof-a")).toFuture();
            CompletableFuture<EnsureResult> second = process.ensure(command(proof, "proof-b")).toFuture();

            EnsureResult a = first.get(60, TimeUnit.SECONDS);
            EnsureResult b = second.get(60, TimeUnit.SECONDS);

            assertThat(a.accountId()).isEqualTo(b.accountId()).isNotNull();
            assertThat(a.status()).isEqualTo(AccountState.ACTIVE);
            assertThat(b.status()).isEqualTo(AccountState.ACTIVE);
            assertThat(db.count("identity_users")).as("round " + round).isEqualTo(1);
            assertThat(db.count("identity_contacts")).isEqualTo(1);
            assertThat(keycloak.creates.get()).isEqualTo(1);
            assertThat(db.count(AccountFixtures.OUTBOX)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a suspended account is refused at once with ACCOUNT_SUSPENDED, and a merging one with ACCOUNT_MERGING")
    void refusals() {
        ProofRecord proof = whatsapp("proof-1");
        String accountId = process.ensure(command(proof, "proof-1")).block().accountId();

        db.template.updateFirst(Query.query(Criteria.where("_id").is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().set("status", AccountState.SUSPENDED), User.class).block();
        assertRefused(() -> process.ensure(command(proof, "proof-1")).block(), ErrorCode.ACCOUNT_SUSPENDED);

        db.template.updateFirst(Query.query(Criteria.where("_id").is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().set("status", AccountState.ACTIVE)
                        .set("pendingKind", com.pml.identity.domain.enums.PendingKind.MERGING), User.class).block();
        assertRefused(() -> process.ensure(command(proof, "proof-1")).block(), ErrorCode.ACCOUNT_MERGING);
    }

    @Test
    @DisplayName("a refusal inside the workflow (the account was suspended after the fast path looked) reaches the caller with its code, and the account is not touched")
    void refusalInsideTheWorkflow() {
        ProofRecord proof = whatsapp("proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        db.template.updateFirst(Query.query(Criteria.where("_id").is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().set("status", AccountState.SUSPENDED), User.class).block();
        EnsureCommand command = command(proof, "proof-1");

        // straight to the workflow, as a caller that raced past the fast path would be
        com.pml.identity.workflow.ensure.AccountEnsureWorkflow workflow = env.getWorkflowClient().newWorkflowStub(
                AccountEnsureWorkflow.class, io.temporal.client.WorkflowOptions.newBuilder()
                        .setWorkflowId(WorkflowIds.accountEnsure(command.contactKey()))
                        .setTaskQueue(TaskQueues.ACCOUNT)
                        .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                        .setTypedSearchAttributes(ProcessSearchAttributes.builder().processKind("AccountEnsure").build()
                                .toSearchAttributes())
                        .build());
        assertThatThrownBy(() -> WorkflowClient.startUpdateWithStart(workflow::ensure, command,
                UpdateOptions.<EnsureResult>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                new WithStartWorkflowOperation<>(workflow::run, command)).getResult())
                .isInstanceOf(WorkflowUpdateException.class)
                .satisfies(error -> assertThat(com.pml.shared.workflow.Refusals.typeOf(error, "none"))
                        .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED.name()));

        assertThat(db.template.findById(accountId, User.class).block().getStatus()).isEqualTo(AccountState.SUSPENDED);
        assertThat(keycloak.creates.get()).as("nothing was created for a refused account").isZero();
    }

    @Test
    @DisplayName("an unknown proof is refused as PROOF_INVALID and no account is made")
    void unknownProof() {
        ProofRecord proof = whatsapp("proof-1");
        db.proofs.clear();

        assertRefused(() -> process.ensure(command(proof, "proof-1")).block(), ErrorCode.PROOF_INVALID);
        assertThat(db.count("identity_users")).isZero();
    }

    @Test
    @DisplayName("the execution is addressed and tagged by opaque values: no id or search attribute holds an address or a number")
    void idsAndAttributesCarryNoContact() {
        ProofRecord proof = whatsapp("proof-1");
        EnsureResult result = process.ensure(command(proof, "proof-1")).block();
        String workflowId = WorkflowIds.accountEnsure(proof.contactKey());

        var description = env.getWorkflowClient().newUntypedWorkflowStub(workflowId).describe();
        var attributes = description.getTypedSearchAttributes();
        assertThat(attributes.get(ProcessSearchAttributes.BUSINESS_ID)).isEqualTo(result.accountId());
        assertThat(attributes.get(ProcessSearchAttributes.PROCESS_KIND)).isEqualTo("AccountEnsure");
        assertThat(workflowId).doesNotContain("@").doesNotContain(PHONE).doesNotContain("260971234567");
        attributes.getUntypedValues().forEach((key, value) ->
                assertThat(String.valueOf(value)).as(key.getName()).doesNotContain("@").doesNotContain("260971"));
    }

    @Test
    @DisplayName("PLT-015 R3 · a finished run's history replays against the implementation")
    void theHistoryReplays() throws Exception {
        ProofRecord proof = whatsapp("proof-1");
        activities.failAfterClaim.set(1);
        process.ensure(command(proof, "proof-1")).block();
        String workflowId = WorkflowIds.accountEnsure(proof.contactKey());

        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, AccountEnsureWorkflowImpl.class);
    }

    private static void assertRefused(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRefusal.class)
                .extracting(error -> ((DomainRefusal) error).errorCode())
                .isEqualTo(code);
    }

    /** The real activities, with a crash that can be scheduled after the claim. */
    static final class FaultyActivities implements AccountEnsureActivities {
        private final AccountEnsureActivities real;
        final AtomicInteger claims = new AtomicInteger();
        /** How many times {@code createKeycloakUser} dies before it is allowed to run. */
        final AtomicInteger failAfterClaim = new AtomicInteger();

        FaultyActivities(AccountEnsureActivities real) {
            this.real = real;
        }

        @Override
        public Claimed claimContact(Claim claim) {
            claims.incrementAndGet();
            return real.claimContact(claim);
        }

        @Override
        public String createKeycloakUser(String accountId) {
            if (failAfterClaim.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                throw new IllegalStateException("worker crashed after the claim") {
                    // retryable: a crash is not a refusal
                };
            }
            return real.createKeycloakUser(accountId);
        }

        @Override
        public void applyAttributesAndRoles(String accountId, String keycloakUserId) {
            real.applyAttributesAndRoles(accountId, keycloakUserId);
        }

        @Override
        public AccountState activateAccount(Activation activation) {
            return real.activateAccount(activation);
        }

        @Override
        public void stageOutbox(String accountId) {
            real.stageOutbox(accountId);
        }
    }
}
