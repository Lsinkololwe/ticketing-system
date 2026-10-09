package com.pml.identity.workflow.ownership;

import com.pml.identity.domain.enums.TransferStatus;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.identity.workflow.notify.NotifyActivities;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Nomination;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Response;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Start;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.View;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ownership-transfer workflow end to end, with the three days skipped.
 */
@Tag("L3")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R7 · a transfer completes on the nominee's word, or expires at three days")
class OwnershipTransferWorkflowTest {

    private static final String TRANSFER = "transfer-1";
    private static final String ORG = "org-kabwe";
    private static final String OWNER = "owner-1";
    private static final String NOMINEE = "nominee-1";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeTransfers transfers;
    private FakeMirror mirror;
    private FakeNotify notify;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        transfers = new FakeTransfers(env);
        mirror = new FakeMirror();
        notify = new FakeNotify();
        Worker onboarding = env.newWorker(TaskQueues.ONBOARDING);
        onboarding.registerWorkflowImplementationTypes(OwnershipTransferWorkflowImpl.class);
        onboarding.registerActivitiesImplementations(transfers, mirror);
        env.newWorker(TaskQueues.NOTIFY).registerActivitiesImplementations(notify);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("R7 · the nominee accepts: ownership moves once, Keycloak mirrors it, both parties are told")
    void acceptanceCompletes() {
        OwnershipTransferWorkflow transfer = open();

        View completed = transfer.accept(new Response(TRANSFER, NOMINEE));
        awaitClosed(transfer);

        assertThat(completed.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfers.completions).isEqualTo(1);
        assertThat(mirror.attempts).isEqualTo(1);
        assertThat(transfers.mirrorPendingMarks).isZero();
        assertThat(notify.requests).extracting(Request::templateKey)
                .containsExactly("ownership.transfer-requested", "ownership.confirmed", "ownership.confirmed");
        assertThat(notify.requests).extracting(Request::recipientUserId).containsExactly(NOMINEE, OWNER, NOMINEE);
    }

    @Test
    @DisplayName("§4 · a transfer nobody answers expires at three days, and nothing moves")
    void expiresAtThreeDays() {
        OwnershipTransferWorkflow transfer = open();

        env.sleep(OwnershipTransferRules.TTL.plusMinutes(1));
        awaitClosed(transfer);

        assertThat(transfers.status).isEqualTo(TransferStatus.EXPIRED);
        assertThat(transfers.completions).isZero();
        assertThat(mirror.attempts).isZero();
    }

    @Test
    @DisplayName("§4 · an hour before expiry the transfer is still pending, and acceptance still works")
    void stillPendingJustBeforeExpiry() {
        OwnershipTransferWorkflow transfer = open();

        env.sleep(OwnershipTransferRules.TTL.minusHours(1));
        assertThat(transfer.current().status()).isEqualTo(TransferStatus.PENDING);

        assertThat(transfer.accept(new Response(TRANSFER, NOMINEE)).status()).isEqualTo(TransferStatus.COMPLETED);
        awaitClosed(transfer);
    }

