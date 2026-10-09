package com.pml.booking.workflow.bank;

import com.pml.booking.domain.model.BankAccount.VerificationStatus;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.bank.BankVerificationWorkflow.Confirmation;
import com.pml.booking.workflow.bank.BankVerificationWorkflow.Start;
import com.pml.booking.workflow.bank.BankVerificationWorkflow.View;
import com.pml.booking.workflow.payout.PayoutRules;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L3")
@Tag("ET-FIN-003")
@DisplayName("ET-FIN-003-R4 · a bank account is verified by a confirmed micro-deposit, with a lockout")
class BankVerificationWorkflowTest {

    private static final String BANK = "bank-1";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeAccounts accounts;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        accounts = new FakeAccounts(() -> env.currentTimeMillis());
        env.newWorker(TaskQueues.FINANCE).registerWorkflowImplementationTypes(BankVerificationWorkflowImpl.class);
        env.getWorkerFactory().getWorker(TaskQueues.FINANCE).registerActivitiesImplementations(accounts);
        env.newWorker(TaskQueues.PROVIDER).registerActivitiesImplementations(accounts);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("the amount that arrived verifies the account and closes the execution")
    void theRightAmountVerifies() {
        BankVerificationWorkflow verification = begin();

        View verified = verification.confirmAmount(new Confirmation("organizer-1", accounts.deposit));
        WorkflowStub.fromTyped(verification).getResult(Void.class);

        assertThat(verified.status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(accounts.depositsSent).isEqualTo(1);
        assertThat(accounts.booked()).hasSize(1);
        assertThat(accounts.reversed()).isEmpty();
    }

    @Test
    @DisplayName("D-31 · a deposit the provider confirms failed has its cost reversed once, and the owner can start again")
    void aFailedDepositIsReversed() {
        accounts.outcome = BankVerificationWorkflow.DepositOutcome.FAILED;
        BankVerificationWorkflow verification = begin();

        WorkflowStub.fromTyped(verification).getResult(Void.class);

        assertThat(accounts.reversed()).hasSize(1).isEqualTo(accounts.booked());
        assertThat(accounts.status).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    @DisplayName("D-31 · a deposit the provider never answers for is asked about until it lapses, and its cost stands")
    void anUnansweredDepositIsNotReversed() {
        accounts.outcome = BankVerificationWorkflow.DepositOutcome.PENDING;
        BankVerificationWorkflow verification = begin();

        WorkflowStub.fromTyped(verification).getResult(Void.class);

        assertThat(accounts.outcomeQuestions()).isGreaterThan(1);
        assertThat(accounts.reversed()).isEmpty();
        assertThat(accounts.status).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    @DisplayName("D-27 · asking to verify twice sends one deposit and books it once as a verification cost")
    void beginIsIdempotent() {
        BankVerificationWorkflow verification = begin();
        verification.begin(new Start(BANK));

        assertThat(accounts.depositsSent).isEqualTo(1);
        assertThat(accounts.booked()).hasSize(1);
    }

    @Test
    @DisplayName("three wrong amounts lock verification for a day; after it, the right amount verifies")
    void threeMissesLockForADay() {
        BankVerificationWorkflow verification = begin();
        BigDecimal wrong = accounts.deposit.add(new BigDecimal("0.20"));

        for (int miss = 0; miss < BankVerificationRules.MAX_ATTEMPTS; miss++) {
            verification.confirmAmount(new Confirmation("organizer-1", wrong));
        }

        assertThatThrownBy(() -> verification.confirmAmount(new Confirmation("organizer-1", accounts.deposit)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.BANK_ACCOUNT_NOT_VERIFIED.name()));
        assertThat(accounts.status).isEqualTo(VerificationStatus.VERIFYING);

        env.sleep(BankVerificationRules.LOCKOUT.plusMinutes(5));

        assertThat(verification.current().attempts()).as("the lockout ended and the count reset").isZero();
        assertThat(verification.confirmAmount(new Confirmation("organizer-1", accounts.deposit)).status())
                .isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("a deposit nobody confirms within a week lapses back to PENDING")
    void anUnconfirmedDepositLapses() {
        BankVerificationWorkflow verification = begin();

        WorkflowStub.fromTyped(verification).getResult(Void.class);

        assertThat(accounts.status).isEqualTo(VerificationStatus.PENDING);
        assertThat(accounts.deposit).isNull();
    }

    @Test
    @DisplayName("a deposit the provider refuses is reported, and leaves the account able to start again")
    void aRefusedDepositIsReported() {
        accounts.depositFails = true;
        BankVerificationWorkflow verification = starter();
        WorkflowClient.start(verification::run, new Start(BANK));
        BankVerificationWorkflow running = client.newWorkflowStub(BankVerificationWorkflow.class, WorkflowIds.bankVerification(BANK));

        assertThatThrownBy(() -> running.begin(new Start(BANK)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.BANK_ACCOUNT_NOT_VERIFIED.name()));
        assertThat(accounts.status).isEqualTo(VerificationStatus.PENDING);
        assertThat(accounts.booked()).isEmpty();
    }

    private BankVerificationWorkflow starter() {
        return client.newWorkflowStub(BankVerificationWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.bankVerification(BANK))
                .setTaskQueue(TaskQueues.FINANCE)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .build());
    }

    private BankVerificationWorkflow begin() {
        WorkflowClient.start(starter()::run, new Start(BANK));
        BankVerificationWorkflow verification = client.newWorkflowStub(BankVerificationWorkflow.class, WorkflowIds.bankVerification(BANK));
        assertThat(verification.begin(new Start(BANK)).status()).isEqualTo(VerificationStatus.VERIFYING);
        return verification;
    }

    static final class FakeAccounts implements BankVerificationActivities {
        private final LongSupplier now;
        VerificationStatus status = VerificationStatus.PENDING;
        BigDecimal deposit;
        int attempts;
        long lockedUntil;
        int depositsSent;
        volatile boolean depositFails;
        private final Set<String> sent = new HashSet<>();
        private final Set<String> bookedDeposits = new HashSet<>();
        private final Set<String> reversedDeposits = new HashSet<>();
        volatile BankVerificationWorkflow.DepositOutcome outcome = BankVerificationWorkflow.DepositOutcome.DELIVERED;
        private int questions;

        FakeAccounts(LongSupplier now) {
            this.now = now;
        }

        private View view() {
            return new View(BANK, status, attempts, lockedUntil);
        }

        @Override
        public synchronized View prepare(String bankAccountId, BigDecimal depositAmount) {
            if (status != VerificationStatus.VERIFYING) {
                status = VerificationStatus.VERIFYING;
                deposit = depositAmount;
                attempts = 0;
                lockedUntil = 0;
            }
            return view();
        }

        @Override
        public synchronized void sendDeposit(String bankAccountId, String providerPayoutId) {
            if (depositFails) {
                throw ApplicationFailure.newNonRetryableFailure("the provider refused the destination", PayoutRules.BAD_ACCOUNT_DETAILS);
            }
            if (sent.add(providerPayoutId)) {
                depositsSent++;
            }
        }

        @Override
        public synchronized void bookDeposit(String bankAccountId, String providerPayoutId) {
            bookedDeposits.add(providerPayoutId);
        }

        synchronized Set<String> booked() {
            return Set.copyOf(bookedDeposits);
        }

        @Override
        public synchronized BankVerificationWorkflow.DepositOutcome depositOutcome(String bankAccountId, String providerPayoutId) {
            questions++;
            return outcome;
        }

        @Override
        public synchronized void reverseDeposit(String bankAccountId, String providerPayoutId) {
            reversedDeposits.add(providerPayoutId);
        }

        synchronized Set<String> reversed() {
            return Set.copyOf(reversedDeposits);
        }

        synchronized int outcomeQuestions() {
            return questions;
        }

        @Override
        public synchronized View confirm(String bankAccountId, String actorId, BigDecimal amount) {
            if (BankVerificationRules.matches(deposit, amount)) {
                status = VerificationStatus.VERIFIED;
            } else if (BankVerificationRules.locksAfter(++attempts)) {
                lockedUntil = now.getAsLong() + BankVerificationRules.LOCKOUT.toMillis();
            }
            return view();
        }

        @Override
        public synchronized View unlock(String bankAccountId) {
            attempts = 0;
            lockedUntil = 0;
            return view();
        }

        @Override
        public synchronized View expire(String bankAccountId) {
            if (status == VerificationStatus.VERIFYING) {
                status = VerificationStatus.PENDING;
                deposit = null;
            }
            return view();
        }
    }
    @Test
    @DisplayName("ET-PLT-015 R3 · a verification's recorded history replays against the current implementation")
    void itsHistoryReplays() throws Exception {
        BankVerificationWorkflow verification = begin();
        verification.confirmAmount(new Confirmation("organizer-1", accounts.deposit));
        WorkflowStub.fromTyped(verification).getResult(Void.class);

        assertReplays(WorkflowIds.bankVerification(BANK), BankVerificationWorkflowImpl.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                io.temporal.api.common.v1.WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }
}