    @Test
    @DisplayName("R7 · only the nominee accepts; the refusal leaves the transfer pending")
    void onlyTheNomineeAccepts() {
        OwnershipTransferWorkflow transfer = open();

        assertThatThrownBy(() -> transfer.accept(new Response(TRANSFER, OWNER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name()));
        assertThat(transfer.current().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfers.completions).isZero();
    }

    @Test
    @DisplayName("R7 · the nominee declines; nothing is mirrored")
    void theNomineeDeclines() {
        OwnershipTransferWorkflow transfer = open();

        assertThatThrownBy(() -> transfer.decline(new Response(TRANSFER, OWNER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name()));
        assertThat(transfer.decline(new Response(TRANSFER, NOMINEE)).status()).isEqualTo(TransferStatus.CANCELLED);
        awaitClosed(transfer);
        assertThat(mirror.attempts).isZero();
    }

    @Test
    @DisplayName("R7 · the initiating owner cancels while pending; the nominee cannot")
    void theOwnerCancels() {
        OwnershipTransferWorkflow transfer = open();

        assertThatThrownBy(() -> transfer.cancel(new Response(TRANSFER, NOMINEE)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name()));
        assertThat(transfer.cancel(new Response(TRANSFER, OWNER)).status()).isEqualTo(TransferStatus.CANCELLED);
        awaitClosed(transfer);
    }

    @Test
    @DisplayName("R8 · MongoDB wins: a Keycloak mirror that never lands is retried, then marked for repair")
    void aMirrorThatNeverLandsIsMarked() {
        mirror.failing = true;
        OwnershipTransferWorkflow transfer = open();

        transfer.accept(new Response(TRANSFER, NOMINEE));
        awaitClosed(transfer);

        assertThat(transfers.status).isEqualTo(TransferStatus.COMPLETED);
        assertThat(mirror.attempts).isEqualTo(OwnershipTransferRules.MIRROR_ATTEMPTS);
        assertThat(transfers.mirrorPendingMarks).isEqualTo(1);
    }

    @Test
    @DisplayName("NTF-001 R4 · a notification that cannot be requested does not stop the transfer")
    void notificationsNeverBlock() {
        notify.failing = true;
        OwnershipTransferWorkflow transfer = open();

        assertThat(transfer.accept(new Response(TRANSFER, NOMINEE)).status()).isEqualTo(TransferStatus.COMPLETED);
        awaitClosed(transfer);
        assertThat(transfers.completions).isEqualTo(1);
    }

    @Test
    @DisplayName("an owner nominating themselves is refused before anything is written")
    void selfNominationIsRefused() {
        OwnershipTransferWorkflow transfer = start();

        assertThatThrownBy(() -> transfer.open(new Nomination(TRANSFER, ORG, OWNER, OWNER, null)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.TRANSFER_TO_SELF.name()));
        assertThat(transfers.status).isNull();
    }

    @Test
    @DisplayName("PLT-015 R3 · an expired transfer's history replays against the implementation")
    void theHistoryReplays() throws Exception {
        OwnershipTransferWorkflow transfer = open();
        env.sleep(OwnershipTransferRules.TTL.plusMinutes(1));
        awaitClosed(transfer);

        assertReplays(WorkflowIds.ownershipTransfer(TRANSFER), OwnershipTransferWorkflowImpl.class);
    }

    // ---- harness -------------------------------------------------------------------------------

    private OwnershipTransferWorkflow start() {
        OwnershipTransferWorkflow starter = client.newWorkflowStub(OwnershipTransferWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(WorkflowIds.ownershipTransfer(TRANSFER))
                        .setTaskQueue(TaskQueues.ONBOARDING)
                        .build());
        WorkflowClient.start(starter::run, new Start(TRANSFER));
        return client.newWorkflowStub(OwnershipTransferWorkflow.class, WorkflowIds.ownershipTransfer(TRANSFER));
    }

    private OwnershipTransferWorkflow open() {
        OwnershipTransferWorkflow transfer = start();
        assertThat(transfer.open(new Nomination(TRANSFER, ORG, OWNER, NOMINEE, "retiring")).status())
                .isEqualTo(TransferStatus.PENDING);
        return transfer;
    }

    private static void awaitClosed(OwnershipTransferWorkflow transfer) {
        WorkflowStub.fromTyped(transfer).getResult(Void.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }

    static final class FakeTransfers implements OwnershipTransferActivities {
        private final TestWorkflowEnvironment env;
        volatile TransferStatus status;
        volatile long expiresAt;
        volatile int completions;
        volatile int mirrorPendingMarks;

        FakeTransfers(TestWorkflowEnvironment env) {
            this.env = env;
        }

        private View view() {
            return status == null
                    ? new View(TRANSFER, null, null, null, null, 0L)
                    : new View(TRANSFER, ORG, OWNER, NOMINEE, status, expiresAt);
        }

        @Override
        public synchronized View load(String transferId) {
            return view();
        }

        @Override
        public synchronized View open(Nomination nomination) {
            if (status == null) {
                status = TransferStatus.PENDING;
                expiresAt = env.currentTimeMillis() + OwnershipTransferRules.TTL.toMillis();
            }
            return view();
        }

        @Override
        public synchronized View complete(String transferId) {
            if (status == TransferStatus.PENDING) {
                status = TransferStatus.COMPLETED;
                completions++;
            } else if (status != TransferStatus.COMPLETED) {
                throw Refusals.refusal(ErrorCode.TRANSFER_NOT_PENDING, "the transfer is " + status);
            }
            return view();
        }

        @Override
        public synchronized View decline(String transferId, String actorId) {
            return close();
        }

        @Override
        public synchronized View cancel(String transferId, String actorId) {
            return close();
        }

        private View close() {
            if (status == TransferStatus.PENDING) {
                status = TransferStatus.CANCELLED;
            }
            return view();
        }

        @Override
        public synchronized View expire(String transferId) {
            if (status == TransferStatus.PENDING) {
                status = TransferStatus.EXPIRED;
            }
            return view();
        }

        @Override
        public synchronized void markMirrorPending(String transferId) {
            mirrorPendingMarks++;
        }
    }

    static final class FakeMirror implements OwnershipMirrorActivities {
        volatile int attempts;
        volatile boolean failing;

        @Override
        public synchronized void mirror(String transferId) {
            attempts++;
            if (failing) {
                throw new RuntimeException("keycloak unavailable");
            }
        }
    }

    static final class FakeNotify implements NotifyActivities {
        final List<Request> requests = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public void request(Request request) {
            if (failing) {
                throw new RuntimeException("temporal frontend unavailable");
            }
            requests.add(request);
        }
    }
}
